# VIP X — Product Baseline

## Product
A standalone Android app centered on its own video/live-stream browsing and playback experience: stream discovery/organization, in-app navigation, multi-tile previews, autoplay while browsing, and floating/Picture-in-Picture playback. Audio enhancement is not a priority.

## User constraints
- Original streaming apps and accounts stay untouched.
- No root, no paid services/APIs, no cloud builds, no publication or distribution.
- No passive monitoring dashboard as the product.
- No fabricated quality claims or hidden capture of private streams.

## Current code reality
The current branch currently provides one HTTPS Media3 stream entry with HLS/DASH modules, a MediaSessionService-backed player, Picture-in-Picture configuration, Android system-brightness control after special user permission, and a local AES-GCM test screen. A first Widevine integration accepts a HTTPS license URL and optional Authorization header, and surfaces DRM/license/provisioning/parser/network diagnostics. It does not bypass DRM. The in-app browser, stream library, multi-tile preview grid, and autoplay-while-browsing are not implemented yet. Real protected playback is not yet verified against a compatible stream and provider-issued license on the target device.

## Engineering constraints
- A normal Android app cannot universally intercept and replace decoded frames rendered by arbitrary third-party apps.
- MediaProjection requires explicit user approval and captures only content the source app permits; it is not a universal in-place enhancement hook.
- Android AudioEffect APIs are tied to audio sessions; attaching effects to the global output mix via session 0 is deprecated.
- VPNService creates a routed virtual interface; packet routing alone does not improve throughput or override a streaming server's bitrate decisions.
- System-wide effects may require OEM/system privileges unavailable to an ordinary app.

## Candidate capabilities (must be proven)
1. In-app stream browser/library with navigation that does not leave VIP X.
2. Multi-tile previews and controlled autoplay with bandwidth, decoder, battery, and lifecycle limits.
3. Persistent selected-stream playback and PiP/floating playback.
4. Widevine playback only with provider-authorized license servers; validate real protected DASH/HLS playback, provisioning, expiry, and renewal on target hardware.
5. Any cross-app display feature only when Android exposes a supported and user-approved mechanism.

## Acceptance gate
A feature is complete only after implementation, local build, and device-level test. DRM is complete only after an actual protected stream starts and remains playable using a valid provider-issued license on the target device. Grid/autoplay is complete only after multiple real streams preview concurrently without crashes or uncontrolled resource use. Never claim untested functionality.

## Excluded
Tango-only grid, fake preview streams, RAM/thermal monitoring as a primary feature, private stream endpoint extraction, token capture, DRM/access-control bypass, app modification/injection, paid APIs, cloud services, CI/cloud builds, and APK publishing.
