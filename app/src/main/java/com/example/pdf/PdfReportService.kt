package com.example.pdf

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream

class PdfReportService(private val context: Context) {
    
    suspend fun exportReport(
        snapshot: ProjectReportSnapshot,
        outputStreamProvider: () -> OutputStream?
    ): Long = withContext(Dispatchers.IO) {
        val sections = mutableListOf<ReportSectionProvider>()
        if (snapshot.options.isCompleteProjectReport || snapshot.options.selectedSections.contains("Executive Summary")) sections.add(ExecutiveSummarySection())
        if (snapshot.options.isCompleteProjectReport || snapshot.options.selectedSections.contains("Circuits & Cable Sizing")) sections.add(CircuitsSection())
        if (snapshot.options.isCompleteProjectReport || snapshot.options.selectedSections.contains("Transformers")) sections.add(TransformersSection())
        if (snapshot.options.isCompleteProjectReport || snapshot.options.selectedSections.contains("Power Systems")) sections.add(PowerSystemsSection())
        if (snapshot.options.isCompleteProjectReport || snapshot.options.selectedSections.contains("Protection & Short Circuit")) sections.add(ProtectionSection())
        if (snapshot.options.isCompleteProjectReport || snapshot.options.selectedSections.contains("Conduits")) sections.add(ConduitsSection())
        if (snapshot.options.isCompleteProjectReport || snapshot.options.selectedSections.contains("BOQ & Procurement")) sections.add(BoqSection())
        
        // Pass 1
        val pass1Renderer = PdfDocumentRenderer(context, isPass1 = true)
        pass1Renderer.engineerName = snapshot.options.engineer
        runRenderSequence(pass1Renderer, snapshot, sections)
        val totalPages = pass1Renderer.currentPageNumber
        val tocMap = pass1Renderer.sectionStartPages

        // Pass 2
        val pass2Renderer = PdfDocumentRenderer(context, isPass1 = false, totalPagesFromPass1 = totalPages)
        pass2Renderer.engineerName = snapshot.options.engineer
        pass2Renderer.sectionStartPages = tocMap
        runRenderSequence(pass2Renderer, snapshot, sections)

        var bytesWritten = 0L
        val outputStream = outputStreamProvider() ?: throw Exception("Could not open OutputStream")
        outputStream.use { os ->
            val countingOs = CountingOutputStream(os)
            pass2Renderer.writeTo(countingOs)
            bytesWritten = countingOs.count
        }
        
        if (bytesWritten == 0L) throw Exception("Zero bytes exported")
        bytesWritten
    }
    
    private fun drawVoltAtlasLogo(canvas: android.graphics.Canvas, originX: Float, originY: Float, logoSize: Float) {
        val scale = logoSize / 108f
        
        val bluePaint = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#08B7E8")
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 8f * scale
            strokeCap = android.graphics.Paint.Cap.ROUND
            strokeJoin = android.graphics.Paint.Join.ROUND
            isAntiAlias = true
        }
        
