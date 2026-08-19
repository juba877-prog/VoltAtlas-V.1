package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.ai.context.freshness.contracts.*
import com.example.ai.context.provider.*
import com.example.ai.context.revision.ProjectRevisionProvider
import com.example.ai.context.revision.ProjectRevisionSnapshot
import com.example.data.*
import com.example.data.catalog.CatalogRepository
import com.example.engine.coordinator.EngineeringCalculationCoordinator
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.lang.reflect.Modifier

/**
 * Unit tests for ProjectContextProvider (Sub-Phase 3F Read Facade).
 *
 * Validates:
 * 1. Basic composition: circuit & transformer context snapshots compose correctly.
 * 2. Freshness integration: CURRENT, STALE, and MISSING freshness states are faithfully reflected.
 * 3. Pure read behavior: reading context NEVER triggers recalculation or populates cache.
 * 4. Validation snapshot: derived from real topology & sizing findings, not hard-coded "NORMAL".
 * 5. Isolation: zero persistent stores, zero new Room tables/DAOs, zero mutations.
 * 6. Architectural purity: pure DTOs without Compose, ViewModel, or Firebase dependencies.
 */
@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE)
class ProjectContextProviderTest {

    companion object {
        private fun findAssetFile(filename: String): File {
            val candidates = listOf(
                File("app/src/main/assets/$filename"),
                File("src/main/assets/$filename"),
                File("../app/src/main/assets/$filename")
            )
            return candidates.firstOrNull { it.exists() }
                ?: throw java.io.FileNotFoundException("Asset file not found: $filename")
        }

        @BeforeClass
        @JvmStatic
        fun loadCatalogData() {
            val repo = CatalogRepository()
            val cableCatalogJson = findAssetFile("cable_catalog.json").readText()
            val cableTypesDataJson = findAssetFile("cable_types_data.json").readText()
            val breakerCatalogJson = findAssetFile("breaker_catalog.json").readText()
            val accessoryCatalogJson = findAssetFile("accessory_catalog.json").readText()
            val deratingDataJson = findAssetFile("derating_data.json").readText()
            val transformersSeedJson = findAssetFile("transformers_seed.json").readText()

            repo.loadAllFromStrings(
                cableCatalogJson = cableCatalogJson,
                cableTypesDataJson = cableTypesDataJson,
                breakerCatalogJson = breakerCatalogJson,
                accessoryCatalogJson = accessoryCatalogJson,
                deratingDataJson = deratingDataJson,
                transformersSeedJson = transformersSeedJson
            )

            val cableTypesJson = findAssetFile("cable_types.json").readText()
            val ctArray = JSONArray(cableTypesJson)
            val ctList = mutableListOf<CableType>()
            for (i in 0 until ctArray.length()) {
                val obj = ctArray.getJSONObject(i)
                ctList.add(
                    CableType(
                        code = obj.getInt("code"),
                        cableType = obj.getString("cableType"),
                        conductor = obj.getString("conductor"),
                        insulation = obj.getString("insulation"),
                        armour = obj.getString("armour"),
                        cores = obj.getString("cores"),
                        voltage = obj.getString("voltage"),
                        standard = obj.getString("standard"),
                        rColumn = obj.getString("rColumn")
                    )
                )
            }
            Catalog.cableTypes = ctList

            val dataSheetJson = findAssetFile("data_sheet.json").readText()
            val dsArray = JSONArray(dataSheetJson)
            val dsList = mutableListOf<DataSheetEntry>()
            for (i in 0 until dsArray.length()) {
                val obj = dsArray.getJSONObject(i)
                dsList.add(
                    DataSheetEntry(
                        size = obj.getDouble("size"),
                        R_Cu70 = obj.getDouble("R_Cu70"),
                        X = obj.getDouble("X"),
                        R_Al70 = obj.getDouble("R_Al70"),
                        R_Cu90 = obj.getDouble("R_Cu90"),
                        R_Al90 = obj.getDouble("R_Al90")
                    )
                )
            }
            Catalog.dataSheet = dsList

            val ccArray = JSONArray(cableCatalogJson)
            val ccList = mutableListOf<CableCatalogEntry>()
            for (i in 0 until ccArray.length()) {
                val obj = ccArray.getJSONObject(i)
                ccList.add(
                    CableCatalogEntry(
                        mfg = obj.optString("mfg", obj.optString("Manufacturer", "Generic")),
                        typeCode = com.example.data.catalog.BreakerParser.parsePoles(obj.opt("typeCode") ?: obj.opt("TypeCode") ?: obj.opt("Code")),
                        size = com.example.data.catalog.BreakerParser.parseDouble(obj.opt("size") ?: obj.opt("SZ (mm²)") ?: obj.opt("Size")),
                        model = obj.optString("model", obj.optString("Model", "")),
                        gnd = com.example.data.catalog.BreakerParser.parseDouble(obj.opt("gnd") ?: obj.opt("GND")),
                        duct = com.example.data.catalog.BreakerParser.parseDouble(obj.opt("duct") ?: obj.opt("DUCT")),
                        air = com.example.data.catalog.BreakerParser.parseDouble(obj.opt("air") ?: obj.opt("AIR")),
                        od = com.example.data.catalog.BreakerParser.parseDouble(obj.opt("od") ?: obj.opt("OD")),
                        wt = com.example.data.catalog.BreakerParser.parseDouble(obj.opt("wt") ?: obj.opt("WT"))
                    )
                )
            }
            Catalog.cableCatalog = ccList

            val brkArray = JSONArray(breakerCatalogJson)
            val brkList = mutableListOf<BreakerEntry>()
            for (i in 0 until brkArray.length()) {
                val obj = brkArray.getJSONObject(i)
                brkList.add(
                    BreakerEntry(
                        mfg = obj.optString("mfg", obj.optString("Source", "Generic")),
                        type = obj.optString("type", "MCCB"),
                        series = obj.optString("series", obj.optString("Series", "")),
                        frame = if (obj.has("frameA")) obj.optString("frameA", "") else obj.optString("frame", obj.optString("Frame", "")),
                        version = obj.optString("version", obj.optString("Version", "")),
                        rating_A = com.example.data.catalog.BreakerParser.parseRating(obj.opt("ratingA") ?: obj.opt("rating_A") ?: obj.opt("In (A)")),
                        poles = com.example.data.catalog.BreakerParser.parsePoles(obj.opt("poles") ?: obj.opt("Poles")),
                        icu_kA = com.example.data.catalog.BreakerParser.parseDouble(obj.opt("icuKa") ?: obj.opt("icu_kA") ?: obj.opt("Icu@415V (kA)")),
                        ics_kA = com.example.data.catalog.BreakerParser.parseDouble(obj.opt("icsKa") ?: obj.opt("ics_kA") ?: obj.opt("Ics@415V (kA)")),
                        icm_kA = com.example.data.catalog.BreakerParser.parseDouble(obj.opt("icmKa") ?: obj.opt("icm_kA") ?: obj.opt("Icm@415V (kA)")),
                        tripUnit = obj.optString("tripUnit", obj.optString("Trip Unit", "Thermal-Magnetic"))
                    )
                )
            }
            Catalog.breakers = brkList

            Catalog.deratingData = JSONObject(deratingDataJson)
        }
    }

