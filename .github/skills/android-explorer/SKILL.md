---
name: android-explorer
description: >
  Dedicated Android app exploration and evaluation agent for Riposte.
  Interacts with the running app on a real device or emulator via Mobile MCP tools.
  Takes screenshots, navigates screens, types queries, reads elements, and reports findings.
  Use when you need to verify app behavior, test features, or evaluate what users see.
version: 1.0.0
triggers:
  - check the app
  - what does the app show
  - test on emulator
  - test on device
  - verify on device
  - explore the app
  - navigate to
  - what's on screen
  - check the emulator
  - app state
---

# Android Explorer Skill

Dedicated agent for interacting with and evaluating the running Riposte app on a device or emulator. **Read-only from a code perspective** — this agent never modifies source files, builds, or runs shell commands. It only observes and interacts with the running app.

---

## Philosophy

```text
┌─────────────────────────────────────────────────────────────┐
│              OBSERVE, INTERACT, REPORT                       │
│                                                             │
│  • See what the user sees — screenshots are ground truth    │
│  • Navigate like a user — taps, swipes, typing              │
│  • Report precisely — coordinates, element labels, text     │
│  • Never assume — always screenshot before and after        │
│  • Structured findings — severity, evidence, location       │
│  • Minimal actions — don't over-navigate, stay focused      │
│  • Stateless reports — each exploration stands alone        │
│                                                             │
│  The goal: trustworthy eyes on the running app.             │
└─────────────────────────────────────────────────────────────┘
```

---

## Available Tools (ONLY these)

This agent operates with a **restricted toolset**. It can ONLY use:

| Tool | Purpose |
|------|---------|
| `mobile_list_available_devices` | Discover connected devices/emulators |
| `mobile_take_screenshot` | Capture current screen state |
| `mobile_save_screenshot` | Save screenshot to a file path |
| `mobile_list_elements_on_screen` | Get UI element tree with coordinates and labels |
| `mobile_click_on_screen_at_coordinates` | Tap an element at (x, y) |
| `mobile_double_tap_on_screen` | Double-tap at (x, y) |
| `mobile_long_press_on_screen_at_coordinates` | Long-press at (x, y) |
| `mobile_swipe_on_screen` | Swipe in a direction |
| `mobile_type_keys` | Type text into focused input |
| `mobile_press_button` | Press BACK, HOME, etc. |
| `mobile_launch_app` | Launch the app |
| `mobile_terminate_app` | Force-stop the app |
| `mobile_get_screen_size` | Get device screen dimensions |
| `mobile_get_orientation` | Get current orientation |
| `mobile_set_orientation` | Change orientation |

**NOT available** (do not attempt):
- File editing (`edit`, `create`)
- Shell commands (`powershell`)
- Code search (`grep`, `glob`)
- File reading (`view`)
- Git operations
- Build/install commands

---

## Device & App Context

| Property | Value |
|----------|-------|
| Package (debug) | `com.adsamcik.riposte.debug` |
| Package (release) | `com.adsamcik.riposte` |
| Primary device | Use `mobile_list_available_devices` to discover |
| App type | Meme organizer with emoji tags, search, sharing |

---

## Workflow

Every exploration task follows this pattern:

```text
1. DISCOVER    → Find the target device
2. ORIENT      → Screenshot + element list to understand current state
3. NAVIGATE    → Reach the target screen/state
4. INTERACT    → Perform the requested action (tap, type, swipe)
5. OBSERVE     → Screenshot + element list to capture result
6. REPORT      → Structured findings back to the caller
```

---

## Phase 1: Discover

```text
1. Call mobile_list_available_devices
2. Select the appropriate device (prefer emulator for testing, physical for UX eval)
3. If no devices found → STOP and report "No devices available"
4. Record device ID for all subsequent calls
```

If a specific device was requested by the caller, use that device ID directly.

---

## Phase 2: Orient

Before any navigation, always establish current state:

```text
1. mobile_take_screenshot(device) → see what's on screen
2. mobile_list_elements_on_screen(device) → get tappable elements with coordinates
3. Identify: which screen are we on? Is the app running? Is there a dialog/overlay?
```

### Screen Identification

| Screen | Signature Elements |
|--------|-------------------|
| **Gallery** | Meme grid, emoji filter chips, search icon, overflow menu |
| **Meme Detail** | Large meme image, share button, emoji tags |
| **Search** | Search text field, keyboard visible, result list or empty state |
| **Settings** | Settings list items, toggles, preference sections |
| **Import** | Import options (camera, gallery, bundle) |
| **Share Sheet** | System share chooser or app share config |

---

## Phase 3: Navigate

### Navigation Patterns

| From → To | How |
|-----------|-----|
| Any → Gallery | Press BACK until gallery is visible, or launch app |
| Gallery → Search | Tap search icon (usually top bar) |
| Gallery → Meme Detail | Tap any meme card in the grid |
| Gallery → Settings | Tap overflow menu (⋮) → Settings |
| Gallery → Import | Tap import action |
| Search → Gallery | Press BACK |
| Detail → Gallery | Press BACK |
| Settings → Gallery | Press BACK |

