# Floatboat architecture

Single Gradle module (`app`), MVVM with unidirectional data flow, Hilt for dependency injection.
Package root: `com.floatboat.app`.

## What exists today (Phases 0 and 1)

```
com.floatboat.app
 ├─ FloatboatApp               Hilt application
 ├─ MainActivity               Hosts the onboarding screen, launches permission flows
 ├─ core/
 │   ├─ overlay/
 │   │   ├─ OverlayWindowManager   The only code that touches WindowManager
 │   │   ├─ ComposeOverlayHost     Lets a ComposeView live outside an Activity
 │   │   └─ PillPlacement          Pure placement maths (unit-tested)
 │   └─ permissions/
 │       └─ PermissionManager      Overlay + notification permission checks and intents
 ├─ island/ui/
 │   └─ IslandPill                 The resting pill composable and its dimensions
 ├─ services/
 │   ├─ FloatboatService           Foreground service; its lifetime is the island's lifetime
 │   └─ IslandServiceState         Process-wide "is the island showing" flag
 ├─ onboarding/                    OnboardingViewModel + OnboardingScreen
 └─ ui/theme/                      Material 3 theme for in-app screens
```

## How the pill gets on screen

1. The user turns the island on in `MainActivity`, which calls `FloatboatService.start()`.
2. `FloatboatService.onStartCommand` promotes itself to a foreground service (type
   `specialUse`), checks the overlay permission, then calls `OverlayWindowManager.show { IslandPill() }`.
3. `OverlayWindowManager` creates a `ComposeOverlayHost` and adds its view to the window
   manager as a `TYPE_APPLICATION_OVERLAY` window.
4. On stop or destroy, the service calls `OverlayWindowManager.hide()`, which removes the
   window synchronously and destroys the host.

`IslandServiceState` is updated by the service and observed by `OnboardingViewModel`, so the
switch in the UI reflects reality even when the service is stopped from the notification.

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
- Gravity `TOP | CENTER_HORIZONTAL`. The system re-centres the window on rotation; the service
  forwards `onConfigurationChanged` so the top offset is recomputed for the new status bar height.
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

Later phases add, alongside the packages above:

- `island/` — `IslandState`, `IslandStateMachine`, `IslandEvent`, `IslandPriorityQueue`. The
  island UI becomes a pure function of `IslandState`.
- `providers/` — one `EventProvider` (`Flow<IslandEvent>`) per source: media, calls, timers,
  charging, notifications, connectivity.
- `core/cutout/` — `CutoutDetector` to anchor the pill over the camera hole.
- `settings/` — DataStore-backed settings and their UI.

## Testing

- Pure logic (`PillPlacement`, `OnboardingViewModel`) is tested with JUnit, MockK and Turbine.
- `OverlayWindowManager` and `ComposeOverlayHost` are tested with Robolectric on API 28 and 35,
  which runs the real framework window and Compose code on the JVM. These cover both the
  pre- and post-Android 11 window-context paths.
