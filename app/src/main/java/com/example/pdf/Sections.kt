package com.example.pdf

import android.graphics.Paint

fun formatReportStatus(rawStatus: String?): String {
    if (rawStatus.isNullOrBlank()) return "-"
    return when (rawStatus.uppercase().trim()) {
        "SELECT_LOADS" -> "Action Required — Select Loads"
        "DATA_REQUIRED" -> "Data Required"
        "NOT_CONFIGURED" -> "Not Configured"
        "NO_LOAD" -> "No Connected Load"
        "INVALID" -> "Engineering Review Required"
        "REVIEW REQUIRED", "REVIEW_REQUIRED" -> "Review Required"
        "DATA MISSING", "DATA_MISSING" -> "Data Missing"
        "NO CORRECTION REQUIRED", "NO_CORRECTION_REQUIRED" -> "No Correction Required"
        "NO STANDARD SIZE AVAILABLE", "NO_STANDARD_SIZE_AVAILABLE" -> "No Standard Size Available"
        "NOT APPLICABLE", "NOT_APPLICABLE" -> "Not Applicable"
        "N/A — NO GENERATOR" -> "Not Configured — No Generator"
        else -> rawStatus.replace("_", " ").lowercase().split(" ").joinToString(" ") { word ->
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.US) else it.toString() }
        }
    }
}

class ExecutiveSummarySection : ReportSectionProvider {
    override val sectionName = "Executive Summary"
    override fun render(renderer: PdfDocumentRenderer, snapshot: ProjectReportSnapshot) {
        renderer.beginPage(true) // Portrait
        renderer.markSectionStart(sectionName)
        renderer.ensureSpace(40f, true) 
        renderer.drawText("1. EXECUTIVE SUMMARY", renderer.marginX, renderer.currentY - renderer.theme.headerPaint.ascent(), renderer.theme.headerPaint)
        renderer.currentY += 20f
        
        if (!renderer.isPass1) {
            renderer.canvas?.drawLine(renderer.marginX, renderer.currentY, renderer.pageWidth - renderer.marginX, renderer.currentY, renderer.theme.dividerPaint)
        }
        renderer.currentY += 15f
        
        val maxLoading = snapshot.transformerResults.values.maxOfOrNull { it.loadingPct } ?: 0.0
        val maxVd = snapshot.sizingResults.values.maxOfOrNull { it.cumulativeVDpct } ?: 0.0
        val maxIsc = snapshot.shortCircuitResults.values.maxOfOrNull { it.iscKA } ?: 0.0
        
        val rawStatus = when {
            snapshot.sizingResults.values.any { it.status == "FAIL" } || snapshot.transformerResults.values.any { it.loadingPct > 100 } -> "FAIL"
            snapshot.sizingResults.values.any { it.status == "REVIEW REQUIRED" } || snapshot.transformerResults.values.any { it.loadingPct > 80 } -> "REVIEW REQUIRED"
            else -> "PASS"
        }
        val cableTxStatus = formatReportStatus(rawStatus)
        
        val clientLine = snapshot.project.clientName?.takeIf { it.isNotBlank() }?.let { "Client: $it\n" } ?: ""
        val content = """
            Project Name: ${snapshot.project.projectName}
            ${clientLine}Cable & Transformer Validation: $cableTxStatus
            
            Key Metrics:
            - Active Circuits: ${snapshot.circuits.size}
            - Active Transformers: ${snapshot.transformers.size}
            - Max Transformer Loading: ${"%.1f".format(maxLoading)} %
            - Max Cumulative Voltage Drop: ${"%.2f".format(maxVd)} %
            - Max Short Circuit Current (3φ): ${"%.2f".format(maxIsc)} kA
        """.trimIndent()
        
        val p = renderer.theme.bodyPaint
        
        content.split("\n").forEach { text ->
            renderer.drawWrappedText(text, renderer.marginX, renderer.pageWidth - 2 * renderer.marginX, p)
            renderer.currentY += 15f
        }
    }
}

