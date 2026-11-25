# VIP X — Product Baseline

## Product
A standalone Android app centered on in-app video/live-stream browsing and playback: an HTTPS stream collection, multi-tile previews, autoplay while the grid is visible, and a full player with Picture-in-Picture. Audio enhancement is not a priority.

## User constraints
- No subscriptions, paid services/APIs, cloud builds, CI builds, publication, or distribution.
- Do not modify `main`, merge, or rebase without explicit approval.
- Do not modify or inject into other streaming apps; no root, token extraction, private endpoint harvesting, or DRM bypass.
- Protected playback is supported only through provider-authorized Widevine licensing.

## Current implementation
- Arabic Compose main screen and a dedicated Live Grid screen.
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
- No local Gradle build, APK install, or target-device playback test is recorded.

## Acceptance gate
A feature is complete only after a successful local build and target-device test. DRM is complete only after an actual provider-authorized protected stream plays with a valid license. Grid/autoplay is complete only after multiple real streams play concurrently without crashes or uncontrolled resource use.

## Excluded
DRM/access-control bypass, private endpoint/token extraction, app modification/injection, paid APIs, cloud builds/CI, APK publishing, and claims of universal playback.
