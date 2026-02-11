package com.adsamcik.riposte.sharing

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.adsamcik.riposte.core.model.Meme
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages dynamic sharing shortcuts for top suggested stickers.
 *
 * Pushes the top stickers as direct share targets so they appear
 * in the Android Sharesheet's Direct Share row and conversation shortcuts.
 */
@Singleton
class SharingShortcutManager
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) {
        /**
         * Update sharing shortcuts with the latest suggestions.
         * Should be called whenever the suggestion list changes.
         *
         * @param suggestions Top suggested memes (first [MAX_SHORTCUTS] are used).
         */
        fun updateShortcuts(suggestions: List<Meme>) {
            val shortcuts =
                suggestions
                    .take(MAX_SHORTCUTS)
                    .mapNotNull { meme -> buildShortcut(meme) }

            // Remove old dynamic shortcuts and push new ones
            ShortcutManagerCompat.removeAllDynamicShortcuts(context)
            if (shortcuts.isNotEmpty()) {
                ShortcutManagerCompat.addDynamicShortcuts(context, shortcuts)
            }
        }

        private fun buildShortcut(meme: Meme): ShortcutInfoCompat? {
            val file = File(meme.filePath)
            if (!file.exists()) return null

            val icon =
                try {
                    val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return null
                    IconCompat.createWithAdaptiveBitmap(bitmap)
                } catch (_: Exception) {
                    return null
                }

            val label =
                meme.title
                    ?: meme.emojiTags.firstOrNull()?.emoji
                    ?: meme.fileName

            val intent =
                Intent(Intent.ACTION_SEND).apply {
                    setClassName(context.packageName, "com.adsamcik.riposte.MainActivity")
                    type = meme.mimeType
                    putExtra(EXTRA_SHORTCUT_MEME_ID, meme.id)
                }

            return ShortcutInfoCompat.Builder(context, shortcutId(meme.id))
                .setShortLabel(label)
                .setIcon(icon)
                .setIntent(intent)
                .setLongLived(true)
                .setCategories(setOf(CATEGORY_STICKER_SHARE))
                .build()
        }

        companion object {
            /** Maximum number of sharing shortcuts to push. */
            private const val MAX_SHORTCUTS = 8

            /** Category matching shortcuts.xml share-target. */
            private const val CATEGORY_STICKER_SHARE = "com.adsamcik.riposte.category.STICKER_SHARE"

            /** Extra key for passing meme ID through shortcut intent. */
            const val EXTRA_SHORTCUT_MEME_ID = "com.adsamcik.riposte.extra.MEME_ID"

            private fun shortcutId(memeId: Long) = "sticker_$memeId"
        }
    }
