package io.kronor.component.swish

import android.os.Build
import android.util.Log
import com.apollographql.apollo3.api.Optional
import com.apollographql.apollo3.exception.ApolloException
import io.kronor.api.*
import io.kronor.api.type.AddSessionDeviceInformationInput
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
): String? {
    val androidVersion = java.lang.Double.parseDouble(
        java.lang.String(Build.VERSION.RELEASE).replaceAll("(\\d+[.]\\d+)(.*)", "$1")
    )
    val response = try {
        kronorApolloClient.mutation(
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
        ).execute()
    } catch (e: ApolloException) {
        Log.d("NewSwishPayment", "Failed: $e")
        null
    }
    Log.d("NewSwishPayment", "${response?.errors}")
    Log.d("NewSwishPayment", "${response?.data?.addSessionDeviceInformation?.result}")
    return response?.data?.newSwishPayment?.waitToken
}

fun Requests.getPaymentRequests(): Flow<List<PaymentStatusSubscription.PaymentRequest>>? {
    return try {
        kronorApolloClient.subscription(
            PaymentStatusSubscription()
        ).toFlow().map { response -> response.data?.paymentRequests }.filterNotNull()
    } catch (e: ApolloException) {
        Log.d("PaymentStatusSub", "Failed; $e")
        null
    }
}