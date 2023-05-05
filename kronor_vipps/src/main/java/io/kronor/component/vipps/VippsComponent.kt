package io.kronor.component.vipps

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

typealias VippsViewModel = WebviewGatewayViewModel

@Composable
fun vippsViewModel(vippsConfiguration: PaymentConfiguration): VippsViewModel {
    return viewModel(factory = WebviewGatewayViewModelFactory(vippsConfiguration, PaymentMethod.Vipps))
}

@SuppressLint("ComposeViewModelForwarding")
@Composable
fun VippsComponent(
    viewModel: VippsViewModel,
    modifier: Modifier = Modifier.fillMaxSize()
) {
    WebviewGatewayComponent(viewModel = viewModel, modifier = modifier)
}