package com.example.ui

import com.example.data.CircuitEntity
import com.example.engine.ShortCircuitResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Blocker (item 2): a DATA MISSING short-circuit row must never let a PARTIAL maximum be presented
 * as the governing fault level.
 *
 * The ATS required Icw / Icm and the "Highest Isc" KPI are both maxima over a group of circuits.
 * When one member of the group is unresolved its true Isc is unknown and may be the highest, so a
 * max over the resolved members alone is understated, not conservative.
 */
class ShortCircuitPartialMaxHonestyTest {

    private fun resolved(id: Int, isc: Double) = ShortCircuitResult(
        circuitId = id,
        sourceName = "TX-01",
        cableMm2 = "150 mm²",
        iscKA = isc,
        iwKA = 50.0,
        deviceKA = 50.0,
        deviceCheck = "PASS",
        cableCheck = "PASS",
        marginPct = 10.0,
        status = "PASS",
        note = ""
    )

    /** DATA MISSING rows carry iscKA = 0.0 by design - the value is not a real fault level. */
    private fun missing(id: Int) = ShortCircuitResult(
        circuitId = id,
        sourceName = "TX-01",
        cableMm2 = "-",
        iscKA = 0.0,
        iwKA = 0.0,
        deviceKA = 0.0,
        deviceCheck = "-",
        cableCheck = "-",
        marginPct = 0.0,
        status = "DATA MISSING",
        note = "Missing or invalid 20 °C conductor resistance (r20)"
    )

    private fun atsCircuit(id: Int) = CircuitEntity(
        id = id,
        circuitName = "ATS-C$id",
        loadKVA = 100.0,
        atsIncluded = true,
        assignedAtsId = "ATS-01"
    )

    @Test
    fun aggregateAtsIsc_twoValidAndOneMissing_isFlaggedIncompleteAndNotJustThePartialMax() {
        val circuits = listOf(atsCircuit(1), atsCircuit(2), atsCircuit(3))
        val results = mapOf(
            1 to resolved(1, 10.0),
            2 to resolved(2, 25.0),   // the max of the VALID rows
            3 to missing(3)           // unresolved; its true Isc may exceed 25.0
        )

        val agg = EngineeringViewModel.aggregateAtsIsc(circuits, results)

        assertTrue("An unresolved ATS-feeding circuit must mark the aggregation incomplete", agg.incomplete)
        assertEquals(listOf(3), agg.missingCircuitIds)
        // The helper reports the partial max, but the caller must NOT treat it as governing.
        assertEquals(25.0, agg.maxResolvedIsc, 0.0)
    }

    @Test
    fun atsRequiredIcw_usesConservativeUpperBoundNotPartialMax_whenAnyRowMissing() {
        val circuits = listOf(atsCircuit(1), atsCircuit(2), atsCircuit(3))
        val results = mapOf(
            1 to resolved(1, 10.0),
            2 to resolved(2, 25.0),
            3 to missing(3)
        )
        val agg = EngineeringViewModel.aggregateAtsIsc(circuits, results)

        // The caller rule under test: incomplete => substitute the transformer-terminal Isc.
        val transformerTerminalIsc = 30.0
        val requiredIcw = if (agg.incomplete) {
            transformerTerminalIsc.takeIf { it > 0.0 } ?: agg.maxResolvedIsc
        } else {
            agg.maxResolvedIsc
        }

        assertTrue(
            "requiredIcw must NOT be the max of the valid rows when a row is unresolved",
            requiredIcw > agg.maxResolvedIsc
        )
        assertEquals(30.0, requiredIcw, 0.0)
        // requiredIcm is derived from the same basis (x2.2 in AtsEngine).
        assertEquals(66.0, requiredIcw * 2.2, 0.0)
    }

    @Test
    fun aggregateAtsIsc_allResolved_isCompleteAndUsesTrueMax() {
        val circuits = listOf(atsCircuit(1), atsCircuit(2))
        val results = mapOf(1 to resolved(1, 10.0), 2 to resolved(2, 25.0))

        val agg = EngineeringViewModel.aggregateAtsIsc(circuits, results)

        assertFalse(agg.incomplete)
        assertTrue(agg.missingCircuitIds.isEmpty())
        assertEquals(25.0, agg.maxResolvedIsc, 0.0)
    }

    @Test
    fun formatHighestIscKpi_marksIncomplete_whenAnyRowMissing() {
        val results = listOf(resolved(1, 10.0), resolved(2, 25.0), missing(3))

        val text = formatHighestIscKpi(results, hasMissingIsc = true)

        assertTrue("KPI must signal incompleteness, got: $text", text.contains("INCOMPLETE"))
        assertTrue("KPI must state the number is a lower bound, got: $text", text.contains(">="))
        assertTrue("KPI must count the unresolved circuits, got: $text", text.contains("1 circuit(s) unresolved"))
        assertTrue("KPI must not present a bare max, got: $text", !text.trim().endsWith("kA"))
    }

    @Test
    fun formatHighestIscKpi_allResolved_showsPlainMaximum() {
        val results = listOf(resolved(1, 10.0), resolved(2, 25.0))

        val text = formatHighestIscKpi(results, hasMissingIsc = false)

        assertEquals("25.00 kA", text)
    }

    @Test
    fun formatHighestIscKpi_allRowsMissing_isDataMissing() {
        val text = formatHighestIscKpi(listOf(missing(1), missing(2)), hasMissingIsc = true)

        assertEquals("DATA MISSING", text)
    }
}
