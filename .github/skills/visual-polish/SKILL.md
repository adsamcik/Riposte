---
name: visual-polish
description: >
  Visual quality audit for Riposte. Evaluates the running app (via Android MCP server)
  or user-provided screenshots using dual-model analysis, then fixes issues directly.
  Gracefully degrades to code-only analysis when no device is available.
  Use when asked to polish UI, find visual bugs, audit spacing, or improve visual quality.
version: 2.0.0
triggers:
  - polish the UI
  - visual audit
  - fix visual issues
  - pixel perfect
  - visual bugs
  - UI polish
  - wasted space
  - visual quality
  - screenshot review
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

## Operating Modes

Select mode based on available tools:

| Mode | When | What's Available |
|------|------|-----------------|
| **Live** | Android MCP server running + device connected | `get_screenshot`, `get_uilayout`, `execute_adb_command` |
| **Screenshot** | User provides screenshots in a folder | `view` tool reads images from disk |
| **Code-only** | No device, no screenshots | Static analysis of Compose code for theme compliance |

**Always attempt Live mode first.** If MCP tools are unavailable, ask the user:
"No Android device detected. Should I (A) analyze screenshots you provide, or (B) do a code-only audit?"

---

## Workflow

```text
1. DETECT MODE  → Check if Android MCP tools are available
2. CAPTURE      → Screenshot + layout dump (Live) or read images (Screenshot)
3. EVALUATE     → Dual-model analysis via parallel task tool calls
4. MERGE        → Deduplicate, resolve conflicts, prioritize
5. PERSIST      → Write merged findings to scratch file (survives compaction)
6. FIX          → Code changes, batched per screen (max 3 rounds)
7. VERIFY       → Re-screenshot (Live) or user confirms (Screenshot/Code-only)
```

---

## Phase 1: Capture

### Live Mode

```text
1. Launch: execute_adb_command("adb shell am start -n com.adriantache.mememood/.MainActivity")
2. Wait, then: get_screenshot → save mental note of screen name
3. Also: get_uilayout → objective spatial data (bounds, coordinates)
4. Navigate to next state via execute_adb_command("adb shell input tap X Y")
```

### Screenshot Mode

```text
1. User places screenshots in docs/screenshots/ or specifies a path
2. Use `view` tool to read each image file
3. User labels which screen/state each image represents
```

### Code-Only Mode

```text
1. Grep for visual anti-patterns (see prompts/code-audit-checklist.md)
2. Read composables and check theme token usage
3. No visual evaluation — structural analysis only
```

### Screen Priority Order

Audit the **critical path first**, not alphabetically:

```text
1. Gallery (populated)     ← users see this 90% of the time
2. Gallery (empty state)   ← first-time users see this
3. Meme Detail             ← second most visited
4. Share UI                ← the money moment
5. Search (with results)   ← core functionality
6. Search (no results)     ← common frustration point
7. Import                  ← one-time flow
8. Settings                ← lowest priority
```

---

## Phase 2: Dual-Model Evaluation

Dispatch **two parallel `task` tool calls** with different models. Each evaluates the same screenshot independently.

```text
task(agent_type: "general-purpose", model: "gpt-5.3-codex", prompt: <spatial prompt>)
task(agent_type: "general-purpose", model: "claude-opus-4", prompt: <ux prompt>)
```

**When to use dual-model vs single:**

| Screens | Approach |
|---------|----------|
| Gallery, Detail, Share (critical path) | Dual-model |
| All other screens | Single-model (Opus) |
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

## Phase 3: Merge Findings

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

## Phase 3.5: Persist Findings (compaction-safe)

**Immediately** after merging findings — before starting any fixes — write them to a scratch file in the session workspace. This ensures the full evaluation survives context compaction.

### File Location

```text
~/.copilot/session-state/<session-id>/files/visual-polish-findings.md
```

Use the `create` tool (or `edit` if the file already exists from a prior screen) to write the file. The session workspace path is provided in the `<session_context>` block at the start of every conversation.

### File Format

```markdown
# Visual Polish Findings

Generated: [timestamp]
Mode: [Live / Screenshot / Code-only]
Device: [device info or N/A]

## Screen: [Name] — [State]

### Status: PENDING | IN_PROGRESS | FIXED | VERIFIED

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

### Update Rules

1. **Write immediately after merge** — do not wait until fixes start
2. **Update during fixes** — check off items as they're fixed (`- [ ]` → `- [x]`)
3. **Update after verification** — change screen status to `VERIFIED` and note any new issues
4. **If context feels large**, re-read this file instead of relying on memory

### Why This Matters

Context compaction can discard the detailed evaluation findings mid-session. By persisting to a file:
- The agent can re-read findings after compaction and continue fixing without re-evaluating
- Progress tracking (checked/unchecked items) survives across the entire session
- The user can inspect `files/visual-polish-findings.md` at any time to see status

---

## Phase 4: Fix & Verify

### Fix Rules

1. **Re-read findings file first** — if context was compacted, `view` the findings file to restore state
2. **Batch fixes per screen** — don't rebuild after every single fix
3. **Max 3 fix rounds total** — diminishing returns are real
4. **Build once per batch**: `./gradlew :app:assembleStandardDebug`
5. **Verify**: Re-screenshot (Live) or inform user to check (other modes)
6. **Update findings file** — check off fixed items and update screen status

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

## Phase 5: Cross-Screen Consistency (final pass)

After individual screens are fixed, do ONE quick pass through all screens checking:

- App bar styling matches across screens
- Spacing scale is consistent
- Typography hierarchy is consistent
- Color usage is consistent
- Empty/loading states share the same pattern

This is a single-model pass. Any inconsistency is a 🟡 issue.

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
```

---

## When to Ask the User

**ASK**: Design choices with multiple valid directions, contradictions between models, whether something is intentional.

**DON'T ASK**: Objectively wrong things (clipped text, broken alignment), clear Material 3 violations, obvious consistency issues.

---

## Stop Criteria

```text
DONE WHEN:
✓ Critical path screens evaluated (Gallery, Detail, Share)
✓ All 🔴 issues fixed and verified
✓ All 🟡 issues fixed (or documented if out of scope)
✓ Max 3 fix rounds completed
✓ Cross-screen consistency checked
✓ Known limitations documented (device-specific, motion blind spot)

ACCEPTABLE PARTIAL:
✓ All 🔴 issues fixed on critical path screens
```

---

## Known Limitations

This skill **cannot** evaluate:
- **Animation/motion** — screenshots are static frames
- **Multi-device rendering** — tested on one device only
- **Performance feel** — can't measure frame drops or jank
- **Gesture responsiveness** — can't feel tap/swipe feedback
- **Dynamic color** — depends on device wallpaper

Document these as out-of-scope in the final report.
