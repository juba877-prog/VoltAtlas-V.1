package com.example.stress

import com.example.data.CircuitEntity
import com.example.data.TransformerEntity
import com.example.data.catalog.CatalogRepository
import com.example.engine.CableSizingEngine
import com.example.engine.ShortCircuitEngine
import com.example.engine.SizingResult
import com.example.engine.TransformerEngine
import com.example.engine.TransformerResult
import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Ignore
import org.junit.Test
import java.io.File
import kotlin.random.Random

@Ignore("heavy stress — run manually")
class FuzzStressTest {

    companion object {
        private val repository = CatalogRepository()

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
        fun setUpRepository() {
            val cableCatalogJson = findAssetFile("cable_catalog.json").readText()
            val cableTypesDataJson = findAssetFile("cable_types_data.json").readText()
            val breakerCatalogJson = findAssetFile("breaker_catalog.json").readText()
            val accessoryCatalogJson = findAssetFile("accessory_catalog.json").readText()
            val deratingDataJson = findAssetFile("derating_data.json").readText()
            val transformersSeedJson = findAssetFile("transformers_seed.json").readText()

            val diag = repository.loadAllFromStrings(
                cableCatalogJson, cableTypesDataJson, breakerCatalogJson,
                accessoryCatalogJson, deratingDataJson, transformersSeedJson
            )
            assertEquals("COMPLETE", diag.status)

            try {
                val cableTypesJson = findAssetFile("cable_types.json").readText()
                val array = org.json.JSONArray(cableTypesJson)
                val list = mutableListOf<com.example.data.CableType>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        com.example.data.CableType(
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
                com.example.data.Catalog.cableTypes = list
            } catch (_: Exception) {}
        }
    }

    @Test
    fun testLevel6FuzzingWithSeededRng() {
        println("=== LEVEL 6: FUZZING WITH SEEDED RNG (100,000 INPUTS) ===")

        val rng = Random(42)
        val fuzzIterations = 100000

        var cableFuzzCount = 0
        var cableCrashes = 0
        var cableInvariantViolations = 0

        val cableTypes = listOf(
            "Cu/XLPE/STA/PVC - 4C", "Cu/PVC/PVC - 4C", "Cu/PVC/SWA/PVC - 4C",
            "Al/XLPE/STA/PVC - 4C", "Cu/XLPE/PVC - Single Core", "Cu/PVC - Single Core",
            "UNSUPPORTED_TYPE_XYZ", ""
        )
        val installations = listOf("Laid in Ducts", "In Air", "Buried", "UNKNOWN_INSTALLATION")
        val phasesList = listOf("3P+N", "1P", "2P", "")

        for (i in 1..fuzzIterations) {
            cableFuzzCount++

            val loadKVA = if (rng.nextDouble() < 0.05) rng.nextDouble(-100.0, 10000.0) else rng.nextDouble(0.1, 2500.0)
            val pf = if (rng.nextDouble() < 0.05) rng.nextDouble(-1.0, 2.0) else rng.nextDouble(0.6, 1.0)
            val voltageV = if (rng.nextDouble() < 0.05) rng.nextInt(-100, 1000) else rng.nextInt(200, 690)
            val lengthM = if (rng.nextDouble() < 0.05) rng.nextDouble(-50.0, 5000.0) else rng.nextDouble(1.0, 500.0)
            val parallelRuns = if (rng.nextDouble() < 0.05) rng.nextInt(-5, 20) else rng.nextInt(1, 8)
            val airTemp = rng.nextInt(-10, 80)
            val gndTemp = rng.nextInt(-10, 60)
            val burialDepth = rng.nextDouble(-0.5, 3.0)
            val soilRes = rng.nextInt(10, 500)
            val noCircuits = rng.nextInt(0, 30)
            val cType = cableTypes[rng.nextInt(cableTypes.size)]
            val inst = installations[rng.nextInt(installations.size)]
            val ph = phasesList[rng.nextInt(phasesList.size)]

            val circ = CircuitEntity(
                id = i,
                circuitName = "FUZZ-CIRC-$i",
                loadKVA = loadKVA,
                pf = pf,
                phases = ph,
                voltageV = voltageV,
                length_m = lengthM,
                installation = inst,
                cableType = cType,
                manufacturer = "Elsewedy",
                parallelRuns = parallelRuns,
                airTempC = airTemp,
                gndTempC = gndTemp,
                burialDepth_m = burialDepth,
                soilRes = soilRes,
                noCircuits = noCircuits
            )

            try {
                val res = CableSizingEngine.sizeCircuit(circ, 0.0, repository)

                // Invariant checks on valid results
                if (res.status == "✓ PASS") {
                    if (res.Ib_A.isNaN() || !res.Ib_A.isFinite() || res.Ib_A < 0.0 ||
                        res.capacity_derated_Iz_A.isNaN() || !res.capacity_derated_Iz_A.isFinite() || res.capacity_derated_Iz_A < 0.0 ||
                        res.voltDrop_V.isNaN() || !res.voltDrop_V.isFinite() || res.voltDrop_V < 0.0
                    ) {
                        cableInvariantViolations++
                        println("Fuzz Invariant Violation (Pass Result): seed=42, i=$i, input=$circ, result=$res")
                    }

                    // Check Ib <= In <= Iz
                    if (res.Ib_A > res.deviceSize_A + 0.01 || res.deviceSize_A > res.capacity_derated_Iz_A + 0.01) {
                        cableInvariantViolations++
                        println("Fuzz Protection Invariant Violation: Ib=${res.Ib_A} <= In=${res.deviceSize_A} <= Iz=${res.capacity_derated_Iz_A}")
                    }
                }
            } catch (e: Exception) {
                cableCrashes++
                println("Fuzz Cable Engine Exception: seed=42, i=$i, input=$circ, Exception: ${e.message}")
            }
        }

        println("LEVEL 6 FUZZING CABLE ENGINE SUMMARY: Total Inputs=$cableFuzzCount, Crashes=$cableCrashes, Invariant Violations=$cableInvariantViolations")
        assertEquals(0, cableCrashes)
        assertEquals(0, cableInvariantViolations)

        // Fuzzing TransformerEngine & ShortCircuitEngine (10,000 iterations)
        var txFuzzCount = 0
        var txCrashes = 0
        var scCrashes = 0
        var scInvariantViolations = 0

        for (i in 1..10000) {
            txFuzzCount++
            val s = rng.nextDouble(10.0, 5000.0)
            val pz = rng.nextDouble(0.5, 20.0)
            val xr = rng.nextDouble(1.0, 30.0)
            val z0r = rng.nextDouble(0.1, 5.0)

            val tx = TransformerEntity(
                transformerId = "FUZZ-TX-$i",
                kVA = s,
                percentZ = pz,
                xr = xr,
                z0OverZ1 = z0r,
                status = "Active",
                sizeMode = "Manual",
                manualKVA = s
            )

            try {
                val txRes = TransformerEngine.calculate(tx, emptyList())

                // Invariant checks on transformer result
                if (txRes.zt.isNaN() || !txRes.zt.isFinite() || txRes.zt < 0.0 ||
                    txRes.isc3Phase.isNaN() || !txRes.isc3Phase.isFinite() || txRes.isc3Phase < 0.0
                ) {
                    println("Fuzz Tx Invariant Violation: Zt=${txRes.zt}, Isc=${txRes.isc3Phase}")
                }

                // ShortCircuitEngine calculate for a dummy circuit
                val circ = CircuitEntity(id = i, circuitName = "MDB-$i", fedFrom = "Transformer", transformerId = tx.transformerId, length_m = rng.nextDouble(1.0, 200.0))
                val dummySizing = SizingResult(
                    Ib_A = 100.0, deviceSize_A = 125.0, deviceType = "MCCB", cTotalDerating = 1.0, iRequired_perCable_A = 100.0,
                    productCode = "CX1-T104-G12", cableSize_mm2 = "25 mm²", ratedI_table_A = 150.0, capacity_derated_Iz_A = 150.0,
                    utilizationPct = 66.7, neutral_mm2 = "25 mm²", earthCPC_mm2 = "25 mm²", voltDrop_V = 1.0, vdPct = 0.25,
                    cumulativeVDpct = 0.25, status = "✓ PASS", failReason = "", cableMaterial = "Cu", cableOD_mm = 24.3,
                    deviceBreakingKA = 25.0, kFactor = 143.0, clearingTime_s = 0.1, rUsed = 0.927, xUsed = 0.082
                )
                val scResults = ShortCircuitEngine.calculate(
                    resolvedCircuits = listOf(circ),
                    circuitParents = mapOf(i to 0),
                    sizingResults = mapOf(i to dummySizing),
                    transformerResults = mapOf(tx.transformerId to txRes)
                )

                val scRes = scResults[i]
                if (scRes != null && scRes.status == "PASS") {
                    if (scRes.iscKA.isNaN() || !scRes.iscKA.isFinite() || scRes.iscKA <= 0.0 ||
                        scRes.ipKA.isNaN() || !scRes.ipKA.isFinite() || scRes.ipKA < scRes.iscKA
                    ) {
                        scInvariantViolations++
                        println("Fuzz ShortCircuit Invariant Violation: Isc=${scRes.iscKA}, ip=${scRes.ipKA}")
                    }
                }
            } catch (e: Exception) {
                scCrashes++
                println("Fuzz Tx/SC Exception: ${e.message}")
            }
        }

        println("LEVEL 6 FUZZING TX & SC SUMMARY: Total Inputs=$txFuzzCount, Crashes=$scCrashes, Invariant Violations=$scInvariantViolations")
        assertEquals(0, scCrashes)
        assertEquals(0, scInvariantViolations)
    }
}
