@file:Suppress("MagicNumber")

package com.adsamcik.riposte.feature.settings.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.adsamcik.riposte.core.database.dao.EmojiUsageStats
import com.adsamcik.riposte.feature.settings.R
import com.adsamcik.riposte.feature.settings.domain.model.MilestoneState
import com.adsamcik.riposte.feature.settings.domain.model.MomentumTrend
import com.adsamcik.riposte.feature.settings.presentation.component.SettingsSection
import com.adsamcik.riposte.feature.settings.presentation.funstats.FunStatsUiState
import java.text.DateFormat
import java.util.Date

// region Meme-o-Meter

internal fun LazyListScope.memeOMeterSection(uiState: FunStatsUiState) {
    if (uiState.totalMemeCount == 0 && uiState.collectionTitle.isEmpty()) return

    item(key = "meme_o_meter") {
        SettingsSection(title = stringResource(R.string.settings_section_meme_o_meter)) {
            MemeOMeterCard(uiState)

            if (uiState.favoriteMemeCount > 0) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_library_favorites_title)) },
                    leadingContent = { Text("❤️", style = MaterialTheme.typography.titleMedium) },
                    trailingContent = { Text(uiState.favoriteMemeCount.toString()) },
                )
            }
        }
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
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
            ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
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
                style = MaterialTheme.typography.headlineLarge,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = uiState.collectionTitle,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                text = pluralStringResource(R.plurals.settings_meme_count, uiState.totalMemeCount, uiState.totalMemeCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
            )

            if (uiState.totalStorageBytes > 0) {
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("💾", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = formatFileSize(uiState.totalStorageBytes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                    )
                    if (uiState.storageFunFact.isNotEmpty()) {
                        Text(
                            text = "· ${uiState.storageFunFact}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f),
                        )
                    }
                }
            }
        }
    }
}

// endregion

// region Vibe Check

