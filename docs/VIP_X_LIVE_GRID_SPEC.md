# VIP X Live Grid — Product and Engineering Specification

## Product goal

VIP X is a standalone Android companion dedicated to a multi-preview live-broadcast browsing experience for Tango. The user should be able to browse a grid of live rooms and watch multiple live previews at once without entering each room. Selecting a tile should open that room in the original Tango app through a supported navigation route.

This is the entire product direction. The previous assistant, APK-analysis, cloud, and accessibility features are excluded. Only general engineering lessons may be reused where they directly improve this product.

## Product pillars

1. VIP-style browsing: live-room grid, quick navigation, favorites/recent rooms, adjustable tile layout, and compact controls.
2. Broadcast quality: device/network diagnostics, resource-aware preview limits, data-use awareness, and quality recommendations. VIP X must not claim to alter Tango's server-side stream quality unless Tango exposes a supported control.

## Current implementation status

- Dedicated Arabic RTL Live Grid shell.
- Tango package detection and launch action.
- 2/3-column layout, 2/4/6 tile setting, and data-saver preference (limited to two preview slots).
- Android network type, validated connectivity, estimated link bandwidth, metered status, available RAM, and thermal status diagnostics.
- No actual video provider is connected. Tiles are explicitly unavailable placeholders, not streams.

## Tango public integration research (25 November 2026)

Tango's official blog says it opened a public API for agencies, third-party providers, and power users. It describes creator-approved access and capabilities including creator chat automation, media-vault operations, starting a creator's own stream, and webhooks. The blog links to developer documentation on Tango for Business.

The publicly readable announcement does not specify a viewer-facing endpoint for discovering arbitrary live rooms or retrieving preview playback URLs. The linked developer documentation is rendered behind a page that could not be inspected in this research pass. Therefore:
- Do not claim that Tango has no viewer-preview API.
- Do not assume the creator API can provide viewer previews.
- Do not request creator account approval or use account credentials as a workaround.
- Revisit only if official technical documentation explicitly confirms viewer discovery and preview playback under the user's no-cost and account-safety constraints.

## Android no-root constraints

- VIP X cannot directly inspect or modify Tango's private process memory, internal databases, or protected network traffic.
- MediaProjection captures the device display after explicit Android user consent; it does not create independent streams for rooms Tango is not already rendering.
- VIP X does not use Accessibility scraping to extract private room data or hidden video streams.
- Android Picture-in-Picture does not itself provide simultaneous playback of multiple rooms.
- Do not claim multi-live playback until an authorized source for each concurrent preview is verified.

## Feasibility gate

1. Review official Tango viewer-facing API documentation, if it becomes publicly readable.
2. Determine whether Tango visibly exposes live video previews in a discovery feed that can be opened through supported Android navigation.
3. If neither path supplies independent authorized previews, deliver a clearly labeled discovery grid of legitimate static thumbnails/metadata with one-at-a-time opening. This fallback is not multi-live video and must never be presented as such.
4. Never reverse engineer private stream endpoints, extract session tokens, defeat DRM/access controls, impersonate clients, or conceal automation to avoid account enforcement.

## UX acceptance criteria

- Dedicated Live Grid screen.
- 2/3-column layout, scroll, and tile selection.
- Each tile distinguishes live video, static thumbnail, and unavailable states.
- Tap opens the corresponding room through a supported Tango route.
- Preview pause/resume, preview cap, and data-saver mode when the source supports them.
- Real loading/error states; no fabricated progress or stream status.
- Original Tango installation and account remain untouched.

## Performance and quality criteria

- Limit concurrent preview playback to a device-tested count.
- Pause off-screen previews when the provider supports it.
- Use Android APIs for connectivity, memory, and thermal diagnostics.
- Treat link bandwidth as an estimate, not measured throughput.
- Recommend fewer previews under low-memory or high-thermal conditions.
- Do not imply VIP X can force higher source bitrate or bypass Tango quality settings.

## Implementation sequence

1. Dedicated app shell — implemented.
2. Layout controls and truthful unavailable states — implemented.
3. Network/memory/thermal diagnostics — implemented.
4. Official API and supported discovery-source investigation — ongoing.
5. Real playback only after the feasibility gate passes.
6. Device-level performance validation without cloud builds or APK uploads.

## Definition of done

Complete only when at least two distinct live rooms visibly play simultaneously from authorized sources while scrolling, and tapping a tile opens that room. Static thumbnails, a single screen capture, or placeholder tiles do not satisfy this definition.
