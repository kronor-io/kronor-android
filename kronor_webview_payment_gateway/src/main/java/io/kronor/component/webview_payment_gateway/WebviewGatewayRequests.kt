package io.kronor.component.webview_payment_gateway


import android.os.Build
import com.apollographql.apollo3.api.Optional
import io.kronor.api.*
import io.kronor.api.type.*
import kotlinx.coroutines.flow.*
import java.util.UUID

data class WebviewGatewayComponentInput(
    val returnUrl: String,
    val deviceFingerprint: String,
    val appName: String,
    val appVersion: String,
    val paymentMethod: WebviewGatewayPaymentMethod
)

suspend fun Requests.makeNewPaymentRequest(
    webviewGatewayInputData: WebviewGatewayComponentInput
): Result<String> {
    val androidVersion = java.lang.Double.parseDouble(
        java.lang.String(Build.VERSION.RELEASE).replaceAll("(\\d+[.]\\d+)(.*)", "$1")
    )
    return when (webviewGatewayInputData.paymentMethod) {
        WebviewGatewayPaymentMethod.CreditCard -> {
            kronorApolloClient.mutation(
                CreditCardPaymentMutation(
                    payment = CreditCardPaymentInput(
                        idempotencyKey = UUID.randomUUID().toString(),
                        returnUrl = webviewGatewayInputData.returnUrl
                    ), deviceInfo = AddSessionDeviceInformationInput(
                        browserName = webviewGatewayInputData.appName,
                        browserVersion = webviewGatewayInputData.appVersion,
                        fingerprint = webviewGatewayInputData.deviceFingerprint,
                        osName = "android",
                        osVersion = androidVersion.toString(),
                        userAgent = "kronor_android_sdk"
                    )
                )
            ).executeMapKronorError().map { it.newCreditCardPayment.waitToken }
        }
        WebviewGatewayPaymentMethod.MobilePay -> {
            kronorApolloClient.mutation(
                MobilePayPaymentMutation(
                    payment = MobilePayPaymentInput(
                        idempotencyKey = UUID.randomUUID().toString(),
                        returnUrl = webviewGatewayInputData.returnUrl
                    ), deviceInfo = AddSessionDeviceInformationInput(
                        browserName = webviewGatewayInputData.appName,
                        browserVersion = webviewGatewayInputData.appVersion,
                        fingerprint = webviewGatewayInputData.deviceFingerprint,
                        osName = "android",
                        osVersion = androidVersion.toString(),
                        userAgent = "kronor_android_sdk"
                    )
                )
            ).executeMapKronorError().map { it.newMobilePayPayment.waitToken }
        }
        WebviewGatewayPaymentMethod.Vipps -> {
            kronorApolloClient.mutation(
                VippsPaymentMutation(
                    payment = VippsPaymentInput(
                        idempotencyKey = UUID.randomUUID().toString(),
                        returnUrl = webviewGatewayInputData.returnUrl
                    ), deviceInfo = AddSessionDeviceInformationInput(
                        browserName = webviewGatewayInputData.appName,
                        browserVersion = webviewGatewayInputData.appVersion,
                        fingerprint = webviewGatewayInputData.deviceFingerprint,
                        osName = "android",
                        osVersion = androidVersion.toString(),
                        userAgent = "kronor_android_sdk"
                    )
                )
            ).executeMapKronorError().map { it.newVippsPayment.waitToken }
        }
    }
}