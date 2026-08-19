package io.kronor.component.googlepay

import android.annotation.SuppressLint
import android.content.Intent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import io.kronor.api.PaymentConfiguration
import io.kronor.api.PaymentMethod
import io.kronor.api.PaymentEvent
import io.kronor.component.webview_payment_gateway.WebviewGatewayComponent
import io.kronor.component.webview_payment_gateway.WebviewGatewayViewModel
import io.kronor.component.webview_payment_gateway.WebviewGatewayViewModelFactory

typealias GooglePayViewModel = WebviewGatewayViewModel

@Composable
@Deprecated("Pass PaymentConfiguration directly to GooglePayComponent")
fun googlePayViewModel(googlePayConfiguration: PaymentConfiguration): GooglePayViewModel {
    return viewModel(factory = WebviewGatewayViewModelFactory(googlePayConfiguration, PaymentMethod.GooglePay))
}

@Composable
fun GooglePayComponent(
    configuration: PaymentConfiguration,
    onResult: (PaymentEvent) -> Unit,
    modifier: Modifier = Modifier.fillMaxSize(),
    redirectIntent: Intent? = null,
    onRedirectHandled: (Intent) -> Unit = {},
) {
    WebviewGatewayComponent(
        configuration = configuration,
        paymentMethod = PaymentMethod.GooglePay,
        onResult = onResult,
        modifier = modifier,
        redirectIntent = redirectIntent,
        onRedirectHandled = onRedirectHandled,
    )
}

@Deprecated("Pass PaymentConfiguration and onResult to GooglePayComponent")
@Suppress("DEPRECATION")
@SuppressLint("ComposeViewModelForwarding")
@Composable
fun GooglePayComponent(
    viewModel: GooglePayViewModel,
    modifier: Modifier = Modifier.fillMaxSize()
) {
    WebviewGatewayComponent(viewModel = viewModel, modifier = modifier)
}
