package com.farmos.app

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.farmos.core.database.AccessAuditEntity
import com.farmos.core.database.ApplicationState
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.database.ReplicationDeviceEntity
import com.farmos.core.database.UnappliedOperation
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
import com.farmos.data.herd.RoomOpsRepository
import com.farmos.domain.access.AccessDenied
import com.farmos.domain.access.AccountStatus
import com.farmos.domain.access.Credential
import com.farmos.domain.access.CredentialKind
import com.farmos.domain.access.LocalAccount
import com.farmos.domain.access.LocalRole
import com.farmos.domain.access.Permission
import com.farmos.domain.access.RolePermissions
import com.farmos.domain.ops.EnablePoultryKind
import com.farmos.domain.ops.FarmCurrency
import com.farmos.domain.ops.GestationDefaults
import com.farmos.domain.ops.GestationPeriod
import com.farmos.domain.ops.GestationSpecies
import com.farmos.domain.ops.PoultryKindIncubation
import com.farmos.domain.replication.DeviceStatus
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import com.farmos.core.model.LocalCommandContext
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class SettingsPage {
    HOME, MEMBERS, CREATE_MEMBER, MEMBER_DETAIL, PERMISSIONS, AUDIT, STORAGE, DEVICES, CURRENCY, SPECIES, CONFLICTS,
    PROFILE, POULTRY_KINDS, LOCATIONS, NOTIFICATIONS, SYNC_SETTINGS, SEARCH_SETTINGS, INTEGRATIONS, HARDWARE,
    BLE_DEVICES, RFID_DEVICES, MODEL_API, SECURITY, DATA_EXPORT, ABOUT, UNITS, PERM_NOTIFICATIONS, PERM_CAMERA,
    PERM_BLUETOOTH, PERM_LOCATION, TERMS,
}

/** Everything the settings pages show, read from this device's database in one pass. */
private data class SettingsSnapshot(
    val actor: LocalAccount?,
    val accounts: List<LocalAccount>,
    val audit: List<AccessAuditEntity>,
    val auditTotal: Long,
    val devices: List<ReplicationDeviceEntity>,
    val journalCount: Long,
    val currencyCode: String,
    val unapplied: List<UnappliedOperation> = emptyList(),
    val unappliedTotal: Long = 0,
    /** The farm's own gestation periods; species absent here use the defaults. */
    val gestationOverrides: Map<GestationSpecies, GestationPeriod> = emptyMap(),
    /** Per device: operations it reported that this device does not hold yet. */
    val unpublished: Map<String, Long> = emptyMap(),
)

