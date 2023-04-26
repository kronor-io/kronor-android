package io.kronor.component.webview_payment_gateway

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.apollographql.apollo3.exception.ApolloException
import com.tinder.StateMachine
import io.kronor.api.*
import io.kronor.api.type.PaymentStatusEnum
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private val DelayBeforeCallback: Duration = 2.seconds // 2000 milliseconds = 2 seconds

class WebviewGatewayViewModelFactory(
    private val WebviewGatewayConfiguration: WebviewGatewayConfiguration
) : ViewModelProvider.NewInstanceFactory() {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return WebviewGatewayViewModel(WebviewGatewayConfiguration) as T
    }
}

class WebviewGatewayViewModel(
    val webviewGatewayConfiguration: WebviewGatewayConfiguration
) : ViewModel() {
    private var _deviceFingerprint: String? = null
    val deviceFingerprint: String? = _deviceFingerprint

    val requests =
        Requests(webviewGatewayConfiguration.sessionToken, webviewGatewayConfiguration.environment)
    val stateMachine: StateMachine<WebviewGatewayStatechart.Companion.State, WebviewGatewayStatechart.Companion.Event, WebviewGatewayStatechart.Companion.SideEffect> =
        WebviewGatewayStatechart().stateMachine
    private var _webviewGatewayState: MutableState<WebviewGatewayStatechart.Companion.State> =
        mutableStateOf(
            WebviewGatewayStatechart.Companion.State.Initializing
        )
    val webviewGatewayState: State<WebviewGatewayStatechart.Companion.State> = _webviewGatewayState
    private var paymentRequest: PaymentStatusSubscription.PaymentRequest? by mutableStateOf(null)
    private var waitToken: String? by mutableStateOf(null)
    val paymentGatewayUrl: Uri = constructPaymentGatewayUrl(
        environment = webviewGatewayConfiguration.environment,
        sessionToken = webviewGatewayConfiguration.sessionToken,
        paymentMethod = webviewGatewayConfiguration.paymentMethod.toPaymentGatewayMethod(),
        merchantReturnUrl = webviewGatewayConfiguration.redirectUrl
    )

    private val _events = MutableSharedFlow<CreditCardEvent>()
    val events: Flow<CreditCardEvent> = _events

    fun transition(event: WebviewGatewayStatechart.Companion.Event) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                _transition(event)

            }
        }
    }

    fun setDeviceFingerPrint(fingerprint: String) {
        this._deviceFingerprint = fingerprint
    }

    private suspend fun _transition(event: WebviewGatewayStatechart.Companion.Event) {

        when (val result = stateMachine.transition(event)) {
            is StateMachine.Transition.Valid -> {
                _webviewGatewayState.value = result.toState
                result.sideEffect?.let {
                    handleSideEffect(it)
                }
            }

            is StateMachine.Transition.Invalid -> {
                Log.d(
                    "WebviewGatewayViewModel",
                    "Cannot transition to $event from ${result.fromState}"
                )
            }
        }
    }

    private suspend fun _transitionToError(t: Throwable?) {
        _transition(
            WebviewGatewayStatechart.Companion.Event.Error(
                (t ?: KronorError.GraphQlError(ApiError(emptyList(), emptyMap()))) as KronorError
            )
        )
    }

    private suspend fun handleSideEffect(sideEffect: WebviewGatewayStatechart.Companion.SideEffect) {
        when (sideEffect) {
            is WebviewGatewayStatechart.Companion.SideEffect.CreatePaymentRequest -> {
                Log.d("WebviewGatewayViewModel", "Creating Payment Request")
                val waitToken = requests.makeNewPaymentRequest(
                    webviewGatewayInputData = WebviewGatewayComponentInput(
                        returnUrl = webviewGatewayConfiguration.redirectUrl.toString(),
                        deviceFingerprint = deviceFingerprint ?: "fingerprint not found",
                        appName = webviewGatewayConfiguration.appName,
                        appVersion = webviewGatewayConfiguration.appVersion,
                        paymentMethod = webviewGatewayConfiguration.paymentMethod
                    )
                )
                when {
                    waitToken.isFailure -> {
                        Log.d(
                            "WebviewGatewayViewModel",
                            "Error creating payment request: ${waitToken.exceptionOrNull()}"
                        )
                        _transitionToError(waitToken.exceptionOrNull())
                    }

                    waitToken.isSuccess -> {
                        _transition(
                            WebviewGatewayStatechart.Companion.Event.PaymentRequestCreated(
                                waitToken = waitToken.getOrNull()!!
                            )
                        )
                    }
                }
            }

            is WebviewGatewayStatechart.Companion.SideEffect.ListenOnPaymentRequest -> {
                this.waitToken = sideEffect.waitToken
            }

            is WebviewGatewayStatechart.Companion.SideEffect.SubscribeToPaymentStatus -> {
            }

            is WebviewGatewayStatechart.Companion.SideEffect.SubscribeToPaymentStatus -> {}
            is WebviewGatewayStatechart.Companion.SideEffect.CancelPaymentRequest -> {
                Log.d("WebviewGatewayViewModel", "Reset payment flow")
                val waitToken = requests.cancelPayment()
                when {
                    waitToken.isFailure -> {
                        Log.d(
                            "WebviewGatewayViewModel",
                            "Failed to cancel payment request: ${waitToken.exceptionOrNull()}"
                        )
                        _transitionToError(waitToken.exceptionOrNull())
                    }

                    waitToken.isSuccess -> {}
                }
            }

            is WebviewGatewayStatechart.Companion.SideEffect.ResetState -> {

            }

            is WebviewGatewayStatechart.Companion.SideEffect.NotifyPaymentSuccess -> {
//                delay(DelayBeforeCallback)
//                webviewGatewayConfiguration.onPaymentSuccess(sideEffect.paymentId)
                Log.d("WebviewGatewayViewModel", "Emitting success")
                _events.emit(CreditCardEvent.PaymentSuccess(sideEffect.paymentId))
            }

            is WebviewGatewayStatechart.Companion.SideEffect.NotifyPaymentFailure -> {
//                webviewGatewayConfiguration.onPaymentFailure()
                Log.d("WebviewGatewayViewModel", "Emitting failure")
                _events.emit(CreditCardEvent.PaymentFailure)
            }

            is WebviewGatewayStatechart.Companion.SideEffect.OpenEmbeddedSite -> {

            }

            is WebviewGatewayStatechart.Companion.SideEffect.CancelAndNotifyFailure -> {

            }

            is WebviewGatewayStatechart.Companion.SideEffect.CancelAfterDeadline -> {
                viewModelScope.launch {
                    delay(DelayBeforeCallback)
                    _transition(WebviewGatewayStatechart.Companion.Event.Cancel)
                }
            }
        }
    }

    suspend fun onSubscription() {
        // If we have a waitToken set in our view model, get the payment request
        // associated with that waitToken and in a status that is not initializing

        try {
            requests.getPaymentRequests().collect { paymentRequestList ->
                // If we have a waitToken set in our view model, get the payment request
                // associated with that waitToken and in a status that is not initializing
                Log.d("WebviewGatewayViewModel", "Inside Collect")
                this.waitToken?.let {
                    this.paymentRequest = paymentRequestList.firstOrNull { paymentRequest ->
                        (paymentRequest.waitToken == this.waitToken) and (paymentRequest.status?.all { paymentStatus ->
                            (paymentStatus.status != PaymentStatusEnum.INITIALIZING)
                        } ?: false)
                    }

                    this.paymentRequest?.let { paymentRequest ->
                        if (_webviewGatewayState.value is WebviewGatewayStatechart.Companion.State.WaitingForPaymentRequest) {
                            _transition(WebviewGatewayStatechart.Companion.Event.PaymentRequestInitialized)
                        }
                        if (_webviewGatewayState.value is WebviewGatewayStatechart.Companion.State.WaitingForSubscription) {
                            _transition(WebviewGatewayStatechart.Companion.Event.PaymentRequestInitialized)
                        }

                        paymentRequest.status?.any {
                            it.status == PaymentStatusEnum.PAID || it.status == PaymentStatusEnum.AUTHORIZED
                        }?.let {
                            if (it) {
                                _transition(
                                    WebviewGatewayStatechart.Companion.Event.PaymentAuthorized(
                                        paymentRequest.resultingPaymentId!!
                                    )
                                )
                            }
                        }

                        paymentRequest.status?.any {
                            listOf(
                                PaymentStatusEnum.ERROR, PaymentStatusEnum.DECLINED
                            ).contains(it.status)
                        }?.let {
                            if (it) {
                                _transition(WebviewGatewayStatechart.Companion.Event.PaymentRejected)
                            }
                        }

                        paymentRequest.status?.any {
                            it.status == PaymentStatusEnum.CANCELLED
                        }?.let {
                            if (it) {
                                _transition(WebviewGatewayStatechart.Companion.Event.Retry)
                            }
                        }
                    }
                    return@let waitToken
                } ?: run {
                    // When no waitToken is set, we should create a new payment request
                    Log.d("WebviewGatewayViewModel", "${this.waitToken}")
                    _transition(WebviewGatewayStatechart.Companion.Event.Initialize)
                }
            }
        } catch (e: ApolloException) {
            Log.d("WebviewGatewayViewModel", "Payment Subscription error: $e")
            _transition(
                WebviewGatewayStatechart.Companion.Event.Error(KronorError.NetworkError(e))
            )
        }
    }

    suspend fun handleIntent(intent: Intent) {
        intent.data?.let { uri ->
            if (uri.scheme == "kronorcheckout" && uri.path == "/mobilepay") {
                if (uri.queryParameterNames.contains("cancel")) {
                    _transition(WebviewGatewayStatechart.Companion.Event.WaitForCancel)
                }
            }
        }
    }
}

sealed class CreditCardEvent {
    data class PaymentSuccess(val paymentId: String) : CreditCardEvent()
    object PaymentFailure : CreditCardEvent()
}

fun constructPaymentGatewayUrl(
    environment: Environment, sessionToken: String, paymentMethod: String, merchantReturnUrl: Uri
): Uri {
    val paymentGatewayHost = when (environment) {
        Environment.Staging -> {
            "payment-gateway.staging.kronor.io"
        }

        Environment.Production -> {
            "payment-gateway.kronor.io"
        }
    }
    return Uri.Builder().scheme("https").authority(paymentGatewayHost).appendPath("payment")
        .appendQueryParameter("env", toGatewayEnvName(environment))
        .appendQueryParameter("paymentMethod", paymentMethod)
        .appendQueryParameter("token", sessionToken)
        .appendQueryParameter("merchantReturnUrl", merchantReturnUrl.toString()).build()
}

fun toGatewayEnvName(environment: Environment): String {
    return when (environment) {
        Environment.Staging -> {
            "staging"
        }

        Environment.Production -> {
            "prod"
        }
    }
}
