@file:Suppress("MagicNumber")

package com.adsamcik.riposte.feature.settings.presentation

import android.content.Context
import com.adsamcik.riposte.core.database.FunStatistics
import com.adsamcik.riposte.core.database.dao.EmojiUsageStats
import com.adsamcik.riposte.feature.settings.R
import com.adsamcik.riposte.feature.settings.domain.model.MomentumTrend
import java.util.Calendar

/**
 * Computes the collection title tier based on total meme count.
 */
internal fun computeCollectionTitle(
    context: Context,
    count: Int,
): String =
    when {
        count == 1337 -> context.getString(R.string.settings_title_meme_h4x0r)
        count >= 1001 -> context.getString(R.string.settings_title_meme_deity)
        count >= 501 -> context.getString(R.string.settings_title_meme_dragon)
        count >= 251 -> context.getString(R.string.settings_title_meme_warlord)
        count >= 101 -> context.getString(R.string.settings_title_meme_lord)
        count >= 51 -> context.getString(R.string.settings_title_meme_knight)
        count >= 11 -> context.getString(R.string.settings_title_meme_squire)
        count >= 1 -> context.getString(R.string.settings_title_meme_peasant)
        else -> context.getString(R.string.settings_title_empty)
    }

private val FLOPPY_SIZE = 1_474_560L
private val CD_SIZE = 700_000_000L
private val SONG_SIZE = 4_000_000L
private val PHOTO_SIZE = 3_500_000L

/**
 * Converts total bytes into a fun real-world equivalence.
 */
internal fun computeStorageFunFact(totalBytes: Long): String {
    if (totalBytes <= 0) return ""

    val equivalences = mutableListOf<String>()

    val floppies = totalBytes / FLOPPY_SIZE
    if (floppies >= 1) equivalences.add("$floppies floppy disks")

    val songs = totalBytes / SONG_SIZE
    if (songs >= 1) equivalences.add("$songs songs")

    val photos = totalBytes / PHOTO_SIZE
    if (photos >= 1) equivalences.add("$photos photos")

    if (totalBytes >= CD_SIZE) {
        val cds = totalBytes / CD_SIZE
        equivalences.add("$cds CDs")
    }

    if (equivalences.isEmpty()) return "A few bytes of culture"

    val index = (totalBytes % equivalences.size).toInt()
    return "≈ ${equivalences[index]} of pure culture"
}

/**
 * Computes a personality tagline from the top emoji distribution.
 */
internal fun computeVibeTagline(topEmojis: List<EmojiUsageStats>): String {
    if (topEmojis.isEmpty()) return ""
    if (topEmojis.size < 2) return "Just getting started!"

    val total = topEmojis.sumOf { it.count }.toFloat()
    val topPct = ((topEmojis[0].count / total) * 100).toInt()

    val topEmoji = topEmojis[0].emoji
    val secondEmoji = topEmojis.getOrNull(1)?.emoji

    return when {
        topEmoji == "😂" && secondEmoji == "💀" -> "$topPct% unhinged humor. Chronically online energy."
        topEmoji == "💀" && secondEmoji == "😂" -> "$topPct% skull energy. You've seen things."
        topEmoji in listOf("❤️", "🥰", "😍") -> "$topPct% wholesome. Suspiciously wholesome."
        topEmoji == "🗿" -> "$topPct% moai. You are an enigma."
        topEmoji == "🔥" -> "$topPct% fire. Everything is lit."
        topEmoji == "😭" -> "$topPct% tears. It's a coping mechanism."
        topEmoji in listOf("😤", "💪") -> "$topPct% determination. Sigma energy."
        topEmoji == "🤡" -> "$topPct% clown. Self-aware chaos."
        topPct >= 60 -> "$topPct% ${topEmojis[0].emoji}. Very committed."
        topPct >= 40 -> "$topPct% ${topEmojis[0].emoji}, ${100 - topPct}% everything else."
        else -> "A balanced diet of ${topEmojis[0].emoji} and ${topEmojis[1].emoji}."
    }
}

