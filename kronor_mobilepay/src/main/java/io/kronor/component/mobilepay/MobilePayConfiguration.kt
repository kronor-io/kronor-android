package io.kronor.component.mobilepay

import android.net.Uri
import androidx.annotation.DrawableRes
import io.kronor.api.Environment
import io.kronor.component.webview_payment_gateway.WebviewGatewayConfiguration
import io.kronor.component.webview_payment_gateway.WebviewGatewayPaymentMethod

data class MobilePayConfiguration(
    val sessionToken: String,
    val environment: Environment,
    val redirectUrl: Uri,
    val appName: String,
    val appVersion: String,
    @DrawableRes
    val merchantLogo: Int? = null,
    val onPaymentFailure : () -> Unit,
    val onPaymentSuccess : (String) -> Unit
)

fun MobilePayConfiguration.toWebviewGatewayConfiguration() : WebviewGatewayConfiguration {
    return WebviewGatewayConfiguration(
        sessionToken = this.sessionToken,
        environment = this.environment,
        redirectUrl = this.redirectUrl,
        appName = this.appName,
        appVersion = this.appVersion,
        merchantLogo = this.merchantLogo,
        onPaymentFailure = this.onPaymentFailure,
        onPaymentSuccess = this.onPaymentSuccess,
        paymentMethod = WebviewGatewayPaymentMethod.MobilePay
    )
}