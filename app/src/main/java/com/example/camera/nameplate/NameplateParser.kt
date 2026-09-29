package com.example.camera.nameplate

import java.util.Locale
import kotlin.math.sqrt

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
        val fullTextLower = rawText.lowercase(Locale.ROOT)

        // 1. Equipment Type Detection
        var detectedType = detectEquipmentType(fullTextLower)

        // 2. Classify lines into primary vs secondary
        val secondaryPattern = Regex("""(?:usable\s+at|derated|alt(?:ernate)?\s*rat)""", RegexOption.IGNORE_CASE)
        val primaryLines = mutableListOf<String>()
        val secondaryLines = mutableListOf<String>()
        for (line in lines) {
            if (secondaryPattern.containsMatchIn(line)) {
                secondaryLines.add(line)
            } else {
                primaryLines.add(line)
            }
        }
        
        val refinedPrimary = mutableListOf<String>()
        var found60Hz = false
        for (line in primaryLines) {
            if (line.lowercase(Locale.ROOT).contains("60hz") || line.lowercase(Locale.ROOT).contains("60 hz")) {
                found60Hz = true
            }
        }
        
        for (line in primaryLines) {
            val is50hz = line.lowercase(Locale.ROOT).contains("50hz") || line.lowercase(Locale.ROOT).contains("50 hz")
            if (found60Hz && is50hz && !line.lowercase(Locale.ROOT).contains("60hz")) {
                secondaryLines.add(line)
            } else {
                refinedPrimary.add(line)
            }
        }

        // 3. Parse primary lines first
        var result = extractValues(refinedPrimary, matchedLines)

        // 4. Fallback: fill nulls from secondary lines
        val fallback = extractValues(secondaryLines, matchedLines)
        result = mergeResults(result, fallback)

        // 5. Auto-calculate missing PF
        val pk = result.powerKw
        val vv = result.voltageV
        val ca = result.currentAmps
        if (result.powerFactor == null && pk != null && vv != null && ca != null) {
            val eff = (result.efficiencyPct ?: 90.0) / 100.0
            val pf = (pk * 1000.0) / (sqrt(3.0) * vv * ca * eff)
            if (pf in 0.1..1.0) {
                result = result.copy(powerFactor = String.format(Locale.US, "%.2f", pf).toDouble())
            }
        }

        // 6. Refine equipment type, calculate confidence
        if (detectedType == NameplateEquipmentType.UNKNOWN) {
            when {
                result.kvaRating != null || result.impedancePercentZ != null -> detectedType = NameplateEquipmentType.TRANSFORMER
                result.powerKw != null || result.rpm != null -> detectedType = NameplateEquipmentType.MOTOR
            }
        }

        var detectedCount = 0
        if (result.powerKw != null) detectedCount++
        if (result.voltageV != null) detectedCount++
        if (result.currentAmps != null) detectedCount++
        if (result.powerFactor != null) detectedCount++
        if (result.efficiencyPct != null) detectedCount++
        if (result.rpm != null) detectedCount++
        if (result.impedancePercentZ != null) detectedCount++
        if (result.kvaRating != null) detectedCount++

        val confidence = (detectedCount.toFloat() / 5.0f).coerceIn(0.0f, 1.0f)

        return result.copy(
            equipmentType = detectedType,
            confidenceScore = confidence,
            rawMatchedLines = matchedLines
        )
    }

    private fun detectEquipmentType(fullTextLower: String): NameplateEquipmentType {
        if (fullTextLower.contains("motor") || fullTextLower.contains("induktion") || (fullTextLower.contains("kw") && fullTextLower.contains("rpm"))) {
            return NameplateEquipmentType.MOTOR
        }
        if (fullTextLower.contains("transformer") || fullTextLower.contains("trafo") || (fullTextLower.contains("kva") && fullTextLower.contains("uk"))) {
            return NameplateEquipmentType.TRANSFORMER
        }
        if (fullTextLower.contains("breaker") || fullTextLower.contains("mccb") || fullTextLower.contains("acb") || fullTextLower.contains("icu")) {
            return NameplateEquipmentType.CIRCUIT_BREAKER
        }
        return NameplateEquipmentType.UNKNOWN
    }

    private fun extractValues(lines: List<String>, matchedLines: MutableList<String>): NameplateScanResult {
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
                            powerKw = String.format(Locale.US, "%.2f", num * 0.7457).toDouble()
                        }
                        matchedLines.add(line)
                    }
                }
            }

            // 4. Voltage (V)
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

            // 5. Current (Amps / FLA / In)
            if (currentAmps == null) {
                val prefixMatch = Regex("""(?<![a-zA-Z0-9])(?:i_n|in|fla|current|i|amps?|ampere)\s*[:=]?\s*(\d+(?:[.,]\d+)?)\s*(?:a(?:mp)?s?)?(?![a-zA-Z0-9])""", RegexOption.IGNORE_CASE).find(lineLower)
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
                val effRegex = Regex("""(?:(?:nema\s+)?nom\.?\s*)?(?:eff(?:iciency)?|[\u03b7\u0397])\.?\s*[:=.]?\s*(\d{1,2}(?:[.,]\d+)?)\s*%?""", RegexOption.IGNORE_CASE)
                effRegex.find(lineLower)?.let {
                    val num = it.groupValues[1].replace(',', '.').toDoubleOrNull()
                    if (num != null && num in 50.0..99.9) {
                        efficiencyPct = num
                        matchedLines.add(line)
                    }
                }
            }

            // 8. Frequency (Hz)
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

            // 9. RPM
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

        return NameplateScanResult(
            equipmentType = NameplateEquipmentType.UNKNOWN,
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
            confidenceScore = 0.0f,
            rawMatchedLines = matchedLines
        )
    }

    private fun mergeResults(primary: NameplateScanResult, fallback: NameplateScanResult): NameplateScanResult {
        return NameplateScanResult(
            equipmentType = primary.equipmentType,
            powerKw = primary.powerKw ?: fallback.powerKw,
            powerHp = primary.powerHp ?: fallback.powerHp,
            voltageV = primary.voltageV ?: fallback.voltageV,
            currentAmps = primary.currentAmps ?: fallback.currentAmps,
            powerFactor = primary.powerFactor ?: fallback.powerFactor,
            efficiencyPct = primary.efficiencyPct ?: fallback.efficiencyPct,
            frequencyHz = primary.frequencyHz ?: fallback.frequencyHz,
            rpm = primary.rpm ?: fallback.rpm,
            impedancePercentZ = primary.impedancePercentZ ?: fallback.impedancePercentZ,
            kvaRating = primary.kvaRating ?: fallback.kvaRating,
            confidenceScore = primary.confidenceScore,
            rawMatchedLines = primary.rawMatchedLines
        )
    }
}
