package com.farmos.app

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.farmos.core.database.AccessAuditEntity
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.database.ReplicationDeviceEntity
import com.farmos.core.design.AnimalFarmQuickAction
import com.farmos.core.design.AnimalFarmTheme
import com.farmos.core.design.FarmEntitySelector
import com.farmos.core.design.FarmOperationalPage
import com.farmos.core.design.FarmOperationalSection
import com.farmos.core.design.FarmPermissionExplanation
import com.farmos.core.design.FarmSearchPage
import com.farmos.core.design.FarmSearchSelector
import com.farmos.core.design.FarmSelectorSearch
import com.farmos.core.design.FarmSelectorOption
import com.farmos.domain.access.AccessDenied
import com.farmos.domain.access.AccountStatus
import com.farmos.domain.access.Credential
import com.farmos.domain.access.CredentialKind
import com.farmos.domain.access.LocalAccount
import com.farmos.domain.access.LocalRole
import com.farmos.domain.access.Permission
import com.farmos.domain.access.RolePermissions
import com.farmos.domain.ops.FarmCurrency
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class SettingsPage { HOME, MEMBERS, CREATE_MEMBER, MEMBER_DETAIL, PERMISSIONS, AUDIT, STORAGE, DEVICES, CURRENCY }

/** Everything the settings pages show, read from this device's database in one pass. */
private data class SettingsSnapshot(
    val actor: LocalAccount?,
    val accounts: List<LocalAccount>,
    val audit: List<AccessAuditEntity>,
    val auditTotal: Long,
    val devices: List<ReplicationDeviceEntity>,
    val journalCount: Long,
    val currencyCode: String,
)

/**
 * Farm settings for local accounts: Accounts & Access (FOS-ADMIN-003/004/005/006/024), Storage & Backup
 * (FOS-ADMIN-023) and Devices (FOS-ADMIN-021) under Settings Home (FOS-ADMIN-001). Every action runs
 * through the local access service, which authorises it against the signed-in account and audits it.
 */
