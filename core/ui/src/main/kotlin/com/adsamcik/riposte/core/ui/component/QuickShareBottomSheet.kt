package com.adsamcik.riposte.core.ui.component

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import androidx.annotation.VisibleForTesting
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
    val category: ShareTargetCategory = ShareTargetCategory.OTHER,
)

/**
 * Category for share targets, used to group apps in the expanded view.
 */
@VisibleForTesting
internal enum class ShareTargetCategory {
    MESSAGING,
    EMAIL,
    SOCIAL,
    OTHER,
}

/**
 * Bottom sheet for quick sharing a meme to a favorite app.
 *
 * Shows a meme preview and a grid of curated share target apps.
 * Collapsed state shows up to 8 prioritized apps (messaging first, then email).
 * Expanded state shows all installed share-capable apps grouped by category.
 *
 * @param meme The meme to share.
 * @param frequentTargets Most-used share targets from the database.
 * @param onTargetSelected Called when the user picks a specific app.
 * @param onMoreClick Called when the user taps "Use system share menu".
 * @param onCopyToClipboard Called when the user taps "Copy to clipboard".
 * @param onDismiss Called when the sheet is dismissed.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun QuickShareBottomSheet(
    meme: Meme,
    frequentTargets: List<ShareTarget>,
    onTargetSelected: (ShareTarget) -> Unit,
    onMoreClick: () -> Unit,
    onCopyToClipboard: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }

    val resolvedTargets = remember(frequentTargets) {
        resolveShareTargets(context, frequentTargets)
    }

    val allCategorizedTargets = remember(frequentTargets) {
        resolveAllShareTargets(context, frequentTargets)
    }

    val extraAppCount = remember(allCategorizedTargets, resolvedTargets) {
        allCategorizedTargets.values.sumOf { it.size } - resolvedTargets.size
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                )
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

            if (!expanded) {
                // Collapsed: 4-column grid of curated targets
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    maxItemsInEachRow = 4,
                ) {
                    resolvedTargets.forEach { resolved ->
                        ShareTargetItem(
                            icon = resolved.icon,
                            label = resolved.target.displayLabel,
                            onClick = { onTargetSelected(resolved.target) },
                        )
                    }
                }
            } else {
                // Expanded: categorized sections
                val categoryOrder = listOf(
                    ShareTargetCategory.MESSAGING,
                    ShareTargetCategory.EMAIL,
                    ShareTargetCategory.SOCIAL,
                    ShareTargetCategory.OTHER,
                )
                for (category in categoryOrder) {
                    val targets = allCategorizedTargets[category] ?: continue
                    if (targets.isEmpty()) continue

                    CategoryHeader(category)

                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        maxItemsInEachRow = 4,
                    ) {
                        targets.forEach { resolved ->
                            ShareTargetItem(
                                icon = resolved.icon,
                                label = resolved.target.displayLabel,
                                onClick = { onTargetSelected(resolved.target) },
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Expand / collapse toggle
            if (extraAppCount > 0) {
                TextButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (expanded) {
                            stringResource(R.string.quick_share_show_fewer)
                        } else {
                            stringResource(R.string.quick_share_show_all_apps, extraAppCount)
                        },
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Copy to clipboard
            TextButton(
                onClick = onCopyToClipboard,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = stringResource(R.string.quick_share_copy_clipboard))
            }

            // System share fallback
            TextButton(
                onClick = onMoreClick,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.quick_share_system_chooser),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CategoryHeader(
    category: ShareTargetCategory,
    modifier: Modifier = Modifier,
) {
    val label = when (category) {
        ShareTargetCategory.MESSAGING -> stringResource(R.string.quick_share_category_messaging)
        ShareTargetCategory.EMAIL -> stringResource(R.string.quick_share_category_email)
        ShareTargetCategory.SOCIAL -> stringResource(R.string.quick_share_category_social)
        ShareTargetCategory.OTHER -> stringResource(R.string.quick_share_category_other)
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
    )
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
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
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
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.size(48.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = label.take(1).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

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

// region Package lists

/**
 * Well-known messaging app packages in default priority order.
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
    "com.skype.raider",                  // Skype
    "com.kakao.talk",                    // KakaoTalk
    "jp.naver.line.android",             // LINE
    "com.tencent.mm",                    // WeChat
)

@VisibleForTesting
internal val EMAIL_PACKAGES = listOf(
    "com.google.android.gm",            // Gmail
    "com.microsoft.office.outlook",      // Outlook
    "com.yahoo.mobile.client.android.mail", // Yahoo Mail
    "com.samsung.android.email.provider", // Samsung Email
    "me.proton.android.mail",            // Proton Mail
)

@VisibleForTesting
internal val SOCIAL_PACKAGES = listOf(
    "com.instagram.android",             // Instagram
    "com.snapchat.android",              // Snapchat
    "com.twitter.android",               // X / Twitter
    "com.reddit.frontpage",              // Reddit
    "com.zhiliaoapp.musically",          // TikTok
    "com.facebook.katana",               // Facebook
)

private val MESSAGING_SET = MESSAGING_PACKAGES.toSet()
private val EMAIL_SET = EMAIL_PACKAGES.toSet()
private val SOCIAL_SET = SOCIAL_PACKAGES.toSet()

/**
 * All known packages combined, for priority scoring.
 */
