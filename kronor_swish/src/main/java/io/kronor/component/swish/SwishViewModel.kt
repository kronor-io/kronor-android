package io.kronor.component.swish

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
import io.kronor.api.ApiError
import io.kronor.api.KronorError
import io.kronor.api.PaymentStatusSubscription
import io.kronor.api.Requests
import io.kronor.api.type.PaymentStatusEnum
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private val DelayBeforeCallback: Duration = 2.seconds

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
    private var _deviceFingerprint: String? = null
    val deviceFingerprint: String? = _deviceFingerprint

    private val requests = Requests(swishConfiguration.sessionToken, swishConfiguration.environment)
    private var stateMachine: StateMachine<SwishStatechart.Companion.State, SwishStatechart.Companion.Event, SwishStatechart.Companion.SideEffect> =
        SwishStatechart().stateMachine
    private var _swishState: MutableState<SwishStatechart.Companion.State> =
        mutableStateOf(
            SwishStatechart.Companion.State.PromptingMethod
        )
    var swishState: State<SwishStatechart.Companion.State> = _swishState
    var paymentRequest: PaymentStatusSubscription.PaymentRequest? by mutableStateOf(null)
    private var waitToken: String? by mutableStateOf(null)
//    private var _selectedMethod: SelectedMethod? = null
    var selectedMethod: SelectedMethod? = null

//    fun setSelectedMethod(selected: SelectedMethod) {
//        this.selectedMethod = selected
//    }

    private val _events = MutableSharedFlow<SwishEvent>()
    val events: Flow<SwishEvent> = _events

    fun transition(event: SwishStatechart.Companion.Event) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                _transition(event)
            }
        }
    }

    fun setDeviceFingerPrint(fingerprint: String) {
        this._deviceFingerprint = fingerprint
    }

    private suspend fun _transition(event: SwishStatechart.Companion.Event) {

        when (val result = stateMachine.transition(event)) {
            is StateMachine.Transition.Valid -> {
                _swishState.value = result.toState
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
                (t ?: KronorError.GraphQlError(ApiError(emptyList(), emptyMap()))) as KronorError
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
                // this is wrong.
                this.selectedMethod = SelectedMethod.QrCode
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
                // this is wrong
                this.selectedMethod = SelectedMethod.PhoneNumber
            }

            is SwishStatechart.Companion.SideEffect.ListenOnPaymentRequest -> {
                this.waitToken = sideEffect.waitToken
            }

            is SwishStatechart.Companion.SideEffect.SubscribeToPaymentStatus -> {
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
//                delay(DelayBeforeCallback)
                _events.emit(SwishEvent.PaymentSuccess(sideEffect.paymentId))
            }

            is SwishStatechart.Companion.SideEffect.NotifyPaymentFailure -> {
                _events.emit(SwishEvent.PaymentFailure)
            }

            SwishStatechart.Companion.SideEffect.OpenSwishApp -> {
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
                Log.d("SwishViewModel", "Inside Collect")
                this.waitToken?.let {
                    this.paymentRequest = paymentRequestList.firstOrNull { paymentRequest ->
                        (paymentRequest.waitToken == this.waitToken) and (paymentRequest.status?.all { paymentStatus ->
                            (paymentStatus.status != PaymentStatusEnum.INITIALIZING)
                        } ?: false)
                    }

                    this.paymentRequest?.let { paymentRequest ->
                        Log.d("SwishViewModle", "after initializing: $selectedMethod and ${this.selectedMethod} and ${_swishState.value}")
                        val selected : SelectedMethod = this.selectedMethod ?: run {
                            when (paymentRequest.paymentFlow) {
                                "mcom" -> SelectedMethod.QrCode
                                "ecom" -> SelectedMethod.PhoneNumber
                                else -> SelectedMethod.QrCode
                            }
                        }
                        if (_swishState.value is SwishStatechart.Companion.State.WaitingForPaymentRequest) {
                            _transition(SwishStatechart.Companion.Event.PaymentRequestInitialized(selected))
                        }
                        if (_swishState.value is SwishStatechart.Companion.State.WaitingForSubscription) {
                            _transition(SwishStatechart.Companion.Event.PaymentRequestInitialized(selected))
                        }

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

                        paymentRequest.status?.any {
                            it.status == PaymentStatusEnum.CANCELLED
                        }?.let {
                            if (it) {
                                _transition(SwishStatechart.Companion.Event.Retry)
                            }
                        }
                    }
                    return@let waitToken
                } ?: run {
                    // When no waitToken is set, we should create a new payment request
                    Log.d("WebviewGatewayViewModel", "${this.waitToken}")
                    _transition(SwishStatechart.Companion.Event.Prompt)
                }
            }
        } catch (e: ApolloException) {
            Log.d("WebviewGatewayViewModel", "Payment Subscription error: $e")
            _transition(
                SwishStatechart.Companion.Event.Error(KronorError.NetworkError(e))
            )
        }
    }
}

sealed class SwishEvent {
    data class PaymentSuccess(val paymentId: String) : SwishEvent()
    object PaymentFailure : SwishEvent()
}