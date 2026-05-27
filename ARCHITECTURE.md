# ApexMapper — Full Architecture + Implementation Plan

## 1. UI ARCHITECTURE

### Layer 1: Floating Mini HUD
- **File**: `ui/FloatingHudService.kt` — ForegroundService with WindowManager overlay
- **Layout**: Compact bar (120x48dp) showing:
  - Profile name (truncated)
  - Runtime status dot (green=ready, yellow=loading, red=error, gray=idle)
  - Play/Stop toggle
  - Menu button (expands palette)
- **Behaviour**: Draggable, edge-snapping, position persisted via DataStore
- **Theme**: Translucent dark card, 12dp corner radius, 85% opacity

### Layer 2: Expanded Tool Palette
- **File**: `ui/ToolPaletteView.kt` — WindowManager overlay, grid layout
- **Grid**: 3 columns × N rows of tool icons
- **Tools**: Touch, Swipe, Scroll, Macro, Joystick, FPS View, Free Look, Keyboard, Keymap, Settings, Diagnostics, Help
- **Behaviour**: Opens on HUD menu tap, closes on back/outside tap, remembers scroll position

### Layer 3: Feature Config Panels
- **Pattern**: Each feature = dedicated Compose BottomSheet or Dialog
- **Navigation**: Palette icon → opens panel → configure → save → close
- **Persistence**: All settings saved to Room (per-profile) or DataStore (global)

---

## 2. FILE-BY-FILE PLAN

### NEW FILES

```
ui/
├── FloatingHudService.kt      — ForegroundService, WindowManager overlay management
├── FloatingHudView.kt         — Mini HUD view (ComposeView or custom View)
├── ToolPaletteView.kt         — Expanded palette grid
├── panels/
│   ├── TouchPanel.kt          — Touch action config
│   ├── SwipePanel.kt          — Swipe/drag config
│   ├── ScrollPanel.kt         — Mouse scroll config
│   ├── MacroPanel.kt          — Macro editor + playback
│   ├── JoystickPanel.kt       — Virtual joystick config
│   ├── FpsViewPanel.kt        — FPS view/aim config
│   ├── FreeLookPanel.kt       — Free look config
│   ├── KeyboardPanel.kt       — Key binding editor
│   ├── KeymapPanel.kt         — Profile/keymap manager
│   ├── SettingsPanel.kt       — Global settings (theme, HUD, diagnostics)
│   ├── DiagnosticsPanel.kt    — Runtime diagnostics viewer
│   └── HelpPanel.kt           — Tutorial / help
├── theme/
│   ├── HudTheme.kt            — Theme data class + presets
│   └── ThemeManager.kt        — Runtime theme application
└── components/
    ├── SliderField.kt         — Reusable slider with label + value
    ├── ToggleField.kt         — Reusable toggle with label
    ├── BindingCapture.kt      — Key/button binding capture widget
    ├── ColorPicker.kt         — Simple color selector
    └── StatusDot.kt           — Runtime status indicator

engine/
├── ActionScheduler.kt         — Timed action queue (tap, hold, swipe, scroll)
├── MacroEngine.kt             — Macro recording, playback, cancellation
├── JoystickEngine.kt          — Virtual joystick math + touch generation
├── FpsViewEngine.kt           — Persistent camera-look sessions
├── ScrollInjector.kt          — Scroll event injection via Shizuku

data/
├── AppSettings.kt             — DataStore-backed global settings
├── HudPosition.kt             — Persisted HUD x/y/edge-snap
├── ThemeConfig.kt             — Persisted theme (color, opacity, size)
└── FeatureConfig.kt           — Per-feature config JSON blobs in profile
```

### MODIFIED FILES

```
engine/RuntimeEngine.kt        — Wire ActionScheduler, MacroEngine, JoystickEngine
service/KeymappingService.kt   — Start FloatingHudService, manage lifecycle
ui/OverlayEditorView.kt        — Refactor to use new panels
data/Models.kt                 — Add feature config fields to GameProfile
data/KeyMapperDatabase.kt      — Migration for new fields
```

---

## 3. FEATURE IMPLEMENTATION DETAILS

### TOUCH (TouchPanel + ActionScheduler)
- Modes: TAP, HOLD, DOWN_UP, LONG_PRESS
- Config: target point (x,y), hold duration, release delay, press delay
- Runtime: ActionScheduler queues touch → PersistentInjector executes
- Reset options: on-edge, joystick-reset, controlling-finger-reset

### SWIPE (SwipePanel + ActionScheduler)
- Config: start point, end point, duration, direction, repeat mode
- Runtime: ActionScheduler generates path → PersistentInjector drags
- Smoothing: bezier interpolation between start/end
- Hold-to-drag: key held = swipe in progress