private val ALL_KNOWN_PACKAGES = MESSAGING_PACKAGES + EMAIL_PACKAGES + SOCIAL_PACKAGES

// endregion

/**
 * Assigns a category to a package name based on known app lists.
 */
private fun categorize(packageName: String): ShareTargetCategory = when (packageName) {
    in MESSAGING_SET -> ShareTargetCategory.MESSAGING
    in EMAIL_SET -> ShareTargetCategory.EMAIL
    in SOCIAL_SET -> ShareTargetCategory.SOCIAL
    else -> ShareTargetCategory.OTHER
}

/**
 * Sorts share targets using a blended score of usage frequency and known-app priority.
 * Usage frequency is primary; messaging > email > social priority breaks ties.
 */
@VisibleForTesting
internal fun prioritizeShareTargets(targets: List<ShareTarget>): List<ShareTarget> {
    return targets.sortedByDescending { target ->
        val knownRank = ALL_KNOWN_PACKAGES.indexOf(target.packageName)
        val knownBoost = if (knownRank >= 0) {
            (ALL_KNOWN_PACKAGES.size - knownRank).toFloat() / ALL_KNOWN_PACKAGES.size
        } else {
            0f
        }
        target.shareCount * 10f + knownBoost
    }
}

/**
 * Resolves the curated set of share targets (collapsed view).
 * Merges frequent DB targets with system-discovered ones, up to [maxItems].
 * Messaging apps first, then email, then others by frequency.
 */
private fun resolveShareTargets(
    context: Context,
    frequentTargets: List<ShareTarget>,
    maxItems: Int = 8,
): List<ResolvedTarget> {
    val pm = context.packageManager
    val ownPackage = context.packageName
    val systemInfos = queryShareCapableApps(pm)
    val systemTargetsByPackage = systemInfos.associateBy { it.activityInfo.packageName }

    val resolved = mutableListOf<ResolvedTarget>()
    val seenPackages = mutableSetOf<String>()

    // Frequent targets first (verify still installed)
    for (target in prioritizeShareTargets(frequentTargets)) {
        if (target.packageName == ownPackage) continue
        val resolveInfo = systemTargetsByPackage[target.packageName] ?: continue
        resolved.add(ResolvedTarget(target, resolveInfo.loadIcon(pm), categorize(target.packageName)))
        seenPackages.add(target.packageName)
        if (resolved.size >= maxItems) break
    }

    // Pad with system targets if needed, prioritizing known apps
    if (resolved.size < maxItems) {
        val remaining = systemInfos
            .filter { it.activityInfo.packageName !in seenPackages && it.activityInfo.packageName != ownPackage }
            .sortedByDescending { info ->
                val idx = ALL_KNOWN_PACKAGES.indexOf(info.activityInfo.packageName)
                if (idx >= 0) ALL_KNOWN_PACKAGES.size - idx else 0
            }

        for (info in remaining) {
            val pkg = info.activityInfo.packageName
            if (pkg in seenPackages) continue
            resolved.add(
                ResolvedTarget(
                    target = ShareTarget(
                        packageName = pkg,
                        activityName = info.activityInfo.name,
                        displayLabel = info.loadLabel(pm).toString(),
                    ),
                    icon = info.loadIcon(pm),
                    category = categorize(pkg),
                ),
            )
            seenPackages.add(pkg)
            if (resolved.size >= maxItems) break
        }
    }

    return resolved
}

/**
 * Resolves ALL share-capable apps grouped by category (expanded view).
 * Frequent targets are boosted within each category.
 */
private fun resolveAllShareTargets(
    context: Context,
    frequentTargets: List<ShareTarget>,
): Map<ShareTargetCategory, List<ResolvedTarget>> {
    val pm = context.packageManager
    val ownPackage = context.packageName
    val systemInfos = queryShareCapableApps(pm)
    val frequentByPackage = frequentTargets.associateBy { it.packageName }

    val all = systemInfos
        .filter { it.activityInfo.packageName != ownPackage }
        .map { info ->
            val pkg = info.activityInfo.packageName
            val tracked = frequentByPackage[pkg]
            ResolvedTarget(
                target = tracked ?: ShareTarget(
                    packageName = pkg,
                    activityName = info.activityInfo.name,
                    displayLabel = info.loadLabel(pm).toString(),
                ),
                icon = info.loadIcon(pm),
                category = categorize(pkg),
            )
        }

    return all
        .groupBy { it.category }
        .mapValues { (_, targets) ->
            targets.sortedByDescending { resolved ->
                val knownRank = ALL_KNOWN_PACKAGES.indexOf(resolved.target.packageName)
                val knownBoost = if (knownRank >= 0) {
                    (ALL_KNOWN_PACKAGES.size - knownRank).toFloat() / ALL_KNOWN_PACKAGES.size
                } else {
                    0f
                }
                resolved.target.shareCount * 10f + knownBoost
            }
        }
}

private fun queryShareCapableApps(pm: PackageManager): List<ResolveInfo> {
    val shareIntent = Intent(Intent.ACTION_SEND).apply { type = "image/*" }
    @Suppress("DEPRECATION")
    return pm.queryIntentActivities(shareIntent, PackageManager.MATCH_DEFAULT_ONLY)
}
