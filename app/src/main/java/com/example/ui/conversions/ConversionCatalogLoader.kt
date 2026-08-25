package com.example.ui.conversions

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.ui.designsystem.icons.ElectricalIcons
import org.json.JSONObject

object ConversionCatalogLoader {

    private var cachedCategories: List<ConversionCategory>? = null
    private var cachedSwgTable: Map<String, SwgGaugeEntry>? = null
    private var cachedAwgOptions: List<String>? = null

    fun getIcon(iconName: String): ImageVector {
        return when (iconName.trim().lowercase()) {
            "cable" -> ElectricalIcons.Conduit
            "power" -> ElectricalIcons.Power
            "sync", "speed" -> Icons.Default.Sync
            "tune", "torque" -> Icons.Default.Tune
            "flash", "capacitance" -> Icons.Default.FlashOn
            "device", "voltage", "current" -> Icons.Default.ElectricBolt
            "thermal", "temp" -> Icons.Default.Thermostat
            else -> Icons.Default.SwapHoriz
        }
    }

    fun loadCategories(context: Context): List<ConversionCategory> {
        cachedCategories?.let { return it }

        val jsonStr = context.assets.open("conversions_catalog.json").bufferedReader().use { it.readText() }
        val root = JSONObject(jsonStr)
        val catArray = root.getJSONArray("categories")
        val categories = mutableListOf<ConversionCategory>()

        for (i in 0 until catArray.length()) {
            val catObj = catArray.getJSONObject(i)
            val catId = catObj.getString("id")
            val titleEn = catObj.getString("titleEn")

            val toolsArray = catObj.getJSONArray("tools")
            val tools = mutableListOf<ConversionTool>()

            for (j in 0 until toolsArray.length()) {
                val toolObj = toolsArray.getJSONObject(j)
                val toolId = toolObj.getString("id")
                val tEn = toolObj.getString("titleEn")
                val dEn = toolObj.getString("descriptionEn")
                val icon = toolObj.optString("icon", "Swap")
                val isCable = toolObj.optBoolean("isCable", false)
                val inputType = toolObj.optString("inputType", "numeric")
                val iLabelEn = toolObj.optString("inputLabelEn", "Value")
                val iLabel2En = toolObj.optString("inputLabel2En")
                val iLabel3En = toolObj.optString("inputLabel3En")
                val iLabel4En = toolObj.optString("inputLabel4En")
                val defInput = toolObj.optString("defaultInput", "10")
                val defInput2 = toolObj.optString("defaultInput2", "400")
                val defInput3 = toolObj.optString("defaultInput3", "50")
                val defInput4 = toolObj.optString("defaultInput4", "50")
                val vLabelEn = toolObj.optString("variantLabelEn")

                val variants = mutableListOf<ConversionVariant>()
                if (toolObj.has("variants")) {
                    val vArray = toolObj.getJSONArray("variants")
                    for (k in 0 until vArray.length()) {
                        val vObj = vArray.getJSONObject(k)
                        variants.add(
                            ConversionVariant(
                                id = vObj.getString("id"),
                                labelEn = vObj.getString("labelEn")
                            )
                        )
                    }
                }

                tools.add(
                    ConversionTool(
                        id = toolId,
                        titleEn = tEn,
                        descriptionEn = dEn,
                        icon = icon,
                        isCable = isCable,
                        inputType = inputType,
                        inputLabelEn = iLabelEn,
                        inputLabel2En = iLabel2En,
                        inputLabel3En = iLabel3En,
                        inputLabel4En = iLabel4En,
                        defaultInput = defInput,
                        defaultInput2 = defInput2,
                        defaultInput3 = defInput3,
                        defaultInput4 = defInput4,
                        variantLabelEn = vLabelEn,
                        variants = variants
                    )
                )
            }

            categories.add(ConversionCategory(catId, titleEn, tools))
        }

        cachedCategories = categories
        return categories
    }

    fun loadSwgTable(context: Context): Map<String, SwgGaugeEntry> {
        cachedSwgTable?.let { return it }
        val jsonStr = context.assets.open("awg_swg_kcmil_table.json").bufferedReader().use { it.readText() }
        val root = JSONObject(jsonStr)
        val swgObj = root.getJSONObject("swg_table")
        val map = LinkedHashMap<String, SwgGaugeEntry>()

        val keys = swgObj.keys()
        while (keys.hasNext()) {
            val gaugeKey = keys.next()
            val entryObj = swgObj.getJSONObject(gaugeKey)
            val d = entryObj.getDouble("diameterMm")
            val a = entryObj.getDouble("areaMm2")
            map[gaugeKey] = SwgGaugeEntry(gaugeKey, d, a)
        }

        cachedSwgTable = map
        return map
    }

    fun loadAwgOptions(context: Context): List<String> {
        cachedAwgOptions?.let { return it }
        val jsonStr = context.assets.open("awg_swg_kcmil_table.json").bufferedReader().use { it.readText() }
        val root = JSONObject(jsonStr)
        val arr = root.getJSONArray("awg_options")
        val list = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            list.add(arr.getString(i))
        }
        cachedAwgOptions = list
        return list
    }
}
