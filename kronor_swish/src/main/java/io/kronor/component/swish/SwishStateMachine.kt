package io.kronor.component.swish

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tinder.StateMachine
import io.kronor.api.Environment
import io.kronor.api.PaymentStatusSubscription
import io.kronor.api.Requests
import io.kronor.api.type.PaymentStatusEnum
import kotlinx.coroutines.flow.*

class SwishStateMachine(val sessionToken: String) {
    private val requests = Requests()
    var stateMachine: StateMachine<SwishStatechart.Companion.State, SwishStatechart.Companion.Event, SwishStatechart.Companion.SideEffect> =
        SwishStatechart().stateMachine
    var swishState: SwishStatechart.Companion.State by mutableStateOf(SwishStatechart.Companion.State.PromptingMethod)
    var paymentRequest: PaymentStatusSubscription.PaymentRequest? by mutableStateOf(null)

    suspend fun transition(event: SwishStatechart.Companion.Event) {
        val result = stateMachine.transition(event)

        when (result) {
            is StateMachine.Transition.Valid -> {
                swishState = result.toState
                result.sideEffect?.let {
                    handleSideEffect(it)
                }
            }
            is StateMachine.Transition.Invalid -> {
                Log.d("SwishStateMachine", "Cannot transition to $event from ${result.fromState}")
            }
        }
    }

    private suspend fun handleSideEffect(sideEffect: SwishStatechart.Companion.SideEffect) {
        when (sideEffect) {
            is SwishStatechart.Companion.SideEffect.CreateMcomPaymentRequest -> {
                Log.d("SwishStateMachine", "Creating Mcom Payment Request")
                val waitToken = requests.makeNewPaymentRequest(
                    swishInputData = SwishComponentInput(
                        sessionToken = sessionToken,
                        customerSwishNumber = null,
                        returnUrl = "kronor_test://"
                    ),
                    deviceFingerprint = "fingerprint",
                    env = Environment.Staging
                )
                if (waitToken == null) {
                    transition(SwishStatechart.Companion.Event.Error("No wait token"))
                } else {
                    transition(SwishStatechart.Companion.Event.PaymentRequestCreated(waitToken = waitToken))
                }
            }
            is SwishStatechart.Companion.SideEffect.CreateEcomPaymentRequest -> {
                Log.d("SwishStateMachine", "Creating Ecom Payment Request")
                val waitToken = requests.makeNewPaymentRequest(
                    swishInputData = SwishComponentInput(
                        sessionToken = sessionToken,
                        customerSwishNumber = sideEffect.phoneNumber,
                        returnUrl = "https://kronor.io/"
                    ),
                    deviceFingerprint = "fingerprint",
                    env = Environment.Staging
                )
                if (waitToken == null) {
                    transition(SwishStatechart.Companion.Event.Error("No wait token"))
                } else {
                    transition(SwishStatechart.Companion.Event.PaymentRequestCreated(waitToken = waitToken))
                }
            }
            is SwishStatechart.Companion.SideEffect.SubscribeToPaymentStatus -> {
                Log.d("SwishStateMachine", "Subscribing to Payment Requests")
                val response = requests.getPaymentRequests(
                    sessionToken = sessionToken,
                    env = Environment.Staging
                )
                if (response == null) {
                    transition(SwishStatechart.Companion.Event.Error("No response from payment request status subscription"))
                } else {
                    response.map {
                        paymentRequest = it.firstOrNull {
                            (it.waitToken == sideEffect.waitToken) and
                                    (it.status?.all {
                                        it.status != PaymentStatusEnum.INITIALIZING
                                    } ?: false)
                        }
                        return@map paymentRequest
                    }.filterNotNull()
                        .mapNotNull {
                            if (swishState is SwishStatechart.Companion.State.WaitingForPaymentRequest)
                                transition(SwishStatechart.Companion.Event.PaymentRequestInitialized)

                            it.status?.any {
                                it.status == PaymentStatusEnum.PAID
                            }?.let {
                                if (it)
                                    transition(SwishStatechart.Companion.Event.PaymentAuthorized)
                            }

                            it.status?.any {
                                listOf(
                                    PaymentStatusEnum.ERROR,
                                    PaymentStatusEnum.DECLINED,
                                    PaymentStatusEnum.CANCELLED
                                ).contains(it.status)
                            }?.let {
                                if (it)
                                    transition(SwishStatechart.Companion.Event.PaymentRejected)
                            }
                        }
                        .collect()
                }
            }
            else -> {
                Log.d("SwishStateMachine", "$sideEffect")
            }
        }
    }
}