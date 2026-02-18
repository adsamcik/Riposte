package com.adsamcik.riposte.feature.settings.presentation

import android.content.Context
import com.adsamcik.riposte.core.database.FunStatistics
import com.adsamcik.riposte.feature.settings.R
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import org.junit.Before
import org.junit.Test

/**
 * Tests for [computeFunFactOfTheDay].
 * Uses mockk Context to supply string resources.
 */
class ComputeFunFactOfTheDayTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        // Set up deterministic string responses for each string resource.
        // getString(int, vararg Any) passes varargs as Object[] → use args array.
        every { context.getString(R.string.settings_fun_fact_not_enough) } returns "not_enough"
        every { context.getString(R.string.settings_fun_fact_total_views, *anyVararg()) } answers {
            val va = args[1] as Array<*>
            "total_views_${va[0]}"
        }
        every { context.getString(R.string.settings_fun_fact_max_views, *anyVararg()) } answers {
            val va = args[1] as Array<*>
            "max_views_${va[0]}"
        }
        every { context.getString(R.string.settings_fun_fact_never_viewed, *anyVararg()) } answers {
            val va = args[1] as Array<*>
            "never_viewed_${va[0]}"
        }
        every { context.getString(R.string.settings_fun_fact_favorites_pct, *anyVararg()) } answers {
            val va = args[1] as Array<*>
            "favorites_pct_${va[0]}"
        }
        every { context.getString(R.string.settings_fun_fact_emoji_count, *anyVararg()) } answers {
            val va = args[1] as Array<*>
            "emoji_count_${va[0]}"
        }
        every { context.getString(R.string.settings_fun_fact_formats, *anyVararg()) } answers {
            val va = args[1] as Array<*>
            "formats_${va[0]}"
        }
        every { context.getString(R.string.settings_fun_fact_total_shares, *anyVararg()) } answers {
            val va = args[1] as Array<*>
            "total_shares_${va[0]}"
        }
        every { context.getString(R.string.settings_fun_fact_avg_size, *anyVararg()) } answers {
            val va = args[1] as Array<*>
            "avg_size_${va[0]}"
        }
        every { context.getString(R.string.settings_fun_fact_collection_age, *anyVararg()) } answers {
            val va = args[1] as Array<*>
            "collection_age_${va[0]}"
        }
        every { context.getString(R.string.settings_fun_fact_fresh_drop, *anyVararg()) } answers {
            val va = args[1] as Array<*>
            "fresh_drop_${va[0]}"
        }
        every { context.getString(R.string.settings_fun_fact_fav_viewers, *anyVararg()) } answers {
            val va = args[1] as Array<*>
            "fav_viewers_${va[0]}"
        }
    }

    // region Not enough memes

    @Test
    fun `returns not enough message when totalMemes is 0`() {
        val result = computeFunFactOfTheDay(context, FunStatistics(totalMemes = 0))
        assertThat(result).isEqualTo("not_enough")
    }

    @Test
    fun `returns not enough message when totalMemes is 2`() {
        val result = computeFunFactOfTheDay(context, FunStatistics(totalMemes = 2))
        assertThat(result).isEqualTo("not_enough")
    }

    @Test
    fun `returns fact when totalMemes is exactly 3`() {
        val result = computeFunFactOfTheDay(
            context,
            FunStatistics(totalMemes = 3, totalViewCount = 10),
        )
        assertThat(result).isNotEqualTo("not_enough")
    }

    // endregion

    // region Individual fact conditions

    @Test
    fun `adds total views fact when totalViewCount greater than 0`() {
        val stats = FunStatistics(totalMemes = 5, totalViewCount = 42)
        val result = computeFunFactOfTheDay(context, stats)
        assertThat(result).isNotNull()
        // With only one condition met, the fact should be the total_views one
        assertThat(result).isEqualTo("total_views_42")
    }

    @Test
    fun `does not add total views fact when totalViewCount is 0`() {
        val stats = FunStatistics(totalMemes = 5, totalViewCount = 0, maxViewCount = 3)
        val result = computeFunFactOfTheDay(context, stats)
        assertThat(result).doesNotContain("total_views")
    }

    @Test
    fun `adds max views fact when maxViewCount is 2 or more`() {
        val stats = FunStatistics(totalMemes = 5, maxViewCount = 2)
        val result = computeFunFactOfTheDay(context, stats)
        assertThat(result).isEqualTo("max_views_2")
    }

    @Test
    fun `does not add max views fact when maxViewCount is 1`() {
        val stats = FunStatistics(totalMemes = 5, maxViewCount = 1, totalViewCount = 5)
        val result = computeFunFactOfTheDay(context, stats)
        assertThat(result).doesNotContain("max_views")
    }

    @Test
    fun `adds never viewed fact when neverInteractedCount greater than 0`() {
        val stats = FunStatistics(totalMemes = 5, neverInteractedCount = 3)
        val result = computeFunFactOfTheDay(context, stats)
        assertThat(result).isEqualTo("never_viewed_3")
    }

    @Test
    fun `adds favorites percentage fact when favoriteMemes greater than 0`() {
        val stats = FunStatistics(totalMemes = 10, favoriteMemes = 5)
        val result = computeFunFactOfTheDay(context, stats)
        assertThat(result).isEqualTo("favorites_pct_50")
    }

    @Test
    fun `does not add favorites percentage fact when favoriteMemes is 0`() {
        val stats = FunStatistics(totalMemes = 5, favoriteMemes = 0, totalViewCount = 1)
        val result = computeFunFactOfTheDay(context, stats)
        assertThat(result).doesNotContain("favorites_pct")
    }

    @Test
    fun `adds emoji count fact when uniqueEmojiCount is 3 or more`() {
        val stats = FunStatistics(totalMemes = 5, uniqueEmojiCount = 3)
        val result = computeFunFactOfTheDay(context, stats)
        assertThat(result).isEqualTo("emoji_count_3")
    }

    @Test
    fun `does not add emoji count fact when uniqueEmojiCount is 2`() {
        val stats = FunStatistics(totalMemes = 5, uniqueEmojiCount = 2, totalViewCount = 1)
        val result = computeFunFactOfTheDay(context, stats)
        assertThat(result).doesNotContain("emoji_count")
    }

    @Test
    fun `adds formats fact when distinctMimeTypes is 2 or more`() {
        val stats = FunStatistics(totalMemes = 5, distinctMimeTypes = 2)
        val result = computeFunFactOfTheDay(context, stats)
        assertThat(result).isEqualTo("formats_2")
    }

    @Test
    fun `does not add formats fact when distinctMimeTypes is 1`() {
        val stats = FunStatistics(totalMemes = 5, distinctMimeTypes = 1, totalViewCount = 1)
        val result = computeFunFactOfTheDay(context, stats)
        assertThat(result).doesNotContain("formats")
    }

    @Test
    fun `adds total shares fact when totalUseCount greater than 0`() {
        val stats = FunStatistics(totalMemes = 5, totalUseCount = 7)
        val result = computeFunFactOfTheDay(context, stats)
        assertThat(result).isEqualTo("total_shares_7")
    }

    @Test
    fun `adds avg size fact when averageFileSize greater than 0`() {
        val stats = FunStatistics(totalMemes = 5, averageFileSize = 2_097_152)
        val result = computeFunFactOfTheDay(context, stats)
        assertThat(result).isEqualTo("avg_size_2.0 MB")
    }

    @Test
    fun `adds favorites with views fact when favoritesWithViews greater than 0`() {
        val stats = FunStatistics(totalMemes = 5, favoritesWithViews = 4)
        val result = computeFunFactOfTheDay(context, stats)
        assertThat(result).isEqualTo("fav_viewers_4")
    }

    // endregion

    // region Null / empty facts

    @Test
    fun `returns null when no conditions are met`() {
        val stats = FunStatistics(
            totalMemes = 3,
            totalViewCount = 0,
            maxViewCount = 0,
            neverInteractedCount = 0,
            favoriteMemes = 0,
            uniqueEmojiCount = 0,
            distinctMimeTypes = 0,
            totalUseCount = 0,
            averageFileSize = 0,
            oldestImportTimestamp = null,
            newestImportTimestamp = null,
            favoritesWithViews = 0,
        )
        val result = computeFunFactOfTheDay(context, stats)
        assertThat(result).isNull()
    }

    // endregion

    // region Deterministic day rotation

    @Test
    fun `same stats returns same fact on same call`() {
        val stats = FunStatistics(
            totalMemes = 10,
            totalViewCount = 5,
            maxViewCount = 3,
            uniqueEmojiCount = 4,
        )
        val result1 = computeFunFactOfTheDay(context, stats)
        val result2 = computeFunFactOfTheDay(context, stats)
        assertThat(result1).isEqualTo(result2)
    }

    @Test
    fun `rotates among multiple facts based on day of year`() {
        // With multiple facts available, the day-of-year mod selects one
        val stats = FunStatistics(
            totalMemes = 10,
            totalViewCount = 5,
            maxViewCount = 10,
            neverInteractedCount = 2,
            favoriteMemes = 3,
            uniqueEmojiCount = 5,
            distinctMimeTypes = 3,
            totalUseCount = 20,
            averageFileSize = 1_048_576,
            favoritesWithViews = 2,
        )
        val result = computeFunFactOfTheDay(context, stats)
        assertThat(result).isNotNull()

        // All possible facts for these stats
        val possibleFacts = listOf(
            "total_views_5",
            "max_views_10",
            "never_viewed_2",
            "favorites_pct_30",
            "emoji_count_5",
            "formats_3",
            "total_shares_20",
            "avg_size_1.0 MB",
            "fav_viewers_2",
        )
        assertThat(possibleFacts).contains(result)
    }

    // endregion

    // region Edge cases

    @Test
    fun `favorites percentage uses integer division`() {
        // 1 favorite out of 3 memes = 33% (integer division: 1*100/3=33)
        val stats = FunStatistics(totalMemes = 3, favoriteMemes = 1)
        val result = computeFunFactOfTheDay(context, stats)
        assertThat(result).isEqualTo("favorites_pct_33")
    }

    @Test
    fun `favorites percentage is 100 when all are favorites`() {
        val stats = FunStatistics(totalMemes = 5, favoriteMemes = 5)
        val result = computeFunFactOfTheDay(context, stats)
        assertThat(result).isEqualTo("favorites_pct_100")
    }

    // endregion
}
