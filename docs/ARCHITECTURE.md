# Floatboat architecture

Single Gradle module (`app`), MVVM with unidirectional data flow, Hilt for dependency injection.
Package root: `com.floatboat.app`.

## What exists today

```
com.floatboat.app
 ├─ FloatboatApp               Hilt application
 ├─ MainActivity               Hosts the onboarding screen, launches permission flows
 ├─ core/
 │   ├─ cutout/
 │   │   ├─ CutoutDetector         Reads screen size, status bar height and top cutout
 │   │   └─ PillPlacement          Pure placement maths (unit-tested)
 │   ├─ overlay/
 │   │   ├─ OverlayWindowManager   The only code that touches WindowManager
 │   │   ├─ ComposeOverlayHost     Lets a ComposeView live outside an Activity
 │   │   └─ OverlayWindowContext   The window context overlays are created and measured with
 │   ├─ di/                        Application coroutine scope
 │   └─ permissions/
 │       └─ PermissionManager      Overlay + notification permission checks and intents
 ├─ island/
 │   ├─ IslandEvent                Events, provider signals and IslandState
 │   ├─ IslandStateMachine         Signals + gestures -> IslandState
 │   └─ ui/                        Island (animated pill) and per-event content
 ├─ providers/
 │   ├─ EventProvider              Interface: Flow<IslandSignal>
 │   ├─ TorchProvider              CameraManager torch callback
 │   ├─ ChargingProvider           Power connected / disconnected broadcasts
 │   └─ NotificationProvider       Notifications and calls from the listener service
 ├─ services/
 │   ├─ FloatboatService           Foreground service; owns overlay and state machine
 │   ├─ FloatboatNotificationListener  Forwards notifications to NotificationProvider
 │   └─ IslandServiceState         Process-wide "is the island switched on" flag
 ├─ onboarding/                    OnboardingViewModel + OnboardingScreen
 ├─ settings/                      PlacementSettings, its repository (DataStore), view model
 │                                 and the "Position and size" card
 └─ ui/theme/                      Material 3 theme for in-app screens
```

## Events, state and the island

Every source implements `EventProvider` and emits `IslandSignal.Show(event)` or
`IslandSignal.End(key)`. Providers are registered with Hilt multibindings
(`ProvidersModule`), so adding a source is one class and one `@Binds @IntoSet` line.

`FloatboatService` merges all provider flows into `IslandStateMachine`, which holds the active
events and publishes one `IslandState`:

- `Hidden` when no event is active. This is the resting state: nothing is drawn.
- `Compact(event)` for the highest-priority active event (ties go to the newest).
- `Expanded(event)` after a tap; it collapses after 6 seconds, on a second tap, or when a
  more important event takes over.

Momentary events (charging, notifications) carry a duration and remove themselves; the
countdown pauses while expanded. Ongoing events (torch, call) stay until their source ends
them. Swipe-up dismisses the current event and reveals the next one, if any.

`Island` is a pure function of `IslandState`. The overlay window is `WRAP_CONTENT` around it,
so while hidden the window is a 1 dp transparent dot that shows nothing and takes no touches.
Because resizing a window every animation frame stutters, the composable's outer box jumps to
the expanded size (plus slack for spring overshoot) when a morph starts and back to the
compact size when it has finished; the black pill animates inside it.

Providers only register system callbacks while the service collects them, and none of them
poll, so an idle island does no work.

### Notifications and calls

`FloatboatNotificationListener` is bound by the system once the user grants notification
access. It extracts app name, title, text, category and flags and hands them to the
`NotificationProvider` singleton, whose pure `toEvent()` decides what reaches the island:
category `call` becomes an ongoing `Call` event; other non-ongoing notifications become
momentary `Notification` events; our own, ongoing, group-summary and empty notifications are
dropped. Call control (answer/decline) is not possible for a non-system app except through the
actions an app puts on its own notification; that is deferred.

## How the island gets on screen

1. The user turns the island on in `MainActivity`, which calls `FloatboatService.start()`.
2. `FloatboatService.onStartCommand` promotes itself to a foreground service (type
   `specialUse`), checks the overlay permission, computes the pill layout (see below), then
   calls `OverlayWindowManager.show(position) { ... }` with the (initially hidden) island.
3. `OverlayWindowManager` creates a `ComposeOverlayHost` and adds its view to the window
   manager as a `TYPE_APPLICATION_OVERLAY` window.
4. On stop or destroy, the service calls `OverlayWindowManager.hide()`, which removes the
   window synchronously and destroys the host.

`IslandServiceState` is updated by the service and observed by `OnboardingViewModel`, so the
switch in the UI reflects reality even when the service is stopped from the notification.

## Where the pill goes

`CutoutDetector.detect()` returns a `ScreenGeometry`: screen width, status bar height, density
and the bounding box of the cutout on the top edge, if any. `PillPlacement.compute()` turns that
plus the user's `PlacementSettings` into a `PillLayout` (size and position in pixels):

- **Top cutout present:** the pill is centred on the camera hole and enlarged if needed so the
  hole is covered with a small margin. Devices report the cutout box differently (a tight box
  around the hole, or one running from the screen edge down to the bottom of the hole), so the
  hole is taken to be as tall as the box's shorter side and to sit at the bottom of the box.
- **No top cutout** (no cutout at all, landscape, Android 8 and 9): centred horizontally and
  vertically centred in the status bar.
- The user's X/Y offsets and size scale are applied last, then the result is clamped on screen.

The service recomputes the layout when settings change, when it receives a configuration
change, and again when the overlay view itself sees the new configuration, so the final layout
always uses up-to-date metrics after a rotation or fold.

`PlacementSettingsRepository` keeps the settings in memory as the source of truth, so the pill
follows the sliders live, and writes to DataStore only after the value has been stable for
300 ms.

## The overlay host

`ComposeView` expects the view tree to provide a `LifecycleOwner`, a `SavedStateRegistryOwner`
and a `ViewModelStoreOwner`. Activities provide these; a service does not, and Compose throws on
attach if one is missing. `ComposeOverlayHost` implements all three:

- The saved-state registry is attached and restored while the lifecycle is still `INITIALIZED`
  (a hard requirement of the API), then the lifecycle moves to `CREATED`.
- Owners are set on the view before it is added to the window.
- `onShown()` moves the lifecycle to `RESUMED`, which is what starts recomposition.
- `destroy()` moves it to `DESTROYED`, which disposes the composition
  (`DisposeOnViewTreeLifecycleDestroyed`) and clears the view-model store.

A host is single-use. Hiding and showing again creates a new one.

## The overlay window

- Type `TYPE_APPLICATION_OVERLAY`, size `WRAP_CONTENT`, so only the pill itself intercepts
  touches; everything around it goes to the app underneath.
- `FLAG_NOT_FOCUSABLE` so the overlay never takes key input or the keyboard.
- `FLAG_LAYOUT_IN_SCREEN | FLAG_LAYOUT_NO_LIMITS` plus the display-cutout mode, so the pill is
  positioned relative to the physical screen and can sit in the status bar area.
- Gravity `TOP | CENTER_HORIZONTAL` with an x offset from the screen centre. Anchoring by the
  centre keeps the window centred on the cutout when its content changes size.
- Application overlays are always drawn below the status bar's own icons. This is an Android
  rule for non-system apps; only an accessibility-service overlay can draw above them.
- On Android 11+ the `WindowManager` comes from a window context
  (`createDisplayContext().createWindowContext()`), which is required for correct metrics from
  a non-Activity context.

Failure handling: `addView` can throw `BadTokenException` or `SecurityException` if the overlay
permission was revoked; `show()` returns `false` and the service stops itself. `updateViewLayout`
and `removeViewImmediate` tolerate the view already being gone.

## Foreground service

- Declared with `foregroundServiceType="specialUse"` and a subtype property, because no standard
  type describes a persistent overlay. Required from Android 14.
- `startForeground` is called on every `onStartCommand` before anything else, including starts
  that will immediately stop, to satisfy the `startForegroundService` contract.
- Notification channel importance is `LOW` (silent, no heads-up).
- `START_STICKY`, so the system restarts the island after a low-memory kill.

## Planned structure

Later phases add:

- More providers: media (with transport controls), timers, Bluetooth.
- `IslandPriorityQueue` with two concurrent items (split pill) and rate limiting.
- `settings/` — theme, behaviour and per-app rules.

## Testing

- Pure logic (`IslandStateMachine`, `PillPlacement`, notification mapping,
  `OnboardingViewModel`, `PlacementSettingsRepository`) is tested with JUnit, MockK and Turbine.
- `Island` is tested in a real overlay window under Robolectric for the window size in each
  state; `TorchProvider` and `ChargingProvider` against Robolectric's system services.
- `OverlayWindowManager`, `ComposeOverlayHost` and `CutoutDetector` are tested with Robolectric on API 28 and 35,
  which runs the real framework window and Compose code on the JVM. These cover both the
  pre- and post-Android 11 window-context paths.
