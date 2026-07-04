# Android Mobile Login Integration

本文档用于安卓客户端对接 Synapse 登录系统的扫码登录和客户端登录令牌能力。后端接口均挂载在同一站点 API 下，示例路径以 `/api/auth/mobile-login` 开头。
API 地址：https://tts.chloemlla.com
## 登录能力

安卓端需要支持两类登录凭证：

- 标准 JWT：由现有 `/api/auth/login`、TOTP/Passkey 二次验证或客户端令牌兑换接口返回，放入 `Authorization: Bearer <token>`。
- 客户端登录令牌：`sml_` 前缀的长期设备令牌，仅用于安卓端静默换取标准 JWT 或确认网页扫码登录。服务端落盘时只保存哈希。

网页扫码登录由 Web 端创建短期挑战，安卓端扫描二维码并确认，Web 端轮询拿到标准 JWT 后进入登录态。

## 标准登录人机验证

安卓端在展示“登录本客户端并签发令牌”前应先读取 Happy-TTS 兼容的公共 Turnstile 配置：

`GET /api/turnstile/public-config`

示例响应：

```json
{
  "enabled": true,
  "siteKey": "0x4AAAA-public-site-key",
  "hcaptchaEnabled": false,
  "hcaptchaSiteKey": null
}
```

当 `enabled=true` 且 `siteKey` 存在时，客户端必须先加载 Turnstile widget。用户完成验证后，把 widget 返回的一次性 token 作为 `/api/auth/login` 的 `cfToken` 字段提交。登录失败、token 过期或 widget 报错后，应清空本地 token 并重新加载 widget；不得保存、显示或记录该 token。

## 标准登录二次验证

`POST /api/auth/login` 请求字段是 `identifier` 和 `password`。`identifier` 可为用户名或邮箱；不要发送旧的 `username` 字段，否则 Happy-TTS 后端会返回 `identifier 不能为空`。

请求：

```json
{
  "identifier": "alice",
  "password": "plain password",
  "cfToken": "turnstile-widget-token"
}
```

如果账号需要二次验证，会返回短期二次验证 token，而不是正式 JWT。安卓端不能用这个 token 签发客户端登录令牌。

示例响应：

```json
{
  "success": true,
  "requires2FA": true,
  "token": "short-lived-2fa-token",
  "twoFactorType": ["TOTP", "Passkey"],
  "user": {
    "id": "string",
    "username": "string",
    "email": "string",
    "role": "user"
  }
}
```

安卓端应保留 `user`、短期 `token` 和 `twoFactorType`，继续走 TOTP 或 Passkey 验证。只有后续接口返回正式 JWT 后，才能调用 `/api/auth/mobile-login/client-token/issue`。

### TOTP 完成认证

`POST /api/totp/verify-token`

请求：

```json
{
  "userId": "string",
  "pendingToken": "short-lived-2fa-token",
  "token": "123456"
}
```

也可使用备用恢复码：

```json
{
  "userId": "string",
  "pendingToken": "short-lived-2fa-token",
  "backupCode": "recovery-code"
}
```

响应：

```json
{
  "message": "验证成功",
  "verified": true,
  "token": "jwt"
}
```

安卓端收到正式 JWT 后应加密保存 JWT，并立即调用客户端登录令牌签发接口。

### Passkey 开始认证

`POST /api/passkey/authenticate/start`

请求：

```json
{
  "username": "string",
  "clientOrigin": "https://tts.chloemlla.com"
}
```

响应：

```json
{
  "options": {
    "challenge": "string",
    "rpId": "string",
    "allowCredentials": [
      {
        "id": "string",
        "transports": ["internal"]
      }
    ],
    "userVerification": "required"
  }
}
```

`options` 是 WebAuthn 认证选项，交给浏览器 Passkey API 或原生 Credential Manager 适配层使用。界面可显示是否已获取 challenge、`rpId`、credential 数量和 `userVerification`，不要完整展示 challenge 或 credential id。

### Passkey 完成认证

`POST /api/passkey/authenticate/finish`

安卓端把 Passkey API 返回的 WebAuthn assertion response 按 `response` 字段发回服务端。请求必须同时包含用户名和客户端 origin：