class CircuitsSection : ReportSectionProvider {
    override val sectionName = "Circuits & Cable Sizing"
    override fun render(renderer: PdfDocumentRenderer, snapshot: ProjectReportSnapshot) {
        renderer.beginPage(false) // Landscape
        renderer.markSectionStart(sectionName)
        
        renderer.drawText("2. CIRCUITS & CABLE SIZING", renderer.marginX, renderer.currentY - renderer.theme.headerPaint.ascent(), renderer.theme.headerPaint)
        renderer.currentY += 20f
        if (!renderer.isPass1) {
            renderer.canvas?.drawLine(renderer.marginX, renderer.currentY, renderer.pageWidth - renderer.marginX, renderer.currentY, renderer.theme.dividerPaint)
        }
        renderer.currentY += 15f
        
        val contentWidth = renderer.pageWidth - 2 * renderer.marginX
        val colWidth = contentWidth / 7.5f
        
        val headers = listOf(
            TableColumn("Circuit", colWidth * 0.8f, renderer.theme.tableHeaderPaint),
            TableColumn("Fed From", colWidth * 0.7f, renderer.theme.tableHeaderPaint),
            TableColumn("Panel", colWidth * 0.6f, renderer.theme.tableHeaderPaint),
            TableColumn("kVA", colWidth * 0.4f, renderer.theme.tableHeaderPaint, TextAlign.RIGHT),
            TableColumn("Ib (A)", colWidth * 0.4f, renderer.theme.tableHeaderPaint, TextAlign.RIGHT),
            TableColumn("Device", colWidth * 0.9f, renderer.theme.tableHeaderPaint),
            TableColumn("Cable", colWidth * 1.2f, renderer.theme.tableHeaderPaint),
            TableColumn("Iz (A)", colWidth * 0.4f, renderer.theme.tableHeaderPaint, TextAlign.RIGHT),
            TableColumn("Util %", colWidth * 0.4f, renderer.theme.tableHeaderPaint, TextAlign.RIGHT),
            TableColumn("Local VD %", colWidth * 0.5f, renderer.theme.tableHeaderPaint, TextAlign.RIGHT),
            TableColumn("Cum. VD %", colWidth * 0.5f, renderer.theme.tableHeaderPaint, TextAlign.RIGHT),
            TableColumn("Status", colWidth * 0.7f, renderer.theme.tableHeaderPaint)
        )
        
        val rows = snapshot.circuits.map { circuit ->
            val result = snapshot.sizingResults[circuit.id]
            val msgs = result?.failReason ?: ""
            val formattedStatus = formatReportStatus(result?.status ?: "-")
            val statusText = if (msgs.isNotBlank()) "$formattedStatus\n$msgs" else formattedStatus
            val utilPct = result?.utilizationPct?.let { "%.1f".format(it) } ?: "-"
            
            val devType = result?.deviceType?.takeIf { it.isNotBlank() } ?: "CB"
            val devSize = result?.deviceSize_A?.let { "%.0f".format(it) } ?: "-"
            val deviceText = "$devType $devSize A"
            
            val cableText = if (result?.cableSize_mm2 != null) "${circuit.cableType} ${result.cableSize_mm2}" else "-"
            val panelType = circuit.panelType?.takeIf { it.isNotBlank() } ?: "-"
            
            TableRow(listOf(
                circuit.circuitName,
                circuit.fedFrom,
                panelType,
                "%.1f".format(circuit.loadKVA),
                result?.Ib_A?.let { "%.1f".format(it) } ?: "-",
                deviceText,
                cableText,
                result?.capacity_derated_Iz_A?.let { "%.1f".format(it) } ?: "-",
                utilPct,
                result?.vdPct?.let { "%.2f".format(it) } ?: "-",
                result?.cumulativeVDpct?.let { "%.2f".format(it) } ?: "-",
                statusText
            ))
        }
        
        renderer.drawTable(headers, rows, renderer.marginX)
    }
}

