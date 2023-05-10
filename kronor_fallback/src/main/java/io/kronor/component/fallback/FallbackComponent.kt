package io.kronor.component.fallback


import android.annotation.SuppressLint
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle.Event.*
import androidx.lifecycle.viewmodel.compose.viewModel
import io.kronor.api.PaymentConfiguration
import io.kronor.api.PaymentMethod
import io.kronor.component.webview_payment_gateway.WebviewGatewayComponent
import io.kronor.component.webview_payment_gateway.WebviewGatewayViewModel
import io.kronor.component.webview_payment_gateway.WebviewGatewayViewModelFactory

typealias FallbackViewModel = WebviewGatewayViewModel

@Composable
fun fallbackViewModel(fallbackConfiguration: PaymentConfiguration, paymentMethod: String): FallbackViewModel {
    return viewModel(factory = WebviewGatewayViewModelFactory(fallbackConfiguration, PaymentMethod.Fallback(paymentMethod)))
}

@SuppressLint("ComposeViewModelForwarding")
@Composable
fun FallbackComponent(
    viewModel: FallbackViewModel,
    modifier: Modifier = Modifier
) {
    WebviewGatewayComponent(viewModel = viewModel, modifier = modifier)
}