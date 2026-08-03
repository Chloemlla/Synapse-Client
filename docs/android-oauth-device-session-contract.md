# Android OAuth 与设备会话契约

本文档描述 Synapse-Client Android 与 Happy-TTS 对接的应用内授权和活动会话接口。Android 端只信任构建配置中的 HTTPS API origin，不接受 intent 传入的 provider origin。

## OAuth intent

唯一入口：

```text
synapse://oauth/authorize
```

查询参数必须完整包含以下字段，不能包含其他字段：

| 字段 | 要求 |
| --- | --- |
| `provider_origin` | HTTPS origin，必须与 `BuildConfig.SYNAPSE_API_BASE_URL` 规范化后完全一致 |
| `response_type` | 固定为 `code` |
| `client_id` | 非空 OAuth client ID |
| `redirect_uri` | `piliplus` 客户端固定为 `piliplus://synapse-auth`；其他客户端使用 HTTPS、带 host、无 user info/fragment |
| `scope` | 以空格分隔的非空 scope |
| `state` | 非空，最长 500 字符；所有批准、拒绝和错误回调都原样带回 |
| `code_challenge` | 43-128 位 RFC 7636 code verifier 的 S256 base64url 值 |
| `code_challenge_method` | 固定为 `S256` |

Intent 不得包含 `token`、`access_token`、`refresh_token`、`id_token`、`jwt` 或 `client_secret`。Android 也不会把 JWT、SML、OAuth access token 或 refresh token 写入回调 Intent。批准回调只允许服务端返回的 OAuth `code` 和 `state`；拒绝回调为 `error=access_denied`、`error_description` 和 `state`。

## OAuth API

Android 使用本机已登录凭据。若本地只有 SML，则先调用现有 `/api/auth/mobile-login/client-token/exchange` 换取 JWT；JWT 只放在 HTTPS `Authorization: Bearer` 请求头，不进入 intent 或 UI。

### Preview

```http
GET /api/oauth/authorize/preview
Authorization: Bearer <jwt>
```

查询参数与 intent 中的 OAuth 字段一致；仅用于本机路由的 `provider_origin` 不发送给服务端。响应至少包含：

```json
{
  "success": true,
  "client": { "clientId": "...", "name": "...", "description": null, "homepageUrl": null },
  "scopes": ["openid", "profile"],
  "scopeDetails": [{ "key": "profile", "label": "资料", "description": "...", "category": "identity", "identityScope": true }],
  "redirectUri": "https://client.example/callback",
  "responseType": "code",
  "state": "STATE",
  "codeChallengeMethod": "S256",
  "user": { "id": "...", "username": "...", "email": "...", "role": "trusted" }
}
```

Android 在弹窗中展示客户端名称、回调地址、scope 和当前账号。弹窗不能通过返回键或点击外部区域绕过明确的同意/拒绝操作。

### Approve / deny

```http
POST /api/oauth/authorize
Authorization: Bearer <jwt>
Content-Type: application/json
```

请求体包含 preview 的 OAuth 参数，并额外包含布尔或字符串形式的 `approve`：

```json
{
  "response_type": "code",
  "client_id": "...",
  "redirect_uri": "https://client.example/callback",
  "scope": "openid profile",
  "state": "STATE",
  "code_challenge": "...",
  "code_challenge_method": "S256",
  "approve": true
}
```

响应：

```json
{ "success": true, "redirectUri": "https://client.example/callback?code=...&state=STATE", "scopes": ["openid", "profile"] }
```

拒绝时 `redirectUri` 必须是 `error=access_denied&error_description=...&state=STATE`。Android 会校验 redirect origin/path、state、回调类型和禁止参数后，才用 `ACTION_VIEW` 打开回调。

## 活动设备与客户端 API

这些接口使用当前 JWT。服务端不得在响应中返回 JWT、OAuth token、SML 或 refresh token。

### List

```http
GET /api/auth/sessions
Authorization: Bearer <jwt>
```

响应：

```json
{
  "success": true,
  "devices": [
    {
      "deviceKey": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
      "deviceId": "device-1",
      "deviceName": "Pixel",
      "clientId": "piliplus",
      "clientName": "PiliPlus",
      "clientType": "PiliPlus",
      "ipAddress": "203.0.113.42",
      "ipLocation": { "country": "中国", "region": "上海", "city": "上海" },
      "userAgent": "...",
      "lastActiveAt": "2026-08-03T00:00:00.000Z",
      "createdAt": "2026-08-02T00:00:00.000Z",
      "current": false,
      "sessions": []
    }
  ]
}
```

Android 展示 `clientName/clientType`、设备名称、最近活动和 IP 属地；IP 地址只展示脱敏摘要。服务端也应将 user-agent、设备标识和 IP 视为敏感数据，避免写入日志。

### Revoke

```http
POST /api/auth/sessions/{deviceKey}/revoke
Authorization: Bearer <jwt>
```

请求路径中的 `deviceKey` 代表一个设备/客户端分组；服务端会撤销该分组下的全部 JWT、SML 和 OAuth 会话。

响应：

```json
{ "success": true, "revoked": 3 }
```

服务端必须拒绝撤销当前 JWT 所属的 `deviceKey`，返回 `409` 和 `code: "CURRENT_SESSION_PROTECTED"`。Android 在发送请求前隐藏当前设备的撤销操作，用户确认后才调用接口，并在成功后重新加载列表。