    private lateinit var database: AppDatabase
    private lateinit var repository: EngineeringRepository
    private lateinit var provider: ProjectContextProvider
    private val coordinator = EngineeringCalculationCoordinator
    private val projectId = "PROJ-FACADE-TEST"

    @Before
    fun setUp() = runBlocking {
        AppDatabase.useTestExecutors = true
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = EngineeringRepository(database)
        coordinator.projectRevisionProvider = repository
        coordinator.clearCache()
        // Create base test project directly in projectDao
        database.projectDao().upsertProject(
            ProjectEntity(
                projectId = projectId,
                projectName = "Facade Test Project",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                revision = 1L
            )
        )

        provider = ProjectContextProviderImpl(
            repository = repository,
            coordinator = coordinator,
            revisionProvider = repository
        )
    }

    @After
    fun tearDown() {
        AppDatabase.useTestExecutors = false
        coordinator.clearCache()
        coordinator.freshnessRegistry.clearAll()
        if (::database.isInitialized && database.isOpen) {
            database.close()
        }
    }

    // =========================================================================
    // 1. Basic Composition Tests
    // =========================================================================

    @Test
    fun circuitContext_returnsNullForMissingCircuit() = runBlocking {
        val result = provider.getCircuitContext(projectId, 99999)
        assertNull("Non-existent circuit must return null snapshot", result)
    }

