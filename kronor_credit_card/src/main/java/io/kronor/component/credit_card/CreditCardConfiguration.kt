package io.kronor.component.credit_card

import android.net.Uri
import androidx.annotation.DrawableRes
import io.kronor.api.Environment

data class CreditCardConfiguration(
    val sessionToken: String,
    val environment: Environment,
    val redirectUrl: Uri,
    val appName: String,
    val appVersion: String,
    @DrawableRes
    val merchantLogo: Int? = null,
    val onPaymentFailure : () -> Unit,
    val onPaymentSuccess : (String) -> Unit
)
