package com.example.ai.hybrid

import com.example.ai.runtime.BoundedProjectContext

/**
 * Descriptor for a deterministic tool exposed to Cloud LLM function calling.
 */
data class CloudFunctionDeclaration(
    val name: String,
    val description: String,
    val parameters: Map<String, String>,
    val requiredParameters: List<String>
)

/**
 * Translates registered Phase 5 (Read) and Phase 6 (Cable) tools into scoped
 * function declarations for Gemini function calling.
 *
 * Invariants:
 * - Scoped Exposure: Only tools relevant to the active context are returned.
 * - Zero Mutation Tools: Mutation/approval tools are strictly forbidden from ever being translated.
 * - Zero Implementation Leakage: No Room entity names, SQL schemas, or internal package paths.
 */
object ToolSchemaTranslator {

    private val FORBIDDEN_MUTATION_KEYWORDS = setOf(
        "mutation", "approve", "approval", "grant", "receipt",
        "durable", "gateway", "workflow", "commit", "insert", "update", "delete"
    )

    private val ALL_READ_TOOL_DECLARATIONS = listOf(
        CloudFunctionDeclaration(
            name = "GetCurrentProjectContextTool",
            description = "Retrieves high-level summary and revision metadata of the active project.",
            parameters = mapOf("projectId" to "STRING"),
            requiredParameters = listOf("projectId")
        ),
        CloudFunctionDeclaration(
            name = "GetEntityTool",
            description = "Retrieves electrical attributes of a circuit, panel, or transformer node.",
            parameters = mapOf(
                "projectId" to "STRING",
                "entityType" to "STRING",
                "entityId" to "STRING"
            ),
            requiredParameters = listOf("projectId", "entityType", "entityId")
        ),
        CloudFunctionDeclaration(
            name = "GetCalculationStateTool",
            description = "Checks the calculation freshness and verification status for an electrical domain.",
            parameters = mapOf(
                "projectId" to "STRING",
                "domain" to "STRING"
            ),
            requiredParameters = listOf("projectId", "domain")
        ),
        CloudFunctionDeclaration(
            name = "GetDependenciesTool",
            description = "Retrieves downstream circuits and connected loads for an electrical node.",
            parameters = mapOf(
                "projectId" to "STRING",
                "entityId" to "STRING"
            ),
            requiredParameters = listOf("projectId", "entityId")
        ),
        CloudFunctionDeclaration(
            name = "GetChangeHistoryTool",
            description = "Retrieves the revision history and recent audit log for the project.",
            parameters = mapOf("projectId" to "STRING"),
            requiredParameters = listOf("projectId")
        ),
        CloudFunctionDeclaration(
            name = "GetCatalogItemTool",
            description = "Searches the equipment catalog for cables, breakers, or transformers.",
            parameters = mapOf(
                "catalogType" to "STRING",
                "query" to "STRING"
            ),
            requiredParameters = listOf("catalogType", "query")
        )
    )

    private val ALL_CABLE_TOOL_DECLARATIONS = listOf(
        CloudFunctionDeclaration(
            name = "CalculateCableSizingTool",
            description = "Runs deterministic cable sizing calculation for a specific circuit using standards.",
            parameters = mapOf(
                "projectId" to "STRING",
                "circuitId" to "STRING",
                "conductorMaterial" to "STRING_OPTIONAL",
                "overrideCableSize" to "STRING_OPTIONAL"
            ),
            requiredParameters = listOf("projectId", "circuitId")
        ),
        CloudFunctionDeclaration(
            name = "EvaluateCableCandidateTool",
            description = "Evaluates whether a specific candidate cable cross-section meets ampacity and voltage drop criteria.",
            parameters = mapOf(
                "projectId" to "STRING",
                "circuitId" to "STRING",
                "candidateSize" to "STRING"
            ),
            requiredParameters = listOf("projectId", "circuitId", "candidateSize")
        ),
        CloudFunctionDeclaration(
            name = "CompareCableCandidatesTool",
            description = "Compares multiple candidate cable cross-sections across ampacity, voltage drop, and thermal margin.",
            parameters = mapOf(
                "projectId" to "STRING",
                "circuitId" to "STRING",
                "candidateSizes" to "STRING_LIST"
            ),
            requiredParameters = listOf("projectId", "circuitId", "candidateSizes")
        ),
        CloudFunctionDeclaration(
            name = "RunCableWhatIfTool",
            description = "Simulates alternative parameters (e.g. ambient temperature, length, material) on cable sizing without mutating project.",
            parameters = mapOf(
                "projectId" to "STRING",
                "circuitId" to "STRING",
                "whatIfParameters" to "MAP_STRING_STRING"
            ),
            requiredParameters = listOf("projectId", "circuitId", "whatIfParameters")
        )
    )

    /**
     * Translates and filters tool declarations based on active [BoundedProjectContext].
     */
    fun getScopedDeclarations(context: BoundedProjectContext?): List<CloudFunctionDeclaration> {
        val isCircuitOrCableContext = context?.activeEntityType.equals("CIRCUIT", ignoreCase = true) ||
                context?.activeScreen?.contains("Circuit", ignoreCase = true) == true ||
                context?.activeScreen?.contains("Cable", ignoreCase = true) == true

        val scopedTools = if (isCircuitOrCableContext) {
            ALL_CABLE_TOOL_DECLARATIONS + ALL_READ_TOOL_DECLARATIONS.filter {
                it.name in setOf("GetEntityTool", "GetCalculationStateTool", "GetCatalogItemTool")
            }
        } else {
            ALL_READ_TOOL_DECLARATIONS
        }

        // Structural verification: ensure zero mutation keywords
        validateZeroMutationTools(scopedTools)

        return scopedTools
    }

    /**
     * Returns all registered non-mutating declarations.
     */
    fun getAllDeclarations(): List<CloudFunctionDeclaration> {
        val all = ALL_READ_TOOL_DECLARATIONS + ALL_CABLE_TOOL_DECLARATIONS
        validateZeroMutationTools(all)
        return all
    }

    private fun validateZeroMutationTools(declarations: List<CloudFunctionDeclaration>) {
        for (decl in declarations) {
            val lowerName = decl.name.lowercase()
            val lowerDesc = decl.description.lowercase()
            for (keyword in FORBIDDEN_MUTATION_KEYWORDS) {
                require(!lowerName.contains(keyword)) {
                    "Security violation: Tool declaration '${decl.name}' contains forbidden mutation keyword '$keyword'."
                }
            }
        }
    }
}
