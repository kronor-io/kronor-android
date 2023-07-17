package io.kronor.api

import android.os.Build
import com.apollographql.apollo3.ApolloCall
import com.apollographql.apollo3.ApolloClient
import com.apollographql.apollo3.api.Operation
import com.apollographql.apollo3.api.Optional
import com.apollographql.apollo3.exception.ApolloException
import com.apollographql.apollo3.network.okHttpClient
import com.apollographql.apollo3.network.ws.SubscriptionWsProtocol
import io.kronor.api.type.AddSessionDeviceInformationInput
import io.kronor.api.type.CreditCardPaymentInput
import io.kronor.api.type.MobilePayPaymentInput
import io.kronor.api.type.PaymentCancelInput
import io.kronor.api.type.SwishPaymentInput
import io.kronor.api.type.VippsPaymentInput
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.lang.Exception
import java.util.*
import kotlin.Result.Companion.failure
import kotlin.Result.Companion.success

enum class Environment {
    Staging, Production
}

class Requests(token: String, env: Environment) {

    private val okHttpClient =
        OkHttpClient.Builder().build()

    val kronorApolloClient = ApolloClient.Builder().httpServerUrl(
        when (env) {
            Environment.Staging -> "https://staging.kronor.io/v1/graphql"
            Environment.Production -> "https://kronor.io/v1/graphql"
        }
    ).webSocketServerUrl(
        when (env) {
            Environment.Staging -> "wss://staging.kronor.io/v1/graphql"
            Environment.Production -> "wss://kronor.io/v1/graphql"
        }
    )
        .addHttpHeader("Authorization", "Bearer $token")
        .wsProtocol(SubscriptionWsProtocol.Factory(connectionPayload = { mapOf("headers" to (mapOf("Authorization" to "Bearer $token"))) }))
        .okHttpClient(okHttpClient).build()

    fun getPaymentRequests(): Flow<List<PaymentStatusSubscription.PaymentRequest>> {
        return kronorApolloClient.subscription(
            PaymentStatusSubscription()
        ).toFlow().map { response -> response.data?.paymentRequests }.filterNotNull()
    }

    suspend fun cancelPayment(): Result<String?> {
        return kronorApolloClient.mutation(
            CancelPaymentMutation(
                pay = PaymentCancelInput(idempotencyKey = UUID.randomUUID().toString())
            )
        ).executeMapKronorError().map { it.cancelPayment.waitToken }
    }

}

data class ApiError(
    val errors: List<com.apollographql.apollo3.api.Error>, val extensions: Map<String, Any?>
)

sealed class KronorError : Throwable() {
    data class NetworkError(val e: ApolloException) : KronorError()

    data class GraphQlError(val e: ApiError) : KronorError()
}

suspend fun <D : Operation.Data> ApolloCall<D>.executeMapKronorError(): Result<D> {
    return try {
        val response = this.execute()
        response.data?.let {
            success(it)
        } ?: failure(
            KronorError.GraphQlError(
                ApiError(
                    response.errors ?: emptyList(), response.extensions
                )
            )
        )
    } catch (e: ApolloException) {
        failure(KronorError.NetworkError(e))
    }
}

data class PaymentRequestArgs(
    val returnUrl: String,
    val deviceFingerprint: String,
    val appName: String,
    val appVersion: String,
    val paymentMethod: PaymentMethod
)


suspend fun Requests.makeNewPaymentRequest(
    paymentRequestArgs: PaymentRequestArgs
): Result<String> {
    val androidVersion = java.lang.Double.parseDouble(
        java.lang.String(Build.VERSION.RELEASE).replaceAll("(\\d+[.]\\d+)(.*)", "$1")
    )
    val os = "android"
    val userAgent = "kronor_android_sdk"
    return when (paymentRequestArgs.paymentMethod) {
        is PaymentMethod.CreditCard -> {
            kronorApolloClient.mutation(
                CreditCardPaymentMutation(
                    payment = CreditCardPaymentInput(
                        idempotencyKey = UUID.randomUUID().toString(),
                        returnUrl = paymentRequestArgs.returnUrl
                    ), deviceInfo = AddSessionDeviceInformationInput(
                        browserName = paymentRequestArgs.appName,
                        browserVersion = paymentRequestArgs.appVersion,
                        fingerprint = paymentRequestArgs.deviceFingerprint,
                        osName = os,
                        osVersion = androidVersion.toString(),
                        userAgent = userAgent
                    )
                )
            ).executeMapKronorError().map { it.newCreditCardPayment.waitToken }
        }

        is PaymentMethod.MobilePay -> {
            kronorApolloClient.mutation(
                MobilePayPaymentMutation(
                    payment = MobilePayPaymentInput(
                        idempotencyKey = UUID.randomUUID().toString(),
                        returnUrl = paymentRequestArgs.returnUrl
                    ), deviceInfo = AddSessionDeviceInformationInput(
                        browserName = paymentRequestArgs.appName,
                        browserVersion = paymentRequestArgs.appVersion,
                        fingerprint = paymentRequestArgs.deviceFingerprint,
                        osName = os,
                        osVersion = androidVersion.toString(),
                        userAgent = userAgent
                    )
                )
            ).executeMapKronorError().map { it.newMobilePayPayment.waitToken }
        }

        is PaymentMethod.Vipps -> {
            kronorApolloClient.mutation(
                VippsPaymentMutation(
                    payment = VippsPaymentInput(
                        idempotencyKey = UUID.randomUUID().toString(),
                        returnUrl = paymentRequestArgs.returnUrl
                    ), deviceInfo = AddSessionDeviceInformationInput(
                        browserName = paymentRequestArgs.appName,
                        browserVersion = paymentRequestArgs.appVersion,
                        fingerprint = paymentRequestArgs.deviceFingerprint,
                        osName = os,
                        osVersion = androidVersion.toString(),
                        userAgent = userAgent
                    )
                )
            ).executeMapKronorError().map { it.newVippsPayment.waitToken }
        }

        is PaymentMethod.Swish -> kronorApolloClient.mutation(
            SwishPaymentMutation(
                payment = SwishPaymentInput(
                    customerSwishNumber = Optional.presentIfNotNull(paymentRequestArgs.paymentMethod.customerSwishNumber),
                    flow = if (paymentRequestArgs.paymentMethod.customerSwishNumber == null) "mcom" else "ecom",
                    idempotencyKey = UUID.randomUUID().toString(),
                    returnUrl = paymentRequestArgs.returnUrl
                ), deviceInfo = AddSessionDeviceInformationInput(
                    browserName = paymentRequestArgs.appName,
                    browserVersion = paymentRequestArgs.appVersion,
                    fingerprint = paymentRequestArgs.deviceFingerprint,
                    osName = os,
                    osVersion = androidVersion.toString(),
                    userAgent = userAgent
                )
            )
        ).executeMapKronorError().map { it.newSwishPayment.waitToken }
        is PaymentMethod.Fallback -> {
            failure<String>(Exception("Impossible!"))
        }
    }
}