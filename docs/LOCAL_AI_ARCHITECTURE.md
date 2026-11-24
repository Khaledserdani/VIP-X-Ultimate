# VIP X Ultimate — Local AI Architecture

Status: accepted direction; implementation in progress
Branch: `feature/vipx-local-ai-foundation`
Date: 2026-11-16

## Product guarantees

- The core assistant must not require an OpenAI/Gemini key, API credits, subscription, or a hosted inference server.
- Core text inference runs on the Android device after the user has installed the app and downloaded a compatible model once.
- Model weights are stored outside the APK. The APK remains small; model download is explicit, resumable where supported, and reports required storage.
- No model is loaded until its file is present and validated.
- Chat history and user-approved memory remain local by default.
- Network-dependent capabilities (live web search, remote integrations, cloud sync) are optional and must be clearly separated from the offline core.
- Existing app screens and installed-app inspection features are preserved. Migration must not silently remove the current cloud screen until local mode is proven.

## Current repository facts

- Android app: Kotlin, Jetpack Compose, application ID `com.vipx.ultimate`, minSdk 26.
- Kotlin plugin: 2.0.21; Android Gradle Plugin: 8.7.3.
- Current assistant screen is `CloudAssistantActivity.kt`; it sends chat requests to `/api/chat` and requires a bearer token.
- The cloud worker and `server/index.js` require provider secrets. They are not the foundation for the free-lifetime assistant.
- No local model runtime or model manager is currently integrated.

## Runtime decision

Use Google's LiteRT-LM Kotlin Android runtime as the first local inference backend, behind an app-owned interface. It accepts a local `.litertlm` model path and can run CPU/GPU backends. The runtime is not itself the intelligence: model weights, prompts, memory, tools, and evaluation remain app responsibilities.

Official implementation references:
- https://github.com/google-ai-edge/LiteRT-LM/blob/main/docs/api/kotlin/getting_started.md
- https://developer.android.com/agents/skills/device-ai/litert/litert-lm/skill

### Compatibility gate — do not skip

The current project uses Kotlin 2.0.21. LiteRT-LM 0.18.x uses Kotlin 2.4 metadata and will not compile safely with the current compiler. LiteRT-LM 0.16.1 uses Kotlin 2.3 metadata and requires Kotlin 2.2 or newer. Therefore:
1. Do not add a `latest.release` dependency.
2. First select a pinned runtime version compatible with the project's Android Gradle Plugin, Gradle wrapper, and Kotlin toolchain.
3. Upgrade build tooling only as a coordinated, CI-verified change.
4. Add the runtime dependency before importing any LiteRT-LM classes.
5. Run the existing Android CI build before adding engine implementation.

## Model selection policy

Do not hard-code a single model before measuring the user's phone.

Initial candidates from Google's Android implementation guide:
- Qwen3-0.6B LiteRT-LM, approximately 0.6 GB: low-footprint baseline candidate.
- Gemma 4 E2B IT LiteRT-LM, approximately 2.6 GB: higher-capability candidate, subject to device memory.

These are candidates, not promises of performance. The app must show model size, available storage, download progress, and a clear compatibility status. The final default is selected after a real-device test. Model licenses and redistribution terms must be checked for the exact model revision before bundling or redistributing weights.

## App-owned architecture

```
Compose UI (existing visual design)
        |
VIPX Assistant Facade
        |
        +-- LocalAiEngine interface
        |       +-- LiteRtLocalEngine (first backend)
        |
        +-- LocalConversationStore (Room/DataStore decision pending)
        +-- LocalMemoryIndex (opt-in memory)
        +-- Prompt & Policy Layer
        +-- Tool/Skill Registry (later phase)
        +-- Evaluation Harness
        |
Optional network adapters (web search / external integrations)
        [never required for core chat]
```

The UI talks only to the facade, never directly to LiteRT-LM or a network provider. This allows replacing the runtime or model without rewriting screens.

## Required implementation sequence

1. Create and verify a feature branch; never alter `main` directly.
2. Add the local engine contract and local model state model without changing the current screen.
3. Pin compatible Gradle/Kotlin/runtime versions and prove a clean APK build in CI.
4. Add model download/import, checksum verification, storage checks, progress, cancellation, and retry.
5. Implement single-owner engine lifecycle on a background executor; never initialize or generate on the UI thread.
6. Add streaming local responses and local chat persistence.
7. Add memory, tools, and skills only after local chat is stable.
8. Compare quality, latency, memory use, and crash rate on actual target devices.
9. Keep the cloud adapter optional and disabled by default; remove cloud-only UI only after local acceptance tests pass.

## Acceptance criteria

- App installs and opens with no API key and no server URL.
- Core chat can run in airplane mode after the model is downloaded.
- No chat content is sent to a remote inference provider in local mode.
- A missing model produces a clear download/import screen, not a crash.
- Large model initialization and generation never block the UI.
- Engine/conversation resources have one owner and are closed exactly once.
- Existing app inspection and launcher features remain functional.
- CI builds the APK from the feature branch.
- No claim of “free forever” depends on a third-party server staying free; local inference has no per-token provider bill, while model downloads use the user's data and storage.

## Explicitly out of scope for the first local release

- Training a frontier-scale language model from scratch.
- Guaranteed performance on unknown hardware.
- Unlimited live web search without a network/search provider.
- Silent background model downloads.
- Removing the existing cloud screen before local mode is verified.
