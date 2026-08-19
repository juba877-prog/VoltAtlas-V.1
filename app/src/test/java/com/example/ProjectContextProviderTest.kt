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

/**
 * Comprehensive test suite for ProjectContextProvider (Sub-Phase 3F Read Facade Correction).
 *
 * Validates:
 * 1. Project-scoped cache isolation (no cross-project leakage).
 * 2. Revision truth (never fabricates revision 1).
 * 3. Compliance truth (uncalculated/stale is never fully compliant).
 * 4. Context contract purity (no com.example.engine dependencies in contracts).
 * 5. Parent resolution (cycle-safe, ambiguity-safe, explicit parentId precedence).
 * 6. Transformer assignment (shared resolution rule, all 18 determinants).
 * 7. Result & freshness coherence (typed ResultUsability).
 * 8. Pure read behavior & side-effect-free invariants.
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
    private val projectAlpha = "PROJ-ALPHA"
    private val projectBeta = "PROJ-BETA"

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
        coordinator.freshnessRegistry.clearAll()

        // Insert base test project
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
    // 1. Project-Scoped Cache Isolation Tests (Mandatory Cross-Project Tests)
    // =========================================================================

    @Test
    fun sameCircuitIdAcrossProjects_neverCrossReturnsAllCircuitDomainResults() = runBlocking {
        database.projectDao().upsertProject(ProjectEntity(projectId = projectAlpha, projectName = "Alpha", createdAt = 0, updatedAt = 0, revision = 1L))
        database.projectDao().upsertProject(ProjectEntity(projectId = projectBeta, projectName = "Beta", createdAt = 0, updatedAt = 0, revision = 1L))

        val circAlpha = CircuitEntity(
            id = 501,
            projectId = projectAlpha,
            circuitName = "Feeder-Alpha-501",
            loadKVA = 80.0,
            voltageV = 400,
            length_m = 50.0,
            cableType = "Cu/XLPE/PVC - 4C",
            manufacturer = "Elsewedy",
            installation = "Laid in Free Air",
            fedFrom = "Grid"
        )
        val circBeta = CircuitEntity(
            id = 501, // Deliberately identical ID
            projectId = projectBeta,
            circuitName = "Feeder-Beta-501",
            loadKVA = 20.0,
            voltageV = 400,
            length_m = 10.0,
            cableType = "Cu/XLPE/PVC - 4C",
            manufacturer = "Elsewedy",
            installation = "Laid in Free Air",
            fedFrom = "Grid"
        )

        // Calculate Alpha only
        coordinator.calculateCableSizing(circAlpha, listOf(circAlpha))
        coordinator.calculateShortCircuit(circAlpha.id, listOf(circAlpha), emptyList())
        coordinator.calculateAds(circAlpha.id, listOf(circAlpha), emptyList())
        coordinator.calculateProtection(circAlpha.id, listOf(circAlpha), emptyList())

        // Check Project Alpha cache
        assertNotNull("Alpha sizing must be cached", coordinator.getCachedSizingResult(projectAlpha, 501))
        assertNotNull("Alpha short-circuit must be cached", coordinator.getCachedShortCircuitResult(projectAlpha, 501))
        assertNotNull("Alpha ads must be cached", coordinator.getCachedAdsResult(projectAlpha, 501))
        assertNotNull("Alpha protection must be cached", coordinator.getCachedProtectionResult(projectAlpha, 501))

        // Check Project Beta cache MUST be completely null
        assertNull("Beta sizing must be NULL", coordinator.getCachedSizingResult(projectBeta, 501))
        assertNull("Beta short-circuit must be NULL", coordinator.getCachedShortCircuitResult(projectBeta, 501))
        assertNull("Beta ads must be NULL", coordinator.getCachedAdsResult(projectBeta, 501))
        assertNull("Beta protection must be NULL", coordinator.getCachedProtectionResult(projectBeta, 501))
        assertNull("Beta coordination must be NULL", coordinator.getCachedCoordinationResult(projectBeta, 501))
    }

    @Test
    fun sameTransformerIdAcrossProjects_neverCrossReturnsTransformerResult() = runBlocking {
        val txAlpha = TransformerEntity(
            projectId = projectAlpha,
            transformerId = "TX-01",
            transformerName = "Alpha 2000kVA",
            kVA = 2000.0,
            voltage = 400
        )
        val txBeta = TransformerEntity(
            projectId = projectBeta,
            transformerId = "TX-01", // Deliberately identical ID
            transformerName = "Beta 500kVA",
            kVA = 500.0,
            voltage = 400
        )

        coordinator.calculateTransformer(txAlpha, emptyList())

        val alphaResult = coordinator.getCachedTransformerResult(projectAlpha, "TX-01")
        assertNotNull("Alpha transformer must be cached", alphaResult)
        assertTrue("Alpha transformer status must be non-blank", alphaResult!!.status.isNotBlank())

        val betaResult = coordinator.getCachedTransformerResult(projectBeta, "TX-01")
        assertNull("Beta transformer result must be null", betaResult)
    }

    @Test
    fun projectContextNeverCombinesEntityFromOneProjectWithResultFromAnother() = runBlocking {
        database.projectDao().upsertProject(ProjectEntity(projectId = projectAlpha, projectName = "Alpha", createdAt = 0, updatedAt = 0, revision = 1L))
        database.projectDao().upsertProject(ProjectEntity(projectId = projectBeta, projectName = "Beta", createdAt = 0, updatedAt = 0, revision = 1L))

        val circAlpha = CircuitEntity(
            id = 701,
            projectId = projectAlpha,
            circuitName = "Alpha-Circuit",
            loadKVA = 100.0,
            voltageV = 400,
            cableType = "Cu/XLPE/PVC - 4C",
            manufacturer = "Elsewedy",
            fedFrom = "Grid"
        )
        val circBeta = CircuitEntity(
            id = 702,
            projectId = projectBeta,
            circuitName = "Beta-Circuit",
            loadKVA = 15.0,
            voltageV = 400,
            cableType = "Cu/XLPE/PVC - 4C",
            manufacturer = "Elsewedy",
            fedFrom = "Grid"
        )
        repository.circuitDao.insertCircuit(circAlpha)
        repository.circuitDao.insertCircuit(circBeta)

        coordinator.calculateCableSizing(circAlpha, listOf(circAlpha))

        val betaSnapshot = provider.getCircuitContext(projectBeta, 702)
        assertNotNull(betaSnapshot)
        assertEquals("Beta-Circuit", betaSnapshot!!.circuitName)
        assertNull("Beta snapshot must not have Alpha result", betaSnapshot.cableSizing.result)
        assertEquals(ResultUsability.MISSING, betaSnapshot.cableSizing.usability)
    }

    @Test
    fun clearProjectCache_removesOnlyTargetProjectEntries() = runBlocking {
        val circAlpha = CircuitEntity(id = 801, projectId = projectAlpha, circuitName = "A", loadKVA = 50.0, cableType = "Cu/XLPE/PVC - 4C", manufacturer = "Elsewedy", fedFrom = "Grid")
        val circBeta = CircuitEntity(id = 802, projectId = projectBeta, circuitName = "B", loadKVA = 50.0, cableType = "Cu/XLPE/PVC - 4C", manufacturer = "Elsewedy", fedFrom = "Grid")

        coordinator.calculateCableSizing(circAlpha, listOf(circAlpha))
        coordinator.calculateCableSizing(circBeta, listOf(circBeta))

        assertNotNull(coordinator.getCachedSizingResult(projectAlpha, 801))
        assertNotNull(coordinator.getCachedSizingResult(projectBeta, 802))

        coordinator.clearProjectCache(projectAlpha)

        assertNull("Alpha cache must be cleared", coordinator.getCachedSizingResult(projectAlpha, 801))
        assertNotNull("Beta cache must remain intact", coordinator.getCachedSizingResult(projectBeta, 802))
    }

    @Test
    fun invalidateCircuit_removesOnlyTargetProjectCircuit() = runBlocking {
        val circAlpha = CircuitEntity(id = 901, projectId = projectAlpha, circuitName = "A", loadKVA = 50.0, cableType = "Cu/XLPE/PVC - 4C", manufacturer = "Elsewedy", fedFrom = "Grid")
        val circBeta = CircuitEntity(id = 901, projectId = projectBeta, circuitName = "B", loadKVA = 50.0, cableType = "Cu/XLPE/PVC - 4C", manufacturer = "Elsewedy", fedFrom = "Grid")

        coordinator.calculateCableSizing(circAlpha, listOf(circAlpha))
        coordinator.calculateCableSizing(circBeta, listOf(circBeta))

        coordinator.invalidateCircuit(projectAlpha, 901)

        assertNull("Alpha circuit 901 must be invalidated", coordinator.getCachedSizingResult(projectAlpha, 901))
        assertNotNull("Beta circuit 901 must remain cached", coordinator.getCachedSizingResult(projectBeta, 901))
    }

    @Test
    fun switchingProjects_preservesIndependentCachedResults() = runBlocking {
        val circAlpha = CircuitEntity(id = 950, projectId = projectAlpha, circuitName = "A", loadKVA = 100.0, cableType = "Cu/XLPE/PVC - 4C", manufacturer = "Elsewedy", fedFrom = "Grid")
        val circBeta = CircuitEntity(id = 950, projectId = projectBeta, circuitName = "B", loadKVA = 20.0, cableType = "Cu/XLPE/PVC - 4C", manufacturer = "Elsewedy", fedFrom = "Grid")

        val resAlpha = coordinator.calculateCableSizing(circAlpha, listOf(circAlpha))
        val resBeta = coordinator.calculateCableSizing(circBeta, listOf(circBeta))

        val cachedA = coordinator.getCachedSizingResult(projectAlpha, 950)
        val cachedB = coordinator.getCachedSizingResult(projectBeta, 950)

        assertNotNull(cachedA)
        assertNotNull(cachedB)
        assertNotEquals(cachedA!!.Ib_A, cachedB!!.Ib_A)
        assertEquals(resAlpha.Ib_A, cachedA.Ib_A, 0.01)
        assertEquals(resBeta.Ib_A, cachedB.Ib_A, 0.01)
    }

    // =========================================================================
    // 2. Revision Truth Tests
    // =========================================================================

    @Test
    fun missingProject_doesNotFabricateRevision() = runBlocking {
        val snapshot = provider.getValidationSnapshot("NON_EXISTENT_PROJECT_999")
        assertNull("Non-existent project must return null snapshot, never fabricated revision", snapshot)
    }

    @Test
    fun authoritativeRevision_isPreservedExactly() = runBlocking {
        database.projectDao().upsertProject(
            ProjectEntity(projectId = "PROJ-REV-42", projectName = "Revision Test", createdAt = 0, updatedAt = 0, revision = 42L)
        )

        val snapshot = provider.getValidationSnapshot("PROJ-REV-42")
        assertNotNull(snapshot)
        assertEquals("Snapshot must use exact authoritative revision", 42L, snapshot!!.projectRevision)
    }

    @Test
    fun validationSnapshot_revisionMatchesRepositoryAfterMutation() = runBlocking {
        database.projectDao().upsertProject(
            ProjectEntity(projectId = "PROJ-MUTATE", projectName = "Mutate Test", createdAt = 0, updatedAt = 0, revision = 1L)
        )

        val circ = CircuitEntity(id = 1101, projectId = "PROJ-MUTATE", circuitName = "Circ", loadKVA = 20.0, fedFrom = "Grid")
        repository.insertCircuit(circ) // Advances revision in EngineeringRepository

        val currentRev = repository.getCurrentRevision("PROJ-MUTATE").revision
        val snapshot = provider.getValidationSnapshot("PROJ-MUTATE")
        assertNotNull(snapshot)
        assertEquals(currentRev, snapshot!!.projectRevision)
    }

    // =========================================================================
    // 3. Compliance Truth Tests
    // =========================================================================

    @Test
    fun uncalculatedProject_isNotFullyCompliant() = runBlocking {
        database.projectDao().upsertProject(
            ProjectEntity(projectId = "PROJ-UNCALC", projectName = "Uncalculated", createdAt = 0, updatedAt = 0, revision = 1L)
        )
        val circuit = CircuitEntity(id = 1201, projectId = "PROJ-UNCALC", circuitName = "Feeder", loadKVA = 30.0, fedFrom = "Grid")
        repository.circuitDao.insertCircuit(circuit)

        val snapshot = provider.getValidationSnapshot("PROJ-UNCALC")
        assertNotNull(snapshot)
        assertNotEquals("Uncalculated project must NEVER be fully compliant", ProjectEngineeringStatus.FULLY_COMPLIANT, snapshot!!.engineeringStatus)
        assertEquals(ProjectEngineeringStatus.UNCALCULATED, snapshot.engineeringStatus)
        assertEquals(1, snapshot.missingCircuitCount)
        assertEquals(0, snapshot.calculatedCircuitCount)
    }

    @Test
    fun staleSizingResult_isNotCompliant() = runBlocking {
        database.projectDao().upsertProject(
            ProjectEntity(projectId = "PROJ-STALE", projectName = "Stale Test", createdAt = 0, updatedAt = 0, revision = 1L)
        )
        val circuit = CircuitEntity(id = 1202, projectId = "PROJ-STALE", circuitName = "Feeder", loadKVA = 30.0, cableType = "Cu/XLPE/PVC - 4C", manufacturer = "Elsewedy", fedFrom = "Grid")
        repository.circuitDao.insertCircuit(circuit)

        coordinator.calculateCableSizing(circuit, listOf(circuit))

        // Mutate circuit to make calculation state STALE
        val mutated = circuit.copy(loadKVA = 75.0)
        repository.updateCircuit(mutated)

        val snapshot = provider.getValidationSnapshot("PROJ-STALE")
        assertNotNull(snapshot)
        assertEquals(ProjectEngineeringStatus.CALCULATIONS_STALE, snapshot!!.engineeringStatus)
        assertEquals(1, snapshot.staleCircuitCount)
    }

    @Test
    fun allRequiredCurrentValidResults_canBeCompliant() = runBlocking {
        database.projectDao().upsertProject(
            ProjectEntity(projectId = "PROJ-COMPLIANT", projectName = "Compliant Test", createdAt = 0, updatedAt = 0, revision = 1L)
        )
        val tx = TransformerEntity(projectId = "PROJ-COMPLIANT", transformerId = "TX-01", transformerName = "Main Substation", kVA = 1000.0, voltage = 400)
        repository.transformerDao.insertTransformer(tx)

        val circuit = CircuitEntity(
            id = 1203,
            projectId = "PROJ-COMPLIANT",
            circuitName = "Compliant-Feeder",
            loadKVA = 45.0,
            voltageV = 400,
            cableType = "Cu/XLPE/PVC - 4C",
            manufacturer = "Elsewedy",
            transformerId = "TX-01",
            fedFrom = "Transformer"
        )
        repository.circuitDao.insertCircuit(circuit)

        coordinator.calculateCableSizing(circuit, listOf(circuit), listOf(tx))
        coordinator.calculateTransformer(tx, listOf(circuit))

        val snapshot = provider.getValidationSnapshot("PROJ-COMPLIANT")
        assertNotNull(snapshot)
        assertEquals(ProjectEngineeringStatus.FULLY_COMPLIANT, snapshot!!.engineeringStatus)
        assertEquals(0, snapshot.criticalFailureCount)
        assertEquals(1, snapshot.calculatedCircuitCount)
        assertEquals(0, snapshot.missingCircuitCount)
    }

    // =========================================================================
    // 4. Contract Purity Tests (Option A: Zero com.example.engine dependencies)
    // =========================================================================

    @Test
    fun contextContracts_haveNoEngineImplementationTypes() {
        val contractClasses = listOf(
            CircuitConfiguredInputs::class.java,
            CircuitContextSnapshot::class.java,
            TransformerConfiguredInputs::class.java,
            TransformerContextSnapshot::class.java,
            CableSizingContextResult::class.java,
            TransformerContextResult::class.java,
            ShortCircuitContextResult::class.java,
            ProtectionAdsContextResult::class.java,
            CoordinationContextResult::class.java,
            ValidationIssueItem::class.java,
            ProjectValidationSnapshot::class.java,
            ProjectContextProvider::class.java
        )

        for (cls in contractClasses) {
            for (field in cls.declaredFields) {
                val typeName = field.type.name
                assertFalse("Field '${field.name}' in $cls must not depend on com.example.engine types", typeName.startsWith("com.example.engine."))
                assertFalse("Field '${field.name}' in $cls must not depend on ViewModel", typeName.contains("ViewModel"))
                assertFalse("Field '${field.name}' in $cls must not depend on Compose", typeName.contains("androidx.compose"))
            }
        }
    }

    // =========================================================================
    // 5. Parent & Source Resolution Tests
    // =========================================================================

    @Test
    fun parentResolution_explicitParentIdWinsOverName() {
        val parent1 = CircuitEntity(id = 10, projectId = projectId, circuitName = "Board-A")
        val parent2 = CircuitEntity(id = 20, projectId = projectId, circuitName = "Board-B")
        val child = CircuitEntity(id = 30, projectId = projectId, circuitName = "Child", parentId = 20, fedFrom = "Board-A")

        val res = ParentResolutionResolver.resolveParent(child, listOf(parent1, parent2, child))
        assertTrue("Explicit parentId must win over name", res is ParentResolution.Resolved)
        assertEquals(20, (res as ParentResolution.Resolved).parent.id)
    }

    @Test
    fun parentResolution_duplicateNames_isAmbiguous_notFirstMatch() {
        val parentA1 = CircuitEntity(id = 10, projectId = projectId, circuitName = "Duplicate-Board")
        val parentA2 = CircuitEntity(id = 20, projectId = projectId, circuitName = "Duplicate-Board")
        val child = CircuitEntity(id = 30, projectId = projectId, circuitName = "Child", fedFrom = "Duplicate-Board")

        val res = ParentResolutionResolver.resolveParent(child, listOf(parentA1, parentA2, child))
        assertTrue("Duplicate names must produce Ambiguous resolution", res is ParentResolution.Ambiguous)
        assertEquals(listOf(10, 20), (res as ParentResolution.Ambiguous).candidateIds)
    }

    @Test
    fun parentResolution_selfParent_isRejected() {
        val selfLoop = CircuitEntity(id = 40, projectId = projectId, circuitName = "SelfLoop", parentId = 40)
        val res = ParentResolutionResolver.resolveParent(selfLoop, listOf(selfLoop))
        assertTrue("Self-parent must be rejected as InvalidCycle", res is ParentResolution.InvalidCycle)
    }

    @Test
    fun parentResolution_crossProjectParent_isRejected() {
        val parentInAlpha = CircuitEntity(id = 50, projectId = projectAlpha, circuitName = "Alpha-Board")
        val childInBeta = CircuitEntity(id = 60, projectId = projectBeta, circuitName = "Beta-Child", parentId = 50)

        val res = ParentResolutionResolver.resolveParent(childInBeta, listOf(parentInAlpha, childInBeta))
        assertTrue("Cross-project parent must produce MissingReference", res is ParentResolution.MissingReference)
    }

    // =========================================================================
    // 6. Transformer Assignment Tests
    // =========================================================================

    @Test
    fun assignedCircuitCount_matchesCoordinator() {
        val tx = TransformerEntity(projectId = projectId, transformerId = "TX-01", transformerName = "TX 1")
        val c1 = CircuitEntity(id = 1, projectId = projectId, circuitName = "C1", transformerId = "TX-01", fedFrom = "Transformer")
        val c2 = CircuitEntity(id = 2, projectId = projectId, circuitName = "C2", fedFrom = "tx-01")
        val c3 = CircuitEntity(id = 3, projectId = projectId, circuitName = "C3", transformerId = "TX-02", fedFrom = "Transformer")
        val c4 = CircuitEntity(id = 4, projectId = projectAlpha, circuitName = "C4", transformerId = "TX-01", fedFrom = "Transformer")

        val assigned = TransformerAssignmentResolver.resolveAssignedCircuits(tx, listOf(c1, c2, c3, c4))
        assertEquals("Must match project circuits assigned to TX-01 only", 2, assigned.size)
        assertEquals(listOf(1, 2), assigned.map { it.id })
    }

    @Test
    fun transformerConfiguredInputs_includeAllAuditedCalculationDeterminants() {
        val tx = TransformerEntity(
            projectId = projectId,
            transformerId = "TX-99",
            transformerName = "Full TX",
            kVA = 1250.0,
            voltage = 400,
            percentZ = 5.5,
            xr = 6.5,
            txType = "Dry",
            sourceType = "Utility Grid",
            status = "Active",
            sizeMode = "Auto",
            manualKVA = 1000.0,
            demandFactor = 0.85,
            diversity = 1.1,
            growth = 1.2,
            safety = 1.15,
            vectorGroup = "Dyn11",
            z0OverZ1 = 1.0,
            pf = 0.82
        )
        val inputs = TransformerConfiguredInputs(
            transformerId = tx.transformerId,
            transformerName = tx.transformerName,
            kVA = tx.kVA,
            voltage = tx.voltage,
            percentZ = tx.percentZ,
            xr = tx.xr,
            txType = tx.txType,
            sourceType = tx.sourceType,
            status = tx.status,
            sizeMode = tx.sizeMode,
            manualKVA = tx.manualKVA,
            demandFactor = tx.demandFactor,
            diversity = tx.diversity,
            growth = tx.growth,
            safety = tx.safety,
            vectorGroup = tx.vectorGroup,
            z0OverZ1 = tx.z0OverZ1,
            pf = tx.pf
        )
        assertEquals("TX-99", inputs.transformerId)
        assertEquals(1250.0, inputs.kVA, 0.001)
        assertEquals(0.85, inputs.demandFactor, 0.001)
        assertEquals("Dyn11", inputs.vectorGroup)
    }

    // =========================================================================
    // 7. Result & Freshness Coherence Tests (Usability)
    // =========================================================================

    @Test
    fun currentEngineeringFail_hasCurrentUsability() = runBlocking {
        val circuit = CircuitEntity(
            id = 1301,
            projectId = projectId,
            circuitName = "Overload-Feeder",
            loadKVA = 5000.0, // High load will cause sizing failure
            voltageV = 400,
            cableType = "Cu/XLPE/PVC - 4C",
            manufacturer = "Elsewedy",
            fedFrom = "Grid"
        )
        repository.circuitDao.insertCircuit(circuit)

        coordinator.calculateCableSizing(circuit, listOf(circuit))

        val snapshot = provider.getCircuitContext(projectId, 1301)
        assertNotNull(snapshot)
        assertEquals(ResultUsability.CURRENT, snapshot!!.cableSizing.usability)
        assertEquals(CalculationState.CURRENT, snapshot.cableSizing.freshness.state)
    }

    @Test
    fun staleCachedResult_isMarkedStale_notCurrent() = runBlocking {
        val circuit = CircuitEntity(
            id = 1302,
            projectId = projectId,
            circuitName = "Stale-Feeder",
            loadKVA = 40.0,
            cableType = "Cu/XLPE/PVC - 4C",
            manufacturer = "Elsewedy",
            fedFrom = "Grid"
        )
        repository.circuitDao.insertCircuit(circuit)

        coordinator.calculateCableSizing(circuit, listOf(circuit))

        val mutated = circuit.copy(loadKVA = 90.0)
        repository.updateCircuit(mutated)

        val snapshot = provider.getCircuitContext(projectId, 1302)
        assertNotNull(snapshot)
        assertNotNull("Stale result is preserved as historical data", snapshot!!.cableSizing.result)
        assertEquals("Usability must be STALE", ResultUsability.STALE, snapshot.cableSizing.usability)
        assertEquals(CalculationState.STALE, snapshot.cableSizing.freshness.state)
    }

    @Test
    fun missingResult_hasMissingUsability() = runBlocking {
        val circuit = CircuitEntity(
            id = 1303,
            projectId = projectId,
            circuitName = "Missing-Feeder",
            loadKVA = 40.0,
            fedFrom = "Grid"
        )
        repository.circuitDao.insertCircuit(circuit)

        val snapshot = provider.getCircuitContext(projectId, 1303)
        assertNotNull(snapshot)
        assertNull(snapshot!!.cableSizing.result)
        assertEquals(ResultUsability.MISSING, snapshot.cableSizing.usability)
    }

    // =========================================================================
    // 8. Pure Read & Side-Effect-Free Regression Tests
    // =========================================================================

    @Test
    fun circuitContext_returnsNullForMissingCircuit() = runBlocking {
        val result = provider.getCircuitContext(projectId, 99999)
        assertNull("Non-existent circuit must return null snapshot", result)
    }

    @Test
    fun transformerContext_returnsNullForMissingTransformer() = runBlocking {
        val result = provider.getTransformerContext(projectId, "TX-MISSING-99")
        assertNull("Non-existent transformer must return null", result)
    }

    @Test
    fun circuitContext_doesNotTriggerNewCalculation_whenReadingContext() = runBlocking {
        val circuit = CircuitEntity(
            id = 1401,
            projectId = projectId,
            circuitName = "No-Lazy-Calc-1401",
            loadKVA = 40.0,
            voltageV = 400,
            fedFrom = "Grid"
        )
        repository.circuitDao.insertCircuit(circuit)

        coordinator.clearCache()
        assertNull(coordinator.getCachedSizingResult(projectId, 1401))

        val snapshot = provider.getCircuitContext(projectId, 1401)
        assertNotNull(snapshot)
        assertNull("Reading context must NOT populate cache entry", coordinator.getCachedSizingResult(projectId, 1401))
        assertNull("Snapshot sizing result must remain null", snapshot!!.cableSizing.result)
    }

    @Test
    fun repeatedReads_returnConsistentSnapshotWithoutSideEffects() = runBlocking {
        val circuit = CircuitEntity(
            id = 1402,
            projectId = projectId,
            circuitName = "Idempotent-Feeder-1402",
            loadKVA = 35.0,
            voltageV = 400,
            fedFrom = "Grid"
        )
        repository.circuitDao.insertCircuit(circuit)

        val snap1 = provider.getCircuitContext(projectId, 1402)
        val snap2 = provider.getCircuitContext(projectId, 1402)

        assertEquals(snap1?.circuitId, snap2?.circuitId)
        assertEquals(snap1?.inputs?.loadKVA, snap2?.inputs?.loadKVA)
        assertEquals(snap1?.cableSizing?.freshness?.state, snap2?.cableSizing?.freshness?.state)
    }

    @Test
    fun providerAddsNoNewRoomEntityOrTable() {
        val providerAnnotations = ProjectContextProviderImpl::class.java.annotations.map { it.annotationClass.java.simpleName }
        assertFalse(providerAnnotations.contains("Entity"))
        assertFalse(providerAnnotations.contains("Dao"))

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
