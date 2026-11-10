# VIP X Ultimate Cloud AI Gateway

This Worker is the secure server-side bridge used by the Android app. The provider key is stored as a Cloudflare secret and is never embedded in the APK.

## Required Worker secrets
- `OPENAI_API_KEY`: API key from your AI provider.
- `APP_ACCESS_TOKEN`: a long random private token used by the app.
- Optional variable `AI_MODEL`: defaults to `gpt-4o-mini`.

## Deploy
1. Create a Cloudflare Worker and paste `worker.js`.
2. Add the two secrets above in Worker Settings → Variables and Secrets.
3. Deploy and copy the HTTPS Worker URL.
4. Open VIP X Ultimate → الذكاء السحابي, enter the Worker URL and APP_ACCESS_TOKEN.

The app saves only the Worker URL. The access token remains in memory and is not written to app storage. Rotate it if exposed.

## Important
A real cloud service requires an active provider account, billing/credits, and deployment of this Worker. This repository cannot deploy into your Cloudflare account without authorization. This gateway is an OpenAI-compatible example; it does not unlock subscriptions or bypass another app's access controls.