class TransformersSection : ReportSectionProvider {
    override val sectionName = "Transformers"
    override fun render(renderer: PdfDocumentRenderer, snapshot: ProjectReportSnapshot) {
        renderer.beginPage(true) // Portrait
        renderer.markSectionStart(sectionName)
        
        renderer.drawText("3. TRANSFORMERS", renderer.marginX, renderer.currentY - renderer.theme.headerPaint.ascent(), renderer.theme.headerPaint)
        renderer.currentY += 20f
        if (!renderer.isPass1) {
            renderer.canvas?.drawLine(renderer.marginX, renderer.currentY, renderer.pageWidth - renderer.marginX, renderer.currentY, renderer.theme.dividerPaint)
        }
        renderer.currentY += 15f
        
        val contentWidth = renderer.pageWidth - 2 * renderer.marginX
        val colWidth = contentWidth / 6f
        
        val headers = listOf(
            TableColumn("Transformer", colWidth, renderer.theme.tableHeaderPaint),
            TableColumn("%Z / Vector", colWidth, renderer.theme.tableHeaderPaint),
            TableColumn("kVA", colWidth * 0.8f, renderer.theme.tableHeaderPaint, TextAlign.RIGHT),
            TableColumn("FLC (A)", colWidth * 0.8f, renderer.theme.tableHeaderPaint, TextAlign.RIGHT),
            TableColumn("Isc (kA)", colWidth * 0.8f, renderer.theme.tableHeaderPaint, TextAlign.RIGHT),
            TableColumn("Zt (mΩ)", colWidth * 0.6f, renderer.theme.tableHeaderPaint, TextAlign.RIGHT),
            TableColumn("Loading %", colWidth, renderer.theme.tableHeaderPaint, TextAlign.RIGHT)
        )
        
        val rows = snapshot.transformers.map { tx ->
            val result = snapshot.transformerResults[tx.transformerId]
            TableRow(listOf(
                "${tx.transformerId}\n(${tx.txType})",
                "${tx.percentZ}% / ${tx.vectorGroup}",
                "${tx.kVA}",
                result?.flc?.let { "%.1f".format(it) } ?: "-",
                result?.isc3Phase?.let { "%.2f".format(it) } ?: "-",
                result?.zt?.let { "%.1f".format(it) } ?: "-",
                result?.loadingPct?.let { "%.1f".format(it) } ?: "-"
            ))
        }
        
        renderer.drawTable(headers, rows, renderer.marginX)
    }
}

class PowerSystemsSection : ReportSectionProvider {
    override val sectionName = "Power Systems"
    override fun render(renderer: PdfDocumentRenderer, snapshot: ProjectReportSnapshot) {
        renderer.beginPage(true)
        renderer.markSectionStart(sectionName)
        renderer.ensureSpace(40f, renderer.currentIsPortrait)
        renderer.drawText("4. POWER SYSTEMS", renderer.marginX, renderer.currentY - renderer.theme.headerPaint.ascent(), renderer.theme.headerPaint)
        renderer.currentY += 20f
        if (!renderer.isPass1) renderer.canvas?.drawLine(renderer.marginX, renderer.currentY, renderer.pageWidth - renderer.marginX, renderer.currentY, renderer.theme.dividerPaint)
        renderer.currentY += 15f
        var currentY = renderer.currentY
        fun drawKeyValue(key: String, value: String) {
            renderer.drawText("$key: $value", renderer.marginX, currentY, renderer.theme.bodyPaint)
            currentY += 15f
        }
        
        snapshot.generatorResult?.let { gen ->
            renderer.drawText("GENERATOR", renderer.marginX, currentY, renderer.theme.tableHeaderPaint)
            currentY += 15f
            drawKeyValue("Design kVA", "%.1f".format(gen.designCapacity))
            drawKeyValue("Status", formatReportStatus(gen.status))
            currentY += 10f
        }
        
        snapshot.atsResult?.let { ats ->
            renderer.drawText("ATS", renderer.marginX, currentY, renderer.theme.tableHeaderPaint)
            currentY += 15f
            drawKeyValue("Governing Current", "%.1f A".format(ats.governingCurrent))
            drawKeyValue("Status", formatReportStatus(ats.status))
            currentY += 10f
        }
        
        snapshot.upsResult?.let { ups ->
            renderer.drawText("UPS", renderer.marginX, currentY, renderer.theme.tableHeaderPaint)
            currentY += 15f
            drawKeyValue("Design kVA", "%.1f".format(ups.designUpsKVA))
            drawKeyValue("Status", formatReportStatus(ups.status))
            currentY += 10f
        }
        
        if (snapshot.pfCorrectionResults.isNotEmpty()) {
            renderer.drawText("PF CORRECTION", renderer.marginX, currentY, renderer.theme.tableHeaderPaint)
            currentY += 15f
            snapshot.pfCorrectionResults.forEach { pf ->
                drawKeyValue(pf.transformerId, "Existing PF: ${"%.2f".format(pf.existingPF)} -> Target PF: ${"%.2f".format(pf.targetPf)} | Bank: ${"%.1f".format(pf.bankCapacity)} kVAR | Status: ${formatReportStatus(pf.status)}")
            }
            currentY += 10f
        }
        
        if (snapshot.generatorResult == null && snapshot.atsResult == null && snapshot.upsResult == null && snapshot.pfCorrectionResults.isEmpty()) {
            renderer.drawText("Not configured", renderer.marginX, currentY, renderer.theme.tableBodyPaint)
            currentY += 15f
        }
        
        renderer.currentY = currentY
    }
}

