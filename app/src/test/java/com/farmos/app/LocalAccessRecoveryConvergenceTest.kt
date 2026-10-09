package com.farmos.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.app.LocalAccessTestFixture.Companion.pin
import com.farmos.domain.access.RecoveryCode
import com.farmos.domain.access.RecoveryResult
import com.farmos.domain.replication.LocalPeerTransport
import com.farmos.domain.replication.SyncSession
import com.farmos.domain.replication.SyncSessionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "en-rUS")
class LocalAccessRecoveryConvergenceTest {
    @Test
    fun simultaneousRecoveryOnTwoDevicesConvergesByCanonicalOrderNotArrivalOrder(): Unit = runBlocking(Dispatchers.IO) {
        LocalAccessTestFixture("a-device").use { a ->
            LocalAccessTestFixture("z-device").use { z ->
                a.create()
                z.pairedTo(a.farmId)
                val endpointA = RoomReplicaEndpoint(a.database, a.farmId, a.deviceId, accessReplicationAppliers).apply {
                    registerPairedDevice(z.deviceId, "Paired phone")
                }
                val endpointZ = RoomReplicaEndpoint(z.database, a.farmId, z.deviceId, accessReplicationAppliers).apply {
                    registerPairedDevice(a.deviceId, "Paired tablet")
                }
                assertEquals(SyncSessionStatus.COMPLETED, SyncSession.run(endpointZ, LocalPeerTransport(endpointA), a.deviceId).status)
                a.now += 1_000
                z.now = a.now
                val first = a.directory.transact {
                    it.recoverOwner(a.farmId, a.setup.owner.accountId, a.setup.recoveryCode, pin("639204"))
                } as RecoveryResult.Recovered
                val last = z.directory.transact {
                    it.recoverOwner(a.farmId, a.setup.owner.accountId, a.setup.recoveryCode, pin("817362"))
                } as RecoveryResult.Recovered
                // A already applied A then receives Z; Z already applied Z then receives A.
                assertEquals(SyncSessionStatus.COMPLETED, SyncSession.run(endpointZ, LocalPeerTransport(endpointA), a.deviceId).status)
                for (f in listOf(a, z)) {
                    val hash = requireNotNull(f.database.localAccess().recoveryHash(a.farmId))
                    assertTrue(f.hasher.verify(RecoveryCode.normalise(last.newRecoveryCode), hash))
                    assertFalse(f.hasher.verify(RecoveryCode.normalise(first.newRecoveryCode), hash))
                    assertFalse(f.hasher.verify(RecoveryCode.normalise(a.setup.recoveryCode), hash))
                    val owner = requireNotNull(f.directory.account(a.farmId, a.setup.owner.accountId))
                    assertTrue(f.hasher.verify("817362", owner.credentialHash))
                    assertFalse(f.hasher.verify("639204", owner.credentialHash))
                }
                val beforeA = a.snapshot()
                val beforeZ = z.snapshot()
                assertEquals(SyncSessionStatus.COMPLETED, SyncSession.run(endpointA, LocalPeerTransport(endpointZ), z.deviceId).status)
                assertEquals(beforeA, a.snapshot())
                assertEquals(beforeZ, z.snapshot())
            }
        }
    }

    @Test
    fun aBackwardClockCannotReturnAnUnusableCodeOrPartiallyReplaceTheOwnerCredential(): Unit = runBlocking(Dispatchers.IO) {
        LocalAccessTestFixture().use { f ->
            f.create()
            f.now += 1_000
            val recovered = f.directory.transact {
                it.recoverOwner(f.farmId, f.setup.owner.accountId, f.setup.recoveryCode, pin("639204"))
            } as RecoveryResult.Recovered
            val before = f.snapshot()
            f.now -= 500
            val failure = runCatching {
                f.directory.transact {
                    it.recoverOwner(f.farmId, f.setup.owner.accountId, recovered.newRecoveryCode, pin("817362"))
                }
            }.exceptionOrNull()
            assertTrue(failure?.message.orEmpty().contains("device clock precedes"))
            assertEquals(before, f.snapshot())
            assertTrue(f.hasher.verify("639204", f.directory.account(f.farmId, f.setup.owner.accountId)!!.credentialHash))
            assertTrue(f.hasher.verify(RecoveryCode.normalise(recovered.newRecoveryCode), f.database.localAccess().recoveryHash(f.farmId)!!))
        }
    }

    @Test
    fun aFutureCredentialReceiptCannotLeaveRecoveryReportingAPinThatNeverBecameCurrent(): Unit = runBlocking(Dispatchers.IO) {
        LocalAccessTestFixture().use { f ->
            f.create()
            f.now += 2_000
            f.directory.transact { it.resetCredential(f.setup.owner, f.setup.owner.accountId, pin("639204")) }
            val before = f.snapshot()
            f.now -= 1_000
            val failure = runCatching {
                f.directory.transact { it.recoverOwner(f.farmId, f.setup.owner.accountId, f.setup.recoveryCode, pin("817362")) }
            }.exceptionOrNull()
            assertTrue(failure?.message.orEmpty().contains("device clock precedes the current account identity"))
            assertEquals(before, f.snapshot())
            assertTrue(f.hasher.verify("639204", f.directory.account(f.farmId, f.setup.owner.accountId)!!.credentialHash))
            assertTrue(f.hasher.verify(RecoveryCode.normalise(f.setup.recoveryCode), f.database.localAccess().recoveryHash(f.farmId)!!))
        }
    }

    @Test
    fun sameDeviceCanRotateAgainAtTheSameBusinessTimeUsingItsNextSequence(): Unit = runBlocking(Dispatchers.IO) {
        LocalAccessTestFixture().use { f ->
            f.create()
            val recovered = f.directory.transact {
                it.recoverOwner(f.farmId, f.setup.owner.accountId, f.setup.recoveryCode, pin("639204"))
            } as RecoveryResult.Recovered
            val second = f.directory.transact {
                it.recoverOwner(f.farmId, f.setup.owner.accountId, recovered.newRecoveryCode, pin("817362"))
            } as RecoveryResult.Recovered
            assertTrue(f.hasher.verify(RecoveryCode.normalise(second.newRecoveryCode), f.database.localAccess().recoveryHash(f.farmId)!!))
            assertFalse(f.hasher.verify(RecoveryCode.normalise(recovered.newRecoveryCode), f.database.localAccess().recoveryHash(f.farmId)!!))
            assertTrue(f.hasher.verify("817362", f.directory.account(f.farmId, f.setup.owner.accountId)!!.credentialHash))
        }
    }
}
