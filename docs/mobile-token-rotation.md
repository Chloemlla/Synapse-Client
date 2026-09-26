# 客户端登录令牌轮换（安卓侧实现，2026-09-26）

> 协议与策略的**权威文本在服务端仓库**：`Chloemlla/Synapse` → `docs/mobile-token-risk-control.md`。
> 本文只记录安卓端怎么执行。

## 为什么做

`sml_` 客户端登录令牌一次签发有效 90 天，且兑换 JWT 不要求 JWT —— 拿到这串字符就等于拿到该账号的
静默登录能力。此前服务端**无法区分真机与复制者**，唯一止损是用户主动撤销。现在把它改成
"一条血缘，默认每天推进一代"，旧代被再次使用即触发整链吊销。

## 端点

```
POST /api/auth/mobile-login/client-token/rotate
{ "clientLoginToken": "sml_…", "deviceId": "<本机稳定 id>", "reason": "scheduled" | "manual" }
```

响应里的 `nextRotationAt` 是下一次该动手的时间；`graceMs` 是旧代还能用在途请求的窗口。
**安卓端不推算节奏**，只用服务端给的值。

设备证明启用后，每次轮换前多一步：

```
POST /api/auth/mobile-login/integrity-challenge
{ "deviceId": "<本机稳定 id>", "clientLoginToken": "sml_…" }   // 或 Bearer JWT（首次签发）
→ { "required": false }                                        // 服务端没启用：跳过
→ { "required": true, "nonce": "…", "cloudProjectNumber": "…" } // 拿去问 Google 换令牌
```

`nonce` 一次性且绑定申请它的账号与设备；换来的令牌随轮换/签发请求以 `integrityNonce` +
`integrityToken` 回传。响应里的 `requiresVerification` 是服务端对**本代**的降级判定。

## 实现分布

| 位置 | 职责 |
|---|---|
| `core/auth/SynapseClientTokenRotation.kt` | 纯函数：`isDue(nextRotationAt, now)`（没记过节奏就视为该补一次）、`retryAt(statusCode, now)`（429 → 30 分钟，网络/5xx → 6 小时，401/403 → 不再重试） |
| `core/auth/SynapseIntegrityPolicy.kt` | 纯逻辑：`required=false`/nonce 缺失/项目号缺失 → 不申请；拿不到 Google 令牌 → 不带证明。**从不本地否决轮换** |
| `core/auth/SynapseIntegrityProvider.kt` | 唯一碰 Play 服务的一环：`IntegrityManagerFactory` + `setNonce` + `setCloudProjectNumber`，15 秒超时，任何失败返回 null |
| `core/auth/SynapseAuthRepository.kt` | `ensureClientTokenRotation()`（到期才动）、`rotateClientTokenNow()`（手动），`Mutex` 单飞；先申请证明再随请求提交；失败按退避改写 `nextRotationAt`，401/403 直接清本机凭据 |
| `core/auth/SynapseCredentialCodec.kt` | 持久化 `client_login_token_next_rotation_at` / `_rotated_at` / `_rotation_index` / `_needs_reverification`；缺字段的旧凭据按第 0 代、无降级解析，不当成损坏 |
| `ui/SynapseLoginViewModel.kt` | 启动后静默补一次轮换；手动入口 `rotateClientLoginToken()`；只有"登录态已失效"才提示 |
| `ui/SynapseMobileApp.kt` | 「本地会话 → 会话操作」新增「更新登录令牌」（带确认）；「设备与凭据」显示下次更新时间与降级提示 |

## 行为约束

1. **触发点**：App 启动、静默登录成功后（非阻塞）、用户手动。三者共用一把 `Mutex`，同一进程内不叠请求。
2. **登录状态不掉**：服务端轮换不撤销旧会话，本地拿到新令牌后整体覆盖；期间在途请求靠 `graceMs` 兜住。
3. **失败语义**：
   - `429`（节流 / 超配额）→ 静默排期，界面不报"失败"；
   - 网络不通 / 5xx / 旧服务端没有该接口（404）→ 保留旧令牌（仍然有效），按退避再试；
   - `401`（含 `MOBILE_TOKEN_REUSED` 整链吊销）→ 清掉该账号的 JWT 与 `sml_` 令牌，提示重新登录，
     **不做自动重登**（自动重试等于给攻击者留活口，也会形成循环）。
4. **设备证明只降级不否决**：申请挑战失败、拿不到 Google 令牌、超时，一律照常提交轮换请求；
   服务端回 `requiresVerification: true` 时只把标记存下来（界面提示一句，下一次校验通过自动清掉），
   有效期与节奏都由服务端在响应里给足，客户端不自己缩短。
5. **明文边界**：屏幕只显示 `sml_xxxx...yyyy` 预览；复制到剪贴板必须先过锁屏 / PIN 验证
   （`docs/token-copy-lock-gate-2026-09-26.md`）；令牌不写日志、不进 URL、不进崩溃上报；
   JWT、密码、二维码 `scanToken` 依旧不展示也不复制。
6. **界面不复述机制**：按钮叫「更新登录令牌」，结果只说"已更新 / 稍后自动重试 / 请重新登录"，
   降级只说"本机登录校验未通过，登录有效期已临时缩短"，不解释血缘、宽限期、状态码、
   也不提 Play / 证明 / nonce（方法论 §一-10）。

## 测试

- `SynapseClientTokenRotationTest`：到期判定、429/401/403/网络失败的退避差异。
- `SynapseIntegrityPolicyTest`：服务端不要求时不申请；挑战字段缺失时不申请；Google 返回空时不带证明。
- `SynapseCredentialCodecTest`：节奏字段与降级标记往返；缺字段的旧凭据不能被判为损坏。
- `JsonMappingsTest`：轮换/签发响应解析（含 `requiresVerification`）；挑战解析与 `required` 缺省。

编译与测试一律由 GitHub Actions 执行（本地禁构建）。
