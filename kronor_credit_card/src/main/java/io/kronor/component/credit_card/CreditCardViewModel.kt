package io.kronor.component.credit_card

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tinder.StateMachine
import io.kronor.api.ApiError
import io.kronor.api.KronorError
import io.kronor.api.PaymentStatusSubscription
import io.kronor.api.Requests
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
            else -> {

            }
        }
    }
}
