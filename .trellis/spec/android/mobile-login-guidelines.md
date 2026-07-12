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
SynapseMobileLoginApi.getTurnstilePublicConfig(): TurnstilePublicConfig
SynapseMobileLoginApi.standardLogin(identifier: String, password: String, cfToken: String? = null): StandardLoginResult
SynapseMobileLoginApi.currentUser(jwt: String): SynapseUser
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
GET /api/turnstile/public-config
POST /api/auth/login
GET /api/auth/me
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

Turnstile public config contract:

```json
{ "enabled": true, "siteKey": "0x4AAAA...", "hcaptchaEnabled": false, "hcaptchaSiteKey": null }
```

When `enabled=true` and `siteKey` is present, the Android login UI must load a Turnstile widget before standard login. The widget token is one-time proof for `/api/auth/login`; never store, display, log, or reuse it after a failed login.

Standard login `requires2FA` response contract:

Standard login request uses `identifier`, not `username`. If Turnstile is enabled, include the widget token as `cfToken`, matching Happy-TTS web `LoginPage` -> `useAuth.login`:

```json
{ "identifier": "alice-or-email@example.com", "password": "plain password", "cfToken": "turnstile-widget-token" }
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
```

GitHub Release environment keys derived by `.github/workflows/synapse-android.yml`:

```text
PROJECT_LUMEN_VERSION_NAME
PROJECT_LUMEN_SHORT_HASH
```

Credential contract:

- JWT and `clientLoginToken` live only in encrypted private app storage.
- `deviceId` is stable for the install and may reset when app data is cleared.
- The credential store supports multiple accounts at the same time and tracks one active account for QR confirmation, silent login, revoke, and clear-current-account actions.
- A legacy single-account credential record must be migrated into the multi-account list on first load.
- UI may show whether a JWT exists, never the JWT value.
- Manual JWT authorization must call `GET /api/auth/me` first, bind the JWT to the returned real user, then issue and persist that user's `sml_` client login token.
- Store the `expiresAt` returned by `/client-token/issue` with the corresponding `sml_` token.
- UI may show and copy the active account `sml_` client login token and its expiration time when explicitly presenting local authorization details; do not log it or include it in API error diagnostics.

Networking contract:

- The Android client is HTTPS-only and must reject non-HTTPS `SYNAPSE_API_BASE_URL` and QR `apiBaseUrl` values before network requests.
- Do not add client-side certificate pinning, `CertificatePinner`, `CertificatePinPolicy`, `SYNAPSE_CERTIFICATE_PINS`, or `SYNAPSE_REQUIRE_CERTIFICATE_PINS` without a new explicit product/security decision and spec update.
- GitHub Actions may supply `SYNAPSE_API_BASE_URL`; it must not require certificate pin secrets for verification or signed releases.

### 4. Validation & Error Matrix

| Condition | Required handling |
|-----------|-------------------|
| QR scheme/host is not `synapse://mobile-login` | Reject before network request |
| QR `apiBaseUrl` is not HTTPS | Reject before network request |
| Default `SYNAPSE_API_BASE_URL` is not HTTPS | Fail repository/client creation before network request |
| QR is expired | Reject scan/confirm action |
| App receives a `synapse://mobile-login` intent | Parse the payload, select the Web login tab, and mark scanned when valid |
| Turnstile public config returns `enabled=true` with `siteKey` | Load the Turnstile widget before standard login and disable/guard login until a widget token is received |
| Standard login fails after a Turnstile token was submitted | Clear the local Turnstile token and reload the widget before another login attempt |
| Turnstile config fetch fails | Surface a retry path; do not fabricate a token or persist a stale token |
| Standard login returns `requires2FA` | Preserve `user`, short-lived token, and `twoFactorType`; do not issue client token until a JWT is available |
| TOTP verify returns a JWT | Save the JWT encrypted, then issue and save the client login token |
| Passkey start returns WebAuthn `options` | Keep the raw options for the native/browser assertion flow, but show only a summary; do not display `challenge` or credential IDs in full |
| Passkey finish returns a JWT | Save the JWT encrypted, then issue and save the client login token |
| Google config returns enabled=true with clientId | Show SIWG entry; use Credential Manager with that serverClientId |
| Google bind-session returns JWT | Save JWT encrypted, issue client login token |
| Google bind-session returns requiresBinding=true | Fall back to POST /api/auth/google for mobile auto upsert |
| Google login disabled/unconfigured | Hide or disable SIWG entry; allow password/passkey login |
| Client token exchange returns 401 | Clear local credentials and require login |
| Stored `sml_` `expiresAt` is in the past | Clear that account's JWT and `sml_` token locally, keep the account metadata and expiration time, and require authorization login again |
| JWT confirm returns 401 and client token exists | Clear local JWT and retry confirm with client token |
| Revoke succeeds with `revoked=true` | Clear local credentials |
| Multiple accounts are stored | Display all accounts, allow switching the active account, and when confirming web login show an account picker dialog before `/challenge/confirm`; use only the selected/active account for QR confirmation |
| API returns validation details in `details`, `errors`, or `issues` | Show the backend message plus field-level reasons, HTTP status, method, URL, and request field names; never echo request values such as passwords, JWTs, `clientLoginToken`, or `scanToken` |

### 5. Good/Base/Bad Cases

