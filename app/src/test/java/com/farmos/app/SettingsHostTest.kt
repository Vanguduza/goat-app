package com.farmos.app

import android.content.Context
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.core.database.ApplicationState
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.database.ReplicationApplicationEntity
import com.farmos.core.database.journalLocalOperation
import com.farmos.core.design.AnimalFarmThemeMode
import com.farmos.core.design.FarmOsTheme
import com.farmos.core.design.FarmSafetyAtoms
import com.farmos.domain.access.AccessAction
import com.farmos.domain.access.AccountStatus
import com.farmos.domain.access.Credential
import com.farmos.domain.access.CredentialHasher
import com.farmos.domain.access.CredentialKind
import com.farmos.domain.access.LocalAccount
import com.farmos.domain.access.LocalRole
import com.farmos.domain.access.SignInResult
import com.farmos.domain.ops.GestationSpecies
import com.farmos.domain.replication.MergeClass
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import com.farmos.core.database.ReplicationDeviceEntity
import com.farmos.domain.replication.DeviceStatus
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Local farm settings on an in-memory farm database: Settings Home (FOS-ADMIN-001), Accounts and access
 * (FOS-ADMIN-003), Add account (FOS-ADMIN-004), Account and role (FOS-ADMIN-005), Roles and permissions
 * (FOS-ADMIN-006), Currency (FOS-ADMIN-011), Devices (FOS-ADMIN-021), Storage and backup (FOS-ADMIN-023) and Access history
 * (FOS-ADMIN-024). Every change goes through the local access service and is authorised and audited.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS")
class SettingsHostTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var database: FarmOsDatabase
    private lateinit var directory: LocalFarmDirectory
    private lateinit var farmId: String
    private lateinit var owner: LocalAccount

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), FarmOsDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        directory = LocalFarmDirectory(database, "device-a", CredentialHasher(iterations = 1_000), initialKeys = localAccessTestVault())
        runBlocking {
            farmId = directory.createFarm("Premier Farm") { id ->
                owner = directory.access.setUpFarm(id, "tendai", "Tendai Moyo", Credential(CredentialKind.PIN, "482913")).owner
            }.farmId
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun render(actorId: String?, io: CoroutineDispatcher = Dispatchers.IO) {
        compose.setContent {
            FarmOsTheme(mode = AnimalFarmThemeMode.LIGHT) {
                SettingsHost(directory, database, farmId, actorId, deviceId = "device-a", onBack = {}, io = io)
            }
        }
    }

    private fun type(label: String, value: String) {
        compose.onNode(hasSetTextAction() and hasText(label)).performScrollTo().performTextInput(value)
    }

    private fun click(label: String) {
        compose.onNode(hasClickAction() and hasText(label, substring = true)).performScrollTo().performClick()
    }

    private fun waitForTag(tag: String) = compose.waitUntil(10_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }

    private fun waitForText(text: String) =
        compose.waitUntil(10_000) { compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }

    private fun worker(): LocalAccount = runBlocking {
        directory.transact { it.createAccount(owner, "rudo", "Rudo Chari", LocalRole.WORKER, Credential(CredentialKind.PIN, "730418")) }
    }

    @Test
    fun ownerAddsAWorkerAccountThatIsHashedAuditedAndCanSignIn() {
        render(owner.accountId)
        waitForTag("farm-screen:FOS-ADMIN-001")
        waitForText("Signed in as Tendai Moyo")
        click("Accounts and access")
        waitForTag("farm-screen:FOS-ADMIN-003")
        click("Add account")
        waitForTag("farm-screen:FOS-ADMIN-004")
        type("Name", "Rudo Chari")
        type("Username", "Rudo")
        type("PIN (6 to 12 digits)", "730418")
        type("Confirm PIN", "730418")
        click("Create account")

        waitForTag("farm-screen:FOS-ADMIN-003")
        waitForText("Rudo Chari · Worker")
        val created = database.localAccess().accounts(farmId).single { it.username == "rudo" }
        assertEquals(LocalRole.WORKER.name, created.role)
        assertFalse(created.credentialHash.contains("730418"))
        assertTrue(runBlocking { directory.transact { it.signIn(farmId, "rudo", "730418") } } is SignInResult.SignedIn)
        assertTrue(AccessAction.ACCOUNT_CREATED.name in database.localAccess().audit(farmId, 20).map { it.action })
    }

    @Test
    fun aDuplicateUsernameIsRefusedOnTheFormWithoutCreatingAnAccount() {
        worker()
        render(owner.accountId)
        waitForText("Signed in as Tendai Moyo")
        click("Accounts and access")
        click("Add account")
        waitForTag("farm-screen:FOS-ADMIN-004")
        type("Name", "Another Rudo")
        type("Username", "rudo")
        type("PIN (6 to 12 digits)", "591047")
        type("Confirm PIN", "591047")
        click("Create account")

        waitForTag("settings-error")
        compose.onNodeWithTag("farm-screen:FOS-ADMIN-004").assertExists()
        waitForText("That username is already used on this farm")
        assertEquals(2, database.localAccess().accounts(farmId).size)
    }

    @Test
    fun ownerDisablesAnAccountAndItCanNoLongerSignIn() {
        val rudo = worker()
        render(owner.accountId)
        waitForText("Signed in as Tendai Moyo")
        click("Accounts and access")
        waitForText("Rudo Chari · Worker")
        click("Rudo Chari · Worker")
        waitForTag("farm-screen:FOS-ADMIN-005")
        click("Disable account")

        waitForText("Worker · disabled")
        assertEquals(AccountStatus.DISABLED.name, database.localAccess().account(farmId, rudo.accountId)!!.status)
        assertEquals(SignInResult.Disabled, runBlocking { directory.transact { it.signIn(farmId, "rudo", "730418") } })
    }

    @Test
    fun ownerPromotesAWorkerToSupervisorThroughTheRoleSelector() {
        val rudo = worker()
        render(owner.accountId)
        waitForText("Signed in as Tendai Moyo")
        click("Accounts and access")
        waitForText("Rudo Chari · Worker")
        click("Rudo Chari · Worker")
        waitForTag("farm-screen:FOS-ADMIN-005")
        compose.onNode(hasText("Save role") and hasClickAction()).assertIsNotEnabled()
        compose.onNodeWithTag("settings-role:option:SUPERVISOR").performScrollTo().performClick()
        click("Save role")

        waitForText("Supervisor · active")
        assertEquals(LocalRole.SUPERVISOR.name, database.localAccess().account(farmId, rudo.accountId)!!.role)
        assertTrue(AccessAction.ROLE_CHANGED.name in database.localAccess().audit(farmId, 20).map { it.action })
    }

    @Test
    fun aWorkerIsShownWhyAccountsStorageAndDevicesAreManagementOnly() {
        val rudo = worker()
        render(rudo.accountId)
        waitForText("Signed in as Rudo Chari · Worker")
        click("Accounts and access")
        waitForTag("farm-screen:FOS-ADMIN-003")
        compose.onNodeWithTag(FarmSafetyAtoms.PERMISSION_EXPLANATION).assertExists()
        assertTrue(compose.onAllNodesWithText("Add account").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun storageAndDevicesReportOnlyWhatThisDeviceActuallyHas() {
        render(owner.accountId)
        waitForText("Signed in as Tendai Moyo")
        click("Storage and backup")
        waitForTag("farm-screen:FOS-ADMIN-023")
        val journalled = runBlocking { database.replication().count(farmId) }
        assertTrue("Farm setup is journalled for replication", journalled > 0)
        waitForText("$journalled operation(s) recorded")
        waitForText("No backup has been made.")
        waitForText("Disconnecting Google Drive never deletes farm records on this device.")
    }

    @Test
    fun devicesShowsThisDeviceIdentity() {
        worker()
        render(owner.accountId)
        waitForText("Signed in as Tendai Moyo")
        click("Devices")
        waitForTag("farm-screen:FOS-ADMIN-021")
        waitForText("Device ID device-a")
    }

    @Test
    fun retiringADeviceWarnsAboutWorkThisDeviceHasNotReceivedAndIsJournalled() {
        runBlocking {
            // The phone reported three operations; none has reached this device yet.
            database.replication().upsertDevice(ReplicationDeviceEntity(farmId, "phone", "Phone", DeviceStatus.ACTIVE.name, 3, null, isLocal = false, publicKey = null))
        }
        val before = runBlocking { database.replication().count(farmId) }
        render(owner.accountId)
        waitForText("Signed in as Tendai Moyo")
        click("Devices")
        waitForTag("farm-screen:FOS-ADMIN-021")
        click("Retire Phone")
        waitForTag("settings-retire-warning:phone")
        waitForText("Phone recorded 3 operation(s) this device has not received.")
        click("Retire Phone anyway")
        compose.waitUntil(10_000) { runBlocking { database.replication().device(farmId, "phone")?.status } == DeviceStatus.RETIRED.name }
        assertEquals(before + 1, runBlocking { database.replication().count(farmId) })
        waitForText("Phone · retired")
    }

    @Test
    fun rolesAndPermissionsListsWhatEachRoleMayDo() {
        worker()
        render(owner.accountId)
        waitForText("Signed in as Tendai Moyo")
        click("Roles and permissions")
        waitForTag("farm-screen:FOS-ADMIN-006")
        waitForText("Manage Owner accounts")
    }

    @Test
    fun accessHistoryCountsEveryEntry() {
        worker()
        render(owner.accountId)
        waitForText("Signed in as Tendai Moyo")
        click("Access history")
        waitForTag("farm-screen:FOS-ADMIN-024")
        val total = database.localAccess().auditCount(farmId)
        assertTrue(total >= 2)
        waitForText("$total entries")
        waitForText("Account created")
    }

    @Test
    fun ownerChangesTheFarmCurrencyThroughSearchAndTheChangeIsJournalled() {
        render(owner.accountId)
        waitForText("Signed in as Tendai Moyo")
        click("Currency")
        waitForTag("farm-screen:FOS-ADMIN-011")
        waitForText("Records are kept in USD")
        compose.onNodeWithTag("settings-currency:query").performScrollTo().performTextInput("rand")
        waitForTag("settings-currency:option:ZAR")
        compose.onNodeWithTag("settings-currency:option:ZAR").performScrollTo().performClick()
        click("Save currency")

        waitForText("Records are kept in ZAR")
        assertEquals("ZAR", runBlocking { database.farmCurrency(farmId) })
        val change = runBlocking { database.replication().operationsInRange(farmId, "device-a", 1, 100) }.single { it.operationType == SET_FARM_CURRENCY_COMMAND }
        assertEquals(SET_FARM_CURRENCY_COMMAND, change.operationType)
        assertEquals(MergeClass.FIELD_UPDATE.name, change.mergeClass)
        assertEquals(owner.accountId, change.actorId)
    }

    @Test
    fun aWorkerCannotChangeTheFarmCurrency() {
        val rudo = worker()
        render(rudo.accountId)
        waitForText("Signed in as Rudo Chari · Worker")
        click("Currency")
        waitForTag("farm-screen:FOS-ADMIN-011")
        compose.onNodeWithTag(FarmSafetyAtoms.PERMISSION_EXPLANATION).assertExists()
        assertTrue(compose.onAllNodesWithTag("settings-currency").fetchSemanticsNodes().isEmpty())
        assertEquals("USD", runBlocking { database.farmCurrency(farmId) })
    }

    @Test
    fun storageListsReceivedChangesThatHaveNotTakenEffectWithTheReason() {
        runBlocking {
            database.journalLocalOperation(
                "op-remote-1", farmId, "inventory_item", "item-x", "worker-9", "device-b", 1_790_000_000_000, 1_790_000_000_000,
                null, "inventory.move.v1", "{}", 1,
            )
            database.replicationApplications().upsert(
                ReplicationApplicationEntity("op-remote-1", farmId, ApplicationState.FAILED.name, "Inventory item not found", 1, 1_790_000_000_000),
            )
        }
        // Unconfined keeps the retry and the refresh on the test thread, so nothing still holds the
        // database when the test closes it.
        render(owner.accountId, io = Dispatchers.Unconfined)
        waitForText("Signed in as Tendai Moyo")
        click("Storage and backup")
        waitForTag("settings-review:op-remote-1")
        waitForText("1 received change(s) not yet in effect")
        waitForText("Inventory item not found")
        click("Try again")
        compose.waitUntil(10_000) {
            runBlocking { database.replicationApplications().get(farmId, "op-remote-1") }!!.attempts > 1
        }
        compose.waitUntil(10_000) { compose.onAllNodes(hasClickAction() and hasText("Try again")).fetchSemanticsNodes().any { it.config.getOrNull(SemanticsProperties.Disabled) == null } }
        compose.waitForIdle()
        waitForText("1 received change(s) not yet in effect")
    }

    @Test
    fun theOwnerSetsTheFarmsGoatGestationWhichIsJournalledAndOthersKeepTheirDefaults() {
        render(owner.accountId)
        waitForText("Signed in as Tendai Moyo")
        click("Species configuration")
        waitForTag("farm-screen:FOS-ADMIN-007")
        waitForText("Typical 150 days, expected between day 145 and day 155")
        compose.onNode(hasSetTextAction() and hasText("Goat typical days")).performScrollTo().performTextClearance()
        type("Goat typical days", "152")
        click("Save Goat")

        waitForText("Typical 152 days, expected between day 145 and day 155")
        val goat = runBlocking { database.gestationPeriod(farmId, GestationSpecies.GOAT) }
        assertEquals(152, goat.typicalDays)
        assertEquals(147, runBlocking { database.gestationPeriod(farmId, GestationSpecies.SHEEP) }.typicalDays)
        val journalled = runBlocking { database.replication().operationsInRange(farmId, "device-a", 1, 100) }
        assertTrue(journalled.any { it.operationType == SET_FARM_GESTATION_COMMAND })
    }

    @Test
    fun aWorkerSeesGestationPeriodsButCannotChangeThem() {
        val rudo = worker()
        render(rudo.accountId)
        waitForText("Signed in as Rudo Chari · Worker")
        click("Species configuration")
        waitForTag("farm-screen:FOS-ADMIN-007")
        waitForText("Typical 283 days")
        compose.onNodeWithTag(FarmSafetyAtoms.PERMISSION_EXPLANATION).assertExists()
        assertTrue(compose.onAllNodes(hasSetTextAction() and hasText("Goat typical days")).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun withoutALocalAccountSettingsExplainsHowToSignIn() {
        render(null)
        waitForTag("farm-screen:FOS-ADMIN-001")
        waitForTag(FarmSafetyAtoms.PERMISSION_EXPLANATION)
    }
}
