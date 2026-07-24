# Synapse Client

Android Kotlin client for **Synapse mobile login** — password login, optional second-factor (TOTP / Passkey), encrypted on-device sessions, and QR / deep-link confirmation for web login.

API base (default): [https://tts.chloemlla.com](https://tts.chloemlla.com)

## What this project does

Synapse Mobile lets you authorize this phone as a trusted client, then use it to approve a browser login without typing your password on the web site.

| Flow | What happens |
|------|----------------|
| **Client login** | Sign in with identifier + password (and Turnstile / 2FA when required). The app issues and stores a long-lived `sml_` client login token. |
| **Silent login** | Exchange a stored client token for a fresh JWT without re-entering the password. |
| **Web QR login** | Scan or open a `synapse://mobile-login?...` challenge, mark it scanned, pick an account if needed, and confirm the browser session. |
| **Session management** | Switch accounts, revoke the client token, or clear encrypted local credentials. |

Sensitive values (JWT, `clientLoginToken`, scan tokens, passwords, Turnstile tokens) are never logged or shown in full in the UI.

## Repository layout

```text
android/                         # Kotlin app (Gradle Kotlin DSL, Jetpack Compose)
  app/src/main/java/com/synapse/mobile/
    core/auth/                   # API client, QR parsing, encrypted credential store
    core/crash/                  # Local crash report capture & share
    ui/                          # Compose screens & ViewModel
docs/
  android-mobile-login-integration.md   # Backend contract for mobile login
.github/workflows/
  synapse-android.yml            # Unit tests, lint, release APK, optional release publish
```

Product-facing Android notes also live in [`android/README.md`](android/README.md).

## Features

- Standard login via `POST /api/auth/login` (`identifier` + `password`, optional `cfToken`)
- Cloudflare Turnstile when public config requires human verification
- TOTP / backup code and Passkey follow-up when `requires2FA` is returned
- Client login token issue, silent JWT exchange, and revoke
- Multi-account local storage with active-account selection
- Camera QR scan + paste + deep link (`synapse://mobile-login`)
- HTTPS-only networking and encrypted private credential storage
- Material 3 Compose UI (client login / web login / session tabs)
- Theme-bound undraw empty-state illustrations (`DynamicColorImageVectors`; unDraw License)
- Crash report screen with sanitized copy/share

## Requirements

| Item | Value |
|------|--------|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Min SDK | 26 |
| Target SDK | 37 |
| JDK (CI) | 21 |
| Gradle (CI) | 9.5.1 |

## Getting started

### 1. Clone

```bash
git clone https://github.com/Chloemlla/Synapse-Client.git
cd Synapse-Client
```

### 2. Open the Android project

Open the **`android/`** directory in Android Studio (not the repository root as a Gradle root unless you intentionally restructure the monorepo layout).

### 3. Configure the API base URL

The app uses `BuildConfig.SYNAPSE_API_BASE_URL` (wired from Gradle / CI env `SYNAPSE_API_BASE_URL`). Point it at your Synapse / Happy-TTS compatible HTTPS API origin.

Only **HTTPS** API origins are accepted for QR `apiBaseUrl` and network clients.

### 4. Run on a device or emulator

Use Android Studio **Run**, or from `android/`:

```bash
gradle assembleDebug
```

Release signing and CI-oriented assemble are documented for GitHub Actions; local release signing helpers live in `setup-android-signing.ps1` (do not commit private keystores or base64 secrets).

## Using the app

1. **本客户端登录** — enter username/email and password, complete Turnstile if shown, finish 2FA if required. A client login token is issued for this device.
2. **网页登录** — on the website, open the mobile login QR; scan it here (or paste / open the deep link), review the target site, then confirm.
3. **本地会话** — silent login, switch accounts, revoke the client token, or clear local credentials.

Deep links use:

```text
synapse://mobile-login?...
```

## Backend contract

Full request/response shapes, field names, and error expectations are in:

- [`docs/android-mobile-login-integration.md`](docs/android-mobile-login-integration.md)

High-level endpoints the app depends on:

| Capability | Endpoint (examples) |
|------------|---------------------|
| Turnstile public config | `GET /api/turnstile/public-config` |
| Standard login | `POST /api/auth/login` |
| TOTP finish | `POST /api/totp/verify-token` |
| Passkey start/finish | `POST /api/passkey/authenticate/start`, `.../finish` |
| Client token issue / exchange / revoke | under `/api/auth/mobile-login/client-token/...` |
| QR scan / confirm | under `/api/auth/mobile-login/challenge/...` |

## Continuous integration

Workflow: [`.github/workflows/synapse-android.yml`](.github/workflows/synapse-android.yml)

On changes under `android/` (and related paths), GitHub Actions typically:

1. Runs repository policy checks
2. Runs unit tests (`testDebugUnitTest`)
3. Runs Android lint (`lintDebug`)
4. Assembles a release APK (`assembleRelease`)
5. Uploads reports / APK artifacts (and can publish a GitHub Release on configured non-PR runs)

Repository policy: prefer CI for full Gradle verification when agent or constrained environments prohibit local build/install.

## Development notes

- App sources stay under `android/`; do not move Gradle roots to the repo root unless you redesign the layout on purpose.
- Prefer encrypted storage for credentials; never log JWT, `clientLoginToken`, scan tokens, passwords, or Turnstile tokens.
- Unit tests cover pure helpers (QR parsing, origin policy, JSON mapping, token expiry display, etc.) under `android/app/src/test/`.
- AI / Trellis workflow tooling lives under `.trellis/` and `.agents/` and is not part of the runtime app.

## Security

- Network: HTTPS only; cleartext traffic disabled in the app manifest / network security config.
- Storage: client credentials use encrypted app-private storage (not plain SharedPreferences for secrets).
- UI: secrets are shown as previews or status only; full values are not rendered.
- Release: R8/ProGuard minification and resource shrinking are used for release builds (see Android release obfuscation guidelines in `.trellis/spec/android/` when contributing).

**Do not commit** keystores, private keys, or files such as `keystore_base64.txt` with real secrets. Use repository secrets for CI signing when publishing releases.

## License

This project is licensed under the [GNU General Public License v3.0](LICENSE).

## Related links

- Integration spec: [`docs/android-mobile-login-integration.md`](docs/android-mobile-login-integration.md)
- Android module notes: [`android/README.md`](android/README.md)
- Default API site: [https://tts.chloemlla.com](https://tts.chloemlla.com)
