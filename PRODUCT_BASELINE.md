# VIP X — Product Baseline

## Product
A personal Android companion dedicated to Tango Live Grid and broadcast-viewing quality diagnostics.

## Implemented
- Kotlin + Jetpack Compose.
- Package: `com.vipx.ultimate`.
- Tango package detection and launch.
- Arabic RTL-friendly dark interface.
- Adjustable 2/3-column layout and preview-slot cap.
- Data-saver preference with two-slot limit.
- Android network, memory, and thermal diagnostics.

## Not implemented
- Authorized Tango multi-live video source.
- Real playback in grid tiles.
- Measured stream throughput, frame-drop telemetry, or per-stream bitrate.
- Automatic adjustment of Tango's server-side quality.

## Excluded legacy features
APK analysis, local AI chat/model download, cloud gateway, remote server, Accessibility scraping, and old agent orchestration.

## Acceptance gate
Do not claim multi-live support until at least two independent authorized live video sources play concurrently in separate tiles, behave correctly during scrolling, and open the selected original room on tap.

## Cost and distribution
Personal use only. No paid APIs or services, no cloud build pipeline, no APK publishing or distribution.
