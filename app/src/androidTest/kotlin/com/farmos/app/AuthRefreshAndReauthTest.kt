package com.farmos.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.farmos.core.network.AuthenticationRequiredException
import com.farmos.core.network.MutableSessionStore
import com.farmos.core.network.RefreshingAccessTokenProvider
import com.farmos.core.network.SupabaseIdentityClient
import com.farmos.core.network.SupabaseSession
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuthRefreshAndReauthTest {
    @Test
    fun liveRefreshSucceedsAndTerminalRefreshClearsTheSession() = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        val email = requireArgument(arguments.getString("farmosE2eEmail"), "farmosE2eEmail")
        val password = requireArgument(arguments.getString("farmosE2ePassword"), "farmosE2ePassword")
        val supabaseUrl = requireArgument(BuildConfig.SUPABASE_URL, "FARM_OS_SUPABASE_URL")
        val publishableKey = requireArgument(
            BuildConfig.SUPABASE_PUBLISHABLE_KEY,
            "FARM_OS_SUPABASE_PUBLISHABLE_KEY",
        )

        val store = MutableSessionStore()
        val identity = SupabaseIdentityClient(
            supabaseUrl = supabaseUrl,
            publishableKey = publishableKey,
            sessionStore = store,
        )

        val original = identity.signIn(email, password)
        val refreshed = identity.refresh()
        assertEquals(original.user.id, refreshed.user.id)
        assertNotNull(store.current()?.accessToken)
        assertNotEquals("", store.current()?.accessToken)

        var refreshCalls = 0
        val provider = RefreshingAccessTokenProvider(store) {
            refreshCalls++
            identity.refresh()
            Unit
        }
        val token = provider.accessToken()
        assertEquals(store.current()?.accessToken, token)
        assertEquals(0, refreshCalls)

        store.set(
            SupabaseSession(
                accessToken = "expired-access",
                refreshToken = "not-a-refresh-token",
                expiresInSeconds = 1,
                user = original.user,
            ),
        )
        val failure = runCatching { identity.refresh() }.exceptionOrNull()
        assertTrue(failure is AuthenticationRequiredException)
        assertNull(store.current())
        assertEquals(0L, store.expiryEpochMillis())
        identity.signOut()
        assertNull(store.current())
    }

    private fun requireArgument(value: String?, name: String): String =
        value?.takeIf { it.isNotBlank() }
            ?: error("Required Farm OS E2E argument $name is not configured")
}