class ProtectionSection : ReportSectionProvider {
    override val sectionName = "Protection & Short Circuit"
    override fun render(renderer: PdfDocumentRenderer, snapshot: ProjectReportSnapshot) {
        renderer.beginPage(false) // Landscape
        renderer.markSectionStart(sectionName)
        renderer.ensureSpace(40f, renderer.currentIsPortrait)
        renderer.drawText("5. PROTECTION & SHORT CIRCUIT", renderer.marginX, renderer.currentY - renderer.theme.headerPaint.ascent(), renderer.theme.headerPaint)
        renderer.currentY += 20f
        if (!renderer.isPass1) renderer.canvas?.drawLine(renderer.marginX, renderer.currentY, renderer.pageWidth - renderer.marginX, renderer.currentY, renderer.theme.dividerPaint)
        renderer.currentY += 15f
        val contentWidth = renderer.pageWidth - 2 * renderer.marginX
        val colWidth = contentWidth / 6f
        
        val headers = listOf(
            TableColumn("Node / Circuit", colWidth * 1.2f, renderer.theme.tableHeaderPaint),
            TableColumn("Fault Type", colWidth * 0.8f, renderer.theme.tableHeaderPaint),
            TableColumn("Isc (kA)", colWidth * 0.8f, renderer.theme.tableHeaderPaint, TextAlign.RIGHT),
            TableColumn("Zs (Ω)", colWidth * 0.8f, renderer.theme.tableHeaderPaint, TextAlign.RIGHT),
            TableColumn("Disc. Time (s)", colWidth * 0.8f, renderer.theme.tableHeaderPaint, TextAlign.RIGHT),
            TableColumn("Status", colWidth * 0.6f, renderer.theme.tableHeaderPaint)
        )
        
        val rows = mutableListOf<TableRow>()
        
        snapshot.shortCircuitResults.forEach { (circuitId, result) ->
            val circuit = snapshot.circuits.find { it.id == circuitId }
            val nodeName = circuit?.circuitName?.takeIf { it.isNotBlank() } ?: result.sourceName
            
            // 1. 3-Phase Short Circuit
            rows.add(TableRow(listOf(
                nodeName,
                "3-Phase",
                "%.2f".format(result.iscKA),
                "-",
                "-",
                formatReportStatus(result.status)
            )))
            
            // 2. Earth Fault / ADS (if present for this circuit)
            val ads = snapshot.adsResults[circuitId]
            if (ads != null) {
                rows.add(TableRow(listOf(
                    nodeName,
                    "Earth Fault (ADS)",
                    "-",
                    "%.4f".format(ads.calculatedZs),
                    ads.disconnectionTime,
                    formatReportStatus(ads.status)
                )))
            }
        }
        
        renderer.drawTable(headers, rows, renderer.marginX)
    }
}

