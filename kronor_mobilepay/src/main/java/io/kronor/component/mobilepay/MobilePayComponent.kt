package io.kronor.component.mobilepay

import android.annotation.SuppressLint
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat.startActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Lifecycle.Event.*
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fingerprintjs.android.fingerprint.Fingerprinter
import com.fingerprintjs.android.fingerprint.FingerprinterFactory
import io.kronor.api.KronorError
import io.kronor.component.webview_payment_gateway.WebviewGatewayStatechart
import io.kronor.component.webview_payment_gateway.WebviewGatewayViewModel
import io.kronor.component.webview_payment_gateway.WebviewGatewayViewModelFactory
import kotlinx.coroutines.launch

@Composable
fun mobilePayViewModel(mobilePayConfiguration: MobilePayConfiguration): WebviewGatewayViewModel {
    return viewModel(factory = WebviewGatewayViewModelFactory(mobilePayConfiguration.toWebviewGatewayConfiguration()))
}

@Composable
fun MobilePayComponent(
    viewModel: WebviewGatewayViewModel
) {
    val context = LocalContext.current

    if (!LocalInspectionMode.current) {
        LaunchedEffect(Unit) {
            val fingerprinterFactory = FingerprinterFactory.create(context)
            fingerprinterFactory.getFingerprint(
                version = Fingerprinter.Version.V_5,
                listener = viewModel::setDeviceFingerPrint
            )
        }

        val lifecycle = LocalLifecycleOwner.current.lifecycle

        LaunchedEffect(Unit) {
            viewModel.transition(WebviewGatewayStatechart.Companion.Event.SubscribeToPaymentStatus)
            launch {
                Log.d("GetMobilePayComponent", "lifecycle scope launched")
                lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    launch {
                        viewModel.onSubscription()
                    }
                }
            }
        }
    }

    MobilePayScreen(
        viewModel::transition,
        viewModel.webviewGatewayState,
        viewModel.paymentGatewayUrl
    )
}

@Composable
fun MobilePayScreen(
    transition: (WebviewGatewayStatechart.Companion.Event) -> Unit,
    state: State<WebviewGatewayStatechart.Companion.State>,
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
        modifier = Modifier, color = MaterialTheme.colors.background
    ) {
        when (state.value) {
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
                    MobilePayErrored(error = (state as WebviewGatewayStatechart.Companion.State.Errored).error,
                        onPaymentRetry = { transition(WebviewGatewayStatechart.Companion.Event.Retry) },
                        onGoBack = { transition(WebviewGatewayStatechart.Companion.Event.CancelFlow) })
                }
            }

            is WebviewGatewayStatechart.Companion.State.PaymentRequestInitialized -> {
                Log.d("MobilePayWrapper", "PaymentGatewayView Starting")
                PaymentGatewayView(gatewayUrl = paymentGatewayUrl.toString(), onPaymentCancel = {
                    transition(WebviewGatewayStatechart.Companion.Event.WaitForCancel)
                })
            }

            is WebviewGatewayStatechart.Companion.State.PaymentRequestInitialized -> {
                PaymentGatewayView(gatewayUrl = paymentGatewayUrl.toString(), onPaymentCancel = {
                    transition(WebviewGatewayStatechart.Companion.Event.WaitForCancel)
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
                        transition(
                            WebviewGatewayStatechart.Companion.Event.Retry
                        )
                    }, onGoBack = {
                        transition(WebviewGatewayStatechart.Companion.Event.CancelFlow)
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
fun MobilePayWrapper(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
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
fun PaymentGatewayView(
    gatewayUrl: String, onPaymentCancel: () -> Unit, modifier: Modifier = Modifier
) {
    Column(
        modifier.fillMaxSize()
    ) {

        val context = LocalContext.current
        AndroidView(factory = {
            WebView(it).apply {
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
                            return false
                        }
                        if (request.url.scheme == "http" || request.url.scheme == "https") {
                            return false
                        }
                        startActivity(
                            context, Intent(
                                Intent.ACTION_VIEW, request.url
                            ).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK), null
                        )
                        return true
                    }
                }
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
            }
        }, update = {
            it.loadUrl(gatewayUrl)
        })
    }
}

@Composable
fun MobilePayErrored(
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
fun MobilePayInitializing(modifier: Modifier = Modifier) {
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
fun MobilePayWaitingForPayment(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(stringResource(R.string.waiting_for_payment))
        Spacer(Modifier.height(30.dp))
        CircularProgressIndicator()
    }
}

@Composable
fun MobilePayPaymentCompleted(modifier: Modifier = Modifier) {
    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxHeight()
    ) {
        Text(stringResource(R.string.payment_completed))
    }
}

@Composable
fun MobilePayPaymentRejected(
    onPaymentRetry: () -> Unit, onGoBack: () -> Unit, modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxHeight(),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(100.dp))
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