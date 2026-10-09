package com.farmos.app

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "en-rUS", application = Application::class)
class DriveBackgroundWorkerTest {
    private lateinit var fixture: DriveDeliveryTestFixture
    private val disabled = mutableListOf<String>()

    @Before
    fun setUp() {
        fixture = DriveDeliveryTestFixture(ApplicationProvider.getApplicationContext<Context>())
    }

    @After
    fun tearDown() {
        fixture.close()
    }

    @Test
    fun offlineWorkerRetriesAndANewWorkerDeliversTheCommittedJournal(): Unit = runBlocking {
        val before = fixture.localOperationCount()
        fixture.online = false
        assertEquals(ListenableWorker.Result.retry(), worker().doWork())
        assertEquals(0, fixture.authorizer.calls.get())
        assertEquals(before, fixture.localOperationCount())
        assertTrue(disabled.isEmpty())

        fixture.online = true
        assertEquals(completed(DriveDeliveryStatus.COMPLETED), worker().doWork())
        assertEquals(before, fixture.runtime().stateCounts().backedUp)
        assertEquals(before, fixture.localOperationCount())
    }

    @Test
    fun consentStopsDurableWorkUntilAnExplicitLocalApproval(): Unit = runBlocking {
        fixture.authorizer.token = null
        assertEquals(completed(DriveDeliveryStatus.NEEDS_CONSENT), worker().doWork())
        assertEquals(listOf(fixture.farmId), disabled)
        assertEquals(1, fixture.authorizer.calls.get())
        fixture.authorizer.token = "test-only-bearer"
        assertEquals(completed(DriveDeliveryStatus.NEEDS_CONSENT), worker().doWork())
        assertEquals(1, fixture.authorizer.calls.get())
        assertEquals(0, fixture.carrier.writes.get())

        fixture.approve()
        assertEquals(completed(DriveDeliveryStatus.COMPLETED), worker().doWork())
        assertEquals(2, disabled.size)
    }

    @Test
    fun foregroundAndActualWorkerPublishEachImmutableObjectOnce(): Unit = runBlocking {
        fixture.carrier.latencyMillis = 35
        val foreground = fixture.runtime()
        val background = worker()
        val first = async(Dispatchers.IO) { foreground.synchroniseOnce() }
        val second = async(Dispatchers.IO) { background.doWork() }
        assertEquals(DriveDeliveryStatus.COMPLETED, first.await())
        assertEquals(completed(DriveDeliveryStatus.COMPLETED), second.await())
        assertTrue(fixture.carrier.objects.isNotEmpty())
        assertEquals(fixture.carrier.objects.size, fixture.carrier.writes.get())
        assertEquals(fixture.localOperationCount(), foreground.stateCounts().backedUp)
    }

    @Test
    fun cancellationPropagatesWithoutRetryingOrDisablingTheGateway(): Unit = runBlocking {
        val cancellation = CancellationException("worker stopped")
        try {
            worker(deliver = { throw cancellation }).doWork()
            fail("Worker cancellation must reach WorkManager")
        } catch (observed: CancellationException) {
            assertSame(cancellation, observed)
        }
        assertTrue(disabled.isEmpty())
        assertEquals(0, fixture.authorizer.calls.get())
    }


    @Test
    fun cancellationAfterRunningClearsTheCachedAndDurableState(): Unit = runBlocking {
        withTimeout(5_000) {
            val before = fixture.localOperationCount()
            val entered = CompletableDeferred<Unit>()
            val runtime = fixture.runtime()
            fixture.carrier.onList = {
                if (runtime.state.value.deliveryStatus == DriveDeliveryStatus.RUNNING) {
                    entered.complete(Unit)
                    awaitCancellation()
                }
            }
            val delivery = async(Dispatchers.IO) {
                worker(deliver = { runtime.synchroniseOnce() }).doWork()
            }
            entered.await()
            assertTrue(runtime.state.value.syncing)
            assertEquals(DriveDeliveryStatus.RUNNING, runtime.state.value.deliveryStatus)
            val publishedBeforeCancellation = fixture.carrier.writes.get()
            delivery.cancelAndJoin()

            assertTrue(delivery.isCancelled)
            assertFalse(runtime.state.value.syncing)
            assertEquals(DriveDeliveryStatus.RETRY_WAIT, runtime.state.value.deliveryStatus)
            assertEquals(DriveDeliveryStatus.RETRY_WAIT, fixture.runtime().state.value.deliveryStatus)
            assertEquals(DriveDeliveryStatus.RETRY_WAIT, fixture.runtime(DriveFarmCoordinator()).state.value.deliveryStatus)
            assertTrue(disabled.isEmpty())
            assertEquals(publishedBeforeCancellation, fixture.carrier.writes.get())
            assertEquals(before, fixture.localOperationCount())

            fixture.carrier.onList = null
            assertEquals(completed(DriveDeliveryStatus.COMPLETED), worker().doWork())
        }
    }

    @Test
    fun invalidFarmInputFailsBeforeOpeningTheRuntime(): Unit = runBlocking {
        assertEquals(
            ListenableWorker.Result.failure(),
            worker(farmId = "../another-farm", deliver = { error("Invalid input must not reach delivery") }).doWork(),
        )
        assertEquals(0, fixture.authorizer.calls.get())
        assertTrue(disabled.isEmpty())
    }

    @Test
    fun durableRequestsCarryOnlyFarmIdentityAndRequireANetwork() {
        val periodic = DriveBackgroundWork.periodicRequest(fixture.farmId)
        val immediate = DriveBackgroundWork.immediateRequest(fixture.farmId)
        for (request in listOf(periodic, immediate)) {
            assertEquals(mapOf(DriveBackgroundWorker.FARM_ID to fixture.farmId), request.workSpec.input.keyValueMap)
            assertEquals(NetworkType.CONNECTED, request.workSpec.constraints.requiredNetworkType)
            assertEquals(DriveBackgroundWorker::class.java.name, request.workSpec.workerClassName)
        }
        assertEquals(TimeUnit.MINUTES.toMillis(15), periodic.workSpec.intervalDuration)
    }

    private fun worker(
        farmId: String = fixture.farmId,
        deliver: suspend (String) -> DriveDeliveryStatus = { requested ->
            check(requested == fixture.farmId)
            fixture.runtime().synchroniseOnce()
        },
    ): DriveBackgroundWorker = TestListenableWorkerBuilder.from(fixture.context, DriveBackgroundWorker::class.java)
        .setInputData(workDataOf(DriveBackgroundWorker.FARM_ID to farmId))
        .setWorkerFactory(object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters,
            ): ListenableWorker? = if (workerClassName == DriveBackgroundWorker::class.java.name) {
                DriveBackgroundWorker(appContext, workerParameters, deliver) { disabled += it }
            } else null
        })
        .build()

    private fun completed(status: DriveDeliveryStatus) =
        ListenableWorker.Result.success(workDataOf(DriveBackgroundWorker.DELIVERY_STATUS to status.name))
}
