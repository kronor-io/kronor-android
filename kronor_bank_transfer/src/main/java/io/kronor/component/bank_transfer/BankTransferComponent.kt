package io.kronor.component.bank_transfer

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material.Button
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.trustlyAndroidLibrary.TrustlyCheckoutAbortHandler
import com.trustlyAndroidLibrary.TrustlyCheckoutErrorHandler
import com.trustlyAndroidLibrary.TrustlyCheckoutSuccessHandler
import com.trustlyAndroidLibrary.TrustlyWebView
import io.kronor.api.KronorError
import io.kronor.api.PaymentConfiguration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


@Composable
fun bankTransferViewModel(bankTransferConfiguration: PaymentConfiguration): BankTransferViewModel {
    return viewModel(factory = BankTransferViewModelFactory(bankTransferConfiguration))
}

@Composable
fun BankTransferComponent(
    viewModel: BankTransferViewModel,
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val context = LocalContext.current

    LaunchedEffect(viewModel.subscribe) {
        if (viewModel.subscribe) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                withContext(Dispatchers.IO) {
                    viewModel.subscription(context)
                }
            }
        }
    }

    BankTransferScreen(
        transition = viewModel::transition,
        state = viewModel.bankTransferState
    )
}


@Composable
private fun BankTransferScreen(
    transition: (BankTransferStatechart.Companion.Event) -> Unit,
    state: State<BankTransferStatechart.Companion.State>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Surface(
        modifier = modifier, color = MaterialTheme.colors.background
    ) {
        when (state.value) {
            BankTransferStatechart.Companion.State.Initializing -> {
                LaunchedEffect(Unit) {
                    transition(BankTransferStatechart.Companion.Event.Initialize(context))
                }
                BankTransferWrapper { BankTransferInitializing(modifier = Modifier.fillMaxSize()) }
            }

            BankTransferStatechart.Companion.State.CreatingPaymentRequest -> {
                BankTransferWrapper { BankTransferInitializing(modifier = Modifier.fillMaxSize()) }
            }

            BankTransferStatechart.Companion.State.WaitingForPaymentRequest -> {
                BankTransferWrapper { BankTransferInitializing(modifier = Modifier.fillMaxSize()) }
            }

            is BankTransferStatechart.Companion.State.Errored -> {
                BankTransferWrapper {
                    BankTransferErrored(error = (state.value as BankTransferStatechart.Companion.State.Errored).error,
                        onPaymentRetry = { transition(BankTransferStatechart.Companion.Event.Retry) },
                        onGoBack = { transition(BankTransferStatechart.Companion.Event.CancelFlow) },
                        modifier = Modifier.fillMaxSize())
                }
            }

            is BankTransferStatechart.Companion.State.PaymentRequestInitialized -> {
                BankTransferWebView(
                    trustlyCheckoutUrl = (state.value as BankTransferStatechart.Companion.State.PaymentRequestInitialized).trustlyCheckoutUrl,
                    onPaymentSuccess = {
                        transition(BankTransferStatechart.Companion.Event.BankTransferWebViewOpened )
                    },
                    onPaymentError = {
                        transition(BankTransferStatechart.Companion.Event.PaymentRejected)
                    },
                    onPaymentAbort = {
                        transition(BankTransferStatechart.Companion.Event.PaymentRejected)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            is BankTransferStatechart.Companion.State.WaitingForPayment -> {
                BankTransferWrapper {
                    BankTransferWaitingForPayment(modifier = Modifier.fillMaxSize())
                }
            }

            is BankTransferStatechart.Companion.State.PaymentRejected -> {
                BankTransferWrapper {
                    BankTransferPaymentRejected(modifier = Modifier.fillMaxSize())
                }
            }

            is BankTransferStatechart.Companion.State.PaymentCompleted -> {
                BankTransferWrapper {
                    BankTransferPaymentCompleted(modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}

@Composable
private fun BankTransferWrapper(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
        modifier = modifier
    ) {
        content.invoke()
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun BankTransferWebView(
    trustlyCheckoutUrl: String,
    onPaymentSuccess: () -> Unit,
    onPaymentError: () -> Unit,
    onPaymentAbort: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier.fillMaxSize()
    ) {
        val currentActivity = LocalActivity.current
        AndroidView(factory = {
            WebView(it).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                settings.javaScriptEnabled = true
                settings.setSupportMultipleWindows(true)
                settings.javaScriptCanOpenWindowsAutomatically = true
                settings.domStorageEnabled = true
            }
        }, update = {
            val trustlyView = TrustlyWebView(currentActivity, trustlyCheckoutUrl)

            trustlyView.successHandler = TrustlyCheckoutSuccessHandler {
                onPaymentSuccess()
            }
            trustlyView.errorHandler = TrustlyCheckoutErrorHandler {
                onPaymentError()
            }
            trustlyView.abortHandler = TrustlyCheckoutAbortHandler {
                onPaymentAbort()
            }
            it.addView(trustlyView)
        })
    }
}

@Composable
private fun BankTransferErrored(
    error: KronorError,
    onPaymentRetry: () -> Unit,
    onGoBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(100.dp))
        when (error) {
            is KronorError.NetworkError -> {
                Text(
                    stringResource(R.string.network_error), textAlign = TextAlign.Center
                )
            }

            is KronorError.GraphQlError -> {
                Text(
                    stringResource(R.string.graphql_error), textAlign = TextAlign.Center
                )
            }
            is KronorError.FlowError -> {
                Text(
                    error.e
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
private fun BankTransferInitializing(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(stringResource(R.string.secure_connection))
        Spacer(modifier = Modifier.height(30.dp))
        CircularProgressIndicator()
    }
}

@Composable
private fun BankTransferWaitingForPayment(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(stringResource(R.string.waiting_for_payment))
        Spacer(Modifier.height(30.dp))
        CircularProgressIndicator()
    }
}

@Composable
private fun BankTransferPaymentCompleted(modifier: Modifier = Modifier) {
    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Text(stringResource(R.string.payment_completed))
    }
}

@Composable
private fun BankTransferPaymentRejected(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(100.dp))
        Text(stringResource(R.string.payment_rejected))
    }
}