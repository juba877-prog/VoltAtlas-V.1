package com.example.pdf

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import java.io.OutputStream

class PdfDocumentRenderer(
    val context: Context,
    val isPass1: Boolean = false,
    val totalPagesFromPass1: Int = 0
) {
    val document = if (isPass1) null else PdfDocument()
    var currentPage: PdfDocument.Page? = null
    var canvas: Canvas? = null
    
    var currentIsPortrait = true
    var pageWidth = 595
    var pageHeight = 842
    
    var currentY = 0f
    var currentPageNumber = 0
    val marginX = 40f
    val marginY = 40f
    
    val theme = PdfTheme(context)
    
    var sectionStartPages = mutableMapOf<String, Int>()
    var engineerName: String = ""
    
    fun setOrientation(isPortrait: Boolean) {
        currentIsPortrait = isPortrait
        if (isPortrait) {
            pageWidth = 595
            pageHeight = 842
        } else {
            pageWidth = 842
            pageHeight = 595
        }
    }
    
    fun beginPage(isPortrait: Boolean) {
        setOrientation(isPortrait)
        currentPageNumber++
        if (!isPass1) {
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPageNumber).create()
            currentPage = document?.startPage(pageInfo)
            canvas = currentPage?.canvas
        }
        currentY = marginY
    }
    
    fun endPage() {
        if (!isPass1) {
            drawFooter()
            currentPage?.let { document?.finishPage(it) }
        }
        currentPage = null
        canvas = null
    }
    
    fun ensureSpace(requiredHeight: Float, isPortrait: Boolean = currentIsPortrait) {
        val ph = if (isPortrait) 842 else 595
        if (currentY + requiredHeight > ph - marginY - 30f) { 
            endPage()
            beginPage(isPortrait)
        }
    }
    
    fun recordSectionStart(sectionName: String) {
        if (isPass1) {
            if (!sectionStartPages.containsKey(sectionName)) {
                sectionStartPages[sectionName] = currentPageNumber + 1 // Next page will be the start if we are ending a page, but better to record after beginPage
            }
        }
    }

    fun markSectionStart(sectionName: String) {
        if (isPass1) {
            if (!sectionStartPages.containsKey(sectionName)) {
                sectionStartPages[sectionName] = Math.max(1, currentPageNumber)
            }
        }
    }
    
    fun drawText(text: String, x: Float, y: Float, paint: Paint) {
        if (!isPass1) canvas?.drawText(text, x, y, paint)
    }
    
    fun measureWrappedTextHeight(text: String, maxWidth: Float, paint: Paint): Float {
        val words = text.split(" ")
        var line = ""
        var totalHeight = 0f
        val lineHeight = paint.descent() - paint.ascent() + 2f
        
        for (word in words) {
            val testLine = if (line.isEmpty()) word else "$line $word"
            val width = paint.measureText(testLine)
            if (width > maxWidth) {
                totalHeight += lineHeight
                line = word
            } else {
                line = testLine
            }
        }
        if (line.isNotEmpty()) {
            totalHeight += lineHeight
        }
        return totalHeight
    }

    fun drawWrappedText(text: String, x: Float, maxWidth: Float, paint: Paint, align: TextAlign = TextAlign.LEFT, exactWidth: Float = maxWidth): Float {
        val words = text.split(" ")
        var line = ""
        var totalHeight = 0f
        val lineHeight = paint.descent() - paint.ascent() + 2f
        
        for (word in words) {
            val testLine = if (line.isEmpty()) word else "$line $word"
            val width = paint.measureText(testLine)
            if (width > maxWidth) {
                ensureSpace(lineHeight, currentIsPortrait)
                val drawX = when(align) {
                    TextAlign.LEFT -> x
                    TextAlign.RIGHT -> x + exactWidth - paint.measureText(line)
                    TextAlign.CENTER -> x + (exactWidth - paint.measureText(line))/2f
                }
                drawText(line, drawX, currentY - paint.ascent(), paint)
                currentY += lineHeight
                totalHeight += lineHeight
                line = word
            } else {
                line = testLine
            }
        }
        if (line.isNotEmpty()) {
            ensureSpace(lineHeight, currentIsPortrait)
            val drawX = when(align) {
                TextAlign.LEFT -> x
                TextAlign.RIGHT -> x + exactWidth - paint.measureText(line)
                TextAlign.CENTER -> x + (exactWidth - paint.measureText(line))/2f
            }
            drawText(line, drawX, currentY - paint.ascent(), paint)
            currentY += lineHeight
            totalHeight += lineHeight
        }
        return totalHeight
    }
    
    private fun drawFooter() {
        if (isPass1) return
        val paint = theme.footerPaint
        val text = "Page $currentPageNumber of $totalPagesFromPass1"
        val width = paint.measureText(text)
        canvas?.drawText(text, pageWidth - marginX - width, pageHeight - marginY, paint)
        
        val footerText = if (engineerName.isNotBlank()) "Prepared by $engineerName — VoltAtlas" else "Prepared by VoltAtlas"
        canvas?.drawText(footerText, marginX, pageHeight - marginY, paint)
        
        theme.dividerPaint.color = theme.cyanColor
        canvas?.drawLine(marginX, pageHeight - marginY - 10f, pageWidth - marginX, pageHeight - marginY - 10f, theme.dividerPaint)
    }
    
    fun writeTo(outputStream: OutputStream) {
        if (!isPass1) {
            document?.writeTo(outputStream)
            document?.close()
        }
    }
}

