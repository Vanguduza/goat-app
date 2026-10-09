# GOAT on the governed Artemis Android testing plane

This is an operator contract for a reusable Android test target. GOAT remains a local-first
Room application under [Project Truth](../00_PROJECT_TRUTH.md). Artemis and Hermes are test
infrastructure, not application dependencies. Test receipts do not issue feature, module,
visual, physical-device, Google/Drive or MVP qualification.

The manifest contains **four bounded UI journeys**, not all GOAT E2E tests: first-run
entry/setup, management-home navigation, one synthetic goat registration/weight/readback
and one synthetic task create/complete/readback. The deterministic suites and other
product gates remain independently required.

## Control and source authority

Use **dial_android_testing**, server **dial-hermes-android-testing**, version **2.0.0**.
Do not expose or call raw upstream Artemis MCP, the private admin service, or a generic
shell dispatcher as an alternative.

| Contract | Source |
| --- | --- |
| Governed tools and JSON-RPC | DDS `agent-system/orchestration/android-testing-mcp.mjs` |
| Admission, task ownership, leases, terminal seal | DDS `agent-system/orchestration/android-testing-plane.mjs` |
| Checkout/origin binding | DDS `agent-system/orchestration/project-repository-resolver.mjs` |
| Restricted bridge | DDS `agent-system/orchestration/artemis-subordinate-client.mjs` and `deploy/netcup/hermes-control/artemis/dial_artemis_mcp_bridge.py` |
| Installation and console boundary | DDS `docs/orchestration/HERMES_ARTEMIS_OPENVIKING_INTEGRATION.md` |
| Android owner console | VAN `android/.../command/artemis/ArtemisConsoleRoute.kt` and `backend/van_gateway/artemis/console.py` |
| Task/test distinction | Artemis `artemis/graph/checkpoints.py` and `mcp_server/tools/task_manager.py` |

DDS checkout reviewed: `b4cc167f7fb9394630b637c8c808acf01bc9e143`. The installed ubuntu
console/supervisor still use `/home/ubuntu/dial-new` at
`9d4a19bd2e96637154c335585f2df96e25c63abc`. The four testing-plane/MCP/subordinate/bridge
files above were byte-identical when inspected. Do not repoint DDS services or change a
development gate while preparing GOAT tests.

Artemis is pinned to `google/artemis@371aa6df56880643da57b30da936e9812fb0ec66`,
installed below `/opt/hermes-mobile-fabric/artemis/` with the DIAL shell-hardening patch.
It runs as **ubuntu (UID 1000)**. VAN's short-lived console session requires owner device
proof; its Android app never receives the private server console token.

## Target, admission and tool resolution

The October 2026 target is the owner-managed ubuntu AVD **goat-artemis-api36**, expected
serial **emulator-5560**, using the official API 36 Google Play image. Installation or
service activity alone does not prove boot/readiness. Retain actual emulator/image
versions, boot result, guest API, serial, display/font configuration and provider readiness
with each execution.

This dial-control guest has no KVM acceleration. The first observed software attempt used
2 GB guest RAM and two guest cores, and was OOM-killed at the 3 GB host service cap.
The smaller retry used 1536 MB guest RAM and one guest core, retaining that 3 GB cap.
At 14:45 UTC it also had not completed boot: the service reached its memory cap and an
ADB getprop probe timed out. The observed OOM counter was then zero, so this second
result is recorded as an unaccepted boot under memory pressure, not a confirmed OOM.
Headless operation and explicit software acceleration remain necessary. A later functional software-emulator
exercise would not prove accelerated performance or physical-device behavior.

Device admission is in `/var/lib/dial-control/config/android-testing-devices.json`:

~~~json
{
  "schema_version": 2,
  "authority": "OWNER_ADMISSION_REQUIRED",
  "devices": [{"serial": "emulator-5560", "enabled": true}],
  "avds": []
}
~~~

