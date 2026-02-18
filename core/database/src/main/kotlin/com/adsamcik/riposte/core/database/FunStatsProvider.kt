package com.adsamcik.riposte.core.database

import com.adsamcik.riposte.core.database.dao.EmojiTagDao
import com.adsamcik.riposte.core.database.dao.ImportRequestDao
import com.adsamcik.riposte.core.database.dao.MemeDao
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private const val TOP_EMOJI_COUNT = 5
private const val WEEKS_TO_TRACK = 4
private const val MILLIS_PER_WEEK = 604_800_000L

/**
 * Provides aggregated fun statistics from the meme library.
 * All queries run against existing data — no schema changes needed.
 */
@Singleton
class FunStatsProvider
    @Inject
    constructor(
        private val memeDao: MemeDao,
        private val emojiTagDao: EmojiTagDao,
        private val importRequestDao: ImportRequestDao,
    ) {
        /**
         * Load all fun statistics. Uses structured concurrency to run
         * independent queries in parallel for better performance.
         */
        suspend fun getStatistics(): FunStatistics =
            coroutineScope {
                val now = System.currentTimeMillis()
                val fourWeeksAgo = now - (WEEKS_TO_TRACK * MILLIS_PER_WEEK)

                // Parallel query groups
                val totalStorage = async { memeDao.getTotalStorageBytes() }
                val avgSize = async { memeDao.getAverageFileSize() }
                val largestSize = async { memeDao.getLargestFileSize() }
                val totalUse = async { memeDao.getTotalUseCount() }
                val totalView = async { memeDao.getTotalViewCount() }
                val maxView = async { memeDao.getMaxViewCount() }
                val totalMemes = async { memeDao.getMemeCount() }
                val favoriteMemes = async { memeDao.getFavoriteCount() }
                val uniqueEmojis = async { emojiTagDao.getUniqueEmojiCount() }
                val topEmojis = async { emojiTagDao.getAllEmojisWithCounts().first().take(TOP_EMOJI_COUNT) }
                val completedImports = async { importRequestDao.getCompletedImportCount() }
                val lastImport = async { importRequestDao.getLastCompletedImportTimestamp() }
                val totalImported = async { importRequestDao.getTotalImportedMemeCount() }
                val weeklyImports = async { memeDao.getWeeklyImportCounts(now, fourWeeksAgo) }
                val oldestImport = async { memeDao.getOldestImportTimestamp() }
                val newestImport = async { memeDao.getNewestImportTimestamp() }
                val distinctMimeTypes = async { memeDao.getDistinctMimeTypeCount() }
                val neverInteracted = async { memeDao.getNeverInteractedCount() }
                val favoritesViewed = async { memeDao.getFavoritesWithViews() }

                FunStatistics(
                    totalStorageBytes = totalStorage.await(),
                    averageFileSize = avgSize.await(),
                    largestFileSize = largestSize.await(),
                    totalUseCount = totalUse.await(),
                    totalViewCount = totalView.await(),
                    maxViewCount = maxView.await(),
                    totalMemes = totalMemes.await(),
                    favoriteMemes = favoriteMemes.await(),
                    uniqueEmojiCount = uniqueEmojis.await(),
                    topEmojis = topEmojis.await(),
                    completedImports = completedImports.await(),
                    lastImportTimestamp = lastImport.await(),
                    totalImportedMemes = totalImported.await(),
                    weeklyImportCounts = weeklyImports.await(),
                    oldestImportTimestamp = oldestImport.await(),
                    newestImportTimestamp = newestImport.await(),
                    distinctMimeTypes = distinctMimeTypes.await(),
                    neverInteractedCount = neverInteracted.await(),
                    favoritesWithViews = favoritesViewed.await(),
                )
            }
    }
