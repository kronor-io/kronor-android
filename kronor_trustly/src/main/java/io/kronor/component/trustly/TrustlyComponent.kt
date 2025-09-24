package io.kronor.component.trustly

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import io.kronor.api.PaymentConfiguration
import io.kronor.api.PaymentMethod
import io.kronor.component.webview_payment_gateway.WebviewGatewayComponent
import io.kronor.component.webview_payment_gateway.WebviewGatewayViewModel
import io.kronor.component.webview_payment_gateway.WebviewGatewayViewModelFactory

typealias TrustlyViewModel = WebviewGatewayViewModel

@Composable
fun trustlyViewModel(trustlyConfiguration: PaymentConfiguration): TrustlyViewModel {
    return viewModel(factory = WebviewGatewayViewModelFactory(trustlyConfiguration, PaymentMethod.BankTransfer))
}

@SuppressLint("ComposeViewModelForwarding")
@Composable
fun BankTransferComponent(
    viewModel: TrustlyViewModel,
    modifier: Modifier = Modifier.fillMaxSize()
) {
    WebviewGatewayComponent(viewModel = viewModel, modifier = modifier)
}