/**
 * Selects the fun fact of the day based on available statistics.
 * Uses the day of year as a deterministic seed for daily rotation.
 */
internal fun computeFunFactOfTheDay(
    context: Context,
    stats: FunStatistics,
): String? {
    if (stats.totalMemes < 3) {
        return context.getString(R.string.settings_fun_fact_not_enough)
    }

    val facts = mutableListOf<String>()

    if (stats.totalViewCount > 0) {
        facts.add(context.getString(R.string.settings_fun_fact_total_views, stats.totalViewCount))
    }
    if (stats.maxViewCount >= 2) {
        facts.add(context.getString(R.string.settings_fun_fact_max_views, stats.maxViewCount))
    }
    if (stats.neverInteractedCount > 0) {
        facts.add(context.getString(R.string.settings_fun_fact_never_viewed, stats.neverInteractedCount))
    }
    if (stats.favoriteMemes > 0 && stats.totalMemes > 0) {
        val pct = (stats.favoriteMemes * 100) / stats.totalMemes
        facts.add(context.getString(R.string.settings_fun_fact_favorites_pct, pct))
    }
    if (stats.uniqueEmojiCount >= 3) {
        facts.add(context.getString(R.string.settings_fun_fact_emoji_count, stats.uniqueEmojiCount))
    }
    if (stats.distinctMimeTypes >= 2) {
        facts.add(context.getString(R.string.settings_fun_fact_formats, stats.distinctMimeTypes))
    }
    if (stats.totalUseCount > 0) {
        facts.add(context.getString(R.string.settings_fun_fact_total_shares, stats.totalUseCount))
    }
    if (stats.averageFileSize > 0) {
        facts.add(context.getString(R.string.settings_fun_fact_avg_size, formatFileSize(stats.averageFileSize)))
    }
    val now = System.currentTimeMillis()
    stats.oldestImportTimestamp?.let { oldest ->
        val daysAgo = ((now - oldest) / 86_400_000L).toInt()
        if (daysAgo >= 7) {
            facts.add(context.getString(R.string.settings_fun_fact_collection_age, daysAgo))
        }
    }
    stats.newestImportTimestamp?.let { newest ->
        val hoursAgo = ((now - newest) / 3_600_000L).toInt()
        if (hoursAgo < 48) {
            facts.add(context.getString(R.string.settings_fun_fact_fresh_drop, hoursAgo))
        }
    }
    if (stats.favoritesWithViews > 0) {
        facts.add(context.getString(R.string.settings_fun_fact_fav_viewers, stats.favoritesWithViews))
    }

    if (facts.isEmpty()) return null

    val dayOfYear = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
    return facts[dayOfYear % facts.size]
}

/**
 * Computes weekly import counts for sparkline display (4 weeks, newest first).
 */
internal fun computeWeeklyData(stats: FunStatistics): List<Int> {
    val weeks = IntArray(4) { 0 }
    for (wc in stats.weeklyImportCounts) {
        if (wc.weekAgo in 0..3) {
            weeks[wc.weekAgo] = wc.count
        }
    }
    // Reverse so index 0 = oldest week, index 3 = this week
    return weeks.reversed()
}

/**
 * Determines the momentum trend from weekly data.
 */
internal fun computeMomentumTrend(weeklyData: List<Int>): MomentumTrend {
    if (weeklyData.size < 2) return MomentumTrend.STABLE
    val recent = weeklyData.takeLast(2).sum()
    val older = weeklyData.take(2).sum()
    return when {
        recent > older + 2 -> MomentumTrend.GROWING
        older > recent + 2 -> MomentumTrend.DECLINING
        else -> MomentumTrend.STABLE
    }
}

internal fun formatFileSize(bytes: Long): String =
    when {
        bytes >= 1_073_741_824 -> "%.1f GB".format(bytes / 1_073_741_824.0)
        bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
        bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
        else -> "$bytes B"
    }
