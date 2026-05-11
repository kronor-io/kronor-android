package io.kronor.component.googlepay

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import io.kronor.api.PaymentConfiguration
import io.kronor.api.PaymentMethod
import io.kronor.component.webview_payment_gateway.WebviewGatewayComponent
import io.kronor.component.webview_payment_gateway.WebviewGatewayViewModel
import io.kronor.component.webview_payment_gateway.WebviewGatewayViewModelFactory

typealias GooglePayViewModel = WebviewGatewayViewModel

@Composable
fun googlePayViewModel(googlePayConfiguration: PaymentConfiguration): GooglePayViewModel {
    return viewModel(factory = WebviewGatewayViewModelFactory(googlePayConfiguration, PaymentMethod.GooglePay))
}

@SuppressLint("ComposeViewModelForwarding")
@Composable
fun GooglePayComponent(
    viewModel: GooglePayViewModel,
    modifier: Modifier = Modifier.fillMaxSize()
) {
    WebviewGatewayComponent(viewModel = viewModel, modifier = modifier)
}