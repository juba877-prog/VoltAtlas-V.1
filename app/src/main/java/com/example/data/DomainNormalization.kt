package com.example.data

/**
 * Domain String Normalization Layer (KT-06).
 *
 * Normalizes free-form, case-insensitive, or localized domain strings
 * (phase systems, conductor materials, insulation types, cable types, conduit types)
 * into canonical representation at entity ingestion boundaries before reaching
 * calculation engines.
 */
object DomainNormalization {

    /**
     * Normalizes phase system string to canonical format ("3P+N", "3P", "1P+N", "1P").
     * Recognizes variants like "3-Phase", "3 Phase", "Three-Phase", "THREE PHASE", "3ph", "3PH+N", etc.
     */
    fun normalizePhases(raw: String?): String {
        if (raw.isNullOrBlank()) return "3P+N"
        val trimmed = raw.trim()
        val upper = trimmed.uppercase()

        val isThreePhase = upper.contains("3P") ||
                upper.contains("3-PHASE") ||
                upper.contains("3 PHASE") ||
                upper.contains("THREE-PHASE") ||
                upper.contains("THREE PHASE") ||
                upper.contains("3PH") ||
                upper == "3"

        if (isThreePhase) {
            val isThreeWireOnly = upper.contains("3P-3W") ||
                    upper.contains("3W") ||
                    upper.contains("NO NEUTRAL") ||
                    (upper.contains("3P") && !upper.contains("N") && !upper.contains("NEUTRAL"))
            return if (isThreeWireOnly) "3P" else "3P+N"
        }

        val isSinglePhase = upper.contains("1P") ||
                upper.contains("1-PHASE") ||
                upper.contains("1 PHASE") ||
                upper.contains("SINGLE-PHASE") ||
                upper.contains("SINGLE PHASE") ||
                upper.contains("1PH") ||
                upper == "1"

        if (isSinglePhase) {
            val isSingleWireOnly = upper.contains("1P-2W") ||
                    (upper.contains("1P") && !upper.contains("N") && !upper.contains("NEUTRAL"))
            return if (isSingleWireOnly) "1P" else "1P+N"
        }

        return trimmed
    }

    /**
     * Normalizes conductor material string ("Cu", "Copper", "cu" -> "Cu"; "Al", "Aluminum", "Aluminium", "al" -> "Al").
     */
    fun normalizeConductorMaterial(raw: String?): String {
        if (raw.isNullOrBlank()) return "Cu"
        val upper = raw.trim().uppercase()
        return when {
            upper == "CU" || upper == "COPPER" -> "Cu"
            upper == "AL" || upper == "ALUMINUM" || upper == "ALUMINIUM" || upper == "ALUM." -> "Al"
            else -> raw.trim()
        }
    }

    /**
     * Normalizes insulation string ("XLPE", "xlpe", "Cross-linked Polyethylene" -> "XLPE"; "PVC", "pvc", "Polyvinyl Chloride" -> "PVC").
     */
    fun normalizeInsulation(raw: String?): String {
        if (raw.isNullOrBlank()) return "XLPE"
        val upper = raw.trim().uppercase()
        return when {
            upper == "XLPE" || upper.contains("CROSS-LINKED") || upper.contains("XLPE") -> "XLPE"
            upper == "PVC" || upper.contains("POLYVINYL") -> "PVC"
            else -> raw.trim()
        }
    }

    /**
     * Normalizes cable type strings by mapping material and insulation synonyms
     * to catalog keys (e.g. "Copper/XLPE/SWA/PVC - 4C" -> "Cu/XLPE/SWA/PVC - 4C").
     */
    fun normalizeCableType(raw: String?): String {
        if (raw.isNullOrBlank()) return "Cu/XLPE/SWA/PVC - 4C"
        var result = raw.trim()

        // Normalize initial conductor prefix
        result = result.replace(Regex("(?i)^Copper[/\\s]"), "Cu/")
            .replace(Regex("(?i)^Aluminum[/\\s]"), "Al/")
            .replace(Regex("(?i)^Aluminium[/\\s]"), "Al/")
            .replace(Regex("(?i)^cu[/\\s]"), "Cu/")
            .replace(Regex("(?i)^al[/\\s]"), "Al/")

        // Normalize insulation tokens inside paths
        result = result.replace(Regex("(?i)/xlpe(?=[/\\s-])"), "/XLPE")
            .replace(Regex("(?i)/pvc(?=[/\\s-])"), "/PVC")
            .replace(Regex("(?i)/xlpe$"), "/XLPE")
            .replace(Regex("(?i)/pvc$"), "/PVC")

        // Normalize armour tokens inside paths
        result = result.replace(Regex("(?i)/swa(?=[/\\s-])"), "/SWA")
            .replace(Regex("(?i)/sta(?=[/\\s-])"), "/STA")
            .replace(Regex("(?i)/awa(?=[/\\s-])"), "/AWA")
            .replace(Regex("(?i)/swa$"), "/SWA")
            .replace(Regex("(?i)/sta$"), "/STA")
            .replace(Regex("(?i)/awa$"), "/AWA")

        return result
    }

    /**
     * Normalizes conduit type string to catalog-recognized types ("Rigid PVC", "EMT Steel", "GI Steel (RMC)").
     */
    fun normalizeConduitType(raw: String?): String {
        if (raw.isNullOrBlank()) return "Rigid PVC"
        val trimmed = raw.trim()
        val upper = trimmed.uppercase()
        return when {
            upper.contains("PVC") -> "Rigid PVC"
            upper.contains("EMT") -> "EMT Steel"
            upper.contains("GI") || upper.contains("RMC") || upper.contains("BS_4568") -> "GI Steel (RMC)"
            else -> trimmed
        }
    }

    /**
     * Extension to normalize a [CircuitEntity] at boundary points before storage or engine execution.
     */
    fun CircuitEntity.normalized(): CircuitEntity = this.copy(
        phases = normalizePhases(this.phases),
        cableType = normalizeCableType(this.cableType)
    )
}
