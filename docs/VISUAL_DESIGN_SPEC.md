# Meme My Mood - Visual Design Specification

> **Final Visual Design v1.0** — January 2026  
> Synthesizes Red Team findings, Devil's Advocate feedback, and user requirements into concrete design decisions.

---

## Design Philosophy

**Pretty & Fast, Not Just Functional**

The gallery should feel like opening a beautifully organized collection, where your most-used stickers greet you instantly and sections have clear visual hierarchy. Every design decision serves the core metric: **time from app-open to share-complete < 5 seconds**.

---

## 1. Grid Density System

### Decision: **Adaptive Columns with User Override**

The grid adapts to screen width with sensible defaults, but users can override in settings for accessibility or preference.

| Screen Width | Default Columns | Min Thumbnail | Touch Target |
|--------------|-----------------|---------------|--------------|
| 360dp–399dp  | 3 columns       | 108dp         | 48dp min     |
| 400dp–479dp  | 4 columns       | 92dp          | 48dp min     |
| 480dp+       | 5 columns       | 88dp          | 48dp min     |

### Implementation Details

```kotlin
object GridDensity {
    // Breakpoints (screen width in dp)
    const val COMPACT_MAX = 399
    const val MEDIUM_MAX = 479
    
    // Column counts by density
    const val COLUMNS_COMPACT = 3
    const val COLUMNS_MEDIUM = 4
    const val COLUMNS_EXPANDED = 5
    
    // Grid spacing
    val GRID_PADDING = 8.dp
    val ITEM_SPACING = 8.dp
    
    // User override values
    enum class UserDensityPreference {
        AUTO,       // Use adaptive columns
        COMPACT,    // Always 3 columns (larger thumbnails)
        STANDARD,   // Always 4 columns
        DENSE       // Always 5 columns (smaller thumbnails)
    }
}
```

### Adaptive Calculation

```kotlin
@Composable
fun rememberGridColumns(
    screenWidthDp: Dp,
    userPreference: UserDensityPreference = AUTO
): Int {
    return when (userPreference) {
        AUTO -> when {
            screenWidthDp < 400.dp -> 3
            screenWidthDp < 480.dp -> 4
            else -> 5
        }
        COMPACT -> 3
        STANDARD -> 4
        DENSE -> 5
    }
}
```

### Accessibility Override

Users with low vision can force 3-column mode (or fewer) from Settings → Display → Grid Density, resulting in **120dp+ thumbnails** on typical phones.

---

## 2. Thumbnail Sizing

### Decision: **Aspect-Aware Sizing with Minimum Guarantees**

| Context | Size | Aspect Ratio | Corner Radius |
|---------|------|--------------|---------------|
| Quick Access Row | 72dp height, aspect-preserved width | Source aspect | 12.dp |
| Main Grid (standard) | Fill column width, aspect-preserved | Source aspect | 12.dp |
| Main Grid (compact/3-col) | Fill column width, capped 120dp | Source aspect | 12.dp |
| Search Results | Same as Main Grid | Source aspect | 12.dp |
| Detail View | Full width, max 400dp height | Source aspect | 0.dp |

### Minimum Touch Target

All thumbnails have a minimum touch target of **48dp × 48dp** per Material 3 accessibility guidelines, even if the visual thumbnail is smaller.

### Implementation

```kotlin
object ThumbnailSizes {
    // Quick Access horizontal row
    val QUICK_ACCESS_HEIGHT = 72.dp
    val QUICK_ACCESS_MAX_WIDTH = 96.dp  // Prevent super-wide stickers from dominating
    
    // Main grid cells
    val MIN_THUMBNAIL_SIZE = 80.dp
    val MAX_THUMBNAIL_HEIGHT = 160.dp   // Cap tall stickers
    
    // Touch target
    val MIN_TOUCH_TARGET = 48.dp
    
    // Corner radius
    val THUMBNAIL_CORNER_RADIUS = 12.dp
}
```

---

## 3. Section Headers & Visual Hierarchy

### Typography Treatment

| Section Type | Typography | Weight | Color Token | Icon |
|--------------|------------|--------|-------------|------|
| Quick Access | `titleSmall` (14sp) | Medium | `onSurfaceVariant` | 🔥 (fire emoji) |
| Pinned | `titleSmall` (14sp) | Medium | `primary` | 📌 (pin emoji) |
| Recent | `titleSmall` (14sp) | Medium | `onSurfaceVariant` | 🕐 (clock emoji) |
| Emoji Group | `titleSmall` (14sp) | Medium | `onSurfaceVariant` | The emoji itself |
| New Imports | `titleSmall` (14sp) | Medium | `tertiary` | ✨ (sparkles) |

### Visual Treatment

