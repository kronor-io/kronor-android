package io.kronor.api

import android.net.Uri
import androidx.annotation.DrawableRes
import java.util.Locale

data class PaymentConfiguration(
    val sessionToken: String,
    val environment: Environment,
    val locale: Locale,
    val redirectUrl: Uri,
    val appName: String,
    val appVersion: String,
    @DrawableRes val merchantLogo: Int? = null
)

enum class PaymentMethod {
    CreditCard, MobilePay, Vipps, Swish
}

fun PaymentMethod.toRedirectMethod() : String {
    return when(this) {
        PaymentMethod.CreditCard -> "creditcard"
        PaymentMethod.MobilePay -> "mobilepay"
        PaymentMethod.Vipps -> "vipps"
        PaymentMethod.Swish -> "swish"
    }
}

fun PaymentMethod.toPaymentGatewayMethod() : String {
    return when (this) {
        PaymentMethod.CreditCard -> "creditCard"
        PaymentMethod.MobilePay -> "mobilePay"
        PaymentMethod.Vipps -> "vipps"
        PaymentMethod.Swish -> "swish"
    }
}