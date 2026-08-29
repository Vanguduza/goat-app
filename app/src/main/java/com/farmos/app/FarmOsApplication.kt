package com.farmos.app

import android.app.Application
import androidx.room.Room
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.database.RoomGoatRepository
import com.farmos.core.model.CommandAcknowledgement
import com.farmos.core.model.CommandResultCode
import com.farmos.core.network.CommandTransport
import com.farmos.core.network.WireCommand
import com.farmos.core.sync.SyncEngine
import com.farmos.core.sync.SyncEngineOwner
import com.farmos.domain.goat.GoatRepository

class FarmOsApplication : Application(), SyncEngineOwner {
    lateinit var database: FarmOsDatabase
        private set

    lateinit var goatRepository: GoatRepository
        private set

    override lateinit var syncEngine: SyncEngine
        private set

    override fun onCreate() {
        super.onCreate()
        database = Room.databaseBuilder(
            this,
            FarmOsDatabase::class.java,
            "farm-os.db",
        ).build()

        // Temporary bootstrap identity is deliberately local-only. It is replaced by Supabase Auth
        // before VERTICAL_SLICE_GREEN can be granted.
        val farmId = "bootstrap-farm"
        goatRepository = RoomGoatRepository(database, farmId)

        val transport = object : CommandTransport {
            override suspend fun send(command: WireCommand): CommandAcknowledgement =
                CommandAcknowledgement(
                    code = CommandResultCode.TEMPORARY_FAILURE,
                    safeMessage = "Supabase Auth and project configuration are not connected yet",
                )
        }
        syncEngine = SyncEngine(database.outbox(), transport)
    }
}
