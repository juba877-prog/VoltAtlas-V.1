package com.example.ai.tools

import com.example.ai.context.freshness.adapters.ProjectFreshnessDataProvider
import com.example.ai.context.freshness.contracts.CalculationFreshnessProvider
import com.example.ai.context.freshness.internal.DefaultCalculationFreshnessProvider
import com.example.ai.context.provider.ProjectContextProviderImpl
import com.example.ai.task.DefaultEngineeringTaskManager
import com.example.ai.task.EngineeringTaskManager
import com.example.ai.tools.cable.DeterministicCableToolInvoker
import com.example.ai.tools.cable.DeterministicCableToolRegistry
import com.example.ai.tools.read.DeterministicReadToolInvoker
import com.example.ai.tools.read.DeterministicReadToolRegistry
import com.example.data.CircuitEntity
import com.example.data.EngineeringRepository
import com.example.data.TransformerEntity
import com.example.data.catalog.CatalogRepository
import com.example.engine.coordinator.EngineeringCalculationCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Production composition root for the deterministic engineering tool layer.
 *
 * This is the wiring that connects the previously unreachable typed tool registries and
 * invokers to the running application. Before this existed, every tool and invoker had
 * test-only callers: the assistant could describe tools but never execute one.
 *
 * Built once per process and shared. All members are safe for concurrent use.
 */
class AssistantToolRuntime(
    /** Authoritative descriptor + authorization surface used to generate model schemas. */
    val descriptorRegistry: EngineeringToolRegistry,
    /** Execution dispatch facade over the same two typed registries. */
    val unifiedRegistry: UnifiedDeterministicToolRegistry,
    /** Turns an authorized request into a real typed execution. */
    val dispatcher: EngineeringToolDispatcher,
    val taskManager: EngineeringTaskManager
) {

    /** Tool names that are simultaneously declared and executable. */
    val executableToolNames: Set<String> get() = descriptorRegistry.getExecutableToolNames()

    companion object {
        /**
         * Builds the full deterministic tool graph for the running application.
         *
         * Dependency order mirrors the verified test-side wiring pattern so production and
         * tests exercise identical composition.
         */
        fun create(
            repository: EngineeringRepository,
            catalogRepository: CatalogRepository,
            coordinator: EngineeringCalculationCoordinator = EngineeringCalculationCoordinator,
            scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        ): AssistantToolRuntime {
            coordinator.projectRevisionProvider = repository

            val freshnessDataProvider = object : ProjectFreshnessDataProvider {
                override suspend fun getCircuits(projectId: String): List<CircuitEntity> =
                    repository.circuitDao.getCircuitsListForProject(projectId)

                override suspend fun getTransformers(projectId: String): List<TransformerEntity> =
                    repository.transformerDao.getTransformersListForProject(projectId)
            }

            val freshnessProvider: CalculationFreshnessProvider = DefaultCalculationFreshnessProvider(
                revisionProvider = repository,
                metadataRegistry = coordinator.freshnessRegistry,
                projectDataProvider = freshnessDataProvider
            )

            val projectContextProvider = ProjectContextProviderImpl(
                repository = repository,
                coordinator = coordinator,
                freshnessProvider = freshnessProvider,
                revisionProvider = repository
            )

            val readRegistry = DeterministicReadToolRegistry.createDefault(
                projectContextProvider = projectContextProvider,
                freshnessProvider = freshnessProvider,
                revisionProvider = repository,
                catalogRepository = catalogRepository
            )

            val cableRegistry = DeterministicCableToolRegistry.createDefault(
                repository = repository,
                revisionProvider = repository,
                freshnessProvider = freshnessProvider,
                coordinator = coordinator
            )

            // One descriptor surface and one dispatch surface, both bound to the same
            // two typed registries. Construction fails if they ever disagree.
            val descriptorRegistry = EngineeringToolRegistry(readRegistry, cableRegistry)

            return AssistantToolRuntime(
                descriptorRegistry = descriptorRegistry,
                unifiedRegistry = UnifiedDeterministicToolRegistry(readRegistry, cableRegistry),
                dispatcher = EngineeringToolDispatcher(
                    registry = descriptorRegistry,
                    readInvoker = DeterministicReadToolInvoker(readRegistry),
                    cableInvoker = DeterministicCableToolInvoker(cableRegistry),
                    revisionProvider = repository
                ),
                taskManager = DefaultEngineeringTaskManager(coroutineScope = scope)
            )
        }
    }
}
