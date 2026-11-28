# VIP X Virtual Stylist — Quality Specification

## Product goal
A consent-based Android virtual wardrobe for adult subjects. The same garment should look naturally worn in camera preview and in user-selected local photos/videos. Screen-capture processing is supported only where Android and the source app permit capture and the user grants the system permission.

## Wardrobe categories
- Formal clothing
- Sportswear
- Sheer-look fashion fabrics in multiple colors (e.g. chiffon, tulle, lace) with opaque, non-revealing lining.
- Garments are selected from a catalog with defined front/side/back views, fabric properties, and size metadata.

## Non-negotiable visual quality requirements
1. Body-aware fit: garment geometry conforms to estimated body shape and pose; no generic rectangular overlay.
2. Temporal stability: tracking remains attached through walking, turning, arm movement, and partial occlusion; avoid flicker, drift, and frame-to-frame garment changes.
3. Fabric behavior: plausible folds, stretch, drape, and motion consistent with garment type.
4. Occlusion correctness: arms, hair, hands, and foreground objects appear in front of or behind garment correctly.
5. Lighting consistency: garment shading, color, highlights, and shadows match the source scene.
6. Identity preservation: preserve the subject's face and recognizable appearance; do not alter identity.
7. Color/material fidelity: user-selected colors remain stable under scene lighting while retaining realistic material response.
8. Consent and safety: process only subjects who have consented; do not generate nudity or intimate undressing.

## Body-shape and anatomy preservation — hard invariant
The source person's body is the immutable reference. Virtual try-on may change garment pixels and garment silhouette only where the actual garment naturally covers the body; it must not reshape, beautify, normalize, or synthesize the person's anatomy.
- Preserve the original body volume and proportions, including a fat/large body, a slim body, muscular definition, natural softness, skin folds, and loose/sagging tissue.
- Never slim the waist, flatten the abdomen, narrow arms or thighs, enlarge shoulders, add muscle, remove cellulite, tighten loose skin, lift or reshape the chest, or otherwise retouch anatomy.
- For a large or fat body, size and drape the garment around the existing abdomen, waist, hips, arms, and thighs. Do not make the person thinner to make a catalog garment fit; choose a better size/pattern or report that the garment fit cannot be rendered reliably.
- Preserve body landmarks and silhouette outside the garment's true coverage. Under the garment, infer only the minimum hidden surface needed for cloth simulation; do not use that inference to change the visible body.
- Separate the person/body mask from the garment mask. The renderer must not apply global warps, face/body beautification, liquify, or body-slimming transforms.
- Keep an untouched source frame available for side-by-side comparison and provide a body-preservation check in quality evaluation.

## Processing sources
- Live camera preview
- User-selected local videos
- User-selected local photos
- User-initiated Android screen capture, subject to OS permission and source-app capture restrictions

## Engineering approach
- Human pose estimation and body segmentation
- Temporal person/garment tracking
- Garment representation with size and material metadata
- Virtual try-on rendering with temporal consistency and occlusion handling
- Explicit body-identity/body-shape preservation mask and geometry constraints
- GPU acceleration where supported; adaptive quality/resolution for device limits
- Local processing by default; disclose any optional external processing and obtain consent

## Acceptance gates
A build must not be described as photorealistic or production-ready until it passes recorded tests across:
- front, side, and back views
- walking, turning, arm raising, and sitting
- varied body shapes, including large/fat bodies, slim bodies, and muscular bodies
- natural abdomen contours, skin folds, and loose tissue remaining unchanged
- varied lighting and backgrounds
- motion blur and partial occlusion
- long clips with no progressive drift or flicker
- before/after body silhouette comparison showing no unintended anatomical change

Any measurable slimming, muscle enhancement, skin tightening, removal of natural folds, or body-proportion drift is a failed test, even if the garment looks attractive. Report measured results and known limitations. A literal 100% realism guarantee is not technically honest; the target is the highest measurable realism on the target phone, with failures clearly reported rather than hidden.

## Android constraints
Overlay UI, camera, local media access, and screen capture require their respective Android permissions and user actions. An ordinary app cannot silently capture or modify protected content in other apps.
