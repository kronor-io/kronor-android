package io.kronor.component.swish

import android.os.Build
import com.apollographql.apollo3.api.Optional
import io.kronor.api.*
import io.kronor.api.type.AddSessionDeviceInformationInput
import io.kronor.api.type.PaymentCancelInput
import io.kronor.api.type.SwishPaymentInput
import kotlinx.coroutines.flow.*
import java.util.UUID

data class SwishComponentInput(
    val customerSwishNumber: String? = null,
    val returnUrl: String,
    val deviceFingerprint: String,
    val appName: String,
    val appVersion: String,
)

suspend fun Requests.makeNewPaymentRequest(
    swishInputData: SwishComponentInput
): Result<String> {
    val androidVersion = java.lang.Double.parseDouble(
        java.lang.String(Build.VERSION.RELEASE).replaceAll("(\\d+[.]\\d+)(.*)", "$1")
    )
    return kronorApolloClient.mutation(
        SwishPaymentMutation(
            payment = SwishPaymentInput(
                customerSwishNumber = Optional.presentIfNotNull(swishInputData.customerSwishNumber),
                flow = if (swishInputData.customerSwishNumber == null) "mcom" else "ecom",
                idempotencyKey = UUID.randomUUID().toString(),
                returnUrl = swishInputData.returnUrl
            ), deviceInfo = AddSessionDeviceInformationInput(
                browserName = swishInputData.appName,
                browserVersion = swishInputData.appVersion,
                fingerprint = swishInputData.deviceFingerprint,
                osName = "android",
                osVersion = androidVersion.toString(),
                userAgent = "kronor_android_sdk"
            )
        )
    ).executeMapKronorError().map { it.newSwishPayment.waitToken }
}
