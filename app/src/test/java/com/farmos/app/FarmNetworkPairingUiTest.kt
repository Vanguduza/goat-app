package com.farmos.app

import android.content.Context
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.design.AnimalFarmThemeMode
import com.farmos.core.design.FarmOsTheme
import com.farmos.domain.access.Credential
import com.farmos.domain.access.CredentialHasher
import com.farmos.domain.access.CredentialKind
import com.farmos.domain.access.LocalAccount
import com.farmos.domain.access.LocalRole
import com.farmos.domain.replication.DeviceKeys
import com.farmos.domain.replication.EnrolmentOutcome
import com.farmos.domain.replication.EnrolmentRequest
import com.farmos.domain.replication.PairingClient
import com.farmos.domain.replication.PairingCode
import java.io.File
import java.net.InetAddress
import java.nio.file.Files
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Pairing through the real screens: on the farm device a manager opens Add a device on Devices
 * (FOS-ADMIN-021) and types the code a new device shows; on the new device, Join a farm on this network
 * (FOS-GLOBAL-007) finds the farm, shows the code, installs the grant, copies the farm and lets a worker
 * sign in. The network is an in-memory NSD stand-in; pairing and sync run over real loopback sockets.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS")
class FarmNetworkPairingUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val loopback = InetAddress.getLoopbackAddress()
    private val network = TestFarmNetwork(loopback.hostAddress)
    private val databases = mutableListOf<FarmOsDatabase>()
    private val folders = mutableListOf<File>()
    private lateinit var tabletDb: FarmOsDatabase
    private lateinit var tabletDirectory: LocalFarmDirectory
    private lateinit var runtime: FarmLanRuntime
    private lateinit var owner: LocalAccount
    private lateinit var farmId: String

    private fun database() = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), FarmOsDatabase::class.java)
        .allowMainThreadQueries()
        .build()
        .also { databases += it }

    private fun vault() = testFarmKeyVault(Files.createTempDirectory("vault").toFile().also { folders += it }, TestSoftwareSealer())

    @Before
    fun setUp() = runBlocking {
        tabletDb = database()
        val tabletVault = vault()
        tabletDirectory = LocalFarmDirectory(tabletDb, "tablet", CredentialHasher(iterations = 1_000), initialKeys = tabletVault)
        farmId = tabletDirectory.createFarm("Premier Farm") { id ->
            owner = tabletDirectory.access.setUpFarm(id, "tendai", "Tendai Moyo", Credential(CredentialKind.PIN, "482913")).owner
        }.farmId
        tabletDirectory.transact { it.createAccount(owner, "rudo", "Rudo Chari", LocalRole.WORKER, Credential(CredentialKind.PIN, "730418")) }
        runtime = FarmLanRuntime(tabletDb, tabletVault, network, farmId, "Premier Farm", "tablet").start(intervalSeconds = 3_600)
        val deadline = System.currentTimeMillis() + 10_000
        while (!runtime.state.value.serving && System.currentTimeMillis() < deadline) Thread.sleep(20)
        check(runtime.state.value.serving) { "The farm network did not start" }
    }

    @After
    fun tearDown() {
        runtime.close()
        databases.forEach { it.close() }
        folders.forEach { it.deleteRecursively() }
    }

    private fun waitForTag(tag: String) = compose.waitUntil(15_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }

    private fun textOf(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.Text].joinToString("") { it.text }

    private fun click(label: String) {
        compose.waitUntil(15_000) { compose.onAllNodes(hasClickAction() and hasText(label)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasClickAction() and hasText(label)).performScrollTo().performClick()
    }

    private fun type(label: String, value: String) {
        compose.waitUntil(15_000) { compose.onAllNodes(hasSetTextAction() and hasText(label)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasSetTextAction() and hasText(label)).performScrollTo().performTextInput(value)
    }

    @Test
    fun aManagerAddsADeviceByTypingTheCodeItShows() {
        compose.setContent {
            FarmOsTheme(mode = AnimalFarmThemeMode.LIGHT) {
                SettingsHost(tabletDirectory, tabletDb, farmId, owner.accountId, "tablet", onBack = {}, lan = runtime)
            }
        }
        click("Devices")
        waitForTag("farm-screen:FOS-ADMIN-021")
        click("Add a device")
        waitForTag("settings-pairing-waiting")

        // A new phone on the network asks to join.
        val pairingPort = network.farms().single().descriptor.pairingPort!!
        val identity = DeviceKeys.generate()
        val request = EnrolmentRequest(
            UUID.randomUUID().toString(), farmId, "phone", "Worker phone", DeviceKeys.encode(identity.public),
            ByteArray(16).also(SecureRandom()::nextBytes), System.currentTimeMillis(),
        )
        val outcome = AtomicReference<EnrolmentOutcome?>(null)
        Thread { outcome.set(PairingClient.requestEnrolment(loopback.hostAddress, pairingPort, request)) }.start()

        waitForTag("settings-pairing-request")
        type("Code shown on Worker phone", PairingCode.of(DeviceKeys.farmPairingFingerprint(farmId), request.devicePublicKey, request.nonce))
        click("Approve")

        compose.waitUntil(15_000) { outcome.get() != null }
        assertEquals("phone", (outcome.get() as EnrolmentOutcome.Granted).grant.deviceId)
        waitForTag("settings-device:phone")
        assertEquals("Worker phone joined this farm", textOf("settings-pairing-result"))
        assertNotNull(runBlocking { tabletDb.replication().device(farmId, "phone") })
    }

    @Test
    fun aNewDeviceJoinsTheFarmOnTheNetworkAndAWorkerSignsInThere() {
        val session = runtime.openPairing(owner)
        val phoneDb = database()
        val phoneVault = vault()
        val phoneDirectory = LocalFarmDirectory(phoneDb, "phone", CredentialHasher(iterations = 1_000), initialKeys = phoneVault)
        var signedIn: LocalAccount? = null
        compose.setContent {
            FarmOsTheme(mode = AnimalFarmThemeMode.LIGHT) {
                LocalFarmEntry(
                    phoneDirectory,
                    onSignedIn = { account, _ -> signedIn = account },
                    discovery = network,
                    joiner = FarmJoiner(phoneDb, phoneVault, "phone", "Worker phone"),
                )
            }
        }
        waitForTag("farm-screen:FOS-GLOBAL-014")
        assertEquals(0, phoneDirectory.farms().size)
        click("Join a farm on this network")
        waitForTag("farm-screen:FOS-GLOBAL-007")
        click("Back")
        waitForTag("farm-screen:FOS-GLOBAL-014")
        click("Join a farm on this network")
        waitForTag("farm-screen:FOS-GLOBAL-007")
        waitForTag("join-farm:$farmId")
        compose.onNodeWithTag("join-farm:$farmId").performScrollTo().performClick()

        // The owner reads the code on the phone and types it on the farm device.
        waitForTag("join-code")
        val code = textOf("join-code").filter(Char::isDigit)
        compose.waitUntil(15_000) { session.pending.value != null }
        session.approve(code)

        waitForTag("farm-screen:FOS-GLOBAL-002")
        type("Username", "rudo")
        type("PIN", "730418")
        click("Sign in")
        compose.waitUntil(15_000) { signedIn != null }
        assertEquals("Rudo Chari", signedIn!!.displayName)
        session.close()
    }
}
