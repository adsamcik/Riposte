# Git Worktree Workflow for Agents

This document describes the standard git worktree workflow that all agents (human or AI) should follow when making non-trivial changes to the Riposte codebase.

## Why Worktrees?

Worktrees let you work on a feature branch in a separate directory while keeping the main branch clean and available. This is especially important when multiple agents work in parallel — each gets an isolated copy without interfering with others.

## Workflow

### 1. Create a Feature Branch + Worktree

```bash
# From the main repo directory
git worktree add ../meme-my-mood-<feature-name> <branch-name>
```

If the branch doesn't exist yet, create it first:

```bash
git branch fix/my-feature main
git worktree add ../meme-my-mood-my-feature fix/my-feature
```

**Branch naming conventions** (from [CONTRIBUTING.md](../CONTRIBUTING.md)):
- `feature/` — New features
- `fix/` — Bug fixes
- `refactor/` — Code refactoring
- `docs/` — Documentation changes
- `test/` — Test additions/fixes

**Worktree directory naming**: Use `meme-my-mood-<short-name>` as a sibling directory to the main repo.

### 2. Do the Work in the Worktree

```bash
cd ../meme-my-mood-my-feature

# Make changes, run tests, etc.
./gradlew :feature:import:testDebugUnitTest
```

### 3. Commit Changes

Use [Conventional Commits](https://www.conventionalcommits.org/) format:

```bash
git add -A
git commit -m "fix(import): hash original source bytes for duplicate detection

Hash raw URI stream bytes instead of re-encoded JPEG for deterministic
duplicate detection. Identical source files now always produce the same
hash regardless of format or device-specific decoding behavior.

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
```

### 4. Merge Back to Main

```bash
# Switch to the main repo
cd ../meme-my-mood

# Merge the feature branch
git merge fix/my-feature

# Clean up
git worktree remove ../meme-my-mood-my-feature
git branch -d fix/my-feature
```

### 5. Push (if applicable)

```bash
git push origin main
```

## Rules for Agents

1. **Always use a worktree** for changes spanning more than one file or requiring test verification.
2. **Never modify files in the main repo directory** while a worktree is active for the same task.
3. **Run tests in the worktree** before merging back.
4. **One worktree per task** — don't reuse worktrees across unrelated tasks.
5. **Clean up** — remove the worktree and delete the branch after merging.
6. **Don't revert other agents' work** — if you encounter conflicts from parallel work, resolve them or wait and retry.

## Handling Parallel Work

Multiple agents may be working in different worktrees simultaneously. If you encounter build errors that appear caused by another agent's in-progress work:

1. **Don't try to fix them.** The errors may resolve when the other agent completes.
2. **Retry the build** in 5-minute increments for up to 1 hour.
3. Only treat the error as your own if it persists after retries or is clearly caused by your changes.

## Quick Reference

| Step | Command |
|------|---------|
| Create worktree | `git worktree add ../meme-my-mood-X branch-name` |
| List worktrees | `git worktree list` |
| Remove worktree | `git worktree remove ../meme-my-mood-X` |
| Merge to main | `git merge branch-name` |
| Delete branch | `git branch -d branch-name` |
