package com.farmos.feature.goat

/** Maps the public host entry route to the goat experience's internal page model. */
internal fun GoatEntryPage.toGoatPage(): GoatPage =
    when (this) {
        GoatEntryPage.DASHBOARD -> GoatPage.DASHBOARD
        GoatEntryPage.WEIGHT -> GoatPage.WEIGHT
        GoatEntryPage.SEARCH -> GoatPage.SEARCH
        GoatEntryPage.SCAN -> GoatPage.SCAN
        GoatEntryPage.SYNC -> GoatPage.SYNC
        GoatEntryPage.KIDDING -> GoatPage.KIDDING
        GoatEntryPage.REPRODUCTION -> GoatPage.REPRODUCTION
    }
