package com.farmos.core.model

import kotlinx.serialization.Serializable

@Serializable
data class CommandEnvelope<T>(
    val commandName: String,
    val commandSchemaVersion: Int = 1,
    val farmId: String,
    val actorId: String,
    val deviceId: String,
    val mutationId: String,
    val aggregateType: String,
    val aggregateId: String,
    val expectedStreamVersion: Long? = null,
    val occurredAtEpochMillis: Long,
    val payload: T,
)

@Serializable
data class DomainEventEnvelope<T>(
    val eventId: String,
    val eventType: String,
    val schemaVersion: Int = 1,
    val farmId: String,
    val aggregateType: String,
    val aggregateId: String,
    val streamId: String,
    val streamVersion: Long,
    val occurredAtEpochMillis: Long,
    val recordedAtEpochMillis: Long,
    val actorUserId: String,
    val deviceId: String,
    val mutationId: String,
    val correlationId: String? = null,
    val causationId: String? = null,
    val payload: T,
)

@Serializable
enum class CommandResultCode {
    ACCEPTED,
    ALREADY_APPLIED,
    CONFLICT,
    VALIDATION_REJECTED,
    AUTH_REJECTED,
    STALE_CLIENT,
    TEMPORARY_FAILURE,
}

@Serializable
data class CommandAcknowledgement(
    val code: CommandResultCode,
    val eventId: String? = null,
    val streamVersion: Long? = null,
    val changeCursor: Long? = null,
    val safeMessage: String? = null,
)

enum class SyncState {
    PENDING,
    IN_FLIGHT,
    ACKNOWLEDGED,
    CONFLICT,
    REJECTED,
    RETRY_WAIT,
    DEAD_LETTER,
}

data class LocalCommandContext(
    val farmId: String,
    val actorId: String,
    val deviceId: String,
    val mutationId: String,
    val occurredAtEpochMillis: Long,
)

data class LocalCommandResult(
    val mutationId: String,
    val aggregateId: String,
    val locallyDurable: Boolean,
)

enum class SearchSource { LOCAL, MEILISEARCH }

enum class AnimalSex { FEMALE, MALE }

enum class AnimalStatus {
    ACTIVE,
    SOLD,
    DEAD,
    CULLED,
    CLOSED,
    ;

    fun wireValue(): String = name.lowercase()

    companion object {
        fun fromWire(value: String): AnimalStatus =
            entries.firstOrNull { it.wireValue() == value.lowercase() }
                ?: error("Unknown animal status $value")
    }
}
