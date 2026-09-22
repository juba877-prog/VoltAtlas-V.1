package com.example.ai.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.ai.approval.DefaultTrustedPrincipalProvider
import com.example.ai.approval.TrustedProposalApprovalGateway
import com.example.ai.hybrid.ConversationEngine
import com.example.ai.hybrid.ConversationEngineProvider
import com.example.ai.proposal.DurableMutationWorkflow
import com.example.data.AppDatabase
import com.example.data.Catalog
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
    private val proposalCoordinatorProvider: () -> CableProposalPreparationCoordinator
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(EngineeringAssistantViewModel::class.java)) {
            return EngineeringAssistantViewModel(
                conversationEngine = conversationEngineProvider(),
                asyncApprovalGatewayProvider = approvalGatewayProvider,
                proposalPreparationCoordinator = proposalCoordinatorProvider()
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
            return EngineeringAssistantViewModelFactory(
                conversationEngineProvider = { ConversationEngineProvider.provideConversationEngine() },
                approvalGatewayProvider = asyncGatewayProvider,
                proposalCoordinatorProvider = { proposalCoordinator }
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
