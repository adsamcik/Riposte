---
name: visual-polish
description: >
  Visual quality audit for Riposte in a strict live-device workflow.
  Evaluates the running app (via Android MCP server) with deterministic execution,
  dual-model analysis, and measurable quality gates, then fixes issues directly.
  Use when asked to polish UI, find visual bugs, audit spacing, or improve visual quality.
version: 3.2.0
triggers:
  - polish the UI
  - visual audit
  - fix visual issues
  - pixel perfect
  - visual bugs
  - UI polish
  - wasted space
  - visual quality
  - look and feel
---

# Visual Polish Skill

Runtime visual quality audit. Evaluates what users **actually see**, not just what code describes.

---

## Philosophy

```text
┌─────────────────────────────────────────────────────────────┐
│              INTENTIONAL CRAFT, NOT RIGID PERFECTION         │
│                                                             │
│  • Think like a user in a hurry, not a designer with a ruler│
│  • Functional clarity first, then delight                   │
│  • Playful looseness is fine — unintentional sloppiness isn't│
│  • Fix what you find — reports don't ship                   │
│  • One device screenshot ≠ all devices — note limitations   │
│  • Cap iterations — 3 fix rounds max, then ship             │
│  • Motion/animation is a blind spot — acknowledge it        │
│                                                             │
│  The goal: feels fast, fun, and crafted.                    │
└─────────────────────────────────────────────────────────────┘
```

---

## Operating Mode (Golden Path)

Use a single required mode:

| Mode | When | What's Available |
|------|------|-----------------|
| **Live (required)** | Android MCP server running + device connected | `mobile_take_screenshot`, `mobile_list_elements_on_screen`, `mobile_click_on_screen_at_coordinates`, `mobile_launch_app` |

**No fallback modes.** If Live mode is unavailable, stop and ask the user to connect a device and rerun.

### Golden Path Rule

If a screen is blocked (ANR, crash, missing state), ask the user to unblock it and retry in Live mode.
Do not switch to screenshot analysis or code-only analysis.

### Golden Path Non-Negotiables

1. Exactly one target device is selected for the run.
2. All required critical-path screen states are captured in order.
3. No alternative analysis mode is used.
4. Every 🔴 issue is fixed or explicitly left as a user-approved exception.

---

## Workflow

```text
0. PREFLIGHT CONTRACT → Verify tools, device, runtime, and readiness gates
1. FRESH BUILD        → Build + install APK (unless user says app is current)
2. CAPTURE            → Screenshot + element list in deterministic screen-state order
3. EVALUATE           → Dual-model analysis via parallel task tool calls
4. MERGE              → Deduplicate, resolve conflicts, prioritize
5. PERSIST            → Save findings + run artifacts (manifest, logs, evidence)
6. FIX                → Code changes, batched per screen (max 3 rounds)
6b. REBUILD           → Build + install + relaunch (build failures don't count as a round)
7. VERIFY             → Re-screenshot (Live)
8. SCORE & GATE       → Apply objective pass/fail gates before concluding
```

---

## Phase 0: Preflight Contract (required)

Complete this checklist before building/capturing:

| Check | Pass Criteria | Failure Action |
|------|---------------|----------------|
| Android MCP availability | `mobile_list_available_devices` succeeds | Ask user to enable MCP/device and pause |
| Device selection | Exactly 1 target device selected for this run | Ask user to pick one device and continue only with that device |
| Package target | `com.adsamcik.riposte.debug` is installed or installable | Run build/install, then re-check |
| Runtime normalization | Portrait orientation and app launches to an interactive screen | Normalize runtime (orientation/theme), relaunch, retry once |
| Critical path readiness | Gallery, Detail, Share can be reached in principle | Ask user to unblock data/state preconditions |

### Preflight Output

Persist a run manifest immediately after preflight:

```text
<session-workspace>\files\visual-polish-run-manifest.json
```

Include: session ID, git commit hash, device ID/model, package name, build variant, theme under test, and preflight pass/fail results.
Use the absolute session workspace path from `<session_context>`; do not rely on `~` expansion.

