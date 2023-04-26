package io.kronor.component.credit_card

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import io.kronor.component.webview_payment_gateway.*

typealias CreditCardViewModel = WebviewGatewayViewModel

@Composable
fun creditCardViewModel(creditCardConfiguration: CreditCardConfiguration): CreditCardViewModel {
    return viewModel(factory = WebviewGatewayViewModelFactory(creditCardConfiguration.toWebviewGatewayConfiguration()))
}

@SuppressLint("ComposeViewModelForwarding")
@Composable
fun CreditCardComponent(
    viewModel: CreditCardViewModel
) {
    WebviewGatewayComponent(viewModel = viewModel)
}