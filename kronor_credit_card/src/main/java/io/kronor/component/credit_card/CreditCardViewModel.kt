package io.kronor.component.credit_card

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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val DelayBeforeCallback: Long = 2000 // 2000 milliseconds = 2 seconds

class CreditCardViewModelFactory(
    private val creditCardConfiguration: CreditCardConfiguration
) : ViewModelProvider.NewInstanceFactory() {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return CreditCardViewModel(creditCardConfiguration) as T
    }
}

class CreditCardViewModel(
    private val creditCardConfiguration: CreditCardConfiguration
) : ViewModel() {
    var deviceFingerprint: String? = null

    private val requests =
        Requests(creditCardConfiguration.sessionToken, creditCardConfiguration.environment)
    var stateMachine: StateMachine<CreditCardStatechart.Companion.State, CreditCardStatechart.Companion.Event, CreditCardStatechart.Companion.SideEffect> =
        CreditCardStatechart().stateMachine
    var creditCardState: CreditCardStatechart.Companion.State by mutableStateOf(CreditCardStatechart.Companion.State.Initializing)
    var paymentRequest: PaymentStatusSubscription.PaymentRequest? by mutableStateOf(null)
    private var waitToken: String? by mutableStateOf(null)

    fun transition(event: CreditCardStatechart.Companion.Event) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                _transition(event)
            }
        }
    }

    private suspend fun _transition(event: CreditCardStatechart.Companion.Event) {

        when (val result = stateMachine.transition(event)) {
            is StateMachine.Transition.Valid -> {
                creditCardState = result.toState
                result.sideEffect?.let {
                    handleSideEffect(it)
                }
            }
            is StateMachine.Transition.Invalid -> {
                Log.d(
                    "CreditCardViewModel", "Cannot transition to $event from ${result.fromState}"
                )
            }
        }
    }

    private suspend fun _transitionToError(t: Throwable?) {
        _transition(
            CreditCardStatechart.Companion.Event.Error(
                (t ?: KronorError.graphQlError(ApiError(emptyList(), emptyMap()))) as KronorError
            )
        )
    }

    private suspend fun handleSideEffect(sideEffect: CreditCardStatechart.Companion.SideEffect) {
        when (sideEffect) {
            is CreditCardStatechart.Companion.SideEffect.CreatePaymentRequest -> {
                Log.d("CreditCardViewModel", "Creating Payment Request")
                val waitToken = requests.makeNewPaymentRequest(
                    creditCardInputData = CreditCardComponentInput(
                        returnUrl = creditCardConfiguration.redirectUrl.toString(),
                        deviceFingerprint = deviceFingerprint ?: "fingerprint not found",
                        appName = creditCardConfiguration.appName,
                        appVersion = creditCardConfiguration.appVersion
                    )
                )
                when {
                    waitToken.isFailure -> {
                        Log.d(
                            "CreditCardViewModel",
                            "Error creating payment request: ${waitToken.exceptionOrNull()}"
                        )
                        _transitionToError(waitToken.exceptionOrNull())
                    }
                    waitToken.isSuccess -> {
                        _transition(CreditCardStatechart.Companion.Event.PaymentRequestCreated(
                            waitToken = waitToken.getOrNull()!!
                        ))
                    }
                }
            }
            is CreditCardStatechart.Companion.SideEffect.ListenOnPaymentRequest -> {
                this.waitToken = sideEffect.waitToken
            }
            is CreditCardStatechart.Companion.SideEffect.SubscribeToPaymentStatus -> {
                Log.d("CreditCardViewModel", "Subscribing to Payment Requests")
                try {
                    requests.getPaymentRequests().collect { paymentRequestList ->
                        // If we have a waitToken set in our view model, get the payment request
                        // associated with that waitToken and in a status that is not initializing
                        this.waitToken?.let {
                            this.paymentRequest = paymentRequestList.firstOrNull { paymentRequest ->
                                (paymentRequest.waitToken == this.waitToken) and (paymentRequest.status?.all { paymentStatus ->
                                    (paymentStatus.status != PaymentStatusEnum.INITIALIZING )
                                } ?: false)
                            }

                            this.paymentRequest?.let { paymentRequest ->
                                if (creditCardState is CreditCardStatechart.Companion.State.WaitingForPaymentRequest) {
                                    _transition(CreditCardStatechart.Companion.Event.PaymentRequestInitialized)
                                }
                                if (creditCardState is CreditCardStatechart.Companion.State.WaitingForSubscription) {
                                    _transition(CreditCardStatechart.Companion.Event.PaymentRequestInitialized)
                                }

                                paymentRequest.status?.any {
                                    it.status == PaymentStatusEnum.PAID
                                }?.let {
                                    if (it) {
                                        _transition(CreditCardStatechart.Companion.Event.PaymentAuthorized(paymentRequest.resultingPaymentId!!))
                                    }
                                }

                                paymentRequest.status?.any {
                                    listOf(
                                        PaymentStatusEnum.ERROR, PaymentStatusEnum.DECLINED
                                    ).contains(it.status)
                                }?.let {
                                    if (it) {
                                       _transition(CreditCardStatechart.Companion.Event.PaymentRejected)
                                    }
                                }

                                paymentRequest.status?.any {
                                    it.status == PaymentStatusEnum.CANCELLED
                                }?.let {
                                 if (it) {
                                     _transition(CreditCardStatechart.Companion.Event.Retry)
                                 }
                                }
                            }
                        } ?: run {
                            // When no waitToken is set, we should create a new payment request
                            _transition(CreditCardStatechart.Companion.Event.Initialize)
                        }
                    }
                } catch (e: ApolloException) {
                    Log.d("CreditCardViewModel", "Payment Subscription error: $e")
                    _transition(CreditCardStatechart.Companion.Event.Error(KronorError.networkError(e)))
                }
            }
            is CreditCardStatechart.Companion.SideEffect.CancelPaymentRequest -> {
                Log.d("CreditCardViewModel", "Reset payment flow")
                val waitToken = requests.cancelPayment()
                when {
                    waitToken.isFailure -> {
                        Log.d("CreditCardViewModel", "Failed to cancel payment request: ${waitToken.exceptionOrNull()}")
                        _transitionToError(waitToken.exceptionOrNull())
                    }
                    waitToken.isSuccess -> {}
                }
            }
            is CreditCardStatechart.Companion.SideEffect.ResetState -> {

            }
            is CreditCardStatechart.Companion.SideEffect.NotifyPaymentSuccess -> {
                delay(DelayBeforeCallback)
                creditCardConfiguration.onPaymentSuccess(sideEffect.paymentId)
            }
            is CreditCardStatechart.Companion.SideEffect.NotifyPaymentFailure -> {
                creditCardConfiguration.onPaymentFailure()
            }
            CreditCardStatechart.Companion.SideEffect.OpenEmbeddedSite -> {

            }
            CreditCardStatechart.Companion.SideEffect.CancelAndNotifyFailure -> {

            }
        }
    }
}
