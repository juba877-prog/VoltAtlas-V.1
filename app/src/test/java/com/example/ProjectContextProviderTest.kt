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
    // 1. BLOCKER A: Cache Signature Semantics & Project Isolation
    // =========================================================================

    @Test
    fun sameProjectSameEntitySameSignature_returnsCachedResult() = runBlocking {
        val circuit = CircuitEntity(
            id = 101,
            projectId = projectId,
            circuitName = "Signature-Feeder-101",
            loadKVA = 50.0,
            voltageV = 400,
            cableType = "Cu/XLPE/PVC - 4C",
            manufacturer = "Elsewedy",
            installation = "Laid in Free Air",
            fedFrom = "Grid"
        )
        val res1 = coordinator.calculateCableSizing(circuit, listOf(circuit))
        val res2 = coordinator.calculateCableSizing(circuit, listOf(circuit))
        assertSame("Subsequent calculation with identical signature must return cached instance", res1, res2)
    }

    @Test
    fun sameProjectSameEntityRelevantInputChange_recomputesAndReplacesCache() = runBlocking {
        val circuit1 = CircuitEntity(
            id = 102,
            projectId = projectId,
            circuitName = "Feeder-102",
            loadKVA = 30.0,
            voltageV = 400,
            cableType = "Cu/XLPE/PVC - 4C",
            manufacturer = "Elsewedy",
            fedFrom = "Grid"
        )
        val res1 = coordinator.calculateCableSizing(circuit1, listOf(circuit1))

        val circuit2 = circuit1.copy(loadKVA = 80.0) // Mutate input
        val res2 = coordinator.calculateCableSizing(circuit2, listOf(circuit2))

        assertNotEquals("Load mutation must change operating current Ib", res1.Ib_A, res2.Ib_A, 0.1)
        val cached = coordinator.getCachedSizingResult(projectId, 102)
        assertNotNull(cached)
        assertEquals(res2.Ib_A, cached!!.Ib_A, 0.01)
    }

    @Test
    fun sameProjectSameEntityDependencyChange_recomputesAndReplacesCache() = runBlocking {
        val parent = CircuitEntity(id = 1, projectId = projectId, circuitName = "ParentBoard", loadKVA = 100.0, cableType = "Cu/XLPE/PVC - 4C", manufacturer = "Elsewedy", fedFrom = "Grid")
        val child = CircuitEntity(id = 2, projectId = projectId, circuitName = "ChildFeeder", loadKVA = 20.0, cableType = "Cu/XLPE/PVC - 4C", manufacturer = "Elsewedy", parentId = 1, fedFrom = "ParentBoard")

        coordinator.calculateCableSizing(parent, listOf(parent, child))
        val childRes1 = coordinator.calculateCableSizing(child, listOf(parent, child))

        // Mutate parent length to increase parent voltage drop
        val mutatedParent = parent.copy(length_m = 150.0)
        coordinator.calculateCableSizing(mutatedParent, listOf(mutatedParent, child))
        val childRes2 = coordinator.calculateCableSizing(child, listOf(mutatedParent, child))

        assertNotEquals("Parent VD change must propagate to child cumulative VD", childRes1.cumulativeVDpct, childRes2.cumulativeVDpct, 0.001)
    }

    @Test
    fun sameEntityIdDifferentProjects_neverCrossReturns() = runBlocking {
        database.projectDao().upsertProject(ProjectEntity(projectId = projectAlpha, projectName = "Alpha", createdAt = 0, updatedAt = 0, revision = 1L))
        database.projectDao().upsertProject(ProjectEntity(projectId = projectBeta, projectName = "Beta", createdAt = 0, updatedAt = 0, revision = 1L))

        val circAlpha = CircuitEntity(id = 501, projectId = projectAlpha, circuitName = "Alpha-501", loadKVA = 80.0, cableType = "Cu/XLPE/PVC - 4C", manufacturer = "Elsewedy", fedFrom = "Grid")
        val circBeta = CircuitEntity(id = 501, projectId = projectBeta, circuitName = "Beta-501", loadKVA = 20.0, cableType = "Cu/XLPE/PVC - 4C", manufacturer = "Elsewedy", fedFrom = "Grid")

        coordinator.calculateCableSizing(circAlpha, listOf(circAlpha))
        coordinator.calculateShortCircuit(circAlpha.id, listOf(circAlpha), emptyList())
        coordinator.calculateAds(circAlpha.id, listOf(circAlpha), emptyList())
        coordinator.calculateProtection(circAlpha.id, listOf(circAlpha), emptyList())

        assertNotNull("Alpha sizing must be cached", coordinator.getCachedSizingResult(projectAlpha, 501))
        assertNull("Beta sizing must be NULL", coordinator.getCachedSizingResult(projectBeta, 501))
        assertNull("Beta short-circuit must be NULL", coordinator.getCachedShortCircuitResult(projectBeta, 501))
        assertNull("Beta ads must be NULL", coordinator.getCachedAdsResult(projectBeta, 501))
        assertNull("Beta protection must be NULL", coordinator.getCachedProtectionResult(projectBeta, 501))
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

    // =========================================================================
    // 2. BLOCKER C: Multi-Node Cycle Protection & Ancestor Chain Resolution
    // =========================================================================

    @Test
    fun parentResolution_twoNodeCycle_isRejected() {
        // A -> B and B -> A
        val nodeA = CircuitEntity(id = 10, projectId = projectId, circuitName = "Panel-A", parentId = 20, fedFrom = "Panel-B")
        val nodeB = CircuitEntity(id = 20, projectId = projectId, circuitName = "Panel-B", parentId = 10, fedFrom = "Panel-A")

        val resA = ParentResolutionResolver.resolveParent(nodeA, listOf(nodeA, nodeB))
        assertTrue("Two-node cycle must return InvalidCycle", resA is ParentResolution.InvalidCycle)
        val cyclePath = (resA as ParentResolution.InvalidCycle).visitedIds
        assertTrue("Cycle path must contain both nodes", cyclePath.contains(10) && cyclePath.contains(20))
    }

    @Test
    fun parentResolution_threeNodeCycle_isRejected() {
        // A -> B -> C -> A
        val nodeA = CircuitEntity(id = 1, projectId = projectId, circuitName = "A", parentId = 2, fedFrom = "B")
        val nodeB = CircuitEntity(id = 2, projectId = projectId, circuitName = "B", parentId = 3, fedFrom = "C")
        val nodeC = CircuitEntity(id = 3, projectId = projectId, circuitName = "C", parentId = 1, fedFrom = "A")

        val res = ParentResolutionResolver.resolveParent(nodeA, listOf(nodeA, nodeB, nodeC))
        assertTrue("Three-node cycle must return InvalidCycle", res is ParentResolution.InvalidCycle)
    }

    @Test
    fun parentResolution_deepAcyclicChain_resolvesDeterministically() {
        // Root -> A -> B -> C
        val root = CircuitEntity(id = 1, projectId = projectId, circuitName = "Main-Switchboard", fedFrom = "Grid")
        val nodeA = CircuitEntity(id = 2, projectId = projectId, circuitName = "Panel-A", parentId = 1, fedFrom = "Main-Switchboard")
        val nodeB = CircuitEntity(id = 3, projectId = projectId, circuitName = "Panel-B", parentId = 2, fedFrom = "Panel-A")
        val nodeC = CircuitEntity(id = 4, projectId = projectId, circuitName = "Panel-C", parentId = 3, fedFrom = "Panel-B")

        val all = listOf(root, nodeA, nodeB, nodeC)
        val resC = ParentResolutionResolver.resolveParent(nodeC, all)
        assertTrue("Acyclic child must resolve parent", resC is ParentResolution.Resolved)
        assertEquals(3, (resC as ParentResolution.Resolved).parent.id)

        val ancestors = ParentResolutionResolver.resolveAncestorChain(nodeC, all)
        assertEquals("Ancestor chain must be [root, nodeA, nodeB]", listOf(1, 2, 3), ancestors.map { it.id })
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
    fun parentResolution_crossProjectReference_isRejected() {
        val parentInAlpha = CircuitEntity(id = 50, projectId = projectAlpha, circuitName = "Alpha-Board")
        val childInBeta = CircuitEntity(id = 60, projectId = projectBeta, circuitName = "Beta-Child", parentId = 50)

        val res = ParentResolutionResolver.resolveParent(childInBeta, listOf(parentInAlpha, childInBeta))
        assertTrue("Cross-project parent must produce MissingReference", res is ParentResolution.MissingReference)
    }

    // =========================================================================
    // 3. BLOCKER D: ResultUsability Mapping & Error Policy
    // =========================================================================

    @Test
    fun provenanceIncomplete_mapsToPartialUsability() = runBlocking {
        val circuit = CircuitEntity(id = 201, projectId = projectId, circuitName = "Partial-Feeder", loadKVA = 30.0, cableType = "Cu/XLPE/PVC - 4C", manufacturer = "Elsewedy", fedFrom = "Grid")
        repository.circuitDao.insertCircuit(circuit)
        coordinator.calculateCableSizing(circuit, listOf(circuit))

        val customFreshness = object : CalculationFreshnessProvider {
            override suspend fun getCalculationState(scope: CalculationScope): CalculationStateSnapshot {
                return CalculationStateSnapshot(
                    scope = scope,
                    state = CalculationState.STALE,
                    currentProjectRevision = 1L,
                    reasonCode = FreshnessReasonCode.PROVENANCE_INCOMPLETE,
                    reason = "Upstream provenance incomplete"
                )
            }
        }
        val customProvider = ProjectContextProviderImpl(repository, coordinator, freshnessProvider = customFreshness)
        val snap = customProvider.getCircuitContext(projectId, 201)
        assertNotNull(snap)
        assertEquals("PROVENANCE_INCOMPLETE must map to PARTIAL usability", ResultUsability.PARTIAL, snap!!.cableSizing.usability)
    }

    @Test
    fun ordinaryStale_mapsToStaleUsability() = runBlocking {
        val circuit = CircuitEntity(id = 202, projectId = projectId, circuitName = "Stale-Feeder", loadKVA = 30.0, cableType = "Cu/XLPE/PVC - 4C", manufacturer = "Elsewedy", fedFrom = "Grid")
        repository.circuitDao.insertCircuit(circuit)
        coordinator.calculateCableSizing(circuit, listOf(circuit))

        val mutated = circuit.copy(loadKVA = 75.0)
        repository.updateCircuit(mutated)

        val snap = provider.getCircuitContext(projectId, 202)
        assertNotNull(snap)
        assertEquals(ResultUsability.STALE, snap!!.cableSizing.usability)
    }

    @Test
    fun currentEngineeringFailure_mapsToCurrentUsability() = runBlocking {
        val circuit = CircuitEntity(id = 203, projectId = projectId, circuitName = "Fail-Feeder", loadKVA = 5000.0, cableType = "Cu/XLPE/PVC - 4C", manufacturer = "Elsewedy", fedFrom = "Grid")
        repository.circuitDao.insertCircuit(circuit)
        coordinator.calculateCableSizing(circuit, listOf(circuit))

        val snap = provider.getCircuitContext(projectId, 203)
        assertNotNull(snap)
        assertEquals("Engineering FAIL with valid calculation must have CURRENT usability", ResultUsability.CURRENT, snap!!.cableSizing.usability)
        assertEquals(CalculationState.CURRENT, snap.cableSizing.freshness.state)
    }

    @Test
    fun missingResultCannotHaveCurrentUsability() = runBlocking {
        val circuit = CircuitEntity(id = 204, projectId = projectId, circuitName = "Uncalc", loadKVA = 30.0, fedFrom = "Grid")
        repository.circuitDao.insertCircuit(circuit)

        val snap = provider.getCircuitContext(projectId, 204)
        assertNotNull(snap)
        assertNull(snap!!.cableSizing.result)
        assertEquals(ResultUsability.MISSING, snap.cableSizing.usability)
    }

    @Test
    fun errorFreshness_payloadExposureFollowsDocumentedPolicy() = runBlocking {
        val circuit = CircuitEntity(id = 205, projectId = projectId, circuitName = "Err-Feeder", loadKVA = 30.0, cableType = "Cu/XLPE/PVC - 4C", manufacturer = "Elsewedy", fedFrom = "Grid")
        repository.circuitDao.insertCircuit(circuit)
        coordinator.calculateCableSizing(circuit, listOf(circuit))

        val errorFreshness = object : CalculationFreshnessProvider {
            override suspend fun getCalculationState(scope: CalculationScope): CalculationStateSnapshot {
                return CalculationStateSnapshot(
                    scope = scope,
                    state = CalculationState.ERROR,
                    currentProjectRevision = 1L,
                    reasonCode = FreshnessReasonCode.ENGINE_CHANGED,
                    reason = "Calculation engine error"
                )
            }
        }
        val customProvider = ProjectContextProviderImpl(repository, coordinator, freshnessProvider = errorFreshness)
        val snap = customProvider.getCircuitContext(projectId, 205)
        assertNotNull(snap)
        assertEquals(ResultUsability.ERROR, snap!!.cableSizing.usability)
        assertNotNull("Prior result is retained as historical payload under ERROR usability", snap.cableSizing.result)
    }

    // =========================================================================
    // 4. BLOCKER E: FULLY_COMPLIANT Domain Completeness
    // =========================================================================

    @Test
    fun sizingCurrentButShortCircuitMissing_isNotFullyCompliant() = runBlocking {
        database.projectDao().upsertProject(ProjectEntity(projectId = "PROJ-SC-MISSING", projectName = "SC Missing", createdAt = 0, updatedAt = 0, revision = 1L))
        val circuit = CircuitEntity(id = 301, projectId = "PROJ-SC-MISSING", circuitName = "Feeder", loadKVA = 30.0, cableType = "Cu/XLPE/PVC - 4C", manufacturer = "Elsewedy", fedFrom = "Grid")
        repository.circuitDao.insertCircuit(circuit)

        coordinator.calculateCableSizing(circuit, listOf(circuit))

        val snap = provider.getValidationSnapshot("PROJ-SC-MISSING")
        assertNotNull(snap)
        assertNotEquals("Missing SC/protection must prevent FULLY_COMPLIANT", ProjectEngineeringStatus.FULLY_COMPLIANT, snap!!.engineeringStatus)
        assertEquals(ProjectEngineeringStatus.CALCULATIONS_MISSING, snap.engineeringStatus)
    }

    @Test
    fun protectionMissing_isNotFullyCompliant() = runBlocking {
        database.projectDao().upsertProject(ProjectEntity(projectId = "PROJ-PROT-MISSING", projectName = "Prot Missing", createdAt = 0, updatedAt = 0, revision = 1L))
        val circuit = CircuitEntity(id = 302, projectId = "PROJ-PROT-MISSING", circuitName = "Feeder", loadKVA = 30.0, cableType = "Cu/XLPE/PVC - 4C", manufacturer = "Elsewedy", fedFrom = "Grid")
        repository.circuitDao.insertCircuit(circuit)

        coordinator.calculateCableSizing(circuit, listOf(circuit))
        coordinator.calculateShortCircuit(circuit.id, listOf(circuit), emptyList())

        val snap = provider.getValidationSnapshot("PROJ-PROT-MISSING")
        assertNotNull(snap)
        assertNotEquals(ProjectEngineeringStatus.FULLY_COMPLIANT, snap!!.engineeringStatus)
    }

    @Test
    fun coordinationStale_isNotFullyCompliant() = runBlocking {
        database.projectDao().upsertProject(ProjectEntity(projectId = "PROJ-COORD-STALE", projectName = "Coord Stale", createdAt = 0, updatedAt = 0, revision = 1L))
        val tx = TransformerEntity(projectId = "PROJ-COORD-STALE", transformerId = "TX-01", transformerName = "TX 100kVA", kVA = 100.0, voltage = 400, percentZ = 4.0)
        repository.transformerDao.insertTransformer(tx)

        val parent = CircuitEntity(id = 303, projectId = "PROJ-COORD-STALE", circuitName = "Parent", loadKVA = 15.0, cableType = "Cu/XLPE/PVC - 4C", manufacturer = "Elsewedy", transformerId = "TX-01", fedFrom = "Transformer")
        val child = CircuitEntity(id = 304, projectId = "PROJ-COORD-STALE", circuitName = "Child", loadKVA = 5.0, cableType = "Cu/XLPE/PVC - 4C", manufacturer = "Elsewedy", parentId = 303, fedFrom = "Parent")
        repository.circuitDao.insertCircuit(parent)
        repository.circuitDao.insertCircuit(child)

        // Calculate all required domains for baseline
        coordinator.calculateTransformer(tx, listOf(parent, child))
        coordinator.calculateCableSizing(parent, listOf(parent, child), listOf(tx))
        coordinator.calculateCableSizing(child, listOf(parent, child), listOf(tx))
        coordinator.calculateShortCircuit(parent.id, listOf(parent, child), listOf(tx))
        coordinator.calculateShortCircuit(child.id, listOf(parent, child), listOf(tx))
        coordinator.calculateAds(parent.id, listOf(parent, child), listOf(tx))
        coordinator.calculateAds(child.id, listOf(parent, child), listOf(tx))
        coordinator.calculateProtection(parent.id, listOf(parent, child), listOf(tx))
        coordinator.calculateProtection(child.id, listOf(parent, child), listOf(tx))
        coordinator.calculateCoordination(parent.id, listOf(parent, child), listOf(tx))
        coordinator.calculateCoordination(child.id, listOf(parent, child), listOf(tx))

        // Pre-mutation assertion: baseline must be FULLY_COMPLIANT
        val preSnap = provider.getValidationSnapshot("PROJ-COORD-STALE")
        assertNotNull(preSnap)
        assertEquals(0, preSnap!!.warningCount)
        assertEquals(0, preSnap.criticalFailureCount)
        assertEquals(0, preSnap.missingCircuitCount)
        assertEquals(0, preSnap.staleCircuitCount)
        assertEquals(ProjectEngineeringStatus.FULLY_COMPLIANT, preSnap.engineeringStatus)

        // Mutate parent device size
        val mutatedParent = parent.copy(overrideDeviceSize = 80.0)
        repository.updateCircuit(mutatedParent)

        // Post-mutation assertion: stale without missing domains
        val snap = provider.getValidationSnapshot("PROJ-COORD-STALE")
        assertNotNull(snap)
        assertEquals(0, snap!!.missingCircuitCount)
        assertTrue(snap.staleCircuitCount > 0)
        assertNotEquals(ProjectEngineeringStatus.FULLY_COMPLIANT, snap.engineeringStatus)
        assertEquals(ProjectEngineeringStatus.CALCULATIONS_STALE, snap.engineeringStatus)
    }

    @Test
    fun allRequiredDomainsCurrentAndValid_canBeFullyCompliant() = runBlocking {
        database.projectDao().upsertProject(ProjectEntity(projectId = "PROJ-ALL-VALID", projectName = "All Valid", createdAt = 0, updatedAt = 0, revision = 1L))
        // H5 note: the transformer FLC is load-driven (~72 A for this project's single
        // 20 kVA feeder) — the feeder device must keep the coordination ratio >= 2.0.
        // See the circuit overrideDeviceSize comment below.
        val tx = TransformerEntity(projectId = "PROJ-ALL-VALID", transformerId = "TX-01", transformerName = "Substation TX", kVA = 100.0, voltage = 400, percentZ = 4.0)
        repository.transformerDao.insertTransformer(tx)

        val circuit = CircuitEntity(
            id = 305,
            projectId = "PROJ-ALL-VALID",
            circuitName = "Valid-Feeder",
            loadKVA = 20.0,
            voltageV = 400,
            cableType = "Cu/XLPE/PVC - 4C",
            manufacturer = "Elsewedy",
            transformerId = "TX-01",
            fedFrom = "Transformer",
            // H5 note: the feeder device must give an upstream/downstream coordination ratio
            // >= MAGNETIC_RATIO_MIN (2.0) so this "fully compliant" snapshot stays truthful.
            // The transformer FLC is load-driven (~72 A for this project); with a 40 A device
            // the ratio is 1.8 (inside the 1.6–2.0 thermal-only band, correctly a WARNING
            // since the H5 remediation). Overriding to 32 A (still >= Ib 28.9 A) yields
            // ratio 2.25 — genuinely screened-selective (IEC 60947-2 current-ratio screening).
            overrideDeviceSize = 32.0
        )
        repository.circuitDao.insertCircuit(circuit)

        coordinator.calculateTransformer(tx, listOf(circuit))
        coordinator.calculateCableSizing(circuit, listOf(circuit), listOf(tx))
        coordinator.calculateShortCircuit(circuit.id, listOf(circuit), listOf(tx))
        coordinator.calculateAds(circuit.id, listOf(circuit), listOf(tx))
        coordinator.calculateProtection(circuit.id, listOf(circuit), listOf(tx))
        coordinator.calculateCoordination(circuit.id, listOf(circuit), listOf(tx))

        val snap = provider.getValidationSnapshot("PROJ-ALL-VALID")
        assertNotNull(snap)
        assertEquals(0, snap!!.criticalFailureCount)
        assertEquals(0, snap.warningCount)
        assertEquals(0, snap.reviewRequiredCount)
        assertEquals(0, snap.missingCircuitCount)
        assertEquals(0, snap.staleCircuitCount)
        assertEquals(1, snap.calculatedCircuitCount)
        assertTrue(snap.topIssues.isEmpty())
        assertEquals("When all domains are current and valid, project is FULLY_COMPLIANT", ProjectEngineeringStatus.FULLY_COMPLIANT, snap.engineeringStatus)
    }

    @Test
    fun fullyCompliantFixture_hasZeroWarningsBeforeStatusAssertion() = runBlocking {
        database.projectDao().upsertProject(ProjectEntity(projectId = "PROJ-ZERO-WARN", projectName = "Zero Warn", createdAt = 0, updatedAt = 0, revision = 1L))
        val tx = TransformerEntity(projectId = "PROJ-ZERO-WARN", transformerId = "TX-01", transformerName = "TX 100kVA", kVA = 100.0, voltage = 400, percentZ = 4.0)
        repository.transformerDao.insertTransformer(tx)

        val circuit = CircuitEntity(id = 307, projectId = "PROJ-ZERO-WARN", circuitName = "Main Feeder", loadKVA = 15.0, voltageV = 400, cableType = "Cu/XLPE/PVC - 4C", manufacturer = "Elsewedy", transformerId = "TX-01", fedFrom = "Transformer")
        repository.circuitDao.insertCircuit(circuit)

        coordinator.calculateTransformer(tx, listOf(circuit))
        coordinator.calculateCableSizing(circuit, listOf(circuit), listOf(tx))
        coordinator.calculateShortCircuit(circuit.id, listOf(circuit), listOf(tx))
        coordinator.calculateAds(circuit.id, listOf(circuit), listOf(tx))
        coordinator.calculateProtection(circuit.id, listOf(circuit), listOf(tx))
        coordinator.calculateCoordination(circuit.id, listOf(circuit), listOf(tx))

        val snap = provider.getValidationSnapshot("PROJ-ZERO-WARN")
        assertNotNull(snap)
        assertEquals(0, snap!!.criticalFailureCount)
        assertEquals(0, snap.warningCount)
        assertEquals(0, snap.reviewRequiredCount)
        assertEquals(0, snap.missingCircuitCount)
        assertEquals(0, snap.staleCircuitCount)
        assertEquals(1, snap.calculatedCircuitCount)
        assertTrue(snap.topIssues.isEmpty())
        assertEquals(ProjectEngineeringStatus.FULLY_COMPLIANT, snap.engineeringStatus)
    }

    @Test
    fun genuineEngineWarning_returnsEngineeringWarning() = runBlocking {
        database.projectDao().upsertProject(ProjectEntity(projectId = "PROJ-ENG-WARN", projectName = "Eng Warn", createdAt = 0, updatedAt = 0, revision = 1L))
        val tx = TransformerEntity(projectId = "PROJ-ENG-WARN", transformerId = "TX-01", transformerName = "TX 100kVA", kVA = 100.0, voltage = 400, percentZ = 4.0)
        repository.transformerDao.insertTransformer(tx)

        // loadKVA = 50.0 produces breaker = 100A, ratio 144.3 / 100 = 1.44 < 1.6 (genuine coordination WARNING)
        val circuit = CircuitEntity(id = 308, projectId = "PROJ-ENG-WARN", circuitName = "Warn Feeder", loadKVA = 50.0, voltageV = 400, cableType = "Cu/XLPE/PVC - 4C", manufacturer = "Elsewedy", transformerId = "TX-01", fedFrom = "Transformer")
        repository.circuitDao.insertCircuit(circuit)

        coordinator.calculateTransformer(tx, listOf(circuit))
        coordinator.calculateCableSizing(circuit, listOf(circuit), listOf(tx))
        coordinator.calculateShortCircuit(circuit.id, listOf(circuit), listOf(tx))
        coordinator.calculateAds(circuit.id, listOf(circuit), listOf(tx))
        coordinator.calculateProtection(circuit.id, listOf(circuit), listOf(tx))
        coordinator.calculateCoordination(circuit.id, listOf(circuit), listOf(tx))

        val snap = provider.getValidationSnapshot("PROJ-ENG-WARN")
        assertNotNull(snap)
        assertTrue("Genuine coordination warning must increase warningCount", snap!!.warningCount > 0)
        assertEquals(ProjectEngineeringStatus.ENGINEERING_WARNING, snap.engineeringStatus)
    }

    @Test
    fun genuineGraphWarning_returnsEngineeringWarning() = runBlocking {
        database.projectDao().upsertProject(ProjectEntity(projectId = "PROJ-GRAPH-WARN", projectName = "Graph Warn", createdAt = 0, updatedAt = 0, revision = 1L))
        val tx = TransformerEntity(projectId = "PROJ-GRAPH-WARN", transformerId = "TX-01", transformerName = "TX 100kVA", kVA = 100.0, voltage = 400, percentZ = 4.0)
        repository.transformerDao.insertTransformer(tx)

        // ATS referencing unconfigured emergency source produces graph warning
        val ats = com.example.data.AtsEntity(projectId = "PROJ-GRAPH-WARN", atsId = "ATS-01", atsName = "Main ATS", normalSourceId = "TX-01", emergencySourceId = "")
        repository.saveAtsSettings(ats)

        val circuit = CircuitEntity(id = 309, projectId = "PROJ-GRAPH-WARN", circuitName = "ATS Feeder", loadKVA = 20.0, voltageV = 400, cableType = "Cu/XLPE/PVC - 4C", manufacturer = "Elsewedy", assignedAtsId = "ATS-01", fedFrom = "ATS-01")
        repository.circuitDao.insertCircuit(circuit)

        coordinator.calculateTransformer(tx, listOf(circuit))
        coordinator.calculateCableSizing(circuit, listOf(circuit), listOf(tx))
        coordinator.calculateShortCircuit(circuit.id, listOf(circuit), listOf(tx))
        coordinator.calculateAds(circuit.id, listOf(circuit), listOf(tx))
        coordinator.calculateProtection(circuit.id, listOf(circuit), listOf(tx))
        coordinator.calculateCoordination(circuit.id, listOf(circuit), listOf(tx))

        val snap = provider.getValidationSnapshot("PROJ-GRAPH-WARN")
        assertNotNull(snap)
        assertNotEquals(ProjectEngineeringStatus.FULLY_COMPLIANT, snap!!.engineeringStatus)
    }

    @Test
    fun warningIsNotSilentlySuppressedToPass() = runBlocking {
        database.projectDao().upsertProject(ProjectEntity(projectId = "PROJ-NO-SUPPRESS", projectName = "No Suppress", createdAt = 0, updatedAt = 0, revision = 1L))
        val tx = TransformerEntity(projectId = "PROJ-NO-SUPPRESS", transformerId = "TX-01", transformerName = "TX 100kVA", kVA = 100.0, voltage = 400, percentZ = 4.0)
        repository.transformerDao.insertTransformer(tx)

        val circuit = CircuitEntity(id = 310, projectId = "PROJ-NO-SUPPRESS", circuitName = "Low Ratio Feeder", loadKVA = 50.0, voltageV = 400, cableType = "Cu/XLPE/PVC - 4C", manufacturer = "Elsewedy", transformerId = "TX-01", fedFrom = "Transformer")
        repository.circuitDao.insertCircuit(circuit)

        coordinator.calculateTransformer(tx, listOf(circuit))
        coordinator.calculateCableSizing(circuit, listOf(circuit), listOf(tx))
        coordinator.calculateShortCircuit(circuit.id, listOf(circuit), listOf(tx))
        coordinator.calculateAds(circuit.id, listOf(circuit), listOf(tx))
        coordinator.calculateProtection(circuit.id, listOf(circuit), listOf(tx))
        coordinator.calculateCoordination(circuit.id, listOf(circuit), listOf(tx))

        val snap = provider.getValidationSnapshot("PROJ-NO-SUPPRESS")
        assertNotNull(snap)
        assertNotEquals("Engineering warning must never be silently suppressed to FULLY_COMPLIANT", ProjectEngineeringStatus.FULLY_COMPLIANT, snap!!.engineeringStatus)
        assertEquals(ProjectEngineeringStatus.ENGINEERING_WARNING, snap.engineeringStatus)
    }

    @Test
    fun missingAndStaleTogether_missingWinsPerPolicy() = runBlocking {
        database.projectDao().upsertProject(ProjectEntity(projectId = "PROJ-MISS-STALE", projectName = "Miss Stale", createdAt = 0, updatedAt = 0, revision = 1L))
        val tx = TransformerEntity(projectId = "PROJ-MISS-STALE", transformerId = "TX-01", transformerName = "TX 100kVA", kVA = 100.0, voltage = 400, percentZ = 4.0)
        repository.transformerDao.insertTransformer(tx)

        val circuit1 = CircuitEntity(id = 311, projectId = "PROJ-MISS-STALE", circuitName = "Stale Circ", loadKVA = 20.0, cableType = "Cu/XLPE/PVC - 4C", manufacturer = "Elsewedy", transformerId = "TX-01", fedFrom = "Transformer")
        val circuit2 = CircuitEntity(id = 312, projectId = "PROJ-MISS-STALE", circuitName = "Uncalc Circ", loadKVA = 15.0, cableType = "Cu/XLPE/PVC - 4C", manufacturer = "Elsewedy", transformerId = "TX-01", fedFrom = "Transformer")
        repository.circuitDao.insertCircuit(circuit1)
        repository.circuitDao.insertCircuit(circuit2)

        // Calculate all for circuit1
        coordinator.calculateTransformer(tx, listOf(circuit1, circuit2))
        coordinator.calculateCableSizing(circuit1, listOf(circuit1, circuit2), listOf(tx))
        coordinator.calculateShortCircuit(circuit1.id, listOf(circuit1, circuit2), listOf(tx))
        coordinator.calculateAds(circuit1.id, listOf(circuit1, circuit2), listOf(tx))
        coordinator.calculateProtection(circuit1.id, listOf(circuit1, circuit2), listOf(tx))
        coordinator.calculateCoordination(circuit1.id, listOf(circuit1, circuit2), listOf(tx))

        // Mutate circuit1 to make it stale
        val mutatedC1 = circuit1.copy(overrideDeviceSize = 80.0)
        repository.updateCircuit(mutatedC1)

        // circuit2 was NEVER calculated (missing)
        val snap = provider.getValidationSnapshot("PROJ-MISS-STALE")
        assertNotNull(snap)
        assertEquals(1, snap!!.missingCircuitCount)
        assertEquals(1, snap.staleCircuitCount)
        assertEquals("When missing and stale coexist, CALCULATIONS_MISSING wins per policy", ProjectEngineeringStatus.CALCULATIONS_MISSING, snap.engineeringStatus)
    }

    @Test
    fun coordinationOnlyStale_noRequiredDomainMissing_returnsCalculationsStale() = runBlocking {
        database.projectDao().upsertProject(ProjectEntity(projectId = "PROJ-COORD-ONLY-STALE", projectName = "Coord Only Stale", createdAt = 0, updatedAt = 0, revision = 1L))
        val tx = TransformerEntity(projectId = "PROJ-COORD-ONLY-STALE", transformerId = "TX-01", transformerName = "TX 100kVA", kVA = 100.0, voltage = 400, percentZ = 4.0)
        repository.transformerDao.insertTransformer(tx)

        val parent = CircuitEntity(id = 313, projectId = "PROJ-COORD-ONLY-STALE", circuitName = "Parent Feeder", loadKVA = 15.0, cableType = "Cu/XLPE/PVC - 4C", manufacturer = "Elsewedy", transformerId = "TX-01", fedFrom = "Transformer")
        val child = CircuitEntity(id = 314, projectId = "PROJ-COORD-ONLY-STALE", circuitName = "Child Subfeeder", loadKVA = 5.0, cableType = "Cu/XLPE/PVC - 4C", manufacturer = "Elsewedy", parentId = 313, fedFrom = "Parent Feeder")
        repository.circuitDao.insertCircuit(parent)
        repository.circuitDao.insertCircuit(child)

        coordinator.calculateTransformer(tx, listOf(parent, child))
        coordinator.calculateCableSizing(parent, listOf(parent, child), listOf(tx))
        coordinator.calculateCableSizing(child, listOf(parent, child), listOf(tx))
        coordinator.calculateShortCircuit(parent.id, listOf(parent, child), listOf(tx))
        coordinator.calculateShortCircuit(child.id, listOf(parent, child), listOf(tx))
        coordinator.calculateAds(parent.id, listOf(parent, child), listOf(tx))
        coordinator.calculateAds(child.id, listOf(parent, child), listOf(tx))
        coordinator.calculateProtection(parent.id, listOf(parent, child), listOf(tx))
        coordinator.calculateProtection(child.id, listOf(parent, child), listOf(tx))
        coordinator.calculateCoordination(parent.id, listOf(parent, child), listOf(tx))
        coordinator.calculateCoordination(child.id, listOf(parent, child), listOf(tx))

        // Pre-mutation snapshot is FULLY_COMPLIANT
        val pre = provider.getValidationSnapshot("PROJ-COORD-ONLY-STALE")
        assertEquals(ProjectEngineeringStatus.FULLY_COMPLIANT, pre!!.engineeringStatus)

        // Mutate parent
        val mutated = parent.copy(overrideDeviceSize = 80.0)
        repository.updateCircuit(mutated)

        val post = provider.getValidationSnapshot("PROJ-COORD-ONLY-STALE")
        assertNotNull(post)
        assertEquals(0, post!!.missingCircuitCount)
        assertTrue(post.staleCircuitCount > 0)
        assertEquals(ProjectEngineeringStatus.CALCULATIONS_STALE, post.engineeringStatus)
    }

    @Test
    fun neverCalculatedCoordination_returnsCalculationsMissing_notStale() = runBlocking {
        database.projectDao().upsertProject(ProjectEntity(projectId = "PROJ-UNPARSED-COORD", projectName = "Uncalculated Coord", createdAt = 0, updatedAt = 0, revision = 1L))
        val tx = TransformerEntity(projectId = "PROJ-UNPARSED-COORD", transformerId = "TX-01", transformerName = "TX 100kVA", kVA = 100.0, voltage = 400, percentZ = 4.0)
        repository.transformerDao.insertTransformer(tx)

        val circuit = CircuitEntity(id = 315, projectId = "PROJ-UNPARSED-COORD", circuitName = "Feeder", loadKVA = 20.0, cableType = "Cu/XLPE/PVC - 4C", manufacturer = "Elsewedy", transformerId = "TX-01", fedFrom = "Transformer")
        repository.circuitDao.insertCircuit(circuit)

        // Calculate all EXCEPT coordination
        coordinator.calculateTransformer(tx, listOf(circuit))
        coordinator.calculateCableSizing(circuit, listOf(circuit), listOf(tx))
        coordinator.calculateShortCircuit(circuit.id, listOf(circuit), listOf(tx))
        coordinator.calculateAds(circuit.id, listOf(circuit), listOf(tx))
        coordinator.calculateProtection(circuit.id, listOf(circuit), listOf(tx))

        val snap = provider.getValidationSnapshot("PROJ-UNPARSED-COORD")
        assertNotNull(snap)
        assertEquals(1, snap!!.missingCircuitCount)
        assertEquals(ProjectEngineeringStatus.CALCULATIONS_MISSING, snap.engineeringStatus)
    }

    @Test
    fun recordedCoordinationThenDependencyMutation_returnsStale() = runBlocking {
        database.projectDao().upsertProject(ProjectEntity(projectId = "PROJ-RECORDED-MUT", projectName = "Recorded Mutation", createdAt = 0, updatedAt = 0, revision = 1L))
        val tx = TransformerEntity(projectId = "PROJ-RECORDED-MUT", transformerId = "TX-01", transformerName = "TX 100kVA", kVA = 100.0, voltage = 400, percentZ = 4.0)
        repository.transformerDao.insertTransformer(tx)

        val parent = CircuitEntity(id = 316, projectId = "PROJ-RECORDED-MUT", circuitName = "P316", loadKVA = 15.0, cableType = "Cu/XLPE/PVC - 4C", manufacturer = "Elsewedy", transformerId = "TX-01", fedFrom = "Transformer")
        val child = CircuitEntity(id = 317, projectId = "PROJ-RECORDED-MUT", circuitName = "C317", loadKVA = 5.0, cableType = "Cu/XLPE/PVC - 4C", manufacturer = "Elsewedy", parentId = 316, fedFrom = "P316")
        repository.circuitDao.insertCircuit(parent)
        repository.circuitDao.insertCircuit(child)

        coordinator.calculateTransformer(tx, listOf(parent, child))
        coordinator.calculateCableSizing(parent, listOf(parent, child), listOf(tx))
        coordinator.calculateCableSizing(child, listOf(parent, child), listOf(tx))
        coordinator.calculateShortCircuit(parent.id, listOf(parent, child), listOf(tx))
        coordinator.calculateShortCircuit(child.id, listOf(parent, child), listOf(tx))
        coordinator.calculateAds(parent.id, listOf(parent, child), listOf(tx))
        coordinator.calculateAds(child.id, listOf(parent, child), listOf(tx))
        coordinator.calculateProtection(parent.id, listOf(parent, child), listOf(tx))
        coordinator.calculateProtection(child.id, listOf(parent, child), listOf(tx))
        coordinator.calculateCoordination(parent.id, listOf(parent, child), listOf(tx))
        coordinator.calculateCoordination(child.id, listOf(parent, child), listOf(tx))

        // Assert cached result exists before mutation
        val cachedCoordBefore = coordinator.getCachedCoordinationResult("PROJ-RECORDED-MUT", child.id)
        assertNotNull("Coordination cached result must exist before mutation", cachedCoordBefore)

        // Mutate parent
        val mutatedP = parent.copy(overrideDeviceSize = 80.0)
        repository.updateCircuit(mutatedP)

        // Cached result is still present
        val cachedCoordAfter = coordinator.getCachedCoordinationResult("PROJ-RECORDED-MUT", child.id)
        assertNotNull("Coordination cached result must still exist after dependency mutation", cachedCoordAfter)

        val snap = provider.getValidationSnapshot("PROJ-RECORDED-MUT")
        assertNotNull(snap)
        assertEquals(ProjectEngineeringStatus.CALCULATIONS_STALE, snap!!.engineeringStatus)
    }

    @Test
    fun aggregateStatusPrecedence_isDeterministic() = runBlocking {
        // Critical Failure beats Missing
        database.projectDao().upsertProject(ProjectEntity(projectId = "PROJ-PRECEDENCE", projectName = "Precedence", createdAt = 0, updatedAt = 0, revision = 1L))
        val circuit = CircuitEntity(id = 318, projectId = "PROJ-PRECEDENCE", circuitName = "Overloaded", loadKVA = 5000.0, cableType = "Cu/XLPE/PVC - 4C", manufacturer = "Elsewedy", fedFrom = "Grid")
        repository.circuitDao.insertCircuit(circuit)
        coordinator.calculateCableSizing(circuit, listOf(circuit))
        // Sizing failed (5000kVA cable overload) but SC/Protection/Coord are missing
        val snap = provider.getValidationSnapshot("PROJ-PRECEDENCE")
        assertNotNull(snap)
        assertEquals("Critical failure outranks missing calculations", ProjectEngineeringStatus.ENGINEERING_FAILURE, snap!!.engineeringStatus)
    }

    @Test
    fun engineeringFailWithCurrentFreshness_isEngineeringFailureNotFreshnessError() = runBlocking {
        database.projectDao().upsertProject(ProjectEntity(projectId = "PROJ-ENG-FAIL", projectName = "Fail", createdAt = 0, updatedAt = 0, revision = 1L))
        val circuit = CircuitEntity(id = 306, projectId = "PROJ-ENG-FAIL", circuitName = "Overload", loadKVA = 5000.0, cableType = "Cu/XLPE/PVC - 4C", manufacturer = "Elsewedy", fedFrom = "Grid")
        repository.circuitDao.insertCircuit(circuit)

        coordinator.calculateCableSizing(circuit, listOf(circuit))
        coordinator.calculateShortCircuit(circuit.id, listOf(circuit), emptyList())
        coordinator.calculateAds(circuit.id, listOf(circuit), emptyList())
        coordinator.calculateProtection(circuit.id, listOf(circuit), emptyList())

        val snap = provider.getValidationSnapshot("PROJ-ENG-FAIL")
        assertNotNull(snap)
        assertEquals(ProjectEngineeringStatus.ENGINEERING_FAILURE, snap!!.engineeringStatus)
    }

    @Test
    fun emptyProject_isUncalculatedNotCompliant() = runBlocking {
        database.projectDao().upsertProject(ProjectEntity(projectId = "PROJ-EMPTY", projectName = "Empty", createdAt = 0, updatedAt = 0, revision = 1L))
        val snap = provider.getValidationSnapshot("PROJ-EMPTY")
        assertNotNull(snap)
        assertEquals(ProjectEngineeringStatus.UNCALCULATED, snap!!.engineeringStatus)
    }

    // =========================================================================
    // 5. BLOCKER F: Revision Truth & Contract Nullability
    // =========================================================================

    @Test
    fun missingProject_validationSnapshot_returnsNull() = runBlocking {
        val snap = provider.getValidationSnapshot("NON_EXISTENT_PROJ_999")
        assertNull("Missing project must return null snapshot", snap)
    }

    @Test
    fun existingProject_revisionLookupFailure_doesNotFabricateRevision() = runBlocking {
        database.projectDao().upsertProject(ProjectEntity(projectId = "PROJ-FAIL-REV", projectName = "Fail Rev", createdAt = 0, updatedAt = 0, revision = 77L))
        val failingRevProvider = object : ProjectRevisionProvider {
            override suspend fun getCurrentRevision(projectId: String): ProjectRevisionSnapshot {
                throw IllegalStateException("Revision store offline")
            }
        }
        val customProvider = ProjectContextProviderImpl(repository, coordinator, revisionProvider = failingRevProvider)
        val snap = customProvider.getValidationSnapshot("PROJ-FAIL-REV")
        assertNotNull(snap)
        assertEquals("Must fall back to repository entity revision (77L), never fabricate 1L", 77L, snap!!.projectRevision)
    }

    @Test
    fun existingProject_revisionMatchesRepositoryExactly() = runBlocking {
        database.projectDao().upsertProject(ProjectEntity(projectId = "PROJ-EXACT-REV", projectName = "Exact", createdAt = 0, updatedAt = 0, revision = 99L))
        val snap = provider.getValidationSnapshot("PROJ-EXACT-REV")
        assertNotNull(snap)
        assertEquals(99L, snap!!.projectRevision)
    }

    // =========================================================================
    // 6. BLOCKER G: Context DTO Mapping Completeness
    // =========================================================================

    @Test
    fun cableSizingContextMapping_preservesAllEngineFieldsDirectly() = runBlocking {
        val circuit = CircuitEntity(id = 401, projectId = projectId, circuitName = "Map-Feeder", loadKVA = 45.0, voltageV = 400, cableType = "Cu/XLPE/PVC - 4C", manufacturer = "Elsewedy", fedFrom = "Grid")
        repository.circuitDao.insertCircuit(circuit)

        val engineResult = coordinator.calculateCableSizing(circuit, listOf(circuit))
        val snapshot = provider.getCircuitContext(projectId, 401)
        assertNotNull(snapshot)
        val dto = snapshot!!.cableSizing.result
        assertNotNull(dto)

        assertEquals(engineResult.status, dto!!.status)
        assertEquals(engineResult.failReason, dto.failReason)
        assertEquals(engineResult.Ib_A, dto.Ib_A, 0.001)
        assertEquals(engineResult.deviceSize_A, dto.deviceSize_A, 0.001)
        assertEquals(engineResult.deviceType, dto.deviceType)
        assertEquals(engineResult.cableSize_mm2, dto.cableSize_mm2)
        assertEquals(engineResult.cTotalDerating, dto.cTotalDerating, 0.001)
        assertEquals(engineResult.ratedI_table_A, dto.ratedI_table_A, 0.001)
        assertEquals(engineResult.capacity_derated_Iz_A, dto.capacity_derated_Iz_A, 0.001)
        assertEquals(engineResult.utilizationPct, dto.utilizationPct, 0.001)
        assertEquals(engineResult.voltDrop_V, dto.voltDrop_V, 0.001)
        assertEquals(engineResult.vdPct, dto.vdPct, 0.001)
        assertEquals(engineResult.cumulativeVDpct, dto.cumulativeVDpct, 0.001)
        assertEquals(engineResult.neutral_mm2, dto.neutral_mm2)
        assertEquals(engineResult.earthCPC_mm2, dto.earthCPC_mm2)
        assertEquals(engineResult.cableMaterial, dto.cableMaterial)
        assertEquals(engineResult.insulation, dto.insulation)
        assertEquals(engineResult.cableOD_mm, dto.cableOD_mm, 0.001)
        assertEquals(engineResult.earthOD_mm, dto.earthOD_mm, 0.001)
    }

    @Test
    fun transformerContextMapping_preservesAllEngineFieldsDirectly() = runBlocking {
        val tx = TransformerEntity(projectId = projectId, transformerId = "TX-MAP", transformerName = "TX Map", kVA = 1000.0, voltage = 400)
        repository.transformerDao.insertTransformer(tx)

        val engineResult = coordinator.calculateTransformer(tx, emptyList())
        val snapshot = provider.getTransformerContext(projectId, "TX-MAP")
        assertNotNull(snapshot)
        val dto = snapshot!!.transformerResult.result
        assertNotNull(dto)

        assertEquals(engineResult.status, dto!!.status)
        assertEquals(engineResult.connectedLoad, dto.connectedLoadKVA, 0.001)
        assertEquals(engineResult.circuitsAssigned, dto.circuitsAssigned)
        assertEquals(engineResult.demandLoad, dto.demandLoadKVA, 0.001)
        assertEquals(engineResult.diversifiedLoad, dto.diversifiedLoadKVA, 0.001)
        assertEquals(engineResult.futureLoad, dto.futureLoadKVA, 0.001)
        assertEquals(engineResult.designLoad, dto.designLoadKVA, 0.001)
        assertEquals(engineResult.recommendedSize, dto.recommendedSizeKVA, 0.001)
        assertEquals(engineResult.selectedSize, dto.selectedSizeKVA, 0.001)
        assertEquals(engineResult.loadingPct, dto.loadingPct, 0.001)
        assertEquals(engineResult.flc, dto.flcA, 0.001)
        assertEquals(engineResult.zt, dto.ztOhms, 0.0001)
        assertEquals(engineResult.rt, dto.rtOhms, 0.0001)
        assertEquals(engineResult.xt, dto.xtOhms, 0.0001)
        assertEquals(engineResult.isc3Phase, dto.isc3PhaseKA, 0.001)
        assertEquals(engineResult.isc1Phase, dto.isc1PhaseKA, 0.001)
    }

    @Test
    fun shortCircuitContextMapping_preservesAllEngineFieldsDirectly() = runBlocking {
        val circuit = CircuitEntity(id = 402, projectId = projectId, circuitName = "SC-Map", loadKVA = 45.0, voltageV = 400, cableType = "Cu/XLPE/PVC - 4C", manufacturer = "Elsewedy", fedFrom = "Grid")
        repository.circuitDao.insertCircuit(circuit)

        val engineResult = coordinator.calculateShortCircuit(circuit.id, listOf(circuit), emptyList())
        val snapshot = provider.getCircuitContext(projectId, 402)
        assertNotNull(snapshot)
        val dto = snapshot!!.shortCircuit.result
        assertNotNull(dto)

        assertEquals(engineResult.status, dto!!.status)
        assertEquals(engineResult.iscKA, dto.iscKA, 0.001)
        assertEquals(engineResult.iwKA, dto.iwKA, 0.001)
        assertEquals(engineResult.deviceKA, dto.deviceKA, 0.001)
        assertEquals(engineResult.deviceCheck, dto.deviceCheck)
        assertEquals(engineResult.cableCheck, dto.cableCheck)
        assertEquals(engineResult.marginPct, dto.marginPct, 0.001)
        assertEquals(engineResult.note, dto.note)
        assertEquals(engineResult.ipKA, dto.ipKA, 0.001)
        assertEquals(engineResult.zTotalMOhm, dto.zTotalMOhm, 0.001)
        assertEquals(engineResult.rMOhm, dto.rMOhm, 0.001)
        assertEquals(engineResult.xMOhm, dto.xMOhm, 0.001)
    }

    @Test
    fun protectionAdsContextMapping_preservesAllEngineFieldsDirectly() = runBlocking {
        val circuit = CircuitEntity(id = 403, projectId = projectId, circuitName = "Prot-Map", loadKVA = 45.0, voltageV = 400, cableType = "Cu/XLPE/PVC - 4C", manufacturer = "Elsewedy", fedFrom = "Grid")
        repository.circuitDao.insertCircuit(circuit)

        val protResult = coordinator.calculateProtection(circuit.id, listOf(circuit), emptyList())
        val adsResult = coordinator.calculateAds(circuit.id, listOf(circuit), emptyList())

        val snapshot = provider.getCircuitContext(projectId, 403)
        assertNotNull(snapshot)
        val dto = snapshot!!.protectionAds.result
        assertNotNull(dto)

        assertEquals(protResult.finalStatus, dto!!.finalStatus)
        assertEquals(protResult.overloadStatus, dto.overloadStatus)
        assertEquals(protResult.shortCircuitStatus, dto.shortCircuitStatus)
        assertEquals(protResult.cableWithstandStatus, dto.cableWithstandStatus)
        assertEquals(protResult.selectivityStatus, dto.selectivityStatus)
        assertEquals(adsResult.status, dto.adsStatus)
        assertEquals(adsResult.calculatedZs, dto.calculatedZs, 0.001)
        assertEquals(adsResult.maxZsAllowed, dto.maxZsAllowed, 0.001)
        assertEquals(adsResult.disconnectionTime, dto.disconnectionTime)
        assertEquals(protResult.messages, dto.messages)
    }

    @Test
    fun coordinationContextMapping_preservesAllEngineFieldsDirectly() = runBlocking {
        val parent = CircuitEntity(id = 404, projectId = projectId, circuitName = "Coord-Parent", loadKVA = 50.0, cableType = "Cu/XLPE/PVC - 4C", manufacturer = "Elsewedy", fedFrom = "Grid")
        val child = CircuitEntity(id = 405, projectId = projectId, circuitName = "Coord-Child", loadKVA = 10.0, cableType = "Cu/XLPE/PVC - 4C", manufacturer = "Elsewedy", parentId = 404, fedFrom = "Coord-Parent")
        repository.circuitDao.insertCircuit(parent)
        repository.circuitDao.insertCircuit(child)

        val engineResult = coordinator.calculateCoordination(child.id, listOf(parent, child), emptyList())
        val snapshot = provider.getCircuitContext(projectId, 405)
        assertNotNull(snapshot)
        val dto = snapshot!!.coordination.result
        assertNotNull(dto)

        assertEquals(engineResult.downstreamCircuitId, dto!!.downstreamCircuitId)
        assertEquals(engineResult.downstreamName, dto.downstreamName)
        assertEquals(engineResult.upstreamName, dto.upstreamName)
        assertEquals(engineResult.ratio, dto.ratio, 0.001)
        assertEquals(engineResult.status, dto.status)
        assertEquals(engineResult.reason, dto.reason)
    }

    // =========================================================================
    // 7. Structural Invariants & Purity
    // =========================================================================

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
}
