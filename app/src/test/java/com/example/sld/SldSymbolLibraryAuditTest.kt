package com.example.sld

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Enforces the IEC 60617 symbol audit contract (Roadmap §1.1):
 *
 *  1. [SldSymbolLibrary.nodeTypeSymbols] is EXHAUSTIVE over [SldNodeType] —
 *     adding a node type without a symbol audit entry fails the build.
 *  2. Every audit record is fully documented (canonical name, IEC reference,
 *     canonical form, current implementation).
 *  3. Only [SldNodeType.UNKNOWN] may be a deliberate PLACEHOLDER — every real
 *     equipment class must have a concrete IEC audit verdict.
 *  4. The extended-library inventory covers every [SldSymbols.IecSymbolType],
 *     so gallery/device symbols cannot be added outside the audit either.
 */
class SldSymbolLibraryAuditTest {

    @Test
    fun nodeTypeSymbolMap_isExhaustiveOverSldNodeType() {
        val enumValues = SldNodeType.values().toSet()
        assertEquals(
            "SldSymbolLibrary.nodeTypeSymbols must contain exactly one entry per SldNodeType. " +
                "Missing: ${enumValues - SldSymbolLibrary.nodeTypeSymbols.keys}, " +
                "Unknown keys: ${SldSymbolLibrary.nodeTypeSymbols.keys - enumValues}",
            enumValues,
            SldSymbolLibrary.nodeTypeSymbols.keys
        )
    }

    @Test
    fun everyNodeSymbolSpec_isFullyDocumented() {
        SldSymbolLibrary.nodeTypeSymbols.values.forEach { spec ->
            assertTrue("[$spec] canonicalSymbol must be documented", spec.canonicalSymbol.isNotBlank())
            assertTrue("[$spec] iecReference must be documented", spec.iecReference.isNotBlank())
            assertTrue("[$spec] canonicalForm must be documented", spec.canonicalForm.isNotBlank())
            assertTrue("[$spec] voltAtlasImplementation must be documented", spec.voltAtlasImplementation.isNotBlank())
            if (spec.auditStatus != SymbolAuditStatus.CONFORMANT) {
                assertTrue(
                    "${spec.nodeType} is ${spec.auditStatus} — a non-conformant entry must explain itself in notes",
                    spec.notes.isNotBlank()
                )
            }
        }
    }

    @Test
    fun onlyUnknownNodeType_mayBePlaceholder() {
        val placeholders = SldSymbolLibrary.nodeTypeSymbols.values
            .filter { it.auditStatus == SymbolAuditStatus.PLACEHOLDER }
        assertEquals(
            "Only SldNodeType.UNKNOWN may be audited as PLACEHOLDER; found: ${placeholders.map { it.nodeType }}",
            setOf(SldNodeType.UNKNOWN),
            placeholders.map { it.nodeType }.toSet()
        )
    }

    @Test
    fun extendedLibrary_coversEveryIecSymbolType() {
        val enumValues = SldSymbols.IecSymbolType.values().toSet()
        val inventory = SldSymbolLibrary.extendedSymbols.map { it.symbolType }.toSet()
        assertEquals(
            "SldSymbolLibrary.extendedSymbols must cover every IecSymbolType. " +
                "Missing: ${enumValues - inventory}, Unknown: ${inventory - enumValues}",
            enumValues,
            inventory
        )
    }

    @Test
    fun extendedLibrary_specsAreFullyDocumented() {
        SldSymbolLibrary.extendedSymbols.forEach { spec ->
            assertTrue("[${spec.symbolType}] iecReference must be documented", spec.iecReference.isNotBlank())
            assertTrue("[${spec.symbolType}] canonicalForm must be documented", spec.canonicalForm.isNotBlank())
            assertTrue("[${spec.symbolType}] voltAtlasImplementation must be documented", spec.voltAtlasImplementation.isNotBlank())
            if (spec.auditStatus != SymbolAuditStatus.CONFORMANT) {
                assertTrue(
                    "${spec.symbolType} is ${spec.auditStatus} — a non-conformant entry must explain itself in notes",
                    spec.notes.isNotBlank()
                )
            }
        }
    }

    @Test
    fun specFor_neverReturnsNullForAnyNodeType() {
        SldNodeType.values().forEach { nodeType ->
            assertEquals(nodeType, SldSymbolLibrary.specFor(nodeType).nodeType)
        }
    }
}