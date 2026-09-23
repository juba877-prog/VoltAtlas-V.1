package com.example.ai.hybrid

import com.example.ai.runtime.BoundedProjectContext
import com.example.ai.tools.EngineeringToolDescriptor
import com.example.ai.tools.EngineeringToolParameter
import com.example.ai.tools.EngineeringToolParameterType
import com.example.ai.tools.EngineeringToolRegistry

/**
 * Provider-facing function declaration derived from an [EngineeringToolDescriptor].
 *
 * There is deliberately no hand-written declaration list anywhere: every instance of
 * this type is generated from the authoritative descriptor catalog.
 */
data class CloudFunctionDeclaration(
    val name: String,
    val description: String,
    val parameters: List<CloudFunctionParameter>,
    val requiredParameters: List<String>
) {
    /** Convenience view mapping parameter name to provider type token. */
    val parameterTypes: Map<String, String> get() = parameters.associate { it.name to it.type }
}

/**
 * Provider-facing parameter declaration. [type] is always a provider-interpretable
 * token produced by [ToolSchemaTranslator.providerTypeOf]; pseudo-types such as
 * "STRING_OPTIONAL" are impossible to express.
 */
data class CloudFunctionParameter(
    val name: String,
    val type: String,
    val description: String,
    val required: Boolean,
    val fields: List<CloudFunctionParameter> = emptyList()
)

/**
 * Generates provider function declarations from the single authoritative tool registry.
 *
 * Invariants:
 * - Registry-driven: declarations derive from [EngineeringToolDescriptor] entries, never
 *   from a duplicated list.
 * - Scoped exposure: only tools legitimately available for the active context are emitted.
 * - Zero mutation tools: any declaration whose name or description contains mutation-related
 *   vocabulary is a hard failure.
 * - Valid type tokens: optionality is expressed by [CloudFunctionParameter.required] rather
 *   than by an invented type token.
 */
object ToolSchemaTranslator {

    private val FORBIDDEN_MUTATION_KEYWORDS = setOf(
        "mutation", "approve", "approval", "grant", "receipt",
        "durable", "gateway", "workflow", "commit", "insert", "update", "delete"
    )

    /** All declarations for the supplied registry. */
    fun getAllDeclarations(registry: EngineeringToolRegistry): List<CloudFunctionDeclaration> =
        getAllDeclarations(registry.getAllDescriptors())

    /** All declarations for an explicit descriptor set (used by schema tests and tooling). */
    fun getAllDeclarations(descriptors: List<EngineeringToolDescriptor>): List<CloudFunctionDeclaration> {
        val declarations = descriptors.map { toDeclaration(it) }
        validateZeroMutationTools(declarations)
        return declarations
    }

    /** Declarations legitimately scoped to the active bounded context. */
    fun getScopedDeclarations(
        registry: EngineeringToolRegistry,
        context: BoundedProjectContext?
    ): List<CloudFunctionDeclaration> =
        getScopedDeclarations(registry.getAvailableDescriptors(context))

    /** Scoped declarations for an explicit descriptor set. */
    fun getScopedDeclarations(descriptors: List<EngineeringToolDescriptor>): List<CloudFunctionDeclaration> {
        val declarations = descriptors.map { toDeclaration(it) }
        validateZeroMutationTools(declarations)
        return declarations
    }

    /** Deterministic single-descriptor projection. */
    fun toDeclaration(descriptor: EngineeringToolDescriptor): CloudFunctionDeclaration =
        CloudFunctionDeclaration(
            name = descriptor.toolName,
            description = buildDescription(descriptor),
            parameters = descriptor.parameters.map { toParameter(it) },
            requiredParameters = descriptor.requiredParameters
        )

    /**
     * Maps a descriptor parameter type onto a provider-interpretable type token.
     * Optionality is never encoded here.
     */
    fun providerTypeOf(type: EngineeringToolParameterType): String = when (type) {
        EngineeringToolParameterType.STRING -> "STRING"
        EngineeringToolParameterType.INTEGER -> "INTEGER"
        EngineeringToolParameterType.NUMBER -> "NUMBER"
        EngineeringToolParameterType.BOOLEAN -> "BOOLEAN"
        EngineeringToolParameterType.STRING_LIST -> "ARRAY<STRING>"
        EngineeringToolParameterType.STRING_MAP -> "OBJECT"
        EngineeringToolParameterType.OBJECT -> "OBJECT"
        EngineeringToolParameterType.OBJECT_LIST -> "ARRAY<OBJECT>"
    }

    private fun toParameter(parameter: EngineeringToolParameter): CloudFunctionParameter =
        CloudFunctionParameter(
            name = parameter.name,
            type = providerTypeOf(parameter.type),
            description = parameter.description,
            required = parameter.required,
            fields = parameter.fields.map {
                CloudFunctionParameter(
                    name = it.name,
                    type = providerTypeOf(it.type),
                    description = it.description,
                    required = it.required
                )
            }
        )

    private fun buildDescription(descriptor: EngineeringToolDescriptor): String = buildString {
        append("PURPOSE: ").append(descriptor.purpose).append('\n')
        append("WHEN TO USE: ").append(descriptor.whenToUse).append('\n')
        append("WHEN NOT TO USE: ").append(descriptor.whenNotToUse).append('\n')
        append("REQUIRED INPUTS: ").append(
            descriptor.requiredParameters.joinToString(", ").ifBlank { "none" }
        ).append('\n')
        append("OUTPUT MEANING: ").append(descriptor.outputMeaning).append('\n')
        append("SIDE EFFECTS: ").append(descriptor.sideEffects).append('\n')
        append("FRESHNESS RULE: ").append(descriptor.requiredFreshness.name).append('\n')
        append("PROJECT SCOPE: ").append(
            if (descriptor.requiresProjectContext) "authoritative project context is required"
            else "project context is not required"
        ).append('\n')
        append("ENTITY SCOPE: ").append(
            if (descriptor.requiresEntityContext) "an authorized entity binding is required"
            else "no entity binding is required"
        ).append('\n')
        append("PROVENANCE: ").append(descriptor.provenanceRule).append('\n')
        append("AUTHORIZATION LEVEL: ").append(descriptor.authorizationLevel.name).append('\n')
        append("EXECUTION MODE: ").append(descriptor.executionMode.name)
    }

    private fun validateZeroMutationTools(declarations: List<CloudFunctionDeclaration>) {
        for (declaration in declarations) {
            val haystacks = listOf(declaration.name.lowercase(), declaration.description.lowercase())
            for (keyword in FORBIDDEN_MUTATION_KEYWORDS) {
                require(haystacks.none { it.contains(keyword) }) {
                    "Security violation: tool declaration '${declaration.name}' contains " +
                        "forbidden mutation keyword '$keyword'."
                }
            }
        }
    }
}
