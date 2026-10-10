package com.example.pdf

import com.example.data.CircuitEntity
import com.example.data.ProjectEntity
import com.example.data.TransformerEntity
import com.example.engine.AdsResult
import com.example.engine.ShortCircuitResult
import com.example.engine.SizingResult
import com.example.engine.SizingResultStatus
import com.example.engine.TransformerResult
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Locale

/**
 * KT-03 Target Test:
 * Proves that PDF engineering outputs use deterministic Locale.US formatting
 * across en-US, de-DE, and ar-EG without emitting locale-dependent commas or numerals.
 */
class PdfReportLocaleTargetTest {

    private val originalLocale = Locale.getDefault()
    private val testLocales = listOf(
        Locale.US,
        Locale.GERMANY,
        Locale.forLanguageTag("ar-EG")
    )

    @Before
    fun setUp() {
        Locale.setDefault(Locale.US)
    }

    @After
    fun tearDown() {
        Locale.setDefault(originalLocale)
    }

    @Test
    fun testFormatEngExtensionUnderAllLocales() {
        for (loc in testLocales) {
            Locale.setDefault(loc)
            assertEquals("12.34", "%.2f".formatEng(12.34))
            assertEquals("250.5 A", "%.1f A".formatEng(250.5))
            assertEquals("0.0123", "%.4f".formatEng(0.0123))
            assertEquals("143.00", 143.0.formatEng("%.2f"))
            assertEquals("3.14", 3.14159f.formatEng("%.2f"))

            assertFalse("%.2f".formatEng(12.34).contains(","))
            assertFalse("%.1f A".formatEng(250.5).contains(","))
            assertFalse("%.4f".formatEng(0.0123).contains(","))
        }
    }

    @Test
    fun testCircuitsTableRowsLocaleInvariance() {
        val project = ProjectEntity(projectId = "p1", projectName = "Test Project")
        val circuit = CircuitEntity(
            id = 1,
            projectId = "p1",
            circuitName = "C1",
            loadKVA = 12.34,
            fedFrom = "MDB",
            phases = "3P+N",
            cableType = "Cu/XLPE",
            length_m = 50.0
        )
        val sizingResult = SizingResult(
            Ib_A = 230.94,
            deviceSize_A = 250.0,
            deviceType = "MCCB",
            cTotalDerating = 0.95,
            iRequired_perCable_A = 243.09,
            productCode = "CODE",
            cableSize_mm2 = "150 mm²",
            ratedI_table_A = 322.0,
            capacity_derated_Iz_A = 305.9,
            utilizationPct = 75.5,
            neutral_mm2 = "70 mm²",
            earthCPC_mm2 = "70 mm²",
            voltDrop_V = 3.284,
            vdPct = 0.821,
            cumulativeVDpct = 1.25,
            status = "PASS",
            resultStatus = SizingResultStatus.PASS,
            isAmpacityOk = true,
            isVoltageDropOk = true,
            primaryFailureCode = null
        )
        val snapshot = ProjectReportSnapshot(
            project = project,
            circuits = listOf(circuit),
            transformers = emptyList(),
            sizingResults = mapOf(1 to sizingResult),
            shortCircuitResults = emptyMap(),
            conduitResults = emptyMap(),
            conduitProcurement = emptyList(),
            options = ReportOptions()
        )

        // Capture output under US as baseline
        Locale.setDefault(Locale.US)
        val baselineRows = buildCircuitsTableRows(snapshot)
        val baselineCells = baselineRows.flatMap { it.cells }

        for (loc in testLocales) {
            Locale.setDefault(loc)
            val rows = buildCircuitsTableRows(snapshot)
            val cells = rows.flatMap { it.cells }

            assertEquals("Row count under $loc must match baseline", baselineRows.size, rows.size)
            for (i in cells.indices) {
                val cellText = cells[i]
                assertEquals("Cell $i under $loc must match US baseline exactly", baselineCells[i], cellText)
                // Numeric cells must not have comma decimal separators
                if (cellText.matches(Regex(".*\\d+[,.]\\d+.*"))) {
                    assertTrue("Cell '$cellText' under $loc must contain dot decimal, not comma", cellText.contains("."))
                    assertFalse("Cell '$cellText' under $loc must NOT contain comma decimal", cellText.contains(","))
                }
            }
        }
    }

