package com.farmos.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.farmos.core.design.FosDimens
import com.farmos.core.network.FarmMembership

@Composable
fun FoundationAuthScreen(
    backendConfigured: Boolean,
    busy: Boolean,
    error: String?,
    memberships: List<FarmMembership>,
    onSignIn: (email: String, password: String) -> Unit,
    onSelectFarm: (FarmMembership) -> Unit,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(FosDimens.ScreenMargin),
        verticalArrangement = Arrangement.spacedBy(FosDimens.IntraCardGap),
    ) {
        Text("Farm OS foundation", style = MaterialTheme.typography.titleLarge)

        if (!backendConfigured) {
            Text(
                "Supabase is not configured for this build. Set FARM_OS_SUPABASE_URL and FARM_OS_SUPABASE_PUBLISHABLE_KEY outside source control.",
            )
            Text("Recording stays locked because a fake farm identity is not an acceptable tenancy proof.")
            return@Column
        }

        if (memberships.isEmpty()) {
            Text("Sign in", style = MaterialTheme.typography.labelLarge)
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Button(
                onClick = { onSignIn(email, password) },
                enabled = !busy && email.isNotBlank() && password.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Sign in")
            }
        } else {
            Text("Choose farm", style = MaterialTheme.typography.labelLarge)
            Text("The server membership table is authoritative for this selection.")
            memberships.forEach { membership ->
                Button(
                    onClick = { onSelectFarm(membership) },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("${membership.farmId.take(8)}… · ${membership.role.replace('_', ' ')}")
                }
            }
        }

        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
