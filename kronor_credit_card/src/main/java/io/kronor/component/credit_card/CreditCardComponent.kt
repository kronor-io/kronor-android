package io.kronor.component.credit_card

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fingerprintjs.android.fingerprint.Fingerprinter
import com.fingerprintjs.android.fingerprint.FingerprinterFactory
import io.kronor.api.KronorError
import io.kronor.api.PaymentStatusSubscription

@Composable
fun creditCardViewModel(creditCardConfiguration: CreditCardConfiguration): CreditCardViewModel {
    return viewModel(factory = CreditCardViewModelFactory(creditCardConfiguration))
}

@Composable
fun GetCreditCardComponent(
    context: Context,
    creditCardConfiguration: CreditCardConfiguration,
    viewModel: CreditCardViewModel = creditCardViewModel(creditCardConfiguration = creditCardConfiguration)
) {

    if (!LocalInspectionMode.current) {
        LaunchedEffect(Unit) {
            val fingerprinterFactory = FingerprinterFactory.create(context)
            fingerprinterFactory.getFingerprint(version = Fingerprinter.Version.V_5, listener = {
                viewModel.deviceFingerprint = it
            })
        }
    }

    CreditCardScreen(
        merchantLogo = creditCardConfiguration.merchantLogo, viewModel = viewModel
    )
}

@Composable
fun CreditCardScreen(merchantLogo: Int?, viewModel: CreditCardViewModel) {
    val state = viewModel.creditCardState
    val paymentRequest: PaymentStatusSubscription.PaymentRequest? = viewModel.paymentRequest

    LaunchedEffect(Unit) {
        viewModel.transition(CreditCardStatechart.Companion.Event.SubscribeToPaymentStatus)
    }

    Surface(
        modifier = Modifier.fillMaxSize(), color = MaterialTheme.colors.background
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(30.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            when (state) {
                CreditCardStatechart.Companion.State.WaitingForSubscription -> {
                    CreditCardInitializing()
                }
                CreditCardStatechart.Companion.State.Initializing -> {
                    CreditCardInitializing()
                }
                CreditCardStatechart.Companion.State.CreatingPaymentRequest -> {
                    CreditCardInitializing()
                }
                CreditCardStatechart.Companion.State.WaitingForPaymentRequest -> {
                    CreditCardInitializing()
                }
                is CreditCardStatechart.Companion.State.Errored -> {
                    CreditCardErrored(error = state.error, onPaymentRetry = {}, onGoBack = {})
                }
                is CreditCardStatechart.Companion.State.PaymentRequestInitialized -> {

                }
                is CreditCardStatechart.Companion.State.WaitingForPayment -> {
                    PaymentGatewayView()
                }
                else -> {
                    Text("In else clause")
                }
            }
        }
    }
}

@Composable
fun PaymentGatewayView() {
    TODO("Not yet implemented")
}

@Composable
fun CreditCardErrored(error: KronorError, onPaymentRetry: () -> Unit, onGoBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxHeight(),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(100.dp))
        when (error) {
            is KronorError.networkError -> {
                Text(
                    stringResource(R.string.network_error),
                    textAlign = TextAlign.Center
                )
            }
            is KronorError.graphQlError -> {
                Text(
                    stringResource(R.string.graphql_error), textAlign = TextAlign.Center
                )
            }
        }
        Button(onClick = {
            onPaymentRetry()
        }) {
            Text(stringResource(R.string.try_again))
        }

        Button(onClick = {
            onGoBack()
        }) {
            Text(stringResource(R.string.go_back))
        }
    }
}

@Composable
fun CreditCardInitializing() {
    Column(
        modifier = Modifier.fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(stringResource(R.string.secure_connection))
        Spacer(modifier = Modifier.height(30.dp))
        CircularProgressIndicator()
    }
}


@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
}