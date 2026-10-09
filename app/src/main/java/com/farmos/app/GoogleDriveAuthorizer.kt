package com.farmos.app

import android.accounts.Account
import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Google consent is a backup permission, never a GOAT login. Google Play services owns its token
 * cache and refresh lifecycle; this app keeps no bearer/refresh token in preferences, files or logs.
 * Android OAuth registration must match com.farmos.app and the build's signing SHA-1.
 *
 * API contract: https://developer.android.com/identity/authorization
 */
internal class GoogleDriveAuthorizer(
    context: Context,
    private val configuredAccount: () -> String? = { null },
) : DriveAuthorizer {
    private val client = Identity.getAuthorizationClient(context.applicationContext)
    @Volatile private var selectedAccount: String? = null

    override suspend fun accessToken(): String? {
        val email = configuredAccount()?.takeIf { it.isNotBlank() } ?: selectedAccount ?: return null
        return tokenFor(email)
    }

    /** A pending account switch cannot change the active gateway's credential before verification. */
    fun forAccount(email: String): DriveAuthorizer = object : DriveAuthorizer {
        override suspend fun accessToken(): String? = tokenFor(email.trim())
        override suspend fun invalidateToken(token: String) = this@GoogleDriveAuthorizer.invalidateToken(token)
    }

    private suspend fun tokenFor(email: String): String? {
        val result = client.authorize(request(email)).awaitDriveTask()
        return if (result.hasResolution()) null else checkedToken(result)
    }

    suspend fun requestConsent(email: String): AuthorizationResult {
        require(email.isNotBlank()) { "Enter the Google account email" }
        selectedAccount = email.trim()
        return client.authorize(request(email.trim())).awaitDriveTask()
    }

    fun acceptConsent(data: Intent?): Boolean {
        requireNotNull(data) { "Google Drive permission was not granted" }
        val result = client.getAuthorizationResultFromIntent(data)
        return !result.hasResolution() && checkedToken(result) != null
    }

    fun granted(result: AuthorizationResult): Boolean = !result.hasResolution() && checkedToken(result) != null

    override suspend fun invalidateToken(token: String) {
        client.clearToken(ClearTokenRequest.builder().setToken(token).build()).awaitDriveTask()
    }

    private fun checkedToken(result: AuthorizationResult): String? {
        require(result.grantedScopes.contains(DRIVE_FILE_SCOPE)) { "Google Drive file permission was not granted" }
        return result.accessToken?.takeIf { it.isNotBlank() }
    }

    private fun request(email: String): AuthorizationRequest = AuthorizationRequest.builder()
        .setAccount(Account(email, "com.google"))
        .setRequestedScopes(listOf(Scope(DRIVE_FILE_SCOPE)))
        .build()
}

internal const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"

/** Launches consent only from the owner's explicit Connect action, including a resolution result. */
@Composable
internal fun rememberDriveConsent(
    authorizer: GoogleDriveAuthorizer,
    onAuthorized: () -> Unit,
    onFailure: (String) -> Unit,
): (String) -> Unit {
    val scope = rememberCoroutineScope()
    val latestAuthorized by rememberUpdatedState(onAuthorized)
    val latestFailure by rememberUpdatedState(onFailure)
    var pending by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { response ->
        pending = false
        if (response.resultCode != Activity.RESULT_OK) {
            latestFailure("Google Drive permission was cancelled. Farm records remain saved on this device.")
        } else {
            try {
                if (authorizer.acceptConsent(response.data)) latestAuthorized()
                else latestFailure("Google Drive file permission was not granted")
            } catch (failure: Exception) {
                latestFailure(driveAuthorizationFailure(failure))
            }
        }
    }
    return { email ->
        if (!pending) {
            pending = true
            scope.launch {
                try {
                    val result = authorizer.requestConsent(email)
                    if (result.hasResolution()) {
                        val intent = requireNotNull(result.pendingIntent) { "Google did not return a consent request" }
                        launcher.launch(IntentSenderRequest.Builder(intent.intentSender).build())
                    } else {
                        pending = false
                        if (authorizer.granted(result)) latestAuthorized()
                        else latestFailure("Google Drive file permission was not granted")
                    }
                } catch (cancelled: CancellationException) {
                    pending = false
                    throw cancelled
                } catch (failure: Exception) {
                    pending = false
                    latestFailure(driveAuthorizationFailure(failure))
                }
            }
        }
    }
}

private fun driveAuthorizationFailure(failure: Exception): String {
    val code = (failure as? com.google.android.gms.common.api.ApiException)?.statusCode
    return if (code == 10) {
        "Google Drive is not configured for this app build. Register this package and signing certificate in Google Cloud."
    } else {
        "Google Drive authorisation could not finish. Check Google Play services and the Google account, then try again."
    }
}

private suspend fun <T> Task<T>.awaitDriveTask(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { result -> if (continuation.isActive) continuation.resume(result) }
    addOnFailureListener { failure -> if (continuation.isActive) continuation.resumeWithException(failure) }
    addOnCanceledListener { continuation.cancel() }
}