    @Test
    fun circuitContext_returnsConfiguredInputsForExistingCircuit() = runBlocking {
        val circuit = CircuitEntity(
            id = 101,
            projectId = projectId,
            circuitName = "Main-Feeder-101",
            loadKVA = 50.0,
            voltageV = 400,
            phases = "3P+N",
            pf = 0.85,
            length_m = 45.0,
            cableType = "Cu/XLPE/PVC - 4C",
            manufacturer = "Elsewedy",
            installation = "Laid in Free Air",
            fedFrom = "Grid"
        )
        repository.circuitDao.insertCircuit(circuit)

        val snapshot = provider.getCircuitContext(projectId, 101)
        assertNotNull("Snapshot must not be null for existing circuit", snapshot)
        assertEquals(projectId, snapshot!!.projectId)
        assertEquals(101, snapshot.circuitId)
        assertEquals("Main-Feeder-101", snapshot.circuitName)
        assertEquals(50.0, snapshot.inputs.loadKVA, 0.001)
        assertEquals(400, snapshot.inputs.voltageV)
        assertEquals(45.0, snapshot.inputs.length_m, 0.001)
        assertEquals("Elsewedy", snapshot.inputs.manufacturer)
        assertEquals("Cu/XLPE/PVC - 4C", snapshot.inputs.cableType)
    }

    @Test
    fun circuitContext_includesCachedSizingResult_whenCoordinatorHasCalculated() = runBlocking {
        val circuit = CircuitEntity(
            id = 102,
            projectId = projectId,
            circuitName = "Calculated-Feeder-102",
            loadKVA = 45.0,
            voltageV = 400,
            phases = "3P+N",
            pf = 0.85,
            length_m = 30.0,
            cableType = "Cu/XLPE/PVC - 4C",
            manufacturer = "Elsewedy",
            installation = "Laid in Free Air",
            fedFrom = "Grid"
        )
        repository.circuitDao.insertCircuit(circuit)

        // Run calculation through coordinator
        val sizingRes = coordinator.calculateCableSizing(circuit, listOf(circuit))
        assertTrue("Calculation must pass", sizingRes.status.contains("PASS"))

        val snapshot = provider.getCircuitContext(projectId, 102)
        assertNotNull("Snapshot must not be null", snapshot)
        assertNotNull("Cached sizing result must be included", snapshot!!.cableSizingResult)
        assertEquals(sizingRes.cableSize_mm2, snapshot.cableSizingResult!!.cableSize_mm2)
        assertEquals(sizingRes.deviceSize_A, snapshot.cableSizingResult!!.deviceSize_A, 0.001)
    }

    @Test
    fun circuitContext_sizingResultIsNull_whenNeverCalculated_andFreshnessReportsMissing() = runBlocking {
        val circuit = CircuitEntity(
            id = 103,
            projectId = projectId,
            circuitName = "Uncalculated-Feeder-103",
            loadKVA = 30.0,
            voltageV = 400,
            fedFrom = "Grid"
        )
        repository.circuitDao.insertCircuit(circuit)

        val snapshot = provider.getCircuitContext(projectId, 103)
        assertNotNull(snapshot)
        assertNull("Sizing result must be null when never calculated", snapshot!!.cableSizingResult)
        assertEquals("Freshness state must be MISSING", CalculationState.MISSING, snapshot.cableSizingFreshness.state)
    }

