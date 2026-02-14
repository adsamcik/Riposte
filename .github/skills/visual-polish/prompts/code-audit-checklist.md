# Code-Only Audit Checklist

Use this when no device or screenshots are available. Pure static analysis of Compose code.

---

## Grep Patterns to Check

### Hardcoded Values (should use theme tokens)

```text
# Hardcoded dp values in composables (should use Spacing tokens)
pattern: "\d+\.dp" in feature/**/*.kt and core/ui/**/*.kt

# Hardcoded colors (should use MaterialTheme.colorScheme)
pattern: "Color\(0x" or "Color.Red" etc.

# Hardcoded text sizes (should use MaterialTheme.typography)
pattern: "\d+\.sp" in composable files
```

### Missing Accessibility

```text
# Images without contentDescription
pattern: "Image(" without contentDescription nearby
pattern: "Icon(" without contentDescription nearby
pattern: "AsyncImage(" without contentDescription nearby

# Missing testTag on interactive elements
pattern: "clickable|onClick" without testTag nearby
```

### Compose Anti-Patterns

```text
# collectAsState instead of collectAsStateWithLifecycle
pattern: "collectAsState()" (should be collectAsStateWithLifecycle)

# Modifier not as last parameter
# Check @Composable function signatures

# Hardcoded strings (should use string resources)
pattern: 'Text\("' with hardcoded English strings
```

### Theme Token Compliance

```text
Check that ALL composables use:
- MaterialTheme.colorScheme.* for colors
- MaterialTheme.typography.* for text styles
- MaterialTheme.shapes.* for shapes
- MaterialTheme.spacing.* for spacing (if custom extension exists)
```

---

## Files to Audit (priority order)

1. `feature/gallery/presentation/GalleryScreen.kt`
2. `feature/gallery/presentation/MemeDetailScreen.kt`
3. `feature/share/presentation/ShareScreen.kt`
4. `core/ui/component/MemeCard.kt`
5. `core/ui/component/SearchBar.kt`
6. `core/ui/component/EmojiFilterRail.kt`
7. `core/ui/component/EmojiChip.kt`
8. `core/ui/theme/` (all files — verify token coverage)
9. Remaining feature screens
10. Remaining core/ui components

---

## Output Format

For each finding:
```markdown
- **File**: path:line
- **Issue**: [description]
- **Pattern**: hardcoded-value / missing-a11y / anti-pattern / theme-violation
- **Fix**: [specific code change]
```
