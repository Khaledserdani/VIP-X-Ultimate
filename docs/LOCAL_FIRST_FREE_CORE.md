# VIP X Ultimate — Local-first and free-to-use core

## Binding product rule
The core Android app must remain usable without a paid host, provider key, subscription, billing profile, or cloud quota. Mobile internet access is not consent to incur service charges.

## Implemented in this branch
- Installed-app selection remains on Android.
- The offline APK analyzer reads the installed base APK and split APKs directly on-device.
- It computes SHA-256 for the base APK, reads package/version/SDK metadata, enumerates DEX/native/resource entries, and reports the signing-certificate fingerprint exposed by Android PackageManager.
- Four transparent local analyzers produce structure, permission-review, resource/design-evidence, and engineering summaries.
- The last report is stored in private app preferences per package.
- The offline analysis path does not open a network connection and does not upload APK files or screen contents.

## Honest capability boundary
The four local analyzers are deterministic rule-based analysis, not generative AI models. They are useful offline and have no provider quota, but they do not replace a full static-analysis suite, dynamic malware sandbox, visual UI review, or an LLM.

A later on-device language model may be added only after Android runtime compatibility, model licensing, device memory/performance, package size, and offline operation are verified. Model files must not silently download through a paid service. No model is currently bundled or claimed to be active.

## Optional cloud features
The cloud chat and server-side AI team remain optional. They require a separately operated HTTPS backend, access token, and Gemini/Groq free-tier credentials. The backend in this branch contains no paid-provider route: legacy OpenAI keys and `ALLOW_PAID_AI` flags are ignored. If all free quotas are exhausted, cloud AI stops; the app must not require it for local app listing, local APK inspection, or viewing locally saved reports.

Free-tier availability and quotas can change and may run out before a 30-day trial ends. This project does not configure billing, deploy hosting, or create paid resources.

## Acceptance gates before any APK is offered
1. Build succeeds from this branch in GitHub Actions.
2. APK exists, is non-empty, and its Android package/version/signature are verified.
3. Offline APK analysis works without network connectivity and without an access token.
4. A test confirms the analyzer does not call network endpoints.
5. The saved report can be reopened after app restart.
6. Optional cloud controls are visibly labeled optional and quota/hosting-dependent.
7. No production service, secret, billing setting, or `main` branch is modified as part of this work.
