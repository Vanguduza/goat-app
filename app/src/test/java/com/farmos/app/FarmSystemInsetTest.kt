package com.farmos.app

import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.core.design.*
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk=[36],qualifiers="en-rUS-w390dp-h900dp-mdpi")
class FarmSystemInsetTest {
    @get:Rule val compose=createComposeRule()
    @Test fun topAndSideSystemAreasAreReservedByTheCommonAppRoot() {
        compose.setContent { FarmOsTheme(mode=AnimalFarmThemeMode.DARK) {
            FarmAppViewport(insets=WindowInsets(left=7.dp,top=28.dp,right=5.dp,bottom=20.dp)) {
                Box(Modifier.fillMaxSize().testTag("actual-page-content"))
            }
        } }
        compose.onNodeWithTag("actual-page-content").assertPositionInRootIsEqualTo(7.dp,28.dp)
    }
}
