package io.kronor.component.swish

import com.tinder.StateMachine
import io.kronor.api.FailureReason
import io.kronor.api.KronorError

internal enum class SelectedMethod {
    SwishApp,
    QrCode,
    PhoneNumber
}

internal class SwishStatechart {
    val stateMachine = StateMachine.create<State, Event, SideEffect> {
        initialState(State.PromptingMethod)

        state<State.PromptingMethod> {
            on<Event.UseSwishApp> {
                transitionTo(
                    state = State.CreatingPaymentRequest(SelectedMethod.SwishApp),
                    sideEffect = SideEffect.CreateMcomPaymentRequest
                )
            }
            on<Event.UseQR> {
                transitionTo(
                    state = State.CreatingPaymentRequest(SelectedMethod.QrCode),
                    sideEffect = SideEffect.CreateMcomPaymentRequest
                )
            }
            on<Event.UsePhoneNumber> {
                transitionTo(state = State.InsertingPhoneNumber)
            }
            on<Event.PhoneNumberInserted> {
                transitionTo(
                    state = State.CreatingPaymentRequest(SelectedMethod.PhoneNumber),
                    sideEffect = SideEffect.CreateEcomPaymentRequest(it.phoneNumber)
                )
            }
            on<Event.PaymentRequestInitialized> {
                transitionTo(
                    state = State.PaymentRequestInitialized(it.selected)
                )
            }
            on<Event.Error> {
                transitionTo(
                    state = State.Errored(it.error)
                )
            }
        }

        state<State.InsertingPhoneNumber> {
            on<Event.PhoneNumberInserted> {
                transitionTo(
                    state = State.CreatingPaymentRequest(SelectedMethod.PhoneNumber),
                    sideEffect = SideEffect.CreateEcomPaymentRequest(it.phoneNumber)
                )
            }
            on<Event.Error> {
                transitionTo(
                    state = State.Errored(it.error)
                )
            }
        }

        state<State.CreatingPaymentRequest> {
            on<Event.PaymentRequestCreated> {
                transitionTo(
                    state = State.WaitingForPaymentRequest(this.selected),
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
                val sideEffect: SideEffect? =
                    if (this.selected == SelectedMethod.SwishApp) SideEffect.OpenSwishApp else null
                transitionTo(state = State.PaymentRequestInitialized(this.selected), sideEffect)
            }
            on<Event.Error> {
                transitionTo(state = State.Errored(it.error))
            }
        }

        state<State.PaymentRequestInitialized> {
            on<Event.Retry> {
                transitionTo(
                    state = State.PromptingMethod
                )
            }

            on<Event.CancelFlow> {
                dontTransition(sideEffect = SideEffect.CancelPaymentRequest)
            }

            on<Event.SwishAppOpened> {
                transitionTo(state = State.WaitingForPayment)
            }

            on<Event.PaymentAuthorized> {
                transitionTo(
                    state = State.PaymentCompleted(it.paymentId),
                    sideEffect = SideEffect.NotifyPaymentSuccess(it.paymentId)
                )
            }

            on<Event.PaymentRejected> {
                transitionTo(state = State.PaymentRejected, sideEffect = SideEffect.NotifyPaymentFailure(FailureReason.Declined))
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
                    state = State.PromptingMethod
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
                    sideEffect = SideEffect.NotifyPaymentFailure(FailureReason.Declined)
                )
            }

            on<Event.Error> {
                transitionTo(state = State.Errored(it.error))
            }
        }

        state<State.PaymentRejected> {
            on<Event.CancelFlow> {
                dontTransition(sideEffect = SideEffect.NotifyPaymentFailure(FailureReason.Cancelled))
            }

            on<Event.Retry> {
                transitionTo(
                    state = State.PromptingMethod,
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
                dontTransition(sideEffect = SideEffect.NotifyPaymentFailure(FailureReason.Cancelled))
            }

            on<Event.Retry> {
                transitionTo(
                    state = State.PromptingMethod,
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
            object PromptingMethod : State()
            object InsertingPhoneNumber : State()
            data class CreatingPaymentRequest(val selected: SelectedMethod) : State()
            data class WaitingForPaymentRequest(val selected: SelectedMethod) : State()
            data class PaymentRequestInitialized(val selected: SelectedMethod) : State()
            object WaitingForPayment : State()
            object PaymentRejected : State()
            data class PaymentCompleted(val paymentId: String) : State()
            data class Errored(val error: KronorError) : State()
        }

        sealed class Event {
            object Prompt : Event()
            object UseSwishApp : Event()
            object UsePhoneNumber : Event()
            data class PhoneNumberInserted(val phoneNumber: String) : Event()
            object UseQR : Event()
            data class PaymentRequestCreated(val waitToken: String) : Event()
            data class PaymentRequestInitialized(val selected: SelectedMethod) : Event()
            data class PaymentAuthorized(val paymentId: String) : Event()
            object PaymentRejected : Event()
            object Retry : Event()
            object CancelFlow : Event()
            data class Error(val error: KronorError) : Event()
            object SwishAppOpened : Event()
        }

        sealed class SideEffect {
            data class CreateEcomPaymentRequest(val phoneNumber: String) : SideEffect()
            object CreateMcomPaymentRequest : SideEffect()
            object CancelPaymentRequest : SideEffect()
            object OpenSwishApp : SideEffect()
            data class ListenOnPaymentRequest(val waitToken: String) : SideEffect()
            data class NotifyPaymentSuccess(val paymentId: String) : SideEffect()
            data class NotifyPaymentFailure(val failureReason: FailureReason) : SideEffect()
            object ResetState : SideEffect()
        }
    }
}