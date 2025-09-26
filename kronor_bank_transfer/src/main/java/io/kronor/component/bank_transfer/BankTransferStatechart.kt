package io.kronor.component.bank_transfer

import android.content.Context
import com.tinder.StateMachine
import io.kronor.api.KronorError

internal class BankTransferStatechart {
    val stateMachine = StateMachine.create<State, Event, SideEffect> {
        initialState(State.Initializing)

        state<State.Initializing> {
            on<Event.Initialize> {
                transitionTo(
                    state = State.CreatingPaymentRequest,
                    sideEffect = SideEffect.CreatePaymentRequest(it.context)
                )
            }
        }
        state<State.CreatingPaymentRequest> {
            on<Event.PaymentRequestCreated> {
                transitionTo(
                    state = State.WaitingForPaymentRequest,
                    sideEffect = SideEffect.ListenOnPaymentRequest(it.waitToken)
                )
            }

            on<Event.Error> {
                transitionTo(
                    state = State.Errored(it.error)
                )
            }
        }

        state<State.WaitingForPaymentRequest> {
            on<Event.PaymentRequestInitialized> {
                val sideEffect: SideEffect = SideEffect.OpenBankTransferWebView
                transitionTo(state = State.PaymentRequestInitialized(it.trustlyCheckoutUrl), sideEffect)
            }
            on<Event.PaymentAuthorized> {
                transitionTo(
                    state = State.PaymentCompleted(it.paymentId),
                    sideEffect = SideEffect.NotifyPaymentSuccess(it.paymentId)
                )
            }
            on<Event.Error> {
                transitionTo(state = State.Errored(it.error))
            }
        }

        state<State.PaymentRequestInitialized> {
            on<Event.Retry> {
                transitionTo(
                    state = State.Initializing
                )
            }

            on<Event.CancelFlow> {
                dontTransition(sideEffect = SideEffect.CancelPaymentRequest)
            }

            on<Event.BankTransferWebViewOpened> {
                transitionTo(state = State.WaitingForPayment)
            }

            on<Event.PaymentAuthorized> {
                transitionTo(
                    state = State.PaymentCompleted(it.paymentId),
                    sideEffect = SideEffect.NotifyPaymentSuccess(it.paymentId)
                )
            }

            on<Event.PaymentRejected> {
                transitionTo(state = State.PaymentRejected, sideEffect = SideEffect.NotifyPaymentFailure)
            }

            on<Event.Error> {
                transitionTo(
                    state = State.Errored(it.error)
                )
            }
        }

        state<State.WaitingForPayment> {
            on<Event.Retry> {
                transitionTo(
                    state = State.Initializing
                )
            }

            on<Event.CancelFlow> {
                dontTransition(sideEffect = SideEffect.CancelPaymentRequest)
            }

            on<Event.PaymentAuthorized> {
                transitionTo(
                    state = State.PaymentCompleted(it.paymentId),
                    sideEffect = SideEffect.NotifyPaymentSuccess(it.paymentId)
                )
            }

            on<Event.PaymentRejected> {
                transitionTo(
                    state = State.PaymentRejected,
                    sideEffect = SideEffect.NotifyPaymentFailure
                )
            }

            on<Event.Error> {
                transitionTo(state = State.Errored(it.error))
            }
        }

        state<State.PaymentRejected> {
            on<Event.CancelFlow> {
                dontTransition(sideEffect = SideEffect.NotifyPaymentFailure)
            }

            on<Event.Retry> {
                transitionTo(
                    state = State.Initializing,
                    sideEffect = SideEffect.ResetState
                )
            }

            on<Event.Error> {
                transitionTo(
                    state = State.Errored(it.error)
                )
            }
        }
        state<State.PaymentCompleted> {

        }
        state<State.Errored> {
            on<Event.CancelFlow> {
                dontTransition(sideEffect = SideEffect.NotifyPaymentFailure)
            }

            on<Event.Retry> {
                transitionTo(
                    state = State.Initializing,
                    sideEffect = SideEffect.ResetState
                )
            }

            on<Event.Error> {
                transitionTo(
                    state = State.Errored(it.error)
                )
            }
        }
    }

    companion object {
        sealed class State {
            data object Initializing : State()
            data object CreatingPaymentRequest : State()
            data object WaitingForPaymentRequest : State()
            data class PaymentRequestInitialized(val trustlyCheckoutUrl: String) : State()
            data object WaitingForPayment : State()
            data object PaymentRejected : State()
            data class PaymentCompleted(val paymentId: String) : State()
            data class Errored(val error: KronorError) : State()
        }

        sealed class Event {
            data class Initialize(val context: Context) : Event()
            data object BankTransferWebViewOpened : Event()
            data class PaymentRequestCreated(val waitToken: String) : Event()
            data class PaymentRequestInitialized(val trustlyCheckoutUrl: String) : Event()
            data class PaymentAuthorized(val paymentId: String) : Event()
            data object PaymentRejected : Event()
            data object Retry : Event()
            data object CancelFlow : Event()
            data class Error(val error: KronorError) : Event()
        }

        sealed class SideEffect {
            data class CreatePaymentRequest(val context: Context) : SideEffect()
            data object CancelPaymentRequest : SideEffect()
            data object OpenBankTransferWebView : SideEffect()
            data class ListenOnPaymentRequest(val waitToken: String) : SideEffect()
            data class NotifyPaymentSuccess(val paymentId: String) : SideEffect()
            data object NotifyPaymentFailure : SideEffect()
            data object ResetState : SideEffect()
        }
    }
}