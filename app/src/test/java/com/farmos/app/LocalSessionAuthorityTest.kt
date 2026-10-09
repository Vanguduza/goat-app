package com.farmos.app

import android.content.Context
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.core.database.ReplicationDeviceEntity
import com.farmos.domain.access.AccessDenied
import com.farmos.domain.access.Permission
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Malformed/contradictory durable authority rows fail closed, without changing any farm record. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "en-rUS")
class LocalSessionAuthorityTest {
    @Test
    fun inactiveNonlocalAndCutoffBearingDevicesCannotAdmitAStoredSignIn() {
        val cases: List<(ReplicationDeviceEntity) -> ReplicationDeviceEntity> = listOf(
            { it.copy(status = "TEMPORARILY_OFFLINE") },
            { it.copy(status = "RETIRED") },
            { it.copy(status = "LOST_REVOKED") },
            { it.copy(isLocal = false) },
            { it.copy(status = "ACTIVE", revokedAfterSequence = it.lastReportedOwnSequence) },
        )
        cases.forEach { change ->
            LocalSessionAuthorityFixture(ApplicationProvider.getApplicationContext<Context>()).use { fixture ->
                val authority = fixture.authority()
                fixture.updateDevice(change)
                assertRefused(fixture, authority)
            }
        }
    }

    @Test
    fun aMissingAccountOrDeviceCannotAdmitAStoredSignIn() {
        listOf("local_accounts", "replication_devices").forEach { table ->
            LocalSessionAuthorityFixture(ApplicationProvider.getApplicationContext<Context>()).use { fixture ->
                val authority = fixture.authority()
                runBlocking(Dispatchers.IO) {
                    fixture.database.withTransaction {
                        if (table == "local_accounts") {
                            fixture.database.openHelper.writableDatabase.execSQL(
                                "DELETE FROM local_accounts WHERE farmId = ? AND accountId = ?",
                                arrayOf(fixture.farmId, fixture.account.accountId),
                            )
                        } else {
                            fixture.database.openHelper.writableDatabase.execSQL(
                                "DELETE FROM replication_devices WHERE farmId = ? AND deviceId = ?",
                                arrayOf(fixture.farmId, fixture.deviceId),
                            )
                        }
                    }
                }
                assertRefused(fixture, authority)
            }
        }
    }

    @Test
    fun endingASignInPermanentlyDisarmsRetainedCallbacksEvenWhenItsRowsStayActive() {
        LocalSessionAuthorityFixture(ApplicationProvider.getApplicationContext<Context>()).use { fixture ->
            val authority = fixture.authority()
            authority.end()
            assertRefused(fixture, authority)
        }
    }

    private fun assertRefused(fixture: LocalSessionAuthorityFixture, authority: LocalSessionAuthority) = runBlocking(Dispatchers.IO) {
        val rows = fixture.database.reports().herdRegister(fixture.farmId)
        val operations = fixture.database.replication().count(fixture.farmId)
        assertTrue(authority.observe().first() is LocalSessionStatus.ReauthenticationRequired)
        val calls = AtomicInteger()
        val failure = runCatching {
            authority.withPermission(fixture.farmId, Permission.EXPORT_FARM_DATA) { calls.incrementAndGet() }
        }.exceptionOrNull()
        assertTrue(failure is AccessDenied)
        assertEquals(0, calls.get())
        assertEquals(rows, fixture.database.reports().herdRegister(fixture.farmId))
        assertEquals(operations, fixture.database.replication().count(fixture.farmId))
    }
}
