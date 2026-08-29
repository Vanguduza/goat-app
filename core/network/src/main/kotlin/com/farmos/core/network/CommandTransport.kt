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
        val token = requireNotNull(tokenProvider.accessToken()) { "Authenticated Supabase session required" }
        return client.post("${supabaseUrl.trimEnd('/')}/rest/v1/rpc/${command.rpcName}") {
            header("apikey", publishableKey)
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(
                mapOf(
                    "p_mutation_id" to command.mutationId,
                    "p_farm_id" to command.farmId,
                    "p_device_id" to command.deviceId,
                    "p_expected_stream_version" to command.expectedStreamVersion,
                    "p_occurred_at_epoch_ms" to command.occurredAtEpochMillis,
                    "p_payload" to command.payload,
                ),
            )
        }.body()
    }
}
