---
name: brainstorm
description: >
  Product and technical brainstorming for the Riposte app.
  Generates feature ideas, evaluates feasibility, explores technical approaches,
  and produces structured proposals grounded in the codebase.
  Use when asked to brainstorm, ideate, explore features, or plan new capabilities.
version: 1.0.0
triggers:
  - brainstorm
  - brainstorming
  - feature ideas
  - what should we build
  - ideate
  - product ideas
  - explore features
  - what if we
  - how could we
  - technical approach
---

# Brainstorm Skill

Structured product and technical brainstorming grounded in the Riposte codebase, architecture, and UX philosophy.

---

## Philosophy

```text
┌─────────────────────────────────────────────────────────────┐
│              GROUNDED CREATIVITY, NOT FANTASY                │
│                                                             │
│  • Every idea must survive the "10-second share" test       │
│  • Explore boldly, then reality-check against the codebase  │
│  • Feasibility matters — know the module boundaries         │
│  • Diverge first (quantity), converge second (quality)       │
│  • Technical depth without premature implementation          │
│  • Challenge assumptions — the best ideas break rules       │
│  • User delight > technical elegance                        │
│  • Ship small, learn fast — prefer incremental ideas        │
│                                                             │
│  The goal: ideas worth building, with a clear path to code. │
└─────────────────────────────────────────────────────────────┘
```

---

## Inputs

The skill accepts a **brainstorming prompt** from the user. This can be:

| Input Type | Example | Behavior |
|------------|---------|----------|
| **Open-ended** | "brainstorm new features" | Full diverge → converge cycle |
| **Themed** | "brainstorm ways to improve search" | Scoped to theme, still multi-phase |
| **Technical** | "brainstorm approaches to offline sync" | Skip product ideation, focus on technical exploration |
| **Problem** | "users can't find memes fast enough" | Problem-first ideation |
| **Competitive** | "what can we learn from Tenor/Giphy?" | Inspiration-driven, adapted to Riposte's context |

If the prompt is ambiguous, ask the user to clarify scope before proceeding.

---

## Workflow

```text
1. UNDERSTAND  → Read prompt, classify input type, clarify if needed
2. GROUND      → Explore relevant codebase areas to understand current state
3. DIVERGE     → Generate ideas via parallel multi-model brainstorming
4. CONVERGE    → Score, rank, and cluster ideas
5. DEEPEN      → Flesh out top ideas with technical feasibility
6. DELIVER     → Write structured proposals to session workspace
```

---

## Phase 1: Understand

Parse the user's brainstorming prompt and classify:

- **Scope**: Open / Themed / Technical / Problem / Competitive
- **Breadth**: Broad exploration or focused deep-dive?
- **Constraints**: Any explicit constraints mentioned?

If the scope is unclear, use `ask_user` to clarify before proceeding. Never brainstorm blindly.

### Context to Load

Always read these files to ground the brainstorm:

```text
.github/context/ARCHITECTURE.md    → Module boundaries, data flows
.github/context/PATTERNS.md        → Coding patterns, MVI structure
docs/VISUAL_DESIGN_SPEC.md         → Design system, UX decisions
CHANGELOG.md                       → What's already been built
README.md                          → Feature list, tech stack
```

For themed/technical prompts, also explore the relevant feature or core modules:

```text
feature/{relevant}/                → Current implementation
core/{relevant}/                   → Supporting infrastructure
```

---

## Phase 2: Ground

Before generating ideas, understand what exists:

1. **Read relevant source files** — Use `grep`, `glob`, and `view` to understand the current implementation in the area being brainstormed
2. **Identify constraints** — Module boundaries, existing patterns, tech stack limitations
3. **Note opportunities** — Unused capabilities, partial implementations, TODO comments
4. **Map the user flow** — How does the relevant flow work today?

Write a brief "Current State" summary (3-5 bullets) before proceeding to ideation.

---

## Phase 3: Diverge (Multi-Model Ideation)

Dispatch **two parallel `task` tool calls** with different models and different creative angles. Each generates ideas independently.

```text
task(agent_type: "general-purpose", model: "claude-opus-4.6", prompt: <product-visionary prompt>)
task(agent_type: "general-purpose", model: "gpt-5.3-codex", prompt: <technical-innovator prompt>)
```

Full prompts are in `prompts/product-visionary.md` and `prompts/technical-innovator.md`.

### Idea Quantity Targets

| Scope | Ideas per Model | Total Raw Ideas |
|-------|----------------|-----------------|
| Open-ended | 8-12 | 16-24 |
| Themed | 6-8 | 12-16 |
| Technical | 4-6 | 8-12 |
| Problem | 5-7 | 10-14 |

### Per-Idea Requirements (both models)