/**
 * Farm settings for local accounts: Accounts & Access (FOS-ADMIN-003/004/005/006/024), Storage & Backup
 * (FOS-ADMIN-023) and Devices (FOS-ADMIN-021) under Settings Home (FOS-ADMIN-001). Every action runs
 * through the local access service, which authorises it against the signed-in account and audits it.
 *
 * Also serves FOS-GLOBAL-003 (create account → Add account page), FOS-GLOBAL-009 (role and permissions
 * explanation → Roles and permissions page) and FOS-GLOBAL-019 (account profile → Account and role page);
 * those pages keep their FOS-ADMIN-* runtime tags (one testTag per node).
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
    /** Farm-LAN replication for this farm, present while a local session runs it. */
    lan: FarmLanRuntime? = null,
    /**
     * Governed command writer for this farm. Present when the session wires it
     * (FarmSessionContent passes app.opsRepository(farmId)); when null, pages that
     * need a command write show their state read-only instead of a dead control.
     */
    ops: RoomOpsRepository? = null,
) {
    val scope = rememberCoroutineScope()
    var page by remember { mutableStateOf(SettingsPage.HOME) }
    var selectedAccountId by remember { mutableStateOf<String?>(null) }
    var snapshot by remember { mutableStateOf<SettingsSnapshot?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(farmId, actorId, refreshKey) {
        // A failed read shows an error instead of taking the settings screen down; cancellation still propagates.
        snapshot = try {
            loadSnapshot(io, directory, database, farmId, actorId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = failure.message ?: "Settings could not be read on this device"
            return@LaunchedEffect
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
                SettingsError(error)
                Text("Loading settings saved on this device", color = AnimalFarmTheme.colors.mutedInk)
                return@FarmOperationalPage
            }
            val actor = current.actor
            if (actor == null) {
                FarmPermissionExplanation("Local settings", "Settings are managed by local farm accounts. Sign in with a farm account on this device to use them.")
                return@FarmOperationalPage
            }
            Text("Signed in as ${actor.displayName} · ${roleLabel(actor.role)}", modifier = Modifier.testTag("settings-signed-in"))
            AnimalFarmQuickAction("Farm profile", { page = SettingsPage.PROFILE })
            AnimalFarmQuickAction("Accounts and access", { page = SettingsPage.MEMBERS })
            AnimalFarmQuickAction("Roles and permissions", { page = SettingsPage.PERMISSIONS })
            AnimalFarmQuickAction("Access history", { page = SettingsPage.AUDIT })
            AnimalFarmQuickAction("Currency", { page = SettingsPage.CURRENCY })
            AnimalFarmQuickAction("Species configuration", { page = SettingsPage.SPECIES })
            AnimalFarmQuickAction("Poultry kinds", { page = SettingsPage.POULTRY_KINDS })
            AnimalFarmQuickAction("Locations", { page = SettingsPage.LOCATIONS })
            AnimalFarmQuickAction("Notifications", { page = SettingsPage.NOTIFICATIONS })
            AnimalFarmQuickAction("Offline and sync", { page = SettingsPage.SYNC_SETTINGS })
            AnimalFarmQuickAction("Search settings", { page = SettingsPage.SEARCH_SETTINGS })
            AnimalFarmQuickAction("Integrations", { page = SettingsPage.INTEGRATIONS })
            AnimalFarmQuickAction("Hardware", { page = SettingsPage.HARDWARE })
            AnimalFarmQuickAction("Security", { page = SettingsPage.SECURITY })
            AnimalFarmQuickAction("Data export", { page = SettingsPage.DATA_EXPORT })
            AnimalFarmQuickAction("Storage and backup", { page = SettingsPage.STORAGE })
            AnimalFarmQuickAction("Devices", { page = SettingsPage.DEVICES })
            AnimalFarmQuickAction("App permissions", { page = SettingsPage.PERM_NOTIFICATIONS })
            AnimalFarmQuickAction("Terms and privacy", { page = SettingsPage.TERMS })
            AnimalFarmQuickAction("About", { page = SettingsPage.ABOUT })
            AnimalFarmQuickAction("Units", { page = SettingsPage.UNITS })
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
            FarmOperationalSection("Farm network sync") {
                if (lan == null) {
                    Text("Not running on this device.")
                } else {
                    LanStatus(lan, current.devices.count { it.deviceId != deviceId })
                }
            }
            FarmOperationalSection("Received changes needing review") {
                ReceivedChangesReview(current.unapplied, current.unappliedTotal, busy || !RolePermissions.allows(actor.role, Permission.RESOLVE_SYNC_CONFLICTS)) {
                    act { RoomReplicaEndpoint(database, farmId, deviceId, replicationAppliers).applyPendingNow() }
                }
                SettingsButton("Open Conflict Centre", !busy) { page = SettingsPage.CONFLICTS }
            }
            FarmOperationalSection("Google Drive") {
                Text("Not connected.")
                Text("Farm work continues on this device. Disconnecting Google Drive never deletes farm records on this device.", color = AnimalFarmTheme.colors.mutedInk)
            }
            FarmOperationalSection("Backup") { Text("No backup has been made.") }
        }
        SettingsPage.CONFLICTS -> {
            val actor = current.actor
            ConflictCentreHost(
                database = database,
                farmId = farmId,
                canResolve = actor != null && RolePermissions.allows(actor.role, Permission.RESOLVE_SYNC_CONFLICTS),
                newContext = { LocalCommandContext(farmId, actor?.accountId ?: "unknown", deviceId, UUID.randomUUID().toString(), System.currentTimeMillis()) },
                onRetryAll = { RoomReplicaEndpoint(database, farmId, deviceId, replicationAppliers).applyPendingNow() },
                onBack = { page = SettingsPage.STORAGE; refreshKey++ },
            )
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
        SettingsPage.SPECIES -> FarmOperationalPage("FOS-ADMIN-007", "Species configuration", "Gestation periods used for due dates on this farm.", onBack = home, backLabel = "Farm settings") {
            val actor = current.actor
            val canEdit = actor != null && RolePermissions.allows(actor.role, Permission.MANAGE_FARM_SETTINGS)
            SettingsError(error)
            Text(
                "Due dates are predicted from the service date. A due date recorded on a pregnancy wins over the prediction, and a recorded birth replaces it.",
                color = AnimalFarmTheme.colors.mutedInk,
            )
            GestationSpecies.entries.forEach { species ->
                GestationEditor(species, current.gestationOverrides[species], canEdit && !busy) { period ->
                    act { database.setFarmGestation(farmId, species, period, it.accountId, deviceId) }
                }
            }
            if (!canEdit) FarmPermissionExplanation("Gestation periods are set by farm management", "Only Owner and Manager accounts can change them.")
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
                others.forEach { device ->
                    KnownDevice(device, current.unpublished[device.deviceId] ?: 0, busy) { status ->
                        act { actor ->
                            database.setDeviceStatus(farmId, device.deviceId, status, actor.accountId, deviceId)
                            // A retired or lost device keeps what it had, but can read nothing sealed from now on.
                            lan?.rotateKeyAfterRevocation(actor.accountId)
                        }
                    }
                }
            }
            if (lan != null && RolePermissions.allows(actor.role, Permission.APPROVE_DEVICE_PAIRING)) {
                AddDevice(lan, actor, onJoined = { refreshKey++ })
            }
        }
        /** FOS-ADMIN-002 — Farm profile: this farm's identity, device and account counts, read from this device's database. */
        SettingsPage.PROFILE -> FarmOperationalPage("FOS-ADMIN-002", "Farm profile", "This farm's identity on this device.", onBack = home, backLabel = "Farm settings") {
            current.actor ?: return@FarmOperationalPage
            var farmName by remember(farmId) { mutableStateOf<String?>(null) }
            var createdAt by remember(farmId) { mutableStateOf<Long?>(null) }
            var loadError by remember(farmId) { mutableStateOf<String?>(null) }
            LaunchedEffect(farmId, refreshKey) {
                runCatching {
                    withContext(io) {
                        database.localAccess().farms().firstOrNull { it.farmId == farmId }
                    }
                }.onSuccess { farm ->
                    farmName = farm?.name
                    createdAt = farm?.createdAtEpochMillis
                }.onFailure { loadError = it.message }
            }
            SettingsError(loadError)
            FarmOperationalSection("Identity") {
                Text("Name: ${farmName ?: "Loading"}", modifier = Modifier.testTag("settings-farm-name"))
                Text("Farm ID: $farmId", modifier = Modifier.testTag("settings-farm-id"))
                Text(
                    "Created: ${createdAt?.let { timestamp(it) } ?: "Loading"}",
                    modifier = Modifier.testTag("settings-farm-created"),
                )
            }
            FarmOperationalSection("On this farm") {
                Text("${current.devices.size} device(s) known", modifier = Modifier.testTag("settings-farm-devices"))
                Text("${current.accounts.size} account(s)", modifier = Modifier.testTag("settings-farm-accounts"))
                Text("Recording currency: ${current.currencyCode}")
            }
        }
        /** FOS-ADMIN-008 — Poultry kinds: enable farm poultry kinds from the governed kinds catalog via the EnablePoultryKind command. */
        SettingsPage.POULTRY_KINDS -> FarmOperationalPage("FOS-ADMIN-008", "Poultry kinds", "Which poultry kinds this farm keeps.", onBack = home, backLabel = "Farm settings") {
            val actor = current.actor ?: return@FarmOperationalPage
            val canEdit = actor.let { RolePermissions.allows(it.role, Permission.MANAGE_FARM_SETTINGS) }
            var enabled by remember(farmId, refreshKey) { mutableStateOf<Set<String>?>(null) }
            var loadError by remember(farmId) { mutableStateOf<String?>(null) }
            LaunchedEffect(farmId, refreshKey) {
                runCatching { withContext(io) { database.lifecycle().enabledPoultryKinds(farmId).map { it.poultryKindCode }.toSet() } }
                    .onSuccess { enabled = it }
                    .onFailure { loadError = it.message }
            }
            SettingsError(error)
            SettingsError(loadError)
            val kinds = enabled
            if (kinds == null) {
                Text("Loading poultry kinds saved on this device", color = AnimalFarmTheme.colors.mutedInk)
                return@FarmOperationalPage
            }
            Text(
                "Kinds enabled here appear across poultry recording. Enabling a kind records a governed farm operation that replicates to other devices; kinds cannot be disabled once enabled.",
                color = AnimalFarmTheme.colors.mutedInk,
            )
            FarmOperationalSection("Kinds") {
                PoultryKindIncubation.KINDS.sorted().forEach { code ->
                    val isOn = code in kinds
                    Text(
                        "${kindLabel(code)} · ${if (isOn) "enabled" else "not enabled"}",
                        modifier = Modifier.testTag("settings-poultry-kind:$code"),
                    )
                    if (!isOn && canEdit) {
                        val writer = ops
                        SettingsButton(
                            "Enable ${kindLabel(code)}",
                            !busy && writer != null,
                        ) {
                            val w = writer ?: return@SettingsButton
                            scope.launch {
                                busy = true
                                error = null
                                runCatching {
                                    withContext(io) {
                                        w.enablePoultryKind(
                                            EnablePoultryKind(code),
                                            LocalCommandContext(farmId, actor.accountId, deviceId, UUID.randomUUID().toString(), System.currentTimeMillis()),
                                        )
                                    }
                                }.onFailure { error = it.message ?: "The kind could not be enabled on this device" }
                                refreshKey++
                                busy = false
                            }
                        }
                    }
                }
            }
            if (ops == null) {
                Text("Kind changes are read-only here: the session has not wired the command writer yet.", color = AnimalFarmTheme.colors.mutedInk)
            }
            if (!canEdit) FarmPermissionExplanation("Poultry kinds are set by farm management", "Only Owner and Manager accounts can enable poultry kinds.")
        }
        /** FOS-ADMIN-009 — Locations: active paddocks and poultry houses recorded on this farm. */
        SettingsPage.LOCATIONS -> FarmOperationalPage("FOS-ADMIN-009", "Locations", "Paddocks and poultry houses on this farm.", onBack = home, backLabel = "Farm settings") {
            current.actor ?: return@FarmOperationalPage
            var paddocks by remember(farmId, refreshKey) { mutableStateOf<List<String>?>(null) }
            var houses by remember(farmId, refreshKey) { mutableStateOf<List<String>?>(null) }
            LaunchedEffect(farmId, refreshKey) {
                runCatching {
                    withContext(io) {
                        database.paddocks().active(farmId).map { "${it.code} · ${it.name}" } to
                            database.lifecycle().houses(farmId).map { "${it.code} · ${it.kind}" }
                    }
                }.onSuccess { (p, h) -> paddocks = p; houses = h }
                    .onFailure { paddocks = emptyList(); houses = emptyList() }
            }
            FarmOperationalSection("Paddocks") {
                val rows = paddocks
                when {
                    rows == null -> Text("Loading locations saved on this device", color = AnimalFarmTheme.colors.mutedInk)
                    rows.isEmpty() -> Text("No paddocks recorded yet.", color = AnimalFarmTheme.colors.mutedInk)
                    else -> rows.forEach { Text(it, modifier = Modifier.testTag("settings-location-paddock")) }
                }
            }
            FarmOperationalSection("Poultry houses") {
                val rows = houses
                when {
                    rows == null -> Text("Loading locations saved on this device", color = AnimalFarmTheme.colors.mutedInk)
                    rows.isEmpty() -> Text("No poultry houses recorded yet.", color = AnimalFarmTheme.colors.mutedInk)
                    else -> rows.forEach { Text(it, modifier = Modifier.testTag("settings-location-house")) }
                }
            }
            Text("Paddocks and houses are recorded in the pasture and poultry modules.", color = AnimalFarmTheme.colors.mutedInk)
        }
        /** FOS-ADMIN-012 — Notifications: local alert preferences kept on this device; no notification server exists. */
        SettingsPage.NOTIFICATIONS -> FarmOperationalPage("FOS-ADMIN-012", "Notifications", "What this device tells you about.", onBack = home, backLabel = "Farm settings") {
            current.actor ?: return@FarmOperationalPage
            val context = LocalContext.current
            val prefs = remember(context) { context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE) }
            Text("These stay on this device. Farm OS has no notification server.", color = AnimalFarmTheme.colors.mutedInk)
            FarmOperationalSection("Local alerts") {
                SettingsToggle("Health alerts", "Due vaccinations and red-flag observations.", prefs, PREF_NOTIFY_HEALTH)
                SettingsToggle("Sync completed", "When this device finishes synchronising.", prefs, PREF_NOTIFY_SYNC)
                SettingsToggle("Low stock", "Inventory falling below its reorder level.", prefs, PREF_NOTIFY_STOCK)
            }
        }
        /** FOS-ADMIN-013 — Offline and sync settings: journal state, farm network sync and Drive state. */
        SettingsPage.SYNC_SETTINGS -> FarmOperationalPage("FOS-ADMIN-013", "Offline and sync", "How this device keeps farm records without a network.", onBack = home, backLabel = "Farm settings") {
            current.actor ?: return@FarmOperationalPage
            FarmOperationalSection("Always offline-first") {
                Text("Every change is saved on this device first and never waits for a network.", color = AnimalFarmTheme.colors.mutedInk)
                Text("${current.journalCount} operation(s) recorded in this device's replication journal.", modifier = Modifier.testTag("settings-sync-journal"))
            }
            FarmOperationalSection("Farm network sync") {
                if (lan == null) {
                    Text("Not running on this device.")
                } else {
                    LanStatus(lan, current.devices.count { it.deviceId != deviceId })
                }
            }
            FarmOperationalSection("Google Drive") {
                Text("Not connected.")
                Text("Connecting Google Drive never deletes farm records on this device.", color = AnimalFarmTheme.colors.mutedInk)
            }
            Text("Backup and conflict review live under Storage and backup.", color = AnimalFarmTheme.colors.mutedInk)
        }
        /** FOS-ADMIN-014 — Search settings: local search scope toggles over this device's database. */
        SettingsPage.SEARCH_SETTINGS -> FarmOperationalPage("FOS-ADMIN-014", "Search settings", "What local search looks through.", onBack = home, backLabel = "Farm settings") {
            current.actor ?: return@FarmOperationalPage
            val context = LocalContext.current
            val prefs = remember(context) { context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE) }
            Text("Search runs against this device's database only. Nothing leaves the device.", color = AnimalFarmTheme.colors.mutedInk)
            FarmOperationalSection("Search scope") {
                SettingsToggle("Animals", "Individual animal records.", prefs, PREF_SEARCH_ANIMALS)
                SettingsToggle("Groups", "Animal groups and flocks.", prefs, PREF_SEARCH_GROUPS)
                SettingsToggle("Health", "Observations, treatments and vaccinations.", prefs, PREF_SEARCH_HEALTH)
                SettingsToggle("Inventory", "Stock items and movements.", prefs, PREF_SEARCH_INVENTORY)
                SettingsToggle("Money", "Sales, purchases and money records.", prefs, PREF_SEARCH_MONEY)
            }
        }
        /** FOS-ADMIN-015 — Integrations: honest connected/not-connected state for each bounded adapter. */
        SettingsPage.INTEGRATIONS -> FarmOperationalPage("FOS-ADMIN-015", "Integrations", "What this farm connects to.", onBack = home, backLabel = "Farm settings") {
            current.actor ?: return@FarmOperationalPage
            FarmOperationalSection("Connections") {
                val serving = lan?.let { runtime ->
                    val lanState by runtime.state.collectAsState()
                    lanState.serving
                }
                Text(
                    "Farm network (LAN): ${when (serving) { true -> "running"; false -> "not running"; null -> "not running on this device" }}",
                    modifier = Modifier.testTag("settings-integration-lan"),
                )
                Text("Google Drive: not connected.", modifier = Modifier.testTag("settings-integration-drive"))
                Text("AI provider: managed inside the Copilot module boundary; keys stay sealed in this device's Keystore.", modifier = Modifier.testTag("settings-integration-ai"))
            }
            Text("Farm OS has no application server. Integrations are bounded adapters, never authorities.", color = AnimalFarmTheme.colors.mutedInk)
        }
        /** FOS-ADMIN-016 — Hardware: this device and its reader pages. */
        SettingsPage.HARDWARE -> FarmOperationalPage("FOS-ADMIN-016", "Hardware", "This device and its readers.", onBack = home, backLabel = "Farm settings") {
            current.actor ?: return@FarmOperationalPage
            FarmOperationalSection("This device") {
                Text("${Build.MANUFACTURER} ${Build.MODEL}", modifier = Modifier.testTag("settings-hardware-device"))
                Text("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})", color = AnimalFarmTheme.colors.mutedInk)
            }
            AnimalFarmQuickAction("Bluetooth devices", { page = SettingsPage.BLE_DEVICES })
            AnimalFarmQuickAction("RFID readers", { page = SettingsPage.RFID_DEVICES })
        }
        /** FOS-ADMIN-017 — Bluetooth devices: bonded devices read from this device's Bluetooth adapter. */
        SettingsPage.BLE_DEVICES -> FarmOperationalPage("FOS-ADMIN-017", "Bluetooth devices", "Devices this phone or tablet knows.", onBack = { page = SettingsPage.HARDWARE }, backLabel = "Hardware") {
            current.actor ?: return@FarmOperationalPage
            val context = LocalContext.current
            var names by remember { mutableStateOf<List<String>?>(null) }
            var note by remember { mutableStateOf<String?>(null) }
            LaunchedEffect(Unit) {
                val adapter = runCatching { BluetoothAdapter.getDefaultAdapter() }.getOrNull()
                if (adapter == null) {
                    note = "This device has no Bluetooth adapter."
                    names = emptyList()
                    return@LaunchedEffect
                }
                if (Build.VERSION.SDK_INT >= 31 &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
                ) {
                    note = "Bluetooth permission is not granted, so bonded devices cannot be listed."
                    names = emptyList()
                    return@LaunchedEffect
                }
                runCatching {
                    withContext(Dispatchers.IO) {
                        @Suppress("MissingPermission")
                        adapter.bondedDevices.map { "${it.name ?: "Unnamed device"} · ${it.address}" }.sorted()
                    }
                }.onSuccess { names = it }
                    .onFailure { note = "Bonded devices could not be read: ${it.message}"; names = emptyList() }
            }
            note?.let { Text(it, color = AnimalFarmTheme.colors.mutedInk) }
            FarmOperationalSection("Bonded devices") {
                val rows = names
                when {
                    rows == null -> Text("Reading Bluetooth devices", color = AnimalFarmTheme.colors.mutedInk)
                    rows.isEmpty() -> Text("No bonded Bluetooth devices.", color = AnimalFarmTheme.colors.mutedInk)
                    else -> rows.forEach { Text(it, modifier = Modifier.testTag("settings-ble-device")) }
                }
            }
        }
        /** FOS-ADMIN-018 — RFID readers: honest unavailable state; no reader adapter is bundled. */
        SettingsPage.RFID_DEVICES -> FarmOperationalPage("FOS-ADMIN-018", "RFID readers", "Tag readers paired with this farm.", onBack = { page = SettingsPage.HARDWARE }, backLabel = "Hardware") {
            current.actor ?: return@FarmOperationalPage
            // No RFID reader adapter is bundled with Farm OS on this device.
            FarmOperationalSection("Readers") {
                Text("No RFID reader is connected.", modifier = Modifier.testTag("settings-rfid-state"))
                Text(
                    "When a reader adapter is fitted, it appears here and tag scans flow into animal recording. Nothing is simulated: with no reader, there is nothing to list.",
                    color = AnimalFarmTheme.colors.mutedInk,
                )
            }
        }
        /** FOS-ADMIN-019 — Model API: provider configuration lives in the Copilot module boundary; settings does not duplicate it. */
        SettingsPage.MODEL_API -> FarmOperationalPage("FOS-ADMIN-019", "Model API", "Where AI provider settings live.", onBack = home, backLabel = "Farm settings") {
            current.actor ?: return@FarmOperationalPage
            // The AI boundary owns provider keys and adapters; settings does not duplicate them.
            FarmOperationalSection("Provider configuration") {
                Text("AI provider keys and adapters are managed inside the Copilot module's Farm OS-owned boundary.", modifier = Modifier.testTag("settings-model-api"))
                Text("Keys are sealed in this device's Keystore and never leave it through settings.", color = AnimalFarmTheme.colors.mutedInk)
                Text("Suggested actions stay advisory: Copilot never prescribes, doses, treats, culls, sells or posts on its own.", color = AnimalFarmTheme.colors.mutedInk)
            }
        }
        /** FOS-ADMIN-020 — Security: device keystore state, biometric presence and sign-in credential kind. */
        SettingsPage.SECURITY -> FarmOperationalPage("FOS-ADMIN-020", "Security", "How this device protects farm records.", onBack = home, backLabel = "Farm settings") {
            val actor = current.actor ?: return@FarmOperationalPage
            val context = LocalContext.current
            FarmOperationalSection("This device") {
                val biometric = context.packageManager.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)
                Text("Biometric hardware: ${if (biometric) "present" else "not present"}", modifier = Modifier.testTag("settings-security-biometric"))
                Text("Farm keys are sealed in this device's Keystore and never leave it.", color = AnimalFarmTheme.colors.mutedInk)
            }
            FarmOperationalSection("Your sign-in") {
                Text("Signed in as ${actor.displayName}", modifier = Modifier.testTag("settings-security-account"))
                Text("Credential: ${actor.credentialKind.name.lowercase()}", color = AnimalFarmTheme.colors.mutedInk)
                Text("Change your PIN from your account page under Accounts and access.", color = AnimalFarmTheme.colors.mutedInk)
            }
            FarmOperationalSection("Recovery") {
                Text("Keep the owner recovery code written down somewhere safe. It is the only way back in if every PIN is lost.", color = AnimalFarmTheme.colors.mutedInk)
            }
        }
        /** FOS-ADMIN-022 — Data export: farm-scoped JSON summary exports with an app-private export log. */
        SettingsPage.DATA_EXPORT -> FarmOperationalPage("FOS-ADMIN-022", "Data export", "Copies of this farm's records, made on this device.", onBack = home, backLabel = "Farm settings") {
            val actor = current.actor ?: return@FarmOperationalPage
            if (!RolePermissions.allows(actor.role, Permission.EXPORT_FARM_DATA)) {
                FarmPermissionExplanation("Exports are for farm management", "Only Owner and Manager accounts can export farm records.")
                return@FarmOperationalPage
            }
            val context = LocalContext.current
            var log by remember(refreshKey) { mutableStateOf(settingsExportLog(context, farmId)) }
            var exporting by remember { mutableStateOf(false) }
            SettingsError(error)
            FarmOperationalSection("New export") {
                Text("Writes a JSON summary of this farm's records to app-private storage and logs it below.", color = AnimalFarmTheme.colors.mutedInk)
                SettingsButton("Export farm summary", !exporting && !busy) {
                    exporting = true
                    scope.launch {
                        runCatching {
                            withContext(io) { writeSettingsExport(context, database, farmId, current) }
                        }.onSuccess { log = settingsExportLog(context, farmId) }
                            .onFailure { error = it.message ?: "The export could not be written on this device" }
                        exporting = false
                    }
                }
            }
            FarmOperationalSection("Export log") {
                if (log.isEmpty()) {
                    Text("No exports made yet.", color = AnimalFarmTheme.colors.mutedInk)
                } else {
                    log.forEach { entry ->
                        Text("${entry.first} · ${entry.second}", modifier = Modifier.testTag("settings-export-log"))
                    }
                }
            }
        }
        /** FOS-ADMIN-025 — About: app version and canonical registry scope. */
        SettingsPage.ABOUT -> FarmOperationalPage("FOS-ADMIN-025", "About", "This app and its canonical scope.", onBack = home, backLabel = "Farm settings") {
            current.actor ?: return@FarmOperationalPage
            FarmOperationalSection("Farm OS") {
                Text("Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", modifier = Modifier.testTag("settings-about-version"))
                Text("Local-first farm operating system. No application server: this device's database is the operational record.", color = AnimalFarmTheme.colors.mutedInk)
            }
            FarmOperationalSection("Canonical scope") {
                Text("545 registered screens · 156 mandatory features · 29 modules", modifier = Modifier.testTag("settings-about-scope"))
                Text("Counts come from the Farm OS screen and feature registries.", color = AnimalFarmTheme.colors.mutedInk)
            }
        }
        /** FOS-ADMIN-010 — Units of measure: not configured in this build (genuine gap). */
        SettingsPage.UNITS -> UnitsScreen(home)
        /** FOS-GLOBAL-010 — Notification permission: current grant state and system request. */
        SettingsPage.PERM_NOTIFICATIONS -> PermissionPage(
            screenId = "FOS-GLOBAL-010",
            title = "Notification permission",
            subtitle = "Let Farm OS notify you about farm work on this device.",
            permission = NOTIFICATION_PERMISSION,
            rationale = "Notifications tell you about due vaccinations, red-flag observations and finished syncs. They never leave this device.",
            onBack = home,
        )
        /** FOS-GLOBAL-011 — Camera permission: current grant state and system request. */
        SettingsPage.PERM_CAMERA -> PermissionPage(
            screenId = "FOS-GLOBAL-011",
            title = "Camera permission",
            subtitle = "Let Farm OS use the camera for photos and tag scans.",
            permission = Manifest.permission.CAMERA,
            rationale = "The camera takes animal photos and scans QR and barcodes. Photos stay in this farm's records on this device.",
            onBack = home,
        )
        /** FOS-GLOBAL-012 — Bluetooth permission: current grant state and system request. */
        SettingsPage.PERM_BLUETOOTH -> PermissionPage(
            screenId = "FOS-GLOBAL-012",
            title = "Bluetooth permission",
            subtitle = "Let Farm OS find nearby Bluetooth devices.",
            permission = BLUETOOTH_PERMISSION,
            rationale = "Bluetooth lists bonded devices such as tag readers. Farm OS never scans silently in the background.",
            onBack = home,
        )
        /** FOS-GLOBAL-013 — Location permission: current grant state and system request. */
        SettingsPage.PERM_LOCATION -> PermissionPage(
            screenId = "FOS-GLOBAL-013",
            title = "Location permission",
            subtitle = "Let Farm OS record where farm work happened.",
            permission = Manifest.permission.ACCESS_FINE_LOCATION,
            rationale = "Location stamps where observations and movements were recorded. It is stored with the farm record on this device.",
            onBack = home,
        )
        /** FOS-GLOBAL-015 — Terms and privacy: how Farm OS treats the farm's records. */
        SettingsPage.TERMS -> FarmOperationalPage("FOS-GLOBAL-015", "Terms and privacy", "How Farm OS treats your farm's records.", onBack = home, backLabel = "Farm settings") {
            current.actor ?: return@FarmOperationalPage
            FarmOperationalSection("Terms") {
                Text("Farm OS is a local-first farm operating system. Your farm's records live in a database on your devices, not on our servers — there is no application server to hold them.", modifier = Modifier.testTag("settings-terms"))
            }
            FarmOperationalSection("Privacy") {
                Text("Records replicate only between devices you pair on your farm network, and to Google Drive only if you connect it. Farm OS itself receives nothing.")
                Text("AI features run against your local records; provider keys you add are sealed in this device's Keystore and never logged or backed up by Farm OS.")
            }
        }
    }
}

