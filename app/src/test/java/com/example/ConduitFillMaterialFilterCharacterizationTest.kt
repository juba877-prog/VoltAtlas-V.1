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
 * P1-8 (I-03 / AB-B09) CHARACTERIZATION — protected engine.
 *
 * The strict-conformance engine (ConduitFillEngine.kt:247-249) filters its catalog with
 * `type == input.type || input.conduitType == "Rigid PVC"` — the disjunct makes the material
 * filter a NO-OP for Rigid PVC requests, admitting EMT/GI rows. The quick tool
 * (QuickConduitFillEngine.kt:217-219) filters strictly by material.
 *
 * `com.example.engine.*` is PROTECTED by the Phase 6 protected-engine rule, so this file
 * LOCKS today's defective behavior instead of changing it. If an assertion here fails, the
 * protected engine's material filter changed — that is either the landed fix (update these
 * expectations to the strict-material contract consciously) or a regression. Do not weaken
 * the assertions to keep them green.
 */
class ConduitFillMaterialFilterCharacterizationTest {

    /**
     * Areas (mm2, pi*d^2/4): PVC_20 = 196.07, GI_20 = 232.35, EMT_20 = 343.07, PVC_25 = 346.36.
     * EMT_20 is both early in the list and the smallest adequate area, so the engine's
     * area-sorted `firstOrNull { fill <= allowed }` picks it under EITHER ordering hypothesis.
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
    fun rigidPvcRequestRecommendsEmtRow_characterizesProtectedDefect() {
        val res = ConduitFillEngine.calculate(pvcRequest(), mixedCatalog)

        // fill ratios: 94.75/196.07 = 48.3%, 94.75/232.35 = 40.8%, 94.75/343.07 = 27.6% (<= 31 OK)
        assertEquals(
            "CHARACTERIZATION (P1-8 / I-03 / AB-B09): a 'Rigid PVC' request recommends an EMT " +
                "Steel conduit because the protected filter ORs in every catalog row for Rigid " +
                "PVC. This assertion LOCKS the current DEFECTIVE behavior — if it fails, the " +
                "protected engine's material filter changed; update this test consciously.",
            "20 mm EMT",
            res.recommendedTradeSize
        )
        assertEquals(
            "Characterization companion: the recommended row is the EMT entry itself.",
            "EMT_20",
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
