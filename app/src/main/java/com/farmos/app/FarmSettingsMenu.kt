package com.farmos.app

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.farmos.core.design.AnimalFarmTheme
import com.farmos.core.design.FarmModuleTools
import com.farmos.core.design.FarmToolAction
import com.farmos.core.design.FarmToolGroup

/** FOS-ADMIN-001 / P14. Each key resolves to the existing authorised SettingsHost destination. */
@Composable
internal fun FarmSettingsMenu(displayName: String, roleLabel: String, onOpen: (String) -> Unit) {
    Text("Signed in as $displayName · $roleLabel", color = AnimalFarmTheme.colors.mutedInk, modifier = Modifier.testTag("settings-signed-in"))
    fun action(label: String, key: String) = FarmToolAction(label, { onOpen(key) }, tag = "settings-action:$key")
    FarmModuleTools(
        title = "Manage",
        groups = listOf(
            FarmToolGroup("Farm", listOf(
                action("Farm profile", "PROFILE"), action("Species configuration", "SPECIES"),
                action("Poultry kinds", "POULTRY_KINDS"), action("Locations", "LOCATIONS"),
                action("Currency", "CURRENCY"), action("Units", "UNITS"),
            )),
            FarmToolGroup("People and access", listOf(
                action("Accounts and access", "MEMBERS"), action("Roles and permissions", "PERMISSIONS"),
                action("Access history", "AUDIT"), action("Security", "SECURITY"),
            )),
            FarmToolGroup("Devices and records", listOf(
                action("Offline and sync", "SYNC_SETTINGS"), action("Devices", "DEVICES"),
                action("Storage and backup", "STORAGE"), action("Data export", "DATA_EXPORT"),
                action("Search settings", "SEARCH_SETTINGS"),
            )),
            FarmToolGroup("Connections", listOf(
                action("Integrations", "INTEGRATIONS"), action("Hardware", "HARDWARE"),
                action("App permissions", "PERM_NOTIFICATIONS"), action("Notifications", "NOTIFICATIONS"),
            )),
            FarmToolGroup("About", listOf(
                action("Terms and privacy", "TERMS"), action("About", "ABOUT"),
            )),
        ),
    )
}
