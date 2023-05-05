package io.kronor.component.credit_card

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import io.kronor.api.PaymentConfiguration
import io.kronor.api.PaymentMethod
import io.kronor.component.webview_payment_gateway.*

typealias CreditCardViewModel = WebviewGatewayViewModel

@Composable
fun creditCardViewModel(creditCardConfiguration: PaymentConfiguration): CreditCardViewModel {
    return viewModel(factory = WebviewGatewayViewModelFactory(creditCardConfiguration, PaymentMethod.CreditCard))
}

@SuppressLint("ComposeViewModelForwarding")
@Composable
fun CreditCardComponent(
    viewModel: CreditCardViewModel,
    modifier : Modifier = Modifier.fillMaxSize()
) {
    WebviewGatewayComponent(viewModel = viewModel, modifier = modifier)
}