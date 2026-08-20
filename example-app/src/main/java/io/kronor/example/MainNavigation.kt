package io.kronor.example

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import io.kronor.api.Environment
import io.kronor.api.PaymentConfiguration
import io.kronor.api.PaymentEvent
import io.kronor.api.PaymentMethod
import io.kronor.component.bank_transfer.BankTransferComponent
import io.kronor.component.bank_transfer.bankTransferViewModel
import io.kronor.component.credit_card.CreditCardComponent
import io.kronor.component.credit_card.creditCardViewModel
import io.kronor.component.fallback.FallbackComponent
import io.kronor.component.fallback.fallbackViewModel
import io.kronor.component.googlepay.GooglePayComponent
import io.kronor.component.googlepay.googlePayViewModel
import io.kronor.component.mobilepay.MobilePayComponent
import io.kronor.component.mobilepay.mobilePayViewModel
import io.kronor.component.paypal.PayPalComponent
import io.kronor.component.paypal.paypalViewModel
import io.kronor.component.swish.SwishComponent
import io.kronor.component.swish.swishViewModel
import io.kronor.component.vipps.VippsComponent
import io.kronor.component.vipps.vippsViewModel
import io.kronor.example.ui.theme.KronorSDKTheme
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Serializable
private sealed interface ExampleDestination : NavKey

@Serializable
private data object PaymentMethodsDestination : ExampleDestination

@Serializable
private data class PaymentDestination(
    val kind: PaymentKind,
    val fallbackMethod: String? = null,
    val instanceId: String = UUID.randomUUID().toString(),
) : ExampleDestination

@Serializable
private enum class PaymentKind {
    SWISH,
    CREDIT_CARD,
    MOBILE_PAY,
    VIPPS,
    PAYPAL,
    BANK_TRANSFER,
    GOOGLE_PAY,
    FALLBACK,
}

private fun PaymentMethod.toDestination(): PaymentDestination = when (this) {
    is PaymentMethod.Swish -> PaymentDestination(PaymentKind.SWISH)
    PaymentMethod.CreditCard -> PaymentDestination(PaymentKind.CREDIT_CARD)
    PaymentMethod.MobilePay -> PaymentDestination(PaymentKind.MOBILE_PAY)
    PaymentMethod.Vipps -> PaymentDestination(PaymentKind.VIPPS)
    PaymentMethod.PayPal -> PaymentDestination(PaymentKind.PAYPAL)
    PaymentMethod.BankTransfer -> PaymentDestination(PaymentKind.BANK_TRANSFER)
    PaymentMethod.GooglePay -> PaymentDestination(PaymentKind.GOOGLE_PAY)
    is PaymentMethod.Fallback -> PaymentDestination(
        kind = PaymentKind.FALLBACK,
        fallbackMethod = paymentMethod,
    )
}

