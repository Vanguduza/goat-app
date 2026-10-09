package com.farmos.app

import android.app.Activity
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.core.app.ActivityOptionsCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.core.design.AnimalFarmThemeMode
import com.farmos.core.design.FarmOsTheme
import com.farmos.domain.access.LocalRole
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowContentResolver

/** Real document-picker callbacks and provider opens remain gated before Room observation catches up. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS")
class ReportDisclosureAuthorityTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var fixture: LocalSessionAuthorityFixture
    private lateinit var authority: LocalSessionAuthority
    private lateinit var documents: DeferredDocumentRegistry
    private lateinit var provider: ReportDocumentProvider
    private lateinit var outputDirectory: File
    private lateinit var outputFile: File
    private lateinit var uri: Uri
    private val mounted = mutableStateOf(true)

    @Before
    fun setUp() {
        fixture = LocalSessionAuthorityFixture(context)
        authority = fixture.authority()
        documents = DeferredDocumentRegistry()
        outputDirectory = Files.createTempDirectory(context.cacheDir.toPath(), "session-export-").toFile()
        outputFile = File(outputDirectory, "report.csv")
        val providerName = "session-export-${fixture.farmId}"
        uri = Uri.parse("content://$providerName/report")
        provider = ReportDocumentProvider(outputFile).also {
            it.attachInfo(context, ProviderInfo().apply { this.authority = providerName })
        }
        ShadowContentResolver.registerProviderInternal(providerName, provider)
    }

    @After
    fun tearDown() {
        compose.runOnIdle { mounted.value = false }
        compose.waitForIdle()
        fixture.close()
        outputDirectory.deleteRecursively()
        File(context.filesDir, "report_exports/${fixture.farmId}.jsonl").delete()
    }

    @Test
    fun aCurrentSessionWritesTheChosenCsvAndItsTruthfulExportReceipt() {
        choose("report-export-herd-register")
        deliver()
        awaitText("Herd register exported: 1 animal(s).")
        assertEquals(1, provider.opens.get())
        val csv = outputFile.readText()
        assertTrue(csv.startsWith("Species,Tag,Name,Sex,Status,Born,Latest weight (kg),Poultry kind"))
        assertTrue(csv.contains("goat,SESSION-001,Session goat,female,active"))
        val log = File(context.filesDir, "report_exports/${fixture.farmId}.jsonl").readText()
        assertTrue(log.contains("herd-register"))
    }

    @Test
    fun aDelayedCsvChooserCannotDiscloseAfterAnAcceptedRemoteDemotionEvenWithAStaleUiBoolean() {
        choose("report-export-herd-register")
        fixture.changeRoleRemotely(LocalRole.WORKER)
        // This owner deliberately has no observer: the UI Boolean still says true.
        deliver()
        awaitText("Export failed: Your active farm account does not have permission for this action.")
        assertNoOutput()
    }

    @Test
    fun aDelayedPdfChooserCannotDiscloseAfterDeviceRevocation() {
        choose("report-export-summary")
        fixture.revokeDeviceRemotely()
        deliver()
        awaitText("Export failed: This device is no longer active for this farm. Ask farm management to review its access.")
        assertNoOutput()
    }

    @Test
    fun aDelayedChooserCannotUseTheOldAuthenticatedCredentialAfterAReset() {
        choose("report-export-herd-register")
        fixture.resetCredentialRemotely()
        deliver()
        awaitText("Export failed: Your sign-in credential has changed. Sign in again with your current PIN or password.")
        assertNoOutput()
    }

    @Test
    fun aRetainedShareActionChecksTheCurrentRoomRoleBeforeStartingAnyActivity() {
        render()
        clickTag("report-open:FOS-REPORT-014")
        awaitTag("farm-screen:FOS-REPORT-014")
        awaitTag("report-share")
        fixture.changeRoleRemotely(LocalRole.WORKER)
        clickTag("report-share")
        awaitText("Share failed: Your active farm account does not have permission for this action.")
        assertEquals(null, shadowOf(context as Application).nextStartedActivity)
        assertNoOutput()
    }

    @Test
    fun anAuthorityFromAnotherFarmCannotWriteThisFarmsChosenReport() {
        val otherFarm = java.util.UUID.randomUUID().toString()
        choose("report-export-herd-register", farmId = otherFarm)
        deliver()
        awaitText("Export failed: Sign in to the farm whose records you want to export.")
        assertNoOutput()
    }

    @Test
    fun cancellationInsideDestinationOpenClosesTheResourceBeforeAWriteOrSuccessCanRun() = runBlocking {
        val closes = AtomicInteger()
        val writes = AtomicInteger()
        val successes = AtomicInteger()
        val stream = object : ByteArrayOutputStream() {
            override fun close() { closes.incrementAndGet(); super.close() }
        }
        val job = launch(Dispatchers.IO) {
            val caller = coroutineContext
            writeAuthorizedReport(
                fixture.farmId, authority,
                open = {
                    // This is the exact lost-return window: provider ownership exists before Room's
                    // cancellable handoff can deliver its result back to the export coroutine.
                    caller.cancel()
                    stream
                },
                write = { writes.incrementAndGet() },
            )
            successes.incrementAndGet()
        }
        job.join()
        assertTrue(job.isCancelled)
        assertEquals(1, closes.get())
        assertEquals(0, writes.get())
        assertEquals(0, successes.get())
        assertNoOutput()
    }

    private fun render(farmId: String = fixture.farmId) {
        compose.setContent {
            if (mounted.value) FarmOsTheme(mode = AnimalFarmThemeMode.LIGHT) {
                CompositionLocalProvider(LocalActivityResultRegistryOwner provides documents) {
                    ReportsModuleHost(fixture.database, farmId, canExport = true, onBack = {}, exportAuthority = authority)
                }
            }
        }
        awaitTag("farm-screen:FOS-REPORT-001")
        // The hub exists before the Room projection inserts metric sections above its actions.
        // Exited is present even for an empty farm, so both authority fixtures wait for final data.
        awaitTag("report-metric:exited")
        compose.onNodeWithTag("report-metric:exited").assertTextEquals("0 animals")
        if (farmId == fixture.farmId) {
            compose.onNodeWithTag("report-metric:active-goat").assertTextEquals("1 animals")
        } else {
            compose.onNodeWithTag("report-metric:active-goat").assertDoesNotExist()
        }
    }

    private fun choose(action: String, farmId: String = fixture.farmId) {
        render(farmId)
        clickTag("report-open-export")
        awaitTag("farm-screen:FOS-REPORT-011")
        compose.onNodeWithTag("farm-screen:FOS-REPORT-001").assertDoesNotExist()
        clickTag(action)
        compose.runOnIdle {
            assertEquals(1, documents.launches)
            assertEquals(0, provider.opens.get())
        }
    }

    private fun clickTag(tag: String) {
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasTestTag(tag) and hasClickAction() and isEnabled())
                .fetchSemanticsNodes().size == 1
        }
        compose.onNodeWithTag(tag)
            .performScrollTo()
            .assertIsDisplayed()
            .assertIsEnabled()
            .performClick()
    }

    private fun deliver() = compose.runOnIdle { documents.deliver(uri) }

    private fun assertNoOutput() {
        assertEquals(0, provider.opens.get())
        assertFalse(outputFile.exists())
        assertFalse(File(context.filesDir, "report_exports/${fixture.farmId}.jsonl").exists())
        runBlocking(Dispatchers.IO) {
            assertEquals(listOf("SESSION-001"), fixture.database.reports().herdRegister(fixture.farmId).map { it.tag })
        }
    }

    private fun awaitTag(tag: String) = compose.waitUntil(10_000) {
        compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }
    private fun awaitText(text: String) = compose.waitUntil(10_000) {
        compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
    }
}

private class DeferredDocumentRegistry : ActivityResultRegistry(), ActivityResultRegistryOwner {
    override val activityResultRegistry: ActivityResultRegistry get() = this
    private var request: Int? = null
    var launches = 0
        private set

    override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
        request = requestCode
        launches++
    }

    fun deliver(uri: Uri) {
        check(dispatchResult(requireNotNull(request), Activity.RESULT_OK, Intent().setData(uri)))
        request = null
    }
}

private class ReportDocumentProvider(private val output: File) : ContentProvider() {
    val opens = AtomicInteger()
    override fun onCreate() = true
    override fun getType(uri: Uri) = "application/octet-stream"
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        opens.incrementAndGet()
        return ParcelFileDescriptor.open(output, ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_TRUNCATE)
    }
}