If preflight fails, stop and ask the user to unblock before continuing.
Preflight retries are independent from capture/fix retry budgets.

---

## Phase 1: Fresh Build (Live mode)

Before capturing, ensure the installed APK reflects current code:

```text
1. Build: ./gradlew :app:assembleStandardDebug
2. Install: mobile_install_app(device, "app/build/outputs/apk/standard/debug/app-standard-debug.apk")
3. Launch: mobile_launch_app(device, "com.adsamcik.riposte.debug")
```

Skip only if the user explicitly says the installed app is current and unchanged from local code.

---

## Phase 2: Capture

### Live Mode

```text
1. Launch: mobile_launch_app(device, "com.adsamcik.riposte.debug")
2. Wait up to 8 seconds (poll every 1 second) for first interactive screen
3. mobile_take_screenshot → identify current screen
4. mobile_list_elements_on_screen → spatial data (bounds, coordinates, labels)
5. Navigate: mobile_click_on_screen_at_coordinates(device, x, y)
   → validate expected screen signature before continuing
6. If blocked (dialog, ANR): try mobile_press_button(device, "BACK")
   → if still blocked, ask user to unblock and retry in Live mode
```

### Dark Mode Toggle (Live mode)

Test critical-path screens (Gallery, Detail) in both themes:

```text
# Enable dark mode (via adb shell)
adb shell cmd uimode night yes
# Re-screenshot Gallery + Detail

# Reset to light mode
adb shell cmd uimode night no
```

If the app doesn't visibly change, dynamic colors may be inactive — note in findings.

### Capture Constraint

```text
Keep capture in Live mode for all audited screens.
If a required screen cannot be reached, pause and ask the user to unblock it.
Do not skip required critical-path states.
```

### Deterministic Timing & Retry Policy

| Operation | Time Budget | Max Attempts | Recovery Step |
|-----------|-------------|--------------|---------------|
| Launch app | 8s | 2 | Terminate app → relaunch |
| In-app navigation tap | 5s | 3 | Re-capture elements, retry with updated coordinates |
| Theme toggle transition | 5s | 2 | Re-run toggle command and confirm with screenshot |
| Share/open system UI | 8s | 2 | Back out once, retry entry action |

Screen-state `Max Attempts` values in the matrix below override these generic operation defaults.

### Recovery Ladder (if state is unknown)

```text
1. Press BACK once
2. Re-capture screenshot + elements
3. If still unknown: terminate + relaunch app
4. Return to last verified screen-state checkpoint
5. Retry current step once
6. If still blocked: ask user to unblock and pause
```

### Checkpoint Definition

A screen-state becomes a checkpoint only after all three conditions pass:

1. Expected screen signature is visible
2. Screenshot captured for that state
3. Element list captured for that state

Record each checkpoint in `visual-polish-step-log.ndjson` with `screenStateId`, `timestamp`, and `status=checkpoint`.

### Screen Priority & State Matrix (required execution order)

Audit the **critical path first**. For each screen-state, capture in this order:

| Order | Screen-State ID | Screen | State | How to Reach | Success Signature (example) | Max Attempts |
|------|------------------|--------|-------|--------------|-----------------------------|--------------|
| 1 | gallery-default | Gallery | Default grid | Launch app | Meme grid + emoji/filter controls visible | 3 |
| 2 | gallery-scrolled | Gallery | Scrolled down | Swipe up | New meme cards visible vs previous viewport | 3 |
| 3 | gallery-filtered | Gallery | Filtered by emoji | Tap emoji chip | Selected chip state + filtered results | 3 |
| 4 | detail-default | Meme Detail | Normal view | Tap meme in gallery | Meme preview + share action visible | 3 |
| 5 | detail-share-sheet | Meme Detail | Share sheet visible | Tap share action | System/app share chooser visible | 2 |
| 6 | search-results | Search | Results visible + keyboard open | Tap search, type valid query | Result list + entered query visible | 3 |
| 7 | search-empty | Search | No-results state | Type nonsense query | Empty-state text + no result cards | 3 |
| 8 | import-entry | Import | Import flow entry | Tap import action | Import action sheet/screen visible | 2 |
| 9 | settings-default | Settings | Settings screen | Navigate to settings | Settings list + expected header visible | 2 |