class ConduitsSection : ReportSectionProvider {
    override val sectionName = "Conduits"
    override fun render(renderer: PdfDocumentRenderer, snapshot: ProjectReportSnapshot) {
        renderer.beginPage(false) // Landscape
        renderer.markSectionStart(sectionName)
        renderer.ensureSpace(40f, renderer.currentIsPortrait)
        renderer.drawText("6. CONDUITS", renderer.marginX, renderer.currentY - renderer.theme.headerPaint.ascent(), renderer.theme.headerPaint)
        renderer.currentY += 20f
        if (!renderer.isPass1) renderer.canvas?.drawLine(renderer.marginX, renderer.currentY, renderer.pageWidth - renderer.marginX, renderer.currentY, renderer.theme.dividerPaint)
        renderer.currentY += 15f
        val contentWidth = renderer.pageWidth - 2 * renderer.marginX
        val colWidth = contentWidth / 6f
        
        val headers = listOf(
            TableColumn("Circuit", colWidth * 1.5f, renderer.theme.tableHeaderPaint),
            TableColumn("Cable OD (mm)", colWidth, renderer.theme.tableHeaderPaint, TextAlign.RIGHT),
            TableColumn("Conduit Size", colWidth, renderer.theme.tableHeaderPaint),
            TableColumn("Qty", colWidth * 0.5f, renderer.theme.tableHeaderPaint, TextAlign.RIGHT),
            TableColumn("Fill %", colWidth, renderer.theme.tableHeaderPaint, TextAlign.RIGHT),
            TableColumn("Status", colWidth, renderer.theme.tableHeaderPaint)
        )
        
        val rows = snapshot.conduitResults.map { (circuitId, result) ->
            TableRow(listOf(
                result.circuitName,
                "%.1f".format(result.mainCableOD_mm),
                result.recommendedTradeSize,
                "${result.conduitsRequired}",
                "%.1f".format(result.fillRatioPercent),
                formatReportStatus(result.status)
            ))
        }
        
        renderer.drawTable(headers, rows, renderer.marginX)
    }
}

private data class AccessoryGroupKey(
    val category: String,
    val catalogCode: String,
    val fallbackDesc: String,
    val cableSize: String
)

