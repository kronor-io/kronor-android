package io.kronor.component.avarda

import android.annotation.SuppressLint
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import io.kronor.api.BuyNowPayLaterProduct
import io.kronor.api.PaymentConfiguration
import io.kronor.api.PaymentMethod
import io.kronor.component.webview_payment_gateway.WebviewGatewayComponent
import io.kronor.component.webview_payment_gateway.WebviewGatewayViewModel
import io.kronor.component.webview_payment_gateway.WebviewGatewayViewModelFactory

typealias AvardaViewModel = WebviewGatewayViewModel

/**
 * Creates the view model for an Avarda buy-now-pay-later payment.
 *
 * The payment runs on Kronor's hosted payment page: it collects the customer's national
 * identification number, creates the payment, and redirects to Avarda's checkout for identification.
 */
@Composable
fun avardaViewModel(
    avardaConfiguration: PaymentConfiguration,
    buyNowPayLaterProduct: BuyNowPayLaterProduct = BuyNowPayLaterProduct.DirectInvoice
): AvardaViewModel {
    return viewModel(
        factory = WebviewGatewayViewModelFactory(
            avardaConfiguration,
            PaymentMethod.Avarda(buyNowPayLaterProduct)
        )
    )
}

@SuppressLint("ComposeViewModelForwarding")
@Composable
fun AvardaComponent(
    viewModel: AvardaViewModel,
    modifier: Modifier = Modifier
) {
    WebviewGatewayComponent(viewModel = viewModel, modifier = modifier)
}
