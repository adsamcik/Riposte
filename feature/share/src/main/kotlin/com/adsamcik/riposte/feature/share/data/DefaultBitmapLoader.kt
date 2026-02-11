package com.adsamcik.riposte.feature.share.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.adsamcik.riposte.feature.share.domain.BitmapLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Default implementation of [BitmapLoader] using BitmapFactory and File.
 */
@Singleton
class DefaultBitmapLoader
    @Inject
    constructor() : BitmapLoader {
        override suspend fun loadBitmap(filePath: String): Bitmap? {
            return withContext(Dispatchers.IO) {
                BitmapFactory.decodeFile(filePath)
            }
        }

        override suspend fun getFileSize(filePath: String): Long {
            return withContext(Dispatchers.IO) {
                File(filePath).length()
            }
        }
    }
