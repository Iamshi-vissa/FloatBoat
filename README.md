# FLOATBOAT

An Android app that shows a floating pill at the top of the screen, over whatever app you are
using. Later phases make it expand and collapse in response to live events such as music, calls,
timers, charging and notifications.

Floatboat is built to feel native to Android, to be fully customizable, and to be easy on the
battery.

## STATUS

Phases 0, 1 and 2 of the build plan are done:

- Gradle project with version catalog, Hilt and Compose
- Onboarding screen that walks through the "Display over other apps" permission (and the
  notification permission on Android 13+)
- Foreground service that keeps the pill alive in the background, with a quiet status
  notification and a Stop action
- A black rounded pill drawn over other apps that stays in place across rotation
- Cutout awareness: the pill centres itself on the camera hole and grows to cover it, and
  falls back to the centre of the status bar when there is no cutout on the top edge
- "Position and size" sliders for manual calibration, saved across restarts

Phase 1 was checked on a real phone (Infinix GT 30 5G, Android 16). Phase 2 is covered by
automated tests only so far. See [docs/BACKLOG.md](docs/BACKLOG.md).

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
3. Optionally grant notifications (Android 13+).
4. Turn on **Floating pill**. Leave the app: the pill stays over the camera cutout.
5. If it is not lined up, adjust it with the **Position and size** sliders in the app.
6. Rotate the device: the pill moves to the top centre.
7. Stop it from the switch in the app or the **Stop** button in the notification.

## PROJECT STRUCTURE

```
app/src/main/kotlin/com/floatboat/app/
 ├─ core/cutout/         Cutout detection and pill placement maths
 ├─ core/overlay/        Overlay window management and the Compose host
 ├─ core/permissions/    Overlay and notification permission checks
 ├─ island/ui/           The pill composable
 ├─ services/            Foreground service that owns the overlay
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
| 3     | State machine and animation                       | Next    |
| 4     | Music provider                                    | Planned |
| 5     | Calls, timers, charging, notifications, Bluetooth | Planned |
| 6     | Priority and multi-event handling                 | Planned |
| 7     | Settings and customization                        | Planned |
| 8     | Hardening (battery, boot, OEM limits)             | Planned |
| 9     | Quality (tests, lint, release build)              | Planned |

## PRIVACY

Floatboat declares no internet permission. Everything runs on the device.

## LICENSE

MIT. See [LICENSE](LICENSE).
