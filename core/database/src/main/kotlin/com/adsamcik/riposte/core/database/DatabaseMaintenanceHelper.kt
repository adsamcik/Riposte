package com.adsamcik.riposte.core.database

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Helper for database maintenance operations.
 *
 * Provides utilities like VACUUM to reclaim disk space after
 * large numbers of deletions and ANALYZE to update query planner statistics.
 */
@Singleton
class DatabaseMaintenanceHelper
    @Inject
    constructor(
        private val database: MemeDatabase,
    ) {
        /**
         * Runs VACUUM to rebuild the database file, reclaiming unused space.
         * This operation can be slow on large databases — run on a background thread.
         */
        suspend fun vacuum() {
            database.openHelper.writableDatabase.execSQL("VACUUM")
        }

        /**
         * Runs ANALYZE to update statistics used by the query planner.
         */
        suspend fun analyze() {
            database.openHelper.writableDatabase.execSQL("ANALYZE")
        }

        /**
         * Performs full maintenance: ANALYZE then VACUUM.
         */
        suspend fun performFullMaintenance() {
            analyze()
            vacuum()
        }
    }
