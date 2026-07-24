package com.chloemlla.synapse.mobile.ui

import com.chloemlla.synapse.mobile.core.auth.SynapsePasskeyCredentialClient
import com.chloemlla.synapse.mobile.core.auth.SynapseFailureMessage
import com.chloemlla.synapse.mobile.core.auth.SynapseGoogleCredentialClient

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.content.ClipData
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chloemlla.synapse.mobile.BuildConfig
import com.chloemlla.synapse.mobile.core.auth.StoredSynapseAccount
import com.chloemlla.synapse.mobile.core.auth.SynapseTokenExpiry
import com.chloemlla.synapse.mobile.ui.svg.DynamicColorImageVectors
import com.chloemlla.synapse.mobile.ui.svg.drawablevectors.coder
import com.chloemlla.synapse.mobile.ui.svg.drawablevectors.download
import com.chloemlla.synapse.mobile.ui.svg.drawablevectors.videoFiles
import com.chloemlla.synapse.mobile.ui.svg.drawablevectors.videoSteaming
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SynapseMobileApp(viewModel: SynapseLoginViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = "Synapse Mobile",
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding),
        ) {
            TabRow(
                selectedTabIndex = state.selectedTab.ordinal,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
            ) {
                SynapseTab.entries.forEach { tab ->
                    Tab(
                        selected = state.selectedTab == tab,
                        onClick = { viewModel.selectTab(tab) },
                        icon = {
                            Icon(
                                imageVector = tab.icon(),
                                contentDescription = null,
                            )
                        },
                        text = { Text(tab.label) },
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                when (state.selectedTab) {
                    SynapseTab.Login -> LoginPanel(state, viewModel)
                    SynapseTab.Qr -> QrPanel(state, viewModel)
                    SynapseTab.Session -> SessionPanel(state, viewModel)
                }
            }
        }
    }
}

private fun SynapseTab.icon(): ImageVector =
    when (this) {
        SynapseTab.Login -> Icons.AutoMirrored.Outlined.Login
        SynapseTab.Qr -> Icons.Outlined.QrCodeScanner
        SynapseTab.Session -> Icons.Outlined.Devices
    }

@Composable
private fun SynapseAppHeader(
    state: SynapseUiState,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val activeAccount = state.credentials.activeAccount ?: state.credentials.accounts.firstOrNull()
    val accountName = activeAccount?.displayName ?: state.credentials.displayName ?: "未登录"
    val tokenReady = state.hasCurrentClientLoginToken
    val qrReady = state.hasUsableQrPayload && state.hasAnyWebLoginCredential
    // Compact phones: prefer denser header; token availability is shown once via the SML pill.
    val outerPadding = if (compact) 8.dp else 10.dp
    val verticalSpacing = if (compact) 4.dp else 6.dp
    val iconSize = if (compact) 32.dp else 40.dp

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 2.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(outerPadding),
            verticalArrangement = Arrangement.spacedBy(verticalSpacing),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(iconSize)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    Text(
                        text = "安全登录中枢",
                        style = if (compact) {
                            MaterialTheme.typography.titleSmall
                        } else {
                            MaterialTheme.typography.titleMedium
                        },
                        fontWeight = FontWeight.SemiBold,
                    )
                    // Subtitle is account/context only — do not repeat token-available wording
                    // (that status lives solely in the SML StatusPill below).
                    Text(
                        text = if (activeAccount == null) {
                            "登录、扫码确认和本机会话集中管理"
                        } else {
                            accountName
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (!compact) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusPill(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Outlined.AccountCircle,
                        label = "账号",
                        value = accountName,
                        active = activeAccount != null,
                    )
                    StatusPill(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Outlined.Key,
                        label = "SML",
                        value = if (tokenReady) "已保存" else "未保存",
                        active = tokenReady,
                    )
                    StatusPill(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Outlined.QrCodeScanner,
                        label = "网页登录",
                        value = if (qrReady) "可确认" else "待准备",
                        active = qrReady,
                    )
                }
            } else {
                // Single compact status line; SML is the only token-availability surface in the header.
                StatusPill(
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Outlined.Key,
                    label = "SML",
                    value = buildString {
                        append(if (tokenReady) "已保存" else "未保存")
                        append(" · ")
                        append(if (qrReady) "网页可确认" else "网页待准备")
                    },
                    active = tokenReady || qrReady,
                )
            }
        }
    }
}

