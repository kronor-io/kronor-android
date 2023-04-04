package io.kronor.component.credit_card

import android.os.Build
import com.apollographql.apollo3.api.Optional
import io.kronor.api.*
import io.kronor.api.type.AddSessionDeviceInformationInput
import io.kronor.api.type.PaymentCancelInput
import io.kronor.api.type.CreditCardPaymentInput
import io.kronor.api.type.SwishPaymentInput
import kotlinx.coroutines.flow.*
import java.util.UUID

data class CreditCardComponentInput(
    val returnUrl: String,
    val deviceFingerprint: String,
    val appName: String,
    val appVersion: String,
)

suspend fun Requests.makeNewPaymentRequest(
    creditCardInputData: CreditCardComponentInput
): Result<String> {
    val androidVersion = java.lang.Double.parseDouble(
        java.lang.String(Build.VERSION.RELEASE).replaceAll("(\\d+[.]\\d+)(.*)", "$1")
    )
    return kronorApolloClient.mutation(
        CreditCardPaymentMutation(
            payment = CreditCardPaymentInput(
                idempotencyKey = UUID.randomUUID().toString(),
                returnUrl = creditCardInputData.returnUrl
            ), deviceInfo = AddSessionDeviceInformationInput(
                browserName = creditCardInputData.appName,
                browserVersion = creditCardInputData.appVersion,
                fingerprint = creditCardInputData.deviceFingerprint,
                osName = "android",
                osVersion = androidVersion.toString(),
                userAgent = "kronor_android_sdk"
            )
        )
    ).executeMapKronorError().map { it.newCreditCardPayment.waitToken }
}