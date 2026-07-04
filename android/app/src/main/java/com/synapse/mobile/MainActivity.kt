package com.synapse.mobile

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import com.synapse.mobile.core.auth.SynapseAuthRepository
import com.synapse.mobile.ui.SynapseLoginViewModel
import com.synapse.mobile.ui.SynapseMobileApp
import com.synapse.mobile.ui.SynapseMobileTheme

class MainActivity : ComponentActivity() {
    private lateinit var viewModel: SynapseLoginViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val repository = SynapseAuthRepository(
            context = applicationContext,
            defaultBaseUrl = BuildConfig.SYNAPSE_API_BASE_URL,
            certificatePins = BuildConfig.SYNAPSE_CERTIFICATE_PINS,
            requireCertificatePins = BuildConfig.SYNAPSE_REQUIRE_CERTIFICATE_PINS,
        )
        viewModel = ViewModelProvider(
            this,
            SynapseLoginViewModel.Factory(repository),
        )[SynapseLoginViewModel::class.java]

        intent.dataString?.let(viewModel::acceptScannedPayload)

        setContent {
            SynapseMobileTheme {
                SynapseMobileApp(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.dataString?.let(viewModel::acceptScannedPayload)
    }
}
