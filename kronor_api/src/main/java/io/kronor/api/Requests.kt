package io.kronor.api

import android.os.Build
import com.apollographql.apollo.ApolloCall
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.Operation
import com.apollographql.apollo.api.Optional
import com.apollographql.apollo.exception.ApolloException
import com.apollographql.apollo.network.okHttpClient
import com.apollographql.apollo.network.ws.SubscriptionWsProtocol
import io.kronor.api.type.AddSessionDeviceInformationInput
import io.kronor.api.type.BankTransferPaymentInput
import io.kronor.api.type.CreditCardPaymentInput
import io.kronor.api.type.GatewayEnum
import io.kronor.api.type.MobilePayPaymentInput
import io.kronor.api.type.MobilePayUserFlow
import io.kronor.api.type.PayPalPaymentInput
import io.kronor.api.type.PaymentCancelInput
import io.kronor.api.type.SwishPaymentInput
import io.kronor.api.type.VippsPaymentInput
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import okhttp3.OkHttpClient
import java.lang.Exception
import java.util.UUID
import kotlin.Result.Companion.failure
import kotlin.Result.Companion.success

enum class Environment {
    Staging, Production
}

private const val TemporaryFailure = "TEMPORARY_FAILURE"
// Six attempts over a 15.5-second backoff window before the error reaches the UI.
internal val DefaultRetryDelaysMillis = listOf(500L, 1_000L, 2_000L, 4_000L, 8_000L)

class Requests internal constructor(
    val kronorApolloClient: ApolloClient,
    private val retryDelaysMillis: List<Long>
) {

    constructor(token: String, env: Environment) : this(
        kronorApolloClient = buildApolloClient(token, env),
        retryDelaysMillis = DefaultRetryDelaysMillis
    )

    fun getPaymentRequests(
        onRetry: () -> Unit = {},
        onRecovered: () -> Unit = {}
    ): Flow<List<PaymentStatusSubscription.PaymentRequest>> {
        return kronorApolloClient.subscription(
            PaymentStatusSubscription()
        ).toFlow().map { response ->
            response.data?.paymentRequests ?: throw KronorError.GraphQlError(
                ApiError(response.errors ?: emptyList(), response.extensions)
            )
        }.filterNotNull().retryTransientErrors(
            delaysMillis = retryDelaysMillis,
            onRetry = onRetry,
            onRecovered = onRecovered
        ).catch { error ->
            when (error) {
                is KronorError -> throw error
                is ApolloException -> throw KronorError.NetworkError(error)
                else -> throw error
            }
        }
    }

    suspend fun cancelPayment(): Result<String?> {
        return kronorApolloClient.mutation(
            CancelPaymentMutation(
                pay = PaymentCancelInput(idempotencyKey = UUID.randomUUID().toString())
            )
        ).executeMapKronorError().map { it.cancelPayment.waitToken }
    }

    companion object {
        private fun buildApolloClient(token: String, env: Environment): ApolloClient {
            val httpServerUrl = when (env) {
                Environment.Staging -> "https://staging.kronor.io/v1/graphql"
                Environment.Production -> "https://kronor.io/v1/graphql"
            }
            val webSocketServerUrl = when (env) {
                Environment.Staging -> "wss://staging.kronor.io/v1/graphql"
                Environment.Production -> "wss://kronor.io/v1/graphql"
            }

            return ApolloClient.Builder()
                .httpServerUrl(httpServerUrl)
                .webSocketServerUrl(webSocketServerUrl)
                .addHttpHeader("Authorization", "Bearer $token")
                .wsProtocol(
                    SubscriptionWsProtocol.Factory(
                        connectionPayload = {
                            mapOf("headers" to mapOf("Authorization" to "Bearer $token"))
                        }
                    )
                )
                .okHttpClient(OkHttpClient.Builder().build())
                .build()
        }
    }

}

data class ApiError(
    val errors: List<com.apollographql.apollo.api.Error>, val extensions: Map<String, Any?>
)

sealed class KronorError : Throwable() {
    data class NetworkError(val e: ApolloException) : KronorError()

    data class GraphQlError(val e: ApiError) : KronorError()

    data class FlowError(val e: String) : KronorError()
}

internal fun Throwable.isRetryableKronorError(): Boolean = when (this) {
    is KronorError.NetworkError, is ApolloException -> true
    is KronorError.GraphQlError -> e.errors.any { error ->
        error.extensions?.get("type") == TemporaryFailure
    }
    else -> false
}

