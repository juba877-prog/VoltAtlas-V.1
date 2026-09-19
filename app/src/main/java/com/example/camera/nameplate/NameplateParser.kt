package com.example.camera.nameplate

import java.util.Locale

/**
 * Categorized electrical equipment types recognized from equipment nameplates.
 */
enum class NameplateEquipmentType {
    MOTOR,
    TRANSFORMER,
    CIRCUIT_BREAKER,
    UNKNOWN
}

/**
 * Structured electrical specifications extracted from OCR text of an equipment nameplate.
 */
data class NameplateScanResult(
    val equipmentType: NameplateEquipmentType,
    val powerKw: Double? = null,
    val powerHp: Double? = null,
    val voltageV: Double? = null,
    val currentAmps: Double? = null,
    val powerFactor: Double? = null,
    val efficiencyPct: Double? = null,
    val frequencyHz: Double? = null,
    val rpm: Int? = null,
    val impedancePercentZ: Double? = null,
    val kvaRating: Double? = null,
    val confidenceScore: Float = 0.0f,
    val rawMatchedLines: List<String> = emptyList()
)

/**
 * Deterministic regex and token parser extracting electrical parameters from OCR text lines.
 * Handles diverse international formats (IEC, NEMA, DIN) and optical scanning variations.
 */
object NameplateParser {