### Navigation Safety

```text
1. Before tapping: list_elements to get current coordinates (don't cache old ones)
2. After tapping: take screenshot to verify navigation succeeded
3. If unexpected screen: press BACK once, re-orient, retry once
4. If still lost after retry: report current state and stop
5. Max 5 navigation actions per exploration task
```

---

## Phase 4: Interact

### Typing Text

```text
1. Ensure text field is focused (tap it if needed)
2. mobile_type_keys(device, text, submit=false)  — for search queries
3. mobile_type_keys(device, text, submit=true)   — when Enter should fire
4. Wait 2-3 seconds for results to load
5. Screenshot to capture result
```

### Scrolling

```text
1. mobile_swipe_on_screen(device, direction="up")  — scroll down (content moves up)
2. mobile_swipe_on_screen(device, direction="down") — scroll up
3. Screenshot after scroll to see new content
```

### Clearing State

```text
1. To clear a text field: tap it, select all (triple-tap or long-press), type new text
2. To dismiss keyboard: press BACK
3. To dismiss dialogs: press BACK or tap outside
```

---

## Phase 5: Observe

After every significant action, capture:

```text
1. mobile_take_screenshot(device) → visual evidence
2. mobile_list_elements_on_screen(device) → structural data
```

### What to Report

For each observation, note:

| Aspect | Details |
|--------|---------|
| **Screen** | Which screen we're on |
| **Key elements visible** | Important UI elements and their states |
| **Content** | What text/images are shown (search results, meme count, etc.) |
| **Issues found** | Any visual problems, errors, empty states, unexpected behavior |
| **Element count** | Number of interactive elements on screen |

---

## Phase 6: Report

Structure findings as:

```markdown
## Exploration Report

**Device**: [device ID]
**Task**: [what was requested]
**Starting screen**: [where we began]
**Ending screen**: [where we ended up]

### Actions Taken
1. [action] → [result]
2. [action] → [result]

### Observations
- [observation 1]
- [observation 2]

### Issues Found
- 🔴 [critical issue — app crash, data loss, blocked flow]
- 🟡 [notable issue — unexpected behavior, poor UX]
- 🔵 [minor issue — cosmetic, non-blocking]

### Evidence
- Screenshot at step N shows: [description]
- Element tree at step N contains: [relevant elements]

### Answer
[Direct answer to the caller's question, based on observations]
```

---

## Common Exploration Tasks

### Verify Search Works

```text
1. Navigate to Gallery
2. Tap search icon → verify search screen opens
3. Type a query (e.g., "happy")
4. Wait 3 seconds
5. Screenshot → count results
6. Report: N results found for query "X", describe what's shown
```

### Check Feature Toggle

```text
1. Navigate to Settings
2. Scroll to find the target setting
3. Screenshot → report current state
4. Tap toggle → screenshot → report new state
5. Navigate back → verify the feature behaves differently
```

### Verify Meme Import

```text
1. Navigate to Gallery → Import
2. Screenshot the import options
3. Report available import methods
```

### Test Share Flow

```text
1. Navigate to a meme detail view
2. Tap share button
3. Screenshot the share sheet/config
4. Report available share options
```

---

## Recovery

If something goes wrong:

```text
Level 1: Press BACK once → re-orient
Level 2: Press BACK 3 times → should reach Gallery
Level 3: Terminate app → relaunch → start over
Level 4: Report failure with last known state
```

---

## Constraints

- **Max 15 tool calls per exploration task** — stay focused
- **Always screenshot before AND after actions** — evidence is required
- **Never cache coordinates** — always re-list elements before tapping
- **Report what you see, not what you expect** — no assumptions
- **If the app crashes or ANRs** — report it as a 🔴 finding, don't try to fix it
- **No code changes** — if a bug is found, report it; the caller will fix it

---

## Caller Integration

This skill is designed to be invoked by the main agent via `task` tool calls:

```text
task(
  agent_type: "general-purpose",
  description: "Explore Android app",
  prompt: "Using the android-explorer skill: [specific task]. Device: [device-id]. 
           The app package is com.adsamcik.riposte.debug.
           Use ONLY mobile MCP tools (mobile_take_screenshot, mobile_list_elements_on_screen, 
           mobile_click_on_screen_at_coordinates, mobile_type_keys, mobile_swipe_on_screen, 
           mobile_press_button, mobile_launch_app, mobile_terminate_app).
           Do NOT use shell commands, file editing, or code search tools.
           Follow the exploration workflow: orient → navigate → interact → observe → report."
)
```

The caller should:
1. Provide a clear, specific task
2. Specify the device ID if known
3. State what answer/evidence they need
4. NOT ask the explorer to fix code — only observe and report

---

## Known Limitations

- **Cannot read logs** — no shell/ADB access; if debug output is needed, the caller must check
- **Cannot modify the app** — observation only
- **Cannot install/build** — the caller must ensure the app is installed and current
- **Screenshot timing** — fast animations may be missed; results captured after settle
- **Element tree limitations** — some custom views may not expose accessibility labels
- **No network inspection** — cannot see API calls or network state
