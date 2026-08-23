# Dependency Status and Test Profiles Review

## 1. Removed Dependency Analysis

- **Artifact:** `com.google.ai.client.generativeai:generativeai:0.9.0`
- **Status:** Removed from `gradle/libs.versions.toml` and `app/build.gradle.kts`.
- **Justification:** Unused and out of scope for the current seam-based phase. The active AI subsystem communicates via `FirebaseAiServiceImpl` and local `GeminiClientSeam` stubs.
- **Verification:** Repository-wide code search confirmed 0 imports or references to `com.google.ai.client.generativeai` in production code.

## 2. Test Execution Profiles

`app/build.gradle.kts` introduces configurable test profiles for optimized developer loop and CI execution:

- `fast` / `dev`: Default fast profile, runs core engineering logic excluding stress/screenshot suites.
- `ai`: Runs AI subsystem tests under `com.example.ai.*` exclusively.
- `stress`: Runs intensive math and fuzzy stress tests.
- `full` / `all`: Runs 100% of all unit and integration tests across the repository.

The default full test command `.\gradlew.bat testDebugUnitTest -Pprofile=full --offline` executes all test classes across the repository.