Human verification: app starts, fetches `/api/turnstile/public-config`, renders Turnstile when enabled, blocks "登录本客户端并签发令牌" until the widget callback returns a token, sends that token as `cfToken` with `identifier` and `password`, then clears/reloads the widget token on login failure.

Good: scanner or system deep link receives a valid QR payload, the app opens the Web login tab, `markScanned` posts `sessionId` and `scanToken`, the confirmation screen shows target site, active account, device id, and `confirmQrLogin` uses the active account JWT or `clientLoginToken`.

Base: user has a `clientLoginToken` but no JWT; app exchanges it at startup and stores the returned JWT/user.

Multi-account: logging in as a second user appends or updates that account instead of overwriting the first account. The selected account is the only account used for silent login and revoke. QR confirmation with multiple stored accounts must first show an account picker dialog with account identity and credential availability; selecting an account makes it active and then continues `/challenge/confirm`.

Two-factor: standard login returns `requires2FA` with `twoFactorType: ["TOTP", "Passkey"]`; app shows the available methods and a short token preview, then requests Passkey options only when the user chooses Passkey. Passkey finish must use Android Credential Manager (`GetPublicKeyCredentialOption` + `PublicKeyCredential.authenticationResponseJson`) against Happy-TTS `/api/passkey/authenticate/*` routes; discoverable start/finish is allowed for passwordless login. Never show full challenge/credential ids or keep assertion JSON in UI state after finish.

Bad: app logs a full JWT or client login token, stores a Turnstile widget token, accepts HTTP `apiBaseUrl`, reintroduces client certificate pin secrets, overwrites an existing account when logging in as another user, or confirms a QR login without showing the target site.

Error display: if an API response says only `输入验证失败` in the top-level message but includes nested field errors, the app must surface those field errors to the user. The diagnostic request context may include `POST https://.../api/...`, HTTP status, and submitted field names only; it must not include submitted values.

### 6. Tests Required

- Unit test `SynapseQrPayload.parse` for valid payload, wrong scheme/host, missing fields, and non-HTTPS `apiBaseUrl`.
- Unit test `SynapseSecureOkHttpFactory.create` for rejecting non-HTTPS API origins while constructing a normal HTTPS OkHttp client.
- Static policy check must fail if Android source or workflow files reintroduce `CertificatePinner`, `CertificatePinPolicy`, `SYNAPSE_CERTIFICATE_PINS`, or `SYNAPSE_REQUIRE_CERTIFICATE_PINS`.
- Unit test API error formatting with nested validation details and a negative assertion that request values are not echoed.
- Unit test Turnstile public config JSON mapping for `enabled`, `siteKey`, and disabled/no-site-key responses.
- Unit test standard login JSON mapping for `requires2FA`, short-lived token fallback from `token`, `twoFactorType`, and `user`.
- Unit test TOTP finish JSON mapping for `verified`, JWT `token`, and `message`.
- Unit test Passkey start/finish JSON mapping, including `allowCredentials` count and finish responses whose `user` omits `role`.
- Unit test Passkey request JSON normalization for Credential Manager (challenge/rpId/allowCredentials) and discoverable empty allow list.
- Passkey UI must invoke Credential Manager; manual assertion JSON paste is fallback-only and must not be the primary path.
- ViewModel/UI test coverage should assert that multi-account QR confirmation opens an account picker, selecting an account switches the active account, and no JWT/`clientLoginToken`/`scanToken` value is rendered in the picker.
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
api.standardLogin(identifier, password)
repository.confirmQrLogin(rawPayload)
credentialStore.saveJwt(login.token!!)
SynapseQrPayload.parse("synapse://mobile-login?apiBaseUrl=http://example.com")
SynapseSecureOkHttpFactory.create(baseUrl, certificatePins = BuildConfig.SYNAPSE_CERTIFICATE_PINS)
```

#### Correct

```kotlin
Text(if (credentials.hasClientLoginToken) "已保存" else "未保存")
val turnstile = api.getTurnstilePublicConfig()
require(!turnstile.requiresVerification || cfToken.isNotBlank()) { "请先完成人机验证" }
JSONObject()
  .put("identifier", identifier)
  .put("password", password)
  .apply { cfToken?.takeIf { it.isNotBlank() }?.let { put("cfToken", it) } }
if (login.requiresTwoFactor) return PendingTwoFactorChallenge(login.user, login.twoFactorToken, login.twoFactorTypes)
if (credentials.accounts.size > 1) showWebLoginAccountPicker()
require(apiBaseUrl.startsWith("https://"))
SynapseSecureOkHttpFactory.create(baseUrl = SynapseApiOriginPolicy.normalizeHttpsOrigin(baseUrl))
credentialStore.saveClientLoginToken(clientLoginToken, expiresAt)
```

### Google Sign-In (Credential Manager SIWG)

- Load `GET /api/auth/google/config` for `enabled` + Web `clientId` (`serverClientId`).
- Use `CredentialManager` + `GetGoogleIdOption` / `GetSignInWithGoogleOption` to obtain a Google ID token.
- Exchange via Happy-TTS: prefer `POST /api/auth/google/bind-session`; if `requiresBinding=true`, fall back to `POST /api/auth/google` (mobile has no web bind UI).
- On JWT success: encrypt JWT, then `POST /api/auth/mobile-login/client-token/issue`.
- Never log or display full Google idToken, JWT, or SML token.
- Dependencies: `androidx.credentials:credentials`, `credentials-play-services-auth`, `com.google.android.libraries.identity.googleid:googleid`.

