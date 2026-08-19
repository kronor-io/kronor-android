package io.kronor.component.mobilepay

import android.annotation.SuppressLint
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle.Event.*
import androidx.lifecycle.viewmodel.compose.viewModel
import io.kronor.api.PaymentConfiguration
import io.kronor.api.PaymentMethod
import io.kronor.api.PaymentEvent
import io.kronor.component.webview_payment_gateway.WebviewGatewayComponent
import io.kronor.component.webview_payment_gateway.WebviewGatewayViewModel
import io.kronor.component.webview_payment_gateway.WebviewGatewayViewModelFactory

typealias MobilePayViewModel = WebviewGatewayViewModel

@Composable
@Deprecated("Pass PaymentConfiguration directly to MobilePayComponent")
fun mobilePayViewModel(mobilePayConfiguration: PaymentConfiguration): MobilePayViewModel {
    return viewModel(factory = WebviewGatewayViewModelFactory(mobilePayConfiguration, PaymentMethod.MobilePay))
}

@Composable
fun MobilePayComponent(
    configuration: PaymentConfiguration,
    onResult: (PaymentEvent) -> Unit,
    modifier: Modifier = Modifier.fillMaxSize(),
    redirectIntent: Intent? = null,
    onRedirectHandled: (Intent) -> Unit = {},
) {
    WebviewGatewayComponent(
        configuration = configuration,
        paymentMethod = PaymentMethod.MobilePay,
        onResult = onResult,
        modifier = modifier,
        redirectIntent = redirectIntent,
        onRedirectHandled = onRedirectHandled,
    )
}

@Deprecated("Pass PaymentConfiguration and onResult to MobilePayComponent")
@Suppress("DEPRECATION")
@SuppressLint("ComposeViewModelForwarding")
@Composable
fun MobilePayComponent(
    viewModel: MobilePayViewModel,
    modifier: Modifier = Modifier.fillMaxSize()
) {
    WebviewGatewayComponent(viewModel = viewModel, modifier = modifier)
}
