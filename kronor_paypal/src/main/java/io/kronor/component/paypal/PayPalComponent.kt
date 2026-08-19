package io.kronor.component.paypal

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
import io.kronor.component.webview_payment_gateway.*

typealias PayPalViewModel = WebviewGatewayViewModel

@Composable
@Deprecated("Pass PaymentConfiguration directly to PayPalComponent")
fun paypalViewModel(paypalConfiguration: PaymentConfiguration): PayPalViewModel {
    return viewModel(factory = WebviewGatewayViewModelFactory(paypalConfiguration, PaymentMethod.PayPal))
}

@Composable
fun PayPalComponent(
    configuration: PaymentConfiguration,
    onResult: (PaymentEvent) -> Unit,
    modifier: Modifier = Modifier.fillMaxSize(),
    redirectIntent: Intent? = null,
    onRedirectHandled: (Intent) -> Unit = {},
) {
    WebviewGatewayComponent(
        configuration = configuration,
        paymentMethod = PaymentMethod.PayPal,
        onResult = onResult,
        modifier = modifier,
        redirectIntent = redirectIntent,
        onRedirectHandled = onRedirectHandled,
    )
}

@Deprecated("Pass PaymentConfiguration and onResult to PayPalComponent")
@Suppress("DEPRECATION")
@SuppressLint("ComposeViewModelForwarding")
@Composable
fun PayPalComponent(
    viewModel: PayPalViewModel,
    modifier : Modifier = Modifier.fillMaxSize()
) {
    WebviewGatewayComponent(viewModel = viewModel, modifier = modifier)
}
