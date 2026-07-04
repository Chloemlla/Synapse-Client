package com.synapse.mobile.ui

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONObject

@Composable
internal fun TurnstileVerificationPanel(
    state: SynapseUiState,
    onVerified: (String) -> Unit,
    onExpired: () -> Unit,
    onError: () -> Unit,
    onRetryWidget: () -> Unit,
    onReloadConfig: () -> Unit,
) {
    when {
        state.turnstileConfigLoading -> {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("人机验证", style = MaterialTheme.typography.titleSmall)
                    Text("正在加载人机验证配置。", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        state.turnstileConfigError != null -> {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("人机验证配置加载失败", style = MaterialTheme.typography.titleSmall)
                    Text(state.turnstileConfigError, style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = onReloadConfig) {
                        Text("重新加载")
                    }
                }
            }
        }

        state.requiresHumanVerification -> {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("人机验证", style = MaterialTheme.typography.titleSmall)
                    TurnstileVerificationView(
                        siteKey = state.turnstileConfig.siteKey.orEmpty(),
                        pageBaseUrl = state.turnstilePageBaseUrl,
                        refreshKey = state.turnstileWidgetKey,
                        onVerified = onVerified,
                        onExpired = onExpired,
                        onError = onError,
                    )
                    when {
                        state.turnstileVerified -> {
                            Text(
                                text = "人机验证通过",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }

                        state.turnstileError -> {
                            Text(
                                text = "验证失败，请重新验证。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                            OutlinedButton(onClick = onRetryWidget) {
                                Text("重新验证")
                            }
                        }
                    }
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun TurnstileVerificationView(
    siteKey: String,
    pageBaseUrl: String,
    refreshKey: Int,
    onVerified: (String) -> Unit,
    onExpired: () -> Unit,
    onError: () -> Unit,
) {
    val bridge = remember { TurnstileBridge() }
    bridge.onVerifiedCallback = onVerified
    bridge.onExpiredCallback = onExpired
    bridge.onErrorCallback = onError

    val loadKey = TurnstileLoadKey(siteKey = siteKey, pageBaseUrl = pageBaseUrl, refreshKey = refreshKey)
    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .height(88.dp),
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(Color.TRANSPARENT)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.javaScriptCanOpenWindowsAutomatically = false
                webViewClient = WebViewClient()
                webChromeClient = WebChromeClient()
                addJavascriptInterface(bridge, TURNSTILE_BRIDGE_NAME)
            }
        },
        update = { webView ->
            if (webView.tag != loadKey) {
                webView.tag = loadKey
                webView.loadDataWithBaseURL(
                    pageBaseUrl.trim().trimEnd('/').ifBlank { DEFAULT_TURNSTILE_BASE_URL },
                    buildTurnstileHtml(siteKey),
                    "text/html",
                    "UTF-8",
                    null,
                )
            }
        },
    )
}

private data class TurnstileLoadKey(
    val siteKey: String,
    val pageBaseUrl: String,
    val refreshKey: Int,
)

private class TurnstileBridge {
    private val mainHandler = Handler(Looper.getMainLooper())

    var onVerifiedCallback: (String) -> Unit = {}
    var onExpiredCallback: () -> Unit = {}
    var onErrorCallback: () -> Unit = {}

    @JavascriptInterface
    fun onVerify(token: String) {
        mainHandler.post { onVerifiedCallback(token) }
    }

    @JavascriptInterface
    fun onExpire() {
        mainHandler.post { onExpiredCallback() }
    }

    @JavascriptInterface
    fun onError() {
        mainHandler.post { onErrorCallback() }
    }
}

private fun buildTurnstileHtml(siteKey: String): String {
    val quotedSiteKey = JSONObject.quote(siteKey)
    return """
        <!doctype html>
        <html>
        <head>
          <meta name="viewport" content="width=device-width, initial-scale=1.0">
          <script src="https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit" async defer></script>
          <style>
            html, body {
              margin: 0;
              padding: 0;
              background: transparent;
              overflow: hidden;
            }
            #turnstile-container {
              min-height: 78px;
              display: flex;
              align-items: center;
              justify-content: flex-start;
            }
          </style>
        </head>
        <body>
          <div id="turnstile-container"></div>
          <script>
            (function () {
              var rendered = false;
              function notifyError() {
                if (window.$TURNSTILE_BRIDGE_NAME) {
                  window.$TURNSTILE_BRIDGE_NAME.onError();
                }
              }
              function renderTurnstile() {
                if (rendered) return;
                if (!window.turnstile) {
                  window.setTimeout(renderTurnstile, 100);
                  return;
                }
                rendered = true;
                try {
                  window.turnstile.render('#turnstile-container', {
                    sitekey: $quotedSiteKey,
                    theme: 'light',
                    size: 'normal',
                    callback: function (token) {
                      window.$TURNSTILE_BRIDGE_NAME.onVerify(token || '');
                    },
                    'expired-callback': function () {
                      window.$TURNSTILE_BRIDGE_NAME.onExpire();
                    },
                    'error-callback': function () {
                      notifyError();
                    }
                  });
                } catch (error) {
                  notifyError();
                }
              }
              window.addEventListener('load', renderTurnstile);
              renderTurnstile();
            })();
          </script>
        </body>
        </html>
    """.trimIndent()
}

private const val TURNSTILE_BRIDGE_NAME = "SynapseTurnstile"
private const val DEFAULT_TURNSTILE_BASE_URL = "https://tts.chloemlla.com"
