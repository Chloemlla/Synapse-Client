package com.synapse.mobile

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModelProvider
import com.synapse.mobile.core.crash.CrashBreadcrumbs
import com.synapse.mobile.core.auth.SynapseAuthRepository
import com.synapse.mobile.ui.CrashReportScreen
import com.synapse.mobile.ui.SynapseLoginViewModel
import com.synapse.mobile.ui.SynapseMobileApp
import com.synapse.mobile.ui.SynapseMobileTheme

class MainActivity : ComponentActivity() {
    private lateinit var viewModel: SynapseLoginViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashBreadcrumbs.record("MainActivity.onCreate")

        val app = application as SynapseApplication
        var initialStartupReport = app.startupCrashReport ?: app.crashReports.load()
        val initialViewModel = if (initialStartupReport == null) {
            createSynapseViewModel(app)?.also { viewModel = it }
        } else {
            null
        }
        if (initialStartupReport == null && initialViewModel == null) {
            initialStartupReport = app.startupCrashReport ?: app.crashReports.load()
        }

        if (::viewModel.isInitialized) {
            intent.dataString?.let(viewModel::handleIncomingUri)
        }

        setContent {
            var startupReport by remember { mutableStateOf(initialStartupReport) }
            SynapseMobileTheme {
                val report = startupReport
                if (report != null) {
                    CrashReportScreen(
                        report = report,
                        onContinue = {
                            app.clearStartupCrashReport()
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
                SynapseLoginViewModel.Factory(repository),
            )[SynapseLoginViewModel::class.java]
        } catch (throwable: Throwable) {
            app.recordStartupCrash(throwable)
            null
        }
    }
}
