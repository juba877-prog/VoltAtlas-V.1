package com.example.ui.conversions

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class ConversionHistoryItem(
    val id: String,
    val toolId: String,
    val toolTitle: String,
    val inputSummary: String,
    val resultSummary: String,
    val timestamp: Long = System.currentTimeMillis()
)

class ConversionPreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("volt_atlas_conversions_prefs", Context.MODE_PRIVATE)

    // FAVORITES
    fun getFavorites(): Set<String> {
        return prefs.getStringSet("favorite_tool_ids", emptySet()) ?: emptySet()
    }

    fun isFavorite(toolId: String): Boolean {
        return getFavorites().contains(toolId)
    }

    fun toggleFavorite(toolId: String): Boolean {
        val current = getFavorites().toMutableSet()
        val isFav = if (current.contains(toolId)) {
            current.remove(toolId)
            false
        } else {
            current.add(toolId)
            true
        }
        prefs.edit().putStringSet("favorite_tool_ids", current).apply()
        return isFav
    }

    // HISTORY
    fun getHistory(): List<ConversionHistoryItem> {
        val jsonStr = prefs.getString("conversion_history_json", null) ?: return emptyList()
        val list = mutableListOf<ConversionHistoryItem>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    ConversionHistoryItem(
                        id = obj.optString("id", "${obj.optString("toolId")}_${obj.optLong("timestamp")}"),
                        toolId = obj.getString("toolId"),
                        toolTitle = obj.getString("toolTitle"),
                        inputSummary = obj.getString("inputSummary"),
                        resultSummary = obj.getString("resultSummary"),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun addHistoryItem(toolId: String, toolTitle: String, inputSummary: String, resultSummary: String) {
        if (inputSummary.isBlank() || resultSummary.isBlank()) return
        val current = getHistory().toMutableList()
        
        // Remove duplicate if same tool & same input summary exists
        current.removeAll { it.toolId == toolId && it.inputSummary == inputSummary }
        
        val newItem = ConversionHistoryItem(
            id = "${toolId}_${System.currentTimeMillis()}",
            toolId = toolId,
            toolTitle = toolTitle,
            inputSummary = inputSummary,
            resultSummary = resultSummary
        )
        current.add(0, newItem)

        // Limit history to 30 items max
        val trimmed = if (current.size > 30) current.take(30) else current

        val array = JSONArray()
        for (item in trimmed) {
            val obj = JSONObject().apply {
                put("id", item.id)
                put("toolId", item.toolId)
                put("toolTitle", item.toolTitle)
                put("inputSummary", item.inputSummary)
                put("resultSummary", item.resultSummary)
                put("timestamp", item.timestamp)
            }
            array.put(obj)
        }
        prefs.edit().putString("conversion_history_json", array.toString()).apply()
    }

    fun clearHistory() {
        prefs.edit().remove("conversion_history_json").apply()
    }
}
