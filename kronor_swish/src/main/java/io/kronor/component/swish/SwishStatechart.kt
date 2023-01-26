package io.kronor.component.swish

import com.tinder.StateMachine

enum class SelectedMethod {
    SwishApp,
    QrCode,
    PhoneNumber
}

class SwishStatechart {
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
        }

        state<State.InsertingPhoneNumber> {
            on<Event.PhoneNumberInserted> {
                transitionTo(
                    state = State.CreatingPaymentRequest(SelectedMethod.PhoneNumber),
                    sideEffect = SideEffect.CreateEcomPaymentRequest(it.phoneNumber)
                )
            }
        }

        state<State.CreatingPaymentRequest> {
            on<Event.PaymentRequestCreated> {
                transitionTo(
                    state = State.WaitingForPaymentRequest(this.selected),
                    sideEffect = SideEffect.SubscribeToPaymentStatus(it.waitToken)
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
                    state = State.PromptingMethod,
                    sideEffect = SideEffect.ResetState
                )
            }

            on<Event.SwishAppOpened> {
                transitionTo(state = State.WaitingForPayment)
            }

            on<Event.PaymentAuthorized> {
                transitionTo(
                    state = State.PaymentCompleted,
                    sideEffect = SideEffect.NotifyPaymentSuccess
                )
            }

            on<Event.PaymentRejected> {
                transitionTo(state = State.PaymentRejected)
            }

            on<Event.Error> {
                transitionTo(
                    state = State.Errored(it.error)
                )
            }
        }

        state<State.WaitingForPayment> {
            on<Event.PaymentAuthorized> {
                transitionTo(
                    state = State.PaymentCompleted,
                    sideEffect = SideEffect.NotifyPaymentSuccess
                )
            }

            on<Event.PaymentRejected> {
                transitionTo(
                    state = State.PaymentRejected
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
                    state = State.PromptingMethod,
                    sideEffect = SideEffect.ResetState
                )
            }
        }
        state<State.PaymentCompleted> {

        }
        state<State.Errored> {

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
            object PaymentCompleted : State()
            data class Errored(val error: String) : State()
        }

        sealed class Event {
            object UseSwishApp : Event()
            object UsePhoneNumber : Event()
            data class PhoneNumberInserted(val phoneNumber: String) : Event()
            object UseQR : Event()
            data class PaymentRequestCreated(val waitToken: String) : Event()
            object PaymentRequestInitialized : Event()
            object PaymentAuthorized : Event()
            object PaymentRejected : Event()
            object Retry : Event()
            object CancelFlow : Event()
            data class Error(val error: String) : Event()
            object SwishAppOpened : Event()
        }

        sealed class SideEffect {
            data class CreateEcomPaymentRequest(val phoneNumber: String) : SideEffect()
            object CreateMcomPaymentRequest : SideEffect()
            object CancelPaymentRequest : SideEffect()
            object OpenSwishApp : SideEffect()
            data class SubscribeToPaymentStatus(val waitToken: String) : SideEffect()
            object NotifyPaymentSuccess : SideEffect()
            object NotifyPaymentFailure : SideEffect()
            object ResetState : SideEffect()
        }
    }
}