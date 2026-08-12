package com.example.data.catalog

object BreakerParser {
    fun parseRating(raw: Any?): Double {
        if (raw is Number) return raw.toDouble()
        val str = raw?.toString()?.trim() ?: return 0.0
        if (str.isEmpty() || str == "-") return 0.0
        
        val cleaned = str.replace("≥", "").replace("A", "").trim()
        if (cleaned.contains("–")) {
            val parts = cleaned.split("–")
            return parts.last().trim().toDoubleOrNull() ?: 0.0
        }
        if (cleaned.contains("-")) {
            val parts = cleaned.split("-")
            return parts.last().trim().toDoubleOrNull() ?: 0.0
        }
        if (cleaned.contains("/")) {
            val parts = cleaned.split("/")
            return parts.last().trim().toDoubleOrNull() ?: 0.0
        }
        return cleaned.toDoubleOrNull() ?: 0.0
    }

    fun parsePoles(raw: Any?): Int {
        if (raw is Number) return raw.toInt()
        val str = raw?.toString()?.trim() ?: return 3
        if (str.isEmpty() || str == "-") return 3
        
        if (str.contains("/")) {
            val parts = str.split("/")
            return parts.first().trim().toIntOrNull() ?: 3
        }
        return str.toIntOrNull() ?: 3
    }

    fun parseDouble(raw: Any?): Double {
        if (raw is Number) return raw.toDouble()
        val str = raw?.toString()?.trim() ?: return 0.0
        if (str.isEmpty() || str == "-") return 0.0
        
        val cleaned = str.replace("≥", "").trim()
        if (cleaned.contains("–")) {
            val parts = cleaned.split("–")
            return parts.last().trim().toDoubleOrNull() ?: 0.0
        }
        if (cleaned.contains("-")) {
            val parts = cleaned.split("-")
            return parts.last().trim().toDoubleOrNull() ?: 0.0
        }
        if (cleaned.contains("/")) {
            val parts = cleaned.split("/")
            return parts.last().trim().toDoubleOrNull() ?: 0.0
        }
        return cleaned.toDoubleOrNull() ?: 0.0
    }
}
