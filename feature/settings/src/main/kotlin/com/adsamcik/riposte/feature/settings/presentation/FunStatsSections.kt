@file:Suppress("MagicNumber")

package com.adsamcik.riposte.feature.settings.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adsamcik.riposte.core.database.dao.EmojiUsageStats
import com.adsamcik.riposte.feature.settings.R
import com.adsamcik.riposte.feature.settings.domain.model.MilestoneState
import com.adsamcik.riposte.feature.settings.domain.model.MomentumTrend
import com.adsamcik.riposte.feature.settings.presentation.funstats.FunStatsUiState
import java.text.DateFormat
import java.util.Date

// ── Shared Section Header ──────────────────────────────────

@Composable
private fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 8.dp),
    )
}

// region Meme-o-Meter

internal fun LazyListScope.memeOMeterSection(uiState: FunStatsUiState) {
    if (uiState.totalMemeCount == 0 && uiState.collectionTitle.isEmpty()) return

    item(key = "meme_o_meter") {
        MemeOMeterCard(uiState)
    }
}

@Composable
private fun MemeOMeterCard(uiState: FunStatsUiState) {
    if (uiState.collectionTitle.isEmpty()) return

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(28.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Large expressive emoji
            Text(
                text =
                    when {
                        uiState.totalMemeCount == 1337 -> "🏴\u200D☠️"
                        uiState.totalMemeCount >= 1001 -> "👑"
                        uiState.totalMemeCount >= 501 -> "🐉"
                        uiState.totalMemeCount >= 251 -> "⚔️"
                        uiState.totalMemeCount >= 101 -> "🏰"
                        uiState.totalMemeCount >= 51 -> "🗡️"
                        uiState.totalMemeCount >= 11 -> "🛡️"
                        uiState.totalMemeCount >= 1 -> "🌱"
                        else -> "✨"
                    },
                fontSize = 48.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = uiState.collectionTitle,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = pluralStringResource(R.plurals.settings_meme_count, uiState.totalMemeCount, uiState.totalMemeCount),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
            )

            // Stats row: favorites + storage
            if (uiState.favoriteMemeCount > 0 || uiState.totalStorageBytes > 0) {
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (uiState.favoriteMemeCount > 0) {
                        StatChip(emoji = "❤️", value = uiState.favoriteMemeCount.toString())
                    }
                    if (uiState.favoriteMemeCount > 0 && uiState.totalStorageBytes > 0) {
                        Spacer(Modifier.width(12.dp))
                    }
                    if (uiState.totalStorageBytes > 0) {
                        StatChip(emoji = "💾", value = formatFileSize(uiState.totalStorageBytes))
                    }
                }
            }

            if (uiState.storageFunFact.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = uiState.storageFunFact,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun StatChip(
    emoji: String,
    value: String,
) {
    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.08f))
                .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(text = emoji, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

// endregion

// region Vibe Check

internal fun LazyListScope.vibeCheckSection(uiState: FunStatsUiState) {
    if (uiState.topVibes.isEmpty()) return

    item(key = "vibe_check") {
        Column {
            SectionHeader(title = stringResource(R.string.settings_section_vibe_check))

            if (uiState.vibeTagline.isNotEmpty()) {
                Text(
                    text = uiState.vibeTagline,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
                Spacer(Modifier.height(4.dp))
            }

            val maxCount = uiState.topVibes.maxOfOrNull { it.count } ?: 1
            uiState.topVibes.forEachIndexed { index, vibe ->
                VibeRow(
                    rank = index + 1,
                    vibe = vibe,
                    maxCount = maxCount,
                )
            }
        }
    }
}

@Composable
private fun VibeRow(
    rank: Int,
    vibe: EmojiUsageStats,
    maxCount: Int,
) {
    val barColor = when (rank) {
        1 -> MaterialTheme.colorScheme.primary
        2 -> MaterialTheme.colorScheme.secondary
        3 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 5.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_vibe_rank, rank),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(24.dp),
        )
        Text(text = vibe.emoji, fontSize = 22.sp)
        Spacer(Modifier.width(10.dp))
        LinearProgressIndicator(
            progress = { vibe.count.toFloat() / maxCount },
            modifier =
                Modifier
                    .weight(1f)
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp)),
            color = barColor,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = stringResource(R.string.settings_vibe_memes, vibe.count),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// endregion

// region Fun Fact of the Day

internal fun LazyListScope.funFactSection(uiState: FunStatsUiState) {
    val fact = uiState.funFactOfTheDay ?: return

    item(key = "fun_fact") {
        Column {
            SectionHeader(title = stringResource(R.string.settings_section_fun_fact))
            Card(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(24.dp),
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    ),
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        text = "🔮",
                        fontSize = 28.sp,
                    )
                    Spacer(Modifier.width(14.dp))
                    Text(
                        text = fact,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }
        }
    }
}

// endregion

// region Momentum

internal fun LazyListScope.momentumSection(uiState: FunStatsUiState) {
    if (uiState.weeklyImportCounts.all { it == 0 }) return

    item(key = "momentum") {
        Column {
            SectionHeader(title = stringResource(R.string.settings_section_momentum))
            Card(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(24.dp),
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = uiState.memesThisWeek.toString(),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.settings_momentum_this_week_label),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                        Spacer(Modifier.weight(1f))
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text =
                                    when (uiState.momentumTrend) {
                                        MomentumTrend.GROWING -> stringResource(R.string.settings_momentum_growing)
                                        MomentumTrend.STABLE -> stringResource(R.string.settings_momentum_stable)
                                        MomentumTrend.DECLINING -> stringResource(R.string.settings_momentum_declining)
                                    },
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))

                    val trendDescription = when (uiState.momentumTrend) {
                        MomentumTrend.GROWING -> stringResource(R.string.settings_momentum_growing)
                        MomentumTrend.STABLE -> stringResource(R.string.settings_momentum_stable)
                        MomentumTrend.DECLINING -> stringResource(R.string.settings_momentum_declining)
                    }
                    val accessibilityDescription = stringResource(
                        R.string.settings_momentum_sparkline_description,
                        uiState.weeklyImportCounts.joinToString(", "),
                        trendDescription,
                    )

                    MomentumSparkline(
                        data = uiState.weeklyImportCounts,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(80.dp)
                                .semantics { contentDescription = accessibilityDescription },
                    )

                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        val labels = listOf(
                            stringResource(R.string.settings_momentum_week_3),
                            stringResource(R.string.settings_momentum_week_2),
                            stringResource(R.string.settings_momentum_week_1),
                            stringResource(R.string.settings_momentum_week_now),
                        )
                        labels.forEach { label ->
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MomentumSparkline(
    data: List<Int>,
    modifier: Modifier = Modifier,
) {
    val lineColor = MaterialTheme.colorScheme.primary
    val fillColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
    val currentDotColor = MaterialTheme.colorScheme.tertiary
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)

    Canvas(modifier = modifier) {
        if (data.isEmpty()) return@Canvas

        val maxVal = (data.maxOrNull() ?: 1).coerceAtLeast(1).toFloat()
        val pointCount = data.size
        val dotRadius = 5.dp.toPx()
        val padding = 6.dp.toPx()
        val plotHeight = size.height - 2 * padding
        val plotWidth = size.width - 2 * dotRadius
        val stepX = plotWidth / (pointCount - 1).coerceAtLeast(1)

        // Subtle grid line
        drawLine(
            color = gridColor,
            start = Offset(0f, size.height - padding),
            end = Offset(size.width, size.height - padding),
            strokeWidth = 1.dp.toPx(),
        )

        if (pointCount < 2) {
            val y = size.height - padding - (data[0].toFloat() / maxVal * plotHeight)
            drawCircle(currentDotColor, radius = dotRadius, center = Offset(size.width / 2, y))
            return@Canvas
        }

        val points =
            data.mapIndexed { index, value ->
                val x = dotRadius + index * stepX
                val y = size.height - padding - (value.toFloat() / maxVal * plotHeight)
                Offset(x, y)
            }

        // Filled area under the line
        val fillPath = Path().apply {
            moveTo(points[0].x, size.height - padding)
            points.forEach { lineTo(it.x, it.y) }
            lineTo(points.last().x, size.height - padding)
            close()
        }
        drawPath(fillPath, color = fillColor, style = Fill)

        // Line
        val linePath = Path().apply {
            moveTo(points[0].x, points[0].y)
            for (i in 1 until points.size) {
                lineTo(points[i].x, points[i].y)
            }
        }
        drawPath(
            linePath,
            color = lineColor,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )

        // Dots
        points.forEachIndexed { index, point ->
            val isCurrentWeek = index == points.lastIndex
            drawCircle(
                color = if (isCurrentWeek) currentDotColor else lineColor,
                radius = if (isCurrentWeek) dotRadius * 1.4f else dotRadius * 0.8f,
                center = point,
            )
            if (isCurrentWeek) {
                drawCircle(
                    color = currentDotColor.copy(alpha = 0.2f),
                    radius = dotRadius * 2.5f,
                    center = point,
                )
            }
        }
    }
}

// endregion

// region Milestones

internal fun LazyListScope.milestonesSection(uiState: FunStatsUiState) {
    if (uiState.milestones.isEmpty() || uiState.unlockedMilestoneCount == 0) return

    item(key = "milestones") {
        Column {
            SectionHeader(
                title = stringResource(R.string.settings_section_milestones) + " " +
                    stringResource(
                        R.string.settings_milestones_count,
                        uiState.unlockedMilestoneCount,
                        uiState.totalMilestoneCount,
                    ),
            )

            // Progress bar for milestone completion
            LinearProgressIndicator(
                progress = { uiState.unlockedMilestoneCount.toFloat() / uiState.totalMilestoneCount },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                strokeCap = StrokeCap.Round,
                gapSize = 0.dp,
                drawStopIndicator = {},
            )

            Spacer(Modifier.height(12.dp))

            val unlocked = uiState.milestones.filter { it.isUnlocked }
            val lockedCount = uiState.milestones.size - unlocked.size

            unlocked.forEach { milestone ->
                MilestoneRow(milestone)
            }

            if (lockedCount > 0) {
                val nextLocked = uiState.milestones.firstOrNull { !it.isUnlocked }

                Card(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors =
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
                        ),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("🔒", fontSize = 22.sp)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            if (nextLocked != null) {
                                Text(
                                    text = milestoneTitle(nextLocked.id),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = milestoneDescription(nextLocked.id),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                )
                            }
                            if (lockedCount > 1) {
                                Text(
                                    text = stringResource(
                                        R.string.settings_milestones_locked_remaining,
                                        lockedCount - 1,
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun MilestoneRow(milestone: MilestoneState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Emoji badge
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (milestone.isUnlocked) milestone.icon else "🔒",
                fontSize = 22.sp,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (milestone.isUnlocked) milestoneTitle(milestone.id) else stringResource(R.string.settings_milestone_locked),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (milestone.isUnlocked) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                },
            )
            if (milestone.isUnlocked) {
                Text(
                    text = milestoneDescription(milestone.id),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                milestone.unlockedAt?.let { timestamp ->
                    Text(
                        text = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(timestamp)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
            }
        }
        if (milestone.isUnlocked) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun milestoneTitle(id: String): String =
    when (id) {
        "first_steps" -> stringResource(R.string.settings_milestone_first_steps)
        "getting_started" -> stringResource(R.string.settings_milestone_getting_started)
        "half_century" -> stringResource(R.string.settings_milestone_half_century)
        "century_club" -> stringResource(R.string.settings_milestone_century_club)
        "the_archivist" -> stringResource(R.string.settings_milestone_the_archivist)
        "meme_hoarder" -> stringResource(R.string.settings_milestone_meme_hoarder)
        "loyal_fan" -> stringResource(R.string.settings_milestone_loyal_fan)
        "emoji_rainbow" -> stringResource(R.string.settings_milestone_emoji_rainbow)
        "format_diplomat" -> stringResource(R.string.settings_milestone_format_diplomat)
        "deep_diver" -> stringResource(R.string.settings_milestone_deep_diver)
        "social_butterfly" -> stringResource(R.string.settings_milestone_social_butterfly)
        "night_owl" -> stringResource(R.string.settings_milestone_night_owl)
        "leet" -> stringResource(R.string.settings_milestone_leet)
        else -> id
    }

@Composable
private fun milestoneDescription(id: String): String =
    when (id) {
        "first_steps" -> stringResource(R.string.settings_milestone_first_steps_desc)
        "getting_started" -> stringResource(R.string.settings_milestone_getting_started_desc)
        "half_century" -> stringResource(R.string.settings_milestone_half_century_desc)
        "century_club" -> stringResource(R.string.settings_milestone_century_club_desc)
        "the_archivist" -> stringResource(R.string.settings_milestone_the_archivist_desc)
        "meme_hoarder" -> stringResource(R.string.settings_milestone_meme_hoarder_desc)
        "loyal_fan" -> stringResource(R.string.settings_milestone_loyal_fan_desc)
        "emoji_rainbow" -> stringResource(R.string.settings_milestone_emoji_rainbow_desc)
        "format_diplomat" -> stringResource(R.string.settings_milestone_format_diplomat_desc)
        "deep_diver" -> stringResource(R.string.settings_milestone_deep_diver_desc)
        "social_butterfly" -> stringResource(R.string.settings_milestone_social_butterfly_desc)
        "night_owl" -> stringResource(R.string.settings_milestone_night_owl_desc)
        "leet" -> stringResource(R.string.settings_milestone_leet_desc)
        else -> ""
    }

// endregion
