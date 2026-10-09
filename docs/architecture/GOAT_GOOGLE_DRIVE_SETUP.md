# Google Drive gateway: Android setup and acceptance

## Implemented route

The optional Drive gateway uses Google Play services `AuthorizationClient` with the `drive.file` scope. The owner starts consent from onboarding or Settings. This is a transport permission: the local GOAT account, farm membership, roles and device keys remain authoritative.

Both entry points use the same checked connection boundary. After consent, GOAT verifies the selected Google account and the actual writable Drive folder. A blank folder ID creates or reuses a folder carrying this farm's application metadata. An existing ID must identify a folder already accessible to this app under `drive.file`; typing an arbitrary folder ID grants no access.

Ordinary journal bundles and attachment bytes use AES-256-GCM through `FarmCipher`, bound to the exact object path and farm. New ordinary payloads use the current farm key. Downloaded content and operation checksums are verified before a Drive vector advances. Unverifiable required journal content stops the pass and retains local data. Backup watermarks are scoped to the Google account and folder and advance only from authenticated observations, not filenames or editable Drive metadata. The separate, tightly scoped key bootstrap described below lets an authorised device obtain missing rotation prerequisites without exposing later business records under an old key.

Google Play services owns its credential cache and token renewal. GOAT stores no bearer token, refresh token or client secret in its database, preferences, files, logs or journal. Farm keys and the device signing identity remain in the Keystore-sealed vault. Possession of a Drive folder ID, Google consent or an old shared farm key does not establish a new farm device identity.

## Key delivery and recovery

A local rotation first checks the current farm account and device authority. It stages the new key as **inactive** in the sealed vault, commits the exact `farm.key_rotated.v1` Room receipt, then reconciles that committed receipt before activation. The old current key and historical keys remain available. A restart or failure between these steps can be reconciled from the sealed pending metadata and the exact committed operation; an uncommitted candidate is never activated. An unresolved receipt/vault mismatch pauses delivery as `KEY_ROTATION_PENDING`. Missing keys produce `KEYS_UNAVAILABLE`; a foreground pass or worker never provisions a replacement key.

The supplied rotation business time must be strictly later than the current rotation time already observed on that device. A clock that has not advanced is refused before staging, with a request to correct the clock and retry; the code does not invent a later event time. Received rotations preserve their original business time and choose the current key deterministically by business time, then key ID when times tie.

An approved gateway publishes a signed singleton copy of each exact accepted rotation and its device-enrolment prerequisites under `GOAT/farms/<farm-id>/key-bootstrap/<gateway-device-id>/<wrapping-key-id>/<exact-bundle-sha256>.rotation`. These copies are encrypted with applicable retained farm keys; a rotation is not bootstrapped under the new key it is introducing. They contain only `farm.key_rotated.v1` or `farm.device_enrolled.v1` prerequisites. Rotated key material is still individually wrapped to eligible recipient device identities. Ordinary journal bundles and attachments are never re-encrypted under old keys for this recovery route.

The gateway's device identity signs the farm, signer ID, full immutable object path and exact encoded bundle. The receiver validates the farm, entity target, singleton command type, bundle and operation checksums, path digest and signature. Before a fresh prerequisite enters the Room journal, the signer must be a known farm device with its registered public key, an `ACTIVE` or `TEMPORARILY_OFFLINE` status and no revocation cutoff. That check runs inside the journal transaction. A signed enrolment from an already trusted gateway can establish the next signer's identity before its rotation is admitted; shared-key ciphertext alone cannot do so.

Bootstrap import retries dependencies in passes, installs an authorised wrapped key through the normal applier and then opens the ordinary current-key journal. It does not fill missing origin sequence numbers or make a singleton prerequisite count as a complete backup. Unreadable redundant copies under keys the receiver does not hold may be ignored; missing required journal or signer dependencies remain visible failures.

An exact already accepted rotation or an already installed matching device identity remains replay compatible. A historical archive with previously unadmitted, unsigned enrolment or rotation records needs an updated authorised gateway that can attest its accepted history, or an authenticated LAN relay that supplies the prerequisites. The app does not relax signature checks to rescue an untrusted archive. If all authorised devices and their recoverable farm keys are lost, the missing Owner escrow/recovery route is still **PLANNED**; the owner's Google account alone cannot decrypt the farm.

