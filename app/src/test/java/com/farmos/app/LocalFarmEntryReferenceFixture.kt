package com.farmos.app

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.room.Room
import com.farmos.core.database.FarmOsDatabase
import com.farmos.domain.access.Credential
import com.farmos.domain.access.CredentialHasher
import com.farmos.domain.access.CredentialKind
import com.farmos.domain.access.LocalAccount
import java.io.Closeable
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/** The production local-account entrance, backed by one deterministic reference farm. */
internal class LocalFarmEntryReferenceFixture(context: Context) : Closeable {
    private val database = Room.inMemoryDatabaseBuilder(context, FarmOsDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val directory = LocalFarmDirectory(
        database,
        "reference-device",
        CredentialHasher(iterations = 1_000),
        clock = { Instant.parse("2026-09-24T00:00:00Z").toEpochMilli() },
        initialKeys = localAccessTestVault(),
    )

    val farmName = "Premier Farm"
    val username = "tendai"
    val pin = "482913"
    val owner: LocalAccount

    init {
        var createdOwner: LocalAccount? = null
        runBlocking {
            directory.createFarm(farmName) { farmId ->
                createdOwner = directory.access.setUpFarm(
                    farmId, username, "Tendai Moyo", Credential(CredentialKind.PIN, pin),
                ).owner
            }
        }
        owner = requireNotNull(createdOwner)
    }

    @Composable
    fun Content(onSignedIn: (LocalAccount, String) -> Unit = { _, _ -> }) {
        // Match LocalFarmEntryTest for repeatable initial captures. Room still schedules
        // transactions on its own executor; interaction tests must await the onSignedIn callback.
        LocalFarmEntry(directory, onSignedIn = onSignedIn, io = Dispatchers.Unconfined)
    }

    override fun close() = database.close()
}
