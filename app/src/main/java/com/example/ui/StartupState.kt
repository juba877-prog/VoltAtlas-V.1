package com.example.ui

sealed interface StartupState {
    object Initial : StartupState
    object OpeningDatabase : StartupState
    object LoadingProjects : StartupState
    object ValidatingCableCatalog : StartupState
    object ValidatingAccessoryCatalog : StartupState
    object ValidatingBreakerCatalog : StartupState
    object ValidatingConduitCatalog : StartupState
    object RestoringApplicationState : StartupState
    
    data class ReadyWithWarnings(val warnings: List<String>) : StartupState
    object Ready : StartupState
    object NoProjects : StartupState
    data class DatabaseError(val message: String, val details: String? = null) : StartupState
    data class CatalogError(val message: String) : StartupState
    data class FatalError(val message: String) : StartupState
}
