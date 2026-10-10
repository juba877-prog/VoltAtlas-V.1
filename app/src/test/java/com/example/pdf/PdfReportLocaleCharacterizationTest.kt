package com.example.pdf

import com.example.sld.SldDxfExporter
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * KT-03 Characterization Test:
 * Characterizes the baseline locale sensitivity across output paths.
 * Must PASS on unmodified code.
 */
class PdfReportLocaleCharacterizationTest {

    private val originalLocale = Locale.getDefault()

    @Before
    fun setUp() {
        Locale.setDefault(Locale.US)
    }

    @After
    fun tearDown() {
        Locale.setDefault(originalLocale)
    }

    @Test
    fun testCurrentJvmFormatIsLocaleSensitiveUnderGermany() {
        Locale.setDefault(Locale.GERMANY)
        val value = 12.34
        // Standard String.format without explicit Locale emits comma in de-DE
        val formatted = "%.2f".format(value)
        assertEquals("In de-DE standard String.format emits comma separator", "12,34", formatted)
    }

    @Test
    fun testCurrentJvmFormatUnderUs() {
        Locale.setDefault(Locale.US)
        val value = 12.34
        val formatted = "%.2f".format(value)
        assertEquals("In en-US standard String.format emits dot separator", "12.34", formatted)
    }

    @Test
    fun testDxfExporterIsStrictlyLocaleInvariant() {
        val testLocales = listOf(Locale.US, Locale.GERMANY, Locale("ar", "EG"))
        for (loc in testLocales) {
            Locale.setDefault(loc)
            val formatted = SldDxfExporter.formatFloat(12.345f)
            assertEquals("DXF exporter must emit period decimal regardless of locale", "12.345", formatted)
        }
    }

    @Test
    fun testJsonExportIsStrictlyLocaleInvariant() {
        val testLocales = listOf(Locale.US, Locale.GERMANY, Locale("ar", "EG"))
        for (loc in testLocales) {
            Locale.setDefault(loc)
            val json = JSONObject().apply {
                put("loadKVA", 12.34)
                put("voltDrop_V", 3.28)
            }
            val jsonStr = json.toString()
            assertTrue("JSON numeric export must use period decimal: $jsonStr", jsonStr.contains("12.34"))
            assertTrue("JSON numeric export must use period decimal: $jsonStr", jsonStr.contains("3.28"))
        }
    }

    @Test
    fun testSimpleDateFormatLocaleLeakageAndUsInvariance() {
        Locale.setDefault(Locale("ar", "EG"))
        val date = Date(1700000000000L) // Fixed timestamp

        val usFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val usDateStr = usFormatter.format(date)
        assertTrue("Locale.US SimpleDateFormat must emit ASCII digits: $usDateStr", usDateStr.matches(Regex("\\d{4}-\\d{2}-\\d{2}")))
    }
}
