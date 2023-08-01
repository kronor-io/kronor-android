package io.kronor.component.paypal

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import io.kronor.api.PaymentConfiguration
import io.kronor.api.PaymentMethod
import io.kronor.component.webview_payment_gateway.*

typealias PayPalViewModel = WebviewGatewayViewModel

@Composable
fun paypalViewModel(paypalConfiguration: PaymentConfiguration): PayPalViewModel {
    return viewModel(factory = WebviewGatewayViewModelFactory(paypalConfiguration, PaymentMethod.PayPal))
}

@SuppressLint("ComposeViewModelForwarding")
@Composable
fun PayPalComponent(
    viewModel: PayPalViewModel,
    modifier : Modifier = Modifier.fillMaxSize()
) {
    WebviewGatewayComponent(viewModel = viewModel, modifier = modifier)
}