package io.kronor.component.swish

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.apollographql.apollo3.exception.ApolloException
import com.tinder.StateMachine
import io.kronor.api.ApiError
import io.kronor.api.KronorError
import io.kronor.api.PaymentStatusSubscription
import io.kronor.api.Requests
import io.kronor.api.type.PaymentStatusEnum
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val DelayBeforeCallback: Long = 2000 // 2000 milliseconds = 2 seconds

class SwishViewModelFactory(
    private val swishConfiguration: SwishConfiguration
) : ViewModelProvider.NewInstanceFactory() {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return SwishViewModel(swishConfiguration) as T
    }
}

class SwishViewModel(
    private val swishConfiguration: SwishConfiguration
) : ViewModel() {

    var deviceFingerprint: String? = null

    private val requests = Requests(swishConfiguration.sessionToken, swishConfiguration.environment)
    var stateMachine: StateMachine<SwishStatechart.Companion.State, SwishStatechart.Companion.Event, SwishStatechart.Companion.SideEffect> =
        SwishStatechart().stateMachine
    var swishState: SwishStatechart.Companion.State by mutableStateOf(SwishStatechart.Companion.State.PromptingMethod)
    var paymentRequest: PaymentStatusSubscription.PaymentRequest? by mutableStateOf(null)
    private var waitToken: String? by mutableStateOf(null)
    private var selectedMethod: SelectedMethod? by mutableStateOf(null)

    fun transition(event: SwishStatechart.Companion.Event) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                _transition(event)
            }
        }
    }

    private suspend fun _transition(event: SwishStatechart.Companion.Event) {

        when (val result = stateMachine.transition(event)) {
            is StateMachine.Transition.Valid -> {
                swishState = result.toState
                result.sideEffect?.let {
                    handleSideEffect(it)
                }
            }
            is StateMachine.Transition.Invalid -> {
                Log.d(
                    "SwishViewModel", "Cannot transition to $event from ${result.fromState}"
                )
            }
        }
    }

    private suspend fun _transitionToError(t: Throwable?) {
        _transition(
            SwishStatechart.Companion.Event.Error(
                (t ?: KronorError.graphQlError(ApiError(emptyList(), emptyMap()))) as KronorError
            )
        )
    }

    private suspend fun handleSideEffect(sideEffect: SwishStatechart.Companion.SideEffect) {
        when (sideEffect) {
            is SwishStatechart.Companion.SideEffect.CreateMcomPaymentRequest -> {
                Log.d("SwishViewModel", "Creating Mcom Payment Request")
                val waitToken = requests.makeNewPaymentRequest(
                    swishInputData = SwishComponentInput(
                        customerSwishNumber = null,
                        returnUrl = swishConfiguration.redirectUrl.toString(),
                        deviceFingerprint = deviceFingerprint ?: "fingerprint not found",
                        appName = swishConfiguration.appName,
                        appVersion = swishConfiguration.appVersion
                    )
                )
                when {
                    waitToken.isFailure -> {
                        Log.d(
                            "SwishViewModel",
                            "Error creating mcom request: ${waitToken.exceptionOrNull()}"
                        )
                        _transitionToError(waitToken.exceptionOrNull())
                    }
                    waitToken.isSuccess -> {
                        _transition(
                            SwishStatechart.Companion.Event.PaymentRequestCreated(
                                waitToken = waitToken.getOrNull()!!
                            )
                        )
                    }
                }
            }
            is SwishStatechart.Companion.SideEffect.CreateEcomPaymentRequest -> {
                Log.d("SwishViewModel", "Creating Ecom Payment Request")
                val waitToken = requests.makeNewPaymentRequest(
                    swishInputData = SwishComponentInput(
                        customerSwishNumber = sideEffect.phoneNumber,
                        returnUrl = swishConfiguration.redirectUrl.toString(),
                        deviceFingerprint = deviceFingerprint ?: "fingerprint not found",
                        appName = swishConfiguration.appName,
                        appVersion = swishConfiguration.appVersion
                    ),
                )
                when {
                    waitToken.isFailure -> {
                        Log.d(
                            "SwishViewModel",
                            "Error creating ecom request: ${waitToken.exceptionOrNull()}"
                        )
                        _transitionToError(waitToken.exceptionOrNull())
                    }
                    waitToken.isSuccess -> {
                        _transition(
                            SwishStatechart.Companion.Event.PaymentRequestCreated(
                                waitToken = waitToken.getOrNull()!!
                            )
                        )
                    }
                }
            }
            is SwishStatechart.Companion.SideEffect.ListenOnPaymentRequest -> {
                this.waitToken = sideEffect.waitToken
            }
            is SwishStatechart.Companion.SideEffect.SubscribeToPaymentStatus -> {
                Log.d("SwishViewModel", "Subscribing to Payment Requests")
                try {
                    requests.getPaymentRequests().map { paymentRequestList ->
                        this.waitToken?.let {
                            paymentRequest = paymentRequestList.firstOrNull { paymentRequest ->
                                (paymentRequest.waitToken == this.waitToken) and (paymentRequest.status?.all { paymentStatus ->
                                    (paymentStatus.status != PaymentStatusEnum.INITIALIZING)
                                } ?: false)
                            }
                            return@map paymentRequest
                        } ?: run {
                            _transition(SwishStatechart.Companion.Event.Prompt)
                            null
                        }
                    }.filterNotNull().mapNotNull { paymentRequest ->
                        if (swishState is SwishStatechart.Companion.State.WaitingForPaymentRequest) _transition(
                            SwishStatechart.Companion.Event.PaymentRequestInitialized
                        )

                        paymentRequest.status?.any {
                            it.status == PaymentStatusEnum.PAID
                        }?.let {
                            if (it) {
                                _transition(
                                    SwishStatechart.Companion.Event.PaymentAuthorized(
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
                                _transition(SwishStatechart.Companion.Event.PaymentRejected)
                            }
                        }
                        paymentRequest.status?.any { it.status == PaymentStatusEnum.CANCELLED }
                            ?.let {
                                if (it) {
                                    _transition(SwishStatechart.Companion.Event.Retry)
                                }
                            }

                    }.collect()
                } catch (e: ApolloException) {
                    Log.d("SwishViewModel", "Payment Subscription error: $e")
                    _transition(SwishStatechart.Companion.Event.Error(KronorError.networkError(e)))
                }
            }
            is SwishStatechart.Companion.SideEffect.CancelPaymentRequest -> {
                Log.d("SwishViewModel", "reset payment flow")
                val waitToken = requests.cancelPayment()
                when {
                    waitToken.isFailure -> {
                        Log.d(
                            "SwishViewModel",
                            "Failed to cancel payment request: ${waitToken.exceptionOrNull()}"
                        )
                        _transitionToError(waitToken.exceptionOrNull())
                    }
                    waitToken.isSuccess -> {}
                }
            }
            is SwishStatechart.Companion.SideEffect.ResetState -> {

            }
            is SwishStatechart.Companion.SideEffect.NotifyPaymentSuccess -> {
                delay(DelayBeforeCallback)
                swishConfiguration.onPaymentSuccess(sideEffect.paymentId)
            }
            is SwishStatechart.Companion.SideEffect.NotifyPaymentFailure -> {
                swishConfiguration.onPaymentFailure()
            }
            SwishStatechart.Companion.SideEffect.OpenSwishApp -> {
            }
        }
    }
}