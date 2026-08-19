package io.kronor.component.vipps

import android.annotation.SuppressLint
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import io.kronor.api.PaymentConfiguration
import io.kronor.api.PaymentMethod
import io.kronor.api.PaymentEvent
import io.kronor.component.webview_payment_gateway.WebviewGatewayComponent
import io.kronor.component.webview_payment_gateway.WebviewGatewayViewModel
import io.kronor.component.webview_payment_gateway.WebviewGatewayViewModelFactory

typealias VippsViewModel = WebviewGatewayViewModel

@Composable
@Deprecated("Pass PaymentConfiguration directly to VippsComponent")
fun vippsViewModel(vippsConfiguration: PaymentConfiguration): VippsViewModel {
    return viewModel(factory = WebviewGatewayViewModelFactory(vippsConfiguration, PaymentMethod.Vipps))
}

@Composable
fun VippsComponent(
    configuration: PaymentConfiguration,
    onResult: (PaymentEvent) -> Unit,
    modifier: Modifier = Modifier.fillMaxSize(),
    redirectIntent: Intent? = null,
    onRedirectHandled: (Intent) -> Unit = {},
) {
    WebviewGatewayComponent(
        configuration = configuration,
        paymentMethod = PaymentMethod.Vipps,
        onResult = onResult,
        modifier = modifier,
        redirectIntent = redirectIntent,
        onRedirectHandled = onRedirectHandled,
    )
}

@Deprecated("Pass PaymentConfiguration and onResult to VippsComponent")
@Suppress("DEPRECATION")
@SuppressLint("ComposeViewModelForwarding")
@Composable
fun VippsComponent(
    viewModel: VippsViewModel,
    modifier: Modifier = Modifier.fillMaxSize()
) {
    WebviewGatewayComponent(viewModel = viewModel, modifier = modifier)
}
