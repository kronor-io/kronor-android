package io.kronor.component.swish

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tinder.StateMachine
import io.kronor.api.PaymentStatusSubscription
import io.kronor.api.Requests
import io.kronor.api.type.PaymentStatusEnum
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed class PaymentEvent {
    object Processing : PaymentEvent()
    data class Paid(val paymentId: String) : PaymentEvent()
    data class Error(val error: String?) : PaymentEvent()
}

class SwishViewModelFactory(
    private val swishConfiguration: SwishConfiguration,
    private val deviceFingerprint: String
) : ViewModelProvider.NewInstanceFactory() {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return SwishViewModel(swishConfiguration, deviceFingerprint) as T
    }
}

class SwishViewModel(
    private val swishConfiguration: SwishConfiguration,
    private val deviceFingerprint: String
) : ViewModel() {

    var paymentEvent : PaymentEvent by mutableStateOf(PaymentEvent.Processing)
        private set

    private val requests = Requests(swishConfiguration.sessionToken, swishConfiguration.environment)
    var stateMachine: StateMachine<SwishStatechart.Companion.State, SwishStatechart.Companion.Event, SwishStatechart.Companion.SideEffect> =
        SwishStatechart().stateMachine
    var swishState: SwishStatechart.Companion.State by mutableStateOf(SwishStatechart.Companion.State.PromptingMethod)
    var paymentRequest: PaymentStatusSubscription.PaymentRequest? by mutableStateOf(null)

    fun transition(event: SwishStatechart.Companion.Event) {
        viewModelScope.launch {
            _transition(event)
        }
    }

    suspend fun _transition(event: SwishStatechart.Companion.Event) {

            when (val result = stateMachine.transition(event)) {
                is StateMachine.Transition.Valid -> {
                    swishState = result.toState
                    result.sideEffect?.let {
                        handleSideEffect(it)
                    }
                }
                is StateMachine.Transition.Invalid -> {
                    Log.d(
                        "SwishStateMachine",
                        "Cannot transition to $event from ${result.fromState}"
                    )
                }

        }
    }

    private suspend fun handleSideEffect(sideEffect: SwishStatechart.Companion.SideEffect) {
        when (sideEffect) {
            is SwishStatechart.Companion.SideEffect.CreateMcomPaymentRequest -> {
                Log.d("SwishStateMachine", "Creating Mcom Payment Request")
                val waitToken = requests.makeNewPaymentRequest(
                    swishInputData = SwishComponentInput(
                        customerSwishNumber = null,
                        returnUrl = swishConfiguration.redirectUrl.toString(),
                        deviceFingerprint = deviceFingerprint,
                        appName = swishConfiguration.appName,
                        appVersion = swishConfiguration.appVersion
                    )
                )
                if (waitToken == null) {
                    _transition(SwishStatechart.Companion.Event.Error("No wait token"))
                } else {
                    _transition(SwishStatechart.Companion.Event.PaymentRequestCreated(waitToken = waitToken))
                }
            }
            is SwishStatechart.Companion.SideEffect.CreateEcomPaymentRequest -> {
                Log.d("SwishStateMachine", "Creating Ecom Payment Request")
                val waitToken = requests.makeNewPaymentRequest(
                    swishInputData = SwishComponentInput(
                        customerSwishNumber = sideEffect.phoneNumber,
                        returnUrl = swishConfiguration.redirectUrl.toString(),
                        deviceFingerprint = deviceFingerprint,
                        appName = swishConfiguration.appName,
                        appVersion = swishConfiguration.appVersion
                    ),
                )
                if (waitToken == null) {
                    _transition(SwishStatechart.Companion.Event.Error("No wait token"))
                } else {
                    _transition(SwishStatechart.Companion.Event.PaymentRequestCreated(waitToken = waitToken))
                }
            }
            is SwishStatechart.Companion.SideEffect.SubscribeToPaymentStatus -> {
                Log.d("SwishStateMachine", "Subscribing to Payment Requests")
                requests.getPaymentRequests()?.map { paymentRequestList ->
                    paymentRequest = paymentRequestList.firstOrNull { paymentRequest ->
                        (paymentRequest.waitToken == sideEffect.waitToken) and (paymentRequest.status?.all { paymentStatus ->
                            paymentStatus.status != PaymentStatusEnum.INITIALIZING
                        } ?: false)
                    }
                    return@map paymentRequest
                }?.filterNotNull()?.mapNotNull { paymentRequest ->
                    if (swishState is SwishStatechart.Companion.State.WaitingForPaymentRequest) _transition(
                        SwishStatechart.Companion.Event.PaymentRequestInitialized
                    )

                    paymentRequest.status?.any {
                        it.status == PaymentStatusEnum.PAID
                    }?.let {
                        if (it) {
                            paymentEvent = (PaymentEvent.Paid(paymentRequest.resultingPaymentId!!))
                            _transition(SwishStatechart.Companion.Event.PaymentAuthorized)
                        }
                    }

                    paymentRequest.status?.any {
                        listOf(
                            PaymentStatusEnum.ERROR,
                            PaymentStatusEnum.DECLINED,
                            PaymentStatusEnum.CANCELLED
                        ).contains(it.status)
                    }?.let {
                        if (it) {
                            paymentEvent = PaymentEvent.Error(
                                paymentRequest.transactionSwishDetails?.firstOrNull()?.errorCode
                            )
                            _transition(SwishStatechart.Companion.Event.PaymentRejected)
                        }
                    }
                }?.collect()
                    ?: suspend {
                        paymentEvent = PaymentEvent.Error(
                            "No payment requests found"
                        )
                        _transition(SwishStatechart.Companion.Event.Error("No response from payment request status subscription"))
                    }
            }
            else -> {
                Log.d("SwishStateMachine", "$sideEffect")
            }
        }
    }

    fun observePaymentEvent(callback: (PaymentEvent) -> Unit) {
        callback(paymentEvent)
    }
}