@Composable
internal fun SettingsHost(
    directory: LocalFarmDirectory,
    database: FarmOsDatabase,
    farmId: String,
    actorId: String?,
    deviceId: String,
    onBack: () -> Unit,
    io: CoroutineDispatcher = Dispatchers.IO,
) {
    val scope = rememberCoroutineScope()
    var page by remember { mutableStateOf(SettingsPage.HOME) }
    var selectedAccountId by remember { mutableStateOf<String?>(null) }
    var snapshot by remember { mutableStateOf<SettingsSnapshot?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(farmId, actorId, refreshKey) {
        snapshot = withContext(io) {
            val access = database.localAccess()
            SettingsSnapshot(
                actor = actorId?.let { directory.account(farmId, it) },
                accounts = directory.accounts(farmId),
                audit = access.audit(farmId, AUDIT_PAGE),
                auditTotal = access.auditCount(farmId),
                devices = database.replication().devices(farmId),
                journalCount = database.replication().count(farmId),
                currencyCode = database.farmCurrency(farmId),
            )
        }
    }

    fun act(onDone: () -> Unit = {}, block: suspend (LocalAccount) -> Unit) {
        val actor = snapshot?.actor ?: return
        scope.launch {
            busy = true
            error = null
            runCatching { withContext(io) { block(actor) } }
                .onSuccess { onDone() }
                .onFailure { error = it.message ?: "The change could not be saved on this device" }
            refreshKey++
            busy = false
        }
    }

    val loaded = snapshot != null
    val current = snapshot ?: SettingsSnapshot(null, emptyList(), emptyList(), 0, emptyList(), 0, FarmCurrency.DEFAULT_CODE)
    val home = { page = SettingsPage.HOME; error = null }
    when (page) {
        SettingsPage.HOME -> FarmOperationalPage("FOS-ADMIN-001", "Farm settings", "Accounts, storage and devices on this farm.", onBack = onBack) {
            if (!loaded) {
                Text("Loading settings saved on this device", color = AnimalFarmTheme.colors.mutedInk)
                return@FarmOperationalPage
            }
            val actor = current.actor
            if (actor == null) {
                FarmPermissionExplanation("Local settings", "Settings are managed by local farm accounts. Sign in with a farm account on this device to use them.")
                return@FarmOperationalPage
            }
            Text("Signed in as ${actor.displayName} · ${roleLabel(actor.role)}", modifier = Modifier.testTag("settings-signed-in"))
            AnimalFarmQuickAction("Accounts and access", { page = SettingsPage.MEMBERS })
            AnimalFarmQuickAction("Roles and permissions", { page = SettingsPage.PERMISSIONS })
            AnimalFarmQuickAction("Access history", { page = SettingsPage.AUDIT })
            AnimalFarmQuickAction("Currency", { page = SettingsPage.CURRENCY })
            AnimalFarmQuickAction("Storage and backup", { page = SettingsPage.STORAGE })
            AnimalFarmQuickAction("Devices", { page = SettingsPage.DEVICES })
        }
        SettingsPage.MEMBERS -> FarmOperationalPage("FOS-ADMIN-003", "Accounts and access", "People who can sign in to this farm.", onBack = home, backLabel = "Farm settings") {
            val actor = current.actor
            if (actor == null || !RolePermissions.allows(actor.role, Permission.MANAGE_ACCOUNTS)) {
                FarmPermissionExplanation("Accounts are managed by farm management", "Only Owner and Manager accounts can add or change accounts. Ask a manager if you need access.")
                return@FarmOperationalPage
            }
            FarmOperationalSection("${current.accounts.size} account(s)") {
                current.accounts.forEach { account ->
                    AnimalFarmQuickAction(
                        "${account.displayName} · ${roleLabel(account.role)}${if (account.status == AccountStatus.DISABLED) " · disabled" else ""}",
                        {
                            selectedAccountId = account.accountId
                            page = SettingsPage.MEMBER_DETAIL
                        },
                    )
                }
            }
            SettingsButton("Add account", !busy) { page = SettingsPage.CREATE_MEMBER }
        }
        SettingsPage.CREATE_MEMBER -> FarmOperationalPage("FOS-ADMIN-004", "Add account", "Create a local account for someone who works on this farm.", onBack = { page = SettingsPage.MEMBERS }, backLabel = "Accounts and access") {
            val actor = current.actor ?: return@FarmOperationalPage
            SettingsError(error)
            CreateAccountForm(assignableRoles(actor), busy) { displayName, username, role, pin ->
                act(onDone = { page = SettingsPage.MEMBERS }) { actor -> directory.transact { it.createAccount(actor, username, displayName, role, Credential(CredentialKind.PIN, pin)) } }
            }
        }
        SettingsPage.MEMBER_DETAIL -> FarmOperationalPage("FOS-ADMIN-005", "Account and role", "Role, status and PIN for one account.", onBack = { page = SettingsPage.MEMBERS }, backLabel = "Accounts and access") {
            val actor = current.actor ?: return@FarmOperationalPage
            val subject = current.accounts.firstOrNull { it.accountId == selectedAccountId } ?: return@FarmOperationalPage
            SettingsError(error)
            MemberDetail(
                subject = subject,
                roles = assignableRoles(actor),
                busy = busy,
                onRole = { role -> act { actor -> directory.transact { it.changeRole(actor, subject.accountId, role) } } },
                onStatus = { status -> act { actor -> directory.transact { it.setStatus(actor, subject.accountId, status) } } },
                onResetPin = { pin -> act { actor -> directory.transact { it.resetCredential(actor, subject.accountId, Credential(CredentialKind.PIN, pin)) } } },
            )
        }
        SettingsPage.PERMISSIONS -> FarmOperationalPage("FOS-ADMIN-006", "Roles and permissions", "What each role may do on this farm.", onBack = home, backLabel = "Farm settings") {
            LocalRole.entries.forEach { role ->
                FarmOperationalSection(roleLabel(role)) {
                    RolePermissions.of(role).sortedBy { it.ordinal }.forEach { Text(permissionLabel(it)) }
                }
            }
        }
        SettingsPage.AUDIT -> FarmOperationalPage("FOS-ADMIN-024", "Access history", "Sign-ins and account changes on this farm.", onBack = home, backLabel = "Farm settings") {
            val actor = current.actor
            if (actor == null || !RolePermissions.allows(actor.role, Permission.MANAGE_ACCOUNTS)) {
                FarmPermissionExplanation("Access history is for farm management", "Only Owner and Manager accounts can review access history.")
                return@FarmOperationalPage
            }
            val shown = current.audit
            Text(
                if (current.auditTotal > shown.size) "Latest ${shown.size} of ${current.auditTotal} entries" else "${current.auditTotal} entries",
                color = AnimalFarmTheme.colors.mutedInk,
                modifier = Modifier.testTag("settings-audit-count"),
            )
            FarmOperationalSection("Entries") {
                shown.forEach { event ->
                    val subject = current.accounts.firstOrNull { it.accountId == event.subjectAccountId }?.displayName
                    Text(listOfNotNull(timestamp(event.atEpochMillis), auditLabel(event.action), subject, event.detail).joinToString(" · "))
                }
            }
        }
        SettingsPage.STORAGE -> FarmOperationalPage("FOS-ADMIN-023", "Storage and backup", "Where this farm's records are kept and copied.", onBack = home, backLabel = "Farm settings") {
            val actor = current.actor
            if (actor == null || !RolePermissions.allows(actor.role, Permission.MANAGE_STORAGE_AND_BACKUP)) {
                FarmPermissionExplanation("Storage is managed by farm management", "Only Owner and Manager accounts can change storage and backup.")
                return@FarmOperationalPage
            }
            FarmOperationalSection("Saved on this device") {
                Text("${current.journalCount} operation(s) recorded in this device's replication journal.", modifier = Modifier.testTag("settings-journal-count"))
                Text("Farm work is always saved here first and never waits for a network.", color = AnimalFarmTheme.colors.mutedInk)
            }
            FarmOperationalSection("Farm network sync") { Text("Not set up. No other farm devices are paired with this one.") }
            FarmOperationalSection("Google Drive") {
                Text("Not connected.")
                Text("Farm work continues on this device. Disconnecting Google Drive never deletes farm records on this device.", color = AnimalFarmTheme.colors.mutedInk)
            }
            FarmOperationalSection("Backup") { Text("No backup has been made.") }
        }
        SettingsPage.CURRENCY -> FarmOperationalPage("FOS-ADMIN-011", "Currency", "The currency new money records use on this farm.", onBack = home, backLabel = "Farm settings") {
            val actor = current.actor
            // Owner decision D-018: the farm currency is owner-changeable.
            if (actor == null || actor.role != LocalRole.OWNER) {
                FarmPermissionExplanation("Currency is set by the farm owner", "Only Owner accounts can change the farm currency.")
                return@FarmOperationalPage
            }
            SettingsError(error)
            CurrencyChoice(current.currencyCode, busy) { code ->
                act {
                    if (it.role != LocalRole.OWNER) throw AccessDenied("Only the farm owner can change the farm currency")
                    database.setFarmCurrency(farmId, code, it.accountId, deviceId)
                }
            }
        }
        SettingsPage.DEVICES -> FarmOperationalPage("FOS-ADMIN-021", "Devices", "This device and the farm devices it knows.", onBack = home, backLabel = "Farm settings") {
            val actor = current.actor
            if (actor == null || !RolePermissions.allows(actor.role, Permission.MANAGE_DEVICES)) {
                FarmPermissionExplanation("Devices are managed by farm management", "Only Owner and Manager accounts can review farm devices.")
                return@FarmOperationalPage
            }
            val local = current.devices.firstOrNull { it.deviceId == deviceId }
            FarmOperationalSection("This device") {
                Text("Device ID $deviceId", modifier = Modifier.testTag("settings-this-device"))
                Text("${local?.lastReportedOwnSequence ?: 0} operation(s) issued by this device")
            }
            FarmOperationalSection("Known farm devices") {
                val others = current.devices.filter { it.deviceId != deviceId }
                if (others.isEmpty()) Text("No other devices yet.", color = AnimalFarmTheme.colors.mutedInk)
                others.forEach { Text("${it.name} · ${it.status.lowercase().replace('_', ' ')} · ${it.lastReportedOwnSequence} operation(s)") }
            }
        }
    }
}

@Composable
private fun SettingsError(error: String?) {
    error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("settings-error")) }
}