    fun parse(rawText: String): NameplateScanResult {
        val lines = rawText.lines().map { it.trim() }.filter { it.isNotBlank() }
        val matchedLines = mutableListOf<String>()

        var detectedType = NameplateEquipmentType.UNKNOWN
        var powerKw: Double? = null
        var powerHp: Double? = null
        var voltageV: Double? = null
        var currentAmps: Double? = null
        var powerFactor: Double? = null
        var efficiencyPct: Double? = null
        var frequencyHz: Double? = null
        var rpm: Int? = null
        var impedancePercentZ: Double? = null
        var kvaRating: Double? = null

        val fullTextLower = rawText.lowercase(Locale.ROOT)

        // 1. Equipment Type Detection
        when {
            fullTextLower.contains("motor") || fullTextLower.contains("induktion") || (fullTextLower.contains("kw") && fullTextLower.contains("rpm")) -> {
                detectedType = NameplateEquipmentType.MOTOR
            }
            fullTextLower.contains("transformer") || fullTextLower.contains("trafo") || (fullTextLower.contains("kva") && fullTextLower.contains("uk")) -> {
                detectedType = NameplateEquipmentType.TRANSFORMER
            }
            fullTextLower.contains("breaker") || fullTextLower.contains("mccb") || fullTextLower.contains("acb") || fullTextLower.contains("icu") -> {
                detectedType = NameplateEquipmentType.CIRCUIT_BREAKER
            }
        }

        for (line in lines) {
            val lineLower = line.lowercase(Locale.ROOT)

            // 2. Power in kW
            if (powerKw == null) {
                val kwRegex = Regex("""(?:output|p2|rating|power)?\s*[:=]?\s*(\d+(?:[.,]\d+)?)\s*(?:kw|k\.w\.)""", RegexOption.IGNORE_CASE)
                kwRegex.find(lineLower)?.let {
                    val num = it.groupValues[1].replace(',', '.').toDoubleOrNull()
                    if (num != null && num > 0.0) {
                        powerKw = num
                        matchedLines.add(line)
                    }
                }
            }

            // 3. Power in HP
            if (powerHp == null) {
                val hpRegex = Regex("""(?:hp|h\.p\.)\s*[:=]?\s*(\d+(?:[.,]\d+)?)|(\d+(?:[.,]\d+)?)\s*(?:hp|h\.p\.)""", RegexOption.IGNORE_CASE)
                hpRegex.find(lineLower)?.let {
                    val rawStr = it.groupValues[1].ifBlank { it.groupValues[2] }
                    val num = rawStr.replace(',', '.').toDoubleOrNull()
                    if (num != null && num > 0.0) {
                        powerHp = num
                        if (powerKw == null) {
                            powerKw = String.format(Locale.US, "%.2f", num * 0.746).toDouble()
                        }
                        matchedLines.add(line)
                    }
                }
            }

            // 4. Voltage (V) - Support both prefix (V: 400, Volts: 460) and suffix (400 V)
            if (voltageV == null) {
                val prefixMatch = Regex("""(?<![a-zA-Z0-9])(?:u_n|u|volts?|voltage|v)\s*[:=]?\s*(\d{2,5})(?![a-zA-Z0-9])""", RegexOption.IGNORE_CASE).find(lineLower)
                val suffixMatch = Regex("""(?<![a-zA-Z0-9])(\d{2,5})\s*(?:v|volts|vac)(?![a-zA-Z0-9])""", RegexOption.IGNORE_CASE).find(lineLower)
                val match = prefixMatch ?: suffixMatch
                match?.let {
                    val num = it.groupValues[1].toDoubleOrNull()
                    if (num != null && num in 100.0..36000.0) {
                        voltageV = num
                        matchedLines.add(line)
                    }
                }
            }

            // 5. Current (Amps / FLA / In) - Support both prefix (I: 82.0, FLA: 13.4) and suffix (82 A)
            if (currentAmps == null) {
                val prefixMatch = Regex("""(?<![a-zA-Z0-9])(?:i_n|in|fla|current|i)\s*[:=]?\s*(\d+(?:[.,]\d+)?)(?![a-zA-Z0-9])""", RegexOption.IGNORE_CASE).find(lineLower)
                val suffixMatch = Regex("""(?<![a-zA-Z0-9])(\d+(?:[.,]\d+)?)\s*(?:a|amp|amps)(?![a-zA-Z0-9])""", RegexOption.IGNORE_CASE).find(lineLower)
                val match = prefixMatch ?: suffixMatch
                match?.let {
                    val num = it.groupValues[1].replace(',', '.').toDoubleOrNull()
                    if (num != null && num > 0.0) {
                        currentAmps = num
                        matchedLines.add(line)
                    }
                }
            }

            // 6. Power Factor (cos φ / PF)
            if (powerFactor == null) {
                val pfRegex = Regex("""(?:cos\s*[\u03c6\u03a6]?|pf|p\.f\.)\s*[:=]?\s*(0[.,]\d{1,3})""", RegexOption.IGNORE_CASE)
                pfRegex.find(lineLower)?.let {
                    val num = it.groupValues[1].replace(',', '.').toDoubleOrNull()
                    if (num != null && num in 0.1..1.0) {
                        powerFactor = num
                        matchedLines.add(line)
                    }
                }
            }

            // 7. Efficiency (η / EFF %)
            if (efficiencyPct == null) {
                val effRegex = Regex("""(?:eff|efficiency|[\u03b7\u0397])\s*[:=]?\s*(\d{1,2}(?:[.,]\d+)?)\s*%?""", RegexOption.IGNORE_CASE)
                effRegex.find(lineLower)?.let {
                    val num = it.groupValues[1].replace(',', '.').toDoubleOrNull()
                    if (num != null && num in 50.0..99.9) {
                        efficiencyPct = num
                        matchedLines.add(line)
                    }
                }
            }

            // 8. Frequency (Hz) - Support both prefix (HZ: 60) and suffix (50 Hz)
            if (frequencyHz == null) {
                val prefixMatch = Regex("""(?<![a-zA-Z0-9])(?:hz|freq|frequency)\s*[:=]?\s*(\d{2})(?![a-zA-Z0-9])""", RegexOption.IGNORE_CASE).find(lineLower)
                val suffixMatch = Regex("""(?<![a-zA-Z0-9])(\d{2})\s*(?:hz|cycles)(?![a-zA-Z0-9])""", RegexOption.IGNORE_CASE).find(lineLower)
                val match = prefixMatch ?: suffixMatch
                match?.let {
                    val num = it.groupValues[1].replace(',', '.').toDoubleOrNull()
                    if (num != null && (num == 50.0 || num == 60.0)) {
                        frequencyHz = num
                        matchedLines.add(line)
                    }
                }
            }

            // 9. RPM - Support both prefix (RPM: 1765) and suffix (1475 RPM)
            if (rpm == null) {
                val prefixMatch = Regex("""(?<![a-zA-Z0-9])(?:rpm|speed|r/min)\s*[:=]?\s*(\d{3,4})(?![a-zA-Z0-9])""", RegexOption.IGNORE_CASE).find(lineLower)
                val suffixMatch = Regex("""(?<![a-zA-Z0-9])(\d{3,4})\s*(?:rpm|r/min|min-1)(?![a-zA-Z0-9])""", RegexOption.IGNORE_CASE).find(lineLower)
                val match = prefixMatch ?: suffixMatch
                match?.let {
                    val num = it.groupValues[1].toIntOrNull()
                    if (num != null && num in 300..3600) {
                        rpm = num
                        matchedLines.add(line)
                    }
                }
            }

            // 10. Transformer %Z / Uk%
            if (impedancePercentZ == null) {
                val zRegex = Regex("""(?:u_k|uk|%z|z%|impedance)\s*[:=]?\s*(\d+(?:[.,]\d+)?)\s*%?""", RegexOption.IGNORE_CASE)
                zRegex.find(lineLower)?.let {
                    val num = it.groupValues[1].replace(',', '.').toDoubleOrNull()
                    if (num != null && num in 1.0..25.0) {
                        impedancePercentZ = num
                        matchedLines.add(line)
                    }
                }
            }

            // 11. kVA / MVA Rating
            if (kvaRating == null) {
                val kvaRegex = Regex("""(\d+(?:[.,]\d+)?)\s*(?:kva|mva)""", RegexOption.IGNORE_CASE)
                kvaRegex.find(lineLower)?.let {
                    val isMva = it.value.lowercase(Locale.ROOT).contains("mva")
                    val num = it.groupValues[1].replace(',', '.').toDoubleOrNull()
                    if (num != null && num > 0.0) {
                        kvaRating = if (isMva) num * 1000.0 else num
                        matchedLines.add(line)
                    }
                }
            }
        }

        // Refine equipment type if strong markers present
        if (detectedType == NameplateEquipmentType.UNKNOWN) {
            when {
                kvaRating != null || impedancePercentZ != null -> detectedType = NameplateEquipmentType.TRANSFORMER
                powerKw != null || rpm != null -> detectedType = NameplateEquipmentType.MOTOR
            }
        }

        // Calculate confidence score based on parameters found
        var detectedCount = 0
        if (powerKw != null) detectedCount++
        if (voltageV != null) detectedCount++
        if (currentAmps != null) detectedCount++
        if (powerFactor != null) detectedCount++
        if (efficiencyPct != null) detectedCount++
        if (rpm != null) detectedCount++
        if (impedancePercentZ != null) detectedCount++
        if (kvaRating != null) detectedCount++

        val confidence = (detectedCount.toFloat() / 5.0f).coerceIn(0.0f, 1.0f)

        return NameplateScanResult(
            equipmentType = detectedType,
            powerKw = powerKw,
            powerHp = powerHp,
            voltageV = voltageV,
            currentAmps = currentAmps,
            powerFactor = powerFactor,
            efficiencyPct = efficiencyPct,
            frequencyHz = frequencyHz,
            rpm = rpm,
            impedancePercentZ = impedancePercentZ,
            kvaRating = kvaRating,
            confidenceScore = confidence,
            rawMatchedLines = matchedLines
        )
    }
}
