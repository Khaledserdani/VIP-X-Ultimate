# VIP X — Product Baseline

## Product
A personal Android companion dedicated to a Tango Live Grid and broadcast-viewing quality controls.

## Current implementation
- Kotlin + Jetpack Compose.
- Package: `com.vipx.ultimate`.
- Tango package detection and launch.
- Arabic RTL-friendly dark interface.
- Adjustable grid columns, preview-slot count, and data-saver preference.
- Explicit unavailable state until an authorized live-preview source exists.

## Not included
- Previous APK inspection and analysis features.
- Previous AI chat/model-download features.
- Cloud assistant, API gateway, remote server, or paid provider.
- Accessibility scraping or overlay service.
- Modifying or replacing Tango, bypassing subscriptions, DRM, or private APIs.

## Acceptance gate for Live Grid
Do not claim multi-live support until two or more independent authorized live video sources play concurrently in separate grid tiles, survive scrolling/resource management, and open the selected original room on tap.

## Cost and distribution
Personal use only. No paid APIs or services, no cloud build pipeline, no APK publishing or distribution.
