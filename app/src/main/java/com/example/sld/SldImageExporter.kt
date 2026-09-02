package com.example.sld

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

object SldImageExporter {

    /**
     * Pure rendering function that draws the SLD diagram into an in-memory ARGB_8888 Bitmap.
     * Returns null if engineResult.nodes is empty.
     */
    fun renderSldBitmap(
        engineResult: SldEngineResult,
        projectName: String = "VoltAtlas_SLD",
        isDarkTheme: Boolean = true
    ): Bitmap? {
        if (engineResult.nodes.isEmpty()) {
            return null
        }

        val bounds = engineResult.bounds
        val margin = 120f
        val width = maxOf(1600, (bounds.width + margin * 2).toInt())
        val height = maxOf(1100, (bounds.height + margin * 2).toInt())

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val bgPaint = Paint().apply {
            color = if (isDarkTheme) Color.parseColor("#0D1117") else Color.parseColor("#F8FAFC")
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        val offsetX = -bounds.left + margin
        val offsetY = -bounds.top + margin + 60f

        // 1. Title Header & Border
        val titlePaint = Paint().apply {
            color = if (isDarkTheme) Color.parseColor("#38BDF8") else Color.parseColor("#0284C7")
            textSize = 28f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val effProject = projectName.ifBlank { "VoltAtlas Project" }
        canvas.drawText("VoltAtlas — Single Line Diagram: $effProject", margin, 48f, titlePaint)

        val metaPaint = Paint().apply {
            color = if (isDarkTheme) Color.parseColor("#8B949E") else Color.parseColor("#64748B")
            textSize = 14f
            isAntiAlias = true
        }
        canvas.drawText("IEC 60364 / BS 7671 • AutoCAD R2000 Equivalent", margin, 74f, metaPaint)

        // 2. Tier Datum Lines
        val tierPaint = Paint().apply {
            color = if (isDarkTheme) Color.parseColor("#21262D") else Color.parseColor("#E2E8F0")
            strokeWidth = 2f
            style = Paint.Style.STROKE
            pathEffect = DashPathEffect(floatArrayOf(10f, 6f), 0f)
        }
        val tierLabelPaint = Paint().apply {
            color = if (isDarkTheme) Color.parseColor("#484F58") else Color.parseColor("#94A3B8")
            textSize = 12f
            isAntiAlias = true
        }
        engineResult.tierDatums.forEach { datum ->
            val y = datum.y + offsetY
            canvas.drawLine(margin, y, width - margin, y, tierPaint)
            canvas.drawText(datum.label, margin + 10f, y - 8f, tierLabelPaint)
        }

        // 3. Edges / Wire Paths (Normal vs Emergency)
        val normalEdgePaint = Paint().apply {
            color = if (isDarkTheme) Color.parseColor("#38BDF8") else Color.parseColor("#0284C7")
            strokeWidth = 3f
            style = Paint.Style.STROKE
            isAntiAlias = true
        }
        val emergencyEdgePaint = Paint().apply {
            color = if (isDarkTheme) Color.parseColor("#F59E0B") else Color.parseColor("#D97706")
            strokeWidth = 3f
            style = Paint.Style.STROKE
            pathEffect = DashPathEffect(floatArrayOf(12f, 8f), 0f)
            isAntiAlias = true
        }

        val feederDataTitlePaint = Paint().apply {
            color = if (isDarkTheme) Color.parseColor("#F0F6FC") else Color.parseColor("#0F172A")
            textSize = 11f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val feederDataPaint = Paint().apply {
            color = if (isDarkTheme) Color.parseColor("#38BDF8") else Color.parseColor("#0284C7")
            textSize = 10f
            isAntiAlias = true
        }
        val breakerSymbolPaint = Paint().apply {
            color = if (isDarkTheme) Color.parseColor("#10B981") else Color.parseColor("#059669")
            strokeWidth = 2f
            style = Paint.Style.STROKE
            isAntiAlias = true
        }

        engineResult.edges.forEach { edge ->
            val isEmerg = edge.isEmergency || edge.label == "EMERGENCY"
            val paint = if (isEmerg) emergencyEdgePaint else normalEdgePaint
            val points = edge.pathPoints
            if (points.size >= 2) {
                for (i in 0 until points.size - 1) {
                    val x1 = points[i].first + offsetX
                    val y1 = points[i].second + offsetY
                    val x2 = points[i + 1].first + offsetX
                    val y2 = points[i + 1].second + offsetY
                    canvas.drawLine(x1, y1, x2, y2, paint)
                }

                // Side-clearance feeder data block
                val childNode = engineResult.nodes.find { it.id == edge.toId }
                if (childNode != null && !childNode.isSource && childNode.dataBlock != null) {
                    val firstPt = points.first()
                    val feederX = points.last().first + offsetX
                    val verticalDropStartY = (if (points.size >= 4) points[2].second else firstPt.second) + offsetY
                    val breakerY = verticalDropStartY + 25f

                    // Breaker Box
                    val bSize = 14f
                    canvas.drawRect(feederX - bSize / 2f, breakerY - bSize / 2f, feederX + bSize / 2f, breakerY + bSize / 2f, breakerSymbolPaint)

                    // Side text corridor
                    val textX = feederX + 16f
                    var textY = verticalDropStartY + 14f
                    val db = childNode.dataBlock

                    db.deviceTypeAndRating?.takeIf { it.isNotBlank() }?.let {
                        canvas.drawText(it, textX, textY, feederDataTitlePaint)
                        textY += 13f
                    }
                    val runsText = if (db.parallelRuns > 1) "${db.parallelRuns}x " else ""
                    db.cableDescription?.takeIf { it.isNotBlank() }?.let {
                        canvas.drawText("$runsText$it", textX, textY, feederDataPaint)
                        textY += 12f
                    }
                    val lvd = listOfNotNull(db.lengthM?.takeIf { it.isNotBlank() }?.let { "L: ${it}m" }, db.voltageDropPct?.takeIf { it.isNotBlank() }?.let { "VD: $it" }).joinToString(" • ")
                    if (lvd.isNotBlank()) {
                        canvas.drawText(lvd, textX, textY, feederDataPaint)
                    }
                }
            }
        }

        // 4. Nodes / Equipment
        val nodeBgPaint = Paint().apply {
            color = if (isDarkTheme) Color.parseColor("#161B22") else Color.parseColor("#FFFFFF")
            style = Paint.Style.FILL
        }
        val nodeBorderPaint = Paint().apply {
            color = if (isDarkTheme) Color.parseColor("#30363D") else Color.parseColor("#CBD5E1")
            strokeWidth = 2f
            style = Paint.Style.STROKE
            isAntiAlias = true
        }
        val copperBusPaint = Paint().apply {
            color = Color.parseColor("#E08A4B")
            style = Paint.Style.FILL
        }
        val labelPaint = Paint().apply {
            color = if (isDarkTheme) Color.parseColor("#F0F6FC") else Color.parseColor("#0F172A")
            textSize = 13f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val subtextPaint = Paint().apply {
            color = if (isDarkTheme) Color.parseColor("#8B949E") else Color.parseColor("#64748B")
            textSize = 10.5f
            isAntiAlias = true
        }

        engineResult.nodes.forEach { pNode ->
            val nx = pNode.x + offsetX
            val ny = pNode.y + offsetY
            val panelUpper = pNode.panelType.uppercase()

            if (pNode.isSource) {
                if (panelUpper.contains("TRANSFORMER")) {
                    val cx = nx + pNode.width / 2f
                    val cy = ny
                    val r = 24f
                    val txPaint = Paint().apply {
                        color = if (isDarkTheme) Color.parseColor("#38BDF8") else Color.parseColor("#0284C7")
                        style = Paint.Style.STROKE
                        strokeWidth = 2.5f
                        isAntiAlias = true
                    }
                    canvas.drawCircle(cx, cy - r * 0.5f, r, txPaint)
                    canvas.drawCircle(cx, cy + r * 0.5f, r, txPaint)
                    canvas.drawText(pNode.label, cx + 40f, cy - 8f, labelPaint)
                    canvas.drawText(pNode.ratingText, cx + 40f, cy + 12f, subtextPaint)
                } else if (panelUpper.contains("GENERATOR")) {
                    val cx = nx + pNode.width / 2f
                    val cy = ny
                    val r = 24f
                    val genPaint = Paint().apply {
                        color = if (isDarkTheme) Color.parseColor("#FBBF24") else Color.parseColor("#D97706")
                        style = Paint.Style.STROKE
                        strokeWidth = 2.5f
                        isAntiAlias = true
                    }
                    canvas.drawCircle(cx, cy, r, genPaint)
                    canvas.drawText("G", cx - 7f, cy + 6f, labelPaint)
                    canvas.drawText(pNode.label, cx + 40f, cy - 8f, labelPaint)
                    canvas.drawText(pNode.ratingText, cx + 40f, cy + 12f, subtextPaint)
                } else if (panelUpper.contains("ATS")) {
                    val cx = nx + pNode.width / 2f
                    val cy = ny
                    val boxW = 52f
                    val boxH = 40f
                    canvas.drawRoundRect(RectF(cx - boxW / 2f, cy - boxH / 2f, cx + boxW / 2f, cy + boxH / 2f), 6f, 6f, nodeBgPaint)
                    canvas.drawRoundRect(RectF(cx - boxW / 2f, cy - boxH / 2f, cx + boxW / 2f, cy + boxH / 2f), 6f, 6f, nodeBorderPaint)
                    canvas.drawText("ATS", cx - 14f, cy + 5f, labelPaint)
                    canvas.drawText(pNode.label, cx + 40f, cy - 8f, labelPaint)
                    canvas.drawText(pNode.ratingText, cx + 40f, cy + 12f, subtextPaint)
                } else {
                    val rect = RectF(nx, ny - 15f, nx + pNode.width, ny + 15f)
                    canvas.drawRoundRect(rect, 6f, 6f, nodeBgPaint)
                    canvas.drawRoundRect(rect, 6f, 6f, nodeBorderPaint)
                    canvas.drawText(pNode.label, nx + 10f, ny + 5f, labelPaint)
                }
            } else {
                val isMain = panelUpper.contains("MDB") || panelUpper.contains("MAIN")
                val barHeight = maxOf(pNode.height, 16f)

                if (isMain) {
                    val encPad = 28f
                    val encRect = RectF(nx - encPad, ny - 80f, nx + pNode.width + encPad, ny + barHeight + 20f)
                    val encPaint = Paint().apply {
                        color = if (isDarkTheme) Color.parseColor("#21262D") else Color.parseColor("#E2E8F0")
                        style = Paint.Style.STROKE
                        strokeWidth = 1.5f
                        pathEffect = DashPathEffect(floatArrayOf(6f, 4f), 0f)
                    }
                    canvas.drawRoundRect(encRect, 10f, 10f, encPaint)
                    canvas.drawText(pNode.label, nx - encPad + 12f, ny - 60f, labelPaint)
                }

                // Copper Busbar
                val busRect = RectF(nx, ny, nx + pNode.width, ny + barHeight)
                canvas.drawRoundRect(busRect, 4f, 4f, copperBusPaint)

                // Label
                canvas.drawText(pNode.label, nx + 10f, ny - 10f, labelPaint)
                if (pNode.ratingText.isNotBlank()) {
                    canvas.drawText(pNode.ratingText, nx + 10f, ny + barHeight + 14f, subtextPaint)
                }
            }
        }

        return bitmap
    }

    /**
     * Writes rendered Bitmap to an arbitrary OutputStream as PNG and flushes.
     */
    fun writeBitmapToStream(bitmap: Bitmap, outputStream: OutputStream) {
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        outputStream.flush()
    }

    /**
     * Renders SLD bitmap, saves it to the app cache directory, and invokes the ACTION_SEND share sheet.
     */
    fun exportAndShareImage(
        context: Context,
        engineResult: SldEngineResult,
        projectName: String = "VoltAtlas_SLD",
        isDarkTheme: Boolean = true
    ) {
        if (engineResult.nodes.isEmpty()) {
            Toast.makeText(context, "Diagram empty. Please add components first.", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val bitmap = renderSldBitmap(engineResult, projectName, isDarkTheme)
            if (bitmap == null) {
                Toast.makeText(context, "Diagram empty. Please add components first.", Toast.LENGTH_SHORT).show()
                return
            }

            val cacheDir = context.cacheDir
            val imageFile = File(cacheDir, "VoltAtlas_SLD.png")
            FileOutputStream(imageFile).use { out ->
                writeBitmapToStream(bitmap, out)
            }

            if (imageFile.exists() && imageFile.length() > 0) {
                val contentUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    imageFile
                )
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, contentUri)
                    putExtra(Intent.EXTRA_SUBJECT, "VoltAtlas SLD Diagram Screenshot")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val chooser = Intent.createChooser(intent, "Share / Save SLD Image")
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(chooser)
                Toast.makeText(context, "SLD Screenshot captured successfully (${imageFile.length() / 1024} KB)", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Failed to create image file.", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Screenshot capture failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }
}
