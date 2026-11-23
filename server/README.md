# VIP X Ultimate — Backend setup and provider policy

## Required runtime
- Node.js 20 or newer.
- `APP_ACCESS_TOKEN`: long random bearer token shared with the Android app.
- A deployed HTTPS endpoint. The Android app does not host this service itself.

## Free-tier AI providers
The backend can use Gemini and Groq as configured free-tier candidates:
- `GEMINI_API_KEY` — Google AI Studio API key.
- `GROQ_API_KEY` — Groq API key.
- `GROQ_FREE_TIER_CONFIRMED=true` — required before Groq can be used; set it only after confirming the Groq organization is on the Free plan, not Developer or another paid plan.
- `AI_PROVIDER=auto` — try Gemini first, then eligible Groq Free-plan fallback.
- `AI_PROVIDER=gemini` or `groq` — choose the preferred first provider.

When a provider returns a temporary quota/rate-limit/server/network error, the backend tries the next configured eligible provider. Authentication and invalid-request errors are returned instead of pointlessly retrying another service.

Groq is disabled unless `GROQ_FREE_TIER_CONFIRMED=true` is explicitly set by the operator. Keep the Groq organization on its Free plan; upgrading it may make usage billable, so remove the flag before any plan change. The Groq fallback model is pinned to `openai/gpt-oss-120b`; the retired `llama-3.3-70b-versatile` is no longer used. Free tiers remain subject to eligibility, rate limits, model availability, and terms that may change. This is not unlimited or guaranteed free service. Internet/mobile data charges may also apply.

## Paid provider safety
This build removes OpenAI and all paid-provider routing from the backend. Legacy variables such as `OPENAI_API_KEY`, `AI_MODEL`, and `ALLOW_PAID_AI` are ignored. Even if they are present or set to `true`, requests can only go to the configured Gemini/Groq free-tier providers. If their quotas are exhausted, cloud AI stops and the Android local features remain available.

## Other optional services
- `DATABASE_URL` enables PostgreSQL-backed agent memory.
- `PORT` defaults to 3000.
- Model IDs are pinned in code to the selected models; environment variables cannot switch the backend to another model.

## Health and verification
- `GET /health` reports whether access control and an AI provider are configured.
- `GET /api/session` requires the bearer token and reports session capabilities.
- Run tests with `npm test`.

## Important deployment boundary
This repository contains backend source code, not a live deployment. A hosting project, environment secrets, provider accounts, and a public HTTPS domain must be configured before cloud features can work. Do not put provider API keys in the APK or commit them to Git.
