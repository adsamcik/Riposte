package com.adsamcik.riposte.core.ui.component

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.net.Uri
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import coil3.compose.AsyncImage
import com.adsamcik.riposte.core.model.Meme
import com.adsamcik.riposte.core.model.ShareTarget
import com.adsamcik.riposte.core.ui.R
import java.io.File

/**
 * Resolved share target with its icon loaded from PackageManager.
 */
private data class ResolvedTarget(
    val target: ShareTarget,
    val icon: Drawable?,
)

/**
 * Bottom sheet for quick sharing a meme to a favorite app.
 *
 * Shows a meme preview, a row of most-used share target apps, and a
 * "More..." button that falls back to the system share sheet.
 *
 * @param meme The meme to share.
 * @param frequentTargets Most-used share targets from the database.
 * @param onTargetSelected Called when the user picks a specific app.
 * @param onMoreClick Called when the user taps "More..." (should open system chooser).
 * @param onDismiss Called when the sheet is dismissed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickShareBottomSheet(
    meme: Meme,
    frequentTargets: List<ShareTarget>,
    onTargetSelected: (ShareTarget) -> Unit,
    onMoreClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current

    // Resolve available targets: merge frequent targets with system-discovered ones
    val resolvedTargets = remember(frequentTargets) {
        resolveShareTargets(context, frequentTargets)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Meme preview thumbnail
            AsyncImage(
                model = File(meme.filePath),
                contentDescription = meme.title ?: meme.fileName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(80.dp)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(12.dp)),
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.quick_share_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Share target row
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(
                    items = resolvedTargets,
                    key = { it.target.packageName },
                ) { resolved ->
                    ShareTargetItem(
                        icon = resolved.icon,
                        label = resolved.target.displayLabel,
                        onClick = { onTargetSelected(resolved.target) },
                    )
                }

                // "More..." button
                item(key = "_more") {
                    MoreItem(onClick = onMoreClick)
                }
            }
        }
    }
}

@Composable
private fun ShareTargetItem(
    icon: Drawable?,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val iconSizePx = remember(density) { with(density) { 48.dp.roundToPx() } }

    Column(
        modifier = modifier
            .width(80.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (icon != null) {
            val bitmap = remember(icon, iconSizePx) {
                icon.toBitmap(iconSizePx, iconSizePx).asImageBitmap()
            }
            Image(
                bitmap = bitmap,
                contentDescription = label,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape),
            )
        } else {
            // Placeholder
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun MoreItem(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(80.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.size(48.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.MoreHoriz,
                    contentDescription = stringResource(R.string.quick_share_more),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.quick_share_more),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Well-known messaging app packages in default priority order.
 * This order is used as a tiebreaker when apps have equal usage frequency.
 */
@VisibleForTesting
internal val MESSAGING_PACKAGES = listOf(
    "com.whatsapp",                      // WhatsApp
    "org.telegram.messenger",            // Telegram
    "com.discord",                       // Discord
    "org.thoughtcrime.securesms",        // Signal
    "com.facebook.orca",                 // Messenger
    "com.google.android.apps.messaging", // Google Messages
    "com.slack",                         // Slack
    "com.microsoft.teams",              // Microsoft Teams
    "com.viber.voip",                    // Viber
    "com.instagram.android",             // Instagram
    "com.snapchat.android",              // Snapchat
    "com.twitter.android",               // X / Twitter
    "com.reddit.frontpage",              // Reddit
    "com.skype.raider",                  // Skype
    "com.google.android.gm",            // Gmail
    "com.kakao.talk",                    // KakaoTalk
    "jp.naver.line.android",             // LINE
    "com.tencent.mm",                    // WeChat
)

private val MESSAGING_PACKAGES_SET = MESSAGING_PACKAGES.toSet()

/**
 * Sorts share targets using a blended score of usage frequency and messaging app priority.
 * Usage frequency is primary; messaging app priority breaks ties and boosts new apps.
 */
@VisibleForTesting
internal fun prioritizeShareTargets(targets: List<ShareTarget>): List<ShareTarget> {
    return targets.sortedByDescending { target ->
        val messagingRank = MESSAGING_PACKAGES.indexOf(target.packageName)
        val messagingBoost = if (messagingRank >= 0) {
            // Higher boost for higher-priority messaging apps (index 0 = highest)
            (MESSAGING_PACKAGES.size - messagingRank).toFloat() / MESSAGING_PACKAGES.size
        } else {
            0f
        }
        // Usage count is primary (weighted 10x), messaging priority is tiebreaker
        target.shareCount * 10f + messagingBoost
    }
}

/**
 * Resolves share targets by merging frequent (DB-tracked) targets with
 * system-available share targets discovered via PackageManager.
 *
 * - Frequent targets are shown first (they already have usage data)
 * - If fewer than 6 frequent targets, pad with system-discovered apps
 * - Verifies that frequent targets are still installed
 * - When no usage data exists, messaging apps are prioritized
 */
private fun resolveShareTargets(
    context: Context,
    frequentTargets: List<ShareTarget>,
    maxItems: Int = 6,
): List<ResolvedTarget> {
    val pm = context.packageManager

    // Discover all apps that can handle image sharing
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "image/*"
    }
    @Suppress("DEPRECATION")
    val resolvedInfos: List<ResolveInfo> = pm.queryIntentActivities(shareIntent, PackageManager.MATCH_DEFAULT_ONLY)

    val systemTargetsByPackage = resolvedInfos.associateBy { it.activityInfo.packageName }

    // Resolve frequent targets (verify still installed)
    val resolved = mutableListOf<ResolvedTarget>()
    val seenPackages = mutableSetOf<String>()

    for (target in prioritizeShareTargets(frequentTargets)) {
        val resolveInfo = systemTargetsByPackage[target.packageName] ?: continue
        val icon = resolveInfo.loadIcon(pm)
        resolved.add(ResolvedTarget(target, icon))
        seenPackages.add(target.packageName)
        if (resolved.size >= maxItems) break
    }

    // Pad with system targets if needed
    if (resolved.size < maxItems) {
        // Sort remaining system targets by messaging app priority
        val remainingInfos = resolvedInfos.filter { it.activityInfo.packageName !in seenPackages }
        val sortedInfos = remainingInfos.sortedByDescending { info ->
            val idx = MESSAGING_PACKAGES.indexOf(info.activityInfo.packageName)
            if (idx >= 0) MESSAGING_PACKAGES.size - idx else 0
        }

        for (info in sortedInfos) {
            val pkg = info.activityInfo.packageName
            if (pkg in seenPackages) continue
            val label = info.loadLabel(pm).toString()
            val icon = info.loadIcon(pm)
            resolved.add(
                ResolvedTarget(
                    target = ShareTarget(
                        packageName = pkg,
                        activityName = info.activityInfo.name,
                        displayLabel = label,
                    ),
                    icon = icon,
                )
            )
            seenPackages.add(pkg)
            if (resolved.size >= maxItems) break
        }
    }

    return resolved
}