internal suspend fun <T> retryTransientErrors(
    delaysMillis: List<Long> = DefaultRetryDelaysMillis,
    onRetry: () -> Unit = {},
    block: suspend () -> Result<T>
): Result<T> {
    var result = block()
    for (delayMillis in delaysMillis) {
        if (result.exceptionOrNull()?.isRetryableKronorError() != true) {
            return result
        }
        onRetry()
        delay(delayMillis)
        result = block()
    }
    return result
}

internal fun <T> Flow<T>.retryTransientErrors(
    delaysMillis: List<Long>,
    onRetry: () -> Unit = {},
    onRecovered: () -> Unit = {}
): Flow<T> = flow {
    var consecutiveFailures = 0
    while (true) {
        try {
            this@retryTransientErrors.collect { value ->
                consecutiveFailures = 0
                onRecovered()
                emit(value)
            }
            return@flow
        } catch (cause: Throwable) {
            if (!cause.isRetryableKronorError() || consecutiveFailures >= delaysMillis.size) {
                throw cause
            }
            onRetry()
            delay(delaysMillis[consecutiveFailures])
            consecutiveFailures += 1
        }
    }
}

suspend fun <D : Operation.Data> ApolloCall<D>.executeMapKronorError(): Result<D> {
    val response = this.execute()
    if (response.exception != null) {
       return failure(KronorError.NetworkError(response.exception!!))
    }
    val errors = response.errors.orEmpty()
    return if (errors.isNotEmpty()) {
        failure(
            KronorError.GraphQlError(
                ApiError(errors, response.extensions)
            )
        )
    } else response.data?.let {
        success(it)
    } ?: failure(
        KronorError.GraphQlError(
            ApiError(
               emptyList(), response.extensions
           )
    ))
}

data class PaymentRequestArgs(
    val returnUrl: String,
    val merchantReturnUrl: String,
    val deviceFingerprint: String,
    val appName: String,
    val appVersion: String,
    val paymentMethod: PaymentMethod,
    val idempotencyKey: String
) {
    constructor(
        returnUrl: String,
        merchantReturnUrl: String,
        deviceFingerprint: String,
        appName: String,
        appVersion: String,
        paymentMethod: PaymentMethod
    ) : this(
        returnUrl = returnUrl,
        merchantReturnUrl = merchantReturnUrl,
        deviceFingerprint = deviceFingerprint,
        appName = appName,
        appVersion = appVersion,
        paymentMethod = paymentMethod,
        idempotencyKey = UUID.randomUUID().toString()
    )
}

data class PaymentRequestResult(
    val paymentId: String,
    val gateway: GatewayEnum?
)


suspend fun Requests.makeNewPaymentRequest(
    paymentRequestArgs: PaymentRequestArgs,
    onRetry: () -> Unit = {}
): Result<PaymentRequestResult> {
    val androidVersion = java.lang.Double.parseDouble(
        java.lang.String(Build.VERSION.RELEASE).replaceAll("(\\d+[.]\\d+)(.*)", "$1")
    )
    val os = "android"
    val userAgent = "kronor_android_sdk/${BuildConfig.VERSION}"
    return retryTransientErrors(onRetry = onRetry) {
        makeNewPaymentRequestOnce(paymentRequestArgs, androidVersion, os, userAgent)
    }
}

