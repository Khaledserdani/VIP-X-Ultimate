# VIP X — Universal Viewing Enhancement Direction

## Direction change
This document replaces the Tango-only Live Grid product direction. VIP X is now intended as a personal Android companion for improving the viewing/listening experience across different video and live-streaming apps. It must not modify, replace, repackage, or inject into those apps.

## User's actual goal
The app should perform useful work, not merely display device diagnostics. Desired outcomes include clearer viewing where technically possible, fewer avoidable playback interruptions, and better listening. The experience should be app-independent rather than tied to Tango.

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
- First investigate which system-wide display/audio controls are genuinely available to an ordinary app on the target Android device.
- Prototype only reversible, user-controlled controls.
- Evaluate any screen-processing approach against latency, secure/black frames, overlay recursion, touch handling, battery use, and compatibility across unrelated apps before adopting it.
- Consider network changes only with controlled before/after playback tests; a VPN alone is not an optimization feature.
- If a feature only works inside VIP X's own player, label it as an in-app feature, not universal.

## Proof required
For each claimed improvement, test at least two unrelated streaming apps with the same content/network/device conditions. Record objective measures where available and user-visible results. Do not claim universal enhancement until the same feature works consistently across those apps.

## Current status
This is a product-direction correction, not a completed enhancement. The current Kotlin screen has moved away from the legacy Tango Live Grid to a standalone Media3 player, a user-granted system-brightness control, and a local AES-GCM test screen. The player supports user-supplied HTTPS media URLs compatible with Media3 (including HLS/DASH modules); these functions do not process video/audio rendered by unrelated apps. No universal cross-app video/audio enhancement has yet been implemented or proven.