class BoqSection : ReportSectionProvider {
    override val sectionName = "BOQ & Procurement"
    override fun render(renderer: PdfDocumentRenderer, snapshot: ProjectReportSnapshot) {
        renderer.beginPage(false) // Landscape
        renderer.markSectionStart(sectionName)
        renderer.ensureSpace(40f, renderer.currentIsPortrait)
        renderer.drawText("7. BOQ & PROCUREMENT", renderer.marginX, renderer.currentY - renderer.theme.headerPaint.ascent(), renderer.theme.headerPaint)
        renderer.currentY += 20f
        if (!renderer.isPass1) renderer.canvas?.drawLine(renderer.marginX, renderer.currentY, renderer.pageWidth - renderer.marginX, renderer.currentY, renderer.theme.dividerPaint)
        renderer.currentY += 15f
        val contentWidth = renderer.pageWidth - 2 * renderer.marginX
        val colWidth = contentWidth / 4f
        
        // 1. Cables
        renderer.ensureSpace(40f, false)
        renderer.drawText("CABLES", renderer.marginX, renderer.currentY, renderer.theme.tableHeaderPaint)
        renderer.currentY += 15f
        
        val cableHeaders = listOf(
            TableColumn("Cable", colWidth * 2, renderer.theme.tableHeaderPaint),
            TableColumn("Length (m)", colWidth, renderer.theme.tableHeaderPaint, TextAlign.RIGHT),
            TableColumn("Weight (kg)", colWidth, renderer.theme.tableHeaderPaint, TextAlign.RIGHT)
        )
        val cableRows = snapshot.boq.map { item ->
            TableRow(listOf(
                "${item.cableType} ${item.size}",
                "%.1f".format(item.totalLength_m),
                "%.1f".format(item.totalWeight_kg)
            ))
        }
        if (cableRows.isNotEmpty()) renderer.drawTable(cableHeaders, cableRows, renderer.marginX)
        
        renderer.currentY += 20f
        
        // 2. Accessories
        renderer.ensureSpace(40f, false)
        renderer.drawText("ACCESSORIES (GLANDS, LUGS, TRAYS)", renderer.marginX, renderer.currentY, renderer.theme.tableHeaderPaint)
        renderer.currentY += 15f
        
        val accHeaders = listOf(
            TableColumn("Item", colWidth * 2, renderer.theme.tableHeaderPaint),
            TableColumn("Type / Size", colWidth, renderer.theme.tableHeaderPaint),
            TableColumn("Qty", colWidth, renderer.theme.tableHeaderPaint, TextAlign.RIGHT)
        )
        
        val accRows = mutableListOf<TableRow>()

        // Cable Glands
        val glandItems = snapshot.procurement.filter { it.glandModel != "N/A" && it.glandModel.isNotBlank() }
        val glandGroups = glandItems.groupBy { item ->
            AccessoryGroupKey(
                category = "Cable Gland",
                catalogCode = item.matchedGlandCatalogCode.takeIf { it.isNotBlank() && it != "N/A" } ?: "UNCODED",
                fallbackDesc = item.glandDesc.takeIf { it.isNotBlank() } ?: item.glandModel,
                cableSize = item.cableSize
            )
        }

        glandGroups.forEach { (key, items) ->
            val totalQty = items.sumOf { it.glandQty }
            val qtyStr = if (totalQty > 0) totalQty.toString() else "Not calculated"
            accRows.add(TableRow(listOf(key.fallbackDesc, key.cableSize, qtyStr)))
        }

        // Compression Lugs
        val lugItems = snapshot.procurement.filter { it.lugsType.isNotBlank() }
        val lugGroups = lugItems.groupBy { item ->
            AccessoryGroupKey(
                category = "Compression Lug",
                catalogCode = item.matchedLugCatalogCode.takeIf { it.isNotBlank() && it != "N/A" } ?: "UNCODED",
                fallbackDesc = item.lugsType,
                cableSize = item.cableSize
            )
        }

        lugGroups.forEach { (key, items) ->
            val totalQty = items.sumOf { it.lugQty }
            val qtyStr = if (totalQty > 0) totalQty.toString() else "Not calculated"
            accRows.add(TableRow(listOf(key.fallbackDesc, key.cableSize, qtyStr)))
        }

        if (accRows.isNotEmpty()) renderer.drawTable(accHeaders, accRows, renderer.marginX)
        
        renderer.currentY += 20f
        
        // 3. Drums
        renderer.ensureSpace(40f, false)
        renderer.drawText("DRUM SCHEDULE", renderer.marginX, renderer.currentY, renderer.theme.tableHeaderPaint)
        renderer.currentY += 15f
        
        val drumHeaders = listOf(
            TableColumn("Drum ID", colWidth, renderer.theme.tableHeaderPaint),
            TableColumn("Cable", colWidth * 1.5f, renderer.theme.tableHeaderPaint),
            TableColumn("Capacity (m)", colWidth * 0.5f, renderer.theme.tableHeaderPaint, TextAlign.RIGHT),
            TableColumn("Used (m)", colWidth * 0.5f, renderer.theme.tableHeaderPaint, TextAlign.RIGHT),
            TableColumn("Waste %", colWidth * 0.5f, renderer.theme.tableHeaderPaint, TextAlign.RIGHT)
        )
        val drumRows = snapshot.drums.map { drum ->
            TableRow(listOf(
                "Drum ${drum.drumIndex}",
                "${drum.cableType} ${drum.size}",
                "%.1f".format(drum.drumCapacity_m),
                "%.1f".format(drum.totalUsedLength_m),
                "%.1f".format(if (drum.drumCapacity_m > 0) (drum.wasteLength_m / drum.drumCapacity_m) * 100.0 else 0.0)
            ))
        }
        if (drumRows.isNotEmpty()) renderer.drawTable(drumHeaders, drumRows, renderer.marginX)
    }
}

