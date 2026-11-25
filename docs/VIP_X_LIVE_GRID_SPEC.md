# VIP X Live Grid — Product and Engineering Specification

## Product goal

VIP X is a standalone Android companion dedicated to a real multi-preview live-broadcast browsing mode for Tango. The user should be able to browse a grid of live rooms and watch multiple live previews at once without entering each room. Selecting a tile opens that room in the original Tango app when a supported deep link or official navigation route exists.

This is the entire product direction. The previous assistant, APK-analysis, cloud, and accessibility features are excluded; only reusable general engineering practices may carry over.

## Two product pillars

1. VIP-style browsing tools: live-room grid, quick navigation, favorites/recent rooms, adjustable tile layout, and a compact control surface.
2. Broadcast quality tools: connection/bitrate diagnostics where measurable, device temperature and resource monitoring, data-use estimates, and recommended quality settings that the host app actually supports.

## Android no-root constraints

- VIP X cannot directly inspect or modify Tango's private process memory, internal databases, or protected network traffic.
- MediaProjection captures the device display after explicit Android user consent; it does not create independent streams for rooms that Tango is not already rendering.
- VIP X does not use an Accessibility service to scrape Tango or control its private UI.
- Android Picture-in-Picture can keep one supported activity visible; it does not by itself provide simultaneous playback of many Tango rooms.
- Do not claim multi-live playback until a supported source for each concurrent preview is verified.

## Feasibility gate for multi-live playback

Before implementing playback, test these routes in order:

1. **Host-provided live previews:** determine whether Tango's visible live-discovery feed itself plays multiple preview videos concurrently. If yes, VIP X may provide an enhanced browsing surface around those supported previews, subject to app/API behavior.
2. **Official SDK/API/deep links:** check for documented public interfaces that provide authorized live preview streams or room metadata. Use only documented interfaces and user-authorized access.
3. **Fallback if neither exists:** provide an enhanced live-room discovery grid using preview images/metadata that Tango visibly and legitimately exposes, plus one-at-a-time opening in Tango. Clearly label this as thumbnails, not simultaneous live video.

### Public API finding (25 November 2026)

Tango's public August 2026 announcement describes API access for agencies, third-party providers, and power users, with creator-approved access for chat automation, media-vault operations, starting a creator's stream, and webhooks. The public announcement does not document a viewer-facing endpoint for discovering arbitrary live rooms or obtaining concurrent viewer preview URLs. Therefore this API is not yet accepted as a Live Grid video source. Revisit only if official technical documentation confirms viewer preview access within the user's no-cost and no-account-risk constraints.

Do not reverse engineer private stream endpoints, extract session tokens, defeat DRM/access controls, impersonate clients, or conceal automation to avoid account enforcement.

## UX acceptance criteria

- A dedicated Live Grid entry exists in VIP X.
- Grid supports 2-column and 3-column layouts, scroll, and tile selection.
- Each tile visibly identifies whether it is a live video preview, a static thumbnail, or unavailable.
- Tapping a tile opens the corresponding room through a supported Tango navigation path.
- User can pause/resume previews, set a preview limit, and select a data-saving mode when the source supports it.
- The screen displays real loading/error states; no simulated progress or fabricated stream status.
- The original Tango installation and account remain untouched.
- No APK replacement, account entitlement spoofing, subscription bypass, anti-cheat evasion, or hidden account-risk claims.

## Performance and quality acceptance criteria

- Limit concurrent preview playback to a device-tested count; never assume all rooms can decode simultaneously.
- Stop or pause off-screen previews when appropriate.
- Provide a low-data mode and explain that actual stream quality is controlled by the source app/service.
- Measure frame drops, memory pressure, thermal state, and network use only through supported Android APIs.
- If simultaneous stream sources are unavailable, disable the multi-video mode and explain the limitation rather than showing fake live tiles.

## Implementation sequence

1. Replace the previous app experience with a dedicated VIP X Live Grid screen shell.
2. Implement tile model, layout controls, loading/error/preview-type states using mock data only in UI tests.
3. Perform a manual compatibility investigation of Tango's actual live-discovery UI and public integration surface.
4. Select the supported preview provider based on evidence.
5. Implement playback only after the feasibility gate passes.
6. Add resource-aware concurrency limits and quality diagnostics.
7. Validate on the user's Android device; do not run cloud builds or upload APK artifacts as part of this work.

## Definition of done

The feature is complete only when at least two distinct live rooms are visibly playing simultaneously from authorized sources while the user scrolls the grid, and tapping a tile opens that room. Static thumbnails or a single screen capture do not satisfy this definition.