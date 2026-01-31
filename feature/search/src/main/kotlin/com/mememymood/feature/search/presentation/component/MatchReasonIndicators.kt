package com.mememymood.feature.search.presentation.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mememymood.core.model.MatchReason

/**
 * Animated badge showing the relevance percentage and match reason indicators.
 *
 * Shows a colored percentage badge with icons for each match type.
 * Animates on appearance with a scale and fade effect.
 *
 * @param percent Relevance percentage (0-100).
 * @param matchReasons List of reasons why the result matched.
 * @param modifier Modifier for the composable.
 * @param showReasonIcons Whether to show individual match reason icons.
 * @param expanded Whether to show expanded view with labels.
 */
@Composable
fun AnimatedRelevanceBadge(
    percent: Int,
    matchReasons: List<MatchReason>,
    modifier: Modifier = Modifier,
    showReasonIcons: Boolean = true,
    expanded: Boolean = false,
) {
    val animatedScale = remember { Animatable(0.8f) }

    LaunchedEffect(Unit) {
        animatedScale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium,
            ),
        )
    }

    val backgroundColor by animateColorAsState(
        targetValue = when {
            percent >= 80 -> MaterialTheme.colorScheme.primary
            percent >= 60 -> MaterialTheme.colorScheme.secondary
            percent >= 40 -> MaterialTheme.colorScheme.tertiary
            else -> MaterialTheme.colorScheme.surfaceVariant
        },
        animationSpec = tween(durationMillis = 300),
        label = "badge_color",
    )

    val contentColor = when {
        percent >= 40 -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val semanticDescription = buildString {
        append("$percent% match")
        if (matchReasons.isNotEmpty()) {
            append(", matched by: ")
            append(matchReasons.joinToString(", ") { it.label })
        }
    }

    Row(
        modifier = modifier
            .graphicsLayer {
                scaleX = animatedScale.value
                scaleY = animatedScale.value
                alpha = animatedScale.value
            }
            .semantics { contentDescription = semanticDescription },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Percentage badge
        Box(
            modifier = Modifier
                .background(
                    color = backgroundColor.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(4.dp),
                )
                .padding(horizontal = 6.dp, vertical = 2.dp),
        ) {
            Text(
                text = "${percent}%",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = contentColor,
            )
        }

        // Match reason indicators
        AnimatedVisibility(
            visible = showReasonIcons && matchReasons.isNotEmpty(),
            enter = expandHorizontally() + fadeIn(),
            exit = shrinkHorizontally() + fadeOut(),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                matchReasons.take(3).forEachIndexed { index, reason ->
                    MatchReasonIcon(
                        reason = reason,
                        delayMs = index * 50L,
                        showLabel = expanded,
                    )
                }
                if (matchReasons.size > 3) {
                    Text(
                        text = "+${matchReasons.size - 3}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * Individual match reason icon with optional label.
 */
@Composable
private fun MatchReasonIcon(
    reason: MatchReason,
    modifier: Modifier = Modifier,
    delayMs: Long = 0L,
    showLabel: Boolean = false,
) {
    val animatedScale = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(delayMs)
        animatedScale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow,
            ),
        )
    }

    val iconColor = when (reason) {
        is MatchReason.TitleMatch -> MaterialTheme.colorScheme.primary
        is MatchReason.TextMatch -> MaterialTheme.colorScheme.secondary
        is MatchReason.EmojiMatch -> MaterialTheme.colorScheme.tertiary
        is MatchReason.SemanticMatch -> MaterialTheme.colorScheme.primary
        is MatchReason.TagMatch -> MaterialTheme.colorScheme.secondary
    }

    Row(
        modifier = modifier
            .scale(animatedScale.value)
            .background(
                color = iconColor.copy(alpha = 0.15f),
                shape = RoundedCornerShape(4.dp),
            )
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = reason.icon,
            style = MaterialTheme.typography.labelSmall,
        )
        if (showLabel) {
            Spacer(Modifier.width(2.dp))
            Text(
                text = reason.label,
                style = MaterialTheme.typography.labelSmall,
                color = iconColor,
            )
        }
    }
}

/**
 * Displays text with matched terms highlighted.
 *
 * Highlights all occurrences of the query terms with a bold font weight
 * and a subtle background color.
 *
 * @param text The full text to display.
 * @param matchedTerms List of terms to highlight.
 * @param modifier Modifier for the composable.
 * @param style Text style to apply.
 * @param maxLines Maximum number of lines to display.
 * @param highlightColor Background color for highlighted terms.
 */
@Composable
fun HighlightedMatchText(
    text: String,
    matchedTerms: List<String>,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    maxLines: Int = Int.MAX_VALUE,
    highlightColor: Color = MaterialTheme.colorScheme.primaryContainer,
) {
    val annotatedString = remember(text, matchedTerms) {
        buildAnnotatedString {
            if (matchedTerms.isEmpty()) {
                append(text)
                return@buildAnnotatedString
            }

            var currentIndex = 0
            val lowercaseText = text.lowercase()
            val sortedTerms = matchedTerms.sortedByDescending { it.length }

            while (currentIndex < text.length) {
                var matched = false

                for (term in sortedTerms) {
                    if (term.isBlank()) continue
                    val lowercaseTerm = term.lowercase()
                    val matchIndex = lowercaseText.indexOf(lowercaseTerm, currentIndex)

                    if (matchIndex == currentIndex) {
                        // Append highlighted match
                        withStyle(
                            SpanStyle(
                                fontWeight = FontWeight.Bold,
                                background = highlightColor,
                            ),
                        ) {
                            append(text.substring(matchIndex, matchIndex + term.length))
                        }
                        currentIndex += term.length
                        matched = true
                        break
                    }
                }

                if (!matched) {
                    // Find next potential match
                    var nextMatchIndex = text.length
                    for (term in sortedTerms) {
                        if (term.isBlank()) continue
                        val idx = lowercaseText.indexOf(term.lowercase(), currentIndex)
                        if (idx != -1 && idx < nextMatchIndex) {
                            nextMatchIndex = idx
                        }
                    }

                    // Append text up to next match (or end)
                    if (nextMatchIndex > currentIndex) {
                        append(text.substring(currentIndex, nextMatchIndex))
                        currentIndex = nextMatchIndex
                    } else {
                        append(text.substring(currentIndex))
                        break
                    }
                }
            }
        }
    }

    Text(
        text = annotatedString,
        style = style,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
        color = LocalContentColor.current,
    )
}

/**
 * Row of matched emoji tags with highlight effect.
 *
 * Shows the matched emojis prominently with a subtle pulsing animation
 * and indicator dot.
 *
 * @param allEmojis All emoji tags on the meme.
 * @param matchedEmojis The emojis that matched the search.
 * @param modifier Modifier for the composable.
 * @param maxDisplay Maximum number of emojis to display before truncating.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HighlightedEmojiRow(
    allEmojis: List<String>,
    matchedEmojis: List<String>,
    modifier: Modifier = Modifier,
    maxDisplay: Int = 5,
) {
    val matchedSet = matchedEmojis.toSet()

    // Sort emojis so matched ones appear first
    val sortedEmojis = remember(allEmojis, matchedEmojis) {
        allEmojis.sortedByDescending { it in matchedSet }
    }

    val displayEmojis = sortedEmojis.take(maxDisplay)
    val remainingCount = allEmojis.size - maxDisplay

    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        displayEmojis.forEach { emoji ->
            val isMatched = emoji in matchedSet
            HighlightedEmojiChip(
                emoji = emoji,
                isMatched = isMatched,
            )
        }

        if (remainingCount > 0) {
            Text(
                text = "+$remainingCount",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

/**
 * Individual emoji chip with highlight animation for matched emojis.
 */
@Composable
private fun HighlightedEmojiChip(
    emoji: String,
    isMatched: Boolean,
    modifier: Modifier = Modifier,
) {
    val scale by animateFloatAsState(
        targetValue = if (isMatched) 1.15f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "emoji_scale",
    )

    val backgroundColor by animateColorAsState(
        targetValue = if (isMatched) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            Color.Transparent
        },
        animationSpec = tween(durationMillis = 200),
        label = "emoji_bg_color",
    )

    val borderColor by animateColorAsState(
        targetValue = if (isMatched) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outlineVariant
        },
        animationSpec = tween(durationMillis = 200),
        label = "emoji_border_color",
    )

    Box(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(6.dp))
            .background(backgroundColor)
            .border(
                width = if (isMatched) 1.5.dp else 0.5.dp,
                color = borderColor,
                shape = RoundedCornerShape(6.dp),
            )
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            // Match indicator dot for matched emojis
            if (isMatched) {
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape,
                        ),
                )
            }
            Text(
                text = emoji,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/**
 * Compact match reason summary showing icons inline with text.
 *
 * @param matchReasons List of match reasons to display.
 * @param modifier Modifier for the composable.
 */
@Composable
fun MatchReasonSummary(
    matchReasons: List<MatchReason>,
    modifier: Modifier = Modifier,
) {
    if (matchReasons.isEmpty()) return

    val summaryText = matchReasons.joinToString(" ") { it.icon }
    val labelText = when {
        matchReasons.size == 1 -> matchReasons.first().label
        else -> "${matchReasons.size} matches"
    }

    Row(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(4.dp),
            )
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = summaryText,
            style = MaterialTheme.typography.labelSmall,
        )
        Text(
            text = labelText,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// region Previews

@Preview(showBackground = true)
@Composable
private fun AnimatedRelevanceBadgePreview() {
    MaterialTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AnimatedRelevanceBadge(
                percent = 95,
                matchReasons = listOf(
                    MatchReason.TitleMatch(matchedTerms = listOf("funny")),
                    MatchReason.EmojiMatch(matchedEmojis = listOf("😂")),
                ),
            )
            AnimatedRelevanceBadge(
                percent = 75,
                matchReasons = listOf(
                    MatchReason.SemanticMatch(similarityScore = 0.75f),
                ),
            )
            AnimatedRelevanceBadge(
                percent = 45,
                matchReasons = listOf(
                    MatchReason.TextMatch(matchedTerms = listOf("meme")),
                ),
            )
            AnimatedRelevanceBadge(
                percent = 25,
                matchReasons = emptyList(),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HighlightedMatchTextPreview() {
    MaterialTheme {
        HighlightedMatchText(
            text = "This is a funny meme about cats being funny",
            matchedTerms = listOf("funny", "meme"),
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun HighlightedEmojiRowPreview() {
    MaterialTheme {
        HighlightedEmojiRow(
            allEmojis = listOf("😂", "🔥", "❤️", "😍", "🙏", "💯"),
            matchedEmojis = listOf("😂", "🔥"),
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun MatchReasonSummaryPreview() {
    MaterialTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MatchReasonSummary(
                matchReasons = listOf(
                    MatchReason.TitleMatch(matchedTerms = listOf("funny")),
                ),
            )
            MatchReasonSummary(
                matchReasons = listOf(
                    MatchReason.TitleMatch(matchedTerms = listOf("funny")),
                    MatchReason.EmojiMatch(matchedEmojis = listOf("😂")),
                    MatchReason.SemanticMatch(similarityScore = 0.85f),
                ),
            )
        }
    }
}

// endregion
