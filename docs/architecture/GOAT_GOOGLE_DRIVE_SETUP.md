# Google Drive gateway: Android setup and acceptance

## Implemented route

The optional Drive gateway uses Google Play services `AuthorizationClient` with the `drive.file` scope. The owner starts consent from onboarding or Settings. This is a transport permission: the local GOAT account, farm membership, roles and device keys remain authoritative.

Both entry points use the same checked connection boundary. After consent, GOAT verifies the selected Google account and the actual writable Drive folder. A blank folder ID creates or reuses a folder carrying this farm's application metadata. An existing ID must identify a folder already accessible to this app under `drive.file`; typing an arbitrary folder ID grants no access.

The carrier uses AES-256-GCM through the existing `FarmCipher` for journal bundles and attachment bytes, bound to the exact object path and farm. Downloaded content and operation checksums are verified before a Drive vector advances. Corrupt, moved, foreign-farm, unknown-key or conflicting objects stop that session and retain local data. Backup watermarks are scoped to the Google account and folder and are updated from authenticated observations, not filenames or editable Drive metadata.

Google Play services owns its credential cache and token renewal. GOAT stores no bearer token, refresh token or client secret in its database, preferences, files, logs or journal. Farm keys remain in the existing Keystore-sealed vault. A device missing a rotated farm key must obtain the authorised key update over the farm's existing pairing/LAN path before opening new Drive payloads.

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

## Verification and limits

`DriveTransportSecurityTest` exercises encrypted round trips, tampering, path binding, key rotation, idempotent retries, sequence gaps, corrupt objects, destination isolation and bounded reads against an in-memory carrier. `DriveSetupAttemptTest` covers refused credentials, destination validation, creation order and connection failures. The Room migration suite covers the full migration chain and a populated version-29 local farm.

These tests do not grant a live Google consent session or prove physical-device/Drive service acceptance. Record that evidence separately for the configured package and signing certificate. The gateway runs while a local farm session is open; background sync after the app is closed remains a separate delivery item.

Drive object names are not unique. The REST carrier detects duplicate objects for one immutable journal path and reports a conflict instead of selecting arbitrary content or overwriting history. Existing ciphertext is authenticated and compared as plaintext on retry, so random encryption nonces do not cause false rewrite conflicts.
