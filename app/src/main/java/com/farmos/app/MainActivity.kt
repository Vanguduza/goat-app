package com.farmos.app

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.farmos.core.design.FarmOsTheme
import com.farmos.core.network.AuthenticationRequiredException
import com.farmos.core.network.AuthorizationLoss
import com.farmos.core.network.FarmAccessDecision
import com.farmos.core.network.FarmAccessGuard
import com.farmos.core.network.FarmMembership
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as FarmOsApplication

        setContent {
            FarmOsTheme {
                val scope = rememberCoroutineScope()
                val restoredMembership = remember { app.lastMembershipForCurrentSession() }
                var memberships by remember {
                    mutableStateOf(restoredMembership?.let(::listOf) ?: emptyList())
                }
                var selectedMembership by remember { mutableStateOf(restoredMembership) }
                var authBusy by remember { mutableStateOf(false) }
                var authError by remember { mutableStateOf<String?>(null) }
                var sessionPresent by remember { mutableStateOf(app.sessionStore.current() != null) }
                var farmNames by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

                val requireReauthentication: (String?) -> Unit = { message ->
                    app.applyAuthorizationLoss(AuthorizationLoss.SESSION_EXPIRED)
                    sessionPresent = false
                    memberships = emptyList()
                    selectedMembership = null
                    authBusy = false
                    authError = message ?: "Your session expired. Sign in again."
                }
                val requireFarmReselection: (String, List<FarmMembership>) -> Unit = { message, available ->
                    app.applyAuthorizationLoss(AuthorizationLoss.FARM_ACCESS_REVOKED)
                    sessionPresent = app.sessionStore.current() != null
                    memberships = available
                    selectedMembership = null
                    authBusy = false
                    authError = message
                }

                DisposableEffect(Unit) {
                    app.authorizationListener = { loss ->
                        Handler(Looper.getMainLooper()).post {
                            if (loss == AuthorizationLoss.SESSION_EXPIRED) {
                                requireReauthentication(null)
                            } else {
                                scope.launch {
                                    val available = runCatching {
                                        app.identityClient?.memberships().orEmpty()
                                    }.getOrDefault(emptyList())
                                    requireFarmReselection(
                                        "Farm access was removed. Choose another farm or sign out.",
                                        available,
                                    )
                                }
                            }
                        }
                    }
                    onDispose { app.authorizationListener = null }
                }

                LaunchedEffect(restoredMembership?.farmId) {
                    val restored = restoredMembership ?: return@LaunchedEffect
                    val identity = app.identityClient ?: return@LaunchedEffect
                    runCatching { identity.memberships() }
                        .onSuccess { available ->
                            memberships = available
                            sessionPresent = true
                            farmNames = runCatching {
                                identity.farms(available.map { it.farmId }).associate { it.id to it.name }
                            }.getOrDefault(farmNames)
                            when (
                                val decision = FarmAccessGuard.decide(
                                    sessionPresent = true,
                                    rememberedFarmId = restored.farmId,
                                    memberships = available,
                                )
                            ) {
                                is FarmAccessDecision.Restore -> {
                                    selectedMembership = decision.membership
                                    app.rememberMembership(decision.membership)
                                }
                                FarmAccessDecision.RequireFarmSelection -> {
                                    selectedMembership = null
                                    app.clearRememberedMembership()
                                }
                                FarmAccessDecision.RequireAuthentication -> requireReauthentication(null)
                            }
                        }
                        .onFailure { error ->
                            if (error is AuthenticationRequiredException) {
                                requireReauthentication(error.message)
                            } else {
                                authError = error.message
                            }
                        }
                }

                val membership = selectedMembership
                if (membership == null) {
                    FoundationAuthScreen(
                        busy = authBusy,
                        error = authError,
                        sessionPresent = sessionPresent,
                        memberships = memberships,
                        farmNames = farmNames,
                        onSignIn = { email, password ->
                            scope.launch {
                                authBusy = true
                                authError = null
                                runCatching {
                                    val identity = requireNotNull(app.identityClient) { "Supabase is not configured" }
                                    identity.signIn(email, password)
                                    val available = identity.memberships()
                                    val names = runCatching {
                                        identity.farms(available.map { it.farmId }).associate { it.id to it.name }
                                    }.getOrDefault(emptyMap())
                                    available to names
                                }.onSuccess { (available, names) ->
                                    memberships = available
                                    farmNames = names
                                    sessionPresent = true
                                }.onFailure { error ->
                                    if (error is AuthenticationRequiredException) {
                                        requireReauthentication(error.message)
                                    } else {
                                        authError = error.message
                                    }
                                }
                                authBusy = false
                            }
                        },
                        onSelectFarm = { chosen ->
                            selectedMembership = chosen
                            app.rememberMembership(chosen)
                            authError = null
                        },
                        onCreateFarm = { name ->
                            scope.launch {
                                authBusy = true
                                authError = null
                                runCatching {
                                    val identity = requireNotNull(app.identityClient) { "Supabase is not configured" }
                                    val created = identity.createFarm(name)
                                    val available = identity.memberships()
                                    val names = identity.farms(available.map { it.farmId }).associate { it.id to it.name }
                                    created to (available to names)
                                }.onSuccess { (created, access) ->
                                    val (available, names) = access
                                    memberships = available
                                    farmNames = names
                                    sessionPresent = true
                                    available.firstOrNull { it.farmId == created.id }?.let { chosen ->
                                        selectedMembership = chosen
                                        app.rememberMembership(chosen)
                                    }
                                }.onFailure { error ->
                                    if (error is AuthenticationRequiredException) {
                                        requireReauthentication(error.message)
                                    } else {
                                        authError = error.message
                                    }
                                }
                                authBusy = false
                            }
                        },
                        onSignOut = {
                            scope.launch {
                                runCatching { app.identityClient?.signOut() }
                                app.applyAuthorizationLoss(AuthorizationLoss.SESSION_EXPIRED)
                                sessionPresent = false
                                memberships = emptyList()
                                selectedMembership = null
                                farmNames = emptyMap()
                                authError = null
                            }
                        },
                    )
                } else {
                    FarmSessionContent(
                        app = app,
                        membership = membership,
                        farmName = farmNames[membership.farmId],
                        onRequireReauth = requireReauthentication,
                        onRequireFarmReselection = requireFarmReselection,
                        onSignOut = {
                            scope.launch {
                                runCatching { app.identityClient?.signOut() }
                                app.applyAuthorizationLoss(AuthorizationLoss.SESSION_EXPIRED)
                                sessionPresent = false
                                memberships = emptyList()
                                selectedMembership = null
                                farmNames = emptyMap()
                                authError = null
                            }
                        },
                    )
                }
            }
        }
    }
}
