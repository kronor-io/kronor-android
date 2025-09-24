package io.kronor.component.bank_transfer

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import io.kronor.api.PaymentConfiguration
import io.kronor.api.PaymentMethod
import io.kronor.component.webview_payment_gateway.WebviewGatewayViewModel
import io.kronor.component.webview_payment_gateway.WebviewGatewayViewModelFactory
import androidx.lifecycle.viewmodel.compose.viewModel
import com.trustlyAndroidLibrary.TrustlyWebView

typealias BankTransferViewModel = WebviewGatewayViewModel

@Composable
fun bankTransferViewModel(
    trustlyConfiguration: PaymentConfiguration
): BankTransferViewModel {
    return viewModel(
        factory = WebviewGatewayViewModelFactory(
            trustlyConfiguration,
            PaymentMethod.BankTransfer
        )
    )
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BankTransferComponent(
    viewModel: BankTransferViewModel,
    modifier: Modifier = Modifier.fillMaxSize()
) {
    val trustlyUrl = viewModel.paymentUrl // assuming your VM exposes the Trustly payment URL

    AndroidView(
        modifier = modifier,
        factory = { context ->
            TrustlyWebView(context, trustlyUrl).apply {
                settings.javaScriptEnabled = true
                // You can set WebViewClient/WebChromeClient if needed
            }
        }
    )
}