```json
{
  "username": "alice",
  "response": {
    "id": "credential-id",
    "rawId": "credential-id",
    "type": "public-key",
    "response": {}
  },
  "clientOrigin": "https://tts.chloemlla.com"
}
```

验证成功后返回正式 JWT：

```json
{
  "success": true,
  "token": "jwt",
  "user": {
    "id": "string",
    "username": "string",
    "email": "string"
  }
}
```

安卓端收到正式 JWT 后应加密保存 JWT，并立即调用客户端登录令牌签发接口。

## 二维码 Payload

Web 登录页调用创建挑战接口后，二维码内容是一个 deep link：

```text
synapse://mobile-login?sessionId=<sessionId>&scanToken=<scanToken>&apiBaseUrl=<apiBaseUrl>&expiresAt=<ISO8601>
```

字段含义：

- `sessionId`：扫码登录会话 ID。
- `scanToken`：安卓端确认扫码会话时必须提交的一次性证明。
- `apiBaseUrl`：当前 Web 所在后端 API Origin，例如 `https://tts.chloemlla.com`。
- `expiresAt`：二维码过期时间。当前有效期为 3 分钟。

安卓端扫描后应校验 scheme 为 `synapse://mobile-login`，再展示账号、设备和目标站点确认页。

当系统扫码或浏览器把 `synapse://mobile-login` deep link 打开到安卓客户端时，客户端应自动切换到“网页登录”页，填入 payload，并在 payload 有效时标记已扫码。

## 接口

### Web 创建扫码挑战

`POST /api/auth/mobile-login/challenge`

响应：

```json
{
  "success": true,
  "sessionId": "string",
  "pollToken": "string",
  "qrPayload": "synapse://mobile-login?...",
  "expiresAt": "2026-07-04T12:00:00.000Z",
  "pollIntervalMs": 2000
}
```

`pollToken` 只给 Web 端保存，不能进入二维码。

### 安卓标记已扫码

`POST /api/auth/mobile-login/challenge/scan`

请求：

```json
{
  "sessionId": "string",
  "scanToken": "string"
}
```

响应中的 `status` 通常为 `scanned`。

### 安卓确认网页登录

安卓端可用当前 JWT 或客户端登录令牌确认。

方式 A：使用 JWT。

```http
POST /api/auth/mobile-login/challenge/confirm
Authorization: Bearer <jwt>
Content-Type: application/json
```

```json
{
  "sessionId": "string",
  "scanToken": "string"
}
```

方式 B：使用客户端登录令牌。

```json
{
  "sessionId": "string",
  "scanToken": "string",
  "clientLoginToken": "sml_...",
  "deviceId": "android-device-stable-id"
}
```

成功响应：

```json
{
  "success": true,
  "ok": true,
  "status": "approved",
  "expiresAt": "2026-07-04T12:00:00.000Z"
}
```

### Web 轮询扫码结果

`POST /api/auth/mobile-login/challenge/poll`

```json
{
  "sessionId": "string",
  "pollToken": "string"
}
```

未完成：

```json
{
  "success": true,
  "status": "pending",
  "expiresAt": "2026-07-04T12:00:00.000Z"
}
```

已确认：

```json
{
  "success": true,
  "status": "approved",
  "expiresAt": "2026-07-04T12:00:00.000Z",
  "token": "<jwt>",
  "user": {
    "id": "string",
    "username": "string",
    "email": "string",
    "role": "user"
  }
}
```

Web 端拿到 `token` 后按现有登录逻辑写入本地登录态。

### 签发客户端登录令牌

安卓端完成标准登录和二次验证后，用 JWT 签发设备令牌：

```http
POST /api/auth/mobile-login/client-token/issue
Authorization: Bearer <jwt>
Content-Type: application/json
```

```json
{
  "deviceId": "android-device-stable-id",
  "deviceName": "Pixel 8 Pro"
}
```

响应：

```json
{
  "success": true,
  "clientLoginToken": "sml_...",
  "expiresAt": "2026-10-02T12:00:00.000Z"
}
```

客户端登录令牌当前有效期为 90 天。安卓端应存入 Android Keystore 或加密后的私有存储，不应写入日志。

