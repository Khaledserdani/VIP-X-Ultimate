# VIP X — Universal Viewing Enhancer

VIP X is being redirected from a Tango-only live-grid prototype to a personal Android viewing-enhancement project intended to work alongside different video and live-streaming apps. The original apps remain installed and unmodified.

## Product direction
The goal is to improve the actual viewing experience wherever Android permits it:
- Better display presentation through system-supported controls.
- Better listening experience through audio controls that Android exposes.
- Reduce avoidable network overhead only where a measurable, safe method exists.
- Keep controls quick, optional, and usable while another app is in the foreground.

## Important technical boundary
A normal, non-root Android app cannot universally rewrite another app's decoded video frames, force its server to send a higher bitrate/resolution, or attach arbitrary audio effects to every other app's private audio session. MediaProjection is user-approved capture, not a universal in-place video processor; some apps can block capture. Android audio effects are session-scoped and global output-mix insertion is deprecated. A VPN routes traffic through a virtual interface but does not, by itself, make a slow connection faster.

Therefore VIP X will not claim universal sharpening, upscaling, bitrate control, frame-drop repair, or audio enhancement until a real implementation is demonstrated on multiple independent apps and devices.

## What is being removed from this direction
- Tango-only live-grid assumptions and package-specific controls.
- RAM, temperature, and connection dashboards as the main product.
- Fake live previews, fabricated quality scores, and passive reports presented as improvements.

## Cost and privacy
Personal use only. No paid APIs, subscriptions, cloud services, cloud builds, APK publishing, or modification/repackaging of other apps. No private stream extraction, session-token capture, DRM bypass, or hidden traffic interception.

## Validation standard
A feature counts as implemented only after a reproducible before/after test shows the effect in at least two unrelated video apps, without destabilizing playback or exposing private screen content.