/** POST_NOTIFICATIONS exists only from API 33; below that, notifications need no runtime permission. */
private val NOTIFICATION_PERMISSION: String
    get() = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.POST_NOTIFICATIONS else ""

/** BLUETOOTH_CONNECT exists only from API 31; below that, classic Bluetooth needs no runtime permission. */
private val BLUETOOTH_PERMISSION: String
    get() = if (Build.VERSION.SDK_INT >= 31) Manifest.permission.BLUETOOTH_CONNECT else ""

/**
 * FOS-GLOBAL-010/011/012/013 — one honest permission surface per permission. Shows the current grant
 * state and offers the system request; the request only takes effect once the permission is declared
 * in the app manifest, which is a one-line packaging change outside this file.
 */
@Composable
private fun PermissionPage(
    screenId: String,
    title: String,
    subtitle: String,
    permission: String,
    rationale: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var granted by remember(permission) { mutableStateOf(permission.isEmpty() || ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        granted = isGranted
    }
    FarmOperationalPage(screenId, title, subtitle, onBack = onBack, backLabel = "Farm settings") {
        FarmOperationalSection("Status") {
            Text(
                when {
                    permission.isEmpty() -> "Not needed on this Android version."
                    granted -> "Granted."
                    else -> "Not granted."
                },
                modifier = Modifier.testTag("settings-permission:$screenId"),
            )
            Text(rationale, color = AnimalFarmTheme.colors.mutedInk)
            if (permission.isNotEmpty() && !granted) {
                Text("This permission is not declared in the app manifest yet; declaring it is a packaging change.", color = AnimalFarmTheme.colors.mutedInk)
                SettingsButton("Request permission", true) { launcher.launch(permission) }
            }
        }
    }
}

/** A local on/off preference kept in app-private SharedPreferences; no server, no account needed. */
@Composable
private fun SettingsToggle(label: String, description: String, prefs: android.content.SharedPreferences, key: String) {
    var on by remember(key) { mutableStateOf(prefs.getBoolean(key, true)) }
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
            Text(label, modifier = Modifier.testTag("settings-toggle:$key"))
            Text(description, color = AnimalFarmTheme.colors.mutedInk)
        }
        Switch(
            checked = on,
            onCheckedChange = {
                on = it
                prefs.edit().putBoolean(key, it).apply()
            },
        )
    }
}

