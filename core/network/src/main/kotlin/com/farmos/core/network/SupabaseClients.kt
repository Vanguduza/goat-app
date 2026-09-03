package com.farmos.core.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

@Serializable
data class AuthUser(val id: String)

@Serializable
data class SupabaseSession(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_in") val expiresInSeconds: Long,
    val user: AuthUser,
)

@Serializable
private data class PasswordLogin(val email: String, val password: String)

@Serializable
private data class RefreshRequest(@SerialName("refresh_token") val refreshToken: String)

@Serializable
data class FarmMembership(
    @SerialName("farm_id") val farmId: String,
    val role: String,
)

@Serializable
data class PulledDomainEvent(
    @SerialName("event_id") val eventId: String,
    @SerialName("event_type") val eventType: String,
    @SerialName("schema_version") val schemaVersion: Int,
    @SerialName("aggregate_type") val aggregateType: String,
    @SerialName("aggregate_id") val aggregateId: String,
    @SerialName("stream_version") val streamVersion: Long,
    @SerialName("occurred_at") val occurredAt: String,
    @SerialName("recorded_at") val recordedAt: String,
    @SerialName("change_cursor") val changeCursor: Long,
    val payload: JsonElement,
)

class MutableSessionStore(
    private val now: () -> Long = System::currentTimeMillis,
    private val onChanged: ((SupabaseSession?, Long) -> Unit)? = null,
) : AccessTokenProvider {
    @Volatile
    private var session: SupabaseSession? = null
    @Volatile
    private var expiresAtEpochMillis: Long = 0L

    fun set(value: SupabaseSession?) {
        val expiry = if (value == null) 0L else now() + value.expiresInSeconds * 1_000L
        session = value
        expiresAtEpochMillis = expiry
        onChanged?.invoke(value, expiry)
    }

    fun restore(value: SupabaseSession, expiresAtEpochMillis: Long) {
        session = value
        this.expiresAtEpochMillis = expiresAtEpochMillis
    }

    fun current(): SupabaseSession? = session

    fun expiryEpochMillis(): Long = expiresAtEpochMillis

    fun needsRefresh(leewayMillis: Long = 60_000L): Boolean =
        session != null && now() + leewayMillis >= expiresAtEpochMillis

    override suspend fun accessToken(): String? = session?.accessToken
}

class RefreshingAccessTokenProvider(
    private val sessionStore: MutableSessionStore,
    private val refreshSession: suspend () -> Unit,
) : AccessTokenProvider {
    override suspend fun accessToken(): String? {
        if (sessionStore.needsRefresh()) {
            refreshSession()
        }
        return sessionStore.accessToken()
    }
}

class SupabaseIdentityClient(
    private val supabaseUrl: String,
    private val publishableKey: String,
    private val sessionStore: MutableSessionStore,
    private val client: HttpClient = defaultFarmOsHttpClient(),
) {
    suspend fun signIn(email: String, password: String): SupabaseSession {
        val session: SupabaseSession = client.post(
            "${supabaseUrl.trimEnd('/')}/auth/v1/token?grant_type=password",
        ) {
            header("apikey", publishableKey)
            contentType(ContentType.Application.Json)
            setBody(PasswordLogin(email.trim(), password))
        }.body()
        sessionStore.set(session)
        return session
    }

    fun signOut() {
        sessionStore.set(null)
    }

    suspend fun refresh(): SupabaseSession {
        val current = sessionStore.current()
            ?: throw AuthenticationRequiredException("No Supabase session to refresh")
        val response = client.post(
            "${supabaseUrl.trimEnd('/')}/auth/v1/token?grant_type=refresh_token",
        ) {
            header("apikey", publishableKey)
            contentType(ContentType.Application.Json)
            setBody(RefreshRequest(current.refreshToken))
        }

        return when (response.status.value) {
            in 200..299 -> {
                val session: SupabaseSession = response.body()
                sessionStore.set(session)
                session
            }
            400, 401, 403 -> {
                sessionStore.set(null)
                throw AuthenticationRequiredException("Supabase session expired; sign in again")
            }
            else -> {
                throw IllegalStateException("Supabase session refresh failed with HTTP ${response.status.value}")
            }
        }
    }

    suspend fun memberships(): List<FarmMembership> {
        if (sessionStore.needsRefresh()) {
            refresh()
        }
        val session = sessionStore.current()
            ?: throw AuthenticationRequiredException("Authentication required")
        return client.get(
            "${supabaseUrl.trimEnd('/')}/rest/v1/farm_users?select=farm_id,role&user_id=eq.${session.user.id}",
        ) {
            header("apikey", publishableKey)
            header(HttpHeaders.Authorization, "Bearer ${session.accessToken}")
        }.body()
    }
}

class SupabasePullClient(
    private val supabaseUrl: String,
    private val publishableKey: String,
    private val tokenProvider: AccessTokenProvider,
    private val client: HttpClient = defaultFarmOsHttpClient(),
) {
    @Serializable
    private data class PullBody(
        val p_farm_id: String,
        val p_after_cursor: Long,
        val p_limit: Int,
    )

    suspend fun pull(farmId: String, afterCursor: Long, limit: Int = 200): List<PulledDomainEvent> {
        val token = tokenProvider.accessToken()
            ?: throw AuthenticationRequiredException("Authentication required")
        return client.post("${supabaseUrl.trimEnd('/')}/rest/v1/rpc/pull_changes_v1") {
            header("apikey", publishableKey)
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(PullBody(farmId, afterCursor, limit))
        }.body()
    }
}

internal fun defaultFarmOsHttpClient() = HttpClient(OkHttp) {
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true; explicitNulls = false })
    }
}
