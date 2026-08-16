package com.example.ai.contracts

/**
 * Pure contract execution policy governing the authorization of ToolRequestEnvelope instances.
 * Enforces contract-level authorization; dispatcher-level enforcement will be implemented when tool dispatchers exist.
 */
object ToolRequestExecutionPolicy {
    fun validate(request: ToolRequestEnvelope<*>): ExecutionAuthorization {
        return when (request.executionMode) {
            ExecutionMode.READ_ONLY,
            ExecutionMode.LIVE_CALCULATION,
            ExecutionMode.WHAT_IF,
            ExecutionMode.VALIDATE_ONLY,
            ExecutionMode.PROPOSAL -> ExecutionAuthorization.Allowed

            ExecutionMode.MUTATION -> ExecutionAuthorization.Blocked(
                StructuredError(
                    errorCode = "MUTATION_NOT_AVAILABLE",
                    message = "Mutation requires explicit approval, a valid proposal, matching project revision, controlled mutation, and targeted revalidation. These capabilities are not implemented yet.",
                    entityType = request.entityType,
                    entityId = request.entityId,
                    isRecoverable = false,
                    remediationGuidance = "Use ExecutionMode.PROPOSAL to create a proposal for user review before attempting mutation in future phases."
                )
            )
        }
    }
}
