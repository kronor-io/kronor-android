package io.kronor.api

sealed class PaymentEvent {
    data class PaymentSuccess(val paymentId: String) : PaymentEvent()
    data class PaymentFailure(val reason: FailureReason) : PaymentEvent()
}

enum class FailureReason {
    Cancelled, Declined
}