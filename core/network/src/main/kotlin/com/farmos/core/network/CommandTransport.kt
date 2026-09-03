package com.farmos.core.network

import com.farmos.core.model.CommandAcknowledgement
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

interface AccessTokenProvider {
    suspend fun accessToken(): String?
}

class AuthenticationRequiredException(message: String) : IllegalStateException(message)

class UnsupportedServerEvent(message: String) : IllegalStateException(message)

interface CommandTransport {
    suspend fun send(command: WireCommand): CommandAcknowledgement
}

@Serializable
data class WireCommand(
    val rpcName: String,
    val mutationId: String,
    val farmId: String,
    val actorId: String,
    val deviceId: String,
    val expectedStreamVersion: Long?,
    val occurredAtEpochMillis: Long,
    val payload: JsonElement,
)

@Serializable
private data class RpcRequestBody(
    val p_mutation_id: String,
    val p_farm_id: String,
    val p_device_id: String,
    val p_expected_stream_version: Long?,
    val p_occurred_at_epoch_ms: Long,
    val p_payload: JsonElement,
)

class SupabaseRpcCommandTransport(
    private val supabaseUrl: String,
    private val publishableKey: String,
    private val tokenProvider: AccessTokenProvider,
    private val client: HttpClient = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    },
) : CommandTransport {
    override suspend fun send(command: WireCommand): CommandAcknowledgement {
        val token = tokenProvider.accessToken()
            ?: throw AuthenticationRequiredException("Authenticated Supabase session required")
        return client.post("${supabaseUrl.trimEnd('/')}/rest/v1/rpc/${command.rpcName}") {
            header("apikey", publishableKey)
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(
                RpcRequestBody(
                    p_mutation_id = command.mutationId,
                    p_farm_id = command.farmId,
                    p_device_id = command.deviceId,
                    p_expected_stream_version = command.expectedStreamVersion,
                    p_occurred_at_epoch_ms = command.occurredAtEpochMillis,
                    p_payload = command.payload,
                ),
            )
        }.body()
    }
}
