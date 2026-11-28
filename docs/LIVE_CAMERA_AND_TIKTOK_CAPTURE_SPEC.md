# VIP X — Live Camera and TikTok Broadcast Analysis

Status: implementation plan / not yet implemented as a live capture pipeline.

## User workflow
The requested use case includes:
1. The phone's own camera, with the person voluntarily standing in view.
2. A TikTok LIVE session in which the user is a host, co-host/guest, or viewer watching another host.

These are separate capture sources and must not be presented as if they already work.

## Mode A — phone camera
- Use Android CameraX Preview + ImageAnalysis.
- Request CAMERA permission at runtime and show a persistent, visible preview while analyzing.
- Run MediaPipe Pose Landmarker in VIDEO mode on sampled frames; preserve monotonic timestamps.
- Convert camera frames using their actual rotation/crop metadata; close every ImageProxy in a finally block.
- Keep inference off the UI thread, bound frame queue to latest-frame strategy, and expose measured FPS, dropped frames, inference latency, pose confidence, and segmentation availability.
- Stop camera and release analyzer/model when the user stops or leaves the screen.
- No background recording; no saving or uploading frames by default.

## Mode B — TikTok LIVE / another host
- VIP X cannot read TikTok's private internal video stream or bypass its encryption/DRM.
- The supported Android route is MediaProjection screen capture after the operating system's explicit consent dialog. The user starts TikTok LIVE, starts VIP X capture, and explicitly approves screen sharing.
- Analyze only the visible screen frames that Android supplies. Do not capture audio unless a separate, explicit audio feature and permission are designed.
- If TikTok/Android marks content secure, the captured frames may be black or unavailable. Detect this and report "protected/unavailable"; never attempt to bypass it.
- Capture must show a clear, persistent in-app state and a Stop control. Stop immediately on revocation, service stop, or app teardown.
- Do not use Accessibility to scrape video frames, inject taps, defeat platform restrictions, or silently monitor other apps.
- A host/co-host and a viewer are equivalent from the capture API perspective: VIP X receives only the screen pixels the user has explicitly shared, not a privileged TikTok stream. The user must have permission to share that content.

## Live processing pipeline
Capture source -> frame normalization/rotation -> bounded latest-frame queue -> MediaPipe pose + segmentation -> temporal/occlusion tracker -> confidence/provenance -> garment overlay preview.
The current GarmentOverlayRenderer is a 2D proof-of-pipeline and must not be described as realistic 3D cloth fitting. Current body surface code is a torso scaffold, not a complete human mesh.

## Acceptance tests before claiming success
- Camera: front/side/three-quarter, raised arms, crossed arms, torso turn, walk, brief occlusion/reacquisition.
- LIVE screen capture: own live preview, co-host split screen, another host, UI overlays/comments, changing layouts, rotation, network quality changes.
- Screen capture consent denied/revoked; TikTok protected/black frames; app background/foreground; screen lock; service stop.
- Measure actual FPS, latency, dropped frames, CPU/thermal load, memory, pose confidence, segmentation availability, and stability.
- Compare close-fitting opaque sportswear with loose clothing under matched framing and lighting. Do not infer naked body geometry or claim true circumferences from a single screen/camera view.
- No production claim until on-device tests on the target Android device and live screen-capture tests pass.

## Current implementation gap
The repository currently analyzes user-picked prerecorded video with MediaMetadataRetriever. The MediaPipe tracker is configured for VIDEO mode and accepts Bitmaps. The app does not yet contain CameraX live preview/ImageAnalysis or a MediaProjection foreground capture service. This document is a design contract, not evidence that live capture is already implemented.