@Composable
private fun StatusPill(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    value: String,
    active: Boolean,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun StatusBanner(
    state: SynapseUiState,
    onDismiss: (() -> Unit)? = null,
) {
    if (!state.loading && state.status.isBlank() && state.error == null) return

    val isError = state.error != null
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
        shape = RoundedCornerShape(12.dp),
        color = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        border = BorderStroke(
            width = 1.dp,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = if (isError) Alignment.Top else Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when {
                state.loading -> CircularProgressIndicator(modifier = Modifier.size(20.dp))
                isError -> Icon(
                    imageVector = Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
                else -> Icon(
                    imageVector = Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                )
            }
            Text(
                text = state.error ?: state.status.ifBlank { "处理中..." },
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                softWrap = true,
                color = if (isError) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onSecondaryContainer
                },
            )
            if (!state.loading && onDismiss != null && (state.error != null || state.status.isNotBlank())) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "关闭提示",
                        tint = if (isError) {
                            MaterialTheme.colorScheme.onErrorContainer
                        } else {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun LoginPanel(
    state: SynapseUiState,
    viewModel: SynapseLoginViewModel,
) {
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val canSubmitLogin = !state.loading &&
        !state.turnstileConfigLoading &&
        state.turnstileConfigError == null &&
        state.username.isNotBlank() &&
        state.password.isNotBlank() &&
        (!state.requiresHumanVerification || state.turnstileVerified)

    PanelColumn(state = state, onDismissFeedback = viewModel::clearFeedback) {
        SectionTitle(
            text = "登录本客户端",
            subtitle = "签发本机 SML 令牌，用于静默登录和网页登录确认。",
            icon = Icons.AutoMirrored.Outlined.Login,
        )
        CredentialSummary(
            state = state,
            viewModel = viewModel,
            title = "本客户端授权信息",
        )
        if (!state.hasStoredAccount) {
            IllustratedEmptyState(
                illustration = DynamicColorImageVectors.download(),
                title = "首次使用",
                lines = listOf(
                    "先登录本客户端签发 SML 令牌，之后可用「网页登录」扫码确认电脑端。",
                    "已在网页完成二次验证时，也可在下方粘贴 JWT 直接授权本机。",
                ),
            )
        }
        OutlinedTextField(
            value = state.username,
            onValueChange = viewModel::updateUsername,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !state.loading,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
            ),
            label = { Text("用户名或邮箱") },
        )
        OutlinedTextField(
            value = state.password,
            onValueChange = viewModel::updatePassword,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !state.loading,
            visualTransformation = if (passwordVisible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    keyboardController?.hide()
                    if (canSubmitLogin) {
                        viewModel.login()
                    }
                },
            ),
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) {
                            Icons.Outlined.VisibilityOff
                        } else {
                            Icons.Outlined.Visibility
                        },
                        contentDescription = if (passwordVisible) "隐藏密码" else "显示密码",
                    )
                }
            },
            label = { Text("密码") },
        )
        OutlinedTextField(
            value = state.deviceName,
            onValueChange = viewModel::updateDeviceName,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !state.loading,
            supportingText = { Text("显示在网页端的设备名称，便于识别本机。") },
            label = { Text("设备名称") },
        )
        TurnstileVerificationPanel(
            state = state,
            onVerified = viewModel::handleTurnstileVerify,
            onExpired = viewModel::handleTurnstileExpire,
            onError = viewModel::handleTurnstileError,
            onRetryWidget = viewModel::retryTurnstile,
            onReloadConfig = viewModel::loadTurnstileConfig,
        )
        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = canSubmitLogin,
            onClick = {
                keyboardController?.hide()
                viewModel.login()
            },
        ) {
            if (state.loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text("登录中…")
            } else {
                ButtonLabel(Icons.AutoMirrored.Outlined.Login, "登录本客户端并签发令牌")
            }
        }
        Text(
            text = "这是登录本客户端；如需二次验证，完成 TOTP 或 Passkey 后才会保存客户端令牌。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        state.pendingTwoFactorChallenge?.let { challenge ->
            InfoCard(
                title = "本客户端登录需要二次验证",
                icon = Icons.Outlined.VerifiedUser,
                lines = listOf(
                    "账号：${challenge.user?.username ?: state.username}",
                    "验证方式：${challenge.methodLabel}",
                    "二次验证凭据：已接收",
                ),
            )
            if (challenge.methods.any { it.equals("Passkey", ignoreCase = true) }) {
                val context = LocalContext.current
                val activity = remember(context) { context.findActivity() }
                val passkeyClient = remember(context) { SynapsePasskeyCredentialClient(context) }
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.loading && activity != null,
                    onClick = {
                        val host = activity ?: return@Button
                        viewModel.startPasskeyAuthentication(host, passkeyClient)
                    },
                ) {
                    ButtonLabel(Icons.Outlined.Key, "使用通行密钥验证")
                }
                if (activity == null) {
                    Text(
                        text = "当前界面无法获取 Activity，暂不能唤起 Credential Manager。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            if (challenge.methods.any { it.equals("TOTP", ignoreCase = true) }) {
                OutlinedTextField(
                    value = state.totpCode,
                    onValueChange = viewModel::updateTotpCode,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !state.loading,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                        imeAction = ImeAction.Next,
                    ),
                    supportingText = { Text("输入身份验证器中的 6 位动态码。") },
                    label = { Text("TOTP 验证码") },
                )
                OutlinedTextField(
                    value = state.backupCode,
                    onValueChange = viewModel::updateBackupCode,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !state.loading,
                    supportingText = { Text("无法使用动态码时，可改用备用恢复码。") },
                    label = { Text("备用恢复码") },
                )
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.loading && (state.totpCode.isNotBlank() || state.backupCode.isNotBlank()),
                    onClick = viewModel::verifyTotp,
                ) {
                    ButtonLabel(Icons.Outlined.VerifiedUser, "完成 TOTP 并登录本客户端")
                }
            }
        }
        state.passkeyOptions?.let { options ->
            InfoCard(
                title = if (options.discoverable) "Discoverable Passkey" else "Passkey 验证中",
                icon = Icons.Outlined.Key,
                lines = options.summaryLines + listOf(
                    "系统通行密钥界面应已自动弹出；若未弹出可点下方按钮重试。",
                    "不会在界面展示 challenge 或 credential id 原文。",
                ),
            )
            val context = LocalContext.current
            val activity = remember(context) { context.findActivity() }
            val passkeyClient = remember(context) { SynapsePasskeyCredentialClient(context) }
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.loading && activity != null,
                onClick = {
                    val host = activity ?: return@OutlinedButton
                    viewModel.authenticateWithPasskey(host, passkeyClient)
                },
            ) {
                ButtonLabel(Icons.Outlined.Key, "重新唤起系统通行密钥")
            }
        }
        // Passwordless discoverable entry is available even without a prior password 2FA challenge.
        if (state.pendingTwoFactorChallenge == null) {
            val context = LocalContext.current
            val activity = remember(context) { context.findActivity() }
            val passkeyClient = remember(context) { SynapsePasskeyCredentialClient(context) }
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.loading && activity != null,
                onClick = {
                    val host = activity ?: return@OutlinedButton
                    viewModel.startDiscoverablePasskeyAuthentication(host, passkeyClient)
                },
            ) {
                ButtonLabel(Icons.Outlined.Key, "使用通行密钥直接登录（无需密码）")
            }
        }
        if (state.googleAuthConfig.canSignIn || state.googleAuthConfigLoading || state.googleAuthConfigError != null) {
            SectionTitle(
                text = "使用 Google 账号登录",
                subtitle = "通过 Credential Manager 获取 Google ID Token，对接 Happy-TTS /api/auth/google。",
            )
            if (state.googleAuthConfigLoading) {
                Text(
                    text = "正在检查 Google 登录配置…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (state.googleAuthConfigError != null) {
                InfoCard(
                    icon = Icons.Outlined.WarningAmber,
                    title = "Google 登录配置不可用",
                    lines = listOf(
                        state.googleAuthConfigError ?: "无法加载 Google 登录配置。",
                        "可稍后重试，或继续使用密码 / 通行密钥登录。",
                    ),
                )
                OutlinedButton(
                    onClick = viewModel::loadGoogleAuthConfig,
                    enabled = !state.loading,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    ButtonLabel(Icons.Outlined.Security, "重新加载 Google 登录配置")
                }
            } else if (state.googleAuthConfig.canSignIn) {
                val context = LocalContext.current
                val activity = remember(context) { context.findActivity() }
                val googleClient = remember(context) { SynapseGoogleCredentialClient(context) }
                Button(
                    onClick = {
                        val host = activity ?: return@Button
                        viewModel.signInWithGoogle(host, googleClient)
                    },
                    enabled = !state.loading && activity != null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    ButtonLabel(Icons.Outlined.AccountCircle, "使用 Google 账号登录")
                }
                Text(
                    text = "将唤起系统 Google 账号选择；登录成功后自动签发本机 SML 令牌。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (state.linuxDoAuthConfig.canSignIn || state.linuxDoAuthConfigLoading || state.linuxDoAuthConfigError != null) {
            SectionTitle(
                text = "使用 Linux.do 登录",
                subtitle = "浏览器完成 OAuth 授权后，用 ticket 换取 JWT 并签发本机 SML 令牌。",
            )
            if (state.linuxDoAuthConfigLoading) {
                Text(
                    text = "正在检查 Linux.do 登录配置…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (state.linuxDoAuthConfigError != null) {
                InfoCard(
                    icon = Icons.Outlined.WarningAmber,
                    title = "Linux.do 登录配置不可用",
                    lines = listOf(
                        state.linuxDoAuthConfigError ?: "无法加载 Linux.do 登录配置。",
                        "可稍后重试，或继续使用密码 / Google / 通行密钥登录。",
                    ),
                )
                OutlinedButton(
                    onClick = viewModel::loadLinuxDoAuthConfig,
                    enabled = !state.loading,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    ButtonLabel(Icons.Outlined.Security, "重新加载 Linux.do 登录配置")
                }
            } else if (state.linuxDoAuthConfig.canSignIn) {
                val context = LocalContext.current
                Button(
                    onClick = {
                        val url = viewModel.linuxDoStartUrl("login")
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                            viewModel.markLinuxDoBrowserOpened()
                        }.onFailure { error ->
                            viewModel.reportLinuxDoBrowserOpenFailed(
                                SynapseFailureMessage.from(
                                    error = error,
                                    fallback = "无法打开浏览器进行 Linux.do 授权。",
                                    context = "Linux.do browser open",
                                ),
                            )
                        }
                    },
                    enabled = !state.loading,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    ButtonLabel(Icons.Outlined.AccountCircle, "使用 Linux.do 登录")
                }
                Text(
                    text = if (state.linuxDoBrowserOpened) {
                        "已打开授权页。完成后应自动回 App；若仍停留在浏览器，请点“打开 Synapse Mobile”或粘贴回调链接/ticket。"
                    } else {
                        "将打开系统浏览器访问 Happy-TTS /api/auth/linuxdo/start?client=synapse-android。授权完成后优先经 synapse:// 或 App Links 回 App；也可随时在下方粘贴回调链接或 ticket。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Always keep paste entry visible: App Links may fail, browser may open
                // outside this process, and users may already hold a ticket.
                var linuxDoCallbackInput by rememberSaveable { mutableStateOf("") }
                OutlinedTextField(
                    value = linuxDoCallbackInput,
                    onValueChange = { linuxDoCallbackInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    minLines = 2,
                    enabled = !state.loading,
                    label = { Text("Linux.do 回调链接或 ticket") },
                    supportingText = {
                        Text("可粘贴完整回调 URL（含 ticket=）、裸 query（ticket=...），或仅粘贴 ticket 字符串。")
                    },
                )
                OutlinedButton(
                    onClick = {
                        val raw = linuxDoCallbackInput.trim()
                        if (raw.isBlank()) return@OutlinedButton
                        if (
                            raw.contains("://") ||
                            raw.contains("ticket=") ||
                            raw.contains("error=") ||
                            raw.contains("sessionToken=") ||
                            raw.startsWith("?")
                        ) {
                            viewModel.completeLinuxDoFromCallback(raw)
                        } else {
                            viewModel.exchangeLinuxDoTicket(raw)
                        }
                    },
                    enabled = !state.loading && linuxDoCallbackInput.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    ButtonLabel(Icons.Outlined.Key, "提交 Linux.do 登录票据")
                }
            }
        }

        SectionTitle(
            text = "用网页端 JWT 登录本客户端",
            subtitle = "适合已在网页端完成二次验证后手动授权本机。",
            icon = Icons.Outlined.Security,
        )
        val jwtClipboard = LocalClipboard.current
        val jwtScope = rememberCoroutineScope()
        OutlinedTextField(
            value = state.manualJwt,
            onValueChange = viewModel::updateManualJwt,
            modifier = Modifier
                .fillMaxWidth()
                .height(112.dp),
            minLines = 2,
            enabled = !state.loading,
            supportingText = { Text("仅粘贴完整 JWT，应用不会完整展示令牌内容。") },
            label = { Text("网页端或二次验证后的 JWT") },
            trailingIcon = {
                IconButton(
                    enabled = !state.loading,
                    onClick = {
                        jwtScope.launch {
                            val clip = jwtClipboard.getClipEntry()?.clipData
                            val pasted = clip
                                ?.takeIf { it.itemCount > 0 }
                                ?.getItemAt(0)
                                ?.text
                                ?.toString()
                                ?.trim()
                                .orEmpty()
                            if (pasted.isNotBlank()) {
                                viewModel.updateManualJwt(pasted)
                            }
                        }
                    },
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ContentPaste,
                        contentDescription = "从剪贴板粘贴 JWT",
                    )
                }
            },
        )
        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.loading && state.manualJwt.isNotBlank(),
            onClick = viewModel::issueClientTokenFromJwt,
        ) {
            ButtonLabel(Icons.Outlined.Security, "用 JWT 登录本客户端")
        }
    }
}

@Composable
private fun QrPanel(
    state: SynapseUiState,
    viewModel: SynapseLoginViewModel,
) {
    var showConfirmWebLogin by rememberSaveable { mutableStateOf(false) }
    val qrClipboard = LocalClipboard.current
    val qrScope = rememberCoroutineScope()
    val qrPayloadHelperText = state.qrPayloadError
        ?: if (state.hasUsableQrPayload) {
            "二维码有效，确认前请核对目标站点和账号。"
        } else {
            "扫描或粘贴 synapse://mobile-login 二维码 payload。"
        }
    val canConfirmWebLogin =
        !state.loading && state.hasUsableQrPayload && state.hasAnyWebLoginCredential

    PanelColumn(state = state, onDismissFeedback = viewModel::clearFeedback) {
        SectionTitle(
            text = "确认网页登录",
            subtitle = "扫描网页二维码后，核对目标站点并选择本机账号确认。",
            icon = Icons.Outlined.QrCodeScanner,
        )
        CredentialSummary(
            state = state,
            viewModel = viewModel,
            title = "网页登录使用的本客户端账号",
        )
        if (!state.hasAnyWebLoginCredential) {
            IllustratedEmptyState(
                illustration = DynamicColorImageVectors.videoSteaming(),
                title = "需要先登录本客户端",
                lines = listOf(
                    "确认网页登录前，请先在「本客户端登录」完成授权并签发令牌。",
                    "至少需要 JWT 或 SML 登录令牌之一。",
                ),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.loading,
                onClick = { viewModel.setScannerVisible(!state.showScanner) },
            ) {
                ButtonLabel(
                    icon = if (state.showScanner) Icons.Outlined.Close else Icons.Outlined.CameraAlt,
                    text = if (state.showScanner) "关闭相机" else "扫描网页登录二维码",
                )
            }
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.loading && state.hasUsableQrPayload,
                onClick = viewModel::markScanned,
            ) {
                ButtonLabel(Icons.Outlined.CheckCircle, "标记网页登录已扫码")
            }
        }

        if (state.showScanner) {
            PermissionAwareQrScanner(
                modifier = Modifier.fillMaxWidth(),
                onQrCode = viewModel::acceptScannedPayload,
            )
        }

        OutlinedTextField(
            value = state.manualQrPayload,
            onValueChange = viewModel::updateManualQrPayload,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 128.dp),
            minLines = 3,
            enabled = !state.loading,
            isError = state.qrPayloadError != null,
            supportingText = { Text(qrPayloadHelperText) },
            label = { Text("网页登录二维码 payload") },
            trailingIcon = {
                IconButton(
                    enabled = !state.loading,
                    onClick = {
                        qrScope.launch {
                            val clip = qrClipboard.getClipEntry()?.clipData
                            val pasted = clip
                                ?.takeIf { it.itemCount > 0 }
                                ?.getItemAt(0)
                                ?.text
                                ?.toString()
                                ?.trim()
                                .orEmpty()
                            if (pasted.isNotBlank()) {
                                viewModel.updateManualQrPayload(pasted)
                            }
                        }
                    },
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ContentPaste,
                        contentDescription = "从剪贴板粘贴二维码 payload",
                    )
                }
            },
        )

        if (state.manualQrPayload.isNotBlank()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    enabled = !state.loading,
                    onClick = viewModel::clearQrPayload,
                ) {
                    ButtonLabel(Icons.Outlined.DeleteOutline, "清除二维码")
                }
            }
        }

        state.parsedQrPayload?.let { payload ->
            InfoCard(
                title = "网页登录二维码详情",
                icon = Icons.Outlined.QrCodeScanner,
                lines = listOf(
                    "目标站点：${payload.apiBaseUrl}",
                    "Session：${payload.sessionId}",
                    "过期时间：${SynapseTokenExpiry.formatInstantDisplay(payload.expiresAt)}",
                    "状态：${if (payload.isExpired) "已过期，请重新扫码" else "有效，可继续确认"}",
                ),
            )
        }

        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = canConfirmWebLogin,
            onClick = { showConfirmWebLogin = true },
        ) {
            if (state.loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text("确认中…")
            } else {
                ButtonLabel(Icons.AutoMirrored.Outlined.Login, "确认登录网页端")
            }
        }

        if (showConfirmWebLogin) {
            state.parsedQrPayload?.let { payload ->
                val activeName = state.credentials.activeAccount?.displayName
                    ?: state.credentials.displayName
                    ?: "当前账号"
                ConfirmActionDialog(
                    title = "确认登录网页端",
                    message = "将使用「$activeName」确认登录：\n${payload.apiBaseUrl}\n\n请确认这是你本人发起的网页登录请求。",
                    confirmText = "确认登录",
                    confirmIcon = Icons.AutoMirrored.Outlined.Login,
                    destructive = false,
                    onConfirm = {
                        showConfirmWebLogin = false
                        viewModel.confirmQrLogin()
                    },
                    onDismiss = { showConfirmWebLogin = false },
                )
            }
        }

        if (state.showWebLoginAccountPicker) {
            WebLoginAccountPickerDialog(
                state = state,
                onSelectAccount = viewModel::confirmQrLoginWithAccount,
                onDismiss = viewModel::dismissWebLoginAccountPicker,
            )
        }
    }
}