private fun kindLabel(code: String): String = code.replace('_', ' ').replaceFirstChar { it.uppercase() }

private const val SETTINGS_PREFS = "farm_os_settings"
private const val PREF_NOTIFY_HEALTH = "notify_health"
private const val PREF_NOTIFY_SYNC = "notify_sync"
private const val PREF_NOTIFY_STOCK = "notify_stock"
private const val PREF_SEARCH_ANIMALS = "search_animals"
private const val PREF_SEARCH_GROUPS = "search_groups"
private const val PREF_SEARCH_HEALTH = "search_health"
private const val PREF_SEARCH_INVENTORY = "search_inventory"
private const val PREF_SEARCH_MONEY = "search_money"

/** Farm-scoped export log in app-private storage, mirroring the reports export log pattern. */
private fun settingsExportDir(context: Context, farmId: String): File {
    val safe = farmId.filter { it.isLetterOrDigit() || it == '-' }.ifBlank { "farm" }
    return File(File(context.filesDir, "settings_exports"), safe).also { it.mkdirs() }
}

private fun settingsExportLog(context: Context, farmId: String): List<Pair<String, String>> {
    val file = File(settingsExportDir(context, farmId), "export_log.jsonl")
    if (!file.exists()) return emptyList()
    return file.readLines().mapNotNull { line ->
        runCatching {
            val at = line.substringAfter("\"at\":\"").substringBefore("\"")
            val name = line.substringAfter("\"file\":\"").substringBefore("\"")
            at to name
        }.getOrNull()
    }.takeLast(50)
}

