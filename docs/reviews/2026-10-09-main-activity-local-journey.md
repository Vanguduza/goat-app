# Actual MainActivity local journey

## Contract and source

This isolated test tranche starts at `4a556f3b5a8fe20cce89e954d52288b3545270eb`. It adds one instrumentation test of the production `MainActivity` and the Compose Android-test dependency required to drive its rendered UI. It changes no production route, authority rule, signing identity or release gate.

`app/src/androidTest/kotlin/com/farmos/app/LocalFarmActivityJourneyTest.kt` requires the canonical local-only build and no pre-existing local farm. It fails clearly before launching the activity if that condition is not met. It creates only synthetic test data through the ordinary UI and retains that fixture after closing the activity; it never deletes existing farm data or a private vault. Each rerun needs a fresh, isolated test installation. Other current app instrumentation suites use separately named Room databases.

The test traverses Intro (`FOS-GLOBAL-014`), farm/Owner setup (`FOS-GLOBAL-006`), acknowledgement of the generated recovery code, species setup (`FOS-GLOBAL-008`), optional Drive setup (`FOS-ADMIN-013`) with no account configured, management home (`FOS-HOME-012-A`), the species entrance (`FOS-HOME-002`), Goat home (`FOS-GOAT-001`), registration (`FOS-GOAT-004`), weight capture (`FOS-GOAT-011`) and the exact Goat profile (`FOS-GOAT-003`).

It reads actual Room state to verify the synthetic Owner and animal identity, 45.125 kg stored exactly as 45125 grams, and exactly one registration plus one weight operation with the expected animal, farm, actor and device. After `ActivityScenario.recreate()`, it signs in again through `FOS-GLOBAL-002`, opens the same exact animal through local search (`FOS-HOME-006` / `FOS-HOME-007`), verifies the rendered weight (the existing profile rounds kilograms to two decimal places), and proves that the original immutable business operations were not duplicated. Dispatching Back through that real activity returns to the retained search and then management home.

## Execution

The canonical `scripts/ci/run-two-device-e2e.sh` app invocation discovers this class automatically; its only excluded class remains the server-era `AuthRefreshAndReauthTest`. Compilation can be checked without a device with `:app:assembleDebugAndroidTest`. A bounded device run uses:

```sh
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.farmos.app.LocalFarmActivityJourneyTest \
  --stacktrace
```

The emulator, application installation and build must be isolated testing resources. A provider-configured build or existing local farm is an explicit test precondition failure, not permission to alter the test fixture or bypass admission.

## Evidence boundary

The first integrated build at `88de9dabf8e277bf0fb7a6ef4a21769041761aa4` ran from 2026-10-09T16:20:53.513147Z to 2026-10-09T16:34:15.576021Z. Its retained reports contain 788 unit-test cases and no recorded failed cases, but the combined build **failed** at `:app:processDebugAndroidTestManifest`: `debugAndroidTestRuntimeClasspath` could not resolve `androidx.compose.ui:ui-test-junit4` because no version constraint applied to that configuration. The instrumentation journey did not execute. The result record `combined-native-attempt1-result.json` has SHA-256 `589b9843003d84752a10bad44c51a7fc95047dafa22be1c9dc5ee9bebb1ab524`; its log `combined-native-attempt1-gradle.log` has SHA-256 `83dc0e09afccb952cfe2334f650d3fc26830972414e2ab8e0368426a87f3bf73`.

The correction shares the existing Compose BOM `2026.08.00` between `implementation` and `androidTestImplementation`, following the [official Android BOM configuration](https://developer.android.com/develop/ui/compose/bom). It preserves the app\'s existing dependency version and changes no journey assertion. Targeted instrumentation packaging and device execution remain pending; this is not a full-CI or release pass.

The final integrated candidate must include the independently reviewed local-session/key-bootstrap repairs before device execution; this test does not supply a runtime bypass for them.

Activity recreation is not process death, a physical-device restart, filesystem power loss or a second device. The local-only build and unconfigured Drive account do not constitute radio/network isolation, live Google consent, Drive restoration, physical hardware evidence or owner field acceptance. Existing file-backed Room reopen and authenticated loopback replication tests provide separate evidence under their recorded scope.

This tranche makes no `FEATURE_GREEN`, `MODULE_GREEN`, `VISUAL_GREEN` or `MVP_GREEN` claim and preserves the full 156-feature, 29-module and 545-screen contract.
