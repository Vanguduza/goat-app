package com.farmos.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import com.farmos.review.NativeModuleReviewTheme as SharedReviewTheme
import com.farmos.review.captureModuleReview as captureSharedReview
import com.farmos.review.captureCurrentModuleReview as captureSharedCurrent
import com.farmos.review.captureObservedNativeOwners as captureSharedObserved

/** Test-only shared observer, compiled in test source sets, never packaged into the app. */
typealias NativeModuleReviewRule = com.farmos.review.NativeModuleReviewRule
@Composable internal fun NativeModuleReviewTheme(content: @Composable () -> Unit) = SharedReviewTheme(content)
internal fun ComposeContentTestRule.captureModuleReview(screenId: String) = captureSharedReview(screenId)
internal fun ComposeContentTestRule.captureCurrentModuleReview() = captureSharedCurrent()
internal fun ComposeContentTestRule.captureObservedNativeOwners() = captureSharedObserved()
