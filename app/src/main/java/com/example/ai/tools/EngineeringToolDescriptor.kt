package com.example.ai.tools

import com.example.ai.contracts.ExecutionMode
import com.example.ai.runtime.PromptPermissionMode

/**
 * Engineering domain that owns a deterministic tool.
 */
enum class EngineeringToolDomain {
    READ,
    CABLE
}

/**
 * Closed parameter type vocabulary for model-facing function schemas.
 *
 * Deliberately small: every token here is directly translatable into a provider
 * function-declaration type. Ad-hoc tokens (e.g. "STRING_OPTIONAL",
 * "MAP_STRING_STRING") previously used as pseudo-types are explicitly rejected
 * because no provider can interpret them.
 *
 * Optionality is expressed by [EngineeringToolParameter.required], never by the type.
 */
enum class EngineeringToolParameterType {
    STRING,
    INTEGER,
    NUMBER,
    BOOLEAN,
    STRING_LIST,
    STRING_MAP,

    /** A single structured object argument. */
    OBJECT,

    /** A list of structured object arguments. */
    OBJECT_LIST
}

/**
 * Structured field declaration for an [EngineeringToolParameterType.OBJECT] or
 * [EngineeringToolParameterType.OBJECT_LIST] parameter.
 */
data class EngineeringToolField(
    val name: String,
    val type: EngineeringToolParameterType,
    val required: Boolean,
    val description: String
)

/**
 * Typed declaration of a single tool parameter.
 */
data class EngineeringToolParameter(
    val name: String,
    val type: EngineeringToolParameterType,
    val required: Boolean,
    val description: String,
    /** Declared shape for OBJECT / OBJECT_LIST parameters; empty for scalar types. */
    val fields: List<EngineeringToolField> = emptyList()
) {
    init {
        require(name.isNotBlank()) { "Parameter name must not be blank." }
        require(name.matches(CONTRACT_FIELD_REGEX)) {
            "Parameter name '$name' must match the executable request contract field name."
        }
        val needsFields = type == EngineeringToolParameterType.OBJECT ||
            type == EngineeringToolParameterType.OBJECT_LIST
        require(needsFields == fields.isNotEmpty()) {
            "Parameter '$name' of type $type must declare fields if and only if it is a structured object type."
        }
        fields.forEach {
            require(it.name.matches(CONTRACT_FIELD_REGEX)) {
                "Field name '${it.name}' in parameter '$name' must match the executable request contract field name."
            }
        }
    }

    private companion object {
        /**
         * Parameter and field names must equal the executable request-DTO field name so that
         * model arguments map mechanically onto typed requests. Tool *names* remain strictly
         * snake_case (see [EngineeringToolDescriptor]); parameter identifiers follow Kotlin
         * property naming and are therefore allowed to be camelCase.
         */
        val CONTRACT_FIELD_REGEX = Regex("^[a-z][A-Za-z0-9_]*$")
    }
}

/**
 * Freshness obligation a tool places on the project revision it reports against.
 *
 * Drives the runtime rule: a cached result may only be presented as current
 * engineering truth when the underlying calculation state is CURRENT for the
 * live project revision.
 */
enum class ToolFreshnessRequirement {
    /** Tool does not depend on calculated engineering results. */
    NONE,

    /** Tool must only report results that are CURRENT for the live project revision. */
    MUST_BE_CURRENT,

    /** Tool may return STALE results, but they must be labelled as stale. */
    LABEL_IF_STALE
}

/**
 * Authoritative, single-source descriptor for a deterministic engineering tool.
 *
 * Every descriptor that is registered must resolve to an executable typed tool,
 * and every executable typed tool must have exactly one descriptor. This binding
 * is enforced at registry construction so that the model-facing schema and the
 * executable registry can never drift apart.
 *
 * Safety invariants (validated in [init]):
 * 1. Names are canonical snake_case, matching the executable registry keys.
 * 2. Mutation is never permitted: [isMutationAllowed] is always false and
 *    [executionMode] can never be [ExecutionMode.MUTATION].
 * 3. Parallel execution is only declared for genuinely read-only tools.
 */
data class EngineeringToolDescriptor(
    /** Canonical snake_case tool name; must equal the executable tool's registry key. */
    val toolName: String,
    val domain: EngineeringToolDomain,
    /** PURPOSE: what the tool does. */
    val purpose: String,
    /** WHEN TO USE. */
    val whenToUse: String,
    /** WHEN NOT TO USE — the primary hallucination guard. */
    val whenNotToUse: String,
    /** OUTPUT MEANING: how the returned payload should be interpreted. */
    val outputMeaning: String,
    /** SIDE EFFECTS: must state read-only / non-mutating explicitly. */
    val sideEffects: String,
    /** AUTHORIZATION LEVEL: permission mode required to execute. */
    val authorizationLevel: PromptPermissionMode,
    /** PROJECT SCOPE: whether an authoritative project context is mandatory. */
    val requiresProjectContext: Boolean,
    /** ENTITY SCOPE: whether an authoritative entity binding is mandatory. */
    val requiresEntityContext: Boolean,
    /** FRESHNESS RULE. */
    val requiredFreshness: ToolFreshnessRequirement,
    /** Provenance rule describing the source of truth for returned values. */
    val provenanceRule: String,
    val executionMode: ExecutionMode,
    val parameters: List<EngineeringToolParameter>,
    /** True only when the tool is read-only, dependency-free and targets one authorized project. */
    val supportsParallelExecution: Boolean = false
) {
    /** Declares a mutation capability. Always false by construction; kept explicit for audit. */
    val isMutationAllowed: Boolean = false

    val permission: PromptPermissionMode get() = authorizationLevel

    val requiredParameters: List<String>
        get() = parameters.filter { it.required }.map { it.name }

    init {
        require(toolName.isNotBlank()) { "Tool name must not be blank." }
        require(toolName.matches(CANONICAL_NAME_REGEX)) {
            "Tool name '$toolName' must be canonical snake_case and match the executable registry key."
        }
        require(!isMutationAllowed) {
            "Tool '$toolName' declares a mutation capability. Mutation tools must never be registered."
        }
        require(executionMode != ExecutionMode.MUTATION) {
            "Tool '$toolName' declares ExecutionMode.MUTATION. Mutation is not an executable tool mode."
        }
        require(!supportsParallelExecution || executionMode == ExecutionMode.READ_ONLY) {
            "Tool '$toolName' declares parallel execution but is not READ_ONLY."
        }
        val duplicates = parameters.groupingBy { it.name }.eachCount().filterValues { it > 1 }.keys
        require(duplicates.isEmpty()) {
            "Tool '$toolName' declares duplicate parameters: $duplicates"
        }
    }

    private companion object {
        val CANONICAL_NAME_REGEX = Regex("^[a-z][a-z0-9_]*$")
    }
}
