package io.kronor.component.mobilepay

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle.Event.*
import androidx.lifecycle.viewmodel.compose.viewModel
import io.kronor.component.webview_payment_gateway.WebviewGatewayComponent
import io.kronor.component.webview_payment_gateway.WebviewGatewayViewModel
import io.kronor.component.webview_payment_gateway.WebviewGatewayViewModelFactory

typealias MobilePayViewModel = WebviewGatewayViewModel

@Composable
fun mobilePayViewModel(mobilePayConfiguration: MobilePayConfiguration): MobilePayViewModel {
    return viewModel(factory = WebviewGatewayViewModelFactory(mobilePayConfiguration.toWebviewGatewayConfiguration()))
}

@SuppressLint("ComposeViewModelForwarding")
@Composable
fun MobilePayComponent(
    viewModel: MobilePayViewModel
) {
    WebviewGatewayComponent(viewModel = viewModel)
}