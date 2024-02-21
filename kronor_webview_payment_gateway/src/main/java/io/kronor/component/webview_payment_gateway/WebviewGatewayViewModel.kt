package io.kronor.component.webview_payment_gateway

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.media.MediaDrm
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.apollographql.apollo3.exception.ApolloException
import com.tinder.StateMachine
import io.kronor.api.*
import io.kronor.api.type.PaymentStatusEnum
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private val DelayBeforeCallback: Duration = 2.seconds // 2000 milliseconds = 2 seconds

class WebviewGatewayViewModelFactory(
    private val WebviewGatewayConfiguration: PaymentConfiguration,
    private val paymentMethod: PaymentMethod
) : ViewModelProvider.NewInstanceFactory() {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return WebviewGatewayViewModel(WebviewGatewayConfiguration, paymentMethod) as T
    }
}

class WebviewGatewayViewModel(
    val webviewGatewayConfiguration: PaymentConfiguration,
    val paymentMethod: PaymentMethod,
) : ViewModel() {
    private val _subscribe: MutableState<Boolean> = mutableStateOf(false)
    internal val subscribe : Boolean by _subscribe
    private var intentReceived: Boolean = false
    private var deviceFingerprint: String? = null
    private val constructedRedirectUrl  : Uri =
        webviewGatewayConfiguration.redirectUrl
            .buildUpon()
            .appendQueryParameter("paymentMethod", paymentMethod.toRedirectMethod())
            .appendQueryParameter("sessionToken", webviewGatewayConfiguration.sessionToken)
            .build()

    private val requests =
        Requests(webviewGatewayConfiguration.sessionToken, webviewGatewayConfiguration.environment)
    private val stateMachine: StateMachine<WebviewGatewayStatechart.Companion.State, WebviewGatewayStatechart.Companion.Event, WebviewGatewayStatechart.Companion.SideEffect> =
        WebviewGatewayStatechart().stateMachine
    private var _webviewGatewayState: MutableState<WebviewGatewayStatechart.Companion.State> =
        mutableStateOf(
            WebviewGatewayStatechart.Companion.State.Initializing
        )
    internal val webviewGatewayState: State<WebviewGatewayStatechart.Companion.State> = _webviewGatewayState
    private var paymentRequest: PaymentStatusSubscription.PaymentRequest? by mutableStateOf(null)
    private var waitToken: String? by mutableStateOf(null)
    val paymentGatewayUrl: Uri = constructPaymentGatewayUrl(
        environment = webviewGatewayConfiguration.environment,
        sessionToken = webviewGatewayConfiguration.sessionToken,
        paymentMethod = paymentMethod,
        merchantReturnUrl = this.constructedRedirectUrl,
        isRedirectPage = false
    )

    val paymentRedirectUrl: Uri = constructPaymentGatewayUrl(
        environment = webviewGatewayConfiguration.environment,
        sessionToken = webviewGatewayConfiguration.sessionToken,
        paymentMethod = paymentMethod,
        merchantReturnUrl = this.constructedRedirectUrl,
        isRedirectPage = true
    )

    private val _events = MutableSharedFlow<PaymentEvent>()
    val events: Flow<PaymentEvent> = _events

    internal fun transition(event: WebviewGatewayStatechart.Companion.Event) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                _transition(event)
            }
        }
    }

    fun setDeviceFingerPrint(context: Context) {
        this.deviceFingerprint ?: {
            this.deviceFingerprint = getWeakFingerprint(context)?.take(64)
        }
    }

    private suspend fun _transition(event: WebviewGatewayStatechart.Companion.Event) {

        when (val result = stateMachine.transition(event)) {
            is StateMachine.Transition.Valid -> {
                _webviewGatewayState.value = result.toState
                result.sideEffect?.let {
                    handleSideEffect(it)
                }
            }

            is StateMachine.Transition.Invalid -> {
                Log.d(
                    "WebviewGatewayViewModel",
                    "Cannot transition to $event from ${result.fromState}"
                )
            }
        }
    }

    private suspend fun _transitionToError(t: Throwable?) {
        _transition(
            WebviewGatewayStatechart.Companion.Event.Error(
                (t ?: KronorError.GraphQlError(ApiError(emptyList(), emptyMap()))) as KronorError
            )
        )
    }

    private suspend fun handleSideEffect(sideEffect: WebviewGatewayStatechart.Companion.SideEffect) {
//        Log.d("WebviewGatewayViewModel", "handleSideEffect: ${this._webviewGatewayState}")
        when (sideEffect) {
            is WebviewGatewayStatechart.Companion.SideEffect.CreatePaymentRequest -> {
                if (this.paymentMethod is PaymentMethod.Fallback) {
                    _transition(WebviewGatewayStatechart.Companion.Event.PaymentRequestWillBeCreatedElsewhere)
                    this._subscribe.value = true
                    return
                }
                Log.d("WebviewGatewayViewModel", "Creating Payment Request")
                setDeviceFingerPrint(sideEffect.context)
                val waitToken = requests.makeNewPaymentRequest(
                    paymentRequestArgs = PaymentRequestArgs(
                        returnUrl = this.paymentRedirectUrl.toString(),
                        merchantReturnUrl = this.constructedRedirectUrl.toString(),
                        deviceFingerprint = deviceFingerprint ?: "fingerprint not found",
                        appName = webviewGatewayConfiguration.appName,
                        appVersion = webviewGatewayConfiguration.appVersion,
                        paymentMethod = paymentMethod
                    )
                )
                when {
                    waitToken.isFailure -> {
                        Log.d(
                            "WebviewGatewayViewModel",
                            "Error creating payment request: ${waitToken.exceptionOrNull()}"
                        )
                        _transitionToError(waitToken.exceptionOrNull())
                    }

                    waitToken.isSuccess -> {
                        _transition(
                            WebviewGatewayStatechart.Companion.Event.PaymentRequestCreated(
                                waitToken = waitToken.getOrNull()!!
                            )
                        )
                    }
                }
            }

            is WebviewGatewayStatechart.Companion.SideEffect.ListenOnPaymentRequest -> {
                this.waitToken = sideEffect.waitToken
                this._subscribe.value = true
            }

            is WebviewGatewayStatechart.Companion.SideEffect.CancelPaymentRequest -> {
                val waitToken = requests.cancelPayment()
                when {
                    waitToken.isFailure -> {
                        Log.d(
                            "WebviewGatewayViewModel",
                            "Failed to cancel payment request: ${waitToken.exceptionOrNull()}"
                        )
                        _transitionToError(waitToken.exceptionOrNull())
                    }

                    waitToken.isSuccess -> {}
                }
            }

            is WebviewGatewayStatechart.Companion.SideEffect.ResetState -> {
                Log.d("WebviewGatewayViewModel", "Reset payment flow")
                val waitToken = requests.cancelPayment()
                when {
                    waitToken.isFailure -> {
                        Log.d(
                            "WebviewGatewayViewModel",
                            "Failed to cancel payment request: ${waitToken.exceptionOrNull()}"
                        )
                        _transitionToError(waitToken.exceptionOrNull())
                    }

                    waitToken.isSuccess -> {
                        this._subscribe.value = false
                        this.waitToken = null
                    }
                }
            }

            is WebviewGatewayStatechart.Companion.SideEffect.NotifyPaymentSuccess -> {
                Log.d("WebviewGatewayViewModel", "Emitting success")
                _events.emit(PaymentEvent.PaymentSuccess(sideEffect.paymentId))
            }

            is WebviewGatewayStatechart.Companion.SideEffect.NotifyPaymentFailure -> {
                Log.d("WebviewGatewayViewModel", "Emitting failure")
                _events.emit(PaymentEvent.PaymentFailure)
            }

            is WebviewGatewayStatechart.Companion.SideEffect.OpenEmbeddedSite -> {

            }

            is WebviewGatewayStatechart.Companion.SideEffect.CancelAndNotifyFailure -> {
                _events.emit(PaymentEvent.PaymentFailure)
            }

            is WebviewGatewayStatechart.Companion.SideEffect.CancelAfterDeadline -> {
                viewModelScope.launch {
                    delay(DelayBeforeCallback)
                    _transition(WebviewGatewayStatechart.Companion.Event.Cancel)
                }
            }
        }
    }

    internal suspend fun subscription(context: Context) {
        // If we have a waitToken set in our view model, get the payment request
        // associated with that waitToken and in a status that is not initializing

        try {
            requests.getPaymentRequests().collect { paymentRequestList ->
                // If we have a waitToken set in our view model, get the payment request
                // associated with that waitToken and in a status that is not initializing
                Log.d("WebviewGatewayViewModel", "Inside Collect")
                this.waitToken?.let {
                    this.paymentRequest = paymentRequestList.firstOrNull { paymentRequest ->
                        (paymentRequest.waitToken == this.waitToken) and (paymentRequest.status?.all { paymentStatus ->
                            (paymentStatus.status != PaymentStatusEnum.INITIALIZING)
                        } ?: false)
                    }

                    this.paymentRequest?.let { paymentRequest ->
                        paymentRequest.status?.let { statuses ->
                            if (statuses.any { it.status == PaymentStatusEnum.PAID || it.status == PaymentStatusEnum.AUTHORIZED}) {
                                _transition(
                                    WebviewGatewayStatechart.Companion.Event.PaymentAuthorized(
                                        paymentRequest.resultingPaymentId!!
                                    )
                                )
                            } else if (statuses.any {it.status == PaymentStatusEnum.ERROR || it.status == PaymentStatusEnum.DECLINED}) {
                                _transition(WebviewGatewayStatechart.Companion.Event.PaymentRejected)

                            } else if (statuses.any {it.status == PaymentStatusEnum.CANCELLED}) {
                                _transition(WebviewGatewayStatechart.Companion.Event.Retry)

                            } else {
                                _transition(WebviewGatewayStatechart.Companion.Event.PaymentRequestInitialized)
                            }
                        }
                    }
                    return@let waitToken
                } ?: run {
                    // When no waitToken is set, we should create a new payment request
                    Log.d("WebviewGatewayViewModel", "${this.waitToken}")
                    Log.d("WebviewGatewayViewModel", "intentReceived : ${this.intentReceived}")
                    this.paymentRequest = paymentRequestList.firstOrNull { paymentRequest ->
                        paymentRequest.status?.any {
                            it.status == PaymentStatusEnum.PAID || it.status == PaymentStatusEnum.AUTHORIZED
                        } ?: false
                    }
                    this.paymentRequest?.let { paymentRequest ->
                        _transition(
                            WebviewGatewayStatechart.Companion.Event.PaymentAuthorized(
                                paymentRequest.resultingPaymentId!!
                            )
                        )
                    } ?: run {
                        if (this.intentReceived && !(_webviewGatewayState.value == WebviewGatewayStatechart.Companion.State.Initializing)) {
                            _transition(WebviewGatewayStatechart.Companion.Event.PaymentRejected)
                        } else {
                            if (!(_webviewGatewayState.value == WebviewGatewayStatechart.Companion.State.PaymentRequestInitialized)) {
                                _transition(WebviewGatewayStatechart.Companion.Event.Initialize(context))
                            }
                        }
                    }
                }
            }
        } catch (e: ApolloException) {
            Log.d("WebviewGatewayViewModel", "Payment Subscription error: $e")
            _transition(
                WebviewGatewayStatechart.Companion.Event.Error(KronorError.NetworkError(e))
            )
        }
    }

    suspend fun handleIntent(intent: Intent) {
        intent.data?.let { uri ->
            if (uri.getQueryParameter("paymentMethod") == this.paymentMethod.toRedirectMethod()) {
                this.intentReceived = true
                if (uri.queryParameterNames.contains("cancel")) {
                    _transition(WebviewGatewayStatechart.Companion.Event.WaitForCancel)
                }
            }
        }
    }
}