```kotlin
@Composable
fun SectionHeader(
    title: String,
    icon: String? = null,  // Emoji or null
    accentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    trailingAction: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Text(
                text = icon,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(end = 8.dp)
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = accentColor,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.weight(1f))
        trailingAction?.invoke()
    }
}
```

### Section Spacing

| Spacing | Value |
|---------|-------|
| Above section header | 16.dp |
| Below section header | 8.dp |
| Between sections | 24.dp |
| Section header horizontal padding | 16.dp |

---

## 4. Emoji Filter UI

### Decision: **Collapsible Chip Grid with Smart Overflow**

The emoji filter appears as a **horizontal row** (up to 8 visible), with a "more" chip that expands to a 4-column grid overlay.

### Default State (Collapsed)

```
┌─────────────────────────────────────────────────────────────────┐
│ [😩][😤][🥺][💀][😂][🔥][😭][+12]                               │
│  ↑ Top 7 by frequency  ↑ "more" chip with count                │
└─────────────────────────────────────────────────────────────────┘
```

### Expanded State (Grid Overlay)

```
┌─────────────────────────────────────────────────────────────────┐
│  All Moods                                               [×]   │
├─────────────────────────────────────────────────────────────────┤
│  [😩][😤][🥺][💀]                                               │
│  [😂][🔥][😭][🤔]                                               │
│  [😏][🤣][😅][🙃]                                               │
│  [😊][🥹][😤][💀]                                               │
│  ...                                                            │
└─────────────────────────────────────────────────────────────────┘
```

### Emoji Chip Specifications

| Property | Active State | Inactive State |
|----------|--------------|----------------|
| Background | `primaryContainer` | `surfaceContainerHigh` |
| Emoji size | 20sp | 20sp |
| Chip height | 36.dp | 36.dp |
| Chip min-width | 44.dp | 44.dp |
| Corner radius | 18.dp (pill) | 18.dp (pill) |
| Border | none | none |
| Spacing between chips | 8.dp | 8.dp |

### "More" Chip

| Property | Value |
|----------|-------|
| Background | `surfaceContainerHighest` |
| Text | `labelMedium` (12sp), color: `onSurfaceVariant` |
| Format | "+N" where N = remaining emoji count |
| Min-width | 44.dp |
| On tap | Expand to grid overlay |

### Multi-Select Behavior

- Tap emoji: Toggle filter (additive)
- Active emojis show filled background (`primaryContainer`)
- Multiple emoji selections use AND logic
- Clear all: "×" button appears when any filter active

### Implementation

```kotlin
@Composable
fun EmojiFilterRail(
    emojis: List<EmojiCount>,
    activeFilters: Set<String>,
    onEmojiToggle: (String) -> Unit,
    onClearAll: () -> Unit,
    maxVisible: Int = 7,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    val visibleEmojis = emojis.take(maxVisible)
    val hiddenCount = (emojis.size - maxVisible).coerceAtLeast(0)
    
    Column(modifier) {
        // Collapsed rail
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            items(visibleEmojis) { emojiCount ->
                EmojiChip(
                    emoji = emojiCount.emoji,
                    isActive = emojiCount.emoji in activeFilters,
                    onClick = { onEmojiToggle(emojiCount.emoji) }
                )
            }
            if (hiddenCount > 0) {
                item {
                    MoreChip(
                        count = hiddenCount,
                        onClick = { isExpanded = true }
                    )
                }
            }
        }
        
        // Expanded grid overlay (ModalBottomSheet or Dropdown)
        if (isExpanded) {
            EmojiGridOverlay(
                emojis = emojis,
                activeFilters = activeFilters,
                onEmojiToggle = onEmojiToggle,
                onDismiss = { isExpanded = false }
            )
        }
    }
}
```

---

## 5. Search Results Styling

### Decision: **Visual Continuity with Relevance Indicators**

Search results use the **same grid layout** as browse, but with these enhancements:

| Enhancement | Description | Visual Treatment |
|-------------|-------------|------------------|
| Match highlighting | Matched terms in title/tags | Bold + `primary` color |
| Relevance score | Visual confidence indicator | Opacity gradient (100% → 70%) |
| Search context label | "X results for 'query'" | `labelLarge` above grid |
| No results state | Helpful guidance | Custom empty state |

### Search Results Header

```kotlin
@Composable
fun SearchResultsHeader(
    query: String,
    resultCount: Int,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = "$resultCount results",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (query.isNotEmpty()) {
            Text(
                text = "for \"$query\"",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
```

### Relevance Opacity

Top results (top 20%) get 100% opacity. Remaining results fade slightly:
- Rank 1-20%: 100% opacity
- Rank 21-50%: 90% opacity  
- Rank 51-100%: 80% opacity

This creates subtle visual hierarchy without hiding content.

---

## 6. Empty States

### New User (No Stickers)

