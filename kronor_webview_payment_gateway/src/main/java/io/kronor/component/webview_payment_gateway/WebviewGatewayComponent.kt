package io.kronor.component.webview_payment_gateway

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.webkit.WebSettingsCompat;
import androidx.webkit.WebViewFeature;
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat.startActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Lifecycle.Event.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.kronor.api.KronorError
import io.kronor.api.PaymentConfiguration
import io.kronor.api.PaymentMethod
import io.kronor.api.PaymentEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun WebviewGatewayComponent(
    configuration: PaymentConfiguration,
    paymentMethod: PaymentMethod,
    onResult: (PaymentEvent) -> Unit,
    modifier: Modifier = Modifier,
    redirectIntent: Intent? = null,
    onRedirectHandled: (Intent) -> Unit = {},
) {
    WebviewGatewayRoute(
        configuration = configuration,
        paymentMethod = paymentMethod,
        onResult = onResult,
        modifier = modifier,
        redirectIntent = redirectIntent,
        onRedirectHandled = onRedirectHandled,
    )
}

@Composable
private fun WebviewGatewayRoute(
    configuration: PaymentConfiguration,
    paymentMethod: PaymentMethod,
    onResult: (PaymentEvent) -> Unit,
    redirectIntent: Intent?,
    onRedirectHandled: (Intent) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WebviewGatewayViewModel = viewModel(
        factory = WebviewGatewayViewModelFactory(configuration, paymentMethod),
    ),
) {
    val result by viewModel.result.collectAsState()
    val currentOnResult by rememberUpdatedState(onResult)
    val currentOnRedirectHandled by rememberUpdatedState(onRedirectHandled)

    LaunchedEffect(result) {
        result?.let {
            viewModel.consumeResult(it)
            currentOnResult(it)
        }
    }
    LaunchedEffect(redirectIntent) {
        redirectIntent?.let { intent ->
            viewModel.handleIntent(intent)
            currentOnRedirectHandled(intent)
        }
    }

    WebviewGatewayContent(
        subscribe = viewModel.subscribe,
        subscription = viewModel::subscription,
        transition = viewModel::transition,
        state = viewModel.webviewGatewayState,
        paymentGatewayUrl = viewModel.paymentGatewayUrl,
        isDelayed = viewModel.isDelayed,
        modifier = modifier,
    )
}

@Deprecated(
    message = "Pass PaymentConfiguration and onResult so the component can own its ViewModel",
)
@SuppressLint("ComposeViewModelForwarding")
@Composable
fun WebviewGatewayComponent(
    viewModel: WebviewGatewayViewModel, modifier: Modifier = Modifier
) {
    WebviewGatewayContent(
        subscribe = viewModel.subscribe,
        subscription = viewModel::subscription,
        transition = viewModel::transition,
        state = viewModel.webviewGatewayState,
        paymentGatewayUrl = viewModel.paymentGatewayUrl,
        isDelayed = viewModel.isDelayed,
        modifier = modifier,
    )
}

@Composable
private fun WebviewGatewayContent(
    subscribe: Boolean,
    subscription: suspend (Context) -> Unit,
    transition: (WebviewGatewayStatechart.Companion.Event) -> Unit,
    state: State<WebviewGatewayStatechart.Companion.State>,
    paymentGatewayUrl: Uri,
    isDelayed: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    if (!LocalInspectionMode.current) {

        val lifecycle = LocalLifecycleOwner.current.lifecycle

        LaunchedEffect(subscribe) {
            if (subscribe) {
                lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    withContext(Dispatchers.IO) {
                        subscription(context)
                    }
                }
            }
        }
    }

    WebviewGatewayScreen(
        transition,
        state,
        paymentGatewayUrl,
        isDelayed,
        modifier = modifier
    )
}

@Composable
private fun WebviewGatewayScreen(
    transition: (WebviewGatewayStatechart.Companion.Event) -> Unit,
    state: State<WebviewGatewayStatechart.Companion.State>,
    paymentGatewayUrl: Uri,
    isDelayed: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Surface(
        modifier = modifier, color = MaterialTheme.colors.background
    ) {
        Column {
            Box(modifier = Modifier.weight(1f)) {
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
            PaymentDelayedNotice(isDelayed)
        }
    }
}

@Composable
private fun PaymentDelayedNotice(isDelayed: Boolean) {
    AnimatedVisibility(visible = isDelayed, enter = fadeIn(), exit = fadeOut()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.payment_taking_longer),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.onBackground.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )
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

        val context = LocalContext.current
        AndroidView(factory = {
            WebView(it).apply {

                if (WebViewFeature.isFeatureSupported(
                        WebViewFeature.PAYMENT_REQUEST)) {
                    WebSettingsCompat.setPaymentRequestEnabled(settings, true);
                }

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
                            context.startActivity(Intent(
                                    Intent.ACTION_VIEW, request.url
                                ), null
                            )
                            true
                        } catch (e: ActivityNotFoundException) {
                            Log.e("WebviewGatewayComponent", "No activity found to handle URL: ${request.url}", e)
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
