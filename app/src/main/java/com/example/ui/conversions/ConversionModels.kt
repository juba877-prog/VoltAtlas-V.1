package com.example.ui.conversions

import com.example.engine.converters.CableConversionResult

data class ConversionVariant(
    val id: String,
    val labelEn: String
)

data class ConversionTool(
    val id: String,
    val titleEn: String,
    val descriptionEn: String,
    val icon: String,
    val isCable: Boolean = false,
    val inputType: String = "numeric",
    val inputLabelEn: String = "Value",
    val inputLabel2En: String? = null,
    val inputLabel3En: String? = null,
    val inputLabel4En: String? = null,
    val defaultInput: String = "10",
    val defaultInput2: String = "400",
    val defaultInput3: String = "50",
    val defaultInput4: String = "50",
    val variantLabelEn: String? = null,
    val variants: List<ConversionVariant> = emptyList()
)

data class ConversionCategory(
    val id: String,
    val titleEn: String,
    val tools: List<ConversionTool>
)

sealed class ConversionOutput {
    data class CableResult(
        val result: CableConversionResult
    ) : ConversionOutput()

    data class SingleValueResult(
        val value: Double,
        val unitEn: String,
        val noteEn: String? = null
    ) : ConversionOutput()

    data class MultiValueResult(
        val values: List<Pair<String, String>>,
        val noteEn: String? = null
    ) : ConversionOutput()

    data class TextResult(
        val textEn: String
    ) : ConversionOutput()

    data class Invalid(
        val messageEn: String = "Please enter a valid positive number"
    ) : ConversionOutput()
}

data class SwgGaugeEntry(
    val gauge: String,
    val diameterMm: Double,
    val areaMm2: Double
)
