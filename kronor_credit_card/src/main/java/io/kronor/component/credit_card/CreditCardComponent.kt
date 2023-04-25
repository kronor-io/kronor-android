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
import io.kronor.component.webview_payment_gateway.WebviewGatewayStatechart
import io.kronor.component.webview_payment_gateway.WebviewGatewayViewModel
import io.kronor.component.webview_payment_gateway.WebviewGatewayViewModelFactory

@Composable
fun creditCardViewModel(creditCardConfiguration: CreditCardConfiguration): WebviewGatewayViewModel {
    return viewModel(factory = WebviewGatewayViewModelFactory(creditCardConfiguration.toWebviewGatewayConfiguration()))
}

@Composable
fun GetCreditCardComponent(
    context: Context,
    creditCardConfiguration: CreditCardConfiguration,
    viewModel: WebviewGatewayViewModel = creditCardViewModel(creditCardConfiguration = creditCardConfiguration)
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
        { event -> viewModel.transition(event) },
        viewModel.webviewGatewayState,
        viewModel.paymentGatewayUrl
    )
}

@Composable
fun CreditCardScreen(
    transition: (WebviewGatewayStatechart.Companion.Event) -> Unit,
    state: WebviewGatewayStatechart.Companion.State,
    paymentGatewayUrl: Uri,
    modifier: Modifier = Modifier
) {
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
        transition(WebviewGatewayStatechart.Companion.Event.SubscribeToPaymentStatus)
    }

    Surface(
        modifier = modifier.fillMaxSize(), color = MaterialTheme.colors.background
    ) {
        when (state) {
            WebviewGatewayStatechart.Companion.State.WaitingForSubscription -> {
                CreditCardWrapper(content = { CreditCardInitializing() })
            }

            WebviewGatewayStatechart.Companion.State.Initializing -> {
                CreditCardWrapper(content = { CreditCardInitializing() })
            }

            WebviewGatewayStatechart.Companion.State.CreatingPaymentRequest -> {
                CreditCardWrapper(content = { CreditCardInitializing() })
            }

            WebviewGatewayStatechart.Companion.State.WaitingForPaymentRequest -> {
                CreditCardWrapper(content = { CreditCardInitializing() })
            }

            is WebviewGatewayStatechart.Companion.State.Errored -> {
                CreditCardWrapper(content = {
                    CreditCardErrored(error = state.error,
                        onPaymentRetry = { transition(WebviewGatewayStatechart.Companion.Event.Retry) },
                        onGoBack = { transition(WebviewGatewayStatechart.Companion.Event.CancelFlow) })
                })
            }

            is WebviewGatewayStatechart.Companion.State.PaymentRequestInitialized -> {
                PaymentGatewayView(gatewayUrl = paymentGatewayUrl, onPaymentCancel = {
                    transition(WebviewGatewayStatechart.Companion.Event.WaitForCancel)
                })
            }

            is WebviewGatewayStatechart.Companion.State.WaitingForPayment -> {
                CreditCardWrapper(content = { CreditCardWaitingForPayment() })
            }

            is WebviewGatewayStatechart.Companion.State.PaymentRejected -> {
                CreditCardWrapper(content = {
                    CreditCardPaymentRejected(onPaymentRetry = {
                        transition(
                            WebviewGatewayStatechart.Companion.Event.Retry
                        )
                    }, onGoBack = {
                        transition(WebviewGatewayStatechart.Companion.Event.CancelFlow)
                    })
                })
            }

            is WebviewGatewayStatechart.Companion.State.PaymentCompleted -> {
                CreditCardWrapper(content = {
                    CreditCardPaymentCompleted()
                })
            }
        }
    }
}

@Composable
fun CreditCardWrapper(content: @Composable () -> Unit, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.padding(30.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        content.invoke()
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PaymentGatewayView(
    gatewayUrl: Uri,
    onPaymentCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize()
    ) {
        AndroidView(factory = {
            object : WebView(it) {
                override fun canGoBack(): Boolean {
                    return false
                }

                override fun canGoForward(): Boolean {
                    return false
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
                            return false
                        }
                        if (request.url.scheme == "http" || request.url.scheme == "https") {
                            return false
                        }
                        return true
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
fun CreditCardErrored(
    error: KronorError,
    onPaymentRetry: () -> Unit,
    onGoBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxHeight(),
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
fun CreditCardInitializing(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(stringResource(R.string.secure_connection))
        Spacer(modifier = Modifier.height(30.dp))
        CircularProgressIndicator()
    }
}

@Composable
fun CreditCardWaitingForPayment(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(stringResource(R.string.waiting_for_payment))
        Spacer(modifier = Modifier.height(30.dp))
        CircularProgressIndicator()
    }
}

@Composable
fun CreditCardPaymentCompleted(modifier: Modifier = Modifier) {
    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxHeight()
    ) {
        Text(stringResource(R.string.payment_completed))
    }
}

@Composable
fun CreditCardPaymentRejected(
    onPaymentRetry: () -> Unit,
    onGoBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxHeight(),
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