@SuppressLint("ComposeViewModelForwarding", "ComposeModifierReused")
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun KronorTestApp(
    viewModel: MainViewModel,
    redirectIntent: Intent?,
    onRedirectHandled: (Intent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val backStack = rememberNavBackStack(PaymentMethodsDestination)
    val pendingPaymentMethod = viewModel.paymentMethodSelected.value
    val sessionToken = viewModel.paymentSessionToken

    fun finishPayment() {
        viewModel.resetPaymentState()
        while (backStack.size > 1) {
            backStack.removeLastOrNull()
        }
    }

    LaunchedEffect(pendingPaymentMethod, sessionToken) {
        if (pendingPaymentMethod != null && sessionToken != null) {
            viewModel.clearPendingPaymentMethod()
            val destination = pendingPaymentMethod.toDestination()
            if (backStack.lastOrNull() is PaymentDestination) {
                backStack[backStack.lastIndex] = destination
            } else {
                backStack.add(destination)
            }
        }
    }

    KronorSDKTheme {
        NavDisplay(
            backStack = backStack,
            onBack = {
                if (backStack.size > 1) {
                    backStack.removeLastOrNull()
                    viewModel.resetPaymentState()
                }
            },
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            entryProvider = entryProvider {
                entry<PaymentMethodsDestination> {
                    PaymentMethodsScreen(
                        viewModel = viewModel,
                        onStartPayment = { paymentMethod ->
                            backStack.add(paymentMethod.toDestination())
                        },
                    )
                }
                entry<PaymentDestination> { destination ->
                    val activeSessionToken = viewModel.paymentSessionToken
                    if (activeSessionToken == null) {
                        LaunchedEffect(destination) {
                            finishPayment()
                        }
                    } else {
                        PaymentScreen(
                            destination = destination,
                            sessionToken = activeSessionToken,
                            redirectIntent = redirectIntent,
                            onRedirectHandled = onRedirectHandled,
                            onFinished = ::finishPayment,
                            modifier = modifier,
                        )
                    }
                }
            },
        )
    }
}

@Composable
private fun PaymentScreen(
    destination: PaymentDestination,
    sessionToken: String,
    redirectIntent: Intent?,
    onRedirectHandled: (Intent) -> Unit,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val configuration = remember(sessionToken) {
        PaymentConfiguration(
            sessionToken = sessionToken,
            merchantLogo = R.drawable.kronor_logo,
            environment = Environment.Staging,
            appName = "kronor-android-test",
            appVersion = "0.1.0",
            locale = Locale.Builder().setRegion("US").setLanguage("en").build(),
            redirectUrl = "kronorcheckout://io.kronor.example/".toUri(),
        )
    }

    when (destination.kind) {
        PaymentKind.SWISH -> {
            val paymentViewModel = swishViewModel(configuration)
            PaymentEffects(
                events = paymentViewModel.events,
                redirectIntent = redirectIntent,
                onRedirect = paymentViewModel::handleIntent,
                onRedirectHandled = onRedirectHandled,
                onFinished = onFinished,
            )
            SwishComponent(paymentViewModel)
        }

        PaymentKind.CREDIT_CARD -> {
            val paymentViewModel = creditCardViewModel(configuration)
            PaymentEffects(
                events = paymentViewModel.events,
                redirectIntent = redirectIntent,
                onRedirect = paymentViewModel::handleIntent,
                onRedirectHandled = onRedirectHandled,
                onFinished = onFinished,
            )
            CreditCardComponent(paymentViewModel, modifier = modifier.statusBarsPadding())
        }

        PaymentKind.MOBILE_PAY -> {
            val paymentViewModel = mobilePayViewModel(configuration)
            PaymentEffects(
                events = paymentViewModel.events,
                redirectIntent = redirectIntent,
                onRedirect = paymentViewModel::handleIntent,
                onRedirectHandled = onRedirectHandled,
                onFinished = onFinished,
            )
            MobilePayComponent(paymentViewModel)
        }

        PaymentKind.VIPPS -> {
            val paymentViewModel = vippsViewModel(configuration)
            PaymentEffects(
                events = paymentViewModel.events,
                redirectIntent = redirectIntent,
                onRedirect = paymentViewModel::handleIntent,
                onRedirectHandled = onRedirectHandled,
                onFinished = onFinished,
            )
            VippsComponent(paymentViewModel)
        }

        PaymentKind.PAYPAL -> {
            val paymentViewModel = paypalViewModel(configuration)
            PaymentEffects(
                events = paymentViewModel.events,
                redirectIntent = redirectIntent,
                onRedirect = paymentViewModel::handleIntent,
                onRedirectHandled = onRedirectHandled,
                onFinished = onFinished,
            )
            PayPalComponent(paymentViewModel)
        }

        PaymentKind.BANK_TRANSFER -> {
            val paymentViewModel = bankTransferViewModel(configuration)
            PaymentEffects(
                events = paymentViewModel.events,
                redirectIntent = redirectIntent,
                onRedirect = paymentViewModel::handleIntent,
                onRedirectHandled = onRedirectHandled,
                onFinished = onFinished,
            )
            BankTransferComponent(paymentViewModel)
        }

        PaymentKind.GOOGLE_PAY -> {
            val paymentViewModel = googlePayViewModel(configuration)
            PaymentEffects(
                events = paymentViewModel.events,
                redirectIntent = redirectIntent,
                onRedirect = paymentViewModel::handleIntent,
                onRedirectHandled = onRedirectHandled,
                onFinished = onFinished,
            )
            GooglePayComponent(paymentViewModel, modifier = modifier.statusBarsPadding())
        }

        PaymentKind.FALLBACK -> {
            val fallbackMethod = requireNotNull(destination.fallbackMethod)
            val paymentViewModel = fallbackViewModel(configuration, fallbackMethod)
            PaymentEffects(
                events = paymentViewModel.events,
                redirectIntent = redirectIntent,
                onRedirect = paymentViewModel::handleIntent,
                onRedirectHandled = onRedirectHandled,
                onFinished = onFinished,
            )
            FallbackComponent(paymentViewModel)
        }
    }
}

@Composable
private fun PaymentEffects(
    events: Flow<PaymentEvent>,
    redirectIntent: Intent?,
    onRedirect: suspend (Intent) -> Unit,
    onRedirectHandled: (Intent) -> Unit,
    onFinished: () -> Unit,
) {
    LaunchedEffect(events) {
        events.collect {
            onFinished()
        }
    }
    LaunchedEffect(redirectIntent) {
        redirectIntent?.let { intent ->
            onRedirect(intent)
            onRedirectHandled(intent)
        }
    }
}

