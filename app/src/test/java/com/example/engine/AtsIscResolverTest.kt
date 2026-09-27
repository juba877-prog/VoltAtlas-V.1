package com.example.engine

import com.example.data.CircuitEntity
import com.example.ui.formatHighestIscKpi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A DATA MISSING short-circuit row must never let a PARTIAL maximum be presented as the governing
 * fault level, and an ATS whose fault level cannot be resolved must surface as DATA MISSING rather
 * than a plausible-looking number.
 *
 * The ATS required Icw / Icm and the "Highest Isc" KPI are maxima over a group of circuits. When one
 * member of the group is unresolved its true Isc is unknown and may be the highest, so a max over
 * the resolved members alone is understated, not conservative.
 */
class AtsIscResolverTest {

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

    private fun atsCircuit(id: Int, atsId: String = "ATS-01") = CircuitEntity(
        id = id,
        circuitName = "ATS-C$id",
        loadKVA = 100.0,
        atsIncluded = true,
        assignedAtsId = atsId
    )

    @Test
    fun aggregateAtsIsc_twoValidAndOneMissing_isFlaggedIncompleteAndNotJustThePartialMax() {
        val circuits = listOf(atsCircuit(1), atsCircuit(2), atsCircuit(3))
        val results = mapOf(
            1 to resolved(1, 10.0),
            2 to resolved(2, 25.0),   // the max of the VALID rows
            3 to missing(3)           // unresolved; its true Isc may exceed 25.0
        )

        val agg = AtsIscResolver.aggregateFor("ATS-01", circuits, results)

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
        val agg = AtsIscResolver.aggregateFor("ATS-01", circuits, results)

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

        val agg = AtsIscResolver.aggregateFor("ATS-01", circuits, results)

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

    // ---- item 2: no silent partial-max fallback, and correct multi-transformer resolution ----

    @Test
    fun decideAtsIsc_incompleteAndNoTransformerIsc_isDataMissingNotPartialMax() {
        val agg = AtsIscAggregation(
            maxResolvedIsc = 25.0,          // 2 valid rows, max 25.0
            incomplete = true,
            missingCircuitIds = listOf(3)
        )

        val decision = AtsIscResolver.decide(agg, transformerIscKA = 0.0)

        assertTrue("No defensible basis => DATA MISSING", decision.dataMissing)
        assertEquals("Must not fall back to the partial max", 0.0, decision.iscKA, 0.0)
        assertTrue(decision.basis.contains("unresolved"))
    }

    @Test
    fun decideAtsIsc_incompleteWithTransformerIsc_usesUpperBound() {
        val agg = AtsIscAggregation(25.0, true, listOf(3))

        val decision = AtsIscResolver.decide(agg, transformerIscKA = 30.0)

        assertFalse(decision.dataMissing)
        assertEquals(30.0, decision.iscKA, 0.0)
        assertTrue(decision.iscKA > agg.maxResolvedIsc)
    }

    @Test
    fun decideAtsIsc_allResolved_usesTrueMax() {
        val agg = AtsIscAggregation(25.0, false, emptyList())

        val decision = AtsIscResolver.decide(agg, transformerIscKA = 30.0)

        assertFalse(decision.dataMissing)
        assertEquals(25.0, decision.iscKA, 0.0)
    }

    @Test
    fun resolveAtsFeedTransformerId_picksTheFeedingTransformerNotTheFirstInTheMap() {
        // ATS-01 is fed from TX-BUS-B. TX-A is first in the map but does NOT feed this ATS.
        val circuits = listOf(
            CircuitEntity(id = 1, circuitName = "C1", loadKVA = 100.0,
                assignedAtsId = "ATS-01", transformerId = "TX-BUS-B"),
            CircuitEntity(id = 2, circuitName = "MDB-A", loadKVA = 200.0, transformerId = "TX-A")
        )
        val parents = mapOf(1 to 0)

        val txId = AtsIscResolver.resolveFeedTransformerId("ATS-01", circuits, parents)

        assertEquals("Must resolve the feeding transformer, not an arbitrary one", "TX-BUS-B", txId)
    }

    @Test
    fun resolveAtsFeedTransformerId_walksHierarchyToTheFeedingTransformer() {
        // Sub-feeder has no transformerId; its parent MDB does.
        val circuits = listOf(
            CircuitEntity(id = 10, circuitName = "SUB", loadKVA = 50.0, assignedAtsId = "ATS-01"),
            CircuitEntity(id = 11, circuitName = "MDB", loadKVA = 200.0, transformerId = "TX-02")
        )
        val parents = mapOf(10 to 11, 11 to 0)

        val txId = AtsIscResolver.resolveFeedTransformerId("ATS-01", circuits, parents)

        assertEquals("TX-02", txId)
    }

    @Test
    fun resolveAtsFeedTransformerId_ambiguousSources_returnsNull() {
        // Two different transformers feed the same ATS -> cannot resolve unambiguously.
        val circuits = listOf(
            CircuitEntity(id = 1, circuitName = "C1", loadKVA = 100.0,
                assignedAtsId = "ATS-01", transformerId = "TX-A"),
            CircuitEntity(id = 2, circuitName = "C2", loadKVA = 100.0,
                assignedAtsId = "ATS-01", transformerId = "TX-B")
        )

        val txId = AtsIscResolver.resolveFeedTransformerId("ATS-01", circuits, mapOf(1 to 0, 2 to 0))

        assertNull("Ambiguous feed must not resolve to an arbitrary transformer", txId)
    }

    @Test
    fun resolveAtsFeedTransformerId_noFeeder_returnsNull() {
        val circuits = listOf(
            CircuitEntity(id = 1, circuitName = "C1", loadKVA = 100.0, transformerId = "TX-A")
        )

        assertNull(AtsIscResolver.resolveFeedTransformerId("ATS-99", circuits, mapOf(1 to 0)))
    }

    @Test
    fun aggregateAtsIscFor_scopeIsOnlyCircuitsAssignedToThisAts() {
        val circuits = listOf(
            atsCircuit(1, "ATS-01"),
            atsCircuit(2, "ATS-01"),
            atsCircuit(3, "ATS-02")   // belongs to a DIFFERENT ATS
        )
        val results = mapOf(
            1 to resolved(1, 10.0),
            2 to resolved(2, 25.0),
            3 to resolved(3, 99.0)    // must NOT leak into ATS-01
        )

        val agg = AtsIscResolver.aggregateFor("ATS-01", circuits, results)

        assertEquals("ATS-02 circuit must not contribute", 25.0, agg.maxResolvedIsc, 0.0)
        assertFalse(agg.incomplete)
    }

    @Test
    fun atsEnginePerInstanceMap_overridesSharedScalarForTheCorrectAts() {
        val ats1 = com.example.data.AtsEntity(atsId = "ATS-01")
        val ats2 = com.example.data.AtsEntity(atsId = "ATS-02")
        val circuits = listOf(
            CircuitEntity(id = 1, circuitName = "C1", loadKVA = 200.0, assignedAtsId = "ATS-01"),
            CircuitEntity(id = 2, circuitName = "C2", loadKVA = 200.0, assignedAtsId = "ATS-02")
        )

        val results = AtsEngine.calculateAll(
            atsList = listOf(ats1, ats2),
            circuits = circuits,
            generatorSelectedSizes = emptyMap(),
            transformerFLC = 500.0,
            isc3PhaseAtTransformer = 0.0,
            totalConnectedLoad = 400.0,
            isc3PhaseByAtsId = mapOf("ATS-01" to 12.0, "ATS-02" to 40.0)
        )

        assertEquals(12.0, results["ATS-01"]!!.requiredIcw, 0.01)
        assertEquals(40.0, results["ATS-02"]!!.requiredIcw, 0.01)
    }

    // ---- condition 1.2: cycle safety through HierarchyResolver ----

    @Test
    fun resolveFeedTransformerId_cyclicParentChain_returnsNullAndTerminates() {
        // 1 -> 2 -> 3 -> 1 : a cycle with no root.
        val circuits = listOf(
            CircuitEntity(id = 1, circuitName = "A", loadKVA = 100.0, assignedAtsId = "ATS-01"),
            CircuitEntity(id = 2, circuitName = "B", loadKVA = 100.0),
            CircuitEntity(id = 3, circuitName = "C", loadKVA = 100.0)
        )
        val cyclicParents = mapOf(1 to 2, 2 to 3, 3 to 1)

        val started = System.nanoTime()
        val txId = AtsIscResolver.resolveFeedTransformerId("ATS-01", circuits, cyclicParents)
        val elapsedMs = (System.nanoTime() - started) / 1_000_000

        assertNull("A cyclic hierarchy cannot resolve a source", txId)
        assertTrue("Must terminate promptly, took ${elapsedMs}ms", elapsedMs < 2_000)
    }

    @Test
    fun resolveFeedTransformerId_selfParenting_returnsNull() {
        val circuits = listOf(
            CircuitEntity(id = 1, circuitName = "A", loadKVA = 100.0, assignedAtsId = "ATS-01")
        )

        assertNull(AtsIscResolver.resolveFeedTransformerId("ATS-01", circuits, mapOf(1 to 1)))
    }

    @Test
    fun resolveFeedTransformerId_cyclicChainWithTransformerOnIt_returnsNullNotTheTransformer() {
        // The cycle node itself carries a transformerId; the walk must still refuse to resolve.
        val circuits = listOf(
            CircuitEntity(id = 1, circuitName = "A", loadKVA = 100.0, assignedAtsId = "ATS-01"),
            CircuitEntity(id = 2, circuitName = "B", loadKVA = 100.0, transformerId = "TX-X")
        )
        val cyclicParents = mapOf(1 to 2, 2 to 1)

        assertNull(AtsIscResolver.resolveFeedTransformerId("ATS-01", circuits, cyclicParents))
    }

    // ---- condition 1.1: DATA MISSING ATS must not show a satisfied-looking withstand ----

    @Test
    fun formatAtsWithstandKpi_dataMissing_neverShowsANumber() {
        assertEquals("DATA MISSING", com.example.ui.formatAtsWithstandKpi("DATA MISSING", 0.0))
        assertEquals("DATA MISSING", com.example.ui.formatAtsWithstandKpi("DATA MISSING", 12.5))
    }

    @Test
    fun formatAtsWithstandKpi_computed_showsTheValue() {
        assertEquals("12.50", com.example.ui.formatAtsWithstandKpi("Valid ATS Sized", 12.5))
        assertEquals("27.50", com.example.ui.formatAtsWithstandKpi("Valid ATS Sized", 27.5))
    }

    @Test
    fun decideAtsIsc_upperBoundBasis_statesWhatIsExcluded() {
        val agg = AtsIscAggregation(25.0, true, listOf(3))

        val decision = AtsIscResolver.decide(
            agg,
            transformerIscKA = 30.0,
            basisNoteSuffix = "utility source only; generator/motor contribution not included"
        )

        assertFalse(decision.dataMissing)
        assertTrue(
            "Basis must name the excluded sources, got: ${decision.basis}",
            decision.basis.contains("generator/motor contribution not included")
        )
    }
}