    @Test
    fun transformerContext_returnsNullForMissingTransformer() = runBlocking {
        val result = provider.getTransformerContext(projectId, "TX-MISSING-99")
        assertNull("Non-existent transformer must return null", result)
    }

    @Test
    fun transformerContext_includesCachedTransformerResult_whenCalculated() = runBlocking {
        val tx = TransformerEntity(
            projectId = projectId,
            transformerId = "TX-01",
            transformerName = "Main Substation TX-01",
            kVA = 1000.0,
            voltage = 400,
            percentZ = 5.0,
            xr = 5.0
        )
        repository.transformerDao.insertTransformer(tx)

        val circuit = CircuitEntity(
            id = 104,
            projectId = projectId,
            circuitName = "TX-Fed-Feeder",
            loadKVA = 300.0,
            voltageV = 400,
            transformerId = "TX-01",
            fedFrom = "Transformer"
        )
        repository.circuitDao.insertCircuit(circuit)

        // Calculate transformer load via coordinator
        val txRes = coordinator.calculateTransformer(tx, listOf(circuit))
        assertNotNull(txRes)

        val snapshot = provider.getTransformerContext(projectId, "TX-01")
        assertNotNull("Transformer snapshot must not be null", snapshot)
        assertEquals("TX-01", snapshot!!.transformerId)
        assertEquals("Main Substation TX-01", snapshot.transformerName)
        assertEquals(1000.0, snapshot.inputs.kVA, 0.001)
        assertNotNull("Transformer result must be cached and included", snapshot.transformerResult)
        assertEquals(1, snapshot.assignedCircuitCount)
    }

    // =========================================================================
    // 2. Freshness Integration Tests
    // =========================================================================

    @Test
    fun circuitContext_freshnessReflectsCurrent_afterMatchingRecordedFingerprint() = runBlocking {
        val circuit = CircuitEntity(
            id = 105,
            projectId = projectId,
            circuitName = "Freshness-Current-105",
            loadKVA = 60.0,
            voltageV = 400,
            phases = "3P+N",
            pf = 0.85,
            length_m = 50.0,
            cableType = "Cu/XLPE/PVC - 4C",
            manufacturer = "Elsewedy",
            installation = "Laid in Free Air",
            fedFrom = "Grid"
        )
        repository.circuitDao.insertCircuit(circuit)

        // Coordinator calculation records freshness metadata
        coordinator.calculateCableSizing(circuit, listOf(circuit))

        val snapshot = provider.getCircuitContext(projectId, 105)
        assertNotNull(snapshot)
        assertEquals("Calculation state must be CURRENT", CalculationState.CURRENT, snapshot!!.cableSizingFreshness.state)
    }

    @Test
    fun circuitContext_freshnessReflectsStale_afterRelevantInputChange() = runBlocking {
        val circuit = CircuitEntity(
            id = 106,
            projectId = projectId,
            circuitName = "Freshness-Stale-106",
            loadKVA = 50.0,
            voltageV = 400,
            phases = "3P+N",
            pf = 0.85,
            length_m = 50.0,
            cableType = "Cu/XLPE/PVC - 4C",
            manufacturer = "Elsewedy",
            installation = "Laid in Free Air",
            fedFrom = "Grid"
        )
        repository.circuitDao.insertCircuit(circuit)

        // Calculate and record metadata for initial state
        coordinator.calculateCableSizing(circuit, listOf(circuit))

        // Modify input in repository and advance project revision
        val mutatedCircuit = circuit.copy(loadKVA = 95.0)
        repository.updateCircuit(mutatedCircuit)

        val snapshot = provider.getCircuitContext(projectId, 106)
        assertNotNull(snapshot)
        assertEquals("Calculation state must be STALE after load modification", CalculationState.STALE, snapshot!!.cableSizingFreshness.state)
        assertTrue("Changed components must include DIRECT_INPUT", snapshot.cableSizingFreshness.changedComponents.contains(ChangedComponent.DIRECT_INPUT))
        assertTrue("Changed field paths must include directInputs", snapshot.cableSizingFreshness.changedFieldPaths.contains("directInputs"))
    }