For critical-path states (1-5), also capture in the **opposite theme** (dark/light).

---

## Handling App-Level Blockers

When encountering issues that prevent normal app operation during a visual audit:

### Blocker Types & Actions

| Blocker | Action | Report as Finding? |
|---------|--------|-------------------|
| **ANR Dialog** | Dismiss → continue audit | ✅ Yes — critical UX issue |
| **Crash Dialog** | Restart app → retry once → ask user if persists | ✅ Yes — critical UX issue |
| **Permission Prompts** | Grant permissions → continue | ❌ No — expected behavior |
| **Onboarding Flow** | Complete flow → continue | Only if poorly designed |
| **Login Required** | Ask user for credentials/state setup before proceeding | ❌ No |
| **Empty State (no data)** | Evaluate the empty state design itself | Only if poor empty state |

### Decision Tree

```text
Encounter blocker
├── Can I dismiss/bypass it? (Back button, grant permission, tap through)
│   ├── YES → Dismiss → Continue → Note in findings if it's a UX issue
│   └── NO → Does it block the entire audit or just this screen?
│       ├── Just this screen → Ask user to unblock required state/screen, then retry
│       └── Entire audit → Ask user for help and pause
└── Is this blocker itself a UX problem users would hit?
    ├── YES → Add to findings as 🔴 Critical
    └── NO → Document as operational note only
```

**Key principle:** Visual polish audits assess what users see and experience in the running app. If a blocker creates a poor user experience, it's a finding. If it blocks the audit, ask the user to unblock it rather than switching modes.

---

## Phase 3: Dual-Model Evaluation

Dispatch **two parallel `task` tool calls** with different models. Each evaluates the same screenshot independently.

```text
task(agent_type: "general-purpose", model: "gpt-5.3-codex", prompt: <spatial prompt>)
task(agent_type: "general-purpose", model: "claude-opus-4.6", prompt: <ux prompt>)
```

**When to use dual-model vs single:**

| Screens | Approach |
|---------|----------|
| Gallery, Detail, Share (critical path) | Dual-model |
| All other screens | Single-model (Opus 4.6) |
| Verification re-screenshots | Single-model (any, quick check) |

Full prompts are in `prompts/spatial-analyst.md` and `prompts/ux-critic.md`.

### Key evaluation dimensions across both models:

| Dimension | Spatial Analyst Checks | UX Critic Checks |
|-----------|----------------------|-------------------|
| **Spacing** | Padding consistency, grid alignment, vertical rhythm | Breathing room, density appropriateness |
| **Hierarchy** | Element sizing uniformity | Visual weight matches importance |
| **Touch** | 48dp minimum, target spacing | Thumb zone reachability |
| **Content** | Clipping, overflow, truncation | Readability, meaning loss from truncation |
| **Consistency** | Corner radii, icon sizes, shadows | Cross-screen pattern coherence |
| **Density** | Content-to-chrome ratio | Usable in a hurry? |
| **Emotion** | — | Fun, playful, appropriate for meme app? |
| **Platform** | — | Feels native Android / Material 3? |

### Per-finding requirements (both models)

Each finding MUST include:
- **Evidence**: What specifically is wrong (not vague impressions)
- **Confidence**: High / Medium / Low
- **Location**: Screen area or component
- **Severity**: 🔴 Glaring / 🟡 Noticeable / 🔵 Subtle

---

## Phase 4: Merge Findings

### Conflict Resolution

| Situation | Resolution |
|-----------|------------|
| Both models report same issue | **Consensus** — high confidence, keep richer description |
| Models disagree on severity | Take higher severity ONLY if higher-severity model has High confidence; otherwise use evidence-weighted judgment |
| One model reports, other doesn't | Keep finding, but note it's single-source |
| Models contradict each other | **Ask the user** — don't auto-resolve contradictions |

### Report Format

