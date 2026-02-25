# Quick Verify Prompt (single-model, fast)

Use this after each fix round to validate the re-screenshot.
Dispatch as a single-model task (any model — prefer speed over depth here).

```
task(agent_type: "general-purpose", model: "claude-haiku-4.5", prompt: <quick verify prompt>)
```

---

## Prompt Template

```text
You are doing a quick visual verification — YES or NO answers only.

Screenshot path: [AFTER_SCREENSHOT_PATH]
Use the `view` tool to load and examine the screenshot.

Screen: [SCREEN_NAME] — State: [STATE]

For each issue listed below, answer:
  ✅ RESOLVED — issue is no longer visible
  ❌ STILL PRESENT — issue remains
  ⚠ NEW ISSUE — something unrelated looks wrong now

Issues to verify:
[LIST EACH FIXED ISSUE FROM THE FINDINGS FILE HERE]

After the per-issue checklist:
- Any NEW visual regressions introduced? (YES/NO — if YES, describe briefly)
- Overall impression: BETTER / SAME / WORSE than before fix?

Keep answers short. Evidence only — no elaboration unless NEW ISSUE found.
```

---

## When to Use

- After every fix round rebuild + reinstall
- Skip for the final gate score (Phase 7 uses the full scorecard, not quick verify)
- When the finding was single-source / Low confidence, this check is sufficient to close it

## Notes

- Single source of truth for whether a fix worked is the screenshot, not the code diff
- If quick verify flags a NEW ISSUE, add it to the findings file immediately and decide if it warrants another fix round
- Build failures do NOT count as a fix round — quick verify is only run after a successful build
