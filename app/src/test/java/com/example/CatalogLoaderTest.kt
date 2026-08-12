package com.example

import com.example.data.catalog.CatalogRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CatalogLoaderTest {

    private fun findAssetFile(filename: String): File {
        val candidates = listOf(
            File("app/src/main/assets/$filename"),
            File("src/main/assets/$filename"),
            File("../app/src/main/assets/$filename")
        )
        return candidates.firstOrNull { it.exists() }
            ?: throw java.io.FileNotFoundException("Asset file not found in candidates: $filename")
    }

    @Test
    fun testCatalogRecordCountsAssertion() {
        val repository = CatalogRepository()

        val cableCatalogJson = findAssetFile("cable_catalog.json").readText()
        val cableTypesDataJson = findAssetFile("cable_types_data.json").readText()
        val breakerCatalogJson = findAssetFile("breaker_catalog.json").readText()
        val accessoryCatalogJson = findAssetFile("accessory_catalog.json").readText()
        val deratingDataJson = findAssetFile("derating_data.json").readText()
        val transformersSeedJson = findAssetFile("transformers_seed.json").readText()

        val diagnostics = repository.loadAllFromStrings(
            cableCatalogJson = cableCatalogJson,
            cableTypesDataJson = cableTypesDataJson,
            breakerCatalogJson = breakerCatalogJson,
            accessoryCatalogJson = accessoryCatalogJson,
            deratingDataJson = deratingDataJson,
            transformersSeedJson = transformersSeedJson
        )

        println("Catalog Load Status: ${diagnostics.status}")
        println("Cable Products Count: ${diagnostics.cableCount}")
        println("Cable Types Data Count: ${diagnostics.cableTypesCount}")
        println("Breakers Count: ${diagnostics.breakerCount}")
        println("Accessories Count: ${diagnostics.accessoryCount}")
        println("Derating Rows Count: ${diagnostics.deratingCount}")
        println("Transformer Seeds Count: ${diagnostics.transformerSeedCount}")

        assertTrue("Cable products count must be > 0, was ${diagnostics.cableCount}", diagnostics.cableCount > 0)
        assertTrue("Cable types data count must be > 0, was ${diagnostics.cableTypesCount}", diagnostics.cableTypesCount > 0)
        assertTrue("Breaker catalog count must be > 0, was ${diagnostics.breakerCount}", diagnostics.breakerCount > 0)
        assertTrue("Accessory catalog count must be > 0, was ${diagnostics.accessoryCount}", diagnostics.accessoryCount > 0)
        assertTrue("Derating rows count must be > 0, was ${diagnostics.deratingCount}", diagnostics.deratingCount > 0)
        assertTrue("Transformers seed count must be > 0, was ${diagnostics.transformerSeedCount}", diagnostics.transformerSeedCount > 0)

        assertEquals("Catalog status must be COMPLETE", "COMPLETE", diagnostics.status)
    }

    @Test
    fun testDefensiveParsingOnMalformedField() {
        val repository = CatalogRepository()

        val badCableCatalog = """[{"mfg": "Test", "typeCode": "NOT_AN_INT", "size": "4.0"}]"""
        val badCableTypes = """[{"TypeCode": 1, "SZ (mm²)": "6.0"}]"""
        val badBreakers = """[{"In (A)": "invalid"}]"""
        val badAccessories = """[{"Size Min (mm²)": "N/A"}]"""
        val badDerating = """[{"AIR_TEMP": "forty"}]"""
        val badTransformers = """[{"kVA": "100.0"}]"""

        val diag = repository.loadAllFromStrings(
            badCableCatalog, badCableTypes, badBreakers, badAccessories, badDerating, badTransformers
        )

        assertNotNull(diag)
        assertEquals(1, repository.cableProducts.size)
        assertEquals(4.0, repository.cableProducts[0].size, 0.001)
        assertEquals(0, repository.cableProducts[0].typeCode) // Defaulted safely
    }

    @Test
    fun testIecGenericCatalogAndDynamicManufacturers() {
        val repository = CatalogRepository()
        val cableCatalogJson = findAssetFile("cable_catalog.json").readText()
        val cableTypesDataJson = findAssetFile("cable_types_data.json").readText()
        val breakerCatalogJson = findAssetFile("breaker_catalog.json").readText()
        val accessoryCatalogJson = findAssetFile("accessory_catalog.json").readText()
        val deratingDataJson = findAssetFile("derating_data.json").readText()
        val transformersSeedJson = findAssetFile("transformers_seed.json").readText()

        repository.loadAllFromStrings(
            cableCatalogJson, cableTypesDataJson, breakerCatalogJson,
            accessoryCatalogJson, deratingDataJson, transformersSeedJson
        )

        val manufacturers = repository.cableProducts.map { it.mfg.trim() }.distinct().sorted()
        println("Dynamic Manufacturers Discovered: $manufacturers")

        assertTrue("Must contain Elsewedy", manufacturers.contains("Elsewedy"))
        assertTrue("Must contain IEC-Generic", manufacturers.contains("IEC-Generic"))

        val iecRows = repository.cableProducts.filter { it.mfg.trim().equals("IEC-Generic", ignoreCase = true) }
        println("Actual IEC Rows Count: ${iecRows.size}")
        assertTrue("Must load at least 110 IEC-Generic rows, was ${iecRows.size}", iecRows.size >= 110)

        // Verify the 5 echo check rows
        val r1 = iecRows.first { it.typeCode == 1 && it.size == 1.5 }
        assertEquals(17.5, r1.air, 0.001)
        assertEquals(19.0, r1.gnd, 0.001)
        assertEquals(18.0, r1.duct, 0.001)

        val r2 = iecRows.first { it.typeCode == 1 && it.size == 240.0 }
        assertEquals(403.0, r2.air, 0.001)

        val r3 = iecRows.first { it.typeCode == 7 && it.size == 240.0 }
        assertEquals(538.0, r3.air, 0.001)

        val r4 = iecRows.first { it.typeCode == 9 && it.size == 95.0 }
        assertEquals(232.0, r4.air, 0.001)

        val r5 = iecRows.first { it.typeCode == 15 && it.size == 16.0 }
        assertEquals(100.0, r5.air, 0.001)
    }

    @Test
    fun testIecGenericDimensionFallback() {
        val repository = CatalogRepository()
        val cableCatalogJson = findAssetFile("cable_catalog.json").readText()
        val cableTypesDataJson = findAssetFile("cable_types_data.json").readText()
        val breakerCatalogJson = findAssetFile("breaker_catalog.json").readText()
        val accessoryCatalogJson = findAssetFile("accessory_catalog.json").readText()
        val deratingDataJson = findAssetFile("derating_data.json").readText()
        val transformersSeedJson = findAssetFile("transformers_seed.json").readText()

        repository.loadAllFromStrings(
            cableCatalogJson, cableTypesDataJson, breakerCatalogJson,
            accessoryCatalogJson, deratingDataJson, transformersSeedJson
        )

        val iecEntry = repository.cableProducts.first { it.mfg == "IEC-Generic" && it.typeCode == 1 && it.size == 240.0 }
        assertEquals("IEC row raw OD is 0.0", 0.0, iecEntry.od, 0.001)

        val dimInfo = com.example.engine.CableSizingEngine.resolveCableDimensions(
            mfg = iecEntry.mfg,
            typeCode = iecEntry.typeCode,
            size = iecEntry.size,
            rawOD = iecEntry.od,
            rawWT = iecEntry.wt,
            catalogProducts = repository.cableProducts
        )

        assertTrue("Dimension is marked as approximated", dimInfo.isApproximated)
        assertEquals("Elsewedy", dimInfo.sourceMfg)
        assertTrue("OD borrowed from Elsewedy 240mm² reference (>40mm)", dimInfo.od_mm > 40.0)
        assertTrue("WT borrowed from Elsewedy 240mm² reference (>1000 kg/km)", dimInfo.wt_kg_km > 1000.0)
        println("IEC 240mm² Fallback Result: OD=${dimInfo.od_mm}mm, WT=${dimInfo.wt_kg_km}kg/km, Source=${dimInfo.sourceMfg}")
    }

    @Test
    fun testDucabCatalogImportAndEchoVerification() {
        val repository = CatalogRepository()
        val cableCatalogJson = findAssetFile("cable_catalog.json").readText()
        val cableTypesDataJson = findAssetFile("cable_types_data.json").readText()
        val breakerCatalogJson = findAssetFile("breaker_catalog.json").readText()
        val accessoryCatalogJson = findAssetFile("accessory_catalog.json").readText()
        val deratingDataJson = findAssetFile("derating_data.json").readText()
        val transformersSeedJson = findAssetFile("transformers_seed.json").readText()

        repository.loadAllFromStrings(
            cableCatalogJson, cableTypesDataJson, breakerCatalogJson,
            accessoryCatalogJson, deratingDataJson, transformersSeedJson
        )

        val ducabRows = repository.cableProducts.filter { it.mfg.trim().equals("Ducab", ignoreCase = true) }
        assertEquals("Must load 122 Ducab rows", 122, ducabRows.size)

        // Echo 1: Cu/SWA 3C 240mm² (typeCode 8, size 240.0) -> gnd: 530, duct: 440, air: 546, od: 55.1, wt: 10755
        val e1 = ducabRows.first { it.typeCode == 8 && it.size == 240.0 }
        assertEquals(530.0, e1.gnd, 0.001)
        assertEquals(440.0, e1.duct, 0.001)
        assertEquals(546.0, e1.air, 0.001)
        assertEquals(55.1, e1.od, 0.001)
        assertEquals(10755.0, e1.wt, 0.001)

        // Echo 2: Al/unarm 3C 240mm² (typeCode 9, size 240.0) -> gnd: 415, duct: 340, air: 409, od: 49.5, wt: 3020
        val e2 = ducabRows.first { it.typeCode == 9 && it.size == 240.0 }
        assertEquals(415.0, e2.gnd, 0.001)
        assertEquals(340.0, e2.duct, 0.001)
        assertEquals(409.0, e2.air, 0.001)
        assertEquals(49.5, e2.od, 0.001)
        assertEquals(3020.0, e2.wt, 0.001)

        // Echo 3: Cu 1C 240mm² (typeCode 13, size 240.0) -> gnd: 570, duct: 580, air: 606, od: 27.7, wt: 2510
        val e3 = ducabRows.first { it.typeCode == 13 && it.size == 240.0 }
        assertEquals(570.0, e3.gnd, 0.001)
        assertEquals(580.0, e3.duct, 0.001)
        assertEquals(606.0, e3.air, 0.001)
        assertEquals(27.7, e3.od, 0.001)
        assertEquals(2510.0, e3.wt, 0.001)

        // Echo 4: Cu/unarm 4C 16mm² (typeCode 15, size 16.0) -> gnd: 120, duct: 93, air: 100, od: 20.0, wt: 780
        val e4 = ducabRows.first { it.typeCode == 15 && it.size == 16.0 }
        assertEquals(120.0, e4.gnd, 0.001)
        assertEquals(93.0, e4.duct, 0.001)
        assertEquals(100.0, e4.air, 0.001)
        assertEquals(20.0, e4.od, 0.001)
        assertEquals(780.0, e4.wt, 0.001)

        // Echo 5: Al/SWA 4C 120mm² (typeCode 76, size 120.0) -> gnd: 275, duct: 230, air: 264, od: 47.1, wt: 3950
        val e5 = ducabRows.first { it.typeCode == 76 && it.size == 120.0 }
        assertEquals(275.0, e5.gnd, 0.001)
        assertEquals(230.0, e5.duct, 0.001)
        assertEquals(264.0, e5.air, 0.001)
        assertEquals(47.1, e5.od, 0.001)
        assertEquals(3950.0, e5.wt, 0.001)
    }

    @Test
    fun auditAccessoryCoverageGaps() {
        val repository = CatalogRepository()
        val cableCatalogJson = findAssetFile("cable_catalog.json").readText()
        val cableTypesDataJson = findAssetFile("cable_types_data.json").readText()
        val breakerCatalogJson = findAssetFile("breaker_catalog.json").readText()
        val accessoryCatalogJson = findAssetFile("accessory_catalog.json").readText()
        val deratingDataJson = findAssetFile("derating_data.json").readText()
        val transformersSeedJson = findAssetFile("transformers_seed.json").readText()

        repository.loadAllFromStrings(
            cableCatalogJson, cableTypesDataJson, breakerCatalogJson,
            accessoryCatalogJson, deratingDataJson, transformersSeedJson
        )

        val accessories = repository.accessories
        println("===================================================================")
        println("1. ACCESSORY CATALOG OVERVIEW")
        println("===================================================================")
        println("Total accessory entries in repository: ${accessories.size}")

        val glands = accessories.filter { it.category.equals("Cable Gland", ignoreCase = true) }
        val cwGlands = glands.filter { it.subCategory.contains("CW", ignoreCase = true) }.sortedBy { it.odMin }
        val a2Glands = glands.filter { it.subCategory.contains("A2", ignoreCase = true) || !it.subCategory.contains("CW", ignoreCase = true) }.sortedBy { it.odMin }

        println("CW (Armoured) Glands count: ${cwGlands.size}")
        cwGlands.forEach { g ->
            println("  Part: ${g.partNumber} | Sub: ${g.subCategory} | OD Range: [${g.odMin} - ${g.odMax}] mm | Size Range: [${g.sizeMin} - ${g.sizeMax}] mm²")
        }

        println("\nA2 (Unarmoured) Glands count: ${a2Glands.size}")
        a2Glands.forEach { g ->
            println("  Part: ${g.partNumber} | Sub: ${g.subCategory} | OD Range: [${g.odMin} - ${g.odMax}] mm | Size Range: [${g.sizeMin} - ${g.sizeMax}] mm²")
        }

        println("\n--- Gaps in CW (Armoured) Glands ---")
        var cwGapCount = 0
        for (i in 0 until cwGlands.size - 1) {
            val curr = cwGlands[i]
            val next = cwGlands[i + 1]
            if (next.odMin > curr.odMax + 0.01) {
                cwGapCount++
                println("[GAP CW #$cwGapCount] Between ${curr.partNumber} (max ${curr.odMax}mm) and ${next.partNumber} (min ${next.odMin}mm): Uncovered [${curr.odMax} mm - ${next.odMin} mm]")
            }
        }
        if (cwGapCount == 0) println("[NO GAPS IN CW GLANDS]")

        println("\n--- Gaps in A2 (Unarmoured) Glands ---")
        var a2GapCount = 0
        for (i in 0 until a2Glands.size - 1) {
            val curr = a2Glands[i]
            val next = a2Glands[i + 1]
            if (next.odMin > curr.odMax + 0.01) {
                a2GapCount++
                println("[GAP A2 #$a2GapCount] Between ${curr.partNumber} (max ${curr.odMax}mm) and ${next.partNumber} (min ${next.odMin}mm): Uncovered [${curr.odMax} mm - ${next.odMin} mm]")
            }
        }
        if (a2GapCount == 0) println("[NO GAPS IN A2 GLANDS]")

        val cables = repository.cableProducts
        println("\n===================================================================")
        println("2. CROSS-CHECK AGAINST ALL ${cables.size} CABLE CATALOG ENTRIES")
        println("===================================================================")

        var noGlandCount = 0
        val missingBreakdown = mutableListOf<String>()

        cables.forEach { c ->
            val isArmoured = c.typeCode in listOf(3, 4, 8, 10, 68, 70, 72, 75, 76)
            val candidates = if (isArmoured) cwGlands else a2Glands

            val (resolvedOD, _, _) = com.example.engine.CableSizingEngine.resolveCableDimensions(
                mfg = c.mfg, typeCode = c.typeCode, size = c.size, rawOD = c.od, rawWT = c.wt, catalogProducts = cables
            )

            val matches = candidates.filter { resolvedOD >= (it.odMin - 1e-6) && resolvedOD <= (it.odMax + 1e-6) }
            if (matches.isEmpty()) {
                noGlandCount++
                val reqGroup = if (isArmoured) "CW" else "A2"
                val minCap = candidates.minOfOrNull { it.odMin } ?: 0.0
                val maxCap = candidates.maxOfOrNull { it.odMax } ?: 0.0
                val reason = when {
                    resolvedOD < minCap -> "OD_TOO_SMALL (< ${minCap}mm)"
                    resolvedOD > maxCap -> "OD_TOO_LARGE (> ${maxCap}mm)"
                    else -> "OD_IN_GAP"
                }
                missingBreakdown.add("${c.mfg} | ${c.model.ifEmpty { "TypeCode " + c.typeCode }} | Size: ${c.size} mm² | OD: ${resolvedOD} mm | Group: $reqGroup | Reason: $reason")
            }
        }

        println("Total Cables Evaluated: ${cables.size}")
        println("Cables with NO MATCHING GLAND: $noGlandCount / ${cables.size}")
        missingBreakdown.forEach { println("  - $it") }
    }
}
