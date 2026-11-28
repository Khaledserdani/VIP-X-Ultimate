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
- GPU acceleration where supported; adaptive quality/resolution for device limits
- Local processing by default; disclose any optional external processing and obtain consent

## Acceptance gates
A build must not be described as photorealistic or production-ready until it passes recorded tests across:
- front, side, and back views
- walking, turning, arm raising, and sitting
- varied body shapes, lighting, and backgrounds
- motion blur and partial occlusion
- long clips with no progressive drift or flicker

Report measured results and known limitations. A literal 100% realism guarantee is not technically honest; the target is the highest measurable realism on the target phone, with failures clearly reported rather than hidden.

## Android constraints
Overlay UI, camera, local media access, and screen capture require their respective Android permissions and user actions. An ordinary app cannot silently capture or modify protected content in other apps.