### SCROLL (ScrollPanel + ScrollInjector)
- Config: sensitivity, repeat rate, direction, smooth mode
- Runtime: ScrollInjector fires scroll events via Shizuku injectInputEvent
- Binding: any key → scroll up/down

### MACRO (MacroEngine)
- Record mode: capture key/touch sequence with timing
- Manual mode: add steps (tap/hold/release/delay) in editor
- Playback: sequential execution on ActionScheduler
- Loop: configurable repeat count or infinite
- Stop: dedicated stop key or re-press macro key
- Storage: per-profile in Room (ActionSequence table)

### JOYSTICK (JoystickEngine)
- Config: center (x,y), radius, dead zone, axis inversion, sensitivity
- Runtime: WASD/DPAD → unit circle → touch point within radius
- Visual: optional overlay showing joystick area + current position
- Reset: on-edge, key release, double-tap

### FPS VIEW (FpsViewEngine)
- Config: sensitivity (X/Y), reset-on-edge, hold/toggle mode
- Runtime: mouse delta → touch drag from center of view frame
- Session: persistent touch pointer that follows processed deltas
- Reset: edge detection releases and re-centers

### FREE LOOK (FpsViewEngine variant)
- Config: hotkey binding, sensitivity, reset behavior
- Runtime: similar to FPS view but with explicit enable/disable key
- Lock indicator: visual dot showing free look is active

### KEYBOARD TYPING (KeyboardPanel)
- Single key mapping with hold/toggle/chord support
- Binding capture: "press any key" dialog
- Modifier support: Shift+key, Ctrl+key
- Repeat suppression: configurable window
- Runtime: RuntimeEngine.processKey() with full state tracking

### DYNAMIC KEYMAP (KeymapPanel)
- Profile list with per-game package association
- Quick switch via floating HUD
- Import/export profiles as JSON
- Layered bindings: base layer + overlay layers
- Fallback profile for unmapped keys

### THEME (SettingsPanel + ThemeManager)
- Preset colors: white, black, red, green, purple, yellow, orange, cyan
- Opacity slider (50%–100%)
- HUD size: small/medium/large
- Applied via HudTheme data class, persisted in DataStore

### DIAGNOSTICS (DiagnosticsPanel)
- Live event log (last 200 events)
- Filter by stage: raw, normalized, engine, injection, pointer
- Runtime state display
- Profile info
- Shizuku status
- Touch pointer count

### HELP (HelpPanel)
- Feature descriptions
- Binding instructions
- Profile usage guide
- Mode explanations

---

## 4. MISSING FEATURES (currently not implemented)

| Feature | Status |
|---------|--------|
| Floating HUD | Not built |
| Tool palette | Not built |
| Touch panel | Not built |
| Swipe panel | Not built |
| Scroll panel | Not built |
| Macro editor | Partial (ActionSequence table exists, no UI or playback) |
| Joystick engine | Not built |
| FPS view engine | Not built |
| Free look | Not built |
| Keyboard binding UI | Not built |
| Profile manager UI | Not built |
| Theme system | Not built |
| Diagnostics viewer | Not built |
| Help/tutorial | Not built |
| Profile auto-switch | Not built |
| DataStore settings | Not built |
| Action scheduler | Not built |
| Scroll injector | Not built |

---

## 5. BUGS / WEAK INPUT BEHAVIOUR

| Issue | Impact |
|-------|--------|
| AccessibilityService fallback only captures keys, not mouse | Mouse unusable without Shizuku |
| GestureInjector uses one-shot gestures | Camera control stutters |
| No action queue | Actions can't be timed/sequenced |
| No macro playback engine | Macros stored but never executed |
| No scroll injection | Scroll mapping is dead |
| D-pad uses touch injection | Not true joystick behaviour |
| Profile loading is async | Race condition on fast key press |
| No edge detection | Aim can drift off-screen |

---

## 6. BUILD CHECKLIST

- [ ] Floating HUD draggable and position-persisted
- [ ] Tool palette opens/closes, all icons functional
- [ ] Touch panel: tap/hold/down-up modes work
- [ ] Swipe panel: configurable swipe with duration
- [ ] Scroll panel: key → scroll mapping works
- [ ] Macro panel: record/playback/stop works
- [ ] Joystick panel: WASD → virtual stick movement
- [ ] FPS view panel: mouse → camera drag
- [ ] Free look panel: hotkey toggle camera look
- [ ] Keyboard panel: key binding capture works
- [ ] Keymap panel: profile switch works
- [ ] Settings panel: theme color changes HUD
- [ ] Diagnostics panel: shows live events
- [ ] Help panel: shows feature descriptions
- [ ] Profile persistence survives app restart
- [ ] Shizuku mode activates when available
- [ ] Accessibility fallback works when Shizuku unavailable
- [ ] No dead buttons or placeholder screens
- [ ] Overlay remains responsive during gameplay
