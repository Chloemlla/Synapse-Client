# Android Mobile Login Guidelines

## Scenario: Synapse Android Mobile Login Client

### 1. Scope / Trigger

Trigger: changes under `android/` that touch login, mobile QR confirmation, client login token storage, Gradle dependencies, or `.github/workflows/synapse-android.yml`.

The Android client is a standalone Kotlin project in `android/`. Keep it separate from the existing repository root tooling.

### 2. Signatures

Android project files:

```text
android/settings.gradle.kts
android/build.gradle.kts
android/app/build.gradle.kts
android/app/src/main/AndroidManifest.xml
android/app/src/main/java/com/synapse/mobile/**
```

Core Kotlin APIs:

```kotlin
SynapseMobileLoginApi.standardLogin(identifier: String, password: String): StandardLoginResult
SynapseMobileLoginApi.issueClientToken(jwt: String, deviceId: String, deviceName: String): ClientTokenIssueResult
SynapseMobileLoginApi.exchangeClientToken(clientLoginToken: String, deviceId: String): JwtExchangeResult
SynapseMobileLoginApi.verifyTotp(userId: String, pendingToken: String, token: String?, backupCode: String?): TotpVerificationResult
SynapseMobileLoginApi.startPasskeyAuthentication(username: String, clientOrigin: String): PasskeyAuthenticationStartResult
SynapseMobileLoginApi.finishPasskeyAuthentication(username: String, response: JSONObject, clientOrigin: String): PasskeyAuthenticationFinishResult
SynapseMobileLoginApi.markScanned(payload: SynapseQrPayload): MobileLoginStatus
SynapseMobileLoginApi.confirmWithJwt(payload: SynapseQrPayload, jwt: String): MobileLoginStatus
SynapseMobileLoginApi.confirmWithClientToken(payload: SynapseQrPayload, clientLoginToken: String, deviceId: String): MobileLoginStatus
SynapseMobileLoginApi.revokeClientToken(jwt: String, clientLoginToken: String): Boolean
SynapseQrPayload.parse(raw: String): SynapseQrPayload
```

### 3. Contracts

Backend contract source of truth: `docs/android-mobile-login-integration.md`.

Required API paths:

```text
POST /api/auth/login
POST /api/totp/verify-token
POST /api/auth/mobile-login/challenge
POST /api/auth/mobile-login/challenge/scan
POST /api/auth/mobile-login/challenge/confirm
POST /api/auth/mobile-login/challenge/poll
POST /api/auth/mobile-login/client-token/issue
POST /api/auth/mobile-login/client-token/exchange
POST /api/auth/mobile-login/client-token/revoke
POST /api/passkey/authenticate/start
POST /api/passkey/authenticate/finish
```

Standard login `requires2FA` response contract:

Standard login request uses `identifier`, not `username`:

```json
{ "identifier": "alice-or-email@example.com", "password": "plain password" }
```

```json
{
  "user": { "id": "string", "username": "string", "email": "string", "role": "user" },
  "token": "short-lived-2fa-token",
  "requires2FA": true,
  "twoFactorType": ["TOTP", "Passkey"]
}
```

The Android client must treat this `token` as a short-lived 2FA token, not a normal JWT, and must not issue a client login token until a TOTP or Passkey finish flow returns a formal JWT.

TOTP finish request/response contract:

```json
{ "userId": "user-id", "pendingToken": "short-lived-2fa-token", "token": "123456" }
```

or:

```json
{ "userId": "user-id", "pendingToken": "short-lived-2fa-token", "backupCode": "recovery-code" }
```

```json
{ "verified": true, "token": "jwt", "message": "验证成功" }
```

Passkey start request/response contract:

```json
{ "username": "alice", "clientOrigin": "https://tts.chloemlla.com" }
```

```json
{
  "options": {
    "challenge": "string",
    "rpId": "string",
    "allowCredentials": [{ "id": "string", "transports": ["internal"] }],
    "userVerification": "required"
  }
}
```

Passkey finish returns:

Passkey finish request wraps the WebAuthn assertion under `response`:

```json
{ "username": "alice", "response": { "id": "credential-id", "type": "public-key", "response": {} }, "clientOrigin": "https://tts.chloemlla.com" }
```

```json
{
  "success": true,
  "token": "jwt",
  "user": { "id": "string", "username": "string", "email": "string" }
}
```

QR payload contract:

```text
synapse://mobile-login?sessionId=<sessionId>&scanToken=<scanToken>&apiBaseUrl=<https origin>&expiresAt=<ISO8601>
```

Environment keys consumed by Android CI/Gradle:

```text
SYNAPSE_ANDROID_VERSION_NAME
SYNAPSE_ANDROID_VERSION_CODE
SYNAPSE_API_BASE_URL
SYNAPSE_CERTIFICATE_PINS
SYNAPSE_REQUIRE_CERTIFICATE_PINS
```

GitHub Release environment keys derived by `.github/workflows/synapse-android.yml`:

