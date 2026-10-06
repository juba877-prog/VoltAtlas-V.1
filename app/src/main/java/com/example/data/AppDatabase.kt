package com.example.data

import android.content.Context
import android.util.Log
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.ai.AiDatabaseConverters
import com.example.data.ai.ApprovalGrantDao
import com.example.data.ai.ApprovalGrantEntity
import com.example.data.ai.MutationExecutionReceiptDao
import com.example.data.ai.MutationExecutionReceiptEntity
import kotlinx.coroutines.flow.Flow




/**
 * Explicit schema inspection for migrations that must support multiple legitimate
 * pre-states (e.g. a database that stopped at an intermediate version whose column
 * was already added by an overlapping historical hop). Never swallows failures:
 * a PRAGMA error propagates and aborts the migration transaction.
 */
internal fun migrationColumnExists(db: SupportSQLiteDatabase, table: String, column: String): Boolean {
    val cursor = db.query("PRAGMA table_info(`$table`)")
    try {
        val nameIndex = cursor.getColumnIndexOrThrow("name")
        while (cursor.moveToNext()) {
            if (cursor.getString(nameIndex) == column) {
                return true
            }
        }
        return false
    } finally {
        cursor.close()
    }
}

/**
 * Mandatory guard for ALTER TABLE ADD COLUMN steps: executes the DDL only when the exact
 * column is absent, so both legitimate pre-states converge to one valid post-state.
 * Every other SQL failure propagates and rolls back the migration transaction.
 */
internal fun migrationEnsureColumn(db: SupportSQLiteDatabase, alterStatement: String) {
    val parsed = Regex("^ALTER TABLE [`]?([A-Za-z0-9_]+)[`]? ADD COLUMN [`]?([A-Za-z0-9_]+)[`]?", RegexOption.IGNORE_CASE)
        .find(alterStatement.trim())
        ?: throw IllegalArgumentException("migrationEnsureColumn cannot parse statement: $alterStatement")
    if (!migrationColumnExists(db, parsed.groupValues[1], parsed.groupValues[2])) {
        db.execSQL(alterStatement)
    }
}

/**
 * Mandatory copy-integrity verification for table-rebuild migrations: the old table may
 * only be dropped after the rebuilt table provably holds exactly as many rows as the
 * source. Any mismatch aborts the migration before source data can be destroyed.
 */
