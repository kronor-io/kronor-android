package io.kronor.component.webview_payment_gateway

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat.startActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Lifecycle.Event.*
import androidx.lifecycle.repeatOnLifecycle
import io.kronor.api.KronorError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@SuppressLint("ComposeViewModelForwarding")
@Composable
fun WebviewGatewayComponent(
    viewModel: WebviewGatewayViewModel, modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    if (!LocalInspectionMode.current) {

        val lifecycle = LocalLifecycleOwner.current.lifecycle

        LaunchedEffect(viewModel.subscribe) {
            if (viewModel.subscribe) {
                lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    withContext(Dispatchers.IO) {
                        viewModel.subscription(context)
                    }
                }
            }
        }
    }

    WebviewGatewayScreen(
        viewModel::transition,
        viewModel.webviewGatewayState,
        viewModel.paymentGatewayUrl,
        modifier = modifier
    )
}

@Composable
private fun WebviewGatewayScreen(
    transition: (WebviewGatewayStatechart.Companion.Event) -> Unit,
    state: State<WebviewGatewayStatechart.Companion.State>,
    paymentGatewayUrl: Uri,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Surface(
        modifier = modifier, color = MaterialTheme.colors.background
    ) {
        when (state.value) {
            WebviewGatewayStatechart.Companion.State.Initializing -> {
                LaunchedEffect(Unit) {
                    transition(WebviewGatewayStatechart.Companion.Event.Initialize(context))
                }
                WebviewGatewayWrapper { WebviewGatewayInitializing(modifier = Modifier.fillMaxSize()) }
            }

            WebviewGatewayStatechart.Companion.State.CreatingPaymentRequest -> {
                WebviewGatewayWrapper { WebviewGatewayInitializing(modifier = Modifier.fillMaxSize()) }
            }

            WebviewGatewayStatechart.Companion.State.WaitingForPaymentRequest -> {
                WebviewGatewayWrapper { WebviewGatewayInitializing(modifier = Modifier.fillMaxSize()) }
            }

            is WebviewGatewayStatechart.Companion.State.Errored -> {
                WebviewGatewayWrapper {
                    WebviewGatewayErrored(error = (state.value as WebviewGatewayStatechart.Companion.State.Errored).error,
                        onPaymentRetry = { transition(WebviewGatewayStatechart.Companion.Event.Retry) },
                        onGoBack = { transition(WebviewGatewayStatechart.Companion.Event.CancelFlow) },
                        modifier = Modifier.fillMaxSize())
                }
            }

            is WebviewGatewayStatechart.Companion.State.PaymentRequestInitialized -> {
                PaymentGatewayView(
                    gatewayUrl = paymentGatewayUrl.toString(),
                    onPaymentCancel = {
                        transition(WebviewGatewayStatechart.Companion.Event.WaitForCancel)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            is WebviewGatewayStatechart.Companion.State.WaitingForPayment -> {
                WebviewGatewayWrapper {
                    WebviewGatewayWaitingForPayment(modifier = Modifier.fillMaxSize())
                }
            }

            is WebviewGatewayStatechart.Companion.State.PaymentRejected -> {
                WebviewGatewayWrapper {
                    WebviewGatewayPaymentRejected(modifier = Modifier.fillMaxSize())
                }
            }

            is WebviewGatewayStatechart.Companion.State.PaymentCompleted -> {
                WebviewGatewayWrapper {
                    WebviewGatewayPaymentCompleted(modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}

@Composable
private fun WebviewGatewayWrapper(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
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
private fun PaymentGatewayView(
    gatewayUrl: String,
    onPaymentCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier.fillMaxSize()
    ) {
        Log.d("WebviewGatewayComponent", "Gateway URL received: ${gatewayUrl}")
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
                        Log.d("WebviewGatewayComponent", "Request URL received: ${request.url}")
                        if (request.url.queryParameterNames.contains("cancel")) {
                            onPaymentCancel()
                            return false
                        }
                        if (request.url.scheme == "http" || request.url.scheme == "https") {
                            return false
                        }
                        return try {
                            startActivity(
                                context, Intent(
                                    Intent.ACTION_VIEW, request.url
                                ), null
                            )
                            true
                        } catch (e: ActivityNotFoundException) {
                            true
                        }
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
private fun WebviewGatewayErrored(
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
private fun WebviewGatewayInitializing(modifier: Modifier = Modifier) {
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
private fun WebviewGatewayWaitingForPayment(modifier: Modifier = Modifier) {
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
private fun WebviewGatewayPaymentCompleted(modifier: Modifier = Modifier) {
    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Text(stringResource(R.string.payment_completed))
    }
}

@Composable
private fun WebviewGatewayPaymentRejected(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(100.dp))
        Text(stringResource(R.string.payment_rejected))
    }
}

