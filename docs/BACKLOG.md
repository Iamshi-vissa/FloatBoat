# Backlog

Deferred work and known gaps. Phase numbers refer to the build plan.

## Not yet verified

- **Event-driven island on a device.** The state machine, providers, animations and window
  sizing are covered by unit and Robolectric tests but have not yet been confirmed on a
  phone. To check: nothing shows while idle; torch, charging, notifications and calls each
  pop the pill; tap, swipe-up and long-press work; animations are smooth.
- **Cutout positioning on a device.** Same: tests only so far.
- **Foldables and Android 8 to 15.** Only one device (Infinix GT 30 5G, Android 16) has run
  the app so far.

## Deferred

- **Remember the on/off choice** (Phase 7/8). The island's enabled state is not persisted;
  DataStore is introduced with settings. Needed before restart-on-boot.
- **Cutout detection on Android 8 and 9.** The system only exposes the cutout through an
  attached view there, so those versions use the status-bar position plus manual calibration.
- **Fullscreen and landscape behaviour** (Phase 7). The pill stays visible in fullscreen apps
  and games.
- **Haptics toggle** (Phase 3/7). Taps and long-presses always give haptic feedback.
- **Long-press quick actions** (Phase 3). Long-press opens Floatboat for now.
- **Tap-through to the source app** (Phase 5) for notifications and calls.
- **Answer / decline on calls** (Phase 5). Only possible via the calling app's own
  notification actions; not wired up yet.
- **Per-app allow/deny list and rate limiting** for notifications (Phase 5/6). Every
  non-ongoing notification pops the island today, which can be busy in chat-heavy use.
- **Music, timers, Bluetooth providers** (Phases 4 and 5).
- **Split pill for two concurrent events** (Phase 6). Only the top-priority event shows.
- **Off-centre cutouts when expanding.** The window shifts in one step, not animated.
- **Compose UI tests on a device** (Phase 9). No `androidTest` source set yet.
- **ktlint/detekt and release signing** (Phase 9). Only Android lint runs today; the release
  build is minified but unsigned.
- **Continuous integration.** No workflow is set up; the wrapper commands in the README are
  what CI should run.
- **Battery-optimisation and OEM autostart guidance** (Phase 8).

## Known limits

- **The status bar draws on top of the pill.** Android places app overlays below the status
  bar's clock and icons. Where they overlap, the icons show over the pill. Drawing above them
  needs an accessibility-service overlay, which the plan avoids unless truly required.

- On Android 13+ the user can dismiss the foreground-service notification, and on Android 14+
  can stop the service from the system task manager. The island then disappears until turned
  back on in the app.
- Some OEM builds kill foreground services aggressively regardless of `START_STICKY`.
