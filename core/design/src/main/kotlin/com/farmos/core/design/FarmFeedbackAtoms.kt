package com.farmos.core.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * P15 sync, recovery and transient feedback atoms. Each renders inside its owning page and takes
 * that page's identity; the `farm-atom:` tag names the canonical atom Screen ID it realises.
 */
object FarmFeedbackAtoms {
    const val LOADING_SKELETON = "farm-atom:FOS-ATOM-032"
    const val ERROR_RECOVERY = "farm-atom:FOS-ATOM-033"
    const val OFFLINE_SAVE_RECEIPT = "farm-atom:FOS-ATOM-034"
    const val SYNC_PENDING_RECEIPT = "farm-atom:FOS-ATOM-035"
}

/**
 * FOS-ATOM-032 — layout-preserving loading placeholder. [label] stays visible and readable to
 * assistive technology; the bars only hold the space the loaded content will take.
 */
@Composable
fun FarmLoadingSkeleton(label: String, modifier: Modifier = Modifier, rows: Int = 3) {
    val colors = AnimalFarmTheme.colors
    Column(
        modifier.fillMaxWidth().testTag(FarmFeedbackAtoms.LOADING_SKELETON),
        verticalArrangement = Arrangement.spacedBy(FosDimens.Grid * 2),
    ) {
        Text(label, color = colors.mutedInk)
        repeat(rows) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(FosDimens.Grid * 4)
                    .clip(RoundedCornerShape(AnimalFarmHomeMetrics.actionRadius))
                    .background(colors.softSurface),
            )
        }
    }
}

/**
 * FOS-ATOM-033 — a failed read, stated plainly, with a retry that repeats the same read. Retrying
 * a read never repeats a write, so no duplicate record can result.
 */
@Composable
fun FarmErrorRecovery(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier, retryLabel: String = "Try again") {
    AnimalFarmWarningSurface(modifier.testTag(FarmFeedbackAtoms.ERROR_RECOVERY)) {
        Text(message)
        TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = AnimalFarmTheme.minimumTouchDp.dp)) { Text(retryLabel) }
    }
}

/**
 * FOS-ATOM-034 — truthful receipt after a local save: the record is committed on this device and
 * queued for sync. It never claims the farm server has the change.
 */
@Composable
fun FarmOfflineSaveReceipt(modifier: Modifier = Modifier, text: String = "Saved on this device · waiting to sync") {
    FarmReceiptSurface(FarmFeedbackAtoms.OFFLINE_SAVE_RECEIPT, modifier) {
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

/** FOS-ATOM-035 — how many changes saved on this device are still waiting to sync. */
@Composable
fun FarmSyncPendingReceipt(pendingCount: Long, modifier: Modifier = Modifier) {
    FarmReceiptSurface(FarmFeedbackAtoms.SYNC_PENDING_RECEIPT, modifier) {
        Text(
            "$pendingCount " + (if (pendingCount == 1L) "change" else "changes") + " saved on this device " +
                (if (pendingCount == 1L) "is" else "are") + " waiting to sync",
            fontWeight = FontWeight.SemiBold,
        )
        Text("Nothing is lost while offline; sync sends these when the farm server is reachable.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun FarmReceiptSurface(tag: String, modifier: Modifier, content: @Composable () -> Unit) {
    val colors = AnimalFarmTheme.colors
    Surface(
        modifier = modifier.fillMaxWidth().testTag(tag).semantics { liveRegion = LiveRegionMode.Polite },
        shape = RoundedCornerShape(AnimalFarmHomeMetrics.tileRadius),
        color = colors.softSurface,
        contentColor = colors.ink,
        border = BorderStroke(FosDimens.Hairline, colors.divider),
    ) {
        Column(Modifier.padding(FosDimens.CardPadding), verticalArrangement = Arrangement.spacedBy(FosDimens.Grid)) { content() }
    }
}
