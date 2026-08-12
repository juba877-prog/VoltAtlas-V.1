package com.example.pdf

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.*
import com.example.engine.SizingResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class PdfReportInstrumentedTest {

    @Test
    fun testRealPdfExport_FullInstrumentationVerification() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val service = PdfReportService(context)
        
        val project = ProjectEntity(
            projectId = "proj_inst_1",
            projectName = "Instrumentation Substation Test",
            projectCode = "INS-001",
            clientName = "Energy Corp"
        )
        val circuits = listOf(
            CircuitEntity(
                id = 1,
                projectId = "proj_inst_1",
                circuitName = "Feeder 1 - Main MDB",
                loadKVA = 50.0,
                fedFrom = "Transformer",
                transformerId = "TX-01"
            )
        )
        val sizingResults = mapOf(
            1 to SizingResult(
                Ib_A = 72.0,
                deviceSize_A = 80.0,
                deviceType = "MCCB",
                cTotalDerating = 1.0,
                iRequired_perCable_A = 72.0,
                productCode = "XLPE-70",
                cableSize_mm2 = "70 mm²",
                ratedI_table_A = 100.0,
                capacity_derated_Iz_A = 100.0,
                utilizationPct = 72.0,
                neutral_mm2 = "35 mm²",
                earthCPC_mm2 = "35 mm²",
                voltDrop_V = 1.2,
                vdPct = 0.3,
                cumulativeVDpct = 0.3,
                status = "✓ PASS"
            )
        )
        
        val snapshot = ProjectReportSnapshot(
            project = project,
            circuits = circuits,
            transformers = emptyList(),
            sizingResults = sizingResults,
            shortCircuitResults = emptyMap(),
            conduitResults = emptyMap(),
            conduitProcurement = emptyList(),
            options = ReportOptions(isCompleteProjectReport = true)
        )
        
        val outputFile = File(context.cacheDir, "instrumentation_report_test.pdf")
        if (outputFile.exists()) {
            outputFile.delete()
        }

        // Assertion 1: No exception is thrown during exportReport
        val bytesWritten = service.exportReport(snapshot) { FileOutputStream(outputFile) }

        // Assertion 2: Output byte count is greater than zero
        assertTrue("Report service must return bytes written > 0", bytesWritten > 0L)
        assertTrue("Output PDF file must exist and be > 0 bytes", outputFile.exists() && outputFile.length() > 0L)

        // Assertion 3: Output begins with PDF signature "%PDF"
        val headerBytes = ByteArray(4)
        FileInputStream(outputFile).use { fis ->
            val readCount = fis.read(headerBytes)
            assertEquals(4, readCount)
        }
        val headerString = String(headerBytes, Charsets.US_ASCII)
        assertEquals("File signature must start with %PDF", "%PDF", headerString)

        // Assertion 4 & 5: Output stream/document closes successfully and can be reopened for reading
        val pfd = ParcelFileDescriptor.open(outputFile, ParcelFileDescriptor.MODE_READ_ONLY)
        assertNotNull("File descriptor must be opened for reading", pfd)

        // Assertion 6: At least one page exists using Android PdfRenderer API
        val pdfRenderer = PdfRenderer(pfd)
        assertTrue("Generated PDF must contain at least 1 page", pdfRenderer.pageCount > 0)

        // Cleanup
        pdfRenderer.close()
        pfd.close()
    }
}
