# VIP X — In-App Live Grid Specification

## Product direction
VIP X is an in-app video/live-stream browsing and playback experience. The user adds stream URLs they are authorized to access, previews them inside VIP X, opens a selected stream in the full player, and may use Android Picture-in-Picture where supported.

## Current implementation
- A dedicated Compose Live Grid screen accepts up to four HTTPS stream URLs.
- URLs are stored locally in SharedPreferences.
- A two-column grid creates one muted Media3 ExoPlayer per tile, with repeat enabled and visible loading/ready/error text.
- Players pause when the grid screen receives lifecycle stop and release when their composables are disposed.
- Widevine can be enabled for the grid. A shared HTTPS license URL is persisted; provider-issued Authorization is stored locally in AuthorizationVault for up to 8 hours, encrypted with AES-GCM using a non-exportable Android Keystore key and indexed by a SHA-256 digest of the stream URL.
- Selecting a tile launches the full PlayerActivity and passes its URL and current DRM settings.
- The full player uses MediaController/MediaSessionService and has PiP configuration.

## Limitations
- This is a user-managed URL grid, not a general browser, search engine, or automatic stream discovery feature.
- DRM configuration is shared across tiles. Independent per-stream license URLs/headers are not yet supported.
- Authorization headers are passed through an Activity intent to the full player. The encrypted local vault retains provider-issued values for up to 8 hours; expired or corrupt entries are removed when read or pruned.
- Four simultaneous players may exceed device decoder, memory, network, battery, or thermal capacity. The UI cap is not a device capability guarantee.
- Grid playback is not persistent in the background. It pauses when the Activity stops and releases when the screen is disposed.
- No provider-issued license or protected test stream has been provided; actual Widevine playback is unverified.
- No local build, APK installation, or device-level multi-stream test is recorded.

## DRM rules
Use only a license URL and request headers issued by the content provider for VIP X. Require HTTPS. Do not bypass DRM, extract keys, intercept other apps, or claim universal protected-stream support. Provider authorization, manifest/container compatibility, device provisioning/security level, license expiry, and renewal must be verified for each provider.

## Acceptance tests
1. Build locally with the pinned project toolchain.
2. Test one clear HLS and one DASH stream.
3. Test two then four streams on the target phone; record playback failures, decoder errors, heat, battery impact, and network use.
4. Background and foreground the grid; confirm pause/resume and resource release.
5. Test PiP on the target Android version.
6. Test DRM only with a provider-authorized protected stream and valid license. Do not mark DRM complete before successful playback.
