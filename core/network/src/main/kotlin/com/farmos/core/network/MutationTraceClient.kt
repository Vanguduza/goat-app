package com.farmos.core.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.serialization.Serializable

@Serializable
data class MutationTraceEvent(
    val eventId: String,
    val eventType: String,
    val streamVersion: Long,
    val changeCursor: Long,
    val recordedAt: String,
    val deviceId: String,
)

@Serializable
data class MutationTraceSearchJob(
    val jobId: Long,
    val origin: String,
    val state: String,
    val attempts: Int,
    val projectionVersion: Long,
    val meiliTaskUid: Long? = null,
    val createdAt: String,
    val completedAt: String? = null,
    val errorPresent: Boolean,
)

@Serializable
data class MutationTraceResponse(
    val code: String,
    val mutationId: String? = null,
    val commandName: String? = null,
    val appliedAt: String? = null,
    val event: MutationTraceEvent? = null,
    val searchJobs: List<MutationTraceSearchJob> = emptyList(),
    val safeMessage: String? = null,
)

class SupabaseMutationTraceClient(
    private val supabaseUrl: String,
    private val publishableKey: String,
    private val tokenProvider: AccessTokenProvider,
    private val client: HttpClient = defaultFarmOsHttpClient(),
) {
    @Serializable
    private data class TraceBody(
        val p_farm_id: String,
        val p_mutation_id: String,
    )

    suspend fun trace(farmId: String, mutationId: String): MutationTraceResponse {
        val token = tokenProvider.accessToken()
            ?: throw AuthenticationRequiredException("Authentication required")
        return client.post("${supabaseUrl.trimEnd('/')}/rest/v1/rpc/mutation_trace_v1") {
            header("apikey", publishableKey)
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(TraceBody(farmId, mutationId))
        }.body()
    }
}
