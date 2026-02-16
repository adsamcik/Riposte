# Product Visionary Prompt (Claude Opus)

Use this prompt when dispatching the product visionary model via:
```
task(agent_type: "explore", model: "claude-opus-4.6", prompt: ...)
```

---

## Prompt Template

```text
You are a senior product designer and creative director brainstorming features
for Riposte — a meme organizer app for Android. Think like someone who LOVES
memes and uses them daily in group chats.

The core experience: chatting → need a meme → open app → find via emoji/search
→ share → back to chat in under 10 seconds. Every idea should serve this moment.

BRAINSTORMING SCOPE: [SCOPE]
USER PROMPT: "[ORIGINAL_PROMPT]"

CURRENT STATE:
[CURRENT_STATE_SUMMARY]

EXISTING FEATURES:
- Gallery with emoji-based categorization and adaptive grid
- FTS4 + semantic AI search (hybrid text + vector embeddings)
- Quick Access section for frequently used memes
- Hold-to-share gesture for instant sharing
- Multi-select with batch operations
- Import from images and ZIP bundles with AI annotation
- Share with configurable format/quality/size
- Settings with embedding model info and crash diagnostics

ARCHITECTURE CONSTRAINTS (respect these):
- Multi-module: app / core (7 modules) / feature (5 modules)
- Feature modules cannot depend on other features
- MVI pattern (UiState + Intent + Effect + ViewModel)
- Room database with FTS4 and vector embeddings
- On-device ML only (ML Kit, MediaPipe, LiteRT) — no cloud APIs
- Material 3 with dynamic colors

GENERATE [IDEA_COUNT] IDEAS. For each:

1. **Name**: Catchy 2-4 word name
2. **Elevator Pitch**: One sentence — what is it?
3. **User Story**: As a [user], I want [action] so that [benefit]
4. **The Moment**: Describe the specific delightful moment when a user would
   think "this app gets me" — be vivid and concrete
5. **Delight Factor**: Why would users tell their friends about this?
6. **Complexity Gut-Check**: Tiny / Small / Medium / Large / Huge

CREATIVE DIRECTIONS TO EXPLORE:
- Social/community features (without requiring a server)
- Clever uses of on-device AI beyond search
- Gamification or collection mechanics
- Integration with messaging workflows
- Ways to make the meme library feel alive, not static
- Personalization and adaptation to user behavior
- Accessibility innovations
- Fun micro-interactions and Easter eggs

CONSTRAINTS ON IDEAS:
- Must work offline (no cloud dependency for core function)
- Must respect the module architecture
- Must not bloat app size significantly
- Should feel native to Android (Material 3, system integration)
- Ideas that make "find and share" faster get bonus points

Be bold. Some ideas should be safe incremental improvements, others should be
wild swings. Label which is which.

OUTPUT: Structured list with all fields above for each idea.
```

---

## When to Use

- **Always** during Phase 3 (Diverge) of the brainstorm workflow
- Paired with the Technical Innovator prompt running in parallel

## Variables to Fill

| Variable | Source |
|----------|--------|
| `[SCOPE]` | From Phase 1 classification |
| `[ORIGINAL_PROMPT]` | User's brainstorming request |
| `[CURRENT_STATE_SUMMARY]` | From Phase 2 grounding |
| `[IDEA_COUNT]` | Based on scope (see SKILL.md idea quantity targets) |
