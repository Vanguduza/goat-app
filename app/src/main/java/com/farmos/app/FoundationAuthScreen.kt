@file:Suppress("ktlint:standard:function-naming")

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.farmos.core.design.FarmAnimalLineup
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

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.widthIn(max = 520.dp).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "Animal Farm",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                FarmAnimalLineup(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp),
                )
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        when {
                            !backendConfigured -> {
                                BackendUnavailableState()
                            }

                            memberships.isNotEmpty() -> {
                                FarmSelectionState(
                                    memberships = memberships,
                                    farmNames = farmNames,
                                    busy = busy,
                                    onSelectFarm = onSelectFarm,
                                    onSignOut = onSignOut,
                                )
                            }

                            sessionPresent -> {
                                FarmSetupWizardState(
                                    farmName = farmName,
                                    onFarmNameChange = { farmName = it },
                                    busy = busy,
                                    onCreateFarm = onCreateFarm,
                                    onSignOut = onSignOut,
                                )
                            }

                            else -> {
                                SignInState(
                                    email = email,
                                    password = password,
                                    busy = busy,
                                    onEmailChange = { email = it },
                                    onPasswordChange = { password = it },
                                    onSignIn = onSignIn,
                                )
                            }
                        }
                        error?.let {
                            Text(
                                text = it,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.SignInState(
    email: String,
    password: String,
    busy: Boolean,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSignIn: (String, String) -> Unit,
) {
    var passwordVisible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = email,
        onValueChange = onEmailChange,
        label = { Text("Email address") },
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
        singleLine = true,
        enabled = !busy,
    )
    OutlinedTextField(
        value = password,
        onValueChange = onPasswordChange,
        label = { Text("Password") },
        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                Icon(
                    imageVector = if (passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = if (passwordVisible) "Hide password" else "Show password",
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        singleLine = true,
        enabled = !busy,
    )
    Button(
        onClick = { onSignIn(email, password) },
        enabled = !busy && email.isNotBlank() && password.isNotBlank(),
        modifier = Modifier.align(Alignment.CenterHorizontally).widthIn(min = 160.dp),
    ) {
        Text(if (busy) "Signing in…" else "Sign in")
    }
}

@Composable
private fun ColumnScope.FarmSelectionState(
    memberships: List<FarmMembership>,
    farmNames: Map<String, String>,
    busy: Boolean,
    onSelectFarm: (FarmMembership) -> Unit,
    onSignOut: () -> Unit,
) {
    Text("Choose your farm", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
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
    TextButton(
        onClick = onSignOut,
        enabled = !busy,
        modifier = Modifier.align(Alignment.CenterHorizontally),
    ) {
        Text("Sign out")
    }
}

@Composable
private fun ColumnScope.FarmSetupWizardState(
    farmName: String,
    onFarmNameChange: (String) -> Unit,
    busy: Boolean,
    onCreateFarm: (String) -> Unit,
    onSignOut: () -> Unit,
) {
    var step by remember { mutableStateOf(0) }
    Text("Create your farm", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
    Text("Step ${step + 1} of 2", color = MaterialTheme.colorScheme.onSurfaceVariant)

    if (step == 0) {
        OutlinedTextField(
            value = farmName,
            onValueChange = onFarmNameChange,
            label = { Text("Farm name") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy,
            singleLine = true,
        )
        Button(
            onClick = { step = 1 },
            enabled = !busy && farmName.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Review setup")
        }
    } else {
        Text(farmName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "Farm identity is ready. Species, locations, units, people and infrastructure remain separate setup steps.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = { onCreateFarm(farmName) },
            enabled = !busy && farmName.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (busy) "Creating…" else "Create farm")
        }
        TextButton(onClick = { step = 0 }, enabled = !busy) { Text("Back") }
    }
    TextButton(
        onClick = onSignOut,
        enabled = !busy,
        modifier = Modifier.align(Alignment.CenterHorizontally),
    ) {
        Text("Sign out")
    }
}

@Composable
private fun BackendUnavailableState() {
    Text("Connection unavailable", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
    Text(
        "Sign in is unavailable until this installation is connected to the farm service.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
