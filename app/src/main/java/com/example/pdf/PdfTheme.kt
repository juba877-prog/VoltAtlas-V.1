package com.example.pdf

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface

class PdfTheme(context: Context) {
    val navyColor = Color.parseColor("#0A1428")       // Primary/Navy header & title emphasis (VoltAtlasLogo.NAVY)
    val blueColor = Color.parseColor("#08B7E8")       // Cyan/Blue accent (VoltAtlasLogo.BLUE)
    val cyanColor = Color.parseColor("#08B7E8")       // Cyan/Blue accent (VoltAtlasLogo.BLUE)
    val passColor = Color.parseColor("#19C979")       // Green accent (VoltAtlasLogo.GREEN)
    val warningColor = Color.parseColor("#E59A00")    // Gold/Orange warning
    val failColor = Color.parseColor("#D83A45")       // Red fail
    val textColor = Color.parseColor("#18212F")       // Slate dark body text
    val headerBgColor = Color.parseColor("#EAF7FD")   // Light cyan tint header background
    val altRowBgColor = Color.parseColor("#F4FAFD")   // Alternate table row
    val borderColor = Color.parseColor("#CBD5E1")     // Soft grey border
    
    val baseTypeface: Typeface = Typeface.createFromAsset(context.assets, "fonts/Roboto-Regular.ttf")
    val boldTypeface: Typeface = Typeface.createFromAsset(context.assets, "fonts/Roboto-Bold.ttf")
    
    val titlePaint = Paint().apply {
        color = navyColor
        textSize = 24f
        typeface = boldTypeface
        isAntiAlias = true
    }

    val headerPaint = Paint().apply {
        color = navyColor
        textSize = 16f
        typeface = boldTypeface
        isAntiAlias = true
    }

    val bodyPaint = Paint().apply {
        color = textColor
        textSize = 10f
        typeface = baseTypeface
        isAntiAlias = true
    }
    
    val smallPaint = Paint().apply {
        color = textColor
        textSize = 8f
        typeface = baseTypeface
        isAntiAlias = true
    }

    val passPaint = Paint().apply {
        color = passColor
        textSize = 10f
        typeface = boldTypeface
        isAntiAlias = true
    }

    val failPaint = Paint().apply {
        color = failColor
        textSize = 10f
        typeface = boldTypeface
        isAntiAlias = true
    }
    
    val warningPaint = Paint().apply {
        color = warningColor
        textSize = 10f
        typeface = boldTypeface
        isAntiAlias = true
    }

    val footerPaint = Paint().apply {
        color = textColor
        textSize = 8f
        typeface = baseTypeface
        isAntiAlias = true
    }
    
    val dividerPaint = Paint().apply {
        color = cyanColor
        strokeWidth = 1f
        isAntiAlias = true
    }
    
    val tableHeaderPaint = Paint().apply {
        color = navyColor
        textSize = 9f
        typeface = boldTypeface
        isAntiAlias = true
    }
    
    val tableBodyPaint = Paint().apply {
        color = textColor
        textSize = 9f
        typeface = baseTypeface
        isAntiAlias = true
    }
}
