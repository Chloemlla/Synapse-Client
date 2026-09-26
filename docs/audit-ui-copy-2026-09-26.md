# UI 文案精简清单（2026-09-26）

> 目标：**不要把实现原理/协议细节写进用户界面**。界面只讲"要做什么、现在什么状态"，
> 不讲"服务端怎么签发、用什么协议字段、内部走哪个 endpoint"。
>
> 数据层（`SynapseFailureMessage` / `SynapseApiErrorFormatter`）仍保留完整诊断信息，
> 但界面默认只显示第一行人类可读摘要，其余折叠到「详情」；诊断信息继续可供复制反馈使用。
>
> 涉及文件：`android/app/src/main/java/com/chloemlla/synapse/mobile/ui/SynapseMobileApp.kt`、
> `.../ui/SynapseLoginViewModel.kt`、`.../core/update/UpdateDialog.kt`

## A. 错误/状态提示的展示层级

| 编号 | 位置 | 现状 | 改法 | 理由 |
|---|---|---|---|---|
| UI-COPY-01 | `SynapseMobileApp.kt` `StatusBanner` | 直接把多行诊断文本（`API 请求：POST …` / `HTTP 状态：400` / `异常类型：…` / `原因 #1：…`）原样铺在横幅里 | 横幅只显示首行摘要；存在后续诊断行时给一个「详情 / 收起详情」按钮，默认折叠 | 用户第一眼看到的是"失败了 + 人话原因"，而不是调用栈；需要反馈时仍可展开复制 |
| UI-COPY-02 | `SynapseMobileApp.kt` `TurnstileVerificationPanel` 调用侧 / 活动会话错误卡 | 配置错误、会话错误把整段诊断铺进卡片 | 卡片只显示摘要行（动作按钮已有「重新加载」） | 同 UI-COPY-01，卡片本身信息密度已高 |

## B. 登录页：删掉"令牌/协议是怎么流转的"解说

| 编号 | 现文案 | 改为 | 理由 |
|---|---|---|---|
| UI-COPY-03 | 副标题「签发本机 SML 令牌，用于静默登录和网页登录确认。」 | 「登录后用于静默登录和网页登录确认。」 | 不解释令牌怎么来的 |
| UI-COPY-04 | 「先登录本客户端签发 SML 令牌，之后可用「网页登录」扫码确认电脑端。」 | 「先登录本客户端，之后可用「网页登录」扫码确认电脑端。」 | 同上 |
| UI-COPY-05 | 「已在网页完成二次验证时，也可在下方粘贴 JWT 直接授权本机。」 | 「已在网页端登录时，也可在下方粘贴 JWT 直接授权本机。」 | 去掉二次验证/流程细节 |
| UI-COPY-06 | 副标题「主路径：填写账号信息并签发本机令牌。」 | 「填写账号信息完成登录。」 | "主路径"是实现用语 |
| UI-COPY-07 | 「这是登录本客户端；如需二次验证，完成 TOTP 或 Passkey 后才会保存客户端令牌。」 | 「如需二次验证，完成 TOTP 或通行密钥后即完成登录。」 | 去掉"才会保存令牌"的机制说明 |
| UI-COPY-08 | 二次验证卡第 3 行「二次验证凭据：已接收」 | 删除该行 | 内部字段状态，无行动价值 |
| UI-COPY-09 | 「当前界面无法获取 Activity，暂不能唤起 Credential Manager。」 | 「暂时无法唤起系统通行密钥，请稍后重试。」 | Activity/Credential Manager 是实现名词 |
| UI-COPY-10 | 通行密钥卡标题 `Discoverable Passkey` / `Passkey 验证中` | 统一「通行密钥验证中」 | 英文协议术语不进标题 |
| UI-COPY-11 | 「不会在界面展示 challenge 或 credential id 原文。」 | 删除该行 | 这本身就是在向用户解释实现约束 |
| UI-COPY-12 | Google 卡副标题「通过 Credential Manager 获取 Google ID Token，对接 Happy-TTS /api/auth/google。」 | 「使用系统账号选择器完成 Google 授权。」 | 内部 endpoint 与协议字段不进 UI |
| UI-COPY-13 | 「将唤起系统 Google 账号选择；登录成功后自动签发本机 SML 令牌。」 | 「将唤起系统 Google 账号选择。」 | 去掉令牌机制 |
| UI-COPY-14 | Linux.do 卡副标题「浏览器完成 OAuth 授权后，用 ticket 换取 JWT 并签发本机 SML 令牌。」 | 「在浏览器完成授权后回到 App 即可登录。」 | 同上 |
| UI-COPY-15 | 「将打开系统浏览器访问 Happy-TTS /api/auth/linuxdo/start?client=synapse-android。授权完成后优先经 synapse:// 或 App Links 回 App；也可随时在下方粘贴回调链接或 ticket。」 | 「将打开系统浏览器完成授权；完成后会自动回到 App，也可在下方粘贴回调链接或 ticket。」 | 内部 URL、scheme、App Links 都是实现细节 |
| UI-COPY-16 | 「可粘贴完整回调 URL（含 ticket=）、裸 query（ticket=...），或仅粘贴 ticket 字符串。」 | 「可粘贴回调链接或 ticket。」 | 解析格式说明不必给用户 |
| UI-COPY-17 | 「仅粘贴完整 JWT，应用不会完整展示令牌内容。」 | 「请粘贴完整 JWT。」 | 后半句是实现约束自述 |
| UI-COPY-18 | 「扫描或粘贴 synapse://mobile-login 二维码 payload。」 | 「扫描或粘贴网页登录二维码内容。」 | 自定义 scheme 是实现细节 |
| UI-COPY-19 | 「可粘贴二维码内容，并查看解析结果。」 | 「可粘贴二维码内容。」 | "解析结果"是实现视角 |
| UI-COPY-20 | 账号选择行「可用凭据：JWT + SML」/「可用凭据：JWT」/「可用凭据：SML」 | 「JWT + SML」/「JWT」/「SML」 | 减少前缀噪音，保留用户可辨的状态 |

