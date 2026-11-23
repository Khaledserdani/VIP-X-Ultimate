# VIP X Ultimate — Local-first and free-to-use core

## Binding product rule
The core Android app must remain usable without a paid host, provider key, subscription, billing profile, or cloud quota. Mobile internet access is not consent to incur service charges.

## Implemented in this branch
- Installed-app selection remains on Android.
- The offline APK analyzer reads the installed base APK and split APKs directly on-device.
- It computes SHA-256 for the base APK, reads package/version/SDK metadata, enumerates DEX/native/resource entries, and reports the signing-certificate fingerprint exposed by Android PackageManager.
- Four transparent local analyzers produce structure, permission-review, resource/design-evidence, and engineering summaries.
- The last report is stored in private app preferences per package.
- Accessibility screen-text capture is disabled by default and requires a separate in-app opt-in; turning it off clears the stored screen context. Sending that context to the cloud requires a second, independent opt-in.
- The offline analysis path does not open a network connection and does not upload APK files or screen contents.

## Honest capability boundary
The four APK analyzers remain deterministic rule-based checks. This branch also adds an optional on-device language model using Google's LiteRT-LM runtime and Qwen3-0.6B (Apache-2.0). The model file is about 614 MB and is downloaded only after the user explicitly taps the download button; its SHA-256 is verified before activation. After the one-time download, inference runs on the phone CPU without an API key, server, or network request. The model is smaller and less capable than cloud models, and is not a substitute for a full static-analysis suite or dynamic malware sandbox.

## Optional cloud features
The cloud chat and server-side AI team remain optional. They require a separately operated HTTPS backend, access token, and Gemini/Groq free-tier credentials. The backend in this branch contains no paid-provider route: legacy OpenAI keys and `ALLOW_PAID_AI` flags are ignored. If all free quotas are exhausted, cloud AI stops; the app must not require it for local app listing, local APK inspection, or viewing locally saved reports.

Free-tier availability and quotas can change and may run out before a 30-day trial ends. This project does not configure billing, deploy hosting, or create paid resources.

## Acceptance gates before any APK is offered
1. Build succeeds from this branch in GitHub Actions.
2. APK exists, is non-empty, and its Android package/version/signature are verified.
3. Offline APK analysis works without network connectivity and without an access token.
4. The model download checks exact byte length and SHA-256 before use.
5. Local model inference is verified on a physical target device; CI compilation alone does not prove device performance.
6. Cloud failures and quota exhaustion route to the local model only after the model has been downloaded.
7. No production service, secret, billing setting, or `main` branch is modified as part of this work.