internal fun verifyMigrationRowCount(db: SupportSQLiteDatabase, sourceTable: String, rebuiltTable: String) {
    val cursor = db.query("SELECT (SELECT COUNT(*) FROM $sourceTable), (SELECT COUNT(*) FROM $rebuiltTable)")
    try {
        if (!cursor.moveToFirst()) {
            throw IllegalStateException(
                "Migration copy verification failed: no count result for $sourceTable -> $rebuiltTable"
            )
        }
        val sourceRows = cursor.getLong(0)
        val rebuiltRows = cursor.getLong(1)
        if (sourceRows != rebuiltRows) {
            throw IllegalStateException(
                "Migration copy verification failed: $sourceTable holds $sourceRows rows " +
                    "but $rebuiltTable holds $rebuiltRows rows; refusing to drop $sourceTable"
            )
        }
    } finally {
        cursor.close()
    }
}
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Schema-aware idempotent guard: a database that executed an overlapping
        // historical upgrade may already carry this column. Other failures abort.
        migrationEnsureColumn(db, "ALTER TABLE `circuits` ADD COLUMN `upsBacked` INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Fully fail-fast rebuild sequence: create -> copy -> verify row parity -> drop ->
        // rename. Every step is mandatory; any failure aborts the Room migration
        // transaction so the original tables survive untouched. A valid-schema but
        // data-empty replacement table can never silently replace a populated source.

        // 1) Rebuild transformers table for composite primary key (projectId, transformerId)
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `transformers_new` (
                `projectId` TEXT NOT NULL DEFAULT 'DEFAULT_PROJECT',
                `transformerId` TEXT NOT NULL,
                `transformerName` TEXT NOT NULL,
                `kVA` REAL NOT NULL,
                `voltage` INTEGER NOT NULL,
                `percentZ` REAL NOT NULL,
                `xr` REAL NOT NULL,
                `txType` TEXT NOT NULL,
                `sourceType` TEXT NOT NULL,
                `status` TEXT NOT NULL,
                `sizeMode` TEXT NOT NULL,
                `manualKVA` REAL NOT NULL,
                `demandFactor` REAL NOT NULL,
                `diversity` REAL NOT NULL,
                `growth` REAL NOT NULL,
                `safety` REAL NOT NULL,
                `vectorGroup` TEXT NOT NULL DEFAULT 'Dyn11',
                `z0OverZ1` REAL NOT NULL DEFAULT 1.0,
                `pf` REAL NOT NULL DEFAULT 0.8,
                PRIMARY KEY(`projectId`, `transformerId`)
            )
        """.trimIndent())
        db.execSQL("""
            INSERT INTO `transformers_new` (
                `projectId`, `transformerId`, `transformerName`, `kVA`, `voltage`, `percentZ`, `xr`, `txType`, `sourceType`, `status`, `sizeMode`, `manualKVA`, `demandFactor`, `diversity`, `growth`, `safety`, `vectorGroup`, `z0OverZ1`, `pf`
            )
            SELECT
                `projectId`, `transformerId`, `transformerName`, `kVA`, `voltage`, `percentZ`, `xr`, `txType`, `sourceType`, `status`, `sizeMode`, `manualKVA`, `demandFactor`, `diversity`, `growth`, `safety`, `vectorGroup`, `z0OverZ1`, `pf`
            FROM `transformers`
        """.trimIndent())
        verifyMigrationRowCount(db, "`transformers`", "`transformers_new`")
        db.execSQL("DROP TABLE `transformers`")
        db.execSQL("ALTER TABLE `transformers_new` RENAME TO `transformers`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_transformers_projectId` ON `transformers`(`projectId`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_transformers_projectId_transformerId` ON `transformers`(`projectId`, `transformerId`)")
        // 2) Rebuild generator_settings table
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `generator_settings_new` (
                `projectId` TEXT NOT NULL DEFAULT 'DEFAULT_PROJECT',
                `id` INTEGER NOT NULL,
                `additionalEssentialLoadKW` REAL NOT NULL,
                `largestMotorKW` REAL NOT NULL,
                `motorStartMethod` TEXT NOT NULL,
                `motorPf` REAL NOT NULL,
                `generatorPf` REAL NOT NULL,
                `demandFactor` REAL NOT NULL,
                `diversityFactor` REAL NOT NULL,
                `stepLoadPct` REAL NOT NULL,
                `oversizingFactor` REAL NOT NULL,
                `enableManualOverride` INTEGER NOT NULL,
                `manualSizeKVA` REAL,
                PRIMARY KEY(`projectId`, `id`)
            )
        """.trimIndent())
        db.execSQL("""
            INSERT INTO `generator_settings_new` (
                `projectId`, `id`, `additionalEssentialLoadKW`, `largestMotorKW`, `motorStartMethod`, `motorPf`, `generatorPf`, `demandFactor`, `diversityFactor`, `stepLoadPct`, `oversizingFactor`, `enableManualOverride`, `manualSizeKVA`
            )
            SELECT
                'DEFAULT_PROJECT', `id`, `additionalEssentialLoadKW`, `largestMotorKW`, `motorStartMethod`, `motorPf`, `generatorPf`, `demandFactor`, `diversityFactor`, `stepLoadPct`, `oversizingFactor`, `enableManualOverride`, `manualSizeKVA`
            FROM `generator_settings`
        """.trimIndent())
        verifyMigrationRowCount(db, "`generator_settings`", "`generator_settings_new`")
        db.execSQL("DROP TABLE `generator_settings`")
        db.execSQL("ALTER TABLE `generator_settings_new` RENAME TO `generator_settings`")
        // 3) Rebuild ats_settings table
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `ats_settings_new` (
                `projectId` TEXT NOT NULL DEFAULT 'DEFAULT_PROJECT',
                `id` INTEGER NOT NULL,
                `systemVoltage` INTEGER NOT NULL,
                `systemFrequency` INTEGER NOT NULL,
                `numberOfSources` INTEGER NOT NULL,
                `atsLocation` TEXT NOT NULL,
                `transferType` TEXT NOT NULL,
                `utilizationCategory` TEXT NOT NULL,
                `atsClass` TEXT NOT NULL,
                `loadCoverage` TEXT NOT NULL,
                `ambientTemp` INTEGER NOT NULL,
                `designSafetyFactor` REAL NOT NULL,
                `futureGrowthFactor` REAL NOT NULL,
                `approvedManufacturer` TEXT NOT NULL,
                `mainsFailureDetectionDelay` INTEGER NOT NULL,
                `generatorStartSignalDelay` INTEGER NOT NULL,
                `generatorWarmUp` INTEGER NOT NULL,
                `mainsReturnDetectionDelay` INTEGER NOT NULL,
                `reTransferDelay` INTEGER NOT NULL,
                `generatorCooldown` INTEGER NOT NULL,
                `enclosureIpRating` TEXT NOT NULL,
                PRIMARY KEY(`projectId`, `id`)
            )
        """.trimIndent())
        db.execSQL("""
            INSERT INTO `ats_settings_new` (
                `projectId`, `id`, `systemVoltage`, `systemFrequency`, `numberOfSources`, `atsLocation`, `transferType`, `utilizationCategory`, `atsClass`, `loadCoverage`, `ambientTemp`, `designSafetyFactor`, `futureGrowthFactor`, `approvedManufacturer`, `mainsFailureDetectionDelay`, `generatorStartSignalDelay`, `generatorWarmUp`, `mainsReturnDetectionDelay`, `reTransferDelay`, `generatorCooldown`, `enclosureIpRating`
            )
            SELECT
                'DEFAULT_PROJECT', `id`, `systemVoltage`, `systemFrequency`, `numberOfSources`, `atsLocation`, `transferType`, `utilizationCategory`, `atsClass`, `loadCoverage`, `ambientTemp`, `designSafetyFactor`, `futureGrowthFactor`, `approvedManufacturer`, `mainsFailureDetectionDelay`, `generatorStartSignalDelay`, `generatorWarmUp`, `mainsReturnDetectionDelay`, `reTransferDelay`, `generatorCooldown`, `enclosureIpRating`
            FROM `ats_settings`
        """.trimIndent())
        verifyMigrationRowCount(db, "`ats_settings`", "`ats_settings_new`")
        db.execSQL("DROP TABLE `ats_settings`")
        db.execSQL("ALTER TABLE `ats_settings_new` RENAME TO `ats_settings`")

        // 4) Rebuild ups_settings table
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `ups_settings_new` (
                `projectId` TEXT NOT NULL DEFAULT 'DEFAULT_PROJECT',
                `id` INTEGER NOT NULL,
                `systemVoltage` INTEGER NOT NULL,
                `systemFrequency` INTEGER NOT NULL,
                `upsPf` REAL NOT NULL,
                `upsEfficiency` REAL NOT NULL,
                `batteryType` TEXT NOT NULL,
                `autonomyMinutes` INTEGER NOT NULL,
                `batteryEodVoltage` REAL NOT NULL,
                `ambientTemp` INTEGER NOT NULL,
                `tempDeratingFactor` REAL NOT NULL,
                `agingFactor` REAL NOT NULL,
                `designMarginPct` REAL NOT NULL,
                PRIMARY KEY(`projectId`, `id`)
            )
        """.trimIndent())
        db.execSQL("""
            INSERT INTO `ups_settings_new` (
                `projectId`, `id`, `systemVoltage`, `systemFrequency`, `upsPf`, `upsEfficiency`, `batteryType`, `autonomyMinutes`, `batteryEodVoltage`, `ambientTemp`, `tempDeratingFactor`, `agingFactor`, `designMarginPct`
            )
            SELECT
                'DEFAULT_PROJECT', `id`, `systemVoltage`, `systemFrequency`, `upsPf`, `upsEfficiency`, `batteryType`, `autonomyMinutes`, `batteryEodVoltage`, `ambientTemp`, `tempDeratingFactor`, `agingFactor`, `designMarginPct`
            FROM `ups_settings`
        """.trimIndent())
        verifyMigrationRowCount(db, "`ups_settings`", "`ups_settings_new`")
        db.execSQL("DROP TABLE `ups_settings`")
        db.execSQL("ALTER TABLE `ups_settings_new` RENAME TO `ups_settings`")
        // 5) Rebuild pf_global_settings table
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `pf_global_settings_new` (
                `projectId` TEXT NOT NULL DEFAULT 'DEFAULT_PROJECT',
                `id` INTEGER NOT NULL,
                `targetPf` REAL NOT NULL,
                `kvarStepSize` REAL NOT NULL,
                `systemVoltage` INTEGER NOT NULL,
                `annualOperatingHours` INTEGER NOT NULL,
                `energyCost` REAL NOT NULL,
                `lossReductionPct` REAL NOT NULL,
                `unityToleranceKvar` REAL NOT NULL,
                PRIMARY KEY(`projectId`, `id`)
            )
        """.trimIndent())
        db.execSQL("""
            INSERT INTO `pf_global_settings_new` (
                `projectId`, `id`, `targetPf`, `kvarStepSize`, `systemVoltage`, `annualOperatingHours`, `energyCost`, `lossReductionPct`, `unityToleranceKvar`
            )
            SELECT
                'DEFAULT_PROJECT', `id`, `targetPf`, `kvarStepSize`, `systemVoltage`, `annualOperatingHours`, `energyCost`, `lossReductionPct`, `unityToleranceKvar`
            FROM `pf_global_settings`
        """.trimIndent())
        verifyMigrationRowCount(db, "`pf_global_settings`", "`pf_global_settings_new`")
        db.execSQL("DROP TABLE `pf_global_settings`")
        db.execSQL("ALTER TABLE `pf_global_settings_new` RENAME TO `pf_global_settings`")

        // 6) Rebuild pf_transformer_settings table
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `pf_transformer_settings_new` (
                `projectId` TEXT NOT NULL DEFAULT 'DEFAULT_PROJECT',
                `transformerId` TEXT NOT NULL,
                `harmonicFilter` TEXT NOT NULL,
                `detuningReactor` TEXT NOT NULL,
                PRIMARY KEY(`projectId`, `transformerId`)
            )
        """.trimIndent())
        db.execSQL("""
            INSERT INTO `pf_transformer_settings_new` (
                `projectId`, `transformerId`, `harmonicFilter`, `detuningReactor`
            )
            SELECT
                'DEFAULT_PROJECT', `transformerId`, `harmonicFilter`, `detuningReactor`
            FROM `pf_transformer_settings`
        """.trimIndent())
        verifyMigrationRowCount(db, "`pf_transformer_settings`", "`pf_transformer_settings_new`")
        db.execSQL("DROP TABLE `pf_transformer_settings`")
        db.execSQL("ALTER TABLE `pf_transformer_settings_new` RENAME TO `pf_transformer_settings`")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 1) projects table + default project (mandatory: fail-fast)
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `projects` (
                `projectId` TEXT NOT NULL,
                `projectName` TEXT NOT NULL,
                `projectCode` TEXT,
                `clientName` TEXT,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`projectId`)
            )
        """.trimIndent())
        // OR IGNORE is the intended idempotency for the legacy default-project seed.
        db.execSQL("""
            INSERT OR IGNORE INTO `projects`
                (`projectId`,`projectName`,`projectCode`,`clientName`,`createdAt`,`updatedAt`)
            VALUES
                ('DEFAULT_PROJECT','Imported (El Rehab)','LEGACY','',
                 ${System.currentTimeMillis()}, ${System.currentTimeMillis()})
        """.trimIndent())

        // 2) app_state table (mandatory: fail-fast)
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `app_state` (
                `id` INTEGER NOT NULL,
                `activeProjectId` TEXT,
                PRIMARY KEY(`id`)
            )
        """.trimIndent())
        db.execSQL("INSERT OR REPLACE INTO `app_state` (`id`,`activeProjectId`) VALUES (0, NULL)")

        // 3) new circuits columns (schema-aware guards; genuine failures propagate)
        migrationEnsureColumn(db, "ALTER TABLE `circuits` ADD COLUMN `projectId` TEXT NOT NULL DEFAULT 'DEFAULT_PROJECT'")
        migrationEnsureColumn(db, "ALTER TABLE `circuits` ADD COLUMN `demandFactor` REAL NOT NULL DEFAULT 1.0")
        migrationEnsureColumn(db, "ALTER TABLE `circuits` ADD COLUMN `diversityGroup` TEXT")
        migrationEnsureColumn(db, "ALTER TABLE `circuits` ADD COLUMN `generatorIncluded` INTEGER NOT NULL DEFAULT 0")
        migrationEnsureColumn(db, "ALTER TABLE `circuits` ADD COLUMN `atsIncluded` INTEGER NOT NULL DEFAULT 0")
        migrationEnsureColumn(db, "ALTER TABLE `circuits` ADD COLUMN `loadNature` TEXT NOT NULL DEFAULT 'Static'")
        migrationEnsureColumn(db, "ALTER TABLE `circuits` ADD COLUMN `startingMethod` TEXT")
        migrationEnsureColumn(db, "ALTER TABLE `circuits` ADD COLUMN `startingCurrentMultiplier` REAL")
        migrationEnsureColumn(db, "ALTER TABLE `circuits` ADD COLUMN `active` INTEGER NOT NULL DEFAULT 1")

        // 4) transformers
        migrationEnsureColumn(db, "ALTER TABLE `transformers` ADD COLUMN `projectId` TEXT NOT NULL DEFAULT 'DEFAULT_PROJECT'")

        // 5) indexes (mandatory; IF NOT EXISTS preserves intended idempotency)
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_circuits_projectId` ON `circuits`(`projectId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_transformers_projectId` ON `transformers`(`projectId`)")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Schema-aware guard: a database that executed 1->2 and stopped exactly at v2
        // already carries upsBacked. Every other failure propagates.
        migrationEnsureColumn(db, "ALTER TABLE circuits ADD COLUMN upsBacked INTEGER NOT NULL DEFAULT 0")

        // Mandatory power-system settings tables: IF NOT EXISTS preserves intended
        // idempotency; genuine SQL failures propagate and roll back the migration.
        db.execSQL("CREATE TABLE IF NOT EXISTS `generator_settings` (`id` INTEGER NOT NULL, `additionalEssentialLoadKW` REAL NOT NULL, `largestMotorKW` REAL NOT NULL, `motorStartMethod` TEXT NOT NULL, `motorPf` REAL NOT NULL, `generatorPf` REAL NOT NULL, `demandFactor` REAL NOT NULL, `diversityFactor` REAL NOT NULL, `stepLoadPct` REAL NOT NULL, `oversizingFactor` REAL NOT NULL, `enableManualOverride` INTEGER NOT NULL, `manualSizeKVA` REAL, PRIMARY KEY(`id`))")
        db.execSQL("CREATE TABLE IF NOT EXISTS `ats_settings` (`id` INTEGER NOT NULL, `systemVoltage` INTEGER NOT NULL, `systemFrequency` INTEGER NOT NULL, `numberOfSources` INTEGER NOT NULL, `atsLocation` TEXT NOT NULL, `transferType` TEXT NOT NULL, `utilizationCategory` TEXT NOT NULL, `atsClass` TEXT NOT NULL, `loadCoverage` TEXT NOT NULL, `ambientTemp` INTEGER NOT NULL, `designSafetyFactor` REAL NOT NULL, `futureGrowthFactor` REAL NOT NULL, `approvedManufacturer` TEXT NOT NULL, `mainsFailureDetectionDelay` INTEGER NOT NULL, `generatorStartSignalDelay` INTEGER NOT NULL, `generatorWarmUp` INTEGER NOT NULL, `mainsReturnDetectionDelay` INTEGER NOT NULL, `reTransferDelay` INTEGER NOT NULL, `generatorCooldown` INTEGER NOT NULL, `enclosureIpRating` TEXT NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("CREATE TABLE IF NOT EXISTS `ups_settings` (`id` INTEGER NOT NULL, `systemVoltage` INTEGER NOT NULL, `systemFrequency` INTEGER NOT NULL, `upsPf` REAL NOT NULL, `upsEfficiency` REAL NOT NULL, `batteryType` TEXT NOT NULL, `autonomyMinutes` INTEGER NOT NULL, `batteryEodVoltage` REAL NOT NULL, `ambientTemp` INTEGER NOT NULL, `tempDeratingFactor` REAL NOT NULL, `agingFactor` REAL NOT NULL, `designMarginPct` REAL NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("CREATE TABLE IF NOT EXISTS `pf_global_settings` (`id` INTEGER NOT NULL, `targetPf` REAL NOT NULL, `kvarStepSize` REAL NOT NULL, `systemVoltage` INTEGER NOT NULL, `annualOperatingHours` INTEGER NOT NULL, `energyCost` REAL NOT NULL, `lossReductionPct` REAL NOT NULL, `unityToleranceKvar` REAL NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("CREATE TABLE IF NOT EXISTS `pf_transformer_settings` (`transformerId` TEXT NOT NULL, `harmonicFilter` TEXT NOT NULL, `detuningReactor` TEXT NOT NULL, PRIMARY KEY(`transformerId`))")
    }
}





data class ProjectRevisionSnapshotRecord(
    val projectId: String,
    val revision: Long,
    val updatedAt: Long?
)

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY updatedAt DESC")
    fun getAllProjects(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects ORDER BY updatedAt DESC")
    suspend fun getAllProjectsList(): List<ProjectEntity>

    @Query("SELECT * FROM projects WHERE projectId = :id")
    suspend fun getProject(id: String): ProjectEntity?

    @Query("SELECT * FROM projects WHERE projectId = :id")
    fun observeProject(id: String): Flow<ProjectEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProject(project: ProjectEntity)

    @Query("UPDATE projects SET projectName = :name, updatedAt = :ts WHERE projectId = :id")
    suspend fun rename(id: String, name: String, ts: Long)

    @Query("DELETE FROM projects WHERE projectId = :id")
    suspend fun deleteProject(id: String)

    @Query("SELECT COUNT(*) FROM projects")
    suspend fun count(): Int

    @Query("SELECT projectId, revision, updatedAt FROM projects WHERE projectId = :id")
    suspend fun getRevisionSnapshot(id: String): ProjectRevisionSnapshotRecord?

    @Query("SELECT revision FROM projects WHERE projectId = :id")
    suspend fun getRevision(id: String): Long?

    @Query("UPDATE projects SET revision = revision + 1, updatedAt = :updatedAt WHERE projectId = :id AND revision < 9223372036854775807")
    suspend fun incrementRevision(id: String, updatedAt: Long = System.currentTimeMillis()): Int

    @Query("UPDATE projects SET revision = revision + 1, updatedAt = :updatedAt WHERE projectId = :id AND revision = :expectedRevision")
    suspend fun incrementRevisionGuarded(id: String, expectedRevision: Long, updatedAt: Long = System.currentTimeMillis()): Int
}


@Dao
interface AppStateDao {
    @Query("SELECT * FROM app_state WHERE id = 0")
    fun observeState(): Flow<AppStateEntity?>

    @Query("SELECT activeProjectId FROM app_state WHERE id = 0")
    suspend fun getActiveProjectId(): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setState(state: AppStateEntity)
}

@Dao
interface CircuitDao {
    @Query("SELECT * FROM circuits WHERE projectId = :projectId ORDER BY id ASC")
    fun getCircuitsForProject(projectId: String): Flow<List<CircuitEntity>>

    @Query("SELECT * FROM circuits WHERE projectId = :projectId")
    suspend fun getCircuitsListForProject(projectId: String): List<CircuitEntity>

    @Query("DELETE FROM circuits WHERE projectId = :projectId")
    suspend fun deleteCircuitsForProject(projectId: String)

    @Query("UPDATE circuits SET generatorIncluded = :gen, atsIncluded = :gen WHERE id = :id")
    suspend fun setGeneratorAndAts(id: Int, gen: Boolean)

    @Query("UPDATE circuits SET upsBacked = :ups WHERE id = :id")
    suspend fun setUpsIncluded(id: Int, ups: Boolean)

    @Query("SELECT * FROM circuits ORDER BY id ASC")
    fun getAllCircuits(): Flow<List<CircuitEntity>>

    @Query("SELECT * FROM circuits")
    suspend fun getAllCircuitsList(): List<CircuitEntity>

    @Query("SELECT * FROM circuits WHERE id = :id")
    suspend fun getCircuitById(id: Int): CircuitEntity?

    @Query("SELECT * FROM circuits WHERE projectId = :projectId AND id = :id LIMIT 1")
    suspend fun getCircuitByIdAndProject(projectId: String, id: Int): CircuitEntity?

    @Query("SELECT * FROM circuits WHERE circuitName = :name LIMIT 1")
    suspend fun getCircuitByName(name: String): CircuitEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCircuits(circuits: List<CircuitEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCircuit(circuit: CircuitEntity): Long

    @Update
    suspend fun updateCircuit(circuit: CircuitEntity): Int

    @Query("UPDATE circuits SET parentId = NULL, sourceType = 'TRANSFORMER', fedFrom = 'Transformer' WHERE parentId = :deletedId AND projectId = :projectId")
    suspend fun nullifyChildrenParentId(deletedId: Int, projectId: String)

    @Delete
    suspend fun deleteCircuit(circuit: CircuitEntity)

    @androidx.room.Transaction
    suspend fun deleteCircuitWithCascade(circuit: CircuitEntity) {
        nullifyChildrenParentId(circuit.id, circuit.projectId)
        deleteCircuit(circuit)
    }

    @Query("DELETE FROM circuits")
    suspend fun deleteAllCircuits()
}

@Dao
interface TransformerDao {
    @Query("SELECT * FROM transformers WHERE projectId = :projectId ORDER BY transformerId ASC")
    fun getTransformersForProject(projectId: String): Flow<List<TransformerEntity>>

    @Query("SELECT * FROM transformers WHERE projectId = :projectId ORDER BY transformerId ASC")
    suspend fun getTransformersListForProject(projectId: String): List<TransformerEntity>


    @Query("DELETE FROM transformers WHERE projectId = :projectId")
    suspend fun deleteTransformersForProject(projectId: String)

    @Query("SELECT * FROM transformers ORDER BY transformerId ASC")
    fun getAllTransformers(): Flow<List<TransformerEntity>>

    @Query("SELECT * FROM transformers")
    suspend fun getAllTransformersList(): List<TransformerEntity>

    @Query("SELECT * FROM transformers WHERE projectId = :projectId AND transformerId = :id")
    suspend fun getTransformerById(projectId: String, id: String): TransformerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransformers(transformers: List<TransformerEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransformer(transformer: TransformerEntity)

    @Update
    suspend fun updateTransformer(transformer: TransformerEntity)

    @Delete
    suspend fun deleteTransformer(transformer: TransformerEntity)

    @Query("DELETE FROM transformers")
    suspend fun deleteAllTransformers()
}


@Dao
interface PowerSystemsDao {
    // Generator
    @Query("SELECT * FROM generator_settings WHERE projectId = :projectId AND id = 1")
    fun getGeneratorSettings(projectId: String): Flow<GeneratorEntity?>
    @Query("SELECT * FROM generator_settings WHERE projectId = :projectId ORDER BY id ASC")
    fun getAllGeneratorSettings(projectId: String): Flow<List<GeneratorEntity>>
    @Query("SELECT * FROM generator_settings WHERE projectId = :projectId ORDER BY id ASC")
    suspend fun getAllGeneratorsList(projectId: String): List<GeneratorEntity>
    @Query("SELECT COALESCE(MAX(id), 0) FROM generator_settings WHERE projectId = :projectId")
    suspend fun getMaxGeneratorId(projectId: String): Int
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveGeneratorSettings(settings: GeneratorEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveAllGeneratorSettings(settings: List<GeneratorEntity>)
    @Query("DELETE FROM generator_settings WHERE projectId = :projectId AND generatorId = :generatorId")
    suspend fun deleteGeneratorSettings(projectId: String, generatorId: String)
    @Query("DELETE FROM generator_settings WHERE projectId = :projectId")
    suspend fun deleteGeneratorsForProject(projectId: String)
    
    // ATS
    @Query("SELECT * FROM ats_settings WHERE projectId = :projectId AND id = 1")
    fun getAtsSettings(projectId: String): Flow<AtsEntity?>
    @Query("SELECT * FROM ats_settings WHERE projectId = :projectId ORDER BY id ASC")
    fun getAllAtsSettings(projectId: String): Flow<List<AtsEntity>>
    @Query("SELECT * FROM ats_settings WHERE projectId = :projectId ORDER BY id ASC")
    suspend fun getAllAtsList(projectId: String): List<AtsEntity>
    @Query("SELECT COALESCE(MAX(id), 0) FROM ats_settings WHERE projectId = :projectId")
    suspend fun getMaxAtsId(projectId: String): Int
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveAtsSettings(settings: AtsEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveAllAtsSettings(settings: List<AtsEntity>)
    @Query("DELETE FROM ats_settings WHERE projectId = :projectId AND atsId = :atsId")
    suspend fun deleteAtsSettings(projectId: String, atsId: String)
    @Query("DELETE FROM ats_settings WHERE projectId = :projectId")
    suspend fun deleteAtsForProject(projectId: String)
    
    // UPS
    @Query("SELECT * FROM ups_settings WHERE projectId = :projectId AND id = 1")
    fun getUpsSettings(projectId: String): Flow<UpsEntity?>
    @Query("SELECT * FROM ups_settings WHERE projectId = :projectId ORDER BY id ASC")
    fun getAllUpsSettings(projectId: String): Flow<List<UpsEntity>>
    @Query("SELECT * FROM ups_settings WHERE projectId = :projectId ORDER BY id ASC")
    suspend fun getAllUpsList(projectId: String): List<UpsEntity>
    @Query("SELECT COALESCE(MAX(id), 0) FROM ups_settings WHERE projectId = :projectId")
    suspend fun getMaxUpsId(projectId: String): Int
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveUpsSettings(settings: UpsEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveAllUpsSettings(settings: List<UpsEntity>)
    @Query("DELETE FROM ups_settings WHERE projectId = :projectId AND upsId = :upsId")
    suspend fun deleteUpsSettings(projectId: String, upsId: String)
    @Query("DELETE FROM ups_settings WHERE projectId = :projectId")
    suspend fun deleteUpsForProject(projectId: String)
    
    // PF Global
    @Query("SELECT * FROM pf_global_settings WHERE projectId = :projectId AND id = 1")
    fun getPfGlobalSettings(projectId: String): Flow<PfGlobalEntity?>
    @Query("SELECT * FROM pf_global_settings WHERE projectId = :projectId AND id = 1")
    suspend fun getPfGlobalSettingsDirect(projectId: String): PfGlobalEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePfGlobalSettings(settings: PfGlobalEntity)
    @Query("DELETE FROM pf_global_settings WHERE projectId = :projectId")
    suspend fun deletePfGlobalForProject(projectId: String)
    
    // PF Transformer
    @Query("SELECT * FROM pf_transformer_settings WHERE projectId = :projectId")
    fun getAllPfTransformerSettings(projectId: String): Flow<List<PfTransformerEntity>>
    @Query("SELECT * FROM pf_transformer_settings WHERE projectId = :projectId")
    suspend fun getAllPfTransformerSettingsList(projectId: String): List<PfTransformerEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePfTransformerSettings(settings: PfTransformerEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveAllPfTransformerSettings(settings: List<PfTransformerEntity>)
    @Query("DELETE FROM pf_transformer_settings WHERE projectId = :projectId")
    suspend fun deletePfTransformerForProject(projectId: String)

    // Earthing
    @Query("SELECT * FROM earthing_settings WHERE projectId = :projectId AND id = 1")
    fun getEarthingSettings(projectId: String): Flow<EarthingEntity?>
    @Query("SELECT * FROM earthing_settings WHERE projectId = :projectId AND id = 1")
    suspend fun getEarthingSettingsDirect(projectId: String): EarthingEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveEarthingSettings(settings: EarthingEntity)
    @Query("DELETE FROM earthing_settings WHERE projectId = :projectId")
    suspend fun deleteEarthingForProject(projectId: String)
}

@Dao
interface ConduitDao {
    @Query("SELECT * FROM conduit_settings WHERE projectId = :projectId")
    fun getConduitSettingsForProject(projectId: String): Flow<List<ConduitSettingsEntity>>

    @Query("SELECT * FROM conduit_settings WHERE projectId = :projectId")
    suspend fun getConduitSettingsListForProject(projectId: String): List<ConduitSettingsEntity>

    @Query("SELECT * FROM conduit_settings WHERE projectId = :projectId AND circuitId = :circuitId")
    suspend fun getConduitSetting(projectId: String, circuitId: Int): ConduitSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveConduitSetting(setting: ConduitSettingsEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveConduitSettings(settings: List<ConduitSettingsEntity>)

    @Query("DELETE FROM conduit_settings WHERE projectId = :projectId")
    suspend fun deleteConduitSettingsForProject(projectId: String)
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `conduit_settings` (
                `projectId` TEXT NOT NULL DEFAULT 'DEFAULT_PROJECT',
                `circuitId` INTEGER NOT NULL,
                `routingMode` TEXT NOT NULL DEFAULT 'ONE_PER_CONDUIT',
                `designProfile` TEXT NOT NULL DEFAULT 'PROJECT_DESIGN',
                `customFillLimitPct` REAL NOT NULL DEFAULT 40.0,
                `isDirectBuried` INTEGER NOT NULL DEFAULT 0,
                `cpcArrangement` TEXT NOT NULL DEFAULT 'INTERNAL_CORE',
                `manualConduitOverride` TEXT,
                `manualCpcOD` REAL,
                PRIMARY KEY(`projectId`, `circuitId`)
            )

        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_conduit_settings_projectId` ON `conduit_settings`(`projectId`)")
    }
}

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        migrationEnsureColumn(db, "ALTER TABLE `circuits` ADD COLUMN `panelType` TEXT")
        migrationEnsureColumn(db, "ALTER TABLE `circuits` ADD COLUMN `parentId` INTEGER")
        migrationEnsureColumn(db, "ALTER TABLE `circuits` ADD COLUMN `level` INTEGER")
        migrationEnsureColumn(db, "ALTER TABLE `circuits` ADD COLUMN `sourceType` TEXT")
    }
}

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        migrationEnsureColumn(db, "ALTER TABLE `ats_settings` ADD COLUMN `normalSourceId` TEXT")
        migrationEnsureColumn(db, "ALTER TABLE `ats_settings` ADD COLUMN `emergencySourceId` TEXT")
    }
}