## C. 会话页

| 编号 | 现文案 | 改为 | 理由 |
|---|---|---|---|
| UI-COPY-21 | 「IP 地址仅显示脱敏摘要，属地来自服务端记录。」 | 「IP 地址已脱敏显示。」 | 不解释数据来源 |
| UI-COPY-22 | 「SML 登录令牌有效期以服务端签发时间为准。」 | 删除该分支（有令牌时不再补一句说明） | 纯机制说明；过期时间已在上一行展示 |
| UI-COPY-23 | 「SML 登录令牌已过期并已自动吊销，请重新完成授权登录。」 | 「登录已过期，请重新登录。」 | 用结果语言替代机制语言 |
| UI-COPY-24 | 「SML 登录令牌已过期，请重新完成授权登录。」 | 「登录已过期，请重新登录。」 | 同上 |

## D. ViewModel 状态文案

| 编号 | 现文案 | 改为 | 理由 |
|---|---|---|---|
| UI-COPY-25 | 「检测到 SML 登录令牌已过期，已自动吊销本地令牌，请重新完成授权登录。」 | 「登录已过期，请重新登录。」 | 机制说明 |
| UI-COPY-26 | 「已切换账号，并检测到 SML 登录令牌已过期，已自动吊销本地令牌，请重新完成授权登录。」 | 「已切换账号；该账号登录已过期，请重新登录。」 | 同上 |
| UI-COPY-27 | `X 验证成功，已登录本客户端并签发客户端登录令牌。当前账号：Y`（Passkey/TOTP/Linux.do/Google/JWT 共 6 处） | `X 登录成功。当前账号：Y` | 不解释令牌签发 |
| UI-COPY-28 | 「已使用 JWT 登录本客户端并签发客户端登录令牌。」 | 「已使用 JWT 登录。」 | 同上 |
| UI-COPY-29 | 「服务端未确认会话已撤销。」 | 「会话撤销未生效，请稍后重试。」 | 不暴露服务端字段语义 |
| UI-COPY-30 | 「服务端未返回 revoked=true。」 | 「撤销未生效，请稍后重试。」 | 同上 |
| UI-COPY-31 | 「当前服务端未启用 Google 登录。」/「服务端未配置 Google Client ID。」 | 「Google 登录暂不可用。」/「Google 登录暂不可用，请稍后重试。」 | 服务端配置细节与用户无关 |

## E. 更新弹窗

| 编号 | 现文案 | 改为 | 理由 |
|---|---|---|---|
| UI-COPY-32 | 「该版本按发布时间判定为更新（版本号无法语义化比较），如已安装可忽略。」 | 「该版本可能不是最新，如已安装可忽略。」 | 不解释版本比较实现 |

## F. 追加项（diff 核查后补齐）

