# 08: AI LLM Provider, Gemini, Firebase AI & Fallback Audit

## 1. Provider Landscape & Classifications

```
+-----------------------------------------------------------------------------------------------+
| Provider Type              | Current Status in Repo   | Network Access | API Key Required?     |
+----------------------------+--------------------------+----------------+-----------------------+
| com.google.ai.client       | REMOVED / ZERO USAGE     | None           | N/A                   |
| RealGoogleGenAiClientSeam  | STUB (Returns fixed text)| None           | FeatureGate (disabled)|
| GeminiClientSeam           | TESTABLE INTERFACE       | None           | None                  |
| LocalRuleEngineAdapter     | REAL PRODUCTION ENGINE   | None           | None (0 network)      |
| Firebase AI Logic          | OUT OF SCOPE (Phase E)   | Pending Cloud  | Backend App Check     |
+-----------------------------------------------------------------------------------------------+
```

---

## 2. Definitive Conclusion on Cloud AI Status

**`FAKE SEAM ONLY / LOCAL FALLBACK ONLY`**

- **Explanation**:
  - The repository contains **zero external network calls** to Gemini or Google AI servers.
  - The `RealGoogleGenAiClientSeam` is an offline stub class that implements `GeminiClientSeam` and returns fixed static text.
  - The `CloudLlmFeatureGate` is default-closed (`BuildConfig.ENABLE_CLOUD_LLM = false`).
  - When the assistant is used in the app, `ConversationEngineProvider` provides `DisabledCloudLlmEngine`, causing `HybridConversationRouter` to execute entirely through `LocalRuleEngineAdapter(ConversationPolicyEngine)`.
