# VIP X — Product Baseline

## Product
A personal, standalone Android companion intended to improve video/live-stream viewing across multiple apps, not Tango specifically.

## User constraints
- Original streaming apps and accounts stay untouched.
- No root, no paid services/APIs, no cloud builds, no publication or distribution.
- No passive monitoring dashboard as the product.
- No fabricated quality claims or hidden capture of private streams.

## Current code reality
The current branch no longer contains the Tango Live Grid or the old diagnostics screen. It currently provides a standalone HTTPS media player (Media3 with HLS/DASH modules), Android system-brightness control after the special user-granted permission, and a local AES-GCM test screen. These are in-app/system-setting utilities; they do not enhance video frames or audio produced by unrelated streaming apps. No cross-app stream enhancement is implemented or validated.

## Engineering constraints
- A normal Android app cannot universally intercept and replace decoded frames rendered by arbitrary third-party apps.
- MediaProjection requires explicit user approval and captures only content the source app permits; it is not a universal in-place enhancement hook.
- Android AudioEffect APIs are tied to audio sessions; attaching effects to the global output mix via session 0 is deprecated.
- VPNService creates a routed virtual interface; packet routing alone does not improve throughput or override a streaming server's bitrate decisions.
- System-wide effects may require OEM/system privileges unavailable to an ordinary app.

## Candidate capabilities (must be proven)
1. System-supported display controls that genuinely affect other apps.
2. Optional user-approved screen processing only if latency, protected-content behavior, touch interaction, and battery cost are acceptable.
3. Audio improvements only for playback paths Android explicitly exposes to VIP X.
4. Network optimization only when a controlled test demonstrates lower latency, fewer stalls, or lower overhead; never promise higher source quality by default.

## Acceptance gate
A feature is called cross-app only after repeatable before/after tests in at least two unrelated streaming apps. A feature is called quality improvement only when an objective or clearly reproducible viewing/listening improvement is demonstrated.

## Excluded
Tango-only grid, fake preview streams, RAM/thermal monitoring as a primary feature, private stream endpoint extraction, token capture, DRM/access-control bypass, app modification/injection, paid APIs, cloud services, CI/cloud builds, and APK publishing.