| 编号 | 位置 | 现文案 | 改为 | 理由 |
|---|---|---|---|---|
| UI-COPY-33 | `SynapseMobileApp.kt` Google 配置错误卡 | 直接铺 `googleAuthConfigError` 全文（含 `上下文：Google config` / `异常类型：…`） | 只显示摘要行 | 同 UI-COPY-01 |
| UI-COPY-34 | `SynapseMobileApp.kt` Linux.do 配置错误卡 | 同上 | 只显示摘要行 | 同上 |
| UI-COPY-35 | `SynapseMobileApp.kt` 扫码区标题/输入框/粘贴按钮 | 「2. 核对 payload」「网页登录二维码 payload」「从剪贴板粘贴二维码 payload」 | 统一改「二维码内容」 | 内部字段名不进 UI |
| UI-COPY-36 | `SynapseMobileApp.kt` 二维码详情卡 | 多一行「Session：{payload.sessionId}」 | 删除该行 | 内部会话 id 对用户无意义 |
| UI-COPY-37 | `AuthModels.kt` `PasskeyAuthenticationOptions.summaryLines` | 「Challenge：已返回」「RP ID：…」「模式：Discoverable（无需用户名）」「Credential 数量：n」「User Verification：…」 | 只留一句「无需输入用户名，直接选择本机保存的通行密钥。」/「请选择要使用的通行密钥。」 | WebAuthn 协议字段全部退出界面；`SynapsePasskeyJsonTest` 只断言不泄露原始 challenge/credential id，仍成立 |

## 不改的部分（明确保留）

- 破坏性操作确认文案（撤销/清理），仍说明"会发生什么"。
- Google 登录的失败措辞见 `docs/google-signin-fix-2026-09-26.md`（同一轮里的另一个缺陷）。
- 活动设备与客户端的**数据本身**（IP 属地、最近活动、设备 ID、过期时间）。
- 「错误详情」展开后的诊断文本（`SynapseFailureMessage` / `SynapseApiErrorFormatter` 原样保留），
  以及更新弹窗既有的「错误详情」折叠区。
- 敏感值不完整展示的策略（README 约定），只删除"我们不会展示 X"这类自述句。

## 落地情况

提交 `c7c366a`（已推 `origin/main`）。逐条去向：

- UI-COPY-01（横幅只显摘要 + 「详情」展开）→ 新增 `ui/UiMessageText.kt`（`splitUiMessage` / `uiMessageSummary`），
  `StatusBanner` 改为 `Column(summary + 可选 details + 详情按钮)`。**这里不是纯文案改动**：
  横幅多了一个展开/收起的局部状态与一个分支，属于展示层逻辑变更。
- UI-COPY-02（卡片只显摘要）→ `SynapseMobileApp.kt` 活动会话不可用卡、会话操作失败行、
  Google 配置错误卡、Linux.do 配置错误卡包 `uiMessageSummary(...)`；
  `TurnstileVerificationView.kt` 的人机验证配置错误卡同样处理。
- UI-COPY-03～20、21～22、33～36 → `ui/SynapseMobileApp.kt`（其中 UI-COPY-22 把原来的四分支 `Text(when{…})`
  改成 `tokenNotice` 变量 + `if (tokenNotice != null)`，有效令牌时不再输出任何解释句）。
- UI-COPY-23～24 与 UI-COPY-25～31 → `ui/SynapseLoginViewModel.kt`（状态/结果文案，无控制流变动）。
- UI-COPY-32 → `core/update/UpdateDialog.kt`。
- UI-COPY-37 → `core/auth/AuthModels.kt`（`summaryLines` 不再输出 challenge / rpId / credential 数量 /
  userVerification；`SynapsePasskeyJsonTest` 只断言不泄露原始 challenge/credential id，仍成立）。
- 鉴权流程、拦截器、凭据存储、`src/app.ts` 风格的中间件顺序均未触碰；
  `SynapseFailureMessage` / `SynapseApiErrorFormatter` 数据层文本原样保留（它们的用例测试未改）。

## 验证

本地禁止构建，全部交给 GitHub Actions：

- 提交：`c7c366ad3fadfb6cead212cd96c637138d79bc69`（已签名，`git log -1 --format=%G?` = `G`）
- CI：`Build Synapse Android`（run `36210030127`）= `completed / success`
- Release：`v1.0.96-c7c366ad`
- 本次未动任何测试文件，因此测试结果等于回归基线（18 个 `src/test` 文件全部参与）