enum class TextAlign { LEFT, RIGHT, CENTER }
data class TableColumn(val name: String, val width: Float, val paint: Paint, val align: TextAlign = TextAlign.LEFT)
data class TableRow(val cells: List<String>)

fun PdfDocumentRenderer.drawTable(
    headers: List<TableColumn>,
    rows: List<TableRow>,
    startX: Float
) {
    val lineHeight = 14f
    val padding = 4f
    
    fun drawHeaders() {
        var x = startX
        ensureSpace(lineHeight + padding * 2, currentIsPortrait)
        val startY = currentY
        if (!isPass1) {
            val bgPaint = Paint().apply { color = theme.headerBgColor }
            canvas?.drawRect(startX, startY, pageWidth - marginX, startY + lineHeight + padding * 2, bgPaint)
        }
        
        currentY += padding
        headers.forEach { col ->
            val textWidth = theme.tableHeaderPaint.measureText(col.name)
            val dx = when(col.align) {
                TextAlign.LEFT -> x + padding
                TextAlign.RIGHT -> x + col.width - padding - textWidth
                TextAlign.CENTER -> x + (col.width - textWidth) / 2f
            }
            drawText(col.name, dx, currentY - theme.tableHeaderPaint.ascent(), theme.tableHeaderPaint)
            x += col.width
        }
        currentY += lineHeight + padding
    }
    
    drawHeaders()
    
    rows.forEachIndexed { index, row ->
        // measure row height
        var maxRowHeight = lineHeight + padding * 2
        row.cells.forEachIndexed { i, text ->
            if (i < headers.size) {
                val h = measureWrappedTextHeight(text, headers[i].width - padding * 2, headers[i].paint) + padding * 2
                if (h > maxRowHeight) maxRowHeight = h
            }
        }
        
        // ensure space, if new page, draw headers again
        val ph = if (currentIsPortrait) 842 else 595
        if (currentY + maxRowHeight > ph - marginY - 30f) {
            endPage()
            beginPage(currentIsPortrait)
            drawHeaders()
        }
        
        val startY = currentY
        if (!isPass1 && index % 2 != 0) {
            val bgPaint = Paint().apply { color = theme.altRowBgColor }
            canvas?.drawRect(startX, startY, pageWidth - marginX, startY + maxRowHeight, bgPaint)
        }
        
        var x = startX
        row.cells.forEachIndexed { i, text ->
            if (i < headers.size) {
                val col = headers[i]
                val prevY = currentY
                currentY += padding
                val cellPaint = when {
                    text.startsWith("PASS") -> theme.passPaint
                    text.startsWith("FAIL") -> theme.failPaint
                    text.startsWith("REVIEW") -> theme.warningPaint
                    else -> col.paint
                }
                val drawnH = drawWrappedText(text, x + padding, col.width - padding * 2, cellPaint, col.align, col.width - padding * 2)
                currentY = prevY
                x += col.width
            }
        }
        currentY += maxRowHeight
    }
}
