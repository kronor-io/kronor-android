package io.kronor.component.webview_payment_gateway

import com.tinder.StateMachine
import io.kronor.api.KronorError

internal class WebviewGatewayStatechart {
    val stateMachine = StateMachine.create<State, Event, SideEffect> {
        initialState(State.WaitingForSubscription)

        state<State.WaitingForSubscription> {
            on<Event.SubscribeToPaymentStatus> {
                dontTransition(sideEffect = SideEffect.SubscribeToPaymentStatus)
            }
            on<Event.Initialize> {
                transitionTo(
                    state = State.CreatingPaymentRequest,
                    sideEffect = SideEffect.CreatePaymentRequest
                )
            }
            on<Event.PaymentRequestInitialized> {
                transitionTo(
                    state = State.PaymentRequestInitialized,
                    sideEffect = SideEffect.OpenEmbeddedSite
                )
            }
            on<Event.PaymentAuthorized> {
                transitionTo(
                    state = State.PaymentCompleted,
                    sideEffect = SideEffect.NotifyPaymentSuccess(paymentId = it.paymentId)
                )
            }
            on<Event.PaymentRejected> {
                transitionTo(
                    state = State.PaymentRejected, sideEffect = SideEffect.NotifyPaymentFailure
                )
            }
            on<Event.Error> {
                transitionTo(
                    state = State.Errored(it.error)
                )
            }
        }

        state<State.Initializing> {
            on<Event.Initialize> {
                transitionTo(
                    state = State.CreatingPaymentRequest,
                    sideEffect = SideEffect.CreatePaymentRequest
                )
            }
        }

        state<State.CreatingPaymentRequest> {
            on<Event.PaymentRequestCreated> {
                transitionTo(
                    state = State.WaitingForPaymentRequest,
                    sideEffect = SideEffect.ListenOnPaymentRequest(waitToken = it.waitToken)
                )
            }

            on<Event.Error> {
                transitionTo(state = State.Errored(error = it.error))
            }
        }

        state<State.WaitingForPaymentRequest> {
            on<Event.PaymentRequestInitialized> {
                transitionTo(
                    state = State.PaymentRequestInitialized,
                    sideEffect = SideEffect.OpenEmbeddedSite
                )
            }

            on<Event.Error> {
                transitionTo(state = State.Errored(error = it.error))
            }
        }

        state<State.PaymentRequestInitialized> {
            on<Event.PaymentAuthorized> {
                transitionTo(
                    state = State.PaymentCompleted,
                    sideEffect = SideEffect.NotifyPaymentSuccess(paymentId = it.paymentId)
                )
            }
            on<Event.CancelFlow> {
                dontTransition(sideEffect = SideEffect.CancelPaymentRequest)
            }
            on<Event.PaymentRejected> {
                transitionTo(
                    state = State.PaymentRejected,
                    sideEffect = SideEffect.NotifyPaymentFailure
                )
            }
            on<Event.Error> {
                transitionTo(state = State.Errored(error = it.error))
            }
            on<Event.Cancel> {
                transitionTo(
                    state = State.PaymentRejected, sideEffect = SideEffect.CancelAndNotifyFailure
                )
            }
            on<Event.WaitForCancel> {
                transitionTo(
                    state = State.WaitingForPayment, sideEffect = SideEffect.CancelAfterDeadline
                )
            }
        }

        state<State.WaitingForPayment> {
            on<Event.PaymentAuthorized> {
                transitionTo(
                    state = State.PaymentCompleted,
                    sideEffect = SideEffect.NotifyPaymentSuccess(paymentId = it.paymentId)
                )
            }
            on<Event.PaymentRejected> {
                transitionTo(
                    state = State.PaymentRejected,
                    sideEffect = SideEffect.NotifyPaymentFailure
                )
            }
            on<Event.Error> {
                transitionTo(state = State.Errored(error = it.error))
            }
            on<Event.Cancel> {
                transitionTo(
                    state = State.PaymentRejected, sideEffect = SideEffect.CancelAndNotifyFailure
                )
            }
            on<Event.CancelFlow> {
                dontTransition(sideEffect = SideEffect.CancelPaymentRequest)
            }
        }

        state<State.PaymentRejected> {
            on<Event.CancelFlow> {
                dontTransition(sideEffect = SideEffect.NotifyPaymentFailure)
            }
            on<Event.Retry> {
                transitionTo(State.Initializing, sideEffect = SideEffect.ResetState)
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
                    state = State.WaitingForSubscription,
                    sideEffect = SideEffect.SubscribeToPaymentStatus
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
            object WaitingForSubscription : State()
            object Initializing : State()
            object CreatingPaymentRequest : State()
            object WaitingForPaymentRequest : State()
            object PaymentRequestInitialized : State()
            object WaitingForPayment : State()
            object PaymentRejected : State()
            object PaymentCompleted : State()
            data class Errored(val error: KronorError) : State()
        }

        sealed class Event {
            object SubscribeToPaymentStatus : Event()
            object Initialize : Event()
            data class PaymentRequestCreated(val waitToken: String) : Event()
            object PaymentRequestInitialized : Event()
            data class PaymentAuthorized(val paymentId: String) : Event()
            object PaymentRejected : Event()
            object Cancel : Event()
            object Retry : Event()
            object CancelFlow : Event()
            data class Error(val error: KronorError) : Event()
            object WaitForCancel : Event()
        }

        sealed class SideEffect {
            object CreatePaymentRequest : SideEffect()
            object OpenEmbeddedSite : SideEffect()
            object CancelPaymentRequest : SideEffect()
            object SubscribeToPaymentStatus : SideEffect()
            data class ListenOnPaymentRequest(val waitToken: String) : SideEffect()
            data class NotifyPaymentSuccess(val paymentId: String) : SideEffect()
            object NotifyPaymentFailure : SideEffect()
            object ResetState : SideEffect()
            object CancelAndNotifyFailure : SideEffect()
            object CancelAfterDeadline : SideEffect()
        }
    }
}