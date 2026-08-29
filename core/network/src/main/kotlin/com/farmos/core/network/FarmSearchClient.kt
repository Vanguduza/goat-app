package com.farmos.core.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
private data class SearchTokenRequest(val farmId: String)

@Serializable
private data class SearchTokenResponse(
    val token: String,
    val expiresAt: Long,
    val indexPrefix: String,
)

@Serializable
private data class MeiliSearchRequest(
    val q: String,
    val limit: Int,
    val attributesToRetrieve: List<String> = listOf("id", "tag", "display_name", "status", "species_code"),
)

@Serializable
private data class MeiliEntityHit(
    val id: String,
    val tag: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    val status: String? = null,
    @SerialName("species_code") val speciesCode: String? = null,
)

@Serializable
private data class MeiliSearchResponse(
    val hits: List<MeiliEntityHit>,
)

data class FarmSearchHit(
    val id: String,
    val tag: String?,
    val displayName: String?,
    val status: String?,
    val speciesCode: String?,
)

class FarmSearchClient(
    private val supabaseUrl: String,
    private val supabasePublishableKey: String,
    private val meiliHost: String,
    private val tokenProvider: AccessTokenProvider,
    private val client: HttpClient = defaultFarmOsHttpClient(),
) {
    suspend fun searchAnimals(farmId: String, query: String, limit: Int = 20): List<FarmSearchHit> {
        require(meiliHost.isNotBlank()) { "Meilisearch host is not configured" }
        val accessToken = requireNotNull(tokenProvider.accessToken()) { "Authentication required" }
        val tenant: SearchTokenResponse = client.post(
            "${supabaseUrl.trimEnd('/')}/functions/v1/search-token",
        ) {
            header("apikey", supabasePublishableKey)
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            contentType(ContentType.Application.Json)
            setBody(SearchTokenRequest(farmId))
        }.body()

        val response: MeiliSearchResponse = client.post(
            "${meiliHost.trimEnd('/')}/indexes/${tenant.indexPrefix}-animals-v1/search",
        ) {
            header(HttpHeaders.Authorization, "Bearer ${tenant.token}")
            contentType(ContentType.Application.Json)
            setBody(MeiliSearchRequest(q = query.trim(), limit = limit.coerceIn(1, 100)))
        }.body()

        return response.hits.map { hit ->
            FarmSearchHit(
                id = hit.id,
                tag = hit.tag,
                displayName = hit.displayName,
                status = hit.status,
                speciesCode = hit.speciesCode,
            )
        }
    }
}