class EarthingSection : ReportSectionProvider {
    override val sectionName = "Earthing System"
    override fun render(renderer: PdfDocumentRenderer, snapshot: ProjectReportSnapshot) {
        val earthing = snapshot.earthingResult ?: return
        renderer.beginPage(true) // Portrait
        renderer.markSectionStart(sectionName)
        renderer.ensureSpace(40f, renderer.currentIsPortrait)
        renderer.drawText("EARTHING SYSTEM VERIFICATION (IEC 60364-5-54)", renderer.marginX, renderer.currentY - renderer.theme.headerPaint.ascent(), renderer.theme.headerPaint)
        renderer.currentY += 20f
        if (!renderer.isPass1) renderer.canvas?.drawLine(renderer.marginX, renderer.currentY, renderer.pageWidth - renderer.marginX, renderer.currentY, renderer.theme.dividerPaint)
        renderer.currentY += 15f

        val labelX = renderer.marginX
        val valX = renderer.marginX + 200f
        var currentY = renderer.currentY

        val items = mutableListOf(
            "Status" to earthing.status,
            "Soil Classification" to earthing.soilClass,
            "Single Rod Resistance (R1)" to "%.2f Ω".format(earthing.singleRodResistance_ohm),
            "Required Rods / Earth Pits" to "${earthing.requiredRods} rods / ${earthing.earthPits} pits",
            "Achieved / Target Resistance" to "%.2f Ω / %.2f Ω".format(earthing.achievedResistance_ohm, earthing.targetResistance_ohm),
            "Prospective / Corrected Fault" to "%.2f A / %.2f A".format(earthing.prospectiveFault_A, earthing.correctedFault_A),
            "Earth Conductor (Exact → Std)" to "%.2f mm² → ${earthing.earthConductorStandard_mm2.toInt()} mm²".format(earthing.earthConductorExact_mm2),
            "Equipotential Bonding" to "${earthing.equipotentialBonding_mm2.toInt()} mm²",
            "Earth Loop Zs / Zs_max" to "%.4f Ω / %.4f Ω".format(earthing.zs_ohm, earthing.zsMax_ohm),
            "Loop Check" to earthing.loopCheck
        )

        val autoRes = earthing.autoDesignResult
        if (autoRes != null) {
            items.add("Auto-Design Mode" to "ENABLED")
            items.add("Soil Data Source" to autoRes.soilResistivitySource)
            items.add("Effective Target Resistance" to "%.2f Ω".format(autoRes.effectiveTargetResistance_ohm))
            items.add("Design Limit (Margin Guard)" to "%.2f Ω".format(autoRes.designLimitResistance_ohm))
            autoRes.recommended?.let { rec ->
                items.add("Recommended Layout" to rec.title)
            }
        }

        for ((label, value) in items) {
            renderer.ensureSpace(16f, true)
            renderer.drawText(label, labelX, currentY, renderer.theme.tableHeaderPaint)
            renderer.drawText(value, valX, currentY, renderer.theme.tableBodyPaint)
            currentY += 15f
        }

        if (autoRes != null && autoRes.soilResistivitySource.equals("ASSUMED", ignoreCase = true)) {
            currentY += 10f
            renderer.ensureSpace(20f, true)
            renderer.drawText("Preliminary design — soil resistivity source: ASSUMED (Verify with a Wenner 4-pin test).", labelX, currentY, renderer.theme.tableHeaderPaint)
            currentY += 15f
        }

        renderer.currentY = currentY
    }
}
