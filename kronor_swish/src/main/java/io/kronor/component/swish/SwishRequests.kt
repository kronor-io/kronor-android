package io.kronor.component.swish

import android.util.Log
import com.apollographql.apollo3.api.Optional
import com.apollographql.apollo3.exception.ApolloException
import com.fingerprintjs.android.fingerprint.DeviceIdResult
import com.fingerprintjs.android.fingerprint.FingerprintResult
import com.fingerprintjs.android.fingerprint.Fingerprinter
import com.fingerprintjs.android.fingerprint.FingerprinterFactory
import io.kronor.api.*
import io.kronor.api.type.AddSessionDeviceInformationInput
import io.kronor.api.type.SwishPaymentInput
import java.util.UUID

data class SwishComponentInput(
    val sessionToken: String, val customerSwishNumber: String? = null, val returnUrl: String
)

suspend fun makeNewPaymentRequest(
    swishInputData: SwishComponentInput,
    deviceFingerprint: String,
    env: Environment
) : String? {
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
        return response?.data?.newSwishPayment?.waitToken
    }
    return null
}