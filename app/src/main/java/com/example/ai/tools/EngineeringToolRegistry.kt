package com.example.ai.tools

import com.example.ai.contracts.ExecutionMode
import com.example.ai.contracts.ExecutionModePolicy
import com.example.ai.runtime.BoundedProjectContext
import com.example.ai.tools.cable.DeterministicCableTool
import com.example.ai.tools.cable.DeterministicCableToolRegistry
import com.example.ai.tools.read.DeterministicReadTool
import com.example.ai.tools.read.DeterministicReadToolRegistry

/**
 * The single authoritative tool registry surface for the assistant.
 *
 * 1. Owns the descriptor catalog (the only declaration surface for tool schemas).
 * 2. Exposes the executable typed tools held by the read and cable registries.
 * 3. Proves at construction time that those two surfaces cannot drift apart.
 *
 * Invariant (enforced in [init]): the set of declared descriptors is exactly equal
 * to the set of executable typed tools. A declared-but-unimplemented tool, or an
 * implemented-but-undeclared tool, is a construction failure rather than a silent
 * runtime surprise. Schemas are generated from this registry and execution is
 * dispatched through it, so there is one declaration list and one execution list.
 */
class EngineeringToolRegistry(
    private val readRegistry: DeterministicReadToolRegistry,
    private val cableRegistry: DeterministicCableToolRegistry,
    descriptors: List<EngineeringToolDescriptor> =
        EngineeringToolCatalog.READ_TOOLS + EngineeringToolCatalog.CABLE_TOOLS
) {

    private val descriptorsByName: Map<String, EngineeringToolDescriptor> =
        descriptors.associateBy { it.toolName }

    init {
        require(descriptorsByName.size == descriptors.size) {
            "Duplicate tool names declared in the descriptor catalog: " +
                descriptors.groupingBy { it.toolName }.eachCount().filterValues { it > 1 }.keys
        }
        validateDescriptorToExecutableBinding()
    }

    // --- Descriptor surface -------------------------------------------------

    fun getDescriptor(toolName: String): EngineeringToolDescriptor? = descriptorsByName[toolName]

    fun getAllDescriptors(): List<EngineeringToolDescriptor> = descriptorsByName.values.toList()

    /** Alias for call sites that reason in terms of available capability sets. */
    fun getAvailableTools(context: BoundedProjectContext?): List<EngineeringToolDescriptor> =
        getAvailableDescriptors(context)

    fun getAllToolNames(): Set<String> = descriptorsByName.keys

    // --- Executable surface -------------------------------------------------

    /** Tool names that actually have a typed implementation registered. */
    fun getExecutableToolNames(): Set<String> =
        readRegistry.getAllToolNames() + cableRegistry.getAllToolNames()

    fun isRegistered(toolName: String): Boolean =
        descriptorsByName.containsKey(toolName) && getExecutableToolNames().contains(toolName)

    @Suppress("UNCHECKED_CAST")
    fun <TReq, TRes> getReadTool(toolName: String): DeterministicReadTool<TReq, TRes>? =
        readRegistry.getTool(toolName)

    @Suppress("UNCHECKED_CAST")
    fun <TReq, TRes> getCableTool(toolName: String): DeterministicCableTool<TReq, TRes>? =
        cableRegistry.getTool(toolName)

    fun isReadTool(toolName: String): Boolean = readRegistry.containsTool(toolName)

    fun isCableTool(toolName: String): Boolean = cableRegistry.getAllToolNames().contains(toolName)

    // --- Authorization & scoping --------------------------------------------

    /**
     * True only when [toolName] is registered, declares [executionMode] as its own
     * mode, and that mode is currently executable. Mutation is rejected by
     * construction and again here as defence in depth.
     */
    fun isAllowed(toolName: String, executionMode: ExecutionMode): Boolean {
        val descriptor = descriptorsByName[toolName] ?: return false
        if (descriptor.isMutationAllowed) return false
        if (!ExecutionModePolicy.isCurrentlyExecutable(executionMode)) return false
        return descriptor.executionMode == executionMode
    }

    /** Authorization check against the tool's own declared execution mode. */
    fun isAllowed(toolName: String): Boolean {
        val descriptor = descriptorsByName[toolName] ?: return false
        return isAllowed(toolName, descriptor.executionMode)
    }

    /**
     * Tools legitimately exposable for the current bounded context.
     *
     * Read tools are always available; domain tools are exposed only when the active
     * context is genuinely scoped to that domain, so a generic project view can never
     * advertise a circuit calculation tool.
     */
    fun getAvailableDescriptors(context: BoundedProjectContext?): List<EngineeringToolDescriptor> =
        scopeTo(getAllDescriptors(), context)

    // --- Internal -----------------------------------------------------------

    private fun validateDescriptorToExecutableBinding() {
        val declared = descriptorsByName.keys
        val executable = getExecutableToolNames()

        val missingImplementation = declared - executable
        require(missingImplementation.isEmpty()) {
            "Descriptor catalog declares tools that have no executable implementation: $missingImplementation"
        }

        val missingDescriptor = executable - declared
        require(missingDescriptor.isEmpty()) {
            "Executable tools have no authoritative descriptor (schema/execution drift): $missingDescriptor"
        }
    }

    companion object {
        /**
         * Pure context-scoping rule, shared by the registry instance and by schema-only
         * callers that hold descriptors but no live database.
         *
         * Read tools are always available; domain tools are exposed only when the active
         * context is genuinely scoped to that domain, so a generic project view can never
         * advertise a circuit calculation tool.
         */
        fun scopeTo(
            descriptors: List<EngineeringToolDescriptor>,
            context: BoundedProjectContext?
        ): List<EngineeringToolDescriptor> {
            val isCircuitScope = context?.activeEntityType.equals("CIRCUIT", ignoreCase = true) ||
                context?.activeScreen?.contains("Circuit", ignoreCase = true) == true ||
                context?.activeScreen?.contains("Cable", ignoreCase = true) == true

            return descriptors
                .filter { it.domain == EngineeringToolDomain.READ || isCircuitScope }
                .filter { !it.requiresProjectContext || !context?.projectId.isNullOrBlank() }
        }
    }
}
