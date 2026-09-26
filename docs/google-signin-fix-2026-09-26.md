# Google 登录修复（2026-09-26）

## 现场症状

用户端报：

```
Google 登录需要重新验证账号。请前往系统设置 → Google → 管理账号，
移除并重新添加此 Google 账号，然后重新尝试登录。
（异常：s.dg:4:4:）
异常类型：java.lang.IllegalStateException
```

设备上的 Google 账号在其它 App（如 Twitter 官方客户端）里都能正常用，
说明**账号本身没坏**，那句"移除并重新添加账号"是客户端自己编的错误建议。

## 根因（逐条对齐官方文档）

1. **`s.dg:4:4:` = `ApiException(statusCode = 4)`**。
   `CommonStatusCodes.SIGN_IN_REQUIRED = 4`：*“The client attempted to connect to the
   service but the user is not signed in.”*（`s.dg` 是 Play 服务里被混淆的 `ApiException` 类名）。
   官方处置是**发起交互式登录**（`GoogleSignInClient.getSignInIntent()`），
   而不是让用户去系统设置里删账号。
2. 这句文案来自 `SynapseGoogleCredentialClient.requestGoogleSignInClientFallback()`：
   它只调 `silentSignIn()`（静默、非交互），首次登录必然返回 `SIGN_IN_REQUIRED`，
   `catch` 里却把任何异常都翻译成"移除并重新添加此 Google 账号"。
   注释里写着"交互式 `getSignInIntent()` 需要 ActivityResultLauncher，这里拿不到" —— 这就是要补的那块。
3. `GoogleSignInOptions.Builder()` **没带 `DEFAULT_SIGN_IN`**，静默登录连基本 scope 都不全，
   失败概率进一步抬高。
4. Credential Manager 三条路径（已授权账号 → 全部账号 → SIWG 按钮流）里，任何一条抛
   非 `NoCredentialException` / 非"技术性取消"的 `GetCredentialException` 都会**直接中断**，
   后面的按钮流根本没机会跑；而官方明确说按钮流正好覆盖"底部弹窗被跳过 / 账号需要重新验证 /
   登录提示被关闭"这几种情况。
5. 官方已知的设备侧诱因（都会表现为"其它 App 能用、这个 App 不能用"）：
   - `TransactionTooLargeException`：Android 14+ 多 Google 账号时 `GetGoogleIdOption` 弹不出窗口，
     **`GetSignInWithGoogleOption` 不受影响**，Play 服务 24.40.XX+ 修复；
   - `Google 账号设置 → 使用 Google 账号登录`（Sign-in prompts）被关闭时，
     底部弹窗路径返回 `NoCredentialException`，**按钮流不受影响**；
   - 缺 `androidx.credentials:credentials-play-services-auth` → `GetCredentialProviderConfigurationException`
     （本仓依赖已齐，排除）。

## 改动清单

| 编号 | 文件 | 改动 |
|---|---|---|
| GS-01 | `core/auth/SynapseGoogleCredentialClient.kt` | `getCredential()` 改用 `MutableContextWrapper(activity)`（官方：避免配置变更时窗口行为不确定/内存泄漏） |
| GS-02 | 同上 | 已授权账号一步按官方启用 `setAutoSelectEnabled(true)`；三条路径全部失败才升级，中途 `GetCredentialException` 不再中断后续策略 |
| GS-03 | 同上 | 删除 `silentSignIn()` 兜底与"移除并重新添加账号"文案；新增 `interactiveSignInIntent()`（`GoogleSignInOptions.Builder(DEFAULT_SIGN_IN)` + `getSignInIntent()`）与 `idTokenFromInteractiveResult()`（按 `ApiException.statusCode` 给结论） |
| GS-04 | 同上 | 新增 `GoogleInteractiveSignInRequired` 异常，携带 `serverClientId`，由界面层用 `ActivityResultLauncher` 承接 |
| GS-05 | `core/auth/SynapseCredentialErrorMapper.kt` | 取消/失败的措辞改成官方成因（需要在设备上登录 Google 账号、检查"使用 Google 账号登录"开关、更新 Google Play 服务、可改用账号密码），不再出现"移除并重新添加"；新增 `googleWindowFailure(statusCode, systemMessage)`、`noCredentialSummary()` |
| GS-06 | `ui/SynapseUiState.kt` | 新增 `googleInteractiveSignInClientId: String?` |
| GS-07 | `ui/SynapseLoginViewModel.kt` | 抽出 `resolveGoogleServerClientId()` / `finishGoogleSignIn(idToken)`；`signInWithGoogle` 捕获 GS-04 后置入 pending；新增 `consumeGoogleInteractiveSignIn()`、`completeGoogleSignInWithWindow()`、`reportGoogleSignInWindowUnavailable()` |
| GS-08 | `ui/SynapseMobileApp.kt` | Google 卡片内注册 `rememberLauncherForActivityResult(StartActivityForResult)`，`LaunchedEffect` 观察 pending 后拉起系统登录窗口，回传结果 |
| GS-09 | `docs/android-mobile-login-integration.md` | Google 章节补"必须走交互回退、不得提示删账号"的约束 |

## 不改的部分

- 服务端 `/api/auth/google/config` 与 `bind-session`/`/api/auth/google` 的取 token 逻辑（协议不变）。
- Passkey / TOTP / Linux.do 路径。
- 诊断信息仍然保留在数据层，界面默认只显示摘要（见 `docs/audit-ui-copy-2026-09-26.md`）。

## 验证

本地禁止构建（性能不足），全部由 GitHub Actions `Build Synapse Android` 编译 + 单元测试裁决。
`SynapseCredentialErrorMapperTest` 现有断言（`重新验证`、`已取消`、`未完成：provider interrupted`、
`shouldRetryAfterCancellation` 四例）与新措辞保持兼容。
