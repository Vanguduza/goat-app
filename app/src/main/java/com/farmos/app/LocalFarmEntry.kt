package com.farmos.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.farmos.core.database.LocalFarmEntity
import com.farmos.core.design.AnimalFarmTheme
import com.farmos.core.design.FarmAnimalLineup
import com.farmos.core.design.FarmEntitySelector
import com.farmos.core.design.FarmSelectionAtoms
import com.farmos.core.design.FarmSelectorOption
import com.farmos.domain.access.Credential
import com.farmos.domain.access.CredentialKind
import com.farmos.domain.access.FarmSetupResult
import com.farmos.domain.access.LocalAccount
import com.farmos.domain.access.RecoveryResult
import com.farmos.domain.access.SignInResult
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface EntryStep {
    data object Loading : EntryStep
    data object Setup : EntryStep
    data class SignIn(val farms: List<LocalFarmEntity>) : EntryStep
    data class Recover(val farms: List<LocalFarmEntity>) : EntryStep
    data class ShowRecoveryCode(val screenId: String, val code: String, val account: LocalAccount, val farmName: String) : EntryStep
}

/**
 * Local farm entry with no server: first-run farm setup creates the Owner (FOS-GLOBAL-006), local sign-in
 * uses a farm account and PIN (FOS-GLOBAL-002), and owner recovery uses the one-time recovery code
 * (FOS-GLOBAL-004). Everything runs against this device's database, so it works fully offline.
 */