安卓端使用手动 JWT 授权时，必须先调用 `GET /api/auth/me` 获取 JWT 对应的真实用户，再调用 `/api/auth/mobile-login/client-token/issue` 签发该用户的 `sml_` 客户端登录令牌。客户端需要持久化保存 `clientLoginToken` 和后端返回的 `expiresAt`。

安卓端可以同时保存多个账号的客户端登录令牌。每次成功登录或二次验证后，按后端返回的用户 ID 更新对应账号；静默登录、撤销和清理操作只作用于当前选中的账号。扫码确认网页登录时，如果本地保存了多个账号，客户端必须先弹窗列出账号身份和可用凭据状态，由用户选择继续网页登录的账号；选择后把该账号设为当前账号，再调用 `/challenge/confirm`。弹窗不得展示或复制 JWT、`clientLoginToken` 或二维码 `scanToken`。

### 客户端令牌兑换 JWT

`POST /api/auth/mobile-login/client-token/exchange`

```json
{
  "clientLoginToken": "sml_...",
  "deviceId": "android-device-stable-id"
}
```

响应：

```json
{
  "success": true,
  "token": "<jwt>",
  "user": {
    "id": "string",
    "username": "string",
    "email": "string",
    "role": "user"
  }
}
```

安卓端启动时可先兑换客户端令牌，成功后使用返回的标准 JWT 调用业务 API。

### 撤销客户端登录令牌

```http
POST /api/auth/mobile-login/client-token/revoke
Authorization: Bearer <jwt>
Content-Type: application/json
```

```json
{
  "clientLoginToken": "sml_..."
}
```

响应：

```json
{
  "success": true,
  "revoked": true
}
```

## 推荐安卓流程

首次登录：

1. 调用现有 `/api/auth/login`，请求体使用 `identifier` 和 `password`。
2. 如果返回 `requires2FA`，使用短期 `token` 调用 TOTP 或 Passkey 验证接口取得正式 JWT。
3. 获得标准 JWT 后调用 `/client-token/issue`。
4. 安全保存 `clientLoginToken`。

自动登录：

1. 读取本地 `clientLoginToken`。
2. 调用 `/client-token/exchange` 换取标准 JWT。
3. 使用标准 JWT 调用业务 API。
4. 如果本地保存的 `expiresAt` 已过期，自动吊销本地 `sml_` 令牌和 JWT，保留账号信息与过期时间，要求用户重新完成授权登录。
5. 如果返回 401，清理本地客户端令牌并要求用户重新登录。

扫码登录网页：

1. 扫描 Web 登录页二维码并解析 deep link。
2. 调用 `/challenge/scan` 标记已扫码。
3. 展示确认页，确认目标站点 `apiBaseUrl`、当前选中账号和设备 ID。
4. 如果客户端已保存多个账号，弹窗选择用于继续网页登录的账号，选择后切换当前账号。
5. 调用 `/challenge/confirm`，优先使用当前 JWT；JWT 不存在或过期时可使用 `clientLoginToken`。

## 错误与状态

常见 `status`：

- `pending`：二维码已生成，等待扫码。
- `scanned`：安卓端已扫码，等待确认。
- `approved`：安卓端已确认。
- `expired`：二维码过期。
- `consumed`：Web 端已消费登录结果。

常见 HTTP 错误：

- `400`：缺少参数、格式错误。
- `401`：JWT 或客户端登录令牌无效/过期。
- `403`：账号封停或设备不匹配。
- `429`：请求过于频繁。

## 安全要求

- `pollToken` 不进入二维码，只由 Web 端保存和轮询使用。
- `scanToken` 只随二维码传给安卓端，用于证明安卓端扫描的是当前二维码。
- 客户端登录令牌不要写日志，不要放入 URL。安卓端本地授权信息页可以展示并复制当前账号的 `sml_` 客户端登录令牌、过期时间、账号信息和设备 ID，但不得展示或复制 JWT、密码、`scanToken`。
- 建议安卓端保存稳定 `deviceId`，换机或清除 App 数据后重新签发客户端令牌。
- 扫码确认页必须显示目标站点和当前登录账号，降低误扫风险。