private fun logSettingsExport(context: Context, farmId: String, fileName: String, at: String) {
    val file = File(settingsExportDir(context, farmId), "export_log.jsonl")
    file.appendText("{\"at\":\"$at\",\"file\":\"$fileName\"}\n")
}

/** Writes a JSON summary of the farm's records for [FOS-ADMIN-022]; every count comes from this device's database. */
private suspend fun writeSettingsExport(context: Context, database: FarmOsDatabase, farmId: String, snapshot: SettingsSnapshot): String {
    val at = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneId.systemDefault()).format(Instant.now())
    val fileName = "farm-summary-$at.json"
    val dir = settingsExportDir(context, farmId)
    val farm = database.localAccess().farms().firstOrNull { it.farmId == farmId }
    val body = buildString {
        appendLine("{")
        appendLine("  \"farmId\": \"${farm?.farmId ?: farmId}\",")
        appendLine("  \"farmName\": \"${(farm?.name ?: "").replace("\"", "'")}\",")
        appendLine("  \"exportedAt\": \"$at\",")
        appendLine("  \"accounts\": ${snapshot.accounts.size},")
        appendLine("  \"devices\": ${snapshot.devices.size},")
        appendLine("  \"journalOperations\": ${snapshot.journalCount},")
        appendLine("  \"currencyCode\": \"${snapshot.currencyCode}\"")
        appendLine("}")
    }
    File(dir, fileName).writeText(body)
    logSettingsExport(context, farmId, fileName, at)
    return fileName
}

