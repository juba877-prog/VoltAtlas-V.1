package com.example.ai.hybrid

import com.example.ai.runtime.BoundedProjectContext
import org.junit.Assert.*
import org.junit.Test

class ToolSchemaTranslatorTest {

    private val circuitContext = BoundedProjectContext(
        projectId = "PROJ-1",
        projectName = "Test Project",
        liveRevision = 1L,
        activeScreen = "CircuitsGridScreen",
        activeEntityType = "CIRCUIT",
        activeEntityId = "C-101",
        activeEntityName = "Feeder 101"
    )

    private val genericProjectContext = BoundedProjectContext(
        projectId = "PROJ-1",
        projectName = "Test Project",
        liveRevision = 1L,
        activeScreen = "ProjectDashboardScreen",
        activeEntityType = null,
        activeEntityId = null
    )

    // 1. Scoped tools: circuit context returns cable tools plus relevant read tools
    @Test
    fun cloudEngine_translatesOnlyScopedToolsNotFullRegistry() {
        val circuitTools = ToolSchemaTranslator.getScopedDeclarations(circuitContext)
        val circuitToolNames = circuitTools.map { it.name }

        assertTrue(circuitToolNames.contains("CalculateCableSizingTool"))
        assertTrue(circuitToolNames.contains("EvaluateCableCandidateTool"))
        assertTrue(circuitToolNames.contains("CompareCableCandidatesTool"))
        assertTrue(circuitToolNames.contains("RunCableWhatIfTool"))
        assertTrue(circuitToolNames.contains("GetEntityTool"))

        val genericTools = ToolSchemaTranslator.getScopedDeclarations(genericProjectContext)
        val genericToolNames = genericTools.map { it.name }

        assertTrue(genericToolNames.contains("GetCurrentProjectContextTool"))
        assertFalse("Generic context must not expose cable sizing calculation tools", genericToolNames.contains("CalculateCableSizingTool"))
    }

    // 2. Security invariant: zero mutation tools in schema
    @Test
    fun cloudEngine_neverIncludesMutationToolsInSchema() {
        val allDeclarations = ToolSchemaTranslator.getAllDeclarations()
        val forbiddenTerms = listOf("mutation", "approve", "approval", "grant", "receipt", "workflow", "gateway", "commit")

        for (decl in allDeclarations) {
            val lowerName = decl.name.lowercase()
            val lowerDesc = decl.description.lowercase()
            for (term in forbiddenTerms) {
                assertFalse(
                    "Tool '${decl.name}' must not contain forbidden term '$term'",
                    lowerName.contains(term) || lowerDesc.contains(term)
                )
            }
        }
    }
}
