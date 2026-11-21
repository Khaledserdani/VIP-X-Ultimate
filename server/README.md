# VIP X Ultimate — Backend setup and provider policy

## Required runtime
- Node.js 20 or newer.
- `APP_ACCESS_TOKEN`: long random bearer token shared with the Android app.
- A deployed HTTPS endpoint. The Android app does not host this service itself.

## Free-tier AI providers
The backend can use Gemini and Groq as configured free-tier candidates:
- `GEMINI_API_KEY` — Google AI Studio API key.
- `GROQ_API_KEY` — Groq API key.
- `AI_PROVIDER=auto` — try Gemini first, then Groq.
- `AI_PROVIDER=gemini` or `groq` — choose the preferred first provider.

When a provider returns a temporary quota/rate-limit/server/network error, the backend tries the next configured eligible provider. Authentication and invalid-request errors are returned instead of pointlessly retrying another service.

Free tiers are subject to provider account eligibility, rate limits, model availability, and terms that may change. This is not unlimited or guaranteed free service. Internet/mobile data charges may also apply.

## Paid provider safety
OpenAI is excluded from automatic routing unless the operator explicitly sets both:
- `OPENAI_API_KEY`
- `ALLOW_PAID_AI=true`

Without that explicit opt-in, the server will not send requests to OpenAI, even if an OpenAI key happens to be present. Never enable this variable unless the account owner has knowingly accepted provider charges.

## Other optional services
- `DATABASE_URL` enables PostgreSQL-backed agent memory.
- `PORT` defaults to 3000.
- `GEMINI_MODEL` defaults to `gemini-2.5-flash`.
- `GROQ_MODEL` defaults to `llama-3.3-70b-versatile`.
- `AI_MODEL` defaults to `gpt-4o-mini` and is only used when paid OpenAI is explicitly enabled.

## Health and verification
- `GET /health` reports whether access control and an AI provider are configured.
- `GET /api/session` requires the bearer token and reports session capabilities.
- Run tests with `npm test`.

## Important deployment boundary
This repository contains backend source code, not a live deployment. A hosting project, environment secrets, provider accounts, and a public HTTPS domain must be configured before cloud features can work. Do not put provider API keys in the APK or commit them to Git.
