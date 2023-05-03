package io.kronor.component.mobilepay

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle.Event.*
import androidx.lifecycle.viewmodel.compose.viewModel
import io.kronor.api.PaymentConfiguration
import io.kronor.component.webview_payment_gateway.WebviewGatewayComponent
import io.kronor.component.webview_payment_gateway.WebviewGatewayViewModel
import io.kronor.component.webview_payment_gateway.WebviewGatewayViewModelFactory

typealias MobilePayViewModel = WebviewGatewayViewModel

@Composable
fun mobilePayViewModel(mobilePayConfiguration: PaymentConfiguration): MobilePayViewModel {
    return viewModel(factory = WebviewGatewayViewModelFactory(mobilePayConfiguration))
}

@SuppressLint("ComposeViewModelForwarding")
@Composable
fun MobilePayComponent(
    viewModel: MobilePayViewModel,
    modifier: Modifier = Modifier.fillMaxSize()
) {
    WebviewGatewayComponent(viewModel = viewModel, modifier = modifier)
}