Each idea MUST include:
- **Name**: Short, memorable name (2-4 words)
- **Elevator pitch**: One sentence explaining the idea
- **User story**: "As a [user], I want [action] so that [benefit]"
- **Delight factor**: Why would users love this? (1-2 sentences)
- **Complexity gut-check**: Tiny / Small / Medium / Large / Huge

---

## Phase 4: Converge

Merge and score ideas from both models.

### Scoring Rubric

Each idea is scored 1-5 on four dimensions:

| Dimension | Weight | What It Measures |
|-----------|--------|-----------------|
| **User Impact** | 35% | Does this make "find meme → share" faster/better? |
| **Delight** | 25% | Would users smile, share this feature with friends? |
| **Feasibility** | 25% | Can this be built within the existing architecture? |
| **Novelty** | 15% | Is this genuinely new, or a me-too feature? |

### Deduplication & Clustering

- Merge overlapping ideas from both models (keep the richer description)
- Cluster related ideas into themes
- Flag contradictions between models for user input

### Output

Rank all ideas by weighted score. Present:
- **Top 5**: Full exploration in Phase 5
- **Honorable Mentions**: Ideas 6-10 with one-line descriptions
- **Parked**: Interesting but out-of-scope or too complex for now

---

## Phase 5: Deepen

For each top-5 idea, produce a structured mini-proposal:

```markdown
### [Idea Name]

**Pitch**: [one sentence]

**User Story**: As a [user], I want [action] so that [benefit].

**How It Works**:
[2-3 paragraphs describing the user experience]

**Technical Approach**:
- Affected modules: [list]
- Key changes: [bullet list of implementation steps]
- New dependencies: [if any]
- Database changes: [if any — note migration implications]
- Estimated complexity: [Tiny/Small/Medium/Large/Huge]

**Risks & Open Questions**:
- [risk or question 1]
- [risk or question 2]

**Fits the 10-Second Flow?**: [Yes/Partially/No — explain how]

**Score**: Impact [X/5] · Delight [X/5] · Feasibility [X/5] · Novelty [X/5] → **Weighted: [X.X/5]**
```

### Technical Feasibility Check

For each top idea, verify against the codebase:
- Does the module structure support this? (Feature → Core only)
- Does MVI pattern accommodate this flow?
- Are there existing components that can be reused?
- What new database entities/migrations would be needed?
- Would this affect the ML pipeline?

---

## Phase 6: Deliver

### Persist to Session Workspace

Write the full brainstorm output to:

```text
~/.copilot/session-state/<session-id>/files/brainstorm-results.md
```

### File Format

```markdown
# Brainstorm: [Topic]

Generated: [timestamp]
Scope: [Open/Themed/Technical/Problem]
Prompt: "[original user prompt]"

## Current State

- [bullet 1]
- [bullet 2]
- [bullet 3]

## Top 5 Ideas

### 1. [Idea Name] — Score: X.X/5
[full mini-proposal]

### 2. [Idea Name] — Score: X.X/5
[full mini-proposal]

(etc.)

## Honorable Mentions

6. **[Name]**: [one-liner] — Score: X.X/5
7. **[Name]**: [one-liner] — Score: X.X/5
(etc.)

## Parked Ideas

- **[Name]**: [why parked]

## Next Steps

- [ ] Discuss top ideas with user
- [ ] Prototype #1 pick
- [ ] Create GitHub issues for approved ideas
```

### Summary to User

After writing the file, present a concise summary:
- Top 3 ideas with one-line pitches
- Ask which ideas to explore further or prototype

---

## When to Ask the User

**ASK**:
- Scope clarification when the prompt is ambiguous
- Which top ideas to pursue further
- Constraints the user hasn't mentioned (timeline, complexity budget)
- Whether to create GitHub issues for approved ideas

**DON'T ASK**:
- Permission to read the codebase (always do it)
- Whether to use multi-model (always do it for diverge phase)
- Formatting preferences (use the standard format)

---

## Interaction with Brainstorm Agent

This skill is designed to be invoked by the **brainstorm agent** (`.github/agents/brainstorm.md`) or directly by the user. When invoked by the agent:

- The agent provides the initial prompt and context
- The skill executes the full workflow
- Results are returned to the agent for presentation

---

## Known Limitations

- **No competitive analysis data** — can reason about competitor features from general knowledge, but can't access competitor apps or APIs
- **No user analytics** — ideas are based on product reasoning, not usage data
- **Feasibility is estimated** — actual implementation may reveal hidden complexity
- **Single-session** — brainstorm results persist in the session workspace, not in the repo

---

## Stop Criteria

```text
DONE WHEN:
✓ Codebase explored for relevant context
✓ Multi-model ideation completed
✓ Ideas scored, ranked, and clustered
✓ Top 5 ideas have full mini-proposals
✓ Technical feasibility checked against codebase
✓ Results written to session workspace
✓ Summary presented to user with next-step options

ACCEPTABLE PARTIAL:
✓ At least 3 ideas fully explored with feasibility check
```
