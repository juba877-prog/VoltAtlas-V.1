package com.example.ai.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.ai.approval.DefaultTrustedPrincipalProvider
import com.example.ai.approval.TrustedProposalApprovalGateway
import com.example.ai.hybrid.ConversationEngineProvider
import com.example.ai.proposal.DurableMutationWorkflow
import com.example.data.AppDatabase
import com.example.data.Catalog
import com.example.data.GlobalPreferences
import com.example.data.catalog.CatalogRepository

/**
 * Authoritative production composition for the assistant. UI code receives the
 * finished ViewModel and never creates Room, repositories, or approval services.
 */
class EngineeringAssistantViewModelFactory private constructor(
    private val conversationEngineProvider: () -> com.example.ai.hybrid.ConversationEngine,
    private val approvalGatewayProvider: () -> TrustedProposalApprovalGateway,
    private val proposalCoordinatorProvider: () -> CableProposalPreparationCoordinator
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(EngineeringAssistantViewModel::class.java))
        return EngineeringAssistantViewModel(
            conversationEngine = conversationEngineProvider(),
            approvalGatewayProvider = approvalGatewayProvider,
            proposalPreparationCoordinator = proposalCoordinatorProvider()
        ) as T
    }

    companion object {
        fun production(application: Application): EngineeringAssistantViewModelFactory {
            val database = AppDatabase.getDatabase(application)
            val preferences = GlobalPreferences(application)
            val principalProvider = DefaultTrustedPrincipalProvider {
                preferences.trustedEngineerIdentity.value
            }
            val proposalCoordinator = CableProposalPreparationCoordinator(
                projectDao = database.projectDao(),
                circuitDao = database.circuitDao()
            )
            // Deferred on-demand gateway: asset JSON files are parsed only if an approval is executed
            val gatewayLazy = lazy {
                val catalogRepository = productionCatalogRepository(application)
                DurableMutationWorkflow.createGateway(
                    database = database,
                    catalogRepository = catalogRepository,
                    principalProvider = principalProvider
                )
            }
            return EngineeringAssistantViewModelFactory(
                conversationEngineProvider = { ConversationEngineProvider.provideConversationEngine() },
                approvalGatewayProvider = { gatewayLazy.value },
                proposalCoordinatorProvider = { proposalCoordinator }
            )
        }

        private fun productionCatalogRepository(application: Application): CatalogRepository =
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
