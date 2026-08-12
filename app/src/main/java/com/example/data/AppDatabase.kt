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
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow



val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        try {
            db.execSQL("ALTER TABLE `circuits` ADD COLUMN `upsBacked` INTEGER NOT NULL DEFAULT 0")
        } catch (e: Exception) {
            // Column may already exist
            Log.e("AppDatabase", "Migration error: ${e.message}", e)
        }
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 1) Rebuild transformers table for composite primary key (projectId, transformerId)
        try {
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
        } catch (e: Exception) { Log.e("AppDatabase", "Migration error: ${e.message}", e) }

        try {
            db.execSQL("""
                INSERT OR REPLACE INTO `transformers_new` (
                    `projectId`, `transformerId`, `transformerName`, `kVA`, `voltage`, `percentZ`, `xr`, `txType`, `sourceType`, `status`, `sizeMode`, `manualKVA`, `demandFactor`, `diversity`, `growth`, `safety`, `vectorGroup`, `z0OverZ1`, `pf`
                )
                SELECT 
                    `projectId`, `transformerId`, `transformerName`, `kVA`, `voltage`, `percentZ`, `xr`, `txType`, `sourceType`, `status`, `sizeMode`, `manualKVA`, `demandFactor`, `diversity`, `growth`, `safety`, `vectorGroup`, `z0OverZ1`, `pf`
                FROM `transformers`
            """.trimIndent())
        } catch (e: Exception) { Log.e("AppDatabase", "Migration error: ${e.message}", e) }

        try {
            db.execSQL("DROP TABLE `transformers`")
        } catch (e: Exception) { Log.e("AppDatabase", "Migration error: ${e.message}", e) }
        try {
            db.execSQL("ALTER TABLE `transformers_new` RENAME TO `transformers`")
        } catch (e: Exception) { Log.e("AppDatabase", "Migration error: ${e.message}", e) }
        try {
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_transformers_projectId` ON `transformers`(`projectId`)")
        } catch (e: Exception) { Log.e("AppDatabase", "Migration error: ${e.message}", e) }
        try {
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_transformers_projectId_transformerId` ON `transformers`(`projectId`, `transformerId`)")
        } catch (e: Exception) { Log.e("AppDatabase", "Migration error: ${e.message}", e) }

        // 2) Rebuild generator_settings table
        try {
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
        } catch (e: Exception) { Log.e("AppDatabase", "Migration error: ${e.message}", e) }

        try {
            db.execSQL("""
                INSERT OR REPLACE INTO `generator_settings_new` (
                    `projectId`, `id`, `additionalEssentialLoadKW`, `largestMotorKW`, `motorStartMethod`, `motorPf`, `generatorPf`, `demandFactor`, `diversityFactor`, `stepLoadPct`, `oversizingFactor`, `enableManualOverride`, `manualSizeKVA`
                )
                SELECT 
                    'DEFAULT_PROJECT', `id`, `additionalEssentialLoadKW`, `largestMotorKW`, `motorStartMethod`, `motorPf`, `generatorPf`, `demandFactor`, `diversityFactor`, `stepLoadPct`, `oversizingFactor`, `enableManualOverride`, `manualSizeKVA`
                FROM `generator_settings`
            """.trimIndent())
        } catch (e: Exception) { Log.e("AppDatabase", "Migration error: ${e.message}", e) }

        try {
            db.execSQL("DROP TABLE `generator_settings`")
        } catch (e: Exception) { Log.e("AppDatabase", "Migration error: ${e.message}", e) }
        try {
            db.execSQL("ALTER TABLE `generator_settings_new` RENAME TO `generator_settings`")
        } catch (e: Exception) { Log.e("AppDatabase", "Migration error: ${e.message}", e) }

        // 3) Rebuild ats_settings table
        try {
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
        } catch (e: Exception) { Log.e("AppDatabase", "Migration error: ${e.message}", e) }

        try {
            db.execSQL("""
                INSERT OR REPLACE INTO `ats_settings_new` (
                    `projectId`, `id`, `systemVoltage`, `systemFrequency`, `numberOfSources`, `atsLocation`, `transferType`, `utilizationCategory`, `atsClass`, `loadCoverage`, `ambientTemp`, `designSafetyFactor`, `futureGrowthFactor`, `approvedManufacturer`, `mainsFailureDetectionDelay`, `generatorStartSignalDelay`, `generatorWarmUp`, `mainsReturnDetectionDelay`, `reTransferDelay`, `generatorCooldown`, `enclosureIpRating`
                )
                SELECT 
                    'DEFAULT_PROJECT', `id`, `systemVoltage`, `systemFrequency`, `numberOfSources`, `atsLocation`, `transferType`, `utilizationCategory`, `atsClass`, `loadCoverage`, `ambientTemp`, `designSafetyFactor`, `futureGrowthFactor`, `approvedManufacturer`, `mainsFailureDetectionDelay`, `generatorStartSignalDelay`, `generatorWarmUp`, `mainsReturnDetectionDelay`, `reTransferDelay`, `generatorCooldown`, `enclosureIpRating`
                FROM `ats_settings`
            """.trimIndent())
        } catch (e: Exception) { Log.e("AppDatabase", "Migration error: ${e.message}", e) }

        try {
            db.execSQL("DROP TABLE `ats_settings`")
        } catch (e: Exception) { Log.e("AppDatabase", "Migration error: ${e.message}", e) }
        try {
            db.execSQL("ALTER TABLE `ats_settings_new` RENAME TO `ats_settings`")
        } catch (e: Exception) { Log.e("AppDatabase", "Migration error: ${e.message}", e) }

        // 4) Rebuild ups_settings table
        try {
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
        } catch (e: Exception) { Log.e("AppDatabase", "Migration error: ${e.message}", e) }

        try {
            db.execSQL("""
                INSERT OR REPLACE INTO `ups_settings_new` (
                    `projectId`, `id`, `systemVoltage`, `systemFrequency`, `upsPf`, `upsEfficiency`, `batteryType`, `autonomyMinutes`, `batteryEodVoltage`, `ambientTemp`, `tempDeratingFactor`, `agingFactor`, `designMarginPct`
                )
                SELECT 
                    'DEFAULT_PROJECT', `id`, `systemVoltage`, `systemFrequency`, `upsPf`, `upsEfficiency`, `batteryType`, `autonomyMinutes`, `batteryEodVoltage`, `ambientTemp`, `tempDeratingFactor`, `agingFactor`, `designMarginPct`
                FROM `ups_settings`
            """.trimIndent())
        } catch (e: Exception) { Log.e("AppDatabase", "Migration error: ${e.message}", e) }

        try {
            db.execSQL("DROP TABLE `ups_settings`")
        } catch (e: Exception) { Log.e("AppDatabase", "Migration error: ${e.message}", e) }
        try {
            db.execSQL("ALTER TABLE `ups_settings_new` RENAME TO `ups_settings`")
        } catch (e: Exception) { Log.e("AppDatabase", "Migration error: ${e.message}", e) }

        // 5) Rebuild pf_global_settings table
        try {
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
        } catch (e: Exception) { Log.e("AppDatabase", "Migration error: ${e.message}", e) }

        try {
            db.execSQL("""
                INSERT OR REPLACE INTO `pf_global_settings_new` (
                    `projectId`, `id`, `targetPf`, `kvarStepSize`, `systemVoltage`, `annualOperatingHours`, `energyCost`, `lossReductionPct`, `unityToleranceKvar`
                )
                SELECT 
                    'DEFAULT_PROJECT', `id`, `targetPf`, `kvarStepSize`, `systemVoltage`, `annualOperatingHours`, `energyCost`, `lossReductionPct`, `unityToleranceKvar`
                FROM `pf_global_settings`
            """.trimIndent())
        } catch (e: Exception) { Log.e("AppDatabase", "Migration error: ${e.message}", e) }

        try {
            db.execSQL("DROP TABLE `pf_global_settings`")
        } catch (e: Exception) { Log.e("AppDatabase", "Migration error: ${e.message}", e) }
        try {
            db.execSQL("ALTER TABLE `pf_global_settings_new` RENAME TO `pf_global_settings`")
        } catch (e: Exception) { Log.e("AppDatabase", "Migration error: ${e.message}", e) }

        // 6) Rebuild pf_transformer_settings table
        try {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `pf_transformer_settings_new` (
                    `projectId` TEXT NOT NULL DEFAULT 'DEFAULT_PROJECT',
                    `transformerId` TEXT NOT NULL,
                    `harmonicFilter` TEXT NOT NULL,
                    `detuningReactor` TEXT NOT NULL,
                    PRIMARY KEY(`projectId`, `transformerId`)
                )
            """.trimIndent())
        } catch (e: Exception) { Log.e("AppDatabase", "Migration error: ${e.message}", e) }

        try {
            db.execSQL("""
                INSERT OR REPLACE INTO `pf_transformer_settings_new` (
                    `projectId`, `transformerId`, `harmonicFilter`, `detuningReactor`
                )
                SELECT 
                    'DEFAULT_PROJECT', `transformerId`, `harmonicFilter`, `detuningReactor`
                FROM `pf_transformer_settings`
            """.trimIndent())
        } catch (e: Exception) { Log.e("AppDatabase", "Migration error: ${e.message}", e) }

        try {
            db.execSQL("DROP TABLE `pf_transformer_settings`")
        } catch (e: Exception) { Log.e("AppDatabase", "Migration error: ${e.message}", e) }
        try {
            db.execSQL("ALTER TABLE `pf_transformer_settings_new` RENAME TO `pf_transformer_settings`")
        } catch (e: Exception) { Log.e("AppDatabase", "Migration error: ${e.message}", e) }
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 1) projects table + default project
        try {
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
        } catch (e: Exception) { Log.e("AppDatabase", "Migration error: ${e.message}", e) }

        try {
            db.execSQL("""
                INSERT OR IGNORE INTO `projects`
                    (`projectId`,`projectName`,`projectCode`,`clientName`,`createdAt`,`updatedAt`)
                VALUES
                    ('DEFAULT_PROJECT','Imported (El Rehab)','LEGACY','',
                     ${System.currentTimeMillis()}, ${System.currentTimeMillis()})
            """.trimIndent())
        } catch (e: Exception) { Log.e("AppDatabase", "Migration error: ${e.message}", e) }

        // 2) app_state table
        try {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `app_state` (
                    `id` INTEGER NOT NULL,
                    `activeProjectId` TEXT,
                    PRIMARY KEY(`id`)
                )
            """.trimIndent())
        } catch (e: Exception) { Log.e("AppDatabase", "Migration error: ${e.message}", e) }
        try {
            db.execSQL("INSERT OR REPLACE INTO `app_state` (`id`,`activeProjectId`) VALUES (0, NULL)")
        } catch (e: Exception) { Log.e("AppDatabase", "Migration error: ${e.message}", e) }

        // 3) new circuits columns
        try { db.execSQL("ALTER TABLE `circuits` ADD COLUMN `projectId` TEXT NOT NULL DEFAULT 'DEFAULT_PROJECT'") } catch (e: Exception) { Log.e("AppDatabase", "Migration error: ${e.message}", e) }
        try { db.execSQL("ALTER TABLE `circuits` ADD COLUMN `demandFactor` REAL NOT NULL DEFAULT 1.0") } catch (e: Exception) { Log.e("AppDatabase", "Migration error: ${e.message}", e) }
        try { db.execSQL("ALTER TABLE `circuits` ADD COLUMN `diversityGroup` TEXT") } catch (e: Exception) { Log.e("AppDatabase", "Migration error: ${e.message}", e) }
        try { db.execSQL("ALTER TABLE `circuits` ADD COLUMN `generatorIncluded` INTEGER NOT NULL DEFAULT 0") } catch (e: Exception) { Log.e("AppDatabase", "Migration error: ${e.message}", e) }
        try { db.execSQL("ALTER TABLE `circuits` ADD COLUMN `atsIncluded` INTEGER NOT NULL DEFAULT 0") } catch (e: Exception) { Log.e("AppDatabase", "Migration error: ${e.message}", e) }
        try { db.execSQL("ALTER TABLE `circuits` ADD COLUMN `loadNature` TEXT NOT NULL DEFAULT 'Static'") } catch (e: Exception) { Log.e("AppDatabase", "Migration error: ${e.message}", e) }
        try { db.execSQL("ALTER TABLE `circuits` ADD COLUMN `startingMethod` TEXT") } catch (e: Exception) { Log.e("AppDatabase", "Migration error: ${e.message}", e) }
        try { db.execSQL("ALTER TABLE `circuits` ADD COLUMN `startingCurrentMultiplier` REAL") } catch (e: Exception) { Log.e("AppDatabase", "Migration error: ${e.message}", e) }
        try { db.execSQL("ALTER TABLE `circuits` ADD COLUMN `active` INTEGER NOT NULL DEFAULT 1") } catch (e: Exception) { Log.e("AppDatabase", "Migration error: ${e.message}", e) }

        // 4) transformers
        try { db.execSQL("ALTER TABLE `transformers` ADD COLUMN `projectId` TEXT NOT NULL DEFAULT 'DEFAULT_PROJECT'") } catch (e: Exception) { Log.e("AppDatabase", "Migration error: ${e.message}", e) }

        // 5) indexes
        try { db.execSQL("CREATE INDEX IF NOT EXISTS `index_circuits_projectId` ON `circuits`(`projectId`)") } catch (e: Exception) { Log.e("AppDatabase", "Migration error: ${e.message}", e) }
        try { db.execSQL("CREATE INDEX IF NOT EXISTS `index_transformers_projectId` ON `transformers`(`projectId`)") } catch (e: Exception) { Log.e("AppDatabase", "Migration error: ${e.message}", e) }
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        try {
            db.execSQL("ALTER TABLE circuits ADD COLUMN upsBacked INTEGER NOT NULL DEFAULT 0")
        } catch (e: Exception) { Log.e("AppDatabase", "Migration error: ${e.message}", e) }
        
        try {
            db.execSQL("CREATE TABLE IF NOT EXISTS `generator_settings` (`id` INTEGER NOT NULL, `additionalEssentialLoadKW` REAL NOT NULL, `largestMotorKW` REAL NOT NULL, `motorStartMethod` TEXT NOT NULL, `motorPf` REAL NOT NULL, `generatorPf` REAL NOT NULL, `demandFactor` REAL NOT NULL, `diversityFactor` REAL NOT NULL, `stepLoadPct` REAL NOT NULL, `oversizingFactor` REAL NOT NULL, `enableManualOverride` INTEGER NOT NULL, `manualSizeKVA` REAL, PRIMARY KEY(`id`))")
        } catch (e: Exception) { Log.e("AppDatabase", "Migration error: ${e.message}", e) }
        
        try {
            db.execSQL("CREATE TABLE IF NOT EXISTS `ats_settings` (`id` INTEGER NOT NULL, `systemVoltage` INTEGER NOT NULL, `systemFrequency` INTEGER NOT NULL, `numberOfSources` INTEGER NOT NULL, `atsLocation` TEXT NOT NULL, `transferType` TEXT NOT NULL, `utilizationCategory` TEXT NOT NULL, `atsClass` TEXT NOT NULL, `loadCoverage` TEXT NOT NULL, `ambientTemp` INTEGER NOT NULL, `designSafetyFactor` REAL NOT NULL, `futureGrowthFactor` REAL NOT NULL, `approvedManufacturer` TEXT NOT NULL, `mainsFailureDetectionDelay` INTEGER NOT NULL, `generatorStartSignalDelay` INTEGER NOT NULL, `generatorWarmUp` INTEGER NOT NULL, `mainsReturnDetectionDelay` INTEGER NOT NULL, `reTransferDelay` INTEGER NOT NULL, `generatorCooldown` INTEGER NOT NULL, `enclosureIpRating` TEXT NOT NULL, PRIMARY KEY(`id`))")
        } catch (e: Exception) { Log.e("AppDatabase", "Migration error: ${e.message}", e) }
        
        try {
            db.execSQL("CREATE TABLE IF NOT EXISTS `ups_settings` (`id` INTEGER NOT NULL, `systemVoltage` INTEGER NOT NULL, `systemFrequency` INTEGER NOT NULL, `upsPf` REAL NOT NULL, `upsEfficiency` REAL NOT NULL, `batteryType` TEXT NOT NULL, `autonomyMinutes` INTEGER NOT NULL, `batteryEodVoltage` REAL NOT NULL, `ambientTemp` INTEGER NOT NULL, `tempDeratingFactor` REAL NOT NULL, `agingFactor` REAL NOT NULL, `designMarginPct` REAL NOT NULL, PRIMARY KEY(`id`))")
        } catch (e: Exception) { Log.e("AppDatabase", "Migration error: ${e.message}", e) }
        
        try {
            db.execSQL("CREATE TABLE IF NOT EXISTS `pf_global_settings` (`id` INTEGER NOT NULL, `targetPf` REAL NOT NULL, `kvarStepSize` REAL NOT NULL, `systemVoltage` INTEGER NOT NULL, `annualOperatingHours` INTEGER NOT NULL, `energyCost` REAL NOT NULL, `lossReductionPct` REAL NOT NULL, `unityToleranceKvar` REAL NOT NULL, PRIMARY KEY(`id`))")
        } catch (e: Exception) { Log.e("AppDatabase", "Migration error: ${e.message}", e) }
        
        try {
            db.execSQL("CREATE TABLE IF NOT EXISTS `pf_transformer_settings` (`transformerId` TEXT NOT NULL, `harmonicFilter` TEXT NOT NULL, `detuningReactor` TEXT NOT NULL, PRIMARY KEY(`transformerId`))")
        } catch (e: Exception) { Log.e("AppDatabase", "Migration error: ${e.message}", e) }
    }
}





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

    @Query("SELECT * FROM circuits WHERE circuitName = :name LIMIT 1")
    suspend fun getCircuitByName(name: String): CircuitEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCircuits(circuits: List<CircuitEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCircuit(circuit: CircuitEntity): Long

    @Update
    suspend fun updateCircuit(circuit: CircuitEntity)

    @Delete
    suspend fun deleteCircuit(circuit: CircuitEntity)

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
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveGeneratorSettings(settings: GeneratorEntity)
    
    // ATS
    @Query("SELECT * FROM ats_settings WHERE projectId = :projectId AND id = 1")
    fun getAtsSettings(projectId: String): Flow<AtsEntity?>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveAtsSettings(settings: AtsEntity)
    
    // UPS
    @Query("SELECT * FROM ups_settings WHERE projectId = :projectId AND id = 1")
    fun getUpsSettings(projectId: String): Flow<UpsEntity?>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveUpsSettings(settings: UpsEntity)
    
    // PF Global
    @Query("SELECT * FROM pf_global_settings WHERE projectId = :projectId AND id = 1")
    fun getPfGlobalSettings(projectId: String): Flow<PfGlobalEntity?>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePfGlobalSettings(settings: PfGlobalEntity)
    
    // PF Transformer
    @Query("SELECT * FROM pf_transformer_settings WHERE projectId = :projectId")
    fun getAllPfTransformerSettings(projectId: String): Flow<List<PfTransformerEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePfTransformerSettings(settings: PfTransformerEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveAllPfTransformerSettings(settings: List<PfTransformerEntity>)

    // Earthing
    @Query("SELECT * FROM earthing_settings WHERE projectId = :projectId AND id = 1")
    fun getEarthingSettings(projectId: String): Flow<EarthingEntity?>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveEarthingSettings(settings: EarthingEntity)
}

@Dao
interface ConduitDao {
    @Query("SELECT * FROM conduit_settings WHERE projectId = :projectId")
    fun getConduitSettingsForProject(projectId: String): Flow<List<ConduitSettingsEntity>>

    @Query("SELECT * FROM conduit_settings WHERE projectId = :projectId AND circuitId = :circuitId")
    suspend fun getConduitSetting(projectId: String, circuitId: Int): ConduitSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveConduitSetting(setting: ConduitSettingsEntity)
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
        try { db.execSQL("ALTER TABLE `circuits` ADD COLUMN `panelType` TEXT") } catch (e: Exception) { Log.e("AppDatabase", "Migration error: ${e.message}", e) }
        try { db.execSQL("ALTER TABLE `circuits` ADD COLUMN `parentId` INTEGER") } catch (e: Exception) { Log.e("AppDatabase", "Migration error: ${e.message}", e) }
        try { db.execSQL("ALTER TABLE `circuits` ADD COLUMN `level` INTEGER") } catch (e: Exception) { Log.e("AppDatabase", "Migration error: ${e.message}", e) }
        try { db.execSQL("ALTER TABLE `circuits` ADD COLUMN `sourceType` TEXT") } catch (e: Exception) { Log.e("AppDatabase", "Migration error: ${e.message}", e) }
    }
}

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        try { db.execSQL("ALTER TABLE `ats_settings` ADD COLUMN `normalSourceId` TEXT") } catch (e: Exception) { Log.e("AppDatabase", "Migration error: ${e.message}", e) }
        try { db.execSQL("ALTER TABLE `ats_settings` ADD COLUMN `emergencySourceId` TEXT") } catch (e: Exception) { Log.e("AppDatabase", "Migration error: ${e.message}", e) }
    }
}

