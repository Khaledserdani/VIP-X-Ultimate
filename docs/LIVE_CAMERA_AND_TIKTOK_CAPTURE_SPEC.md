# VIP X — Live Camera and Cross-App Broadcast Analysis

Status: engineering specification / live capture is not yet implemented.

## User workflow
Support two distinct sources:
1. The phone's own camera, with the person voluntarily in view.
2. A live broadcast displayed in another Android app, whether the user is the host, co-host/guest, or viewer. Examples include TikTok LIVE, Instagram Live, Facebook Live, YouTube Live, and other apps that display live video.

The feature is app-agnostic at the Android screen-capture layer. It must not claim that every app is compatible: protected/secure surfaces and platform restrictions can prevent capture.

## Mode A — phone camera
- Use Android CameraX Preview + ImageAnalysis.
- Request CAMERA permission at runtime and show a persistent, visible preview while analyzing.
- Run MediaPipe Pose Landmarker on sampled frames; preserve monotonic timestamps.
- Convert camera frames using actual rotation/crop metadata; close every ImageProxy in a finally block.
- Keep inference off the UI thread, bound frame queue to latest-frame strategy, and expose measured FPS, dropped frames, inference latency, pose confidence, and segmentation availability.
- Stop camera and release analyzer/model when the user stops or leaves the screen.
- No background recording; no saving or uploading frames by default.

## Mode B — live broadcast in another app
- Use Android MediaProjection only after the operating system's explicit screen-sharing consent dialog. The user opens the chosen live app, starts VIP X capture, and approves sharing.
- This same method applies to any broadcast app, not only a named platform. VIP X analyzes only the screen pixels Android makes available; it does not obtain the broadcaster's private source stream.
- The capture may include app controls, comments, split-screen guests, overlays, and changing layouts. The vision pipeline must detect the actual video region where possible and report uncertainty when UI overlays obscure the person.
- Secure/DRM-protected surfaces may appear black or unavailable. Detect and report "protected/unavailable"; never bypass encryption, DRM, secure-window flags, app access controls, or paid restrictions.
- Capture must show a clear, persistent state and Stop control. Stop immediately on user stop, consent revocation, service stop, or app teardown.
- Do not use Accessibility to scrape video frames, inject taps, defeat platform restrictions, or silently monitor other apps.
- Do not capture or analyze audio by default. Any future audio feature requires a separate explicit design and permissions.
- The user must have the right and permission to share/analyze the displayed broadcast. No covert capture.

## App compatibility behavior
- Maintain a runtime compatibility result per source session: CAPTURE_AVAILABLE, CAPTURE_BLOCKED, NO_VIDEO_REGION, or USER_STOPPED.
- Do not hardcode a whitelist of broadcast apps as if that guarantees access. Compatibility depends on Android version, app implementation, secure-window policy, device vendor, and the actual screen content.
- If capture is blocked, explain that VIP X cannot access that protected surface and offer the phone-camera or user-imported-video path instead.

## Live processing pipeline
Selected source -> frame normalization/rotation -> video-region/person detection -> bounded latest-frame queue -> MediaPipe pose + segmentation -> temporal/occlusion tracker -> confidence/provenance -> garment overlay preview.
The current GarmentOverlayRenderer is a 2D proof-of-pipeline and must not be described as realistic 3D cloth fitting. Current body surface code is a torso scaffold, not a complete human mesh.

## Acceptance tests before claiming success
- Camera: front/side/three-quarter, raised arms, crossed arms, torso turn, walk, brief occlusion/reacquisition.
- Broadcast apps: test multiple unrelated live apps, host view, co-host/guest split screen, viewer view, UI overlays/comments, changing layouts, orientation changes, and poor network conditions.
- Screen capture consent denied/revoked; secure/black frames; app background/foreground; screen lock; service stop.
- Measure actual FPS, latency, dropped frames, CPU/thermal load, memory, pose confidence, segmentation availability, and stability.
- Compare close-fitting opaque sportswear with loose clothing under matched framing and lighting. Do not infer nude body geometry or claim true circumferences from a single camera/screen view.
- No production claim until on-device tests on the target Android device and representative broadcast apps pass.

## Current implementation gap
The repository currently analyzes user-picked prerecorded video with MediaMetadataRetriever. The MediaPipe tracker is configured for VIDEO mode and accepts Bitmaps. The app does not yet contain CameraX live preview/ImageAnalysis or a MediaProjection foreground capture service. This document is a design contract, not evidence that live capture is already implemented.
