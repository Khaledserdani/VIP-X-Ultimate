# VIP X Ultimate — Cloud Platform Baseline

## Android app
- Local installed-app inventory and APK metadata inspection.
- Arabic RTL cloud assistant screen.
- HTTPS gateway configuration; only the gateway URL is persisted.
- Bearer access token is held in memory only.
- Sends recent chat context to a user-controlled gateway.

## Cloud gateway
- Cloudflare Worker template at `cloud/worker.js`.
- Requires server-side `OPENAI_API_KEY` and `APP_ACCESS_TOKEN` secrets.
- Proxies chat requests to an OpenAI-compatible Chat Completions API.
- Validates bearer access, method, path, and message length.

## Not yet live
The Worker is source code only until deployed to a Cloudflare account and configured with valid provider credentials. No cloud AI call can succeed before that. API usage may incur provider charges.

## Platform boundaries
The app does not unlock VIP subscriptions, bypass payment, or access private app data. It can inspect package metadata and APK files Android exposes to it.
