package com.farmos.app

import android.app.Application
import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.lifecycle.MutableLiveData
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Operation
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** WorkManager reports enqueue failure through its Operation future, after the method has returned. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "en-rUS", application = Application::class)
class DriveBackgroundSchedulingTest {
    @Test
    fun eitherAsynchronousEnqueueFailureReachesTheSchedulingCaller(): Unit = runBlocking {
        withTimeout(5_000) {
            for (failPeriodic in listOf(true, false)) {
                val periodic = PendingOperation()
                val immediate = PendingOperation()
                if (!failPeriodic) periodic.succeed()
                val reached = CompletableDeferred<Unit>()
                var immediateRequested = false
                val scheduling = async {
                    runCatching {
                        DriveBackgroundWork.enqueueRequests(
                            periodic = {
                                if (failPeriodic) reached.complete(Unit)
                                periodic
                            },
                            immediate = {
                                immediateRequested = true
                                reached.complete(Unit)
                                immediate
                            },
                        )
                    }
                }
                reached.await()
                assertFalse("Scheduling is not accepted while its Operation is pending", scheduling.isCompleted)
                val cause = IllegalStateException(if (failPeriodic) "Periodic enqueue storage failed" else "Immediate enqueue storage failed")
                val failure = IOException("Asynchronous enqueue failed", cause)
                (if (failPeriodic) periodic else immediate).fail(failure)
                val propagated = requireNotNull(scheduling.await().exceptionOrNull()) { "The enqueue failure must reach its caller" }
                // Coroutine stack-trace recovery may copy a throwable while preserving its diagnosis.
                assertEquals(IOException::class.java, propagated.javaClass)
                assertEquals(failure.message, propagated.message)
                val rootCause = generateSequence(propagated) { it.cause }.last()
                assertEquals(cause.javaClass, rootCause.javaClass)
                assertEquals(cause.message, rootCause.message)
                assertEquals(!failPeriodic, immediateRequested)
            }
        }
    }

    private class PendingOperation : Operation {
        private val state = MutableLiveData<Operation.State>(Operation.IN_PROGRESS)
        private lateinit var completion: CallbackToFutureAdapter.Completer<Operation.State.SUCCESS>
        private val result = CallbackToFutureAdapter.getFuture<Operation.State.SUCCESS> { completer ->
            completion = completer
            "Drive enqueue operation fixture"
        }

        override fun getState() = state
        override fun getResult() = result
        fun succeed() { completion.set(Operation.SUCCESS) }
        fun fail(failure: Throwable) { completion.setException(failure) }
    }
}
