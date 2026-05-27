# ApexMapper — Panda Mouse Pro Architecture Rebuild

## THE PROBLEM
Current codebase is a monolith: KeymappingService owns runtime state, overlay windows, gesture injection, key binding capture, AND profile loading. This is unmaintainable and fragile.

## THE SOLUTION: 5-Layer Architecture

```
Physical Input → InputCapture → RuntimeEngine → ActionExecutor → Gesture Injection
                                        ↑
                              ProfileStore (persistence)
                                        ↑
                              OverlayEditor (visual editor only)
```

---

## PHASE 1 — Expanded Data Models
**Files:** `data/Models.kt`, `data/KeyMapperDatabase.kt`, `data/KeyMapperRepository.kt`

- [ ] Add `BindingGroup` model (movement pad, aim area, fire cluster, utility, macro)
- [ ] Add `holdMode` field to KeyMapping (TAP, HOLD, TOGGLE)
- [ ] Add `repeatPolicy` field (NONE, AUTO_REPEAT, ON_RELEASE)
- [ ] Add `groupId` field to KeyMapping
- [ ] Add `chordKeys` field to KeyMapping (JSON array of keycodes for combos)
- [ ] Add `deadZone`, `smoothing`, `accelerationCurve` fields for mouse/look
- [ ] Add `actionSequence` table for macro steps (separate from KeyMapping)
- [ ] Bump DB version with destructive migration (dev phase, OK)
- [ ] Expand MacroAction: add HOLD, RELEASE, REPEAT, CONDITIONAL types

## PHASE 2 — Runtime Engine (THE CORE)
**New file:** `engine/RuntimeEngine.kt`

- [ ] In-memory profile snapshot (atomic load, no DB queries on key press)
- [ ] Pressed key table: `Map<Int, KeyState>` with down/up/repeat/consumed flags
- [ ] Mouse button table: `Map<Int, Boolean>` (left, right, middle, side)
- [ ] Modifier/chord table: track active combos
- [ ] Mouse delta accumulator with sensitivity, smoothing, dead zone, acceleration
- [ ] Movement vector state (computed from WASD or grouped keys)
- [ ] Injection queue with cooldown/debounce
- [ ] Runtime state machine:
  ```
  States: Idle → ProfileLoading → Ready → InputLocked → AimMode → MacroRunning → Suspended → PermissionMissing
  Transitions: Ready→AimMode, Ready→MacroRunning, MacroRunning→Ready, Ready→PermissionMissing, etc.
  ```
- [ ] `processKeyEvent(KeyEvent): Boolean` — normalise and route
- [ ] `processMouseEvent(deltaX, deltaY, buttons, wheel)` — mouse pipeline
- [ ] Debug logging for every event path

## PHASE 3 — Input Capture Layer
**Refactor:** `service/AccessibilityTouchService.kt`
**New file:** `engine/InputNormalizer.kt`

- [ ] AccessibilityTouchService becomes PURE capture bridge only
- [ ] Receives accessibility key events → dispatches to RuntimeEngine
- [ ] No business logic in this class
- [ ] InputNormalizer: converts raw KeyEvent/MotionEvent into normalized InputEvent
- [ ] Separate handlers: keyDown, keyUp, keyRepeat, mouseMove, mouseDown, mouseUp, scrollWheel
- [ ] Each event becomes a normalized internal event, not directly triggering UI logic

## PHASE 4 — Action Executor Layer
**New file:** `engine/ActionExecutor.kt`

- [ ] Perform actual outputs: tap, press-hold, drag/swipe, camera look, scroll, macro, mode toggle, profile switch
- [ ] Hold gestures: continuous fire, sustained aim drag, movement drag
- [ ] Macro engine runs on dedicated queue with timing, cancellation, interruption, profile context
- [ ] `executeAction(Action, screenMetrics)` — single dispatch point
- [ ] `cancelAllActions()` — stop-all command
- [ ] Chord detection: Shift+W, Ctrl+Q, W+A, Mouse+Key
- [ ] Key states: down, up, repeat, consumed, chord membership
- [ ] Hold logic: movement keys, crouch hold, aim hold, fire hold
- [ ] Debounce: avoid repeated accidental execution on bounce/auto-repeat

