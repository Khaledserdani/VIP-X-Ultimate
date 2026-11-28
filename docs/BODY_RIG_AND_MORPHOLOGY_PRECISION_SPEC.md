# VIP X — Body Rig and Morphology Precision Specification

Status: Engineering requirement / not yet implemented as a complete 3D pipeline.

## Objective
Build garments around the person's actual body shape and motion. The system must not reshape, slim, widen, or otherwise alter the source person's body.

## Two coupled representations

### 1. Articulated body skeleton
- Track the full articulated landmark set, not only visible torso points.
- Maintain 3D joint positions, parent-child bone relationships, joint limits, and segment-length consistency.
- Track shoulders, spine/torso, pelvis, arms, hands, legs, and feet.
- Preserve left/right identity and handle front, side, rear, and turning poses.
- Every joint estimate must carry provenance: directly observed, temporally tracked, geometrically inferred, or unavailable.
- Every estimate must carry confidence. Do not silently label inferred points as observed.

### 2. Body surface / morphology
- Use a deformable 3D body surface, not only a 2D silhouette or landmark polygon.
- Preserve individual morphology: chest/bust, under-chest, waist, abdomen projection, hips, shoulders, upper arms, thighs, calves, torso length, and natural left/right asymmetry.
- Fit the surface to available multi-view images, calibrated depth, or explicitly supplied measurements.
- A single RGB frame cannot establish hidden-side geometry or true body circumferences. Such values must remain estimates with uncertainty until supported by more evidence.
- Surface topology must remain stable across frames; avoid frame-to-frame body shape flicker.

## Occlusion policy
1. Prefer current reliable observations.
2. When a joint is temporarily occluded, predict it from recent motion and the articulated skeleton.
3. Enforce plausible bone lengths and joint constraints without forcing anatomical symmetry.
4. Reconcile predictions when the joint becomes visible again.
5. If uncertainty exceeds a threshold, mark the region low-confidence and avoid fabricating a precise garment boundary.

## Garment fitting rules
- The body surface is immutable input to garment fitting; garment vertices adapt around it.
- Garment anchors follow the current skeleton exactly.
- Cloth vertices may move, drape, and collide with the body surface; they must never displace body vertices.
- Fit must account for local curvature, body volume, posture, and motion, not only shoulder/hip widths.
- Sheer fabrics always retain an opaque lining layer for coverage.

## Validation gates
- Evaluate front, side, rear, three-quarter, sitting, bending, raised arms, crossed arms, and temporary occlusion.
- Measure joint jitter, bone-length drift, surface temporal stability, garment/body intersections, clipping, and anchor error.
- Report observed vs inferred joint counts and confidence; never advertise an inferred measurement as exact.
- Do not claim production-quality 3D fit until multi-view/depth fitting, collision handling, and full-video rendering are implemented and tested.

## Current project boundary
The current MediaPipe Pose Landmarker pipeline supplies pose landmarks and a person segmentation mask. The existing garment rig is a simplified 2D scaffold. The current cloth solver is a small deterministic stepper. These components do not yet constitute a fitted 3D body surface, an occlusion-complete body tracker, or production cloth simulation.

## Sportswear-first body estimation and garment evaluation

### Safe input method
- Do not remove, synthesize, or infer nudity from a real person's clothing or video.
- Estimate the visible body contour from the person while clothed. Prefer close-fitting, opaque sportswear over loose clothing when the user voluntarily supplies such footage.
- Treat tight-fabric contours as an observation of the clothed silhouette, not as exact skin geometry. Fabric compression, seams, folds, support garments, and posture can change the contour.
- Use multi-view footage (front, side, rear, and three-quarter) plus calibrated scale or explicit measurements to constrain the 3D body. Keep hidden geometry and circumferences unknown unless measured.

### Sportswear test sequence
1. Neutral standing pose, front / side / rear / three-quarter.
2. Arms raised overhead and lowered; arms crossed; elbows flexed.
3. Torso bend and rotation; shallow squat and lunge.
4. Walking or stepping, then brief partial occlusion and reacquisition.
5. Repeat in close-fitting opaque sportswear and in a loose outfit, using the same camera distance, lighting, and poses.

### Comparison metrics
- Pose: joint confidence, left/right identity switches, temporal jitter, and bone-length drift.
- Body estimate: visible-contour overlap, torso/limb surface stability, and the fraction of surface marked observed versus inferred.
- Garment: anchor error, clipping, body intersections, unwanted gaps, and temporal flutter during movement.
- Report results separately for each garment type; do not let a good result on a tight sportswear top imply that trousers or loose garments also fit correctly.

### Evidence limitation for the currently supplied clip
The currently available 15.9-second 1920x1080 clip shows a person in a dark work outfit reaching upward while standing on a ladder, with ladder-related occlusion. It is useful for raised-arm pose tracking and temporary occlusion tests, but it is not a sportswear comparison clip and does not provide front/rear multi-view body evidence. Do not score sportswear fit from this clip.