@Composable
private fun CurrencyChoice(currentCode: String, busy: Boolean, onSave: (String) -> Unit) {
    var chosen by remember(currentCode) { mutableStateOf(currencyOption(Currency.getInstance(currentCode))) }
    FarmOperationalSection("Farm currency", "New sales, purchases and money records use this currency. Amounts already recorded keep their own currency and are never converted.") {
        Text("Records are kept in ${currencyOption(Currency.getInstance(currentCode)).label}", modifier = Modifier.testTag("settings-currency-current"))
        FarmSearchSelector(
            atomTag = "settings-currency",
            title = "Currency",
            search = CurrencySearch,
            selected = chosen,
            onSelect = { chosen = it },
            emptyText = "No currency matches",
            enabled = !busy,
            searchLabel = "Search by code or name",
        )
        SettingsButton("Save currency", !busy && chosen.id != currentCode) { onSave(chosen.id) }
    }
}

/** Every recordable ISO 4217 currency, searched by code or name; a stable instance so the selector does not restart. */
private val CurrencySearch = FarmSelectorSearch { query, offset, limit ->
    val matches = FarmCurrency.search(query)
    FarmSearchPage(matches.drop(offset).take(limit).map(::currencyOption), hasMore = matches.size > offset + limit)
}

private fun currencyOption(currency: Currency) =
    FarmSelectorOption(currency.currencyCode, "${currency.currencyCode} · ${currency.getDisplayName(Locale.ENGLISH)}")

