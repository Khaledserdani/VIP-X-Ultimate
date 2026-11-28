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