@Composable
private fun WebLoginAccountPickerDialog(
    state: SynapseUiState,
    onSelectAccount: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val targetSite = state.parsedQrPayload?.apiBaseUrl ?: "当前网页登录请求"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择网页登录账号") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "目标站点：$targetSite",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "请选择用于继续网页登录的本客户端账号。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                state.credentials.accounts.forEach { account ->
                    WebLoginAccountChoice(
                        account = account,
                        active = account.accountId == state.credentials.activeAccountId,
                        enabled = !state.loading && (account.hasJwt || account.hasClientLoginToken),
                        onSelect = { onSelectAccount(account.accountId) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun WebLoginAccountChoice(
    account: StoredSynapseAccount,
    active: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
) {
    val credentialLabel = when {
        account.hasJwt && account.hasClientLoginToken -> "可用凭据：JWT + SML"
        account.hasJwt -> "可用凭据：JWT"
        account.hasClientLoginToken -> "可用凭据：SML"
        else -> "无可用网页登录凭据"
    }
    OutlinedButton(
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        onClick = onSelect,
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(14.dp),
    ) {
        Icon(
            imageVector = if (active) Icons.Outlined.VerifiedUser else Icons.Outlined.AccountCircle,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.size(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = if (active) "当前：${account.displayName}" else account.displayName,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = account.email ?: account.userId ?: account.accountId,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = credentialLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SessionPanel(
    state: SynapseUiState,
    viewModel: SynapseLoginViewModel,
) {
    var showRevokeConfirmation by rememberSaveable { mutableStateOf(false) }
    var showClearConfirmation by rememberSaveable { mutableStateOf(false) }

    PanelColumn(state = state, onDismissFeedback = viewModel::clearFeedback) {
        SectionTitle(
            text = "本地会话",
            subtitle = "管理当前设备上的授权状态和本机令牌。",
            icon = Icons.Outlined.Devices,
        )
        CredentialSummary(
            state = state,
            viewModel = viewModel,
            title = "设备与凭据",
        )
        if (!state.hasStoredAccount) {
            IllustratedEmptyState(
                illustration = DynamicColorImageVectors.videoFiles(),
                title = "暂无本地会话",
                lines = listOf(
                    "还没有保存账号。请先到「本客户端登录」完成授权。",
                    "登录成功后可在这里自动登录、撤销令牌或清理凭据。",
                ),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.loading && state.hasCurrentClientLoginToken,
                onClick = viewModel::silentLogin,
            ) {
                if (state.loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("处理中…")
                } else {
                    ButtonLabel(Icons.AutoMirrored.Outlined.Login, "自动登录本客户端")
                }
            }
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.loading && state.hasCurrentClientLoginToken,
                onClick = { showRevokeConfirmation = true },
            ) {
                ButtonLabel(Icons.Outlined.Key, "撤销本客户端令牌")
            }
        }
        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.loading && state.hasStoredAccount,
            onClick = { showClearConfirmation = true },
        ) {
            ButtonLabel(Icons.Outlined.DeleteOutline, "清理本地凭据")
        }
        OssCreditsFooter()
    }

    if (showRevokeConfirmation) {
        ConfirmActionDialog(
            title = "撤销本客户端令牌",
            message = "撤销后当前账号需要重新完成授权登录，才能继续静默登录或确认网页登录。",
            confirmText = "撤销",
            onConfirm = {
                showRevokeConfirmation = false
                viewModel.revokeClientToken()
            },
            onDismiss = { showRevokeConfirmation = false },
        )
    }

    if (showClearConfirmation) {
        ConfirmActionDialog(
            title = "清理本地凭据",
            message = "清理后当前账号保存在本机的 JWT 和 SML 登录令牌会被移除。",
            confirmText = "清理",
            onConfirm = {
                showClearConfirmation = false
                viewModel.clearCredentials()
            },
            onDismiss = { showClearConfirmation = false },
        )
    }
}

@Composable
private fun CredentialSummary(
    state: SynapseUiState,
    viewModel: SynapseLoginViewModel,
    title: String,
) {
    val accounts = state.credentials.accounts
    if (accounts.isEmpty()) return

    val active = state.credentials.activeAccount ?: accounts.first()
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            // Tighter padding than the old 16dp; token availability is already shown once
            // in SynapseAppHeader's SML pill — avoid a second "令牌·可用" chip here.
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Key,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.secondary,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = active.displayName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            CopyableLine("当前账号", active.displayName)
            CopyableLine("User ID", active.userId ?: "未返回")
            CopyableLine("邮箱", active.email ?: "未返回")
            CopyableLine("当前设备 ID", state.deviceId)
            CopyableLine(
                label = "SML 登录令牌",
                value = active.clientLoginTokenPreview ?: "未保存",
                copyValue = "",
            )
            CopyableLine(
                label = "SML 过期时间",
                value = SynapseTokenExpiry.formatDisplay(active.clientLoginTokenExpiresAt) ?: "未保存",
            )
            Text(
                text = when {
                    active.clientLoginToken == null && active.clientLoginTokenExpiresAt != null ->
                        "SML 登录令牌已过期并已自动吊销，请重新完成授权登录。"
                    active.isClientLoginTokenExpired ->
                        "SML 登录令牌已过期，请重新完成授权登录。"
                    active.hasClientLoginToken ->
                        "SML 登录令牌有效期以服务端签发时间为准。"
                    else ->
                        "当前账号尚未保存 SML 登录令牌。"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (accounts.size > 1) {
                SectionTitle(
                    text = "已登录账号",
                    subtitle = "选择用于网页登录确认的本机账号。",
                    icon = Icons.Outlined.AccountCircle,
                )
                accounts.forEach { account ->
                    AccountSelectorRow(
                        account = account,
                        active = account.accountId == active.accountId,
                        onSelect = { viewModel.selectAccount(account.accountId) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountSelectorRow(
    account: StoredSynapseAccount,
    active: Boolean,
    onSelect: () -> Unit,
) {
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (active) "当前：${account.displayName}" else account.displayName,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            OutlinedButton(
                onClick = {
                    coroutineScope.launch {
                        clipboard.setClipEntry(account.displayName.toClipEntry("Synapse account"))
                    }
                },
            ) {
                ButtonLabel(Icons.Outlined.ContentCopy, "复制")
            }
            OutlinedButton(
                enabled = !active,
                onClick = onSelect,
            ) {
                ButtonLabel(Icons.Outlined.SwapHoriz, "切换")
            }
        }
    }
}

@Composable
private fun CopyableLine(
    label: String,
    value: String,
    copyValue: String = value,
) {
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()
    val canCopy = copyValue.isNotBlank() && copyValue != "未返回" && copyValue != "未保存"
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            OutlinedButton(
                enabled = canCopy,
                onClick = {
                    coroutineScope.launch {
                        clipboard.setClipEntry(copyValue.toClipEntry(label))
                    }
                },
            ) {
                ButtonLabel(Icons.Outlined.ContentCopy, "复制")
            }
        }
    }
}

private fun String.toClipEntry(label: String): ClipEntry =
    ClipEntry(ClipData.newPlainText(label, this))

@Composable
private fun ConfirmActionDialog(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmIcon: ImageVector = Icons.Outlined.DeleteOutline,
    destructive: Boolean = true,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = if (destructive) Icons.Outlined.WarningAmber else Icons.Outlined.VerifiedUser,
                contentDescription = null,
                tint = if (destructive) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
        },
        title = { Text(title) },
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                ButtonLabel(confirmIcon, confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun PanelColumn(
    state: SynapseUiState,
    onDismissFeedback: () -> Unit,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // Prefer compact header on typical phones (portrait ~640–800 dp height).
        val compactHeader = maxHeight < 640.dp

        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(PaddingValues(8.dp)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 760.dp)
                    .align(Alignment.TopCenter),
                verticalArrangement = Arrangement.spacedBy(if (compactHeader) 4.dp else 6.dp),
            ) {
                SynapseAppHeader(
                    state = state,
                    compact = compactHeader,
                )
                StatusBanner(
                    state = state,
                    onDismiss = onDismissFeedback,
                )
                content()
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun SectionTitle(
    text: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ButtonLabel(
    icon: ImageVector,
    text: String,
) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.size(18.dp),
    )
    Spacer(modifier = Modifier.size(8.dp))
    Text(text)
}

@Composable
private fun InfoCard(
    title: String,
    lines: List<String>,
    icon: ImageVector = Icons.Outlined.Info,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            lines.forEach { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun IllustratedEmptyState(
    illustration: ImageVector,
    title: String,
    lines: List<String>,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                imageVector = illustration,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16 / 9f)
                    .padding(horizontal = 8.dp),
                contentScale = ContentScale.Fit,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth(),
            )
            lines.forEach { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun OssCreditsFooter() {
    val uriHandler = LocalUriHandler.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                imageVector = DynamicColorImageVectors.coder(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth(0.72f)
                    .aspectRatio(16 / 9f),
                contentScale = ContentScale.Fit,
            )
            Text(
                text = "插画致谢",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "空状态插画基于 unDraw（Katerina Limpitsouni），使用 unDraw License。主题色随 Material 3 动态适配。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "构建 ${BuildConfig.VERSION_NAME} · ${BuildConfig.SHORT_HASH} · ${BuildConfig.BUILD_TIME}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(
                onClick = { uriHandler.openUri("https://undraw.co/") },
                modifier = Modifier.align(Alignment.Start),
            ) {
                Text("打开 unDraw")
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}


