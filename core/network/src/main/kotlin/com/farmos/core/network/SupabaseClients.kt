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

class MutableSessionStore : AccessTokenProvider {
    @Volatile
    private var session: SupabaseSession? = null

    fun set(value: SupabaseSession?) {
        session = value
    }

    fun current(): SupabaseSession? = session

    override suspend fun accessToken(): String? = session?.accessToken
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

    suspend fun refresh(): SupabaseSession {
        val current = requireNotNull(sessionStore.current()) { "No Supabase session to refresh" }
        val session: SupabaseSession = client.post(
            "${supabaseUrl.trimEnd('/')}/auth/v1/token?grant_type=refresh_token",
        ) {
            header("apikey", publishableKey)
            contentType(ContentType.Application.Json)
            setBody(RefreshRequest(current.refreshToken))
        }.body()
        sessionStore.set(session)
        return session
    }

    suspend fun memberships(): List<FarmMembership> {
        val session = requireNotNull(sessionStore.current()) { "Authentication required" }
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
        val token = requireNotNull(tokenProvider.accessToken()) { "Authentication required" }
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