This is an additive example, not an instruction to replace the registry. Enable the serial
only after observing the intended ready guest. Keep the new AVD absent/disabled in launch
admission: stock Artemis passes only an AVD name, without this host's required
headless/software/resource arguments, and monitors boot for only 180 seconds. The
owner-managed service boots the guest; Artemis controls the admitted ready serial.
Preserve other AVDs and devices.

The separate `/var/lib/dial-control/config/project-repositories.json` testing binding
belongs at `projects["goat-app"]`, preserving all other entries:

~~~json
{
  "path": "/absolute/path/to/the/frozen/goat-checkout",
  "origin_url": "https://github.com/Vanguduza/goat-app.git",
  "default_branch": "main",
  "authority_mode": "PROJECT_REPOSITORY_CANON"
}
~~~

This binds testing evidence to a checkout/origin. It does not admit a DDS mission or alter
DDS qualification. Using project `dial` for GOAT would misattribute the receipt.

Let `SDK` denote the operator-verified Android SDK; the current path is
`/home/ubuntu/work/goat-review-tooling-20261009/android-sdk`. Set `ANDROID_HOME` and
`ANDROID_SDK_ROOT` to it, and both `DIAL_ADB_BIN` and `ARTEMIS_ADB_PATH` to its absolute
`platform-tools/adb`. Prefix existing PATH with `SDK/platform-tools` and `SDK/emulator`.
An older upstream helper checks PATH first, so a single ADB override is insufficient.

This Artemis pin has no `EMULATOR_BIN` override. Emulator discovery uses PATH then SDK
paths. AVD discovery scans ubuntu's `~/.android/avd/*.ini`; setting only
`ANDROID_AVD_HOME` does not make the probe discover an AVD.

Run the installed governed MCP as ubuntu:

~~~text
/home/ubuntu/.local/bin/node /home/ubuntu/dial-new/agent-system/orchestration/android-testing-mcp.mjs
~~~

Its environment uses `DIAL_CONTROL_HOME=/var/lib/dial-control`,
`DIAL_REPO_DIR=/home/ubuntu/dial-new`, `DIAL_PROJECT_ID=goat-app`,
`DIAL_HARNESS_ID=goat-artemis-qualification` and the installed absolute
`DIAL_ARTEMIS_ROOT`, `DIAL_ARTEMIS_BIN`, `DIAL_ARTEMIS_PYTHON` and
`DIAL_ARTEMIS_BRIDGE`. Use only authorized operator-managed provider configuration;
never put keys, PINs, recovery codes or private Google accounts in a request. Its observed
missing readiness is recorded below.

## Initialize and inspect

Send newline-delimited JSON, awaiting each response before the next request:

~~~json
{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"goat-artemis-qualification","version":"1.0"}}}
~~~

Check server name/version, send `notifications/initialized`, then `tools/list`.
Stop if the returned contract differs from the reviewed one.

| Tool | Arguments/use |
| --- | --- |
| `android_testing_status` | `{}`; admitted and connected serials |
| `android_diagnose` | `{"device_serial":"emulator-5560","attempt_fix":false,"verify_credentials":true,"probe_device":true}`; no launch |
| `android_device_state` | `{"device_serial":"emulator-5560","view_type":"screenshot"}` or `"hierarchy"` |
| `android_task_start` | The prepared request below: explicit project/serial/APK/package and strict Pro |
| `android_task_manage` | `{"trace_id":"RETURNED_ID","action":"status"}`; terminal status seals evidence |
| `android_trace_inspect` | `{"trace_id":"RETURNED_ID","action":"view_summary"}`; inspect steps with `view_step_details`/`view_step_screenshots` and `step_number`, or `search` with `query` |

Calls use `{"jsonrpc":"2.0","id":N,"method":"tools/call","params":{"name":"TOOL","arguments":{...}}}`.
Device capture writes an observation; terminal polling can write the evidence seal.
Artemis provider readiness is separate from Android Google consent and GOAT Drive setup.

## Provider readiness is separate from Google consent

