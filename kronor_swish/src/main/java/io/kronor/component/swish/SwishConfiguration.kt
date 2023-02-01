package io.kronor.component.swish

import android.net.Uri
import androidx.annotation.DrawableRes
import io.kronor.api.Environment
import java.util.*

data class SwishConfiguration(
    val sessionToken: String,
    val environment: Environment,
    val locale: Locale,
    val redirectUrl: Uri,
    val swishFlow: SupportedSwishFlows = SupportedSwishFlows.All,
    val appName: String,
    val appVersion: String,
    val onSuccess: (paymentId: String) -> Unit,
    val onFailure: () -> Unit,
    @DrawableRes val merchantLogo: Int? = null
)

enum class SupportedSwishFlows {
    SwishApp, SwishQr, PayWithSwishNumber, All
}