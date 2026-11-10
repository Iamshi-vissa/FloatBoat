# Backlog

Deferred work and known gaps. Phase numbers refer to the build plan.

## Not yet verified

Only the Phase 1 overlay has been seen working on a phone (Infinix GT 30 5G, Android 16).
Everything below is covered by unit and Robolectric tests but still needs a real run:

- **Event-driven island:** nothing shows while idle; each event pops the pill; tap, swipe-up
  and long-press work; animations are smooth.
- **Cutout positioning:** the pill covers the camera hole without manual adjustment.
- **Music:** correct track, artwork and progress; previous / play-pause / next control the
  player; behaviour with two players open.
- **Calls:** caller shown; Answer and Decline work with the phone's dialer and with
  WhatsApp-style VoIP calls.
- **Open source app:** long-press opens the right screen from the background on Android 14+.
- **Bluetooth:** the pop-up appears on connect once Nearby devices is granted.
- **Hide in fullscreen:** depends on the overlay window receiving status-bar visibility
  changes, which varies by device. Off by default for that reason.
- **Start on boot** on OEM builds with auto-start restrictions.
- **Foldables and Android 8 to 15.**

## Deferred

- **Timers and stopwatch** (Phase 5). These need per-clock-app notification parsing.
- **Split pill for two concurrent events** (Phase 6). Only the top-priority event shows.
- **Themes** (Phase 7): pill colour, corner radius, opacity, accent, Material You, animation
  speed.
- **Bluetooth battery level.** Not available through public APIs.
- **Customisable long-press action** and the wider gesture suite (roadmap Tier A).
- **Cutout detection on Android 8 and 9.** The system only exposes the cutout through an
  attached view there, so those versions use the status-bar position plus manual calibration.
- **Off-centre cutouts when expanding.** The window shifts in one step, not animated.
- **Compose UI tests on a device** (Phase 9). No `androidTest` source set yet.
- **ktlint / detekt** (Phase 9). Only Android lint runs today.
- **Continuous integration.** No workflow is set up; the wrapper commands in the README are
  what CI should run.
- **Roadmap tiers A to C** from the project brief.

## Known limits

- **Call control depends on the calling app.** Answer and Decline only appear when the app
  uses a call-style notification (Android 12+). Floatboat cannot answer or end calls itself.
- **A dismissed music pill returns on the next track** or play/pause, because that is the
  next time the player reports anything.
- **The equaliser bars animate continuously while music plays** and the compact pill is
  visible, which costs a little GPU time; swipe the pill away to stop it.
- **The status bar draws on top of the pill.** Android places app overlays below the status
  bar's clock and icons. Where they overlap, the icons show over the pill. Drawing above them
  needs an accessibility-service overlay, which the plan avoids unless truly required.

- On Android 13+ the user can dismiss the foreground-service notification, and on Android 14+
  can stop the service from the system task manager. The island then disappears until turned
  back on in the app.
- Some OEM builds kill foreground services aggressively regardless of `START_STICKY`.
