package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * P1-7 (I-01) regression proof for the authoritative catalog status rule.
 *
 * The pre-fix rule validated only cable types / data sheet / cables / breakers / accessories —
 * conduits and busways were silently ignored, so a catalog with zero conduit or busway rows
 * (or rows whose provenance/status keys were missing from the JSON) still reported COMPLETE.
 * [computeCatalogStatus] is the single source of truth consumed by Catalog.getDiagnostics().
 */
class CatalogStatusRuleTest {

    private fun status(
        loadError: Boolean = false,
        cableTypesEmpty: Boolean = false,
        dataSheetEmpty: Boolean = false,
        cableCatalogEmpty: Boolean = false,
        breakersEmpty: Boolean = false,
        accessoriesEmpty: Boolean = false,
        conduitsEmpty: Boolean = false,
        buswaysEmpty: Boolean = false,
        unverifiedProvenance: Boolean = false,
        unsupportedFamilies: Int = 0
    ) = computeCatalogStatus(
        hasLoadError = loadError,
        cableTypesEmpty = cableTypesEmpty,
        dataSheetEmpty = dataSheetEmpty,
        cableCatalogEmpty = cableCatalogEmpty,
        breakersEmpty = breakersEmpty,
        accessoriesEmpty = accessoriesEmpty,
        conduitsEmpty = conduitsEmpty,
        buswaysEmpty = buswaysEmpty,
        hasUnverifiedProvenance = unverifiedProvenance,
        unsupportedFamilies = unsupportedFamilies
    )

    @Test
    fun everyTableVerifiedIsComplete() {
        assertEquals("COMPLETE", status())
    }

    @Test
    fun emptyConduitTableIsInvalid() {
        // Regression: the pre-fix rule never examined conduits — an asset-less conduit
        // catalog still reported COMPLETE.
        assertEquals("INVALID", status(conduitsEmpty = true))
    }

    @Test
    fun emptyBuswayTableIsInvalid() {
        assertEquals("INVALID", status(buswaysEmpty = true))
    }

    @Test
    fun blankProvenanceOrStatusIsInvalid() {
        // Parser absence (missing JSON keys -> blank) must surface as INVALID, never as a
        // fabricated APPROVED default.
        assertEquals("INVALID", status(unverifiedProvenance = true))
    }

    @Test
    fun coreTableEmptiesRemainInvalid() {
        assertEquals("INVALID", status(breakersEmpty = true))
        assertEquals("INVALID", status(accessoriesEmpty = true))
        assertEquals("INVALID", status(cableCatalogEmpty = true))
        assertEquals("INVALID", status(cableTypesEmpty = true))
        assertEquals("INVALID", status(dataSheetEmpty = true))
    }

    @Test
    fun loadErrorRemainsInvalid() {
        assertEquals("INVALID", status(loadError = true))
    }

    @Test
    fun unsupportedFamiliesRemainPartial() {
        assertEquals("PARTIAL", status(unsupportedFamilies = 3))
    }
}