## PHASE 5 — Keyboard Runtime (Stabilise First!)
**Refactor:** `service/KeymappingService.kt`

- [ ] Synchronous in-memory snapshot on profile load (not async)
- [ ] Handle key down/up SEPARATELY (not just "isDown" boolean)
- [ ] Add repeat suppression (don't re-fire on auto-repeat unless configured)
- [ ] Add chord detection (multiple keys pressed together)
- [ ] Remove "first D-pad mapping" hack → dedicated movement group with anchor
- [ ] Movement system: compute direction vector from pressed keys, not per-key swipe
- [ ] Debug logging for every key event path

## PHASE 6 — Mouse Runtime (THE MISSING CORE)
**New file:** `engine/MouseProcessor.kt`

- [ ] Capture: deltaX, deltaY, current button state, wheel delta
- [ ] Apply: sensitivity, acceleration curve, smoothing, dead zone, clamping, optional inversion
- [ ] Mouse buttons independent: left click, right click, middle click, side buttons
- [ ] Pointer lock / aim mode: pointer movement → camera movement or swipe translation
- [ ] Scroll as separate action type (not faked as swipe)
- [ ] Aim mode state: UI stops consuming pointer events, engine operates in locked mode

## PHASE 7 — Separate Editor from Engine
**Refactor:** `ui/OverlayEditorView.kt`

- [ ] OverlayEditorView becomes PURE editor
- [ ] Only edits: place nodes, resize, bind keys, select action type, edit sensitivity, configure modes, save
- [ ] No runtime side effects except saving to ProfileStore
- [ ] Editor reads immutable snapshots, doesn't poll runtime state

## PHASE 8 — Dashboard ViewModel Cleanup
**Refactor:** `ui/DashboardViewModel.kt`

- [ ] Profile selection controller only
- [ ] Service start/stop
- [ ] Permission monitoring
- [ ] Status indicators
- [ ] Does NOT know injection details

## PHASE 9 — Service Cleanup
**Refactor:** `service/KeymappingService.kt`

- [ ] Strip UI responsibilities out over time
- [ ] Keep: active profile snapshot, receive normalized input events, execute mapping actions, host foreground service
- [ ] Expose state to UI via StateFlows
- [ ] Not a dumping ground for overlay logic

## PHASE 10 — Profile Management
- [ ] Per-game profiles with package-name targeting
- [ ] Quick switch between profiles
- [ ] Fallback default profile
- [ ] Auto-switch based on foreground app (future)

---

## NEW FILE STRUCTURE
```
com.example/
├── data/
│   ├── Models.kt              (expanded: BindingGroup, ActionSequence, RuntimeState)
│   ├── KeyMapperDatabase.kt   (v2 schema)
│   └── KeyMapperRepository.kt (expanded queries)
├── engine/                    ← NEW LAYER
│   ├── RuntimeEngine.kt       (state machine, snapshot, event routing)
│   ├── InputNormalizer.kt     (raw events → normalized InputEvent)
│   ├── ActionExecutor.kt      (tap, swipe, hold, macro, movement)
│   ├── MouseProcessor.kt      (mouse delta, sensitivity, pointer lock)
│   └── RuntimeState.kt        (enum states, transition logic)
├── service/
│   ├── KeymappingService.kt   (slim: foreground service + engine host)
│   └── AccessibilityTouchService.kt (slim: capture bridge only)
├── ui/
│   ├── DashboardScreen.kt     (unchanged mostly)
│   ├── DashboardViewModel.kt  (slim: profile + permissions only)
│   ├── OverlayEditorView.kt   (pure editor, no runtime)
│   └── theme/                 (unchanged)
└── MainActivity.kt            (unchanged)
```

---

## VERIFICATION CHECKLIST (per binding)
1. ✅ Binding saved
2. ✅ Binding loaded into snapshot
3. ✅ Input event received
4. ✅ Input event matched
5. ✅ Action state valid
6. ✅ Injection succeeded
7. ✅ Game accepted it

---

## BUILD STRATEGY
- All code changes via GitHub Codespaces (can't build on ARM phone)
- Push to `kalukabap/keymapper` repo
- Build APK in Codespaces
- Download to phone for testing
- Each phase should be testable independently