    @Test
    fun circuitContext_doesNotTriggerNewCalculation_whenReadingContext() = runBlocking {
        val circuit = CircuitEntity(
            id = 107,
            projectId = projectId,
            circuitName = "No-Lazy-Calc-107",
            loadKVA = 40.0,
            voltageV = 400,
            fedFrom = "Grid"
        )
        repository.circuitDao.insertCircuit(circuit)

        // Ensure cache is completely empty
        coordinator.clearCache()
        assertNull("Cache must be empty prior to read", coordinator.getCachedSizingResult(107))

        // Read context
        val snapshot = provider.getCircuitContext(projectId, 107)
        assertNotNull(snapshot)

        // Verify reading context did NOT trigger calculation or populate cache
        assertNull("Reading context must NOT populate cache entry", coordinator.getCachedSizingResult(107))
        assertNull("Snapshot sizing result must remain null", snapshot!!.cableSizingResult)
    }

    // =========================================================================
    // 3. Validation Snapshot Tests
    // =========================================================================

    @Test
    fun validationSnapshot_reflectsRealIssues_notHardCodedNormal() = runBlocking {
        // Create an ungrounded, orphan circuit fed from a non-existent parent board
        val brokenCircuit = CircuitEntity(
            id = 108,
            projectId = projectId,
            circuitName = "Broken-Feeder",
            loadKVA = 50.0,
            voltageV = 400,
            fedFrom = "NON_EXISTENT_PANEL"
        )
        repository.circuitDao.insertCircuit(brokenCircuit)

        val snapshot = provider.getValidationSnapshot(projectId)
        assertNotNull(snapshot)
        assertEquals(projectId, snapshot.projectId)
        assertNotEquals("Overall status must reflect real issues, not static normal", "100% COMPLIANT / PASS", snapshot.overallStatus)
        assertTrue("Issues count must be > 0", snapshot.criticalFailureCount > 0 || snapshot.reviewRequiredCount > 0 || snapshot.warningCount > 0)
        assertTrue("Top issues must not be empty", snapshot.topIssues.isNotEmpty())
    }

    @Test
    fun validationSnapshot_countsMatchUnderlyingValidatorData() = runBlocking {
        val tx = TransformerEntity(
            projectId = projectId,
            transformerId = "TX-01",
            transformerName = "Main Substation",
            kVA = 1000.0,
            voltage = 400
        )
        repository.transformerDao.insertTransformer(tx)

        val validCircuit = CircuitEntity(
            id = 109,
            projectId = projectId,
            circuitName = "Compliant-Feeder-109",
            loadKVA = 45.0,
            voltageV = 400,
            phases = "3P+N",
            pf = 0.85,
            length_m = 30.0,
            cableType = "Cu/XLPE/PVC - 4C",
            manufacturer = "Elsewedy",
            installation = "Laid in Free Air",
            transformerId = "TX-01",
            fedFrom = "Transformer"
        )
        repository.circuitDao.insertCircuit(validCircuit)

        coordinator.calculateCableSizing(validCircuit, listOf(validCircuit), listOf(tx))
        coordinator.calculateTransformer(tx, listOf(validCircuit))

        val snapshot = provider.getValidationSnapshot(projectId)
        assertEquals(0, snapshot.criticalFailureCount)
        assertTrue("Status must indicate compliance", snapshot.overallStatus.contains("COMPLIANT") || snapshot.overallStatus.contains("PASS"))
    }

