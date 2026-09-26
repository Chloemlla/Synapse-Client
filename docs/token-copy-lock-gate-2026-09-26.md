# 登录令牌复制的锁屏门禁（2026-09-26）

## 需求

复制 `sml_` 登录令牌前必须先通过系统锁屏 / PIN / 图案 / 密码（已录入时含指纹、人脸）验证；
设备没设锁屏时不能静默放行，要把用户引导去系统设置里创建。

## 官方依据

| 结论 | 出处 |
|---|---|
| `KeyguardManager.isDeviceSecure()` 判定"是否设了 PIN / 图案 / 密码"，滑动解锁不算（API 23） | `android.app.KeyguardManager` 参考页 |
| `KeyguardManager.createConfirmDeviceCredentialIntent(title, description)` 拉起系统凭据确认界面，调用方用 `startActivityForResult` 并按 `Activity.RESULT_OK` 判定（API 21，API 29 起官方建议改用 `BiometricPrompt`） | 同上 |
| 未设置凭据时用 `Settings.ACTION_BIOMETRIC_ENROLL` + `Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED`：官方说明该页 "enroll biometrics, **and setup PIN/Pattern/Pass if necessary**"，即顺带引导创建锁屏 | `android.provider.Settings` 参考页 / Sign in with Biometric 指南 |
| `BIOMETRIC_STRONG \| DEVICE_CREDENTIAL` 这个组合在 Android 10（API 29）及以下不被支持，官方明确"在 10 及以下用 `KeyguardManager.isDeviceSecure()` 检查是否存在 PIN/图案/密码" | developer.android.com「Biometric authentication」 |
| `ClipDescription.EXTRA_IS_SENSITIVE`：标记剪贴板内容敏感（API 33），系统提示不再预览明文 | `android.content.ClipDescription` 参考页 |

## 技术选择

`minSdk = 26`，且 `MainActivity` 是 `ComponentActivity`（不是 `FragmentActivity`，历史上改基类引发过启动崩溃）。
`androidx.biometric` 的 `DEVICE_CREDENTIAL` 只在 API 30+ 可用，走它必须再写一条 KeyguardManager 分支、
还要新增依赖与 `setNegativeButtonText` 互等约束。所以**统一走 `KeyguardManager` 一条路径**（覆盖 API 21+，
已录入生物识别时系统界面本身也接受生物识别），不新增依赖。

## 落地的行为矩阵

| 设备状态 | 点「复制」的结果 |
|---|---|
| 已设锁屏凭据 | 弹系统锁屏/PIN 验证 → `RESULT_OK` 才写入剪贴板，行下提示「已复制到剪贴板」 |
| 验证被取消 / 失败 | **不写入**，提示「未完成验证，没有复制」 |
| 未设锁屏 | 弹「需要先设置锁屏」对话框，「去设置」跳系统设置（`ACTION_BIOMETRIC_ENROLL` → `ACTION_SECURITY_SETTINGS` → `ACTION_SETTINGS` 逐级回退） |
| 系统给不出确认 Intent（返回 null） | 按未通过处理，同样弹引导对话框——不放行 |
| 设置页也拉不起来 | 提示「请手动到系统设置里设置锁屏」 |

其它行（当前账号、User ID、邮箱、设备 ID、SML 过期时间）不需要验证；JWT / 密码 / 二维码 `scanToken`
依旧既不展示也不可复制。屏幕上的令牌始终只显示脱敏预览，明文只进剪贴板。

## 涉及文件

- 新增 `core/auth/SynapseDeviceLock.kt`（`isSecure` / `confirmIntent` / `openLockSetup`）
- `ui/SynapseMobileApp.kt`：`CopyableLine` 增加 `requiresDeviceAuth`，验证通过后经
  `toSensitiveClipEntry()` 写剪贴板（Android 13+ 标记敏感）；只有「SML 登录令牌」这一行开启门禁
- `docs/android-mobile-login-integration.md`、`README.md`：安全边界同步