Implementation: [rotation staging and reconciliation](../../app/src/main/java/com/farmos/app/FarmKeyRotation.kt), [gateway prerequisite delivery](../../app/src/main/java/com/farmos/app/DriveKeyBootstrap.kt), [signed bootstrap format](../../app/src/main/java/com/farmos/app/DriveRotationBootstrapCodec.kt), and [the shared Drive runtime](../../app/src/main/java/com/farmos/app/FarmDriveRuntime.kt).

## Required release configuration

Before a physical-device Drive acceptance run, configure the Google Cloud project used by the app:

1. Enable the Google Drive API and complete the app's OAuth consent/branding configuration.
2. Register an Android OAuth client for application ID `com.farmos.app` and the SHA-1 fingerprint of the certificate that signs the installed build. Register debug and release distributions separately when their signing certificates differ.
3. During an OAuth testing rollout, include the intended test Google accounts. Install a build with supported Google Play services.
4. Use the existing local Owner/Management account to choose the Google account and connect. Consent is handled by Google's system UI.

No application server, web-client secret or server-side refresh-token exchange is used. Missing project/signature configuration, permission refusal, unavailable Play services or network failure must leave the connection unsuccessful and ordinary local farm work available.

Primary implementation references, checked 9 October 2026:

- [Android authorization guide](https://developer.android.com/identity/authorization)
- [AuthorizationClient reference](https://developers.google.com/android/reference/com/google/android/gms/auth/api/identity/AuthorizationClient)
- [AuthorizationRequest.Builder reference](https://developers.google.com/android/reference/com/google/android/gms/auth/api/identity/AuthorizationRequest.Builder)
- [Drive about.get account information and scopes](https://developers.google.com/workspace/drive/api/reference/rest/v3/about/get)

## Preparing the installed test identity

Use [`scripts/testing/prepare-google-test-build.py`](../../scripts/testing/prepare-google-test-build.py) after the candidate source is committed and frozen. It invokes the existing `:app:assembleDebug` target, requires the retained external debug keystore, and refuses a missing key, a different pinned certificate, dirty sources, or server-era provider/test inputs. It never generates or rotates a signing key.

```sh
python3 scripts/testing/prepare-google-test-build.py \
  --expected-commit FULL_CANDIDATE_COMMIT_SHA \
  --expected-sha1 PINNED_PUBLIC_CERTIFICATE_SHA1 \
  --sdk /absolute/path/to/android-sdk \
  --java-home /absolute/path/to/jdk-21 \
  --output-dir /absolute/path/outside/the/checkout/new-test-build
```

The build output directory contains the retained test APK, public signing certificate, signature and manifest inspections, build log, and `test-build-provenance.json`. Signature, package, version and digest verification operate on the retained APK. The provenance records the exact repository commit/tree/ref, build target, toolchain and canonical-state revisions. It grants no release or visual qualification and records live Google and device acceptance as unexecuted by the command.

Register the public SHA-1 for that actual APK in the Android OAuth client. A separately built CI APK can have a different debug signing certificate and is not interchangeable with this installation. Keep the retained private key outside Git and protect it from other users; never resolve a signing mismatch by automatically uninstalling the app and deleting local farm data. Debug signing is for the owner-authorized testing task; production distribution requires its own qualified signing and release process.

Primary signing references:

- [Android app signing and API-provider certificates](https://developer.android.com/studio/publish/app-signing)
- [Android SDK APK signature verification](https://developer.android.com/tools/apksigner)

## Background delivery and local approval

The connection records the local account that approved it and the exact local device. Every foreground or background pass rechecks the approver's active status, the storage/backup capability, the active local device with no revocation cutoff, and the same account/folder connection binding. A legacy configuration with no such approval requires an explicit checked reconnection. Restoring an account role alone does not silently reinstate a connection after approval was rejected.

`ApprovedDriveStore` repeats the approval check before every carrier operation. The actual REST write path also checks it after token acquisition and the immutable-object metadata lookup, immediately before opening the upload POST. A revocation discovered during that lookup therefore prevents the POST. Bootstrap, journal and attachment writes all use this same checked carrier.

WorkManager retains one periodic network-constrained request per farm with a 15-minute minimum interval, plus one unique immediate request after connection or a committed action. The scheduling coroutine awaits both returned `Operation` futures, so a later asynchronous enqueue failure is reported as a scheduling failure. Accepting these requests does not mean a transfer has completed. Delivery remains subject to Android scheduling and network availability. The foreground timer and background worker share one farm coordinator, encrypted carrier and verified-cursor rules.

A worker may renew already granted access through Play services, but it never launches consent. Required consent or lost approval stops scheduled delivery until an explicit reconnect. Offline failures and transient errors retain local changes and retry state. Cancellation or thread interruption after `RUNNING` clears the current pass's syncing flag, persists `RETRY_WAIT` for the same connection and propagates the interruption. On restart, a persisted `RUNNING` state also loads as `RETRY_WAIT`. Cleanup cannot overwrite a newly approved connection. Missing keys and unresolved local rotation staging retain their distinct recovery states rather than creating a key.

Implementation: [approval checks](../../app/src/main/java/com/farmos/app/DriveGatewayApproval.kt), [REST writes](../../app/src/main/java/com/farmos/app/DriveRestStore.kt), [durable scheduling](../../app/src/main/java/com/farmos/app/DriveBackgroundWork.kt), [the worker](../../app/src/main/java/com/farmos/app/DriveBackgroundWorker.kt), and [persisted delivery state](../../app/src/main/java/com/farmos/app/DriveDeliveryState.kt).

## Verification and limits

The repository contains the following bounded automated regression coverage. Test names describe what must be verified; they do not record a new passing run for the final candidate.

| Boundary | Regression source |
|---|---|
| Encrypted round trips, tampering, path binding, retries, sequence gaps, destination isolation and bounded reads | [`DriveTransportSecurityTest`](../../app/src/test/java/com/farmos/app/DriveTransportSecurityTest.kt) |
| Refused credentials, checked destinations, creation order and connection failures | [`DriveSetupAttemptTest`](../../app/src/test/java/com/farmos/app/DriveSetupAttemptTest.kt) |
| Multiple rotations, independently rotated peers, signed enrolment dependencies, and truthful sequence watermarks | [`DriveKeyRotationDeliveryTest`](../../app/src/test/java/com/farmos/app/DriveKeyRotationDeliveryTest.kt) |
| Unknown, revoked, cutoff, spoofed and tampered signers; unsigned identity injection; signature and path binding | [`DriveKeyBootstrapSecurityTest`](../../app/src/test/java/com/farmos/app/DriveKeyBootstrapSecurityTest.kt) |
| Interrupted rotation recovery and refusal of a local clock that has not advanced | [`FarmKeyRotationRecoveryTest`](../../app/src/test/java/com/farmos/app/FarmKeyRotationRecoveryTest.kt), [`FarmKeyRotationOrderingTest`](../../app/src/test/java/com/farmos/app/FarmKeyRotationOrderingTest.kt) |
| Revocation during the real REST metadata lookup prevents its POST | [`DriveRestApprovalTest`](../../app/src/test/java/com/farmos/app/DriveRestApprovalTest.kt) |
| Both asynchronous enqueue failures reach the caller; cancellation after `RUNNING` updates cached and durable state | [`DriveBackgroundSchedulingTest`](../../app/src/test/java/com/farmos/app/DriveBackgroundSchedulingTest.kt), [`DriveBackgroundWorkerTest`](../../app/src/test/java/com/farmos/app/DriveBackgroundWorkerTest.kt) |

Production compilation of these changes is not a completed unit or device acceptance run. Record passing current-revision test results separately. These tests use controlled carriers, Room fixtures or worker seams; they do not grant a live Google consent session or establish Google Drive service acceptance. A live handset process/reboot/Google delivery run, the required physical-device multi-device proof, Owner recovery evidence and independent visual approval remain separate qualification gates under [Project Truth](../00_PROJECT_TRUTH.md). Configuring a test Google account or preparing a signed debug APK closes none of those gates by itself.

Drive object names are not unique. The REST carrier detects duplicate objects for one immutable journal path and reports a conflict instead of selecting arbitrary content or overwriting history. Existing ciphertext is authenticated and compared as plaintext on retry, so random encryption nonces and valid signature variation do not cause false rewrite conflicts.
