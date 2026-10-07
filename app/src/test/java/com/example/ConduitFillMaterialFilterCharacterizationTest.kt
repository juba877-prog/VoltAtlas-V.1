package com.example

import com.example.data.Catalog
import com.example.data.ConduitCatalogEntry
import com.example.engine.ConduitFillEngine
import com.example.engine.ConduitFillInput
import com.example.ui.screens.quick.ConduitFillQuickInput
import com.example.ui.screens.quick.ConduitTypeOption
import com.example.ui.screens.quick.QuickConduitFillEngine
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * P1-8 (I-03 / AB-B09) / ECT-D-EE-11 TARGET REGRESSION — protected engine.
 *
 * Previously, the strict-conformance engine (ConduitFillEngine.kt:247-249) filtered its catalog with
 * `type == input.type || input.conduitType == "Rigid PVC"` — the disjunct made the material
 * filter a NO-OP for Rigid PVC requests, admitting EMT/GI rows.
 *
 * Authorized by ECT-D-EE-11 (P1-08), strict material filtering was restored.
 * Both ConduitFillEngine and QuickConduitFillEngine now strictly filter by requested material.
 */
class ConduitFillMaterialFilterCharacterizationTest {

    /**
     * Areas (mm2, pi*d^2/4): PVC_20 = 196.07, GI_20 = 232.35, EMT_20 = 343.07, PVC_25 = 346.36.
     * With strict filtering, EMT_20 and GI_20 are excluded for Rigid PVC requests.
     */
    private val mixedCatalog = listOf(
        ConduitCatalogEntry("PVC_20", "Rigid PVC", "20 mm", 15.8),
        ConduitCatalogEntry("GI_20", "GI Steel (RMC)", "20 mm GI", 17.2),
        ConduitCatalogEntry("EMT_20", "EMT Steel", "20 mm EMT", 20.9),
        ConduitCatalogEntry("PVC_25", "Rigid PVC", "25 mm", 21.0)
    )

    /**
     * 1 main cable d=6.0 (28.27 mm2) + manual CPC d=9.2 (66.48 mm2) = 94.75 mm2 total.
     * SEPARATE_CONDUIT -> 2 objects -> 31% fill -> required >= 305.65 mm2.
     */
    private fun pvcRequest() = ConduitFillInput(
        circuitId = 901,
        circuitName = "CHAR_RIGID_PVC",
        mainCableOD_mm = 6.0,
        manualCpcOD_mm = 9.2,
        cpcArrangement = "SEPARATE_CONDUIT",
        designProfile = "PROJECT_DESIGN",
        conduitType = "Rigid PVC"
    )

    @Test
    fun rigidPvcRequestStrictlyFiltersMaterial_targetRegression() {
        val res = ConduitFillEngine.calculate(pvcRequest(), mixedCatalog)

        // Strict material filtering admits only PVC rows:
        // PVC_20 (196.07 mm2) has 48.3% fill > 31% (fails).
        // PVC_25 (346.36 mm2) has 27.4% fill <= 31% (passes).
        // EMT_20 (343.07 mm2) is strictly rejected because it is EMT Steel.
        assertEquals(
            "ECT-D-EE-11 / P1-08 target: a 'Rigid PVC' request strictly recommends a PVC " +
                "conduit and rejects EMT/GI candidates.",
            "25 mm",
            res.recommendedTradeSize
        )
        assertEquals(
            "Target companion: the recommended row is PVC_25, not an EMT/GI entry.",
            "PVC_25",
            res.recommendedConduitId
        )
    }

    @Test
    fun quickToolFiltersStrictlyByMaterial_documentsParityGap() {
        val original = Catalog.conduits
        try {
            Catalog.conduits = mixedCatalog
            val quick = QuickConduitFillEngine.calculate(
                ConduitFillQuickInput(
                    conduitType = ConduitTypeOption.RIGID_PVC,
                    mainCableCount = 1,
                    mainCableOD_mm = 6.0,
                    cpcCableCount = 1,
                    cpcCableOD_mm = 9.2
                )
            )
            // Strict material match keeps only the PVC rows: 196.07 too small, 25 mm (346.36) fits.
            assertEquals(
                "P1-8 reference behavior: the quick tool's material filter is strict — it must " +
                    "recommend the PVC size and never the EMT row the engine counterpart admits.",
                "25 mm",
                quick.recommendedTradeSize
            )
        } finally {
            Catalog.conduits = original
        }
    }
}
