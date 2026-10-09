package com.farmos.app

import android.app.Application
import android.content.Context
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Real REST lookup/upload control flow with a local Room authority change and no external network. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "en-rUS", application = Application::class)
class DriveRestApprovalTest {
    private lateinit var fixture: DriveDeliveryTestFixture

    @Before
    fun setUp() {
        fixture = DriveDeliveryTestFixture(ApplicationProvider.getApplicationContext<Context>())
    }

    @After
    fun tearDown() {
        fixture.close()
    }

    @Test
    fun aRevocationCutoffDuringTheRestLookupPreventsTheActualPost(): Unit = runBlocking {
        val before = fixture.localOperationCount()
        val bytes = "sealed carrier fixture".toByteArray()
        val http = ScriptedDriveHttp(bytes) {
            fixture.database.withTransaction {
                val dao = fixture.database.replication()
                val device = requireNotNull(dao.device(fixture.farmId, fixture.deviceId))
                // Status remains ACTIVE: a learned cutoff alone must stop this network write.
                dao.upsertDevice(device.copy(revokedAfterSequence = device.lastReportedOwnSequence))
            }
        }
        try {
            approvedStore(http).putIfAbsent(path(bytes), bytes, sha256Hex(bytes))
            fail("A revoked gateway must not publish after its REST lookup")
        } catch (_: DriveGatewayApprovalException) {
            // This is current local authority denial, not an HTTP failure after publication.
        }
        assertEquals(1, http.lookups)
        assertEquals(0, http.posts)
        assertEquals(before, fixture.localOperationCount())
    }

    @Test
    fun anActiveApprovalStillUploadsAndVerifiesTheExactBytes(): Unit = runBlocking {
        val bytes = "sealed carrier fixture".toByteArray()
        val http = ScriptedDriveHttp(bytes) {}
        assertTrue(approvedStore(http).putIfAbsent(path(bytes), bytes, sha256Hex(bytes)))
        assertEquals(1, http.posts)
        assertEquals(2, http.lookups)
        assertEquals(1, http.downloads)
    }

    private fun approvedStore(http: ScriptedDriveHttp): DriveObjectStore {
        val config = requireNotNull(fixture.store.get(fixture.farmId))
        val rest = DriveRestStore(fixture.authorizer, { config.folderId }, http::open)
        val authority = DriveGatewayAuthority(fixture.database, fixture.deviceId)
        return ApprovedDriveStore(rest) {
            authority.requireCurrent(fixture.farmId, config, fixture.store)
        }
    }

    private fun path(bytes: ByteArray) =
        "GOAT/farms/" + fixture.farmId + "/attachments/" + sha256Hex(bytes)

    private class ScriptedDriveHttp(
        private val bytes: ByteArray,
        private val afterFirstLookup: suspend () -> Unit,
    ) {
        var lookups = 0
        var posts = 0
        var downloads = 0

        fun open(url: URL): HttpURLConnection = object : HttpURLConnection(url) {
            override fun connect() = Unit
            override fun disconnect() = Unit
            override fun usingProxy() = false

            override fun getResponseCode(): Int {
                if (requestMethod == "GET" && !url.query.orEmpty().contains("alt=media")) {
                    lookups++
                    if (lookups == 1) runBlocking { afterFirstLookup() }
                }
                return HTTP_OK
            }

            override fun getInputStream(): InputStream =
                if (url.query.orEmpty().contains("alt=media")) {
                    downloads++
                    bytes.inputStream()
                } else {
                    (if (posts == 0) """{"files":[]}""" else """{"files":[{"id":"stored-object"}]}""")
                        .byteInputStream()
                }

            override fun getOutputStream(): OutputStream {
                check(requestMethod == "POST")
                posts++
                return ByteArrayOutputStream()
            }
        }
    }
}
