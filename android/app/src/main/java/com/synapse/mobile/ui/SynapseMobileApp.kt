package com.synapse.mobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SynapseMobileApp(viewModel: SynapseLoginViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable { mutableStateOf(SynapseTab.Login) }

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
            TabRow(selectedTabIndex = selectedTab.ordinal) {
                SynapseTab.entries.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = { Text(tab.label) },
                    )
                }
            }

            StatusBanner(state = state)

            when (selectedTab) {
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
        Button(
            enabled = !state.loading,
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
    }
}

@Composable
private fun SessionPanel(
    state: SynapseUiState,
    viewModel: SynapseLoginViewModel,
) {
    PanelColumn {
        SectionTitle("本地会话")
        InfoCard(
            title = "设备与凭据",
            lines = listOf(
                "Device ID：${state.deviceId}",
                "当前账号：${state.credentials.username ?: state.credentials.email ?: "未登录"}",
                "JWT：${if (state.credentials.hasJwt) "已保存" else "未保存"}",
                "客户端登录令牌：${if (state.credentials.hasClientLoginToken) "已保存" else "未保存"}",
            ),
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