@Composable
internal fun LocalFarmEntry(
    directory: LocalFarmDirectory,
    onSignedIn: (account: LocalAccount, farmName: String) -> Unit,
    io: CoroutineDispatcher = Dispatchers.IO,
) {
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf<EntryStep>(EntryStep.Loading) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(directory) {
        val farms = withContext(io) { directory.farms() }
        step = if (farms.isEmpty()) EntryStep.Setup else EntryStep.SignIn(farms)
    }

    fun launchEntry(block: suspend () -> Unit) {
        scope.launch {
            busy = true
            error = null
            runCatching { block() }.onFailure { error = it.message ?: "Something went wrong on this device" }
            busy = false
        }
    }

    val screenId = when (val current = step) {
        EntryStep.Loading, is EntryStep.SignIn -> "FOS-GLOBAL-002"
        EntryStep.Setup -> "FOS-GLOBAL-006"
        is EntryStep.Recover -> "FOS-GLOBAL-004"
        is EntryStep.ShowRecoveryCode -> current.screenId
    }
    EntryShell(screenId) {
        when (val current = step) {
            EntryStep.Loading -> Text("Opening this device's farm records", color = AnimalFarmTheme.colors.mutedInk)
            EntryStep.Setup -> SetupForm(busy) { farmName, displayName, username, pin ->
                launchEntry {
                    var result: FarmSetupResult? = null
                    val farm = withContext(io) {
                        directory.createFarm(farmName) { farmId ->
                            result = directory.access.setUpFarm(farmId, username, displayName, Credential(CredentialKind.PIN, pin))
                        }
                    }
                    val setup = requireNotNull(result)
                    step = EntryStep.ShowRecoveryCode("FOS-GLOBAL-006", setup.recoveryCode, setup.owner, farm.name)
                }
            }
            is EntryStep.SignIn -> SignInForm(current.farms, busy, onRecover = { step = EntryStep.Recover(current.farms) }) { farm, username, pin ->
                launchEntry {
                    when (val result = withContext(io) { directory.access.signIn(farm.farmId, username, pin) }) {
                        is SignInResult.SignedIn -> onSignedIn(result.account, farm.name)
                        SignInResult.InvalidCredentials -> error = "The username or PIN is not correct."
                        SignInResult.Disabled -> error = "This account is disabled. Ask a manager to enable it."
                        is SignInResult.Locked -> error = "Too many attempts. Try again after ${clockTime(result.untilEpochMillis)}."
                    }
                }
            }
            is EntryStep.Recover -> RecoverForm(current.farms, busy, onBack = { step = EntryStep.SignIn(current.farms) }) { farm, username, code, pin ->
                launchEntry {
                    val result = withContext(io) {
                        val owner = directory.ownerByUsername(farm.farmId, username)
                        if (owner == null) RecoveryResult.InvalidCode else directory.access.recoverOwner(farm.farmId, owner.accountId, code, Credential(CredentialKind.PIN, pin))
                    }
                    when (result) {
                        is RecoveryResult.Recovered -> step = EntryStep.ShowRecoveryCode("FOS-GLOBAL-004", result.newRecoveryCode, result.owner, farm.name)
                        RecoveryResult.InvalidCode -> error = "That owner username and recovery code do not match this farm."
                    }
                }
            }
            is EntryStep.ShowRecoveryCode -> RecoveryCodeNotice(current.code) { onSignedIn(current.account, current.farmName) }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
private fun EntryShell(screenId: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier = Modifier.fillMaxSize().testTag("farm-screen:$screenId"), color = MaterialTheme.colorScheme.background) {
        Box(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 24.dp), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier.widthIn(max = 520.dp).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("Animal Farm", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                FarmAnimalLineup(Modifier.fillMaxWidth().heightIn(max = 220.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.SetupForm(busy: Boolean, onCreate: (farmName: String, displayName: String, username: String, pin: String) -> Unit) {
    var farmName by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    Text("Set up this farm", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
    Text("Everything is stored on this device first. No Internet or server is needed.", color = AnimalFarmTheme.colors.mutedInk)
    EntryField("Farm name", farmName, busy) { farmName = it }
    EntryField("Your name", displayName, busy) { displayName = it }
    EntryField("Owner username", username, busy) { username = it }
    PinField("Owner PIN (6 to 12 digits)", pin, busy) { pin = it }
    PinField("Confirm PIN", confirm, busy) { confirm = it }
    if (confirm.isNotEmpty() && confirm != pin) Text("The PINs do not match.", color = MaterialTheme.colorScheme.error)
    EntryButton(
        label = if (busy) "Creating farm" else "Create farm",
        enabled = !busy && farmName.isNotBlank() && displayName.isNotBlank() && username.isNotBlank() && pin.isNotEmpty() && pin == confirm,
    ) { onCreate(farmName, displayName, username, pin) }
}

@Composable
private fun ColumnScope.SignInForm(
    farms: List<LocalFarmEntity>,
    busy: Boolean,
    onRecover: () -> Unit,
    onSignIn: (farm: LocalFarmEntity, username: String, pin: String) -> Unit,
) {
    var farmId by remember { mutableStateOf(farms.first().farmId) }
    var username by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    Text("Sign in", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
    FarmChoice(farms, farmId, busy) { farmId = it }
    EntryField("Username", username, busy) { username = it }
    PinField("PIN", pin, busy) { pin = it }
    EntryButton(if (busy) "Signing in" else "Sign in", !busy && username.isNotBlank() && pin.isNotEmpty()) {
        onSignIn(farms.first { it.farmId == farmId }, username, pin)
    }
    Text("Forgotten PIN? A manager can reset it for you.", color = AnimalFarmTheme.colors.mutedInk)
    TextButton(onClick = onRecover, enabled = !busy, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Recover the owner account") }
}

@Composable
private fun ColumnScope.RecoverForm(
    farms: List<LocalFarmEntity>,
    busy: Boolean,
    onBack: () -> Unit,
    onRecover: (farm: LocalFarmEntity, username: String, code: String, pin: String) -> Unit,
) {
    var farmId by remember { mutableStateOf(farms.first().farmId) }
    var username by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    Text("Recover the owner account", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
    Text("Enter the recovery code you saved when the farm was set up. It is replaced by a new code afterwards.", color = AnimalFarmTheme.colors.mutedInk)
    FarmChoice(farms, farmId, busy) { farmId = it }
    EntryField("Owner username", username, busy) { username = it }
    EntryField("Recovery code", code, busy) { code = it }
    PinField("New owner PIN (6 to 12 digits)", pin, busy) { pin = it }
    PinField("Confirm new PIN", confirm, busy) { confirm = it }
    EntryButton(if (busy) "Recovering" else "Recover owner", !busy && username.isNotBlank() && code.isNotBlank() && pin.isNotEmpty() && pin == confirm) {
        onRecover(farms.first { it.farmId == farmId }, username, code, pin)
    }
    TextButton(onClick = onBack, enabled = !busy, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Back to sign in") }
}

@Composable
private fun ColumnScope.RecoveryCodeNotice(code: String, onContinue: () -> Unit) {
    Text("Save your owner recovery code", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
    Text(
        "Write this code down and keep it somewhere safe. It is the only way to recover the owner account on this device. It is shown once.",
        color = AnimalFarmTheme.colors.mutedInk,
    )
    Text(code, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.testTag("local-recovery-code"))
    EntryButton("I have saved the recovery code", true, onContinue)
}

@Composable
private fun FarmChoice(farms: List<LocalFarmEntity>, selectedId: String, busy: Boolean, onSelect: (String) -> Unit) {
    if (farms.size < 2) return
    FarmEntitySelector(
        atomTag = FarmSelectionAtoms.FARM_SELECTOR,
        title = "Farm",
        options = farms.map { FarmSelectorOption(it.farmId, it.name) },
        selectedId = selectedId,
        onSelect = onSelect,
        emptyText = "No farms on this device",
        enabled = !busy,
    )
}

@Composable
private fun EntryField(label: String, value: String, busy: Boolean, onChange: (String) -> Unit) {
    OutlinedTextField(value, onChange, label = { Text(label) }, singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun PinField(label: String, value: String, busy: Boolean, onChange: (String) -> Unit) {
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
private fun ColumnScope.EntryButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.align(Alignment.CenterHorizontally).widthIn(min = 160.dp).heightIn(min = AnimalFarmTheme.minimumTouchDp.dp),
    ) { Text(label) }
}

private fun clockTime(epochMillis: Long): String =
    DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(epochMillis))

/**
 * The server-free session: local entry until someone signs in, then the farm under that local account.
 * Every record the session writes is attributed to the signed-in local account.
 */
@Composable
internal fun LocalFarmSession(app: FarmOsApplication) {
    val directory = remember { LocalFarmDirectory(app.database) }
    var signedIn by remember { mutableStateOf<Pair<LocalAccount, String>?>(null) }
    val current = signedIn
    if (current == null) {
        LocalFarmEntry(directory, onSignedIn = { account, farmName -> signedIn = account to farmName })
        return
    }
    val (account, farmName) = current
    val membership = account.membership()
    FarmSessionContent(
        app = app,
        membership = membership,
        farmName = farmName,
        memberships = listOf(membership),
        farmNames = mapOf(membership.farmId to farmName),
        onSwitchFarm = {},
        onRequireReauth = { signedIn = null },
        onRequireFarmReselection = { _, _ -> signedIn = null },
        onSignOut = { signedIn = null },
        actorId = account.accountId,
    )
}
