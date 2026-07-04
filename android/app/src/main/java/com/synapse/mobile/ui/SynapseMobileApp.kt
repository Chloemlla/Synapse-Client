package com.synapse.mobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.synapse.mobile.core.auth.StoredSynapseAccount

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SynapseMobileApp(viewModel: SynapseLoginViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Synapse Mobile")
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            TabRow(selectedTabIndex = state.selectedTab.ordinal) {
                SynapseTab.entries.forEach { tab ->
                    Tab(
                        selected = state.selectedTab == tab,
                        onClick = { viewModel.selectTab(tab) },
                        text = { Text(tab.label) },
                    )
                }
            }

            StatusBanner(state = state)

            when (state.selectedTab) {
                SynapseTab.Login -> LoginPanel(state, viewModel)
                SynapseTab.Qr -> QrPanel(state, viewModel)
                SynapseTab.Session -> SessionPanel(state, viewModel)
            }
        }
    }
}

@Composable
private fun StatusBanner(state: SynapseUiState) {
    if (!state.loading && state.status.isBlank() && state.error == null) return

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp, 12.dp, 16.dp, 0.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                state.error != null -> MaterialTheme.colorScheme.errorContainer
                else -> MaterialTheme.colorScheme.secondaryContainer
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.loading) {
                CircularProgressIndicator()
            }
            Text(
                text = state.error ?: state.status.ifBlank { "处理中..." },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun LoginPanel(
    state: SynapseUiState,
    viewModel: SynapseLoginViewModel,
) {
    PanelColumn {
        SectionTitle("登录本客户端")
        CredentialSummary(
            state = state,
            viewModel = viewModel,
            title = "本客户端授权信息",
        )
        OutlinedTextField(
            value = state.username,
            onValueChange = viewModel::updateUsername,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("用户名或邮箱") },
        )
        OutlinedTextField(
            value = state.password,
            onValueChange = viewModel::updatePassword,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            label = { Text("密码") },
        )
        OutlinedTextField(
            value = state.deviceName,
            onValueChange = viewModel::updateDeviceName,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
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
            enabled = !state.loading &&
                !state.turnstileConfigLoading &&
                state.turnstileConfigError == null &&
                (!state.requiresHumanVerification || state.turnstileVerified),
            onClick = viewModel::login,
        ) {
            Text("登录本客户端并签发令牌")
        }
        Text(
            text = "这是登录本客户端；如需二次验证，完成 TOTP 或 Passkey 后才会保存客户端令牌。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        state.pendingTwoFactorChallenge?.let { challenge ->
            InfoCard(
                title = "本客户端登录需要二次验证",
                lines = listOf(
                    "账号：${challenge.user?.username ?: state.username}",
                    "验证方式：${challenge.methodLabel}",
                    "二次验证凭据：已接收",
                ),
            )
            if (challenge.methods.any { it.equals("Passkey", ignoreCase = true) }) {
                OutlinedButton(
                    enabled = !state.loading,
                    onClick = viewModel::startPasskeyAuthentication,
                ) {
                    Text("获取本客户端 Passkey 认证选项")
                }
            }
            if (challenge.methods.any { it.equals("TOTP", ignoreCase = true) }) {
                OutlinedTextField(
                    value = state.totpCode,
                    onValueChange = viewModel::updateTotpCode,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    label = { Text("TOTP 验证码") },
                )
                OutlinedTextField(
                    value = state.backupCode,
                    onValueChange = viewModel::updateBackupCode,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("备用恢复码") },
                )
                Button(
                    enabled = !state.loading && (state.totpCode.isNotBlank() || state.backupCode.isNotBlank()),
                    onClick = viewModel::verifyTotp,
                ) {
                    Text("完成 TOTP 并登录本客户端")
                }
            }
        }
        state.passkeyOptions?.let { options ->
            InfoCard(
                title = "Passkey 认证选项",
                lines = options.summaryLines,
            )
            OutlinedTextField(
                value = state.passkeyAssertionJson,
                onValueChange = viewModel::updatePasskeyAssertionJson,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(112.dp),
                minLines = 2,
                label = { Text("Passkey assertion response JSON") },
            )
            Button(
                enabled = !state.loading && state.passkeyAssertionJson.isNotBlank(),
                onClick = viewModel::finishPasskeyAuthentication,
            ) {
                Text("完成 Passkey 并登录本客户端")
            }
        }
        SectionTitle("用网页端 JWT 登录本客户端")
        OutlinedTextField(
            value = state.manualJwt,
            onValueChange = viewModel::updateManualJwt,
            modifier = Modifier
                .fillMaxWidth()
                .height(112.dp),
            minLines = 2,
            label = { Text("网页端或二次验证后的 JWT") },
        )
        OutlinedButton(
            enabled = !state.loading && state.manualJwt.isNotBlank(),
            onClick = viewModel::issueClientTokenFromJwt,
        ) {
            Text("用 JWT 登录本客户端")
        }
    }
}

