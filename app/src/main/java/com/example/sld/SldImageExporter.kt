package com.example.sld

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
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
        val margin = 100f
        val width = maxOf(1400, (bounds.width + margin * 2).toInt())
        val height = maxOf(1000, (bounds.height + margin * 2).toInt())

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val bgPaint = Paint().apply {
            color = if (isDarkTheme) Color.parseColor("#0D1117") else Color.parseColor("#F8FAFC")
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        val offsetX = -bounds.left + margin
        val offsetY = -bounds.top + margin + 40f

        // Title Header
        val titlePaint = Paint().apply {
            color = if (isDarkTheme) Color.parseColor("#38BDF8") else Color.parseColor("#0284C7")
            textSize = 28f
            isFakeBoldText = true
            isAntiAlias = true
        }
        canvas.drawText("VoltAtlas — Single Line Diagram: ${projectName.ifBlank { "Project" }}", margin, 45f, titlePaint)

        // Tier Lines
        val tierPaint = Paint().apply {
            color = if (isDarkTheme) Color.parseColor("#21262D") else Color.parseColor("#E2E8F0")
            strokeWidth = 2f
            style = Paint.Style.STROKE
        }
        engineResult.tierDatums.forEach { datum ->
            val y = datum.y + offsetY
            canvas.drawLine(margin, y, width - margin, y, tierPaint)
        }

        // Edges / Connection Lines
        val edgePaint = Paint().apply {
            color = if (isDarkTheme) Color.parseColor("#38BDF8") else Color.parseColor("#0284C7")
            strokeWidth = 3f
            style = Paint.Style.STROKE
            isAntiAlias = true
        }
        engineResult.edges.forEach { edge ->
            val points = edge.pathPoints
            if (points.size >= 2) {
                for (i in 0 until points.size - 1) {
                    val x1 = points[i].first + offsetX
                    val y1 = points[i].second + offsetY
                    val x2 = points[i + 1].first + offsetX
                    val y2 = points[i + 1].second + offsetY
                    canvas.drawLine(x1, y1, x2, y2, edgePaint)
                }
            }
        }

        // Nodes / Boards / Equipment
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
        val labelPaint = Paint().apply {
            color = if (isDarkTheme) Color.parseColor("#F0F6FC") else Color.parseColor("#0F172A")
            textSize = 14f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val subtextPaint = Paint().apply {
            color = if (isDarkTheme) Color.parseColor("#8B949E") else Color.parseColor("#64748B")
            textSize = 11f
            isAntiAlias = true
        }

        engineResult.nodes.forEach { pNode ->
            val nx = pNode.x + offsetX
            val ny = pNode.y + offsetY
            val rect = RectF(nx, ny, nx + pNode.width, ny + pNode.height)

            canvas.drawRoundRect(rect, 8f, 8f, nodeBgPaint)
            canvas.drawRoundRect(rect, 8f, 8f, nodeBorderPaint)

            canvas.drawText(pNode.label, nx + 10f, ny + 22f, labelPaint)
            val sub = "${pNode.panelType} • ${pNode.dataBlock?.voltage ?: ""}"
            canvas.drawText(sub, nx + 10f, ny + 40f, subtextPaint)
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
