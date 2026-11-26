# VIP X — In-App Live Grid Specification

## Product direction
VIP X provides a user-managed in-app grid for live streams the user is authorized to access. Users add HTTPS stream URLs, preview up to four streams, choose one audible stream, open a stream in the full player, or display the same preview players in a movable floating overlay.

## Current implementation (source-reviewed; not runtime-accepted)
- Compose Live Grid displays up to four stream tiles in a two-column layout.
- Stream URL, per-stream Widevine enablement, and per-stream license URL are persisted locally in SharedPreferences. Provider Authorization is not stored in those preferences.
- `MultiStreamPlaybackService` owns up to four Media3 ExoPlayer instances. The grid and floating-stream overlay attach views to those same players; opening the overlay does not create another set of players.
- `playStream` is idempotent by URL while a player for that URL exists. The same URL will not create a duplicate player. A terminal player error removes that player, detaches its views, and releases it; the UI offers a manual retry. Retryable load failures use Media3's default load-error handling; no custom unbounded retry loop is configured.
- Each preview player is configured with an 8 MiB LoadControl target, an 8-second maximum buffer, 1-second minimum/rebuffer thresholds, no retained back buffer, and time-priority buffering. The nominal combined target for four players is 32 MiB, not a hard memory cap.
- Preview video is muted by default. The service arbitrates Android audio focus for one explicitly selected audible stream. Muted previews pause when they have no visible grid/floating PlayerView; a selected audible stream may continue audio-only while it holds audio focus.
- Grid visibility is tracked separately from floating-overlay visibility. The grid's views are detached when the Activity stops; visible floating views can continue playback independently. The overlay is movable and closable.
- The foreground notification provides an explicit `إيقاف الكل` action. Stop-all, stream removal, terminal player failure, and service destruction detach registered views before releasing their players.
- The service uses `START_NOT_STICKY`. After process death, active preview players and in-memory session state are not restored automatically. The saved stream configuration can be loaded again when the grid is reopened; protected streams may require fresh Authorization if the encrypted value has expired or is unavailable.
- Widevine settings are per stream, including each stream's HTTPS license URL. Provider-issued Authorization is stored in AuthorizationVault for up to 8 hours, encrypted with AES-GCM using a non-exportable Android Keystore key and indexed by a SHA-256 digest of the stream URL. Expired/corrupt entries are removed when read or pruned; legacy 24-hour entries are capped to 8 hours from their original save time and migrated on successful read. Credentials are not sent to a VIP X backend.
- Opening a tile in the full player removes that preview from the grid and stops its preview player before launching the full player with that tile's current DRM settings and in-memory Authorization. The encrypted vault entry remains until expiry unless the stream is explicitly removed.
- The floating-stream overlay only displays streams already configured in VIP X. It does not inspect, discover, capture, or access private media/network/DRM sessions belonging to other applications.

## Resource and device limitations
- Four players are a configured ceiling, not a guarantee that every Android device can decode four streams simultaneously.
- The 8 MiB LoadControl target is not a hard heap/process-memory limit. Codec allocations, decoder surfaces, textures, manifests, network buffers, audio, and framework overhead add to actual memory use.
- No explicit resolution/bitrate cap or device-tier selector is configured. Adaptive formats are selected according to the stream manifests and Media3 track selection.
- Battery, bandwidth, thermal behavior, audio-focus behavior, decoder capacity, and background restrictions require measurement on target devices. Source inspection alone does not establish these properties.
- Widevine playback remains unverified without a provider-authorized protected stream and valid license. Do not claim arbitrary protected streams can be opened.

## Acceptance tests
1. Build locally with the pinned project toolchain.
2. Test one clear HLS stream and one DASH stream.
3. Test two and then four simultaneous streams on the target phone; record decoder errors, playback stability, memory, heat, battery, and network use.
4. Test duplicate URL submission/retry, stream removal, stop-all, and terminal network/HTTP errors; confirm no duplicate or orphaned players/views remain.
5. Background and foreground the grid with and without the floating overlay; verify pause/resume and player ownership.
6. Test selected-stream audio, transient/permanent audio-focus loss, and focus regain.
7. Test PiP in the full player on the target Android version.
8. Test DRM only with a provider-authorized protected stream and valid license. Do not mark DRM complete before successful playback.