    @Test
    fun validationSnapshot_isBoundedInSize() = runBlocking {
        // Insert 25 circuits with invalid parent references to generate many topology issues
        for (i in 200..225) {
            val circ = CircuitEntity(
                id = i,
                projectId = projectId,
                circuitName = "Orphan-Circuit-$i",
                loadKVA = 10.0,
                voltageV = 400,
                fedFrom = "GHOST_BOARD_$i"
            )
            repository.circuitDao.insertCircuit(circ)
        }

        val snapshot = provider.getValidationSnapshot(projectId)
        assertTrue("Top issues list must be capped at 20", snapshot.topIssues.size <= 20)
    }

    // =========================================================================
    // 4. Isolation & Purity Tests
    // =========================================================================

    @Test
    fun providerAddsNoNewRoomEntityOrTable() {
        val providerAnnotations = ProjectContextProviderImpl::class.java.annotations.map { it.annotationClass.java.simpleName }
        assertFalse("ProjectContextProviderImpl must not be annotated with @Entity", providerAnnotations.contains("Entity"))
        assertFalse("ProjectContextProviderImpl must not be annotated with @Dao", providerAnnotations.contains("Dao"))

        val dtoList = listOf(
            CircuitContextSnapshot::class.java,
            TransformerContextSnapshot::class.java,
            ProjectValidationSnapshot::class.java
        )
        for (dto in dtoList) {
            val annos = dto.annotations.map { it.annotationClass.java.simpleName }
            assertFalse("DTO ${dto.simpleName} must not be a Room @Entity", annos.contains("Entity"))
        }
    }

    @Test
    fun repeatedReads_returnConsistentSnapshotWithoutSideEffects() = runBlocking {
        val circuit = CircuitEntity(
            id = 110,
            projectId = projectId,
            circuitName = "Idempotent-Feeder-110",
            loadKVA = 35.0,
            voltageV = 400,
            fedFrom = "Grid"
        )
        repository.circuitDao.insertCircuit(circuit)

        val snap1 = provider.getCircuitContext(projectId, 110)
        val snap2 = provider.getCircuitContext(projectId, 110)

        assertEquals("Repeated reads must return identical circuit ID", snap1?.circuitId, snap2?.circuitId)
        assertEquals("Repeated reads must return identical load", snap1?.inputs?.loadKVA, snap2?.inputs?.loadKVA)
        assertEquals("Repeated reads must return identical freshness state", snap1?.cableSizingFreshness?.state, snap2?.cableSizingFreshness?.state)
    }

    @Test
    fun projectContextProviderContracts_haveNoComposeViewModelFirebaseOrEngineDependency() {
        val dtoClasses = listOf(
            CircuitConfiguredInputs::class.java,
            CircuitContextSnapshot::class.java,
            TransformerConfiguredInputs::class.java,
            TransformerContextSnapshot::class.java,
            ValidationIssueItem::class.java,
            ProjectValidationSnapshot::class.java,
            ProjectContextProvider::class.java
        )

        for (cls in dtoClasses) {
            for (field in cls.declaredFields) {
                val typeName = field.type.name
                assertFalse("DTO field '${field.name}' in $cls must not depend on ViewModel", typeName.contains("ViewModel"))
                assertFalse("DTO field '${field.name}' in $cls must not depend on Compose", typeName.contains("androidx.compose"))
                assertFalse("DTO field '${field.name}' in $cls must not depend on Firebase", typeName.contains("firebase", ignoreCase = true))
            }
        }
    }

    @Test
    fun providerExposesNoMutationMethods() {
        val methods = ProjectContextProvider::class.java.methods
        val forbiddenPrefixes = listOf("set", "save", "update", "delete", "insert", "record", "clear", "write", "mutate", "put")

        for (method in methods) {
            val name = method.name.lowercase()
            for (prefix in forbiddenPrefixes) {
                assertFalse(
                    "ProjectContextProvider method '${method.name}' violates read-only invariant (matches '$prefix')",
                    name.startsWith(prefix)
                )
            }
        }
    }
}
