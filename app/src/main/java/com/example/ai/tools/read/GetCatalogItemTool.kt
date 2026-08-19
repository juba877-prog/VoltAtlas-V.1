package com.example.ai.tools.read

import com.example.ai.contracts.ProvenanceMetadata
import com.example.ai.contracts.StructuredError
import com.example.ai.contracts.ToolRequestEnvelope
import com.example.ai.contracts.ToolResultEnvelope
import com.example.data.catalog.CatalogRepository

/**
 * Deterministic read tool to query engineering catalog databases for cables, breakers,
 * transformers, accessories, and busways using authoritative catalog repositories.
 */
class GetCatalogItemTool(
    private val catalogRepository: CatalogRepository
) : DeterministicReadTool<GetCatalogItemRequest, CatalogSearchResult> {

    override val toolName: String = ReadToolNames.GET_CATALOG_ITEM
    override val description: String = "Queries verified engineering catalog databases for cables, breakers, transformers, and accessories."

    override suspend fun execute(
        request: ToolRequestEnvelope<GetCatalogItemRequest>
    ): ToolResultEnvelope<CatalogSearchResult> {
        val started = System.currentTimeMillis()
        val params = request.parameters
        val category = params.category.trim().uppercase()
        val limit = (params.limit.takeIf { it in 1..50 } ?: 20)
        val query = params.query?.trim()?.lowercase()
        val mfgFilter = params.manufacturer?.trim()?.lowercase()

        val matchingItems = mutableListOf<CatalogItemDto>()
        val provenanceSource: String

        when (category) {
            "CABLE", "CABLES" -> {
                provenanceSource = "CatalogRepository.cableProducts"
                val products = catalogRepository.cableProducts.filter { p ->
                    val matchesMfg = mfgFilter == null || p.mfg.lowercase().contains(mfgFilter)
                    val matchesSize = params.sizeMm2 == null || Math.abs(p.size - params.sizeMm2) < 0.001
                    val matchesQuery = query == null || p.model.lowercase().contains(query) || p.mfg.lowercase().contains(query)
                    matchesMfg && matchesSize && matchesQuery
                }
                products.take(limit).forEach { p ->
                    matchingItems.add(
                        CatalogItemDto(
                            category = "CABLE",
                            identifier = "${p.mfg}-${p.typeCode}-${p.size}mm2",
                            manufacturer = p.mfg,
                            modelOrSeries = p.model,
                            primaryRating = "${p.size} mm² (Air: ${p.air}A, Duct: ${p.duct}A, Gnd: ${p.gnd}A)",
                            details = mapOf(
                                "size_mm2" to p.size.toString(),
                                "od_mm" to p.od.toString(),
                                "weight_kg_km" to p.wt.toString()
                            ),
                            provenance = p.provenance
                        )
                    )
                }
            }
            "BREAKER", "BREAKERS" -> {
                provenanceSource = "CatalogRepository.breakers"
                val breakers = catalogRepository.breakers.filter { b ->
                    val matchesMfg = mfgFilter == null || b.mfg.lowercase().contains(mfgFilter)
                    val matchesRating = params.ratingA == null || Math.abs(b.ratingA - params.ratingA) < 0.001
                    val matchesQuery = query == null || b.series.lowercase().contains(query) ||
                            b.frame.lowercase().contains(query) || b.mfg.lowercase().contains(query)
                    matchesMfg && matchesRating && matchesQuery
                }
                breakers.take(limit).forEach { b ->
                    matchingItems.add(
                        CatalogItemDto(
                            category = "BREAKER",
                            identifier = b.orderingCode.ifBlank { "${b.mfg}-${b.series}-${b.ratingA}A" },
                            manufacturer = b.mfg,
                            modelOrSeries = "${b.series} / ${b.frame}",
                            primaryRating = "${b.ratingA} A (${b.icuKa} kA Icu)",
                            details = mapOf(
                                "poles" to b.poles.toString(),
                                "icu_ka" to b.icuKa.toString(),
                                "ics_ka" to b.icsKa.toString(),
                                "tripUnit" to b.tripUnit
                            ),
                            provenance = b.provenance
                        )
                    )
                }
            }
            "TRANSFORMER", "TRANSFORMERS" -> {
                provenanceSource = "CatalogRepository.transformerSeeds"
                val seeds = catalogRepository.transformerSeeds.filter { tx ->
                    val matchesSize = params.ratingA == null || Math.abs(tx.kVA - params.ratingA) < 0.001
                    val matchesQuery = query == null || tx.transformerId.lowercase().contains(query)
                    matchesSize && matchesQuery
                }
                seeds.take(limit).forEach { tx ->
                    matchingItems.add(
                        CatalogItemDto(
                            category = "TRANSFORMER",
                            identifier = tx.transformerId,
                            manufacturer = "Standard",
                            modelOrSeries = "Standard Distribution Transformer",
                            primaryRating = "${tx.kVA} kVA (${tx.primaryVoltageV}V / ${tx.secondaryVoltageV}V)",
                            details = mapOf(
                                "percentZ" to tx.percentZ.toString(),
                                "xrRatio" to tx.xrRatio.toString(),
                                "iscKA" to tx.iscKA.toString()
                            ),
                            provenance = tx.provenance
                        )
                    )
                }
            }
            "ACCESSORY", "ACCESSORIES" -> {
                provenanceSource = "CatalogRepository.accessories"
                val accessories = catalogRepository.accessories.filter { a ->
                    val matchesMfg = mfgFilter == null || a.mfg.lowercase().contains(mfgFilter)
                    val matchesQuery = query == null || a.partNumber.lowercase().contains(query) ||
                            a.description.lowercase().contains(query) || a.category.lowercase().contains(query)
                    matchesMfg && matchesQuery
                }
                accessories.take(limit).forEach { a ->
                    matchingItems.add(
                        CatalogItemDto(
                            category = "ACCESSORY",
                            identifier = a.partNumber,
                            manufacturer = a.mfg,
                            modelOrSeries = "${a.category} / ${a.subCategory}",
                            primaryRating = a.description,
                            details = mapOf(
                                "sizeMin" to a.sizeMin.toString(),
                                "sizeMax" to a.sizeMax.toString()
                            ),
                            provenance = a.provenance
                        )
                    )
                }
            }
            "BUSWAY", "BUSWAYS" -> {
                provenanceSource = "CatalogRepository.buswayEntries"
                val busways = catalogRepository.buswayEntries.filter { b ->
                    val matchesMfg = mfgFilter == null || b.mfg.lowercase().contains(mfgFilter)
                    val matchesRating = params.ratingA == null || Math.abs(b.ratingA - params.ratingA) < 0.001
                    val matchesQuery = query == null || b.model.lowercase().contains(query) || b.mfg.lowercase().contains(query)
                    matchesMfg && matchesRating && matchesQuery
                }
                busways.take(limit).forEach { b ->
                    matchingItems.add(
                        CatalogItemDto(
                            category = "BUSWAY",
                            identifier = "${b.mfg}-${b.model}-${b.ratingA}A",
                            manufacturer = b.mfg,
                            modelOrSeries = b.model,
                            primaryRating = "${b.ratingA} A",
                            details = mapOf(
                                "conductor" to b.conductor,
                                "weightKgPerM" to b.weightKgPerM.toString()
                            ),
                            provenance = "BUSWAY_CATALOG_JSON"
                        )
                    )
                }
            }
            else -> {
                return ToolResultEnvelope.invalidInput(
                    requestId = request.requestId,
                    toolName = toolName,
                    projectId = request.projectId,
                    errors = listOf(
                        StructuredError(
                            errorCode = "UNSUPPORTED_CATALOG_CATEGORY",
                            message = "Catalog category '$category' is unsupported. Supported: CABLE, BREAKER, TRANSFORMER, ACCESSORY, BUSWAY.",
                            isRecoverable = false
                        )
                    ),
                    startedAt = started
                )
            }
        }

        val result = CatalogSearchResult(
            category = category,
            matchCount = matchingItems.size,
            items = matchingItems,
            isTruncated = matchingItems.size >= limit,
            provenance = provenanceSource
        )

        return ToolResultEnvelope.pass(
            requestId = request.requestId,
            toolName = toolName,
            projectId = request.projectId,
            result = result,
            provenance = ProvenanceMetadata(
                engineName = "CATALOG_REPOSITORY",
                engineVersion = "1.0",
                projectId = request.projectId,
                calculatedAt = System.currentTimeMillis()
            ),
            startedAt = started
        )
    }
}