/**
 * Changes received from other farm devices that have not taken effect here, with the reason. They stay in
 * the journal; one waiting on an earlier change is retried automatically, and Try again retries now.
 */
@Composable
private fun ReceivedChangesReview(rows: List<UnappliedOperation>, total: Long, busy: Boolean, onRetry: () -> Unit) {
    if (total == 0L) {
        Text("Every change received from other farm devices has taken effect here.", modifier = Modifier.testTag("settings-review-count"))
        return
    }
    Text(
        if (total > rows.size) "Latest ${rows.size} of $total received change(s) not yet in effect" else "$total received change(s) not yet in effect",
        modifier = Modifier.testTag("settings-review-count"),
    )
    rows.forEach { row ->
        val why = when (row.state) {
            ApplicationState.AWAITING_APPLIER.name -> "needs a newer version of this app"
            else -> row.reason ?: "not applied yet"
        }
        Text(
            listOf(timestamp(row.businessTimeEpochMillis), auditLabel(row.operationType.substringBeforeLast(".v").replace('.', '_')), "from ${row.deviceId}", why).joinToString(" · "),
            modifier = Modifier.testTag("settings-review:${row.operationId}"),
        )
    }
    SettingsButton("Try again", !busy, onRetry)
}

@Composable
private fun GestationEditor(species: GestationSpecies, farmPeriod: GestationPeriod?, editable: Boolean, onSave: (GestationPeriod) -> Unit) {
    val period = farmPeriod ?: GestationDefaults.period(species)
    var earliest by remember(period) { mutableStateOf(period.earliestDays.toString()) }
    var typical by remember(period) { mutableStateOf(period.typicalDays.toString()) }
    var latest by remember(period) { mutableStateOf(period.latestDays.toString()) }
    val name = species.name.lowercase().replaceFirstChar { it.uppercase() }
    FarmOperationalSection(name, if (farmPeriod == null) "Default for this species" else "Set for this farm") {
        Text(
            "Typical ${period.typicalDays} days, expected between day ${period.earliestDays} and day ${period.latestDays}",
            modifier = Modifier.testTag("settings-gestation:${species.code}"),
        )
        if (!editable) return@FarmOperationalSection
        SettingsNumber("$name earliest day", earliest) { earliest = it }
        SettingsNumber("$name typical days", typical) { typical = it }
        SettingsNumber("$name latest day", latest) { latest = it }
        val candidate = runCatching { GestationPeriod(earliest.toInt(), typical.toInt(), latest.toInt()) }.getOrNull()
        if (candidate == null) Text("Enter whole days with earliest ≤ typical ≤ latest.", color = MaterialTheme.colorScheme.error)
        SettingsButton("Save $name", candidate != null && candidate != period) { candidate?.let(onSave) }
    }
}

