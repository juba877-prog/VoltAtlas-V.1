package com.example.ai.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.ai.approval.DefaultTrustedPrincipalProvider
import com.example.ai.approval.TrustedProposalApprovalGateway
import com.example.ai.context.revision.ProjectRevisionProvider
import com.example.ai.hybrid.ConversationEngine
import com.example.ai.hybrid.ConversationEngineProvider
import com.example.ai.hybrid.DefaultSecureCredentialStorage
import com.example.ai.hybrid.SecureCredentialStorage
import com.example.ai.hybrid.UserAiPreferences
import com.example.ai.proposal.DurableMutationWorkflow
import com.example.ai.tools.AssistantToolRuntime
import com.example.data.AppDatabase
import com.example.data.Catalog
import com.example.data.EngineeringRepository
import com.example.data.GlobalPreferences
import com.example.data.catalog.CatalogRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Factory for creating [EngineeringAssistantViewModel] with dependencies resolved from context.
 */
class EngineeringAssistantViewModelFactory(
    private val conversationEngineProvider: () -> ConversationEngine,
    private val approvalGatewayProvider: suspend () -> TrustedProposalApprovalGateway,
    private val proposalCoordinatorProvider: () -> CableProposalPreparationCoordinator,
    private val projectRevisionProvider: ProjectRevisionProvider? = null,
    private val toolRuntimeProvider: (suspend () -> AssistantToolRuntime?)? = null,
    private val credentialStorage: SecureCredentialStorage? = null,
    private val userAiPreferences: UserAiPreferences? = null
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(EngineeringAssistantViewModel::class.java)) {
            return EngineeringAssistantViewModel(
                credentialStorage = credentialStorage,
                userAiPreferences = userAiPreferences,
                conversationEngineProvider = conversationEngineProvider,
                asyncApprovalGatewayProvider = approvalGatewayProvider,
                proposalPreparationCoordinator = proposalCoordinatorProvider(),
                projectRevisionProvider = projectRevisionProvider,
                toolRuntimeProvider = toolRuntimeProvider
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }

    companion object {
        fun production(application: Application): EngineeringAssistantViewModelFactory = create(application)

        fun create(application: Application): EngineeringAssistantViewModelFactory {
            val database = AppDatabase.getDatabase(application)
            val preferences = GlobalPreferences.getInstance(application)
            val principalProvider: () -> String = {
                preferences.trustedEngineerIdentity.value
            }
            // Single authoritative repository: also serves as the ProjectRevisionProvider so
            // assistant turns resolve a real revision instead of a fabricated one.
            val repository = EngineeringRepository(database)
            val proposalCoordinator = CableProposalPreparationCoordinator(
                projectDao = database.projectDao(),
                circuitDao = database.circuitDao()
            )
            // Asynchronous gateway provider: asset JSON files are parsed on Dispatchers.IO
            // inside viewModelScope without blocking the calling thread.
            val asyncGatewayProvider: suspend () -> TrustedProposalApprovalGateway = {
                val catalogRepository = productionCatalogRepository(application)
                DurableMutationWorkflow.createGateway(
                    database = database,
                    catalogRepository = catalogRepository,
                    principalProvider = DefaultTrustedPrincipalProvider(principalProvider)
                )
            }
            // Built once, on first tool-backed turn, on an IO dispatcher. Catalog JSON parsing
            // must never run on the main thread.
            var cachedToolRuntime: AssistantToolRuntime? = null
            val toolRuntimeProvider: suspend () -> AssistantToolRuntime? = {
                cachedToolRuntime ?: productionCatalogRepository(application).let { catalogRepository ->
                    AssistantToolRuntime.create(
                        repository = repository,
                        catalogRepository = catalogRepository
                    ).also { cachedToolRuntime = it }
                }
            }
            val credentialStorage = DefaultSecureCredentialStorage.getInstance(application)
            val userAiPreferences = UserAiPreferences(application)
            com.example.ai.hybrid.CloudLlmFeatureGate.setCredentialSupplier { credentialStorage.getCredential() }
            return EngineeringAssistantViewModelFactory(
                credentialStorage = credentialStorage,
                userAiPreferences = userAiPreferences,
                conversationEngineProvider = {
                    ConversationEngineProvider.provideConversationEngine(
                        context = application,
                        credentialStorage = credentialStorage,
                        toolRuntimeProvider = toolRuntimeProvider,
                        aiProvider = userAiPreferences.getSelectedProvider()
                    )
                },
                approvalGatewayProvider = asyncGatewayProvider,
                proposalCoordinatorProvider = { proposalCoordinator },
                projectRevisionProvider = repository,
                toolRuntimeProvider = toolRuntimeProvider
            )
        }

        private suspend fun productionCatalogRepository(application: Application): CatalogRepository =
            withContext(Dispatchers.IO) {
                CatalogRepository().apply {
                    loadAllFromStrings(
                        cableCatalogJson = Catalog.readAsset(application, "cable_catalog.json"),
                        cableTypesDataJson = Catalog.readAsset(application, "cable_types_data.json"),
                        breakerCatalogJson = Catalog.readAsset(application, "breaker_catalog.json"),
                        accessoryCatalogJson = Catalog.readAsset(application, "accessory_catalog.json"),
                        deratingDataJson = Catalog.readAsset(application, "derating_data.json"),
                        transformersSeedJson = Catalog.readAsset(application, "transformers_seed.json"),
                        buswayCatalogJson = Catalog.readAsset(application, "busway_catalog.json")
                    )
                }
            }
    }
}
