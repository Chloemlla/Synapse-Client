package com.chloemlla.synapse.mobile

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.chloemlla.lumen.crash.CrashBreadcrumbs
import com.chloemlla.lumen.crash.ui.LumenCrashReportScreen
import com.chloemlla.synapse.mobile.core.auth.SynapseAuthRepository
import com.chloemlla.synapse.mobile.ui.SynapseLoginViewModel
import com.chloemlla.synapse.mobile.ui.SynapseMobileApp
import com.chloemlla.synapse.mobile.ui.SynapseMobileTheme

class MainActivity : ComponentActivity() {
    private lateinit var viewModel: SynapseLoginViewModel

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* Live Updates degrade silently when denied. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching { CrashBreadcrumbs.record("MainActivity.onCreate") }
        maybeRequestNotificationPermission()

        val app = application as SynapseApplication
        var initialStartupReport = app.loadPendingCrashReport()
        val initialViewModel = if (initialStartupReport == null) {
            createSynapseViewModel(app)?.also { viewModel = it }
        } else {
            null
        }
        if (initialStartupReport == null && initialViewModel == null) {
            initialStartupReport = app.loadPendingCrashReport()
        }

        if (::viewModel.isInitialized) {
            intent.dataString?.let(viewModel::handleIncomingUri)
            viewModel.consumeLiveUpdateIntent(intent)
        }

        setContent {
            var startupReport by remember { mutableStateOf(initialStartupReport) }
            SynapseMobileTheme {
                val report = startupReport
                if (report != null) {
                    // Prefer the SDK crash UI. If it cannot render (e.g. fail-closed integrity),
                    // clear the pending report and fall back to the normal app surface.
                    val showCrashUi = remember(report.reportId) {
                        runCatching {
                            // Touch a cheap SDK path so integrity failures are caught before composition.
                            report.toClipboardText()
                            true
                        }.getOrElse {
                            app.clearPendingCrashReport()
                            false
                        }
                    }
                    if (showCrashUi) {
                        LumenCrashReportScreen(
                            report = report,
                            onContinue = {
                                app.clearPendingCrashReport()
                                startupReport = null
                                if (initialViewModel == null) recreate()
                            },
                        )
                    } else if (initialViewModel != null) {
                        SynapseMobileApp(viewModel = initialViewModel)
                    } else {
                        recreate()
                    }
                } else {
                    initialViewModel?.let { readyViewModel ->
                        SynapseMobileApp(viewModel = readyViewModel)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        runCatching { CrashBreadcrumbs.record("MainActivity.onNewIntent") }
        setIntent(intent)
        if (::viewModel.isInitialized) {
            intent.dataString?.let(viewModel::handleIncomingUri)
            viewModel.consumeLiveUpdateIntent(intent)
        }
    }

    private fun createSynapseViewModel(app: SynapseApplication): SynapseLoginViewModel? {
        return try {
            val repository = SynapseAuthRepository(
                context = applicationContext,
                defaultBaseUrl = BuildConfig.SYNAPSE_API_BASE_URL,
            )
            ViewModelProvider(
                this,
                SynapseLoginViewModel.Factory(
                    repository = repository,
                    liveUpdateNotifier = app.liveUpdateNotifier,
                ),
            )[SynapseLoginViewModel::class.java]
        } catch (throwable: Throwable) {
            app.recordStartupCrash(throwable)
            null
        }
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
