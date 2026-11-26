# VIP X Live Grid — Device Acceptance Checklist

Status: **prepared, not executed**. This document defines the real-device checks required before treating four-stream playback as verified. No APK build, emulator run, or physical-device test is implied by this checklist.

## 1. Android service and permission preflight

- [ ] Install and launch on the minimum supported Android version (API 26).
- [ ] Install and launch on Android 13 (API 33), Android 14 (API 34), and Android 15 (API 35) or newer.
- [ ] Confirm the playback notification appears while one or more streams are active.
- [ ] Repeat with notification permission granted and denied. When denied, verify playback still works and document the system's foreground-service notification presentation.
- [ ] Confirm the app can start playback from a visible Live Grid activity without a Foreground Service start restriction exception.
- [ ] Grant overlay permission, open the floating previews, then revoke permission and verify the overlay closes safely.
- [ ] Background and foreground the app repeatedly; confirm hidden grid previews pause, and visible floating previews remain attached to their existing players.
- [ ] Swipe the activity away / navigate elsewhere and verify the documented service lifecycle; separately force-stop the app and verify no automatic session restoration is claimed.

Manifest preflight already checked in source:
- `INTERNET`, `POST_NOTIFICATIONS`, `SYSTEM_ALERT_WINDOW`, `FOREGROUND_SERVICE`, and `FOREGROUND_SERVICE_MEDIA_PLAYBACK` are declared.
- `MultiStreamPlaybackService` declares `foregroundServiceType="mediaPlayback"`.
- `FloatingStreamsOverlayService` is a regular non-exported service, not a foreground service. Verify its overlay lifetime on each supported Android release; do not assume that binding it to the playback service automatically makes the overlay service itself foreground.

## 2. Stream matrix

Use provider-authorized test streams only. Do not use private credentials in screenshots, logs, or bug reports.

- [ ] One public/test HLS stream over HTTPS.
- [ ] One public/test DASH stream over HTTPS.
- [ ] Two simultaneous streams (HLS + DASH).
- [ ] Four simultaneous streams (mix HLS/DASH where available).
- [ ] Four streams with one provider-authorized Widevine test stream, if a legitimate test license is available.
- [ ] Invalid HTTP URL, malformed URL, invalid license URL, and unreachable host each produce a safe user-facing failure.
- [ ] Confirm a duplicate URL does not create a second player.
- [ ] Confirm a fifth distinct URL is rejected while four players are active.

Record for every run: device model, Android version, stream type, resolution, codec, network type, number of active streams, time to first frame, rebuffer count, and observed errors.

## 3. Lifecycle and recovery

- [ ] Open and close Live Grid repeatedly; player count must not grow for the same URLs.
- [ ] Navigate away and return; verify existing players are reused.
- [ ] Hide the grid; verify muted hidden previews pause.
- [ ] Open the floating overlay; verify it attaches to the existing players (no extra decoders or duplicate network sessions).
- [ ] Close the overlay; verify grid surfaces are restored when the grid is visible.
- [ ] Remove one stream while others continue.
- [ ] Stop all; verify all PlayerViews detach before player release and the notification disappears.
- [ ] Disconnect network, restore network, and record Media3 retry behavior.
- [ ] Cause a terminal playback error; verify the failed player is removed and manual retry creates one replacement.
- [ ] Background/foreground the app during buffering and active playback.
- [ ] Force-stop and relaunch; confirm only persisted stream configuration is restored, not a claim that in-memory playback sessions survived.

## 4. Audio focus

- [ ] All previews start muted.
- [ ] Select one stream for audio; only that stream becomes audible.
- [ ] Switch the selected audio stream; the previous stream becomes muted.
- [ ] Select no audio stream; all previews become muted and focus is abandoned.
- [ ] Trigger transient focus loss, duck request, and permanent focus loss using another audio app/call; verify volume and selected-stream UI recover correctly.

## 5. Performance and stability

Measure at 1, 2, and 4 streams, each for at least 10 minutes:

- [ ] Process memory (PSS / Java heap / native heap where available).
- [ ] Decoder initialization failures and codec resource exhaustion.
- [ ] CPU load, device temperature, and battery drain.
- [ ] Network throughput and rebuffer frequency.
- [ ] UI responsiveness while adding/removing streams and opening the overlay.
- [ ] Confirm no repeated player creation, leaked PlayerViews, or orphaned foreground notification after Stop All.

The configured 8 MiB LoadControl target per player is **not** a hard process-memory ceiling. Record measured memory rather than treating 32 MiB as the total app cost.

## 6. Existing automated-test coverage

The current repository contains:
- JVM tests for encrypted media round trips, tamper detection, and malformed/truncated encrypted payloads.
- An Android instrumentation test that decrypts a test MP4 and decodes a frame with MediaCodec.

Those tests do **not** exercise Live Grid service lifecycle, multi-player concurrency, Widevine license exchange, Android foreground-service restrictions, overlay lifetime, or four-stream performance. They must not be reported as Live Grid acceptance coverage.

## 7. Acceptance gate

Do not mark Live Grid as device-verified until:
1. The one-, two-, and four-stream runs complete on at least one real device.
2. Lifecycle, overlay, Stop All, audio focus, and terminal-error checks pass.
3. Memory, thermal, decoder, and network observations are recorded.
4. Any failures are fixed and the affected checks are repeated.

Attach sanitized logs and measurements to the project issue/PR. Never include Authorization headers, license tokens, or full private stream URLs.
