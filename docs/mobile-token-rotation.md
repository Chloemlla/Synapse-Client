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

## 实现分布

| 位置 | 职责 |
|---|---|
| `core/auth/SynapseClientTokenRotation.kt` | 纯函数：`isDue(nextRotationAt, now)`（没记过节奏就视为该补一次）、`retryAt(statusCode, now)`（429 → 30 分钟，网络/5xx → 6 小时，401/403 → 不再重试） |
| `core/auth/SynapseAuthRepository.kt` | `ensureClientTokenRotation()`（到期才动）、`rotateClientTokenNow()`（手动），`Mutex` 单飞；失败按退避改写 `nextRotationAt`，401/403 直接清本机凭据 |
| `core/auth/SynapseCredentialCodec.kt` | 持久化 `client_login_token_next_rotation_at` / `_rotated_at` / `_rotation_index`；缺字段的旧凭据按第 0 代解析，不当成损坏 |
| `ui/SynapseLoginViewModel.kt` | 启动后静默补一次轮换；手动入口 `rotateClientLoginToken()`；只有"登录态已失效"才提示 |
| `ui/SynapseMobileApp.kt` | 「本地会话 → 会话操作」新增「更新登录令牌」（带确认）；「设备与凭据」显示下次更新时间 |

## 行为约束

1. **触发点**：App 启动、静默登录成功后（非阻塞）、用户手动。三者共用一把 `Mutex`，同一进程内不叠请求。
2. **登录状态不掉**：服务端轮换不撤销旧会话，本地拿到新令牌后整体覆盖；期间在途请求靠 `graceMs` 兜住。
3. **失败语义**：
   - `429`（节流 / 超配额）→ 静默排期，界面不报"失败"；
   - 网络不通 / 5xx / 旧服务端没有该接口（404）→ 保留旧令牌（仍然有效），按退避再试；
   - `401`（含 `MOBILE_TOKEN_REUSED` 整链吊销）→ 清掉该账号的 JWT 与 `sml_` 令牌，提示重新登录，
     **不做自动重登**（自动重试等于给攻击者留活口，也会形成循环）。
4. **明文边界**：屏幕只显示 `sml_xxxx...yyyy` 预览；复制到剪贴板必须先过锁屏 / PIN 验证
   （`docs/token-copy-lock-gate-2026-09-26.md`）；令牌不写日志、不进 URL、不进崩溃上报；
   JWT、密码、二维码 `scanToken` 依旧不展示也不复制。
5. **界面不复述机制**：按钮叫「更新登录令牌」，结果只说"已更新 / 稍后自动重试 / 请重新登录"，
   不解释血缘、宽限期、状态码（方法论 §一-10）。

## 测试

- `SynapseClientTokenRotationTest`：到期判定、429/401/403/网络失败的退避差异。
- `SynapseCredentialCodecTest`：节奏字段往返；缺字段的旧凭据不能被判为损坏。
- `JsonMappingsTest`：轮换响应解析；旧服务端不返回 `nextRotationAt` 时的容错。

编译与测试一律由 GitHub Actions 执行（本地禁构建）。