val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        migrationEnsureColumn(db, "ALTER TABLE `ats_settings` ADD COLUMN `outputBoardId` INTEGER")
    }
}

val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Schema integrity verification and no-op transition for Version 10
    }
}

val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `earthing_settings` (
                `projectId` TEXT NOT NULL DEFAULT 'DEFAULT_PROJECT',
                `id` INTEGER NOT NULL,
                `soilResistivity_ohmM` REAL NOT NULL,
                `rodLength_m` REAL NOT NULL,
                `rodDiameter_mm` REAL NOT NULL,
                `rodSpacing_m` REAL NOT NULL,
                `ringConductor_mm2` REAL NOT NULL,
                `ringBurialDepth_m` REAL NOT NULL,
                `kFactor` REAL NOT NULL,
                `projectType` TEXT NOT NULL,
                `disconnectionTime_s` REAL NOT NULL,
                PRIMARY KEY(`projectId`, `id`)
            )
        """.trimIndent())
    }
}

val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Mandatory SLD layout table: failures must abort the migration transaction.
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `sld_node_positions` (
                `projectId` TEXT NOT NULL DEFAULT 'DEFAULT_PROJECT',
                `nodeId` TEXT NOT NULL,
                `x` REAL NOT NULL,
                `y` REAL NOT NULL,
                PRIMARY KEY(`projectId`, `nodeId`)
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sld_node_positions_projectId` ON `sld_node_positions`(`projectId`)")
    }
}

val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        migrationEnsureColumn(db, "ALTER TABLE `circuits` ADD COLUMN `directLoadConfirmed` INTEGER DEFAULT NULL")
    }
}

val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        migrationEnsureColumn(db, "ALTER TABLE `projects` ADD COLUMN `defaultPhaseImbalanceLimitPct` REAL NOT NULL DEFAULT 20.0")
        migrationEnsureColumn(db, "ALTER TABLE `circuits` ADD COLUMN `phaseImbalanceLimitOverridePct` REAL DEFAULT NULL")
    }
}

val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        migrationEnsureColumn(db, "ALTER TABLE `circuits` ADD COLUMN `loadCategory` TEXT DEFAULT NULL")
    }
}

val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val statements = listOf(
            "ALTER TABLE `circuits` ADD COLUMN `busbarRatingA` REAL DEFAULT NULL",
            "ALTER TABLE `circuits` ADD COLUMN `scRatingKA` REAL DEFAULT NULL",
            "ALTER TABLE `circuits` ADD COLUMN `mainIncomingType` TEXT DEFAULT NULL",
            "ALTER TABLE `circuits` ADD COLUMN `mainIncomingCount` INTEGER DEFAULT NULL",
            "ALTER TABLE `circuits` ADD COLUMN `ipRating` TEXT DEFAULT NULL",
            "ALTER TABLE `circuits` ADD COLUMN `panelLocation` TEXT DEFAULT NULL",
            "ALTER TABLE `circuits` ADD COLUMN `cableEntry` TEXT DEFAULT NULL",
            "ALTER TABLE `circuits` ADD COLUMN `feederDescription` TEXT DEFAULT NULL",
            "ALTER TABLE `circuits` ADD COLUMN `numberOfWays` INTEGER DEFAULT NULL"
        )
        for (sql in statements) {
            migrationEnsureColumn(db, sql)
        }
    }
}

val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val statements = listOf(
            "ALTER TABLE `circuits` ADD COLUMN `feederType` TEXT NOT NULL DEFAULT 'Cable'",
            "ALTER TABLE `circuits` ADD COLUMN `buswayMfg` TEXT DEFAULT NULL",
            "ALTER TABLE `circuits` ADD COLUMN `buswayModel` TEXT DEFAULT NULL"
        )
        for (sql in statements) {
            migrationEnsureColumn(db, sql)
        }
    }
}

val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val statements = listOf(
            "ALTER TABLE `earthing_settings` ADD COLUMN `targetResistanceOverride_ohm` REAL DEFAULT NULL",
            "ALTER TABLE `earthing_settings` ADD COLUMN `soilResistivitySource` TEXT NOT NULL DEFAULT 'ASSUMED'",
            "ALTER TABLE `earthing_settings` ADD COLUMN `designMarginPct` REAL NOT NULL DEFAULT 10.0",
            "ALTER TABLE `earthing_settings` ADD COLUMN `isAutoDesignEnabled` INTEGER NOT NULL DEFAULT 0",
            "ALTER TABLE `earthing_settings` ADD COLUMN `siteLength_m` REAL DEFAULT NULL",
            "ALTER TABLE `earthing_settings` ADD COLUMN `siteWidth_m` REAL DEFAULT NULL",
            "ALTER TABLE `earthing_settings` ADD COLUMN `autoDesignAppliedConfigId` TEXT DEFAULT NULL"
        )
        for (sql in statements) {
            migrationEnsureColumn(db, sql)
        }
    }
}

val MIGRATION_18_19 = object : Migration(18, 19) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val statements = listOf(
            "ALTER TABLE `generator_settings` ADD COLUMN `generatorId` TEXT NOT NULL DEFAULT 'GEN-01'",
            "ALTER TABLE `generator_settings` ADD COLUMN `generatorName` TEXT NOT NULL DEFAULT 'Generator 1'",
            "ALTER TABLE `ats_settings` ADD COLUMN `atsId` TEXT NOT NULL DEFAULT 'ATS-01'",
            "ALTER TABLE `ats_settings` ADD COLUMN `atsName` TEXT NOT NULL DEFAULT 'ATS 1'",
            "ALTER TABLE `ats_settings` ADD COLUMN `enableManualOverride` INTEGER NOT NULL DEFAULT 0",
            "ALTER TABLE `ats_settings` ADD COLUMN `manualSizeAmp` REAL DEFAULT NULL",
            "ALTER TABLE `ups_settings` ADD COLUMN `upsId` TEXT NOT NULL DEFAULT 'UPS-01'",
            "ALTER TABLE `ups_settings` ADD COLUMN `upsName` TEXT NOT NULL DEFAULT 'UPS 1'",
            "ALTER TABLE `ups_settings` ADD COLUMN `enableManualOverride` INTEGER NOT NULL DEFAULT 0",
            "ALTER TABLE `ups_settings` ADD COLUMN `manualSizeKVA` REAL DEFAULT NULL"
        )
        for (sql in statements) {
            migrationEnsureColumn(db, sql)
        }
    }
}

val MIGRATION_19_20 = object : Migration(19, 20) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val statements = listOf(
            "ALTER TABLE `circuits` ADD COLUMN `assignedGeneratorId` TEXT DEFAULT NULL",
            "ALTER TABLE `circuits` ADD COLUMN `assignedAtsId` TEXT DEFAULT NULL"
        )
        for (sql in statements) {
            migrationEnsureColumn(db, sql)
        }
    }
}

val MIGRATION_20_21 = object : Migration(20, 21) {
    override fun migrate(db: SupportSQLiteDatabase) {
        migrationEnsureColumn(db, "ALTER TABLE `circuits` ADD COLUMN `assignedUpsId` TEXT DEFAULT NULL")
    }
}

val MIGRATION_21_22 = object : Migration(21, 22) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `projects` ADD COLUMN `revision` INTEGER NOT NULL DEFAULT 1")
    }
}

val MIGRATION_22_23 = object : Migration(22, 23) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `approval_grants` (
                `approvalId` TEXT NOT NULL,
                `proposalId` TEXT NOT NULL,
                `proposalDigest` TEXT NOT NULL,
                `proposalSemanticKey` TEXT NOT NULL,
                `activeProposalSemanticKey` TEXT,
                `projectId` TEXT NOT NULL,
                `targetEntityType` TEXT NOT NULL,
                `targetEntityId` TEXT NOT NULL,
                `baseProjectRevision` INTEGER NOT NULL,
                `status` TEXT NOT NULL,
                `issuerType` TEXT NOT NULL,
                `approvedBy` TEXT NOT NULL,
                `userActionId` TEXT NOT NULL,
                `issuedAt` INTEGER NOT NULL,
                `expiresAt` INTEGER NOT NULL,
                `consumedAt` INTEGER,
                `consumedByExecutionId` TEXT,
                PRIMARY KEY(`approvalId`),
                FOREIGN KEY(`projectId`) REFERENCES `projects`(`projectId`) ON DELETE CASCADE
            )
        """.trimIndent())

        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_approval_grants_approvalId` ON `approval_grants`(`approvalId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_approval_grants_projectId` ON `approval_grants`(`projectId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_approval_grants_proposalDigest` ON `approval_grants`(`proposalDigest`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_approval_grants_proposalSemanticKey` ON `approval_grants`(`proposalSemanticKey`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_approval_grants_activeProposalSemanticKey` ON `approval_grants`(`activeProposalSemanticKey`)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `mutation_execution_receipts` (
                `executionId` TEXT NOT NULL,
                `approvalId` TEXT NOT NULL,
                `proposalId` TEXT NOT NULL,
                `proposalDigest` TEXT NOT NULL,
                `proposalSemanticKey` TEXT NOT NULL,
                `projectId` TEXT NOT NULL,
                `targetEntityType` TEXT NOT NULL,
                `targetEntityId` TEXT NOT NULL,
                `revisionBefore` INTEGER NOT NULL,
                `revisionAfter` INTEGER NOT NULL,
                `revalidationStatus` TEXT NOT NULL,
                `appliedChangesJson` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `committedAt` INTEGER NOT NULL,
                `attemptCount` INTEGER NOT NULL DEFAULT 0,
                `lastAttemptAt` INTEGER,
                `workflowCompletedAt` INTEGER,
                `errorCode` TEXT,
                `messageKey` TEXT,
                `isRecoverable` INTEGER NOT NULL DEFAULT 0,
                `diagnosticRef` TEXT,
                PRIMARY KEY(`executionId`),
                FOREIGN KEY(`approvalId`) REFERENCES `approval_grants`(`approvalId`) ON DELETE CASCADE
            )
        """.trimIndent())

        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_mutation_execution_receipts_executionId` ON `mutation_execution_receipts`(`executionId`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_mutation_execution_receipts_approvalId` ON `mutation_execution_receipts`(`approvalId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_mutation_execution_receipts_projectId` ON `mutation_execution_receipts`(`projectId`)")
    }
}

/**
 * v23 -> v24 (Roadmap Phase 1 — generic SLD topology foundation):
 *
 *  1. New `bus_ties` table for Main-Tie-Main bus couplers. Non-destructive:
 *     CREATE TABLE/INDEX on a fresh table preserves all existing rows.
 *  2. `generator_settings` gains `parallelingGroupId` + `syncBusId` (nullable,
 *     DEFAULT NULL) via the idempotent [migrationEnsureColumn] guard.
 *  3. `sld_node_positions` gains `portOverrides` (nullable, DEFAULT NULL).
 *
 * No table rebuilds, no row rewrites — zero data loss.
 */
val MIGRATION_23_24 = object : Migration(23, 24) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 1. Bus ties (fail-fast: CREATE TABLE must succeed — house MIGRATION_4_5 style)
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `bus_ties` (
                `projectId` TEXT NOT NULL DEFAULT 'DEFAULT_PROJECT',
                `id` INTEGER NOT NULL,
                `busTieId` TEXT NOT NULL DEFAULT 'TIE-01',
                `name` TEXT NOT NULL,
                `sourceBoardId` INTEGER,
                `targetBoardId` INTEGER,
                `interlockScheme` TEXT NOT NULL DEFAULT 'TWO_OUT_OF_THREE',
                `normalStatus` TEXT NOT NULL DEFAULT 'OPEN',
                `breakerRatingA` REAL,
                PRIMARY KEY(`projectId`, `id`)
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_bus_ties_source` ON `bus_ties`(`projectId`, `sourceBoardId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_bus_ties_target` ON `bus_ties`(`projectId`, `targetBoardId`)")

        // 2. Generator paralleling / synchronizing columns (idempotent guard per house style)
        migrationEnsureColumn(db, "ALTER TABLE `generator_settings` ADD COLUMN `parallelingGroupId` TEXT DEFAULT NULL")
        migrationEnsureColumn(db, "ALTER TABLE `generator_settings` ADD COLUMN `syncBusId` TEXT DEFAULT NULL")

        // 3. Persisted manual port-override hints for SLD node anchoring
        migrationEnsureColumn(db, "ALTER TABLE `sld_node_positions` ADD COLUMN `portOverrides` TEXT DEFAULT NULL")
    }
}

/**
 * v24 -> v25 (Roadmap Phase 2B — utility source modeling foundation):
 *
 *  1. `transformers.primaryVoltageV` (INTEGER, nullable) — real MV/HV primary-side
 *     supply voltage in volts (e.g. 11000 for 11 kV). Previously the SLD mislabeled
 *     the transformer's LV `voltage` column as the grid supply voltage (G5).
 *  2. `transformers.gridFaultMVA` (REAL, nullable) — utility short-circuit MVA at the
 *     point of supply (S_kQ, IEC 60909). Nullable: NULL keeps the historical
 *     infinite-grid convention (fault study begins at the transformer); setting a
 *     value makes the grid's own impedance part of the source-to-feeder fault path (G4).
 *
 * Both columns are added idempotently via [migrationEnsureColumn] — zero data loss.
 */
val MIGRATION_24_25 = object : Migration(24, 25) {
    override fun migrate(db: SupportSQLiteDatabase) {
        migrationEnsureColumn(db, "ALTER TABLE `transformers` ADD COLUMN `primaryVoltageV` INTEGER DEFAULT NULL")
        migrationEnsureColumn(db, "ALTER TABLE `transformers` ADD COLUMN `gridFaultMVA` REAL DEFAULT NULL")
    }
}

/**
 * v25 -> v26 (Phase 1 Referential Integrity & Multi-Instance Power Systems Migration):
 *
 * One-time data migration converting legacy equipment inclusion flags into explicit assignments:
 * 1. Where legacy intent is UNAMBIGUOUS (exactly 1 generator/ATS/UPS exists in the project),
 *    write the explicit assignment (assignedGeneratorId / assignedAtsId / assignedUpsId) so
 *    the previously computed attribution is preserved.
 * 2. Where AMBIGUOUS (more than 1 instance, or 0 instances exist in the project), leave the
 *    circuit unassigned and clear the stale legacy boolean inclusion flag (generatorIncluded /
 *    atsIncluded / upsBacked) to prevent phantom adoption or stale reattachment.
 */
val MIGRATION_25_26 = object : Migration(25, 26) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 1. Generator: Unambiguous (count == 1) -> assign explicit ID
        db.execSQL("""
            UPDATE `circuits`
            SET `assignedGeneratorId` = (
                SELECT `generatorId` FROM `generator_settings` g WHERE g.`projectId` = `circuits`.`projectId`
            )
            WHERE `generatorIncluded` = 1
              AND (`assignedGeneratorId` IS NULL OR `assignedGeneratorId` = '')
              AND (SELECT COUNT(*) FROM `generator_settings` g WHERE g.`projectId` = `circuits`.`projectId`) = 1
        """.trimIndent())

        // Generator: Ambiguous (count != 1) -> clear legacy flag
        db.execSQL("""
            UPDATE `circuits`
            SET `generatorIncluded` = 0
            WHERE `generatorIncluded` = 1
              AND (`assignedGeneratorId` IS NULL OR `assignedGeneratorId` = '')
              AND (SELECT COUNT(*) FROM `generator_settings` g WHERE g.`projectId` = `circuits`.`projectId`) != 1
        """.trimIndent())

        // 2. ATS: Unambiguous (count == 1) -> assign explicit ID
        db.execSQL("""
            UPDATE `circuits`
            SET `assignedAtsId` = (
                SELECT `atsId` FROM `ats_settings` a WHERE a.`projectId` = `circuits`.`projectId`
            )
            WHERE `atsIncluded` = 1
              AND (`assignedAtsId` IS NULL OR `assignedAtsId` = '')
              AND (SELECT COUNT(*) FROM `ats_settings` a WHERE a.`projectId` = `circuits`.`projectId`) = 1
        """.trimIndent())

        // ATS: Ambiguous (count != 1) -> clear legacy flag
        db.execSQL("""
            UPDATE `circuits`
            SET `atsIncluded` = 0
            WHERE `atsIncluded` = 1
              AND (`assignedAtsId` IS NULL OR `assignedAtsId` = '')
              AND (SELECT COUNT(*) FROM `ats_settings` a WHERE a.`projectId` = `circuits`.`projectId`) != 1
        """.trimIndent())

        // 3. UPS: Unambiguous (count == 1) -> assign explicit ID
        db.execSQL("""
            UPDATE `circuits`
            SET `assignedUpsId` = (
                SELECT `upsId` FROM `ups_settings` u WHERE u.`projectId` = `circuits`.`projectId`
            )
            WHERE `upsBacked` = 1
              AND (`assignedUpsId` IS NULL OR `assignedUpsId` = '')
              AND (SELECT COUNT(*) FROM `ups_settings` u WHERE u.`projectId` = `circuits`.`projectId`) = 1
        """.trimIndent())

        // UPS: Ambiguous (count != 1) -> clear legacy flag
        db.execSQL("""
            UPDATE `circuits`
            SET `upsBacked` = 0
            WHERE `upsBacked` = 1
              AND (`assignedUpsId` IS NULL OR `assignedUpsId` = '')
              AND (SELECT COUNT(*) FROM `ups_settings` u WHERE u.`projectId` = `circuits`.`projectId`) != 1
        """.trimIndent())
    }
}

