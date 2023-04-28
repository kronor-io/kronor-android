package io.kronor.api

sealed class PaymentEvent {
    data class PaymentSuccess(val paymentId: String) : PaymentEvent()
    object PaymentFailure : PaymentEvent()
}