```markdown
## Screen: [Name] — [State]

**Impression**: [1-2 sentences]
**Device**: [model, if known] — findings may differ on other devices

### 🔴 Glaring (fix immediately)
- [Issue]: [evidence] — Found by: [model(s)], Confidence: [H/M/L]

### 🟡 Noticeable (fix in this pass)
- ...

### 🔵 Subtle (fix if time permits)
- ...

### ✅ Working Well
- ...
```

---

## Phase 4.5: Persist Findings (compaction-safe)

**Immediately** after merging findings — before starting any fixes — write them to a scratch file in the session workspace. This ensures the full evaluation survives context compaction.

### File Location

```text
<session-workspace>\files\visual-polish-findings.md
```

Use the `create` tool (or `edit` if the file already exists from a prior screen) to write the file. The session workspace path is provided in the `<session_context>` block at the start of every conversation.

### File Format

```markdown
# Visual Polish Findings

Generated: [timestamp]
Run ID: [unique run identifier]
Commit: [git commit hash or "dirty/<short-hash>"]
Mode: Live
Device: [device info or N/A]

## Screen: [Name] — [State]

### Status: PENDING | IN_PROGRESS | FIXED | VERIFIED
### Mode: Live

### 🔴 Glaring
- [ ] [Issue]: [evidence] — Source: [model(s)], Confidence: [H/M/L]

### 🟡 Noticeable
- [ ] [Issue]: [evidence] — Source: [model(s)], Confidence: [H/M/L]

### 🔵 Subtle
- [ ] [Issue]: [evidence] — Source: [model(s)], Confidence: [H/M/L]

### ✅ Working Well
- [positive observation]

---

(repeat for each screen evaluated)
```

### Required Run Artifacts

Persist these artifacts for every run:

```text
<session-workspace>\files\visual-polish-run-manifest.json
<session-workspace>\files\visual-polish-step-log.ndjson
<session-workspace>\files\visual-polish-evidence-map.json
```

Store screenshots with stable names for diffability:

```text
before/<screen-state-id>.png
after/<screen-state-id>.png
diff/<screen-state-id>.png
```

### Update Rules

1. **Write immediately after merge** — do not wait until fixes start
2. **Update during fixes** — check off items as they're fixed (`- [ ]` → `- [x]`)
3. **Update after verification** — change screen status to `VERIFIED` and note any new issues
4. **Append step log entries** on every transition/retry/failure
5. **If context feels large**, re-read findings + manifest files instead of relying on memory

### Why This Matters

Context compaction can discard the detailed evaluation findings mid-session. By persisting to a file:
- The agent can re-read findings after compaction and continue fixing without re-evaluating
- Progress tracking (checked/unchecked items) survives across the entire session
- The user can inspect `files/visual-polish-findings.md` at any time to see status

---

## Phase 5: Fix & Verify

### Fix Rules

1. **Re-read findings file first** — if context was compacted, `view` the findings file to restore state
2. **Batch fixes per screen** — don't rebuild after every single fix
3. **Max 3 fix rounds total** (including rounds triggered by failed gates) — diminishing returns are real
4. **Build + install + relaunch** after each batch:
   ```text
   ./gradlew :app:assembleStandardDebug
   mobile_install_app(device, "app/build/outputs/apk/standard/debug/app-standard-debug.apk")
   mobile_launch_app(device, "com.adsamcik.riposte.debug")
   ```
   If the build fails, fix compilation errors first — build failures do NOT count toward the 3-round cap.
5. **Verify**: Re-screenshot in Live mode
6. **Run objective gates after each verification**
7. **Update findings file** — check off fixed items and update screen status

If gates still fail after round 3, stop and report unresolved gates/blockers explicitly.

### Verification (single-model, quick)

```text
"Is the [issue] on [screen] resolved? Any NEW issues introduced? YES/NO only."
```

### Common Fix Patterns

| Issue | Fix Location | Pattern |
|-------|-------------|---------|
| Hardcoded spacing | Composable | → `MaterialTheme.spacing.*` tokens |
| Hardcoded colors | Composable | → `MaterialTheme.colorScheme.*` |
| Small touch target | Modifier chain | → `Modifier.minimumInteractiveComponentSize()` |
| Inconsistent corners | Component | → `MaterialTheme.shapes.*` |
| Wasted space | Screen layout | → Adjust `Arrangement`, padding |
| Clipped text | Text composable | → `maxLines` + `overflow = TextOverflow.Ellipsis` |