internal fun LazyListScope.vibeCheckSection(uiState: FunStatsUiState) {
    if (uiState.topVibes.isEmpty()) return

    item(key = "vibe_check") {
        SettingsSection(title = stringResource(R.string.settings_section_vibe_check)) {
            if (uiState.vibeTagline.isNotEmpty()) {
                Text(
                    text = uiState.vibeTagline,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
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
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_vibe_rank, rank),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(4.dp))
        Text(text = vibe.emoji, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.width(8.dp))
        LinearProgressIndicator(
            progress = { vibe.count.toFloat() / maxCount },
            modifier =
                Modifier
                    .weight(1f)
                    .height(8.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            strokeCap = StrokeCap.Butt,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.settings_vibe_memes, vibe.count),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// endregion

// region Fun Fact of the Day

internal fun LazyListScope.funFactSection(uiState: FunStatsUiState) {
    val fact = uiState.funFactOfTheDay ?: return

    item(key = "fun_fact") {
        SettingsSection(title = stringResource(R.string.settings_section_fun_fact)) {
            Card(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        text = "🔮",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = fact,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
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
        SettingsSection(title = stringResource(R.string.settings_section_momentum)) {
            Card(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.settings_momentum_this_week, uiState.memesThisWeek),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text =
                            when (uiState.momentumTrend) {
                                MomentumTrend.GROWING -> stringResource(R.string.settings_momentum_growing)
                                MomentumTrend.STABLE -> stringResource(R.string.settings_momentum_stable)
                                MomentumTrend.DECLINING -> stringResource(R.string.settings_momentum_declining)
                            },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(8.dp))

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
                            .height(64.dp)
                            .semantics { contentDescription = accessibilityDescription },
                )

                // Week labels
                Spacer(Modifier.height(4.dp))
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
    val currentDotColor = MaterialTheme.colorScheme.tertiary
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    Canvas(modifier = modifier) {
        if (data.isEmpty()) return@Canvas

        val maxVal = (data.maxOrNull() ?: 1).coerceAtLeast(1).toFloat()
        val pointCount = data.size
        val dotRadius = 4.dp.toPx()
        val padding = 4.dp.toPx()
        val plotWidth = size.width - 2 * dotRadius
        val stepX = plotWidth / (pointCount - 1).coerceAtLeast(1)

        // Grid line
        drawLine(
            color = gridColor,
            start = Offset(0f, size.height - padding),
            end = Offset(size.width, size.height - padding),
            strokeWidth = 1.dp.toPx(),
        )

        if (pointCount < 2) {
            // Single dot
            val y = size.height - padding - (data[0].toFloat() / maxVal * (size.height - 2 * padding))
            drawCircle(currentDotColor, radius = dotRadius, center = Offset(size.width / 2, y))
            return@Canvas
        }

        val path = Path()
        val points =
            data.mapIndexed { index, value ->
                val x = dotRadius + index * stepX
                val y = size.height - padding - (value.toFloat() / maxVal * (size.height - 2 * padding))
                Offset(x, y)
            }

        path.moveTo(points[0].x, points[0].y)
        for (i in 1 until points.size) {
            path.lineTo(points[i].x, points[i].y)
        }

        drawPath(
            path = path,
            color = lineColor,
            style =
                Stroke(
                    width = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
        )

        // Dots — highlight the last one (current week)
        points.forEachIndexed { index, point ->
            val isCurrentWeek = index == points.lastIndex
            drawCircle(
                color = if (isCurrentWeek) currentDotColor else lineColor,
                radius = if (isCurrentWeek) dotRadius else 3.dp.toPx(),
                center = point,
            )
        }
    }
}

// endregion

// region Milestones

internal fun LazyListScope.milestonesSection(uiState: FunStatsUiState) {
    if (uiState.milestones.isEmpty() || uiState.unlockedMilestoneCount == 0) return

    item(key = "milestones") {
        SettingsSection(
            title =
                stringResource(R.string.settings_section_milestones) + " " +
                    stringResource(
                        R.string.settings_milestones_count,
                        uiState.unlockedMilestoneCount,
                        uiState.totalMilestoneCount,
                    ),
        ) {
            val unlocked = uiState.milestones.filter { it.isUnlocked }
            val lockedCount = uiState.milestones.size - unlocked.size

            unlocked.forEach { milestone ->
                MilestoneRow(milestone)
            }

            if (lockedCount > 0) {
                val nextLocked = uiState.milestones.firstOrNull { !it.isUnlocked }
                ListItem(
                    headlineContent = {
                        Text(
                            text = if (nextLocked != null) {
                                milestoneTitle(nextLocked.id)
                            } else {
                                stringResource(R.string.settings_milestones_locked_remaining, lockedCount)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    supportingContent = if (nextLocked != null) {
                        {
                            Column {
                                Text(
                                    text = milestoneDescription(nextLocked.id),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                )
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
                    } else {
                        null
                    },
                    leadingContent = {
                        Text("🔒", style = MaterialTheme.typography.titleMedium)
                    },
                )
            }
        }
    }
}

@Composable
private fun MilestoneRow(milestone: MilestoneState) {
    ListItem(
        headlineContent = {
            if (milestone.isUnlocked) {
                Text(
                    text = milestoneTitle(milestone.id),
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Text(
                    text = stringResource(R.string.settings_milestone_locked),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                )
            }
        },
        supportingContent =
            if (milestone.isUnlocked) {
                {
                    Column {
                        Text(milestoneDescription(milestone.id))
                        milestone.unlockedAt?.let { timestamp ->
                            Text(
                                text = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(timestamp)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else {
                null
            },
        leadingContent = {
            Text(
                text = if (milestone.isUnlocked) milestone.icon else "🔒",
                style = MaterialTheme.typography.titleMedium,
            )
        },
        trailingContent =
            if (milestone.isUnlocked) {
                { Text("✅", style = MaterialTheme.typography.titleMedium) }
            } else {
                null
            },
    )
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
