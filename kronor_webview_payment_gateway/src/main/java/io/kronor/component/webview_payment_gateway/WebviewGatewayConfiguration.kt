package io.kronor.component.webview_payment_gateway

import android.net.Uri
import androidx.annotation.DrawableRes
import io.kronor.api.Environment

data class WebviewGatewayConfiguration(
    val sessionToken: String,
    val environment: Environment,
    val redirectUrl: Uri,
    val appName: String,
    val appVersion: String,
    val paymentMethod : WebviewGatewayPaymentMethod,
    @DrawableRes
    val merchantLogo: Int? = null
)

enum class WebviewGatewayPaymentMethod {
    CreditCard, MobilePay, Vipps
}

fun WebviewGatewayPaymentMethod.toPaymentGatewayMethod() : String {
    return when (this) {
        WebviewGatewayPaymentMethod.CreditCard -> "creditCard"
        WebviewGatewayPaymentMethod.MobilePay -> "mobilePay"
        WebviewGatewayPaymentMethod.Vipps -> "vipps"
    }
}