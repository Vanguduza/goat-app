package com.farmos.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import com.farmos.feature.ops.PastureRecordNavigator

@Composable
internal fun PastureModuleContent(state: PastureModuleState) {
    val retainedPages = rememberSaveableStateHolder()
    with(state) {
        retainedPages.SaveableStateProvider(page.name) {
            when (page) {
                PastureModulePage.HOME -> PastureRecordNavigator(records) { recordActions -> PastureHomeContent(state, recordActions) }
                PastureModulePage.REST -> PastureRestPeriodScreen(ops = ops, onBack = { page = PastureModulePage.HOME })
                PastureModulePage.CAPACITY -> PastureCarryingCapacityScreen(ops = ops, onBack = { page = PastureModulePage.HOME })
                PastureModulePage.CONDITION -> PastureConditionScreen(ops = ops, onBack = { page = PastureModulePage.HOME })
                PastureModulePage.REPORT -> PastureReportScreen(ops = ops, onBack = { page = PastureModulePage.HOME })
                PastureModulePage.MAP -> PastureMapScreen(ops = ops, farmId = farmId, onBack = { page = PastureModulePage.HOME })
            }
        }
    }
}
