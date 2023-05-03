package io.kronor.component.webview_payment_gateway

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.media.MediaDrm
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat.startActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Lifecycle.Event.*
import androidx.lifecycle.repeatOnLifecycle
import io.kronor.api.KronorError
import io.kronor.api.PaymentMethod
import kotlinx.coroutines.launch
import java.lang.Exception
import java.security.MessageDigest
import java.util.UUID

@Composable
fun WebviewGatewayComponent(
    viewModel: WebviewGatewayViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    if (!LocalInspectionMode.current) {
        LaunchedEffect(Unit) {
            viewModel.setDeviceFingerPrint(getWeakFingerprint(context))
        }

        val lifecycle = LocalLifecycleOwner.current.lifecycle

        LaunchedEffect(Unit) {
            viewModel.transition(WebviewGatewayStatechart.Companion.Event.SubscribeToPaymentStatus)
            launch {
                lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    launch {
                        viewModel.subscription()
                    }
                }
            }
        }
    }

    WebviewGatewayScreen(
        viewModel::transition,
        viewModel.webviewGatewayState,
        viewModel.paymentGatewayUrl,
        viewModel.webviewGatewayConfiguration.paymentMethod,
        modifier = modifier
    )
}

@Composable
private fun WebviewGatewayScreen(
    transition: (WebviewGatewayStatechart.Companion.Event) -> Unit,
    state: State<WebviewGatewayStatechart.Companion.State>,
    paymentGatewayUrl: Uri,
    paymentMethod: PaymentMethod,
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
        modifier = modifier, color = MaterialTheme.colors.background
    ) {
        when (state.value) {
            WebviewGatewayStatechart.Companion.State.WaitingForSubscription -> {
                WebviewGatewayWrapper { WebviewGatewayInitializing(modifier = Modifier.fillMaxSize()) }
            }

            WebviewGatewayStatechart.Companion.State.Initializing -> {
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
                        onGoBack = { transition(WebviewGatewayStatechart.Companion.Event.CancelFlow) }
                    , modifier = Modifier.fillMaxSize())
                }
            }

            is WebviewGatewayStatechart.Companion.State.PaymentRequestInitialized -> {
                PaymentGatewayView(gatewayUrl = paymentGatewayUrl.toString(),
                    paymentMethod = paymentMethod,
                    onPaymentCancel = {
                        transition(WebviewGatewayStatechart.Companion.Event.WaitForCancel)
                    },
                modifier = Modifier.fillMaxSize())
            }

            is WebviewGatewayStatechart.Companion.State.WaitingForPayment -> {
                WebviewGatewayWrapper {
                    WebviewGatewayWaitingForPayment(modifier = Modifier.fillMaxSize())
                }
            }

            is WebviewGatewayStatechart.Companion.State.PaymentRejected -> {
                WebviewGatewayWrapper {
                    WebviewGatewayPaymentRejected(onPaymentRetry = {
                        transition(
                            WebviewGatewayStatechart.Companion.Event.Retry
                        )
                    }, onGoBack = {
                        transition(WebviewGatewayStatechart.Companion.Event.CancelFlow)
                    }, modifier = Modifier.fillMaxSize())
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
    paymentMethod: PaymentMethod,
    onPaymentCancel: () -> Unit,
    modifier: Modifier = Modifier
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
                        Log.d("WebviewGatewayComponent", "Request URL received: ${request.url}")
                        if (request.url.queryParameterNames.contains("cancel")) {
                            onPaymentCancel()
                            return false
                        }
                        if (request.url.scheme == "http" || request.url.scheme == "https") {
                            if (paymentMethod == PaymentMethod.CreditCard) {
                                return true
                            }
                            return false
                        }
                        return try {
                            startActivity(
                                context, Intent(
                                    Intent.ACTION_VIEW, request.url
                                ), null
                            )
                            true
                        } catch (e : ActivityNotFoundException) {
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
private fun WebviewGatewayPaymentRejected(
    onPaymentRetry: () -> Unit, onGoBack: () -> Unit, modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
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

@SuppressLint("HardwareIds")
private fun getWeakFingerprint(context: Context) : String {
    val contentResolver : ContentResolver = context.contentResolver!!

    val androidId: String? by lazy {
        try {
            Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        } catch (e: Exception) {
            null
        }
    }

    fun getGsfId(): String? {
        val URI = Uri.parse("content://com.google.android.gsf.gservices")
        val params = arrayOf("android_id")
        return try {
            val cursor: Cursor = contentResolver
                .query(URI, null, null, params, null) ?: return null

            if (!cursor.moveToFirst() || cursor.columnCount < 2) {
                cursor.close()
                return null
            }
            try {
                val result = java.lang.Long.toHexString(cursor.getString(1).toLong())
                cursor.close()
                result
            } catch (e: NumberFormatException) {
                cursor.close()
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    val gsfId : String? by lazy {
        try {
            getGsfId()
        } catch (e: Exception) {
            null
        }
    }


    fun releaseMediaDRM(mediaDrm: MediaDrm) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            mediaDrm.close()
        } else {
            mediaDrm.release()
        }
    }

    fun mediaDrmId(): String {
        val wIDEWINE_UUID_MOST_SIG_BITS = -0x121074568629b532L
        val wIDEWINE_UUID_LEAST_SIG_BITS = -0x5c37d8232ae2de13L
        val widevineUUID = UUID(wIDEWINE_UUID_MOST_SIG_BITS, wIDEWINE_UUID_LEAST_SIG_BITS)
        val wvDrm: MediaDrm?

        wvDrm = MediaDrm(widevineUUID)
        val mivevineId = wvDrm.getPropertyByteArray(MediaDrm.PROPERTY_DEVICE_UNIQUE_ID)
        releaseMediaDRM(wvDrm)
        val md: MessageDigest = MessageDigest.getInstance("SHA-256")
        md.update(mivevineId)

        return md.digest().joinToString("") {
            java.lang.String.format("%02x", it)
        }
    }

    val drmId : String? by lazy {
        try {
            mediaDrmId()
        } catch (e: Exception) {
            null
        }
    }

    return gsfId ?: drmId ?: androidId ?: "nofingerprintandroid"
}