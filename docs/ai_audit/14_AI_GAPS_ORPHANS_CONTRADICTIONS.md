# 14: AI Gaps, Orphans & Contradictions Analysis

## 1. Orphan Production Code
- **None detected**: All 131 production Kotlin files have active callers or registrations in factory/registry graphs (`UnifiedDeterministicToolRegistry`, `EngineeringAssistantViewModelFactory`, `ConversationEngineProvider`).

## 2. Test-Only Architecture
- **Multi-Turn Generative Tool Loop**: Verified only via test fakes implementing `GeminiClientSeam`. In production runtime, `RealGoogleGenAiClientSeam` is a static stub returning plain text.

## 3. Duplicate Implementations
- **None**: Single unified tool registry (`UnifiedDeterministicToolRegistry`), single mutation engine (`EngineeringMutationEngine`), and single router (`HybridConversationRouter`).

## 4. Documentation Drift vs Code
- **Firebase AI Service (`FirebaseAiServiceImpl.kt`)**: Older stub class created during early scaffolding is currently unused by the new `HybridConversationRouter` pipeline.

## 5. Dead or Misleading Names
- **`RealGoogleGenAiClientSeam`**: Named "Real", but is currently a static offline stub returning `"VoltAtlas Cloud AI Assistant processed response."`.