@Composable
private fun QrPanel(
    state: SynapseUiState,
    viewModel: SynapseLoginViewModel,
) {
    PanelColumn {
        SectionTitle("确认网页登录")
        CredentialSummary(
            state = state,
            viewModel = viewModel,
            title = "网页登录使用的本客户端账号",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = !state.loading,
                onClick = { viewModel.setScannerVisible(!state.showScanner) },
            ) {
                Text(if (state.showScanner) "关闭相机" else "扫描网页登录二维码")
            }
            OutlinedButton(
                enabled = !state.loading,
                onClick = viewModel::markScanned,
            ) {
                Text("标记网页登录已扫码")
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
                .height(128.dp),
            minLines = 3,
            label = { Text("网页登录二维码 payload") },
        )

        state.parsedQrPayload?.let { payload ->
            InfoCard(
                title = "网页登录二维码详情",
                lines = listOf(
                    "目标站点：${payload.apiBaseUrl}",
                    "Session：${payload.sessionId}",
                    "过期时间：${payload.expiresAt}",
                    "是否过期：${if (payload.isExpired) "是" else "否"}",
                ),
            )
        }

        Button(
            enabled = !state.loading && state.manualQrPayload.isNotBlank(),
            onClick = viewModel::confirmQrLogin,
        ) {
            Text("确认登录网页端")
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
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
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
    PanelColumn {
        SectionTitle("本地会话")
        CredentialSummary(
            state = state,
            viewModel = viewModel,
            title = "设备与凭据",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = !state.loading,
                onClick = viewModel::silentLogin,
            ) {
                Text("自动登录本客户端")
            }
            OutlinedButton(
                enabled = !state.loading,
                onClick = viewModel::revokeClientToken,
            ) {
                Text("撤销本客户端令牌")
            }
        }
        OutlinedButton(
            enabled = !state.loading,
            onClick = viewModel::clearCredentials,
        ) {
            Text("清理本地凭据")
        }
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            CopyableLine("当前账号", active.displayName)
            CopyableLine("User ID", active.userId ?: "未返回")
            CopyableLine("邮箱", active.email ?: "未返回")
            CopyableLine("当前设备 ID", state.deviceId)
            CopyableLine("SML 登录令牌", active.clientLoginToken ?: "未保存")
            CopyableLine("SML 过期时间", active.clientLoginTokenExpiresAt ?: "未保存")
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
                Text("已登录账号", style = MaterialTheme.typography.titleSmall)
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
    val clipboard = LocalClipboardManager.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = if (active) "当前：${account.displayName}" else account.displayName,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedButton(
            onClick = { clipboard.setText(AnnotatedString(account.displayName)) },
        ) {
            Text("复制")
        }
        OutlinedButton(
            enabled = !active,
            onClick = onSelect,
        ) {
            Text("切换")
        }
    }
}

@Composable
private fun CopyableLine(label: String, value: String) {
    val clipboard = LocalClipboardManager.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = value,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedButton(
                enabled = value.isNotBlank() && value != "未返回" && value != "未保存",
                onClick = { clipboard.setText(AnnotatedString(value)) },
            ) {
                Text("复制")
            }
        }
    }
}

@Composable
private fun PanelColumn(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(PaddingValues(16.dp)),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        content()
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
    )
}

@Composable
private fun InfoCard(
    title: String,
    lines: List<String>,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            lines.forEach { line ->
                Text(line, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
