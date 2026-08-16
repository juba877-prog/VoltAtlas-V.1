package com.example.data

enum class ImportStatus {
    SUCCESS,
    SUCCESS_WITH_WARNINGS,
    FAILED
}

enum class ValidationStatus {
    VALID,
    WARNING,
    INVALID,
    NOT_EVALUATED
}

enum class MigrationFieldClassification {
    RESTORED,
    MIGRATED,
    DEFAULTED_BY_SCHEMA,
    UNAVAILABLE_FROM_V1,
    INVALID,
    ERROR
}

data class MigrationNotice(
    val entityType: String,
    val entityIdentifier: String,
    val fieldName: String,
    val classification: MigrationFieldClassification,
    val message: String
)

data class ImportCounts(
    val circuits: Int = 0,
    val transformers: Int = 0,
    val generators: Int = 0,
    val ats: Int = 0,
    val ups: Int = 0,
    val pfGlobal: Int = 0,
    val pfTransformers: Int = 0,
    val conduitSettings: Int = 0,
    val earthingSettings: Int = 0,
    val sldNodePositions: Int = 0
)

data class ProjectImportResult(
    val projectId: String?,
    val status: ImportStatus,
    val schemaVersion: Int,
    val importedCounts: ImportCounts,
    val notices: List<MigrationNotice> = emptyList(),
    val warnings: List<String> = emptyList(),
    val errors: List<String> = emptyList(),
    val hierarchyStatus: ValidationStatus = ValidationStatus.NOT_EVALUATED,
    val sldStatus: ValidationStatus = ValidationStatus.NOT_EVALUATED,
    val engineeringStatus: ValidationStatus = ValidationStatus.NOT_EVALUATED
)
