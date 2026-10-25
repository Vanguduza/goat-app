package com.farmos.core.design

import androidx.compose.foundation.layout.size

import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource

/** Exact Lucide path geometry extracted from the approved HTML; ISC license retained in res/raw. */
@Composable
fun FarmHtmlIcon(name: String, contentDescription: String?, modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) {
    val resource = when (name) {
        "arrow-left" -> R.drawable.af_html_arrow_left
        "arrow-right" -> R.drawable.af_html_arrow_right
        "arrow-up-right" -> R.drawable.af_html_arrow_up_right
        "book-open" -> R.drawable.af_html_book_open
        "boxes" -> R.drawable.af_html_boxes
        "chart-no-axes-combined" -> R.drawable.af_html_chart_no_axes_combined
        "circle-plus" -> R.drawable.af_html_circle_plus
        "clipboard-list" -> R.drawable.af_html_clipboard_list
        "heart" -> R.drawable.af_html_heart
        "house" -> R.drawable.af_html_house
        "layout-grid" -> R.drawable.af_html_layout_grid
        "message-square-plus" -> R.drawable.af_html_message_square_plus
        "moon" -> R.drawable.af_html_moon
        "package" -> R.drawable.af_html_package
        "paw-print" -> R.drawable.af_html_paw_print
        "plus" -> R.drawable.af_html_plus
        "scale" -> R.drawable.af_html_scale
        "settings-2" -> R.drawable.af_html_settings_2
        "sprout" -> R.drawable.af_html_sprout
        "sun" -> R.drawable.af_html_sun
        "users" -> R.drawable.af_html_users
        "wallet" -> R.drawable.af_html_wallet
        "x" -> R.drawable.af_html_x
        else -> error("Unregistered approved HTML icon: $name")
    }
    Icon(painterResource(resource), contentDescription, modifier.size(AnimalFarmHomeMetrics.lineIconSize), tint)
}
