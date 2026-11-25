# VIP X — Product Baseline

## Product
A standalone Android app for in-app media access and playback: live/HTTPS streams, multi-tile previews, full playback with Picture-in-Picture, and local image/video viewing, including encrypted images and videos when the app has a supported decryption method and the user is authorized to access the content. Audio enhancement is not a priority.

## User constraints
- No subscriptions, paid services/APIs, cloud builds, CI builds, publication, or distribution.
- Do not modify `main`, merge, or rebase without explicit approval.
- Do not modify or inject into other streaming apps; no root, token extraction, private endpoint harvesting, or DRM bypass.
- Protected playback is supported only through provider-authorized Widevine licensing.

## Current implementation
- Arabic Compose main screen, dedicated Live Grid screen, and a Media Library screen.
- Media Library uses Android's Storage Access Framework document picker to select local images or videos without broad storage permission. Images are rendered in-app with ImageView; videos are played in-app with Media3 ExoPlayer. This is newly added source code and has not yet been built or device-tested.
- Encrypted image/video handling is a required product capability, not yet implemented by the basic Media Library. The design must support user-provided keys/passwords or provider-authorized DRM/license flows where applicable, identify supported encryption/container formats, and keep secrets out of persistent logs/storage. Do not imply universal decryption of arbitrary encrypted files.
- Up to four user-supplied HTTPS stream URLs are saved locally and shown in a two-column grid.
- Each tile creates its own Media3 ExoPlayer, starts muted, repeats, and exposes loading/ready/error state.
- Grid players pause when the grid Activity stops and release when their composable is disposed; returning to the grid resumes only previews that were actually playing before the stop, and the Android PlayerView is detached on release.
- Each grid entry stores its own Widevine enabled flag and license URL in an ordered JSON list; Authorization is held in an in-memory map keyed by stream URL for the current grid-screen session only and is not persisted.
- Tapping a tile opens the full Media3 player backed by MediaSessionService and passes the current DRM settings.
- Full player accepts HTTPS stream and license URLs, optional Authorization header, and has PiP configuration.
- HLS/DASH Media3 modules are dependencies. This is a URL-based stream grid, not yet a general web browser or content discovery engine.

## Known limitations / outstanding work
- Per-stream DRM configuration is implemented for grid entries. The license URL and DRM-enabled flag are stored per entry; the Authorization header is session-only and is lost when the grid screen/process is recreated. Existing legacy URL entries are migrated using the former shared DRM settings when loaded.
- Authorization is passed in Activity intents to the full player for the selected stream; avoid logging it and keep its lifetime short. It is not written to preferences or the per-stream JSON.
- The four-player cap is a UI limit, not proof that every device can decode four streams. Preview lifecycle resume now preserves the prior playing state rather than blindly restarting an idle/ended/error player. Device decoder, memory, bandwidth, battery, and thermal limits remain untested.
- Grid previews are Activity/composable-scoped. They pause in background and are released when the screen is disposed; they do not persist as a multi-stream background service.
- Full-player PiP and MediaSessionService integration are configured but require target-device verification.
- No real protected stream/provider license has been supplied or tested. Widevine integration is configuration plumbing, not verified DRM playback.
- The new local image/video viewer has not yet been built or tested on a device. Remote image URLs, remote standalone video URLs, and general web browsing are not yet part of the Media Library.
- Encrypted local images/videos, password/key entry, and format-specific decryption are not yet implemented. Widevine support currently exists only as per-stream player configuration; protected local video formats and other DRM systems are not verified or implemented.
- No local Gradle build, APK install, or target-device playback test is recorded.

## Acceptance gate
A feature is complete only after a successful local build and target-device test. DRM is complete only after an actual provider-authorized protected stream plays with a valid license. Grid/autoplay is complete only after multiple real streams play concurrently without crashes or uncontrolled resource use.

## Security testing boundary
Temporary security experiments are limited to app-owned test data, devices, and services, or systems for which the user has explicit authorization. The local AES-GCM lab creates its own temporary key and ciphertext and verifies both successful decryption and tamper rejection; it does not unlock third-party DRM. The product goal includes opening encrypted images and videos through supported decryption methods and authorized access, not merely displaying unencrypted media. Temporary test windows may be used to validate authorized access and playback, but must not be represented as a universal DRM bypass.

## Excluded
Unauthorized DRM/access-control bypass, private endpoint/token extraction, app modification/injection, paid APIs, cloud builds/CI, APK publishing, and claims of universal playback.
