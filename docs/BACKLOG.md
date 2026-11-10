# Backlog

Deferred work and known gaps. Phase numbers refer to the build plan.

## Not yet verified

- **Phase 2 on a device.** Cutout detection and the calibration sliders are covered by unit and
  Robolectric tests but have not yet been confirmed on a phone. To check: the pill covers the
  camera hole without adjustment, the sliders move and resize it live, and the values survive
  an app restart.
- **Foldables and Android 8 to 15.** Only one device (Infinix GT 30 5G, Android 16) has run
  the app so far.

## Deferred

- **Remember the on/off choice** (Phase 7/8). The island's enabled state is not persisted;
  DataStore is introduced with settings. Needed before restart-on-boot.
- **Cutout detection on Android 8 and 9.** The system only exposes the cutout through an
  attached view there, so those versions use the status-bar position plus manual calibration.
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

- **The status bar draws on top of the pill.** Android places app overlays below the status
  bar's clock and icons. Where they overlap, the icons show over the pill. Drawing above them
  needs an accessibility-service overlay, which the plan avoids unless truly required.

- On Android 13+ the user can dismiss the foreground-service notification, and on Android 14+
  can stop the service from the system task manager. The island then disappears until turned
  back on in the app.
- Some OEM builds kill foreground services aggressively regardless of `START_STICKY`.