---

## Phase 6: Cross-Screen Consistency (final pass)

After individual screens are fixed, do ONE quick pass checking:

**Code-based checks (all audited screens):**
- Theme token usage (`colorScheme`, `typography`, spacing values)
- Modifier patterns and shared component reuse
- Consistent use of design system components

**Visual checks (all audited screens):**
- App bar styling appears identical across screens
- Spacing scale looks consistent
- Typography hierarchy is visually uniform
- Color usage matches across screens
- Empty/loading states share the same visual pattern

This is a single-model pass. Any inconsistency is a 🟡 issue.

---

## Phase 7: Objective Gates & Polish Score

Use a 100-point scorecard (10 gates × 10 points).  
**Hard-fail gates** must pass regardless of total score.

| Gate | Measure | Pass Threshold | Hard-Fail |
|------|---------|----------------|-----------|
| Touch target size | Interactive bounds (px→dp) | Primary actions >= 48x48dp | ✅ |
| Target separation | Distance between adjacent tap targets | >= 8dp and no overlap | ✅ |
| Text clipping | Screenshot + element bounds review | No clipped critical text | ✅ |
| Contrast compliance | Foreground/background ratio | Text >= 4.5:1, large >= 3:1 | ✅ |
| Spacing scale consistency | Mapping to spacing scale | >= 90% values match scale | ❌ |
| Keyline/alignment drift | Edge alignment variance | Typical drift <= 2dp | ❌ |
| Style consistency | Reused component visual parity | No obvious cross-screen mismatch | ❌ |
| Visual regression delta | Before/after diff | No unapproved major delta | ✅ |
| Critical flow budget | Launch→filter→detail→share (3 runs) | Median <= 10s and <= 4 taps | ✅ |
| Critical issue closure | Findings tracker | 0 unresolved 🔴 issues | ✅ |

### Gate Decision

```text
PASS = all hard-fail gates pass AND score >= 85
FAIL = any hard-fail gate fails OR score < 85
```

On FAIL:
- If fix rounds used < 3: do another fix round
- If fix rounds used = 3: stop and explicitly report unresolved gates/blockers

---

## User Flow Audit (Live mode only)

Walk through the critical 10-second flow, screenshotting each step:

```text
App opens → Gallery visible → Tap emoji filter → Grid updates →
Find meme → Tap meme → Detail loads → Share action → Share fires

Questions at each step:
- Is the next action obvious?
- Does the transition feel instant?
- Could the user get lost?
- Is the flow within 10s and 4 taps on the median of 3 runs?
```

---

## When to Ask the User

**ASK**: Design choices with multiple valid directions, contradictions between models, whether something is intentional.

**DON'T ASK**: Objectively wrong things (clipped text, broken alignment), clear Material 3 violations, obvious consistency issues.

---

## Stop Criteria

```text
DONE WHEN:
✓ Preflight contract passed
✓ Critical path screens evaluated (Gallery, Detail, Share)
✓ All 🔴 issues fixed and verified
✓ All 🟡 issues fixed (or documented if out of scope)
✓ Max 3 fix rounds completed
✓ Cross-screen consistency checked
✓ Objective gates passed (no hard-fail violations, score >= 85)
✓ Run artifacts persisted (findings, manifest, step log, evidence map)
✓ Known limitations documented (device-specific, motion blind spot)

ACCEPTABLE PARTIAL:
✓ All hard-fail gates pass except score threshold, with explicit follow-up list
```

---

## Known Limitations

This skill **cannot** evaluate:
- **Animation/motion** — screenshots are static frames
- **Multi-device rendering** — tested on one device only
- **Performance feel** — can time coarse flow steps, but can't measure frame-level jank/input latency
- **Gesture responsiveness** — can't feel tap/swipe feedback
- **Dynamic color** — depends on device wallpaper

Document these as out-of-scope in the final report.