val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        try { db.execSQL("ALTER TABLE `ats_settings` ADD COLUMN `outputBoardId` INTEGER") } catch (e: Exception) { Log.e("AppDatabase", "Migration error: ${e.message}", e) }
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
        try {
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
        } catch (e: Exception) {
            Log.e("AppDatabase", "Migration error: ${e.message}", e)
        }
    }
}

val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        try {
            db.execSQL("ALTER TABLE `circuits` ADD COLUMN `directLoadConfirmed` INTEGER DEFAULT NULL")
        } catch (e: Exception) {
            Log.e("AppDatabase", "Migration error: ${e.message}", e)
        }
    }
}

val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        try {
            db.execSQL("ALTER TABLE `projects` ADD COLUMN `defaultPhaseImbalanceLimitPct` REAL NOT NULL DEFAULT 20.0")
        } catch (e: Exception) {
            Log.e("AppDatabase", "Migration error: ${e.message}", e)
        }
        try {
            db.execSQL("ALTER TABLE `circuits` ADD COLUMN `phaseImbalanceLimitOverridePct` REAL DEFAULT NULL")
        } catch (e: Exception) {
            Log.e("AppDatabase", "Migration error: ${e.message}", e)
        }
    }
}

val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        try {
            db.execSQL("ALTER TABLE `circuits` ADD COLUMN `loadCategory` TEXT DEFAULT NULL")
        } catch (e: Exception) {
            Log.e("AppDatabase", "Migration error: ${e.message}", e)
        }
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
            try {
                db.execSQL(sql)
            } catch (e: Exception) {
                Log.e("AppDatabase", "Migration error: ${e.message}", e)
            }
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
            try {
                db.execSQL(sql)
            } catch (e: Exception) {
                Log.e("AppDatabase", "Migration error: ${e.message}", e)
            }
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
            try {
                db.execSQL(sql)
            } catch (e: Exception) {
                Log.e("AppDatabase", "Migration error: ${e.message}", e)
            }
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
            try {
                db.execSQL(sql)
            } catch (e: Exception) {
                Log.e("AppDatabase", "Migration 18->19 statement error: ${e.message}", e)
            }
        }
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
        SldNodePositionEntity::class
    ],
    version = 19,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun circuitDao(): CircuitDao
    abstract fun transformerDao(): TransformerDao
    abstract fun powerSystemsDao(): PowerSystemsDao
    abstract fun projectDao(): ProjectDao
    abstract fun appStateDao(): AppStateDao
    abstract fun conduitDao(): ConduitDao
    abstract fun sldNodePositionDao(): SldNodePositionDao

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
                    MIGRATION_17_18, MIGRATION_18_19
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