```text
PROJECT_LUMEN_VERSION_NAME
PROJECT_LUMEN_SHORT_HASH
```

Credential contract:

- JWT and `clientLoginToken` live only in encrypted private app storage.
- `deviceId` is stable for the install and may reset when app data is cleared.
- UI may show whether a token exists, never the token value.

### 4. Validation & Error Matrix

| Condition | Required handling |
|-----------|-------------------|
| QR scheme/host is not `synapse://mobile-login` | Reject before network request |
| QR `apiBaseUrl` is not HTTPS | Reject before network request |
| QR is expired | Reject scan/confirm action |
| Standard login returns `requires2FA` | Preserve `user`, short-lived token, and `twoFactorType`; do not issue client token until a JWT is available |
| TOTP verify returns a JWT | Save the JWT encrypted, then issue and save the client login token |
| Passkey start returns WebAuthn `options` | Keep the raw options for the native/browser assertion flow, but show only a summary; do not display `challenge` or credential IDs in full |
| Passkey finish returns a JWT | Save the JWT encrypted, then issue and save the client login token |
| Client token exchange returns 401 | Clear local credentials and require login |
| JWT confirm returns 401 and client token exists | Clear local JWT and retry confirm with client token |
| Revoke succeeds with `revoked=true` | Clear local credentials |
| Missing certificate pins while pins are required | Fail OkHttp client creation |
| API returns validation details in `details`, `errors`, or `issues` | Show the backend message plus field-level reasons, HTTP status, method, URL, and request field names; never echo request values such as passwords, JWTs, `clientLoginToken`, or `scanToken` |

### 5. Good/Base/Bad Cases

Good: scanner receives a valid QR payload, `markScanned` posts `sessionId` and `scanToken`, the confirmation screen shows target site, and `confirmQrLogin` uses JWT or `clientLoginToken`.

Base: user has a `clientLoginToken` but no JWT; app exchanges it at startup and stores the returned JWT/user.

Two-factor: standard login returns `requires2FA` with `twoFactorType: ["TOTP", "Passkey"]`; app shows the available methods and a short token preview, then requests Passkey options only when the user chooses Passkey.

Bad: app logs a full JWT or client login token, accepts HTTP `apiBaseUrl`, or confirms a QR login without showing the target site.

Error display: if an API response says only `输入验证失败` in the top-level message but includes nested field errors, the app must surface those field errors to the user. The diagnostic request context may include `POST https://.../api/...`, HTTP status, and submitted field names only; it must not include submitted values.

### 6. Tests Required

- Unit test `SynapseQrPayload.parse` for valid payload, wrong scheme/host, missing fields, and non-HTTPS `apiBaseUrl`.
- Unit test `CertificatePinPolicy.parse` for whitespace/comma separated pins and invalid entries.
- Unit test API error formatting with nested validation details and a negative assertion that request values are not echoed.
- Unit test standard login JSON mapping for `requires2FA`, short-lived token fallback from `token`, `twoFactorType`, and `user`.
- Unit test TOTP finish JSON mapping for `verified`, JWT `token`, and `message`.
- Unit test Passkey start/finish JSON mapping, including `allowCredentials` count and finish responses whose `user` omits `role`.
- Do not add `androidTestImplementation` dependencies until real instrumentation tests exist. Unused AndroidX Test/Espresso dependencies still participate in `generateDebugAndroidTestLintModel` and can conflict with dependency lock constraints.
- CameraX `ImageProxy.image` usage must be explicitly marked with AndroidX annotation opt-in, for example `@androidx.annotation.OptIn(markerClass = [ExperimentalGetImage::class])`; Kotlin's standard `@OptIn(ExperimentalGetImage::class)` does not satisfy AndroidX lint. Do not hide this lint error with a baseline.
- CI must run `gradle testDebugUnitTest`, `gradle lintDebug`, and `gradle assembleRelease` from `android/`.
- CI must upload verification reports and release APK artifact.
- CI must copy release APKs into root `release-assets/` and publish a non-draft, latest GitHub Release with `softprops/action-gh-release@v2` on successful non-PR runs.

Local Gradle commands remain prohibited by repository policy; do not run them from the agent session.

### 7. Wrong vs Correct

#### Wrong

```kotlin
Text(credentials.clientLoginToken.orEmpty())
Log.d("Synapse", "jwt=$jwt")
JSONObject().put("username", username).put("password", password)
credentialStore.saveJwt(login.token!!)
SynapseQrPayload.parse("synapse://mobile-login?apiBaseUrl=http://example.com")
```

#### Correct

```kotlin
Text(if (credentials.hasClientLoginToken) "已保存" else "未保存")
JSONObject().put("identifier", identifier).put("password", password)
if (login.requiresTwoFactor) return PendingTwoFactorChallenge(login.user, login.twoFactorToken, login.twoFactorTypes)
require(apiBaseUrl.startsWith("https://"))
credentialStore.saveClientLoginToken(clientLoginToken)
```
