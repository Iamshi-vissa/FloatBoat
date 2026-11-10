# FLOATBOAT

An Android app that shows a floating pill at the top of the screen, over whatever app you are
using. Later phases make it expand and collapse in response to live events such as music, calls,
timers, charging and notifications.

Floatboat is built to feel native to Android, to be fully customizable, and to be easy on the
battery.

## STATUS

The pill stays hidden and pops up only when something happens, then goes away again.

What works today:

- **Event-driven island:** hidden when idle; shows a compact pill for an event, expands on tap,
  collapses by itself, and can be swiped up to dismiss. Spring animations between states.
- **Torch:** shows while the flashlight is on.
- **Charging:** pops up with the battery level when a charger is plugged in.
- **Notifications:** pops up for new notifications (needs notification access).
- **Calls:** shows while a call notification is active (needs notification access).
- **Priorities:** a call takes over from a notification, which takes over from charging and
  the torch; the lower one returns when the higher one ends.
- **Cutout awareness:** the pill centres itself on the camera hole and covers it, with
  "Position and size" sliders for manual calibration.
- Onboarding for the overlay, notification and notification-access permissions, and a
  foreground service that keeps the island available in the background.

Checked on a real phone so far: the overlay foundation (Infinix GT 30 5G, Android 16). The
event-driven behaviour and cutout positioning are covered by automated tests only. See
[docs/BACKLOG.md](docs/BACKLOG.md).

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
3. Optionally grant notifications (Android 13+) and notification access (for calls and
   notification pop-ups).
4. Turn on **Floating pill**. Nothing appears yet: the pill is waiting for an event.
5. Turn on the flashlight from quick settings: the pill appears over the camera cutout and
   stays while the torch is on.
6. Tap the pill to expand it, tap again to collapse, swipe it up to dismiss, long-press to
   open Floatboat.
7. Plug in a charger, or have a notification or call arrive, to see the other events.
8. If the pill is not lined up with the camera, adjust **Position and size** in the app
   while an event is showing.
9. Stop it from the switch in the app or the **Stop** button in the notification.

## PROJECT STRUCTURE

```
app/src/main/kotlin/com/floatboat/app/
 ├─ core/cutout/         Cutout detection and pill placement maths
 ├─ core/overlay/        Overlay window management and the Compose host
 ├─ core/permissions/    Overlay and notification permission checks
 ├─ island/              Events, states and the state machine
 ├─ island/ui/           The animated island and its content
 ├─ providers/           Event sources: torch, charging, notifications and calls
 ├─ services/            Foreground service that owns the overlay; notification listener
 ├─ onboarding/          Permission walkthrough screen
 ├─ settings/            Saved position and size calibration
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
| 4     | Music provider                                    | Next    |
| 5     | Calls, timers, charging, notifications, Bluetooth | Partly  |
| 6     | Priority and multi-event handling                 | Partly  |
| 7     | Settings and customization                        | Planned |
| 8     | Hardening (battery, boot, OEM limits)             | Planned |
| 9     | Quality (tests, lint, release build)              | Planned |

## PRIVACY

Floatboat declares no internet permission. Everything runs on the device. With notification
access granted, it reads a notification's app name, title, text and category only to show
them in the pill; nothing is stored.

## LICENSE

MIT. See [LICENSE](LICENSE).