The canonical preflight at **2026-10-09 14:23:44 UTC**, using
`android_diagnose` with `verify_credentials=true` and `probe_device=false`,
reported **zero configured providers** and no pinned Artemis `.env`. This is an observed
execution blocker; no Artemis journey has run as part of this preparation. Safe checks also
found no local ADC file or configured Vertex project variables. No credential value was read.

This Artemis pin accepts native provider API credentials through its canonical environment
or dotenv configuration. It also supports Vertex AI through authorized ADC and a project.
Its source contract is `artemis/config/settings.py`, `paths.py` and `llm.py`.
Every selected model must have a compatible configured provider. A Google Drive connection,
Android Google consent or a DDS subscription CLI login does not supply that contract.

Credential setup belongs in a secure owner-controlled terminal using the supported Artemis
configuration flow with restrictive file permissions, outside GOAT requests and commits.
If its interactive initializer offers upstream MCP installation, select **Skip for now**;
keep the governed DIAL integration. The VAN proxy intentionally refuses raw credential-admin
writes/tests, and this helper provides no substitute. After authorized setup, rerun the
canonical diagnostic and retain its sanitized readiness result before starting a task.

## Bind the request to real APK bytes

Use the [canonical Google test build helper](../../scripts/testing/prepare-google-test-build.py)
against an expected clean, attached commit. Keep its retained APK/provenance together.
The submitted APK must also be inside the admitted checkout; its normal Gradle output is
acceptable only when the bytes match that provenance. Uninstall and clear-data operations
are not part of this integration.

After substituting actual paths and candidate:

~~~sh
python3 scripts/testing/artemis_goat.py prepare \
  --expected-commit "$CANDIDATE" \
  --apk app/build/outputs/apk/debug/app-debug.apk \
  --build-provenance "$BUILD_OUTPUT/test-build-provenance.json" \
  --sdk "$SDK" --build-tools 37.0.0 \
  --expected-cert-sha1 AB:16:7A:BC:C2:35:59:CE:06:95:8A:6B:0A:81:BC:36:25:FF:AD:E3 \
  --scenario local-entry-inspection --device-serial emulator-5560 \
  --output "$EVIDENCE_OUTPUT/artemis-entry-request.json"
~~~

The helper checks origin, clean commit/tree, actual APK digest/size, and SDK-verified
package/signature against retained build provenance. It runs only read-only Git/public SDK
inspection commands, not Artemis or installation. Send the output object's **request**
member to the initialized MCP; the surrounding provenance is not a tool argument.

The manifest currently has four scenarios and **22 required assertions**:

| Scenario | Preconditions and bounded outcome |
| --- | --- |
| `local-entry-inspection` | First-run intro with no existing farm; inspect empty setup without submitting credentials. |
| `management-home-navigation` | Operator-opened synthetic management session; rendered navigation and complete labels at the actual device/font configuration. |
| `goat-register-weight-local` | Same synthetic management session with Goats enabled; one unique test tag, one 12.5 kg weight, exact search/profile and module readback. |
| `task-create-complete-local` | Same synthetic management session; one unique unassigned one-off task, completion/evidence and module readback. |

The last three require an operator-opened **GOAT Artemis Qualification** farm as its
active local owner/manager. The write journeys authorize only the named synthetic records,
with a fresh nonsecret marker retained in the report. They do not sign in, store a PIN,
change authority, replace existing records or clean up data. Missing preconditions mean
BLOCKED; do not alter or delete existing data to manufacture a passing run.