    @Test
    fun testTransformersTableRowsLocaleInvariance() {
        val project = ProjectEntity(projectId = "p1", projectName = "Test Project")
        val tx = TransformerEntity(
            transformerId = "TX-01",
            transformerName = "TX-01",
            projectId = "p1",
            kVA = 1000.0,
            percentZ = 5.0,
            vectorGroup = "Dyn11",
            txType = "Dry"
        )
        val txResult = TransformerResult(
            connectedLoad = 1000.0,
            circuitsAssigned = 10,
            realPower = 800.0,
            demandLoad = 800.0,
            diversifiedLoad = 800.0,
            futureLoad = 800.0,
            designLoad = 800.0,
            requiredKVA = 1000.0,
            recommendedSize = 1000.0,
            selectedSize = 1000.0,
            loadingPct = 85.5,
            flc = 1443.38,
            zt = 9.6,
            rt = 2.0,
            xt = 9.39,
            isc3Phase = 28.87,
            isc1Phase = 25.12,
            status = "PASS",
            z0 = 0.0,
            vectorGroup = "Dyn11"
        )
        val snapshot = ProjectReportSnapshot(
            project = project,
            circuits = emptyList(),
            transformers = listOf(tx),
            sizingResults = emptyMap(),
            shortCircuitResults = emptyMap(),
            conduitResults = emptyMap(),
            conduitProcurement = emptyList(),
            transformerResults = mapOf("TX-01" to txResult),
            options = ReportOptions()
        )

        Locale.setDefault(Locale.US)
        val baselineRows = buildTransformersTableRows(snapshot)
        val baselineCells = baselineRows.flatMap { it.cells }

        for (loc in testLocales) {
            Locale.setDefault(loc)
            val rows = buildTransformersTableRows(snapshot)
            val cells = rows.flatMap { it.cells }

            assertEquals("Transformers rows under $loc must match baseline", baselineRows.size, rows.size)
            for (i in cells.indices) {
                val cellText = cells[i]
                assertEquals("Transformer cell $i under $loc must match US baseline", baselineCells[i], cellText)
                if (cellText.matches(Regex(".*\\d+[,.]\\d+.*"))) {
                    assertTrue("Cell '$cellText' under $loc must contain dot decimal", cellText.contains("."))
                    assertFalse("Cell '$cellText' under $loc must NOT contain comma", cellText.contains(","))
                }
            }
        }
    }

    @Test
    fun testProtectionTableRowsLocaleInvariance() {
        val project = ProjectEntity(projectId = "p1", projectName = "Test Project")
        val circuit = CircuitEntity(id = 1, projectId = "p1", circuitName = "C1", fedFrom = "MDB")
        val scResult = ShortCircuitResult(
            circuitId = 1,
            sourceName = "MDB",
            cableMm2 = "150 mm²",
            iscKA = 25.45,
            iwKA = 30.0,
            deviceKA = 36.0,
            deviceCheck = "PASS",
            cableCheck = "PASS",
            marginPct = 15.0,
            status = "PASS",
            note = "OK"
        )
        val adsResult = AdsResult(
            circuitId = 1,
            circuitName = "C1",
            calculatedZs = 0.0456,
            maxZsAllowed = 0.0800,
            status = "PASS",
            disconnectionTime = "< 0.4s",
            r1_ohm_km = 0.15,
            r2_ohm_km = 0.25,
            length_m = 50.0
        )
        val snapshot = ProjectReportSnapshot(
            project = project,
            circuits = listOf(circuit),
            transformers = emptyList(),
            sizingResults = emptyMap(),
            shortCircuitResults = mapOf(1 to scResult),
            adsResults = mapOf(1 to adsResult),
            conduitResults = emptyMap(),
            conduitProcurement = emptyList(),
            options = ReportOptions()
        )

        Locale.setDefault(Locale.US)
        val baselineRows = buildProtectionTableRows(snapshot)
        val baselineCells = baselineRows.flatMap { it.cells }

        for (loc in testLocales) {
            Locale.setDefault(loc)
            val rows = buildProtectionTableRows(snapshot)
            val cells = rows.flatMap { it.cells }

            assertEquals("Protection rows under $loc must match baseline", baselineRows.size, rows.size)
            for (i in cells.indices) {
                val cellText = cells[i]
                assertEquals("Protection cell $i under $loc must match US baseline", baselineCells[i], cellText)
                if (cellText.matches(Regex(".*\\d+[,.]\\d+.*"))) {
                    assertTrue("Cell '$cellText' under $loc must contain dot decimal", cellText.contains("."))
                    assertFalse("Cell '$cellText' under $loc must NOT contain comma", cellText.contains(","))
                }
            }
        }
    }
}
