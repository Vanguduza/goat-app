package com.farmos.design.patterns

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.farmos.design.R
import com.farmos.design.components.*
import com.farmos.design.theme.*
import com.farmos.model.AnimalProfilePreview
import com.farmos.model.LoadState

/** TimelineScreen identity header with an explicitly transient gallery photo flow. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimalProfile(profile: AnimalProfilePreview, state: LoadState, currentPhoto: ImageBitmap?, candidate: ImageBitmap?,
    loading: Boolean, photoError: String?, onPick: () -> Unit, onUse: () -> Unit, onCancel: () -> Unit,
    onRemove: () -> Unit, onWeight: () -> Unit, onRetry: () -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    var confirmRemoval by remember { mutableStateOf(false) }
    WorkspacePage(stringResource(R.string.animal_profile), onBack, modifier) {
        if (state != LoadState.Idle) FosLoadableState(state, "", {}, onRetry, genericContent = true)
        else {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                AnimalPortrait(profile.species, Modifier.size(HomeTokens.profilePortrait), currentPhoto,
                    stringResource(if (currentPhoto == null) R.string.family_portrait_description else R.string.personal_photo_description, profile.name))
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(FosSpace.medium)) {
                Text(profile.name, Modifier.weight(1f), style = FosText.display, color = FosTheme.colors.text)
                AnimalPortrait(profile.species, Modifier.size(HomeTokens.badgePortrait))
                FosLabel(familyName(profile.species))
            }
            HomeAction(stringResource(if (currentPhoto == null) R.string.add_photo else R.string.change_photo), onPick,
                Modifier.fillMaxWidth().testTag("photo-pick"), enabled = !loading)
            if (loading) FosLabel(stringResource(R.string.preparing_photo), Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            photoError?.let { Text(it, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = FosText.body, color = FosTheme.colors.critical) }
            if (currentPhoto != null) FosAction(stringResource(R.string.remove_photo), { confirmRemoval = true }, Modifier.testTag("photo-remove"))
            FosLabel(stringResource(R.string.photo_preview_only))
            FosCard {
                FosLabel(stringResource(R.string.weight)); FosBody(profile.lastWeight)
                FosRule()
                FosLabel(stringResource(R.string.body_condition)); FosBody(profile.bodyCondition)
            }
            FosAction(stringResource(R.string.record_weight), onWeight)
        }
    }
    if (candidate != null || confirmRemoval) {
        val close = { confirmRemoval = false; onCancel() }
        ModalBottomSheet(onDismissRequest = close, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(FosSpace.page),
                verticalArrangement = Arrangement.spacedBy(FosSpace.page), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(if (confirmRemoval) R.string.confirm_remove_photo else R.string.review_photo), style = FosText.titleLg)
                Text(profile.name, style = FosText.bodyStrong)
                AnimalPortrait(profile.species, Modifier.size(HomeTokens.profilePortrait), if (confirmRemoval) null else candidate)
                HomeAction(stringResource(if (confirmRemoval) R.string.remove_photo else R.string.use_photo), {
                    if (confirmRemoval) { onRemove(); confirmRemoval = false } else onUse()
                }, Modifier.fillMaxWidth().testTag("photo-confirm"))
                FosAction(stringResource(R.string.cancel), close, Modifier.testTag("photo-cancel"))
            }
        }
    }
}