@Composable
private fun SettingsNumber(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { typed -> onChange(typed.filter(Char::isDigit).take(3)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun LanStatus(lan: FarmLanRuntime, pairedDevices: Int) {
    val state by lan.state.collectAsState()
    Text(if (state.serving) "Sharing this farm on the local network." else "Starting the farm network on this device.")
    Text("${state.peers.size} of $pairedDevices paired device(s) found on this network.", modifier = Modifier.testTag("settings-lan-peers"))
    Text(
        state.lastSyncEpochMillis?.let { "Last synchronised ${timestamp(it)}" } ?: "Not synchronised on this network yet.",
        color = AnimalFarmTheme.colors.mutedInk,
    )
    state.lastError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    SettingsButton("Synchronise now", state.serving) { lan.requestSync() }
}

@Composable
private fun KnownDevice(device: ReplicationDeviceEntity, unpublished: Long, busy: Boolean, onSetStatus: (DeviceStatus) -> Unit) {
    var confirming by remember(device.deviceId) { mutableStateOf<DeviceStatus?>(null) }
    val active = device.status == DeviceStatus.ACTIVE.name || device.status == DeviceStatus.TEMPORARILY_OFFLINE.name
    Text(
        "${device.name} · ${device.status.lowercase().replace('_', ' ')} · ${device.lastReportedOwnSequence} operation(s)",
        modifier = Modifier.testTag("settings-device:${device.deviceId}"),
    )
    if (!active) return
    when (confirming) {
        null -> {
            SettingsButton("Retire ${device.name}", !busy) { confirming = DeviceStatus.RETIRED }
            SettingsButton("Report ${device.name} lost", !busy) { confirming = DeviceStatus.LOST_REVOKED }
        }
        DeviceStatus.RETIRED -> {
            // Owner decision D-014: warn before retiring a device whose work has not reached this device.
            Text(
                if (unpublished > 0) {
                    "${device.name} recorded $unpublished operation(s) this device has not received. Synchronise it first: once retired, they are refused for good."
                } else {
                    "Everything ${device.name} recorded has reached this device. Once retired it cannot synchronise with this farm again."
                },
                color = if (unpublished > 0) MaterialTheme.colorScheme.error else AnimalFarmTheme.colors.mutedInk,
                modifier = Modifier.testTag("settings-retire-warning:${device.deviceId}"),
            )
            SettingsButton(if (unpublished > 0) "Retire ${device.name} anyway" else "Confirm retiring ${device.name}", !busy) {
                confirming = null
                onSetStatus(DeviceStatus.RETIRED)
            }
            SettingsButton("Cancel", !busy) { confirming = null }
        }
        else -> {
            Text("${device.name} will never synchronise with this farm again, and anything it recorded but had not shared is lost.", color = MaterialTheme.colorScheme.error)
            SettingsButton("Confirm ${device.name} is lost", !busy) {
                confirming = null
                onSetStatus(DeviceStatus.LOST_REVOKED)
            }
            SettingsButton("Cancel", !busy) { confirming = null }
        }
    }
}

/** Opens pairing on this device; each device asking to join is added only when its code is typed here. */
@Composable
private fun AddDevice(lan: FarmLanRuntime, approver: LocalAccount, onJoined: () -> Unit) {
    var session by remember { mutableStateOf<PairingSession?>(null) }
    var opening by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    DisposableEffect(Unit) { onDispose { session?.close() } }
    FarmOperationalSection("Add a device") {
        val open = session
        if (open == null) {
            Text("A phone or tablet on this network can ask to join. You approve it by typing the code it shows.", color = AnimalFarmTheme.colors.mutedInk)
            failure?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            SettingsButton(if (opening) "Opening" else "Add a device", !opening) {
                opening = true
                failure = null
                scope.launch {
                    // Opening waits for the network worker, which may be mid-sync; never on the main thread.
                    withContext(Dispatchers.IO) { runCatching { lan.openPairing(approver) } }
                        .onSuccess { session = it }
                        .onFailure { failure = it.message ?: "Adding a device could not start on this device" }
                    opening = false
                }
            }
            return@FarmOperationalSection
        }
        val pending by open.pending.collectAsState()
        val result by open.lastResult.collectAsState()
        LaunchedEffect(result) { if (result != null) onJoined() }
        result?.let { Text(it, modifier = Modifier.testTag("settings-pairing-result")) }
        val request = pending
        if (request == null) {
            Text("Waiting for a device. On the new device choose Join a farm on this network.", modifier = Modifier.testTag("settings-pairing-waiting"))
        } else {
            var code by remember(request) { mutableStateOf("") }
            Text("${request.deviceName} wants to join this farm.", modifier = Modifier.testTag("settings-pairing-request"))
            OutlinedTextField(
                value = code,
                onValueChange = { typed -> code = typed.filter(Char::isDigit).take(6) },
                label = { Text("Code shown on ${request.deviceName}") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            SettingsButton("Approve", code.length == 6) { open.approve(code) }
            SettingsButton("Decline", true) { open.decline() }
        }
        SettingsButton("Stop adding devices", true) {
            open.close()
            session = null
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
    Permission.EXPORT_FARM_DATA -> "Export farm records"
    Permission.MANAGE_OWNERS -> "Manage Owner accounts"
}

private fun auditLabel(action: String): String = action.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }

private fun timestamp(epochMillis: Long): String =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(epochMillis))

private const val AUDIT_PAGE = 100
private const val REVIEW_PAGE = 50

/** Everything the settings pages show, read from this device's database in one pass. */
private suspend fun loadSnapshot(
    io: CoroutineDispatcher,
    directory: LocalFarmDirectory,
    database: FarmOsDatabase,
    farmId: String,
    actorId: String?,
): SettingsSnapshot = withContext(io) {
    val access = database.localAccess()
    SettingsSnapshot(
        actor = actorId?.let { directory.account(farmId, it) },
        accounts = directory.accounts(farmId),
        audit = access.audit(farmId, AUDIT_PAGE),
        auditTotal = access.auditCount(farmId),
        devices = database.replication().devices(farmId),
        journalCount = database.replication().count(farmId),
        currencyCode = database.farmCurrency(farmId),
        unapplied = database.replicationApplications().unappliedForReview(farmId, REVIEW_PAGE),
        unappliedTotal = database.replicationApplications().count(farmId, ApplicationState.FAILED.name) +
            database.replicationApplications().count(farmId, ApplicationState.AWAITING_APPLIER.name),
        gestationOverrides = database.farmGestationOverrides(farmId),
        unpublished = database.replication().devices(farmId).associate { it.deviceId to database.unpublishedOperations(farmId, it.deviceId) },
    )
}

/**
 * FOS-ADMIN-010 — Units of measure.
 *
 * GENUINE GAP — not implemented: there is no units-of-measure store, DAO, or governed command in
 * the local schema; quantities are captured in fixed canonical units (grams, millilitres, head)
 * with exact decimal conversion at the UI layer.
 * Required domain piece: a farm-scoped units configuration with a governed command, validator
 * and migration. This screen fails closed.
 */
@Composable
private fun UnitsScreen(onBack: () -> Unit) {
    FarmOperationalPage(
        screenId = "FOS-ADMIN-010",
        title = "Units",
        subtitle = "Not configurable in this build.",
        onBack = onBack,
        backLabel = "Farm settings",
    ) {
        FarmOperationalSection("Unavailable") {
            Text(
                "Units of measure are not configurable in this build: there is no units store in the " +
                    "local schema. Quantities are captured in canonical units (grams, millilitres, head).",
                color = AnimalFarmTheme.colors.mutedInk,
            )
            Text("Required: a farm-scoped units configuration with a governed command, validator and migration.")
        }
    }
}
