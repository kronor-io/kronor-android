package io.kronor.component.swish

import android.util.Log
import com.apollographql.apollo3.api.Optional
import com.apollographql.apollo3.exception.ApolloException
import com.fingerprintjs.android.fingerprint.DeviceIdResult
import com.fingerprintjs.android.fingerprint.Fingerprinter
import com.fingerprintjs.android.fingerprint.FingerprinterFactory
import io.kronor.api.*
import io.kronor.api.type.AddSessionDeviceInformationInput
import io.kronor.api.type.SwishPaymentInput
import kotlinx.coroutines.flow.*
import java.util.UUID

data class SwishComponentInput(
    val sessionToken: String, val customerSwishNumber: String? = null, val returnUrl: String
)

suspend fun Requests.makeNewPaymentRequest(
    swishInputData: SwishComponentInput,
    deviceFingerprint: String,
    env: Environment
): String? {
    kronorApolloClient(token = swishInputData.sessionToken, env)?.let {
        val response = try {
            it.mutation(
                SwishPaymentMutation(
                    payment = SwishPaymentInput(
                        customerSwishNumber = Optional.presentIfNotNull(swishInputData.customerSwishNumber),
                        flow = if (swishInputData.customerSwishNumber == null) "mcom" else "ecom",
                        idempotencyKey = UUID.randomUUID().toString(),
                        returnUrl = swishInputData.returnUrl
                    ),
                    deviceInfo = AddSessionDeviceInformationInput(
                        browserName = "kronor.component.swish",
                        browserVersion = "0.0.1",
                        fingerprint = deviceFingerprint,
                        osName = "android",
                        osVersion = "21.33.23",
                        userAgent = "kronor-api"
                    )
                )
            ).execute()
        } catch (e: ApolloException) {
            Log.d("NewSwishPayment", "Failed: $e")
            null
        }
        Log.d("NewSwishPayment", "${response?.errors}")
        return response?.data?.newSwishPayment?.waitToken
    }
    Log.d("NewSwishPayment", "client log")
    return null
}

fun Requests.getPaymentRequests(
    sessionToken: String,
    env: Environment
) : Flow<List<PaymentStatusSubscription.PaymentRequest>>? {
    return kronorApolloClient(token = sessionToken, env = env)?.let {
        try {
            Log.d("InSub", "I am here in try $sessionToken")
            it.subscription(
                PaymentStatusSubscription()
            ).toFlow()
                .map { response -> response.data?.paymentRequests }
                .filterNotNull()
        } catch (e: ApolloException) {
            Log.d("PaymentStatusSub", "Failed; $e")
            null
        }
    }
}