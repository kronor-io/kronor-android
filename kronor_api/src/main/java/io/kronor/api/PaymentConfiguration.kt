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


sealed class PaymentMethod {
    data class Swish(val customerSwishNumber: String? = null) : PaymentMethod()
    object CreditCard : PaymentMethod()
    object MobilePay : PaymentMethod()
    object Vipps: PaymentMethod()
    object PayPal: PaymentMethod()
    object BankTransfer: PaymentMethod()
    object GooglePay: PaymentMethod()
    data class Avarda(
        val buyNowPayLaterProduct: BuyNowPayLaterProduct = BuyNowPayLaterProduct.DirectInvoice
    ) : PaymentMethod()

    data class Fallback(val paymentMethod : String) : PaymentMethod()
}

// The buy-now-pay-later product used for an Avarda payment.
enum class BuyNowPayLaterProduct(val value: String) {
    DirectInvoice("DIRECT_INVOICE"),
    Invoice("INVOICE"),
    Loan("LOAN"),
    PartPayment("PART_PAYMENT");

    companion object {
        fun fromValue(value: String): BuyNowPayLaterProduct? {
            return entries.firstOrNull { it.value == value }
        }
    }
}

fun PaymentMethod.toRedirectMethod() : String {
    return when(this) {
        is PaymentMethod.CreditCard -> "creditcard"
        is PaymentMethod.MobilePay -> "mobilepay"
        is PaymentMethod.Vipps -> "vipps"
        is PaymentMethod.Swish -> "swish"
        is PaymentMethod.PayPal -> "paypal"
        is PaymentMethod.BankTransfer -> "bankTransfer"
        is PaymentMethod.GooglePay ->  "googlePay"
        is PaymentMethod.Avarda -> "avarda"
        is PaymentMethod.Fallback -> this.paymentMethod
    }
}

fun PaymentMethod.toPaymentGatewayMethod() : String {
    return when (this) {
        is PaymentMethod.CreditCard -> "creditCard"
        is PaymentMethod.MobilePay -> "mobilePay"
        is PaymentMethod.Vipps -> "vipps"
        is PaymentMethod.Swish -> "swish"
        is PaymentMethod.PayPal -> "paypal"
        is PaymentMethod.BankTransfer -> "bankTransfer"
        is PaymentMethod.GooglePay -> "googlePay"
        is PaymentMethod.Avarda -> "avarda"
        is PaymentMethod.Fallback -> this.paymentMethod
    }
}