private fun constructPaymentGatewayUrl(
    environment: Environment, sessionToken: String, paymentMethod: PaymentMethod, merchantReturnUrl: Uri, isRedirectPage : Boolean
): Uri {
    val paymentGatewayHost = when (environment) {
        Environment.Staging -> {
            "payment-gateway.staging.kronor.io"
        }

        Environment.Production -> {
            "payment-gateway.kronor.io"
        }
    }
    return Uri.Builder().scheme("https").authority(paymentGatewayHost).appendPath(if (isRedirectPage) getRedirectPage(paymentMethod) else "payment")
        .appendQueryParameter("env", toGatewayEnvName(environment))
        .appendQueryParameter("paymentMethod", paymentMethod.toPaymentGatewayMethod())
        .appendQueryParameter("token", sessionToken)
        .appendQueryParameter("merchantReturnUrl", merchantReturnUrl.toString()).build()
}

fun getRedirectPage(paymentMethod: PaymentMethod): String {
    return when (paymentMethod) {
        is PaymentMethod.Swish -> "swish-redirect"
        is PaymentMethod.MobilePay -> "reepay-redirect"
        is PaymentMethod.Vipps -> "reepay-redirect"
        is PaymentMethod.CreditCard -> "reepay-redirect"
        is PaymentMethod.PayPal -> "paypal-redirect"
        is PaymentMethod.Fallback -> "payment"
    }
}

private fun toGatewayEnvName(environment: Environment): String {
    return when (environment) {
        Environment.Staging -> {
            "staging"
        }

        Environment.Production -> {
            "prod"
        }
    }
}

@SuppressLint("HardwareIds")
private fun getWeakFingerprint(context: Context) : String? {
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

    return gsfId ?: drmId ?: androidId
}