package com.example.ai.contracts

import com.squareup.moshi.JsonClass

/**
 * Typed evaluation outcome governing tool execution authorization.
 */
sealed interface ExecutionAuthorization {
    data object Allowed : ExecutionAuthorization

    @JsonClass(generateAdapter = true)
    data class Blocked(
        val error: StructuredError
    ) : ExecutionAuthorization
}
