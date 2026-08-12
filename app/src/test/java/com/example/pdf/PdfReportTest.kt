package com.example.pdf

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import com.example.engine.SizingResult
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.*
import android.graphics.pdf.PdfDocument

@RunWith(RobolectricTestRunner::class)
class PdfReportTest {

    @Test
    @org.junit.Ignore("Requires real Android environment for PdfDocument native methods")
    fun generateRealReferenceReports() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        
        // --- 1. A) MODULE REPORT ---
        val projectA = ProjectEntity(projectId = "proj_test_A", projectName = "مشروع بوابة 3 (Project Gate 3)")
        val circuits = mutableListOf<CircuitEntity>()
        val sizingResults = mutableMapOf<Int, SizingResult>()
        
        for (i in 1..60) {
            val cid = i
            circuits.add(CircuitEntity(id = i, projectId = "proj_test_A", circuitName = "Circuit $i with Ω, mm², kA, kVA, %, °C, ≤ and ≥", loadKVA = 10.0, fedFrom = "TX-01", transformerId = "TX-01", loadType = "Normal", phases = "3P+N", voltageV = 400, pf = 0.8, safetyFactor = 1.0, manufacturer = "Elsewedy", cableType = "PVC", installation = "Laid in Free Air", parallelRuns = 1, airTempC = 40, gndTempC = 40, burialDepth_m = 0.6, soilRes = 125, noCircuits = 1, formation = "Flat", spacing = "Touching", length_m = 100.0, maxVDpct = 0.05))
            sizingResults[cid] = SizingResult(
                Ib_A = 10.0 + i,
                deviceSize_A = 16.0,
                deviceType = "MCB",
                cTotalDerating = 1.0,
                iRequired_perCable_A = 10.0,
                productCode = "CODE",
                cableSize_mm2 = "4",
                ratedI_table_A = 16.0,
                capacity_derated_Iz_A = 16.0,
                utilizationPct = 0.5,
                neutral_mm2 = "4",
                earthCPC_mm2 = "4",
                voltDrop_V = 2.0,
                vdPct = 1.2,
                cumulativeVDpct = 1.2,
                status = if (i % 5 == 0) "FAIL" else if (i % 3 == 0) "WARNING" else "PASS",
                failReason = if (i % 5 == 0) "Voltage drop exceeds limit" else ""
            )
        }
        
        val moduleSnapshot = ProjectReportSnapshot(
            project = projectA,
            circuits = circuits,
            transformers = emptyList(),
            sizingResults = sizingResults,
            shortCircuitResults = emptyMap(),
            conduitResults = emptyMap(),
            conduitProcurement = emptyList(),
            options = ReportOptions(isCompleteProjectReport = false, selectedSections = listOf("Circuits & Cable Sizing"))
        )
        
        val service = PdfReportService(context)
        
        val moduleFile = File("module_report_test.pdf")
        
        // Pass 1
        val pass1Renderer = PdfDocumentRenderer(context, isPass1 = true)
        val sections = listOf(CircuitsSection())
        sections.forEach { section ->
            section.render(pass1Renderer, moduleSnapshot)
            pass1Renderer.endPage()
        }
        val totalPages = pass1Renderer.currentPageNumber
        
        // Pass 2
        val pass2Renderer = PdfDocumentRenderer(context, isPass1 = false, totalPagesFromPass1 = totalPages)
        pass2Renderer.sectionStartPages = pass1Renderer.sectionStartPages
        sections.forEach { section ->
            section.render(pass2Renderer, moduleSnapshot)
            pass2Renderer.endPage()
        }
        
        FileOutputStream(moduleFile).use { fos ->
            pass2Renderer.writeTo(fos)
        }
        