@Composable
private fun CreateAccountForm(roles: List<LocalRole>, busy: Boolean, onCreate: (String, String, LocalRole, String) -> Unit) {
    var displayName by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var role by remember { mutableStateOf(LocalRole.WORKER) }
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    FarmOperationalSection("New account") {
        SettingsField("Name", displayName, busy) { displayName = it }
        SettingsField("Username", username, busy) { username = it }
        RoleChoice(roles, role, busy) { role = it }
        SettingsPin("PIN (6 to 12 digits)", pin, busy) { pin = it }
        SettingsPin("Confirm PIN", confirm, busy) { confirm = it }
        SettingsButton("Create account", !busy && displayName.isNotBlank() && username.isNotBlank() && pin.isNotEmpty() && pin == confirm) {
            onCreate(displayName, username, role, pin)
        }
    }
}

@Composable
private fun MemberDetail(
    subject: LocalAccount,
    roles: List<LocalRole>,
    busy: Boolean,
    onRole: (LocalRole) -> Unit,
    onStatus: (AccountStatus) -> Unit,
    onResetPin: (String) -> Unit,
) {
    var role by remember(subject.accountId, subject.role) { mutableStateOf(subject.role) }
    var pin by remember(subject.accountId) { mutableStateOf("") }
    FarmOperationalSection(subject.displayName) {
        Text("Username ${subject.username}")
        Text("${roleLabel(subject.role)} · ${if (subject.status == AccountStatus.ACTIVE) "active" else "disabled"}", modifier = Modifier.testTag("settings-member-state"))
        subject.workerId?.let { Text("Linked to worker record $it") }
    }
    FarmOperationalSection("Role") {
        RoleChoice((roles + subject.role).distinct(), role, busy) { role = it }
        SettingsButton("Save role", !busy && role != subject.role) { onRole(role) }
    }
    FarmOperationalSection("Status") {
        if (subject.status == AccountStatus.ACTIVE) {
            SettingsButton("Disable account", !busy) { onStatus(AccountStatus.DISABLED) }
        } else {
            SettingsButton("Enable account", !busy) { onStatus(AccountStatus.ACTIVE) }
        }
    }
    FarmOperationalSection("Reset PIN") {
        SettingsPin("New PIN (6 to 12 digits)", pin, busy) { pin = it }
        SettingsButton("Reset PIN", !busy && pin.isNotEmpty()) {
            onResetPin(pin)
            pin = ""
        }
    }
}

@Composable
private fun RoleChoice(roles: List<LocalRole>, selected: LocalRole, busy: Boolean, onSelect: (LocalRole) -> Unit) {
    FarmEntitySelector(
        atomTag = "settings-role",
        title = "Role",
        options = roles.map { FarmSelectorOption(it.name, roleLabel(it)) },
        selectedId = selected.name,
        onSelect = { onSelect(LocalRole.valueOf(it)) },
        emptyText = "No roles available",
        enabled = !busy,
    )
}

@Composable
private fun SettingsField(label: String, value: String, busy: Boolean, onChange: (String) -> Unit) {
    OutlinedTextField(value, onChange, label = { Text(label) }, singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun SettingsPin(label: String, value: String, busy: Boolean, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { typed -> onChange(typed.filter(Char::isDigit).take(12)) },
        label = { Text(label) },
        singleLine = true,
        enabled = !busy,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SettingsButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = AnimalFarmTheme.minimumTouchDp.dp)) { Text(label) }
}

/** Roles [actor] may assign: management assigns every role below Owner; only Owners assign Owner. */
private fun assignableRoles(actor: LocalAccount): List<LocalRole> =
    LocalRole.entries.filter { it != LocalRole.OWNER || RolePermissions.allows(actor.role, Permission.MANAGE_OWNERS) }

private fun roleLabel(role: LocalRole): String = role.name.lowercase().replaceFirstChar { it.uppercase() }

private fun permissionLabel(permission: Permission): String = when (permission) {
    Permission.VIEW_FARM -> "View farm records"
    Permission.RECORD_FARM_WORK -> "Record farm work"
    Permission.CAPTURE_STOCK_COUNT -> "Count stock"
    Permission.REVIEW_WORK -> "Review recorded work"
    Permission.MANAGE_WORKERS -> "Manage worker records"
    Permission.POST_STOCK_ADJUSTMENT -> "Post stock adjustments"
    Permission.MANAGE_ACCOUNTS -> "Add and change accounts"
    Permission.RESET_USER_CREDENTIALS -> "Reset PINs and passwords"
    Permission.MANAGE_DEVICES -> "Manage farm devices"
    Permission.APPROVE_DEVICE_PAIRING -> "Approve new devices"
    Permission.MANAGE_STORAGE_AND_BACKUP -> "Manage storage and backup"
    Permission.RESOLVE_SYNC_CONFLICTS -> "Resolve sync conflicts"
    Permission.MANAGE_FARM_SETTINGS -> "Change farm settings"
    Permission.MANAGE_OWNERS -> "Manage Owner accounts"
}

private fun auditLabel(action: String): String = action.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }

private fun timestamp(epochMillis: Long): String =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(epochMillis))

private const val AUDIT_PAGE = 100