private suspend fun Requests.makeNewPaymentRequestOnce(
    paymentRequestArgs: PaymentRequestArgs,
    androidVersion: Double,
    os: String,
    userAgent: String
): Result<PaymentRequestResult> = when (paymentRequestArgs.paymentMethod) {
        is PaymentMethod.CreditCard -> {
            kronorApolloClient.mutation(
                CreditCardPaymentMutation(
                    payment = CreditCardPaymentInput(
                        idempotencyKey = paymentRequestArgs.idempotencyKey,
                        returnUrl = paymentRequestArgs.merchantReturnUrl,
                        merchantReturnUrl = Optional.present(paymentRequestArgs.merchantReturnUrl)
                    ), deviceInfo = AddSessionDeviceInformationInput(
                        browserName = paymentRequestArgs.appName,
                        browserVersion = paymentRequestArgs.appVersion,
                        fingerprint = paymentRequestArgs.deviceFingerprint,
                        osName = os,
                        osVersion = androidVersion.toString(),
                        userAgent = userAgent
                    )
                )
            ).executeMapKronorError().map { PaymentRequestResult(paymentId =it.newCreditCardPayment.waitToken, gateway = it.newCreditCardPayment.gateway) }
        }

        is PaymentMethod.MobilePay -> {
            kronorApolloClient.mutation(
                MobilePayPaymentMutation(
                    payment = MobilePayPaymentInput(
                        idempotencyKey = paymentRequestArgs.idempotencyKey,
                        returnUrl = paymentRequestArgs.merchantReturnUrl,
                        merchantReturnUrl = Optional.present(paymentRequestArgs.merchantReturnUrl),
                        userFlow = Optional.present(MobilePayUserFlow.NativeRedirect)
                    ), deviceInfo = AddSessionDeviceInformationInput(
                        browserName = paymentRequestArgs.appName,
                        browserVersion = paymentRequestArgs.appVersion,
                        fingerprint = paymentRequestArgs.deviceFingerprint,
                        osName = os,
                        osVersion = androidVersion.toString(),
                        userAgent = userAgent,
                    )
                )
            ).executeMapKronorError().map { PaymentRequestResult(paymentId = it.newMobilePayPayment.waitToken, gateway = it.newMobilePayPayment.gateway) }
        }

        is PaymentMethod.Vipps -> {
            kronorApolloClient.mutation(
                VippsPaymentMutation(
                    payment = VippsPaymentInput(
                        idempotencyKey = paymentRequestArgs.idempotencyKey,
                        returnUrl = paymentRequestArgs.merchantReturnUrl,
                        merchantReturnUrl = Optional.present(paymentRequestArgs.merchantReturnUrl)
                    ), deviceInfo = AddSessionDeviceInformationInput(
                        browserName = paymentRequestArgs.appName,
                        browserVersion = paymentRequestArgs.appVersion,
                        fingerprint = paymentRequestArgs.deviceFingerprint,
                        osName = os,
                        osVersion = androidVersion.toString(),
                        userAgent = userAgent
                    )
                )
            ).executeMapKronorError().map { PaymentRequestResult(paymentId = it.newVippsPayment.waitToken, gateway = it.newVippsPayment.gateway) }
        }

        is PaymentMethod.Swish -> kronorApolloClient.mutation(
            SwishPaymentMutation(
                payment = SwishPaymentInput(
                    customerSwishNumber = Optional.presentIfNotNull(paymentRequestArgs.paymentMethod.customerSwishNumber),
                    flow = if (paymentRequestArgs.paymentMethod.customerSwishNumber == null) "mcom" else "ecom",
                    idempotencyKey = paymentRequestArgs.idempotencyKey,
                    returnUrl = paymentRequestArgs.merchantReturnUrl,
                    merchantReturnUrl = Optional.present(paymentRequestArgs.merchantReturnUrl)
                ), deviceInfo = AddSessionDeviceInformationInput(
                    browserName = paymentRequestArgs.appName,
                    browserVersion = paymentRequestArgs.appVersion,
                    fingerprint = paymentRequestArgs.deviceFingerprint,
                    osName = os,
                    osVersion = androidVersion.toString(),
                    userAgent = userAgent
                )
            )
        ).executeMapKronorError().map { PaymentRequestResult(paymentId = it.newSwishPayment.waitToken, gateway = null) }

        is PaymentMethod.PayPal -> {
            kronorApolloClient.mutation(
                PayPalPaymentMutation(
                    payment = PayPalPaymentInput(
                        idempotencyKey = paymentRequestArgs.idempotencyKey,
                        returnUrl = paymentRequestArgs.returnUrl,
                        merchantReturnUrl = Optional.present(paymentRequestArgs.merchantReturnUrl)
                    ), deviceInfo = AddSessionDeviceInformationInput(
                        browserName = paymentRequestArgs.appName,
                        browserVersion = paymentRequestArgs.appVersion,
                        fingerprint = paymentRequestArgs.deviceFingerprint,
                        osName = os,
                        osVersion = androidVersion.toString(),
                        userAgent = userAgent
                    )
                )
            ).executeMapKronorError().map { PaymentRequestResult(paymentId = it.newPayPalPayment.paymentId, gateway = null) }
        }

        is PaymentMethod.BankTransfer -> {
            kronorApolloClient.mutation(
                BankTransferPaymentMutation(
                    payment = BankTransferPaymentInput(
                        idempotencyKey = paymentRequestArgs.idempotencyKey,
                        returnUrl = paymentRequestArgs.returnUrl,
                        merchantReturnUrl = Optional.present(paymentRequestArgs.merchantReturnUrl),
                        flow = Optional.present("mcom")
                    ), deviceInfo = AddSessionDeviceInformationInput(
                        browserName = paymentRequestArgs.appName,
                        browserVersion = paymentRequestArgs.appVersion,
                        fingerprint = paymentRequestArgs.deviceFingerprint,
                        osName = os,
                        osVersion = androidVersion.toString(),
                        userAgent = userAgent
                    )
                )
            ).executeMapKronorError().map { PaymentRequestResult(paymentId = it.newBankTransferPayment.paymentId, gateway = it.newBankTransferPayment.gateway) }
        }

        is PaymentMethod.Fallback -> {
            failure(Exception("Impossible!"))
        }
    }
