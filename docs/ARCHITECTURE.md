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
 │   ├─ IslandEvent                Events, actions, provider signals and IslandState
 │   ├─ IslandStateMachine         Signals + gestures -> IslandState
 │   ├─ IslandPreferences          User choices; decides which events are allowed
 │   ├─ IslandActionHandler        Opens source apps, presses media / call buttons
 │   └─ ui/                        Island (animated pill) and per-event content
 ├─ providers/
 │   ├─ EventProvider              Interface: Flow<IslandSignal>
 │   ├─ MediaProvider              Active media session: metadata, state, transport controls
 │   ├─ NotificationProvider       Notifications and calls from the listener service
 │   ├─ BluetoothProvider          Device connected / disconnected broadcasts
 │   ├─ ChargingProvider           Power connected / disconnected broadcasts
 │   └─ TorchProvider              CameraManager torch callback
 ├─ services/
 │   ├─ FloatboatService           Foreground service; owns overlay and state machine
 │   ├─ FloatboatNotificationListener  Forwards notifications to NotificationProvider
 │   ├─ BootReceiver               Restores the island after reboot or app update
 │   └─ IslandServiceState         Process-wide "is the island switched on" flag
 ├─ onboarding/                    OnboardingViewModel + OnboardingScreen
 ├─ settings/                      Preference and placement repositories (DataStore), view
 │                                 models and the settings cards
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

Momentary events (charging, notifications, Bluetooth, paused media) carry a duration and
remove themselves; the countdown pauses while expanded. Ongoing events (playing media, call,
torch) stay until their source ends them. Swipe-up dismisses the current event and reveals
the next one, if any; a dismissed ongoing event returns the next time its source reports
(for music, the next track or play/pause).

Before a `Show` signal reaches the state machine, the service checks
`IslandPreferences.allows(event)`, which applies the per-type switches and the muted-app list.

Long-press and the expanded island's buttons go through `IslandActionHandler`. It fires the
source app's own `PendingIntent`s (a notification's content intent, a call notification's
answer / decline intents, the media session's activity) or calls the media session's transport
controls. Since Android 14 a background sender must explicitly opt in to lending its
activity-start privilege, which Floatboat holds because it shows an overlay.

`Island` is a pure function of `IslandState`. The overlay window is `WRAP_CONTENT` around it,
so while hidden the window is a 1 dp transparent dot that shows nothing and takes no touches.
Because resizing a window every animation frame stutters, the composable's outer box jumps to
the expanded size (plus slack for spring overshoot) when a morph starts and back to the
compact size when it has finished; the black pill animates inside it.

Providers only register system callbacks while the service collects them, and none of them
poll, so an idle island does no work.

### Media

Reading other apps' media sessions is only permitted for an enabled notification listener, so
`MediaProvider` waits for `NotificationAccessState` (set when the system connects the
listener) and then uses `MediaSessionManager` with the listener's component. It follows the
session that is playing, registers a `MediaController.Callback` on it, and maps each change
through `MediaSignalMapper`: playing shows; a pause right after playing shows once more and
then expires; anything else ends the event. Playback position is never polled by the
provider: the event carries a position snapshot and the expanded UI extrapolates it once a
second, only while it is on screen.

### Notifications and calls

`FloatboatNotificationListener` is bound by the system once the user grants notification
access. It extracts app name, title, text, category and flags and hands them to the
`NotificationProvider` singleton, whose pure `toEvent()` decides what reaches the island:
category `call` becomes an ongoing `Call` event; other non-ongoing notifications become
momentary `Notification` events; our own, ongoing, group-summary and empty notifications are
dropped. Bursts from one app within 1.5 seconds pop the island once.

A non-system app cannot answer or end calls itself. What it can do is fire the intents a
call-style notification (Android 12+) carries for exactly that purpose; when present, the
expanded island shows Answer and Decline. Dialers that do not use call-style notifications get
the caller display only.

### Fullscreen, boot and background limits

- With "Hide in fullscreen" on, the overlay treats a hidden status bar (reported through its
  own window insets) as "a fullscreen app is showing" and renders `Hidden`.
- `BootReceiver` restarts the service after `BOOT_COMPLETED` or an app update when the user
  had the island on, kept "start on boot" enabled, and the overlay permission is still held.
- The app shows a "Keep running in the background" card while battery optimisation applies to
  it, pointing at the system exemption list and at OEM auto-start settings.

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

## Not built yet

- Timer and stopwatch events.
- Two concurrent events side by side (split pill).
- Theme customisation (pill colour, corner radius, opacity, accent, Material You).
- The roadmap tiers in the project brief (live activities, navigation, quick actions,
  widgets, SDK and so on).

## Testing

- Pure logic (`IslandStateMachine`, `IslandPreferences`, `MediaSignalMapper`, `PillPlacement`,
  notification mapping and rate limiting, the repositories, `OnboardingViewModel`) is tested with JUnit, MockK and Turbine.
- `Island` is tested in a real overlay window under Robolectric for the window size in each
  state; `TorchProvider` and `ChargingProvider` against Robolectric's system services.
- `OverlayWindowManager`, `ComposeOverlayHost` and `CutoutDetector` are tested with Robolectric on API 28 and 35,
  which runs the real framework window and Compose code on the JVM. These cover both the
  pre- and post-Android 11 window-context paths.
