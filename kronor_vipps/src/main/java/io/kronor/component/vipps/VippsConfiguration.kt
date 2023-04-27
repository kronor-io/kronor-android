package io.kronor.component.vipps

import android.net.Uri
import androidx.annotation.DrawableRes
import io.kronor.api.Environment
import io.kronor.component.webview_payment_gateway.WebviewGatewayConfiguration
import io.kronor.component.webview_payment_gateway.WebviewGatewayPaymentMethod

data class VippsConfiguration(
    val sessionToken: String,
    val environment: Environment,
    val redirectUrl: Uri,
    val appName: String,
    val appVersion: String,
    @DrawableRes
    val merchantLogo: Int? = null
)

fun VippsConfiguration.toWebviewGatewayConfiguration() : WebviewGatewayConfiguration {
    return WebviewGatewayConfiguration(
        sessionToken = this.sessionToken,
        environment = this.environment,
        redirectUrl = this.redirectUrl,
        appName = this.appName,
        appVersion = this.appVersion,
        merchantLogo = this.merchantLogo,
        paymentMethod = WebviewGatewayPaymentMethod.Vipps
    )
}