The registration/weight steps follow
[`GoatCaptureScreens.kt`](../../feature/goat/src/main/kotlin/com/farmos/feature/goat/GoatCaptureScreens.kt)
and [`GoatExperienceScreen.kt`](../../feature/goat/src/main/kotlin/com/farmos/feature/goat/GoatExperienceScreen.kt),
with [`GoatModuleHost.kt`](../../app/src/main/java/com/farmos/app/GoatModuleHost.kt)
owning the committed write. Task steps follow
[`TasksBoardScreen.kt`](../../feature/ops/src/main/kotlin/com/farmos/feature/ops/TasksBoardScreen.kt),
[`TaskModuleNavigation.kt`](../../app/src/main/java/com/farmos/app/TaskModuleNavigation.kt)
and [`TaskWorkspaceScreens.kt`](../../app/src/main/java/com/farmos/app/TaskWorkspaceScreens.kt).
The task journey uses the board's inline Add task form; its screen list does not claim the
separate `FOS-TASK-004` entry route. Screen IDs in this manifest describe intended surfaces,
not accepted route evidence.

For write journeys, form dismissal is insufficient: inspect the actual record and any
write/refresh error before continuing. A saved-but-refresh-failed message must not trigger
a duplicate submission. Reopening a module exercises visible local readback; the scenarios
do not claim process restart, network isolation, another device's state or cloud delivery.
They have not been executed merely by adding the manifest.

Poll at least once per minute, or consume the installed supervisor's 60-second polling.
Inspect strict Pro notes and actual actions/screenshots. Started is not completed.

## Check the canonical terminal receipt

Task records are under `/var/lib/dial-control/android-testing/tasks/<trace_id>.json`.
Seals are under `android-testing/evidence/goat-app/trace-<trace_id>/summary.json` in the
same control home. The async summary hashes final screenshot/logcat and copied outcome,
plan and output notes. The SPMRF candidate reference is on the **task's evidence object**,
not the asynchronous summary; a candidate is not accepted Project Truth.

~~~sh
python3 scripts/testing/artemis_goat.py verify \
  --prepared-request "$EVIDENCE_OUTPUT/artemis-entry-request.json" \
  --trace-id "$TRACE_ID" \
  --output "$EVIDENCE_OUTPUT/artemis-entry-validation.json"
~~~

The validator checks project/commit/branch/device/package/APK/objective, terminal lifecycle,
contained regular paths, every listed size/hash, required artifacts, and agreement between
Hermes `test_summary` and `run_outcome.json`. Unsealed, missing or changed evidence is refused,
as are scenario IDs missing from the retained plan/report.

Artemis explicitly permits `task_status=completed` with failed assertions. Exit 0 therefore
means **TRACE_CHECKS_PASSED_REVIEW_REQUIRED**: completed, enough passed checks, and no failed,
inconclusive, unchecked or retired requirements. A valid receipt with unmet requirements
exits 1; invalid/incomplete evidence exits 2.

Counts and ID mentions alone do not establish per-requirement visual correctness. Review
each assertion against trace steps. The helper does not independently authenticate copied
files outside the protected Hermes store, accept references, promote registries or issue
feature/module/MVP/visual/physical-device approval.

## Deterministic tests and remaining gates

The canonical connected harness is
[`scripts/ci/run-two-device-e2e.sh`](../../scripts/ci/run-two-device-e2e.sh). It runs core
database and app local-first instrumentation, excluding superseded live-Supabase
`AuthRefreshAndReauthTest`. Its separate Room replicas/loopback on an Android guest are
not proof of two physical phones.

`android_test_run` can run one Gradle task before the Artemis exercise. It does not run
that two-command shell harness, supply its legacy exclusion or retain all JUnit XML.
Retain the complete canonical suite and fresh JUnit results separately against the same
source. Ensure only the intended target is eligible for any connected Gradle execution:
the wrapper's serial controls its own ADB/Artemis calls, not arbitrary Gradle selection.
Never broaden device authority or bypass the governed MCP to get a test result.

Use accelerated Foundation CI for the large suite when the measured software-emulator
resource/time budget is insufficient. Record the real environment. Actual multi-device
transfer, live Drive, all-device-loss recovery, field workflows, clinical safety,
performance, accessibility/native references and owner/independent acceptance retain their
contracts. A reusable target and four bounded journeys do not close those gates.

Run isolated helper contract tests with:

~~~sh
python3 scripts/testing/artemis_goat_test.py
~~~
