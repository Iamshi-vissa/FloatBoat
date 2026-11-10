# Backlog

Deferred work and known gaps. Phase numbers refer to the build plan.

## Not yet verified

- **On-device run.** Phases 0 and 1 were built and tested without an emulator or device (the
  build environment has no hardware virtualisation). The overlay is covered by Robolectric tests,
  but the following still need a real run: pill visible over other apps, position relative to the
  status bar, behaviour on rotation, and the notification's Stop action.

## Deferred

- **Remember the on/off choice** (Phase 7/8). The island's enabled state is not persisted;
  DataStore is introduced with settings. Needed before restart-on-boot.
- **Cutout-aware positioning** (Phase 2). The pill is centred at the top; it is not yet aligned
  to off-centre camera holes, and there is no manual offset or size calibration.
- **Fullscreen and landscape behaviour** (Phase 7). The pill stays visible in fullscreen apps
  and games.
- **Touch handling** (Phase 3). The pill consumes taps on itself but does nothing with them yet.
- **Compose UI tests on a device** (Phase 9). No `androidTest` source set yet.
- **ktlint/detekt and release signing** (Phase 9). Only Android lint runs today; the release
  build is minified but unsigned.
- **Continuous integration.** No workflow is set up; the wrapper commands in the README are
  what CI should run.
- **Battery-optimisation and OEM autostart guidance** (Phase 8).

## Known limits

- On Android 13+ the user can dismiss the foreground-service notification, and on Android 14+
  can stop the service from the system task manager. The island then disappears until turned
  back on in the app.
- Some OEM builds kill foreground services aggressively regardless of `START_STICKY`.