```
┌─────────────────────────────────────────────────────────────────┐
│                                                                 │
│                           📱                                    │
│                     (illustration)                              │
│                                                                 │
│                Your sticker collection                          │
│                    is waiting!                                  │
│                                                                 │
│      Import your favorite reaction images and tag them          │
│      with emojis for lightning-fast searching.                  │
│                                                                 │
│                 ┌─────────────────────┐                         │
│                 │  Import Stickers    │                         │
│                 └─────────────────────┘                         │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### No Search Results

```
┌─────────────────────────────────────────────────────────────────┐
│                                                                 │
│                          🔍                                     │
│                                                                 │
│               No stickers found for                             │
│                  "your query"                                   │
│                                                                 │
│           Try different words or check your                     │
│                   emoji filters.                                │
│                                                                 │
│              ┌────────────────────────┐                         │
│              │  Clear Filters    │                              │
│              └────────────────────────┘                         │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### No Emoji Tag Matches

```
┌─────────────────────────────────────────────────────────────────┐
│                                                                 │
│                          😶                                     │
│                                                                 │
│               No 😩 stickers yet                                │
│                                                                 │
│            You can add emoji tags when viewing                  │
│              any sticker's details.                             │
│                                                                 │
│              ┌────────────────────────┐                         │
│              │    Browse All          │                         │
│              └────────────────────────┘                         │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### Empty State Specifications

| Property | Value |
|----------|-------|
| Icon/Illustration size | 64.dp |
| Icon color | `onSurfaceVariant` at 60% opacity |
| Title | `titleMedium` (16sp), `onSurface` |
| Body text | `bodyMedium` (14sp), `onSurfaceVariant` |
| Max body width | 280.dp (for readability) |
| Vertical spacing (icon → title) | 16.dp |
| Vertical spacing (title → body) | 8.dp |
| Vertical spacing (body → action) | 24.dp |
| Action button | `FilledTonalButton` |

### Implementation

```kotlin
@Composable
fun EmptyState(
    icon: String,  // Emoji
    title: String,
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = icon,
            style = MaterialTheme.typography.displayMedium,
            modifier = Modifier.alpha(0.6f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 280.dp)
        )
        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.height(24.dp))
            FilledTonalButton(onClick = onAction) {
                Text(actionLabel)
            }
        }
    }
}
```

---

## 7. Quick Access Section

### Decision: **Elevated Horizontal Strip with Visual Distinction**

Quick Access is the most important section—it should feel **premium and immediate**.

### Visual Treatment

| Property | Value |
|----------|-------|
| Background | `surfaceContainerLow` |
| Elevation | 1.dp (tonal elevation) |
| Padding (inside) | 12.dp vertical, 16.dp horizontal |
| Margin (outside) | 0.dp (edge-to-edge) |
| Corner radius | 0.dp (full-width strip) |
| Header | "🔥 Quick Access" with `titleSmall` |

### Thumbnail Enhancements

| Property | Value |
|----------|-------|
| Size | 72dp height, aspect-preserved |
| Max width | 96.dp |
| Corner radius | 12.dp |
| Shadow | 2.dp elevation |
| Spacing between items | 12.dp |
| Tap behavior | Single tap = instant share |

### Layout

```
┌─────────────────────────────────────────────────────────────────┐
│ 🔥 Quick Access                                          ⚙️   │
├─────────────────────────────────────────────────────────────────┤
│  ╔════════╗  ╔════════╗  ╔════════╗  ╔════════╗  ╔════════╗    │
│  ║        ║  ║        ║  ║        ║  ║        ║  ║        ║    │
│  ║  img   ║  ║  img   ║  ║  img   ║  ║  img   ║  ║  img   ║→   │
│  ║        ║  ║        ║  ║        ║  ║        ║  ║        ║    │
│  ╚════════╝  ╚════════╝  ╚════════╝  ╚════════╝  ╚════════╝    │
│  ↑ Subtle shadow, rounded corners, horizontal scroll          │
└─────────────────────────────────────────────────────────────────┘
```

### Implementation

```kotlin
@Composable
fun QuickAccessSection(
    memes: List<Meme>,
    onQuickShare: (Long) -> Unit,
    onLongPress: (Long) -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🔥",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Quick Access",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(
                    onClick = onSettingsClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "Quick Access settings",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Sticker row
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(memes, key = { it.id }) { meme ->
                    QuickAccessItem(
                        meme = meme,
                        onTap = { onQuickShare(meme.id) },
                        onLongPress = { onLongPress(meme.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickAccessItem(
    meme: Meme,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .height(72.dp)
            .widthIn(max = 96.dp),
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 2.dp
    ) {
        AsyncImage(
            model = meme.imageUri,
            contentDescription = meme.title ?: meme.fileName,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .combinedClickable(
                    onClick = onTap,
                    onLongClick = onLongPress
                )
        )
    }
}
```

---

## 8. Color Tokens Summary

### Light Theme

| Token | Usage | Value |
|-------|-------|-------|
| `surface` | Main background | `#FFFBFE` |
| `surfaceContainerLow` | Quick Access bg | Dynamic (M3) |
| `surfaceContainerHigh` | Inactive emoji chips | Dynamic (M3) |
| `primary` | Active states, Pinned header | Indigo `#6366F1` |
| `primaryContainer` | Active emoji chip bg | Dynamic (M3) |
| `tertiary` | New imports header | Pink `#EC4899` |
| `onSurface` | Primary text | `#1C1B1F` |
| `onSurfaceVariant` | Secondary text, icons | Dynamic (M3) |

### Dark Theme

| Token | Usage | Value |
|-------|-------|-------|
| `surface` | Main background | `#1C1B1F` |
| `surfaceContainerLow` | Quick Access bg | Dynamic (M3) |
| `surfaceContainerHigh` | Inactive emoji chips | Dynamic (M3) |
| `primary` | Active states, Pinned header | `#818CF8` |
| `primaryContainer` | Active emoji chip bg | Dynamic (M3) |
| `tertiary` | New imports header | `#F472B6` |
| `onSurface` | Primary text | `#E6E1E5` |
| `onSurfaceVariant` | Secondary text, icons | Dynamic (M3) |

---

## 9. Animation & Motion

### Micro-interactions

| Interaction | Animation | Duration | Easing |
|-------------|-----------|----------|--------|
| Emoji chip tap | Scale pulse 1.0 → 0.95 → 1.0 | 150ms | EaseOut |
| Grid item tap | Scale 1.0 → 0.96 | 100ms | EaseOut |
| Hold-to-share progress | Circular fill | User-configured (800-2000ms) | Linear |
| Quick Access tap | Ripple + scale 1.0 → 0.98 | 100ms | EaseOut |
| Section expand | Slide down + fade in | 200ms | EaseOutQuart |
| Empty state appear | Fade in + slide up 16dp | 300ms | EaseOut |

### Spring Physics

```kotlin
object MotionTokens {
    val PressedScale = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium
    )
    
    val QuickBounce = spring<Float>(
        dampingRatio = 0.6f,
        stiffness = 800f
    )
}
```

---

## 10. Accessibility

### Touch Targets

- All interactive elements: **minimum 48dp × 48dp**
- Emoji chips: 44dp with 8dp spacing (effective 52dp)
- Grid items: Full cell width (88dp+)

### Text Scaling

- All text respects user's system font scaling
- Grid adjusts: If font scale > 1.3, force 3-column max
- Section headers remain readable up to 200% scale

### Screen Reader Support

- Grid items: "{title}, sticker. Double-tap to open, hold to share"
- Emoji chips: "{emoji} filter, {active/inactive}"
- Empty states: Read full message with action hint

### Reduced Motion

```kotlin
val LocalReducedMotion = compositionLocalOf { false }

@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        ) == 0f
    }
}
```

---

## 11. Responsive Breakpoints Summary

| Width | Grid | Quick Access | Emoji Rail | Notes |
|-------|------|--------------|------------|-------|
| < 360dp | 2 col | 4 items | 5 + more | Rare, legacy devices |
| 360-399dp | 3 col | 5 items | 6 + more | Small phones |
| 400-479dp | 4 col | 6 items | 7 + more | Standard phones |
| 480-599dp | 5 col | 8 items | 8 + more | Large phones |
| 600dp+ | 6 col | 10 items | 10 + more | Tablets, foldables |

---

## 12. Implementation Priority

### Phase 1: Core Layout (Week 1)
1. ✅ Adaptive grid columns
2. ✅ Section headers with visual hierarchy
3. ✅ Quick Access elevated section
4. ✅ Empty states for new user and no results

### Phase 2: Emoji System (Week 2)
1. ✅ Collapsible emoji rail
2. ✅ Emoji grid overlay for 8+ emojis
3. ✅ Multi-select with clear all

### Phase 3: Polish (Week 3)
1. ✅ Search results header and relevance opacity
2. ✅ Animations and micro-interactions
3. ✅ Accessibility audit and fixes
4. ✅ User density preference in settings

---

## Design Review Checklist

- [ ] Quick Access is visually distinct and feels premium
- [ ] Grid adapts properly at all breakpoints
- [ ] Emoji rail handles 30+ emojis gracefully
- [ ] Empty states are helpful, not dead-ends
- [ ] All touch targets are 48dp minimum
- [ ] Motion feels responsive, not sluggish
- [ ] Works beautifully in both light and dark themes
- [ ] Typography hierarchy is clear and consistent

---

*This specification is the source of truth for visual implementation. All measurements are in dp, all colors use Material 3 tokens, and all typography uses the Material Type Scale.*
