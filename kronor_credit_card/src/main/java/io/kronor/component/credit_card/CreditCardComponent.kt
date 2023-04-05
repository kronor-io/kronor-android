package io.kronor.component.credit_card

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fingerprintjs.android.fingerprint.Fingerprinter
import com.fingerprintjs.android.fingerprint.FingerprinterFactory
import io.kronor.api.KronorError

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
    val context = LocalContext.current
    var backPressedCount by remember { mutableStateOf(0) }
    val backPressedDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    DisposableEffect(key1 = backPressedDispatcher) {
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (backPressedCount == 0) {
                    backPressedCount++
                    Toast.makeText(context, "Press back again to exit", Toast.LENGTH_SHORT).show()
                } else {
                    isEnabled = false
                    backPressedDispatcher?.onBackPressed()
                }
            }
        }
        backPressedDispatcher?.addCallback(callback)

        onDispose {
            callback.remove()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.transition(CreditCardStatechart.Companion.Event.SubscribeToPaymentStatus)
    }

    Surface(
        modifier = Modifier.fillMaxSize(), color = MaterialTheme.colors.background
    ) {
        when (state) {
            CreditCardStatechart.Companion.State.WaitingForSubscription -> {
                CreditCardWrapper { CreditCardInitializing() }
            }
            CreditCardStatechart.Companion.State.Initializing -> {
                CreditCardWrapper { CreditCardInitializing() }
            }
            CreditCardStatechart.Companion.State.CreatingPaymentRequest -> {
                CreditCardWrapper { CreditCardInitializing() }
            }
            CreditCardStatechart.Companion.State.WaitingForPaymentRequest -> {
                CreditCardWrapper { CreditCardInitializing() }
            }
            is CreditCardStatechart.Companion.State.Errored -> {
                CreditCardWrapper {
                    CreditCardErrored(error = state.error,
                        onPaymentRetry = { viewModel.transition(CreditCardStatechart.Companion.Event.Retry) },
                        onGoBack = { viewModel.transition(CreditCardStatechart.Companion.Event.CancelFlow) })
                }
            }
            is CreditCardStatechart.Companion.State.PaymentRequestInitialized -> {
                PaymentGatewayView(gatewayUrl = viewModel.paymentGatewayUrl, onPaymentCancel = {
                    viewModel.transition(CreditCardStatechart.Companion.Event.WaitForCancel)
                })
            }
            is CreditCardStatechart.Companion.State.WaitingForPayment -> {
                CreditCardWrapper {
                    CreditCardWaitingForPayment()
                }
            }
            is CreditCardStatechart.Companion.State.PaymentRejected -> {
                CreditCardWrapper {
                    CreditCardPaymentRejected(onPaymentRetry = {
                        viewModel.transition(
                            CreditCardStatechart.Companion.Event.Retry
                        )
                    }, onGoBack = {
                        viewModel.transition(CreditCardStatechart.Companion.Event.CancelFlow)
                    })
                }
            }
            is CreditCardStatechart.Companion.State.PaymentCompleted -> {
                CreditCardWrapper {
                    CreditCardPaymentCompleted()
                }
            }
        }
    }
}

@Composable
fun CreditCardWrapper(content: @Composable () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(30.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        content.invoke()
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PaymentGatewayView(gatewayUrl: Uri, onPaymentCancel: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        AndroidView(factory = {
            object : WebView(it) {
                override fun canGoBack(): Boolean {
                    return false;
                }

                override fun canGoForward(): Boolean {
                    return false;
                }
            }.apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?, request: WebResourceRequest
                    ): Boolean {
                        if (request.url.queryParameterNames.contains("cancel")) {
                            onPaymentCancel()
                            return false;
                        }
                        if (request.url.scheme == "http" || request.url.scheme == "https") {
                            return false;
                        }
                        return true;
                    }
                }
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                loadUrl(gatewayUrl.toString())
            }
        }, update = {
            it.loadUrl(gatewayUrl.toString())
        })
    }
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
                    stringResource(R.string.network_error), textAlign = TextAlign.Center
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
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(stringResource(R.string.secure_connection))
        Spacer(modifier = Modifier.height(30.dp))
        CircularProgressIndicator()
    }
}

@Composable
fun CreditCardWaitingForPayment() {
    Column(
        modifier = Modifier.fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(stringResource(R.string.waiting_for_payment))
        Spacer(modifier = Modifier.height(30.dp))
        CircularProgressIndicator()
    }
}

@Composable
fun CreditCardPaymentCompleted() {
    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxHeight()
    ) {
        Text(stringResource(R.string.payment_completed))
    }
}

@Composable
fun CreditCardPaymentRejected(onPaymentRetry: () -> Unit, onGoBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxHeight(),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(100.dp))
        Text(stringResource(R.string.payment_rejected))
        Button(onClick = {
            onPaymentRetry()
        }) {
            Text(stringResource(id = R.string.try_again))
        }

        Button(onClick = {
            onGoBack()
        }) {
            Text(stringResource(id = R.string.go_back))
        }
    }
}


@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
}