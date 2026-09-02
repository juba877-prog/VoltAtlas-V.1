package com.example.sld

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 5 §5.1 — Industrial Vector Symbol Expansion Unit Tests.
 */
class SldSymbolExpansionTest {

    @Test
    fun newIndustrialSymbols_areRegisteredInExtendedLibrary() {
        val requiredSymbols = listOf(
            SldSymbols.IecSymbolType.ATS_3_TERMINAL,
            SldSymbols.IecSymbolType.UPS_STATIC_ONLINE,
            SldSymbols.IecSymbolType.TIE_BREAKER_NO,
            SldSymbols.IecSymbolType.TRANSFORMER_DYN11,
            SldSymbols.IecSymbolType.GENERATOR_GENSET,
            SldSymbols.IecSymbolType.CT_VT_EARTH
        )

        val extendedMap = SldSymbolLibrary.extendedSymbols.associateBy { it.symbolType }

        requiredSymbols.forEach { sym ->
            val spec = extendedMap[sym]
            assertNotNull("Symbol $sym must be registered in SldSymbolLibrary.extendedSymbols", spec)
            assertEquals("Symbol $sym must be audited as CONFORMANT", SymbolAuditStatus.CONFORMANT, spec!!.auditStatus)
            assertTrue("Symbol $sym must reference valid IEC standard", spec.iecReference.startsWith("IEC 60617"))
            assertTrue("Symbol $sym must describe canonical form", spec.canonicalForm.isNotBlank())
            assertTrue("Symbol $sym must point to SldSymbols drawing implementation", spec.voltAtlasImplementation.isNotBlank())
        }
    }

    @Test
    fun extendedLibrary_coversAllIecSymbolTypesWithoutGaps() {
        val allEnumTypes = SldSymbols.IecSymbolType.values().toSet()
        val allRegisteredTypes = SldSymbolLibrary.extendedSymbols.map { it.symbolType }.toSet()

        assertEquals(
            "Extended library must have 100% 1-to-1 coverage of all IecSymbolTypes",
            allEnumTypes,
            allRegisteredTypes
        )
    }
}
