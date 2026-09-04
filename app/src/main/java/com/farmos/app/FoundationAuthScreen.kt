package com.farmos.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.farmos.core.design.FarmPastoralBackdrop
import com.farmos.core.design.FarmStorySurface
import com.farmos.core.design.FosDimens
import com.farmos.core.network.FarmMembership

/** FOS-GLOBAL-002 / 005 / 006 — entrance family recovery surface. */
@Composable
fun FoundationAuthScreen(
    backendConfigured: Boolean,
    busy: Boolean,
    error: String?,
    sessionPresent: Boolean,
    memberships: List<FarmMembership>,
    farmNames: Map<String, String> = emptyMap(),
    onSignIn: (email: String, password: String) -> Unit,
    onSelectFarm: (FarmMembership) -> Unit,
    onCreateFarm: (name: String) -> Unit,
    onSignOut: () -> Unit,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var farmName by remember { mutableStateOf("") }

    FarmPastoralBackdrop(Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.widthIn(max = 520.dp).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    "Farm OS",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "Animals. Land. People. A Better Tomorrow.",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    "Real Farms. Brighter Futures.",
                    style = MaterialTheme.typography.bodyLarge,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.primary,
                )

                FarmStorySurface(Modifier.fillMaxWidth()) {
                    when {
                        !backendConfigured -> BackendUnavailableState()
                        memberships.isNotEmpty() -> FarmSelectionState(
                            memberships = memberships,
                            farmNames = farmNames,
                            busy = busy,
                            onSelectFarm = onSelectFarm,
                            onSignOut = onSignOut,
                        )
                        sessionPresent -> FarmCreationState(
                            farmName = farmName,
                            onFarmNameChange = { farmName = it },
                            busy = busy,
                            onCreateFarm = onCreateFarm,
                            onSignOut = onSignOut,
                        )
                        else -> SignInState(
                            email = email,
                            password = password,
                            busy = busy,
                            onEmailChange = { email = it },
                            onPasswordChange = { password = it },
                            onSignIn = onSignIn,
                        )
                    }
                    error?.let {
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    listOf("Plan", "Monitor", "Grow", "Sustain").forEach { value ->
                        Text(value, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onBackground)
                    }
                }
            }
        }
    }
}

@Composable
private fun SignInState(
    email: String,
    password: String,
    busy: Boolean,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSignIn: (String, String) -> Unit,
) {
    Text("Welcome back", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
    Text("Sign in to your farm.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    OutlinedTextField(
        value = email,
        onValueChange = onEmailChange,
        label = { Text("Email address") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        enabled = !busy,
    )
    OutlinedTextField(
        value = password,
        onValueChange = onPasswordChange,
        label = { Text("Password") },
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        enabled = !busy,
    )
    Button(
        onClick = { onSignIn(email, password) },
        enabled = !busy && email.isNotBlank() && password.isNotBlank(),
        modifier = Modifier.fillMaxWidth(),
    ) { Text(if (busy) "Signing in…" else "Sign in") }
    Text(
        "Healthy Animals. Thriving Farms.",
        modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun FarmSelectionState(
    memberships: List<FarmMembership>,
    farmNames: Map<String, String>,
    busy: Boolean,
    onSelectFarm: (FarmMembership) -> Unit,
    onSignOut: () -> Unit,
) {
    Text("Choose your farm", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
    Text("Your server membership determines the farms you can open.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    memberships.forEach { membership ->
        Button(
            onClick = { onSelectFarm(membership) },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            val name = farmNames[membership.farmId] ?: "${membership.farmId.take(8)}…"
            Text("$name · ${membership.role.replace('_', ' ')}")
        }
    }
    TextButton(onClick = onSignOut, enabled = !busy, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Sign out") }
}

@Composable
private fun FarmCreationState(
    farmName: String,
    onFarmNameChange: (String) -> Unit,
    busy: Boolean,
    onCreateFarm: (String) -> Unit,
    onSignOut: () -> Unit,
) {
    Text("Create your farm", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
    Text("Start the farm identity now. Species and operating setup follow after creation.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    OutlinedTextField(
        value = farmName,
        onValueChange = onFarmNameChange,
        label = { Text("Farm name") },
        modifier = Modifier.fillMaxWidth(),
        enabled = !busy,
        singleLine = true,
    )
    Button(
        onClick = { onCreateFarm(farmName) },
        enabled = !busy && farmName.isNotBlank(),
        modifier = Modifier.fillMaxWidth(),
    ) { Text(if (busy) "Creating…" else "Create farm") }
    TextButton(onClick = onSignOut, enabled = !busy, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Sign out") }
}

@Composable
private fun BackendUnavailableState() {
    Text("Farm OS is offline", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
    Text("This build has no Supabase connection configured. Recording remains locked because Farm OS will not invent a farm identity.")
    Text("Configure the backend outside source control, then sign in. Your tenancy boundary remains explicit.", color = MaterialTheme.colorScheme.onSurfaceVariant)
}
