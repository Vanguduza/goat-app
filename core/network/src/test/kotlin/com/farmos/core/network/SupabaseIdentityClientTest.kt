package com.farmos.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlinx.serialization.json.Json

class SupabaseIdentityClientTest {
    private val initialSession = SupabaseSession(
        accessToken = "old-access",
        refreshToken = "old-refresh",
        expiresInSeconds = 60,
        user = AuthUser("user-1"),
    )

    @Test
    fun `successful refresh rotates and persists the session`() {
        val store = MutableSessionStore(now = { 1_000L })
        store.set(initialSession)
        val identity = SupabaseIdentityClient(
            supabaseUrl = "https://farm-os.test",
            publishableKey = "publishable",
            sessionStore = store,
            client = mockClient(
                HttpStatusCode.OK,
                """{"access_token":"new-access","refresh_token":"new-refresh","expires_in":3600,"user":{"id":"user-1"}}""",
            ),
        )

        val refreshed = runSuspend { identity.refresh() }

        assertEquals("new-access", refreshed.accessToken)
        assertEquals("new-refresh", refreshed.refreshToken)
        assertEquals("new-access", store.current()?.accessToken)
        assertEquals(3_601_000L, store.expiryEpochMillis())
    }

    @Test
    fun `terminal refresh rejection clears stale credentials and requires sign in`() {
        var persistedSession: SupabaseSession? = initialSession
        val store = MutableSessionStore(
            now = { 1_000L },
            onChanged = { session, _ -> persistedSession = session },
        )
        store.set(initialSession)
        val identity = SupabaseIdentityClient(
            supabaseUrl = "https://farm-os.test",
            publishableKey = "publishable",
            sessionStore = store,
            client = mockClient(HttpStatusCode.Unauthorized, """{"error":"invalid_grant"}"""),
        )

        assertFailsWith<AuthenticationRequiredException> {
            runSuspend { identity.refresh() }
        }

        assertEquals(null, store.current())
        assertEquals(null, persistedSession)
        assertEquals(0L, store.expiryEpochMillis())
    }

    @Test
    fun `transient refresh failure preserves refresh credentials for retry`() {
        val store = MutableSessionStore(now = { 1_000L })
        store.set(initialSession)
        val identity = SupabaseIdentityClient(
            supabaseUrl = "https://farm-os.test",
            publishableKey = "publishable",
            sessionStore = store,
            client = mockClient(HttpStatusCode.ServiceUnavailable, """{"error":"temporarily_unavailable"}"""),
        )

        val failure = assertFailsWith<IllegalStateException> {
            runSuspend { identity.refresh() }
        }

        assertNotNull(store.current())
        assertEquals("old-refresh", store.current()?.refreshToken)
        assertEquals("Supabase session refresh failed with HTTP 503", failure.message)
    }

    private fun mockClient(status: HttpStatusCode, body: String): HttpClient = HttpClient(
        MockEngine {
            respond(
                content = body,
                status = status,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        },
    ) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; explicitNulls = false })
        }
    }
}

private fun <T> runSuspend(block: suspend () -> T): T {
    var outcome: Result<T>? = null
    block.startCoroutine(
        object : Continuation<T> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(result: Result<T>) {
                outcome = result
            }
        },
    )
    return requireNotNull(outcome) { "Suspending test did not complete synchronously" }.getOrThrow()
}
