package com.mememymood.feature.gallery.presentation.component

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
import com.mememymood.core.model.Meme
import com.mememymood.core.model.ShareTarget
import com.mememymood.feature.gallery.R
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
                text = stringResource(R.string.gallery_quick_share_title),
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
                    contentDescription = stringResource(R.string.gallery_quick_share_more),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.gallery_quick_share_more),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Well-known messaging app packages, used to prioritize these apps
 * in the share target list when there is no user usage data.
 */
@VisibleForTesting
internal val MESSAGING_PACKAGES = setOf(
    "com.whatsapp",
    "org.telegram.messenger",
    "com.discord",
    "com.facebook.orca",
    "com.google.android.apps.messaging",
    "org.thoughtcrime.securesms",
    "com.slack",
    "com.viber.voip",
    "com.instagram.android",
    "com.snapchat.android",
)

/**
 * Sorts share targets so that messaging apps appear first when there is no usage data.
 *
 * @param targets List of share targets to sort.
 * @return Sorted list with messaging apps prioritized when no usage data exists.
 */
@VisibleForTesting
internal fun prioritizeShareTargets(targets: List<ShareTarget>): List<ShareTarget> {
    val hasUsageData = targets.any { it.shareCount > 0 }
    if (hasUsageData) return targets
    return targets.sortedByDescending { it.packageName in MESSAGING_PACKAGES }
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

    // Check if user has any usage data
    val hasUsageData = frequentTargets.any { it.shareCount > 0 }

    // Resolve frequent targets (verify still installed)
    val resolved = mutableListOf<ResolvedTarget>()
    val seenPackages = mutableSetOf<String>()

    for (target in frequentTargets) {
        val resolveInfo = systemTargetsByPackage[target.packageName] ?: continue
        val icon = resolveInfo.loadIcon(pm)
        resolved.add(ResolvedTarget(target, icon))
        seenPackages.add(target.packageName)
        if (resolved.size >= maxItems) break
    }

    // Pad with system targets if needed
    if (resolved.size < maxItems) {
        // Sort remaining system targets: messaging apps first when no usage data
        val remainingInfos = resolvedInfos.filter { it.activityInfo.packageName !in seenPackages }
        val sortedInfos = if (hasUsageData) {
            remainingInfos
        } else {
            remainingInfos.sortedByDescending { it.activityInfo.packageName in MESSAGING_PACKAGES }
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
