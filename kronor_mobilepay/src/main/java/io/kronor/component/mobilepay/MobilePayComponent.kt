package io.kronor.component.mobilepay

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
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
import androidx.core.content.ContextCompat.startActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fingerprintjs.android.fingerprint.Fingerprinter
import com.fingerprintjs.android.fingerprint.FingerprinterFactory
import io.kronor.api.KronorError
import io.kronor.component.webview_payment_gateway.WebviewGatewayStatechart
import io.kronor.component.webview_payment_gateway.WebviewGatewayViewModel
import io.kronor.component.webview_payment_gateway.WebviewGatewayViewModelFactory
import kotlinx.coroutines.flow.filterNotNull

@Composable
fun mobilePayViewModel(mobilePayConfiguration: MobilePayConfiguration): WebviewGatewayViewModel {
    return viewModel(factory = WebviewGatewayViewModelFactory(mobilePayConfiguration.toWebviewGatewayConfiguration()))
}

@Composable
fun GetMobilePayComponent(
    context: Context,
    mobilePayConfiguration: MobilePayConfiguration,
    viewModel: WebviewGatewayViewModel = mobilePayViewModel(mobilePayConfiguration = mobilePayConfiguration),
    newIntent: Intent?
) {

    if (!LocalInspectionMode.current) {
        LaunchedEffect(Unit) {
            val fingerprinterFactory = FingerprinterFactory.create(context)
            fingerprinterFactory.getFingerprint(version = Fingerprinter.Version.V_5, listener = {
                viewModel.deviceFingerprint = it
            })
        }

        val currentIntent = rememberUpdatedState(newValue = newIntent)

        LaunchedEffect(Unit) {
            snapshotFlow { currentIntent.value }.filterNotNull().collect {
                    viewModel.handleIntent(it)
                }
        }
    }

    MobilePayScreen(viewModel = viewModel)
}

@Composable
fun MobilePayScreen(viewModel: WebviewGatewayViewModel) {
    val state = viewModel.webviewGatewayState
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
        viewModel.transition(WebviewGatewayStatechart.Companion.Event.SubscribeToPaymentStatus)
    }

    Surface(
        modifier = Modifier.fillMaxSize(), color = MaterialTheme.colors.background
    ) {
        when (state) {
            WebviewGatewayStatechart.Companion.State.WaitingForSubscription -> {
                MobilePayWrapper { MobilePayInitializing() }
            }
            WebviewGatewayStatechart.Companion.State.Initializing -> {
                MobilePayWrapper { MobilePayInitializing() }
            }
            WebviewGatewayStatechart.Companion.State.CreatingPaymentRequest -> {
                MobilePayWrapper { MobilePayInitializing() }
            }
            WebviewGatewayStatechart.Companion.State.WaitingForPaymentRequest -> {
                MobilePayWrapper { MobilePayInitializing() }
            }
            is WebviewGatewayStatechart.Companion.State.Errored -> {
                MobilePayWrapper {
                    MobilePayErrored(error = state.error,
                        onPaymentRetry = { viewModel.transition(WebviewGatewayStatechart.Companion.Event.Retry) },
                        onGoBack = { viewModel.transition(WebviewGatewayStatechart.Companion.Event.CancelFlow) })
                }
            }
            is WebviewGatewayStatechart.Companion.State.PaymentRequestInitialized -> {
                PaymentGatewayView(gatewayUrl = viewModel.paymentGatewayUrl, onPaymentCancel = {
                    viewModel.transition(WebviewGatewayStatechart.Companion.Event.WaitForCancel)
                })
            }
            is WebviewGatewayStatechart.Companion.State.WaitingForPayment -> {
                MobilePayWrapper {
                    MobilePayWaitingForPayment()
                }
            }
            is WebviewGatewayStatechart.Companion.State.PaymentRejected -> {
                MobilePayWrapper {
                    MobilePayPaymentRejected(onPaymentRetry = {
                        viewModel.transition(
                            WebviewGatewayStatechart.Companion.Event.Retry
                        )
                    }, onGoBack = {
                        viewModel.transition(WebviewGatewayStatechart.Companion.Event.CancelFlow)
                    })
                }
            }
            is WebviewGatewayStatechart.Companion.State.PaymentCompleted -> {
                MobilePayWrapper {
                    MobilePayPaymentCompleted()
                }
            }
        }
    }
}

@Composable
fun MobilePayWrapper(content: @Composable () -> Unit) {
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
        val context = LocalContext.current
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
                        Log.d("MobilePayComponent", "Request URL received: ${request.url}")
                        if (request.url.queryParameterNames.contains("cancel")) {
                            onPaymentCancel()
                            return false;
                        }
                        if (request.url.scheme == "http" || request.url.scheme == "https") {
                            return false;
                        }
                        if (request.url.scheme == "mobilepayonline-test" || request.url.scheme == "mobilepayonline") {
                            startActivity(
                                context, Intent(
                                    Intent.ACTION_VIEW, request.url
                                ).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK), null
                            )
                            return true;
                        }
                        if (request.url.scheme == "kronorcheckout" && request.url.path == "/mobilepay") {
                            Log.d("KronorIntent", "${request.url}") // after successful payment we are here
                            // the statechart has probably already transitioned in the background
                            return true;
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
fun MobilePayErrored(error: KronorError, onPaymentRetry: () -> Unit, onGoBack: () -> Unit) {
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
fun MobilePayInitializing() {
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
fun MobilePayWaitingForPayment() {
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
fun MobilePayPaymentCompleted() {
    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxHeight()
    ) {
        Text(stringResource(R.string.payment_completed))
    }
}

@Composable
fun MobilePayPaymentRejected(onPaymentRetry: () -> Unit, onGoBack: () -> Unit) {
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