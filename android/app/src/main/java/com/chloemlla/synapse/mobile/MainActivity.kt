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
import com.chloemlla.lumen.crash.LumenCrash
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
        CrashBreadcrumbs.record("MainActivity.onCreate")
        maybeRequestNotificationPermission()

        val app = application as SynapseApplication
        var initialStartupReport = LumenCrash.loadPendingReport()
        val initialViewModel = if (initialStartupReport == null) {
            createSynapseViewModel(app)?.also { viewModel = it }
        } else {
            null
        }
        if (initialStartupReport == null && initialViewModel == null) {
            initialStartupReport = LumenCrash.loadPendingReport()
        }

        if (::viewModel.isInitialized) {
            intent.dataString?.let(viewModel::handleIncomingUri)
        }

        setContent {
            var startupReport by remember { mutableStateOf(initialStartupReport) }
            SynapseMobileTheme {
                val report = startupReport
                if (report != null) {
                    LumenCrashReportScreen(
                        report = report,
                        onContinue = {
                            LumenCrash.clearPendingReport()
                            startupReport = null
                            if (initialViewModel == null) recreate()
                        },
                    )
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
        CrashBreadcrumbs.record("MainActivity.onNewIntent")
        setIntent(intent)
        if (::viewModel.isInitialized) {
            intent.dataString?.let(viewModel::handleIncomingUri)
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
