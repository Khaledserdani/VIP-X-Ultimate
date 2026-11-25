# VIP X — Universal Viewing Enhancement Direction

## Direction change
This document replaces the Tango-only Live Grid product direction. VIP X is now intended as a personal Android companion for improving the viewing/listening experience across different video and live-streaming apps. It must not modify, replace, repackage, or inject into those apps.

## User's actual goal
VIP X is an in-app video/live browsing and playback experience: browse a collection of streams, keep playback inside VIP X while moving between items, provide a multi-tile preview/grid, support automatic playback while browsing, and offer a floating/Picture-in-Picture player. Audio enhancement is not a priority. DRM-protected streams are in scope only through valid provider-issued Widevine licenses; bypassing DRM is explicitly out of scope.

## Reality check: Android boundaries
- A regular non-root app has no universal API to intercept decoded video frames from arbitrary third-party apps and replace them with enhanced frames.
- MediaProjection can capture screen/audio only after user approval. Android documents that capture can be stopped by the user/system, and source apps may restrict playback capture. It is not an invisible global video-processing pipeline.
- Android's AudioEffect APIs are associated with a specific audio session. The documented global output-mix path using session 0 is deprecated.
- VPNService routes packets through a virtual interface. It does not inherently improve the radio link, remove congestion, or force a streaming service to provide a higher bitrate.
- A stream's resolution and bitrate are usually selected by the source app/service and server; VIP X must not claim to override them without an exposed supported control.

## Product rules
1. Remove Tango as the sole target and remove the old live-grid assumption.
2. Remove RAM, temperature, and generic network status panels as the product's main functionality.
3. Do not display invented quality scores, FPS, bitrate, packet-loss, or improvement percentages.
4. Do not capture screens or audio in the background or without a clear user action and Android consent.
5. Do not extract private stream URLs, session tokens, or protected traffic; do not bypass DRM or app access controls.
6. No root, paid APIs, subscriptions, cloud services, CI/cloud builds, release uploads, or public distribution.

## Candidate implementation path
- Build an in-app stream library/browser and persistent navigation so selecting another stream does not require leaving the VIP X experience.
- Add a multi-tile preview grid with explicit per-tile loading/error states and resource limits; autoplay behavior must respect Android lifecycle, bandwidth, and user controls.
- Keep a selected stream in the Media3 playback service and support Picture-in-Picture/floating playback where Android permits.
- Integrate Widevine using provider-issued license URLs and authorized request headers; verify DASH/HLS/container compatibility, device Widevine provisioning/security level, license expiry, and renewal behavior.
- Never promise playback for arbitrary protected providers: each provider must authorize VIP X and issue a compatible license.

## Proof required
For each claimed improvement, test at least two unrelated streaming apps with the same content/network/device conditions. Record objective measures where available and user-visible results. Do not claim universal enhancement until the same feature works consistently across those apps.

## Current status
The app currently has a single HTTPS stream entry and a Media3 player backed by a MediaSessionService, plus system brightness control and a local AES-GCM test screen. Picture-in-Picture is configured. A first Widevine path now accepts a HTTPS license-server URL and optional Authorization request header and passes them to Media3 ExoPlayer. Error reporting distinguishes DRM/license, provisioning, parser, and network failure categories. This is not yet a browser, stream library, multi-tile preview grid, or automatic multi-stream browsing experience. Widevine playback is not yet verified on a real protected stream with a valid provider-issued license on the target phone.
