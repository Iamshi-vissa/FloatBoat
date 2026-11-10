# FLOATBOAT

An Android app that shows a floating pill at the top of the screen, over whatever app you are
using. Later phases make it expand and collapse in response to live events such as music, calls,
timers, charging and notifications.

Floatboat is built to feel native to Android, to be fully customizable, and to be easy on the
battery.

## STATUS

The pill stays hidden and pops up only when something happens, then goes away again.

What it does today:

| Event | Behaviour |
| --- | --- |
| Music and media | Album art and animated bars while playing; expanded view with title, artist, progress and previous / play-pause / next |
| Calls | Shows the caller while a call is ringing or active; Answer and Decline when the calling app offers them |
| Notifications | Pops up briefly with title and text; per-app mute list |
| Charging | Pops up with the battery level when a charger is plugged in |
| Bluetooth | Pops up with the device name when earbuds, a speaker or a car connects |
| Torch | Shows while the flashlight is on |

- **Gestures:** tap to expand or collapse, swipe up to dismiss, press and hold to open the app
  the event came from.
- **Priorities:** a call takes over from a notification, then Bluetooth, charging, media and
  the torch; the lower one returns when the higher one ends.
- **Cutout awareness:** the pill centres itself on the camera hole and covers it, with
  "Position and size" sliders for manual calibration.
- **Settings:** switch each event type on or off, mute individual apps, vibration, start when
  the phone starts, hide in fullscreen apps and games.
- **Staying alive:** foreground service, restart after reboot, and guidance for battery
  optimisation and OEM auto-start settings.

Checked on a real phone so far: the overlay foundation only (Infinix GT 30 5G, Android 16).
Everything after that is covered by automated tests but not yet confirmed on a device. See
[docs/BACKLOG.md](docs/BACKLOG.md) for what is unverified and what is not built.

## TECH STACK

| Area         | Choice                                          |
| ------------ | ----------------------------------------------- |
| Language     | Kotlin                                          |
| UI           | Jetpack Compose (Material 3) in overlay windows |
| Architecture | MVVM, unidirectional data flow, single module   |
| DI           | Hilt                                            |
| Async        | Kotlin Coroutines and Flow                      |
| Build        | Gradle Kotlin DSL with a version catalog        |
| Testing      | JUnit, MockK, Turbine, Robolectric              |

Minimum supported Android version is 8.0 (API 26). Target and compile SDK is 37.

## REQUIREMENTS

- JDK 17 or newer
- Android SDK with platform `android-37.0` and build tools (Android Studio installs these; the
  build downloads missing build tools itself once licences are accepted)
- Either the `ANDROID_HOME` environment variable or a `local.properties` file in the project
  root containing `sdk.dir=/path/to/Android/sdk`

## BUILD AND TEST

Build a debug APK (output: `app/build/outputs/apk/debug/app-debug.apk`):

```bash
./gradlew assembleDebug
```

Run the unit tests:

```bash
./gradlew testDebugUnitTest
```

Run Android lint:

```bash
./gradlew lintDebug
```

Install on a connected device or emulator:

```bash
./gradlew installDebug
```

## TRYING IT ON A DEVICE

1. Open Floatboat.
2. Tap **Grant** under "Display over other apps" and turn the switch on in system settings,
   then go back.
3. Grant the optional permissions you want: notifications (Android 13+), notification access
   (music, calls and notification pop-ups) and Nearby devices (Bluetooth, Android 12+).
4. Turn on **Floating pill**. Nothing appears yet: the pill is waiting for an event.
5. Play a song: the pill appears over the camera cutout. Tap it for controls.
6. Try the flashlight, a charger, a notification, a call, and connecting earbuds.
7. Swipe the pill up to dismiss it; press and hold to open the source app.
8. If the pill is not lined up with the camera, adjust **Position and size** in the app
   while an event is showing.
9. Stop it from the switch in the app or the **Stop** button in the notification.

If Android greys out the notification-access switch with a "Restricted setting" message, open
the phone's Settings > Apps > Floatboat, tap the three dots at the top right and choose
"Allow restricted settings".

## RELEASE BUILD

`./gradlew assembleRelease` produces a minified APK. To sign it, create `keystore.properties`
in the project root (it is git-ignored):

```
storeFile=/path/to/your.keystore
storePassword=...
keyAlias=...
keyPassword=...
```

Without that file the release APK is built unsigned.

## PROJECT STRUCTURE

```
app/src/main/kotlin/com/floatboat/app/
 ├─ core/cutout/         Cutout detection and pill placement maths
 ├─ core/overlay/        Overlay window management and the Compose host
 ├─ core/permissions/    Overlay and notification permission checks
 ├─ island/              Events, states and the state machine
 ├─ island/ui/           The animated island and its content
 ├─ providers/           Event sources: media, calls, notifications, charging, Bluetooth, torch
 ├─ services/            Foreground service that owns the overlay; notification listener
 ├─ onboarding/          Permission walkthrough screen
 ├─ settings/            Preferences, per-app mute list, position and size calibration
 └─ ui/theme/            Material 3 theme
docs/
 ├─ ARCHITECTURE.md      How the pieces fit together
 └─ BACKLOG.md           Deferred work and known limits
```

## ROADMAP

| Phase | Scope                                             | State   |
| ----- | ------------------------------------------------- | ------- |
| 0     | Project setup                                     | Done    |
| 1     | Overlay foundation                                | Done    |
| 2     | Cutout awareness and positioning                  | Done    |
| 3     | State machine and animation                       | Done    |
| 4     | Music provider                                    | Done    |
| 5     | Calls, timers, charging, notifications, Bluetooth | Done except timers |
| 6     | Priority and multi-event handling                 | Done except split pill |
| 7     | Settings and customization                        | Done except themes |
| 8     | Hardening (battery, boot, OEM limits)             | Done    |
| 9     | Quality (tests, lint, release build)              | Done except ktlint/detekt and on-device UI tests |

## PRIVACY

Floatboat declares no internet permission. Everything runs on the device. With notification
access granted, it reads a notification's app name, title, text and category, and the current
media session's title, artist and artwork, only to show them in the pill. Nothing is stored
except your settings and the list of apps you muted.

## LICENSE

MIT. See [LICENSE](LICENSE).
