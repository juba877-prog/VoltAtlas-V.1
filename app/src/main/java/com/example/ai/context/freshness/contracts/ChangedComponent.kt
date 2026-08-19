package com.example.ai.context.freshness.contracts

/**
 * Strongly typed components that can change and trigger calculation staleness.
 */
enum class ChangedComponent {
    DIRECT_INPUT,
    UPSTREAM_DEPENDENCY,
    CATALOG_DATA,
    METHODOLOGY,
    ENGINE_VERSION,
    PROJECT_CONTEXT,
    PROVENANCE
}