/**
 * Migration 26 -> 27:
 * EE-03 (Owner Decision D3): Adds nullable `overrideClearingTime_s` column to `circuits` table
 * to allow optional explicit user overrides for cable thermal short-circuit withstand clearing time.
 */
val MIGRATION_26_27 = object : Migration(26, 27) {
    override fun migrate(db: SupportSQLiteDatabase) {
        migrationEnsureColumn(db, "ALTER TABLE `circuits` ADD COLUMN `overrideClearingTime_s` REAL DEFAULT NULL")
    }
}

@Dao
interface SldNodePositionDao {
    @Query("SELECT * FROM sld_node_positions WHERE projectId = :projectId")
    fun getPositionsForProject(projectId: String): Flow<List<SldNodePositionEntity>>

    @Query("SELECT * FROM sld_node_positions WHERE projectId = :projectId")
    suspend fun getPositionsForProjectSync(projectId: String): List<SldNodePositionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdatePosition(position: SldNodePositionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdatePositions(positions: List<SldNodePositionEntity>)

    @Query("DELETE FROM sld_node_positions WHERE projectId = :projectId")
    suspend fun clearPositionsForProject(projectId: String)

    @Query("DELETE FROM sld_node_positions WHERE projectId = :projectId AND nodeId = :nodeId")
    suspend fun deletePositionForNode(projectId: String, nodeId: String)
}

@Database(
    entities = [
        CircuitEntity::class,
        TransformerEntity::class,
        GeneratorEntity::class,
        AtsEntity::class,
        UpsEntity::class,
        PfGlobalEntity::class,
        PfTransformerEntity::class,
        ProjectEntity::class,
        AppStateEntity::class,
        ConduitSettingsEntity::class,
        EarthingEntity::class,
        SldNodePositionEntity::class,
        ApprovalGrantEntity::class,
        MutationExecutionReceiptEntity::class,
        BusTieEntity::class
    ],
    version = 27,
    // KT-02: schema export is enabled so the Room schema history is generated by the compiler and
    // committed to `app/schemas/`. Without it Room cannot validate migrations against a known
    // schema and any drift between the entity classes and the shipped SQLite tables surfaces only
    // as an IllegalStateException during startup on upgrade. The output location is configured in
    // app/build.gradle.kts via `ksp { arg("room.schemaLocation", ...) }`.
    exportSchema = true
)
@TypeConverters(AiDatabaseConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun circuitDao(): CircuitDao
    abstract fun transformerDao(): TransformerDao
    abstract fun powerSystemsDao(): PowerSystemsDao
    abstract fun projectDao(): ProjectDao
    abstract fun appStateDao(): AppStateDao
    abstract fun conduitDao(): ConduitDao
    abstract fun sldNodePositionDao(): SldNodePositionDao
    abstract fun approvalGrantDao(): ApprovalGrantDao
    abstract fun mutationExecutionReceiptDao(): MutationExecutionReceiptDao
    abstract fun busTieDao(): BusTieDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        var useTestExecutors = false

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val builder = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "electrical_engineering_db"
                ).addMigrations(
                    MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
                    MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9,
                    MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13,
                    MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17,
                    MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21,
                    MIGRATION_21_22, MIGRATION_22_23, MIGRATION_23_24,
                    MIGRATION_24_25, MIGRATION_25_26, MIGRATION_26_27
                )


                if (useTestExecutors) {
                    builder.setQueryExecutor { it.run() }
                    builder.setTransactionExecutor { it.run() }
                    builder.allowMainThreadQueries()
                }

                val instance = builder.build()
                INSTANCE = instance
                instance
            }
        }
    }
}