        val greenPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#19C979")
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 8f * scale
            strokeCap = android.graphics.Paint.Cap.ROUND
            strokeJoin = android.graphics.Paint.Join.ROUND
            isAntiAlias = true
        }
        
        val vPath = android.graphics.Path().apply {
            moveTo(originX + 34f * scale, originY + 38f * scale)
            lineTo(originX + 54f * scale, originY + 72f * scale)
            lineTo(originX + 74f * scale, originY + 38f * scale)
        }
        
        val barPath = android.graphics.Path().apply {
            moveTo(originX + 44f * scale, originY + 55f * scale)
            lineTo(originX + 64f * scale, originY + 55f * scale)
        }
        
        canvas.drawPath(vPath, bluePaint)
        canvas.drawPath(barPath, greenPaint)
    }

    private fun drawTitleBlock(renderer: PdfDocumentRenderer, snapshot: ProjectReportSnapshot) {
        if (renderer.isPass1) return
        val canvas = renderer.canvas ?: return
        
        val startX = renderer.marginX
        val startY = renderer.marginY
        val boxWidth = renderer.pageWidth - 2 * renderer.marginX
        val boxHeight = 115f
        
        // Outer Frame
        val framePaint = android.graphics.Paint().apply {
            color = renderer.theme.navyColor
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 1.5f
            isAntiAlias = true
        }
        canvas.drawRect(startX, startY, startX + boxWidth, startY + boxHeight, framePaint)
        
        // Header band background fill (top 46pt)
        val headerBgPaint = android.graphics.Paint().apply {
            color = renderer.theme.headerBgColor
            style = android.graphics.Paint.Style.FILL
        }
        canvas.drawRect(startX + 1f, startY + 1f, startX + boxWidth - 1f, startY + 46f, headerBgPaint)
        
        // Logo
        drawVoltAtlasLogo(canvas, startX + 8f, startY + 5f, 36f)
        
        // Brand Title
        val logoTitlePaint = android.graphics.Paint().apply {
            color = renderer.theme.navyColor
            textSize = 15f
            typeface = renderer.theme.boldTypeface
            isAntiAlias = true
        }
        canvas.drawText("VOLTATLAS", startX + 50f, startY + 24f, logoTitlePaint)
        
        val logoSubPaint = android.graphics.Paint().apply {
            color = renderer.theme.cyanColor
            textSize = 9f
            typeface = renderer.theme.boldTypeface
            isAntiAlias = true
        }
        canvas.drawText("ELECTRICAL DESIGN REPORT", startX + 50f, startY + 38f, logoSubPaint)
        
        // Project Name (Right Aligned in Header Band)
        val projName = snapshot.options.projectName.ifBlank { snapshot.project.projectName.ifBlank { "Untitled Project" } }
        val projPaint = android.graphics.Paint().apply {
            color = renderer.theme.navyColor
            textSize = 11f
            typeface = renderer.theme.boldTypeface
            isAntiAlias = true
        }
        val projText = "Project: $projName"
        val projWidth = projPaint.measureText(projText)
        canvas.drawText(projText, startX + boxWidth - 12f - projWidth, startY + 28f, projPaint)
        
        // Divider line below header band
        val divPaint = android.graphics.Paint().apply {
            color = renderer.theme.cyanColor
            strokeWidth = 1.5f
            isAntiAlias = true
        }
        canvas.drawLine(startX, startY + 46f, startX + boxWidth, startY + 46f, divPaint)
        
        // Details Grid (2 columns)
        val labelPaint = android.graphics.Paint().apply {
            color = renderer.theme.navyColor
            textSize = 8.5f
            typeface = renderer.theme.boldTypeface
            isAntiAlias = true
        }
        val valuePaint = android.graphics.Paint().apply {
            color = renderer.theme.textColor
            textSize = 8.5f
            typeface = renderer.theme.baseTypeface
            isAntiAlias = true
        }
        
        val col1X = startX + 12f
        val col2X = startX + (boxWidth / 2f) + 12f
        
        fun drawPair(x: Float, y: Float, label: String, value: String) {
            val displayVal = if (value.isBlank()) "—" else value
            canvas.drawText(label, x, y, labelPaint)
            val lblW = labelPaint.measureText(label)
            canvas.drawText(displayVal, x + lblW + 5f, y, valuePaint)
        }
        
        // Left Column: Engineer, Contractor, Consultant
        var y1 = startY + 62f
        drawPair(col1X, y1, "Engineer:", snapshot.options.engineer)
        y1 += 16f
        drawPair(col1X, y1, "Contractor:", snapshot.options.contractorCompany)
        y1 += 16f
        drawPair(col1X, y1, "Consultant:", snapshot.options.consultant)
        
        // Right Column: Owner, Date, Revision
        var y2 = startY + 62f
        val ownerVal = snapshot.options.owner.ifBlank { snapshot.project.clientName ?: "" }
        drawPair(col2X, y2, "Owner/Client:", ownerVal)
        y2 += 16f
        drawPair(col2X, y2, "Date:", snapshot.options.date)
        y2 += 16f
        drawPair(col2X, y2, "Revision:", snapshot.options.revision)
    }

    private fun runRenderSequence(renderer: PdfDocumentRenderer, snapshot: ProjectReportSnapshot, sections: List<ReportSectionProvider>) {
        renderer.beginPage(true)
        
        // Render Professional Title Block at top of Page 1
        drawTitleBlock(renderer, snapshot)
        
        // Set Y below title block
        renderer.currentY = renderer.marginY + 115f + 30f

        renderer.drawText("COMPREHENSIVE ELECTRICAL DESIGN REPORT", renderer.marginX, renderer.currentY, renderer.theme.titlePaint)
        renderer.currentY += 22f
        
        renderer.drawText("Calculations, Equipment Schedules & Verification Summary", renderer.marginX, renderer.currentY, renderer.theme.headerPaint)
        renderer.currentY += 15f
        
        renderer.theme.dividerPaint.color = renderer.theme.cyanColor
        renderer.canvas?.drawLine(renderer.marginX, renderer.currentY, renderer.pageWidth - renderer.marginX, renderer.currentY, renderer.theme.dividerPaint)
        renderer.currentY += 25f

        val subtitlePaint = android.graphics.Paint().apply {
            color = renderer.theme.navyColor
            textSize = 12f
            typeface = renderer.theme.boldTypeface
            isAntiAlias = true
        }
        renderer.drawText("PROJECT OVERVIEW & STANDARDS", renderer.marginX, renderer.currentY, subtitlePaint)
        renderer.currentY += 20f
        
        fun drawOverviewRow(label: String, value: String) {
            renderer.drawText(label, renderer.marginX, renderer.currentY, renderer.theme.tableHeaderPaint)
            renderer.drawText(value, renderer.marginX + 140f, renderer.currentY, renderer.theme.bodyPaint)
            renderer.currentY += 18f
        }
        
        drawOverviewRow("Project Code:", snapshot.project.projectCode.takeIf { !it.isNullOrBlank() } ?: "—")
        drawOverviewRow("Total Circuits:", "${snapshot.circuits.size} Active Circuits")
        drawOverviewRow("Total Transformers:", "${snapshot.transformers.size} Active Units")
        
        val dateStr = snapshot.options.date.ifBlank {
            java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        }
        drawOverviewRow("Export Date:", dateStr)
        
        renderer.currentY += 15f
        renderer.drawText("GOVERNING ENGINEERING STANDARDS", renderer.marginX, renderer.currentY, subtitlePaint)
        renderer.currentY += 20f
        
        val stdList = listOf(
            "IEC 60364 — Low-Voltage Electrical Installations",
            "IEC 60502 — Power Cables with Extruded Insulation",
            "IEC 60909 — Short-Circuit Currents in Three-Phase AC Systems",
            "IEC 60076 — Power Transformers Design & Testing",
            "BS 7430 — Code of Practice for Protective Earthing of Electrical Installations"
        )
        
        stdList.forEach { std ->
            renderer.drawText("•  $std", renderer.marginX + 8f, renderer.currentY, renderer.theme.bodyPaint)
            renderer.currentY += 16f
        }
        
        renderer.endPage()
        
        // TOC
        renderer.beginPage(true)
        renderer.drawText("TABLE OF CONTENTS", renderer.marginX, 80f, renderer.theme.titlePaint)
        renderer.currentY = 120f
        sections.forEach { section ->
            val p = renderer.sectionStartPages[section.sectionName] ?: 0
            renderer.drawText(section.sectionName, renderer.marginX, renderer.currentY, renderer.theme.headerPaint)
            if (p > 0) {
                renderer.drawText(p.toString(), renderer.pageWidth - renderer.marginX - renderer.theme.headerPaint.measureText(p.toString()), renderer.currentY, renderer.theme.headerPaint)
            }
            renderer.currentY += 25f
        }
        renderer.endPage()
        
        for (section in sections) {
            section.render(renderer, snapshot)
            renderer.endPage() // Make sure section ends its last page
        }
    }
}
