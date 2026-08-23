# 11: AI Security, Privacy & Network Audit

## 1. Network & Secret Findings

- **Internet Permission**: `android.permission.INTERNET` is declared in `AndroidManifest.xml` for Firebase authentication / general connectivity.
- **API Keys**: Zero hardcoded API keys exist in git-tracked code. `BuildConfig.GEMINI_API_KEY` is an empty string by default.
- **Feature Gate**: `BuildConfig.ENABLE_CLOUD_LLM` is `false` by default.
- **Data Egress**: Zero project data leaves the device in the current build. All conversation turns and tool queries execute locally on-device.

---

## 2. Security Defenses

1. **Engineering Claim Guard (`EngineeringClaimGuard.kt`)**: Prevents any LLM from fabricating numerical engineering claims (such as sizing or voltage drop) unless backed by an authentic deterministic tool result envelope.
2. **Layer D Context Boundary (`RuntimeContracts.kt`)**: Limits exposed context strictly to bounded entity definitions without leaking raw internal Room metadata or user device info.
3. **Prompt Injection Hardening (`RuntimePromptCompiler.kt`)**: Wraps user input in distinct XML/fenced tags with system instructions that user input cannot alter calculation rules or bypass approval gateways.