        println("Module Report PDF: ${moduleFile.absolutePath}")
        println("Module Report Size: ${moduleFile.length()} bytes")
        println("Module Report Pages: ${totalPages}")
        assertTrue(moduleFile.length() > 0)
        assertTrue(totalPages > 1)
        
        
        // --- 1. B) COMPLETE PROJECT REPORT ---
        val projectB = ProjectEntity(projectId = "proj_test_B", projectName = "Complete Project B")
        val txs = listOf(
            TransformerEntity(transformerId = "TX_1", transformerName="TX_1", projectId = "proj_test_B", kVA = 1000.0, voltage = 400, percentZ = 5.0, xr = 4.0, txType="Dry", sourceType="Grid", status="Active", sizeMode="Auto", manualKVA=0.0, demandFactor=1.0, diversity=1.0, growth=1.0, safety=1.0),
            TransformerEntity(transformerId = "TX_2", transformerName="TX_2", projectId = "proj_test_B", kVA = 1500.0, voltage = 400, percentZ = 5.0, xr = 4.0, txType="Dry", sourceType="Grid", status="Active", sizeMode="Auto", manualKVA=0.0, demandFactor=1.0, diversity=1.0, growth=1.0, safety=1.0)
        )
        
        val completeSnapshot = ProjectReportSnapshot(
            project = projectB,
            circuits = circuits,
            transformers = txs,
            sizingResults = sizingResults,
            shortCircuitResults = emptyMap(),
            conduitResults = emptyMap(),
            conduitProcurement = emptyList(),
            options = ReportOptions(isCompleteProjectReport = true)
        )
        
        val completeFile = File("complete_report_test.pdf")
        
        val completeSections = listOf(
            ExecutiveSummarySection(),
            CircuitsSection(),
            TransformersSection(),
            PowerSystemsSection(),
            ProtectionSection(),
            ConduitsSection(),
            BoqSection()
        )
        
        // Pass 1
        val pass1RendererComplete = PdfDocumentRenderer(context, isPass1 = true)
        pass1RendererComplete.beginPage(true)
        pass1RendererComplete.endPage() // Cover
        pass1RendererComplete.beginPage(true)
        pass1RendererComplete.endPage() // TOC
        
        completeSections.forEach { section ->
            section.render(pass1RendererComplete, completeSnapshot)
            pass1RendererComplete.endPage()
        }
        val totalPagesComplete = pass1RendererComplete.currentPageNumber
        
        // Pass 2
        val pass2RendererComplete = PdfDocumentRenderer(context, isPass1 = false, totalPagesFromPass1 = totalPagesComplete)
        pass2RendererComplete.sectionStartPages = pass1RendererComplete.sectionStartPages
        
        // Draw Cover
        pass2RendererComplete.beginPage(true)
        pass2RendererComplete.drawText("VoltAtlas", pass2RendererComplete.marginX, 200f, pass2RendererComplete.theme.titlePaint)
        pass2RendererComplete.endPage()
        
        // Draw TOC
        pass2RendererComplete.beginPage(true)
        pass2RendererComplete.drawText("TABLE OF CONTENTS", pass2RendererComplete.marginX, 80f, pass2RendererComplete.theme.titlePaint)
        pass2RendererComplete.endPage()
        
        completeSections.forEach { section ->
            section.render(pass2RendererComplete, completeSnapshot)
            pass2RendererComplete.endPage()
        }
        
        FileOutputStream(completeFile).use { fos ->
            pass2RendererComplete.writeTo(fos)
        }
        
        println("Complete Report PDF: ${completeFile.absolutePath}")
        println("Complete Report Size: ${completeFile.length()} bytes")
        println("Complete Report Pages: ${totalPagesComplete}")
        assertTrue(completeFile.length() > 0)
        assertTrue(totalPagesComplete > 1)
        
        val isolationTest = completeSnapshot.circuits.all { it.projectId == "proj_test_A" }
        assertTrue("Snapshot contains identifiers from Project A", isolationTest)
        
        renderPdfToPngs(completeFile, "complete")
    }

    private fun renderPdfToPngs(pdfFile: File, prefix: String) {
        val pfd = android.os.ParcelFileDescriptor.open(pdfFile, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = android.graphics.pdf.PdfRenderer(pfd)
        for (i in 0 until renderer.pageCount) {
            val page = renderer.openPage(i)
            val bitmap = android.graphics.Bitmap.createBitmap(page.width, page.height, android.graphics.Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.WHITE)
            page.render(bitmap, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            val outFile = File("${prefix}_page_${i + 1}.png")
            val out = FileOutputStream(outFile)
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
            out.flush()
            out.close()
            page.close()
            println("Rendered ${outFile.absolutePath}")
        }
        renderer.close()
    }
}
