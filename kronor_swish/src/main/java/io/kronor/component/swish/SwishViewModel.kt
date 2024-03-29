package io.kronor.component.swish

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
import io.kronor.api.ApiError
import io.kronor.api.KronorError
import io.kronor.api.PaymentConfiguration
import io.kronor.api.PaymentEvent
import io.kronor.api.PaymentMethod
import io.kronor.api.PaymentRequestArgs
import io.kronor.api.PaymentStatusSubscription
import io.kronor.api.Requests
import io.kronor.api.makeNewPaymentRequest
import io.kronor.api.type.PaymentStatusEnum
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.UUID

class SwishViewModelFactory(
    private val swishConfiguration: PaymentConfiguration
) : ViewModelProvider.NewInstanceFactory() {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return SwishViewModel(swishConfiguration) as T
    }
}

class SwishViewModel(
    private val swishConfiguration: PaymentConfiguration
) : ViewModel() {
    private val _subscribe: MutableState<Boolean> = mutableStateOf(false)
    internal val subscribe : Boolean by _subscribe
    private var intentReceived: Boolean = false
    private var deviceFingerprint: String? = null
    private val constructedRedirectUrl: Uri =
        swishConfiguration.redirectUrl.buildUpon().appendQueryParameter("paymentMethod", "swish")
            .appendQueryParameter("sessionToken", swishConfiguration.sessionToken).build()

    private val requests = Requests(swishConfiguration.sessionToken, swishConfiguration.environment)
    private var stateMachine: StateMachine<SwishStatechart.Companion.State, SwishStatechart.Companion.Event, SwishStatechart.Companion.SideEffect> =
        SwishStatechart().stateMachine
    private var _swishState: MutableState<SwishStatechart.Companion.State> = mutableStateOf(
        SwishStatechart.Companion.State.PromptingMethod
    )
    internal val swishState: State<SwishStatechart.Companion.State> = _swishState
    var paymentRequest: PaymentStatusSubscription.PaymentRequest? by mutableStateOf(null)
    private var waitToken: String? by mutableStateOf(null)
    private var _selectedMethod: MutableState<SelectedMethod?> = mutableStateOf(null)
    internal val selectedMethod: State<SelectedMethod?> = _selectedMethod

    private val _events = MutableSharedFlow<PaymentEvent>()
    val events: Flow<PaymentEvent> = _events

    internal fun transition(event: SwishStatechart.Companion.Event) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                _transition(event)
            }
        }
    }

    fun merchantLogo(): Int? {
        return swishConfiguration.merchantLogo
    }

    internal fun updateSelectedMethod(selected: SelectedMethod) {
        _selectedMethod.value = selected
    }

    fun setDeviceFingerPrint(context: Context) {
        this.deviceFingerprint ?: {
            this.deviceFingerprint = getWeakFingerprint(context)?.take(64)
        }
    }

    private suspend fun _transition(event: SwishStatechart.Companion.Event) {

        when (val result = stateMachine.transition(event)) {
            is StateMachine.Transition.Valid -> {
                _swishState.value = result.toState
                result.sideEffect?.let {
                    handleSideEffect(it)
                }
            }

            is StateMachine.Transition.Invalid -> {
                Log.d(
                    "SwishViewModel", "Cannot transition to $event from ${result.fromState}"
                )
            }
        }
    }

    private suspend fun _transitionToError(t: Throwable?) {
        _transition(
            SwishStatechart.Companion.Event.Error(
                (t ?: KronorError.GraphQlError(ApiError(emptyList(), emptyMap()))) as KronorError
            )
        )
    }

    private suspend fun handleSideEffect(sideEffect: SwishStatechart.Companion.SideEffect) {
        when (sideEffect) {
            is SwishStatechart.Companion.SideEffect.CreateMcomPaymentRequest -> {
                Log.d("SwishViewModel", "Creating Mcom Payment Request")

                this.setDeviceFingerPrint(sideEffect.context)
                val waitToken = requests.makeNewPaymentRequest(
                    paymentRequestArgs = PaymentRequestArgs(
                        returnUrl = constructedRedirectUrl.toString(),
                        merchantReturnUrl = constructedRedirectUrl.toString(),
                        deviceFingerprint = deviceFingerprint ?: "fingerprint not found",
                        appName = swishConfiguration.appName,
                        appVersion = swishConfiguration.appVersion,
                        paymentMethod = PaymentMethod.Swish()
                    )
                )
                when {
                    waitToken.isFailure -> {
                        Log.d(
                            "SwishViewModel",
                            "Error creating mcom request: ${waitToken.exceptionOrNull()}"
                        )
                        _transitionToError(waitToken.exceptionOrNull())
                    }

                    waitToken.isSuccess -> {
                        _transition(
                            SwishStatechart.Companion.Event.PaymentRequestCreated(
                                waitToken = waitToken.getOrNull()!!
                            )
                        )
                    }
                }
            }

            is SwishStatechart.Companion.SideEffect.CreateEcomPaymentRequest -> {
                Log.d("SwishViewModel", "Creating Ecom Payment Request")
                this.setDeviceFingerPrint(sideEffect.context)
                val waitToken = requests.makeNewPaymentRequest(
                    paymentRequestArgs = PaymentRequestArgs(
                        paymentMethod = PaymentMethod.Swish(sideEffect.phoneNumber),
                        returnUrl = constructedRedirectUrl.toString(),
                        merchantReturnUrl = constructedRedirectUrl.toString(),
                        deviceFingerprint = deviceFingerprint ?: "fingerprint not found",
                        appName = swishConfiguration.appName,
                        appVersion = swishConfiguration.appVersion
                    ),
                )
                when {
                    waitToken.isFailure -> {
                        Log.d(
                            "SwishViewModel",
                            "Error creating ecom request: ${waitToken.exceptionOrNull()}"
                        )
                        _transitionToError(waitToken.exceptionOrNull())
                    }

                    waitToken.isSuccess -> {
                        _transition(
                            SwishStatechart.Companion.Event.PaymentRequestCreated(
                                waitToken = waitToken.getOrNull()!!
                            )
                        )
                    }
                }
            }

            is SwishStatechart.Companion.SideEffect.ListenOnPaymentRequest -> {
                this.waitToken = sideEffect.waitToken
                this._subscribe.value = true
            }

            is SwishStatechart.Companion.SideEffect.CancelPaymentRequest -> {
                Log.d("SwishViewModel", "reset payment flow")
                val waitToken = requests.cancelPayment()
                when {
                    waitToken.isFailure -> {
                        Log.d(
                            "SwishViewModel",
                            "Failed to cancel payment request: ${waitToken.exceptionOrNull()}"
                        )
                        _transitionToError(waitToken.exceptionOrNull())
                    }

                    waitToken.isSuccess -> {
                    }
                }
            }

            is SwishStatechart.Companion.SideEffect.ResetState -> {
                this._subscribe.value = false;
                this.waitToken = null;
            }

            is SwishStatechart.Companion.SideEffect.NotifyPaymentSuccess -> {
                Log.d("SwishViewModel", "Emitting success")
                _events.emit(PaymentEvent.PaymentSuccess(sideEffect.paymentId))
            }

            is SwishStatechart.Companion.SideEffect.NotifyPaymentFailure -> {
                Log.d("SwishViewModel", "Emitting failure")
                _events.emit(PaymentEvent.PaymentFailure)
            }

            SwishStatechart.Companion.SideEffect.OpenSwishApp -> {
            }
        }
    }

    internal suspend fun subscription() {
        // If we have a waitToken set in our view model, get the payment request
        // associated with that waitToken and in a status that is not initializing

        try {
            requests.getPaymentRequests().collect { paymentRequestList ->
                // If we have a waitToken set in our view model, get the payment request
                // associated with that waitToken and in a status that is not initializing
                Log.d("SwishViewModel", "Inside Collect")
                this.waitToken?.let {
                    this.paymentRequest = paymentRequestList.firstOrNull { paymentRequest ->
                        (paymentRequest.waitToken == this.waitToken) and (paymentRequest.status?.all { paymentStatus ->
                            (paymentStatus.status != PaymentStatusEnum.INITIALIZING)
                        } ?: false)
                    }

                    this.paymentRequest?.let { paymentRequest ->
                        val selected: SelectedMethod = selectedMethod.value ?: run {
                            when (paymentRequest.paymentFlow) {
                                "mcom" -> SelectedMethod.QrCode
                                "ecom" -> SelectedMethod.PhoneNumber
                                else -> SelectedMethod.QrCode
                            }
                        }

                        paymentRequest.status?.let { statuses ->
                            if (statuses.any { it.status == PaymentStatusEnum.PAID }) {
                                _transition(
                                    SwishStatechart.Companion.Event.PaymentAuthorized(
                                        paymentRequest.resultingPaymentId!!
                                    )
                                )
                            } else if (statuses.any { it.status == PaymentStatusEnum.ERROR || it.status == PaymentStatusEnum.DECLINED}) {
                                _transition(SwishStatechart.Companion.Event.PaymentRejected)
                            } else if (statuses.any { it.status == PaymentStatusEnum.CANCELLED}) {
                                _transition(SwishStatechart.Companion.Event.Retry)
                            } else if (_swishState.value is SwishStatechart.Companion.State.WaitingForPaymentRequest) {
                                _transition(
                                    SwishStatechart.Companion.Event.PaymentRequestInitialized(
                                        selected
                                    )
                                )
                            }
                        }
                    }
                    return@let waitToken
                } ?: run {
                    // When no waitToken is set, we should create a new payment request
                    Log.d("SwishViewModel", "${this.waitToken}")
                    Log.d("SwishViewModel", "intentReceived : ${this.intentReceived}")
                    this.paymentRequest = paymentRequestList.firstOrNull { paymentRequest ->
                        paymentRequest.status?.any {
                            it.status == PaymentStatusEnum.PAID || it.status == PaymentStatusEnum.AUTHORIZED
                        } ?: false
                    }
                    this.paymentRequest?.let { paymentRequest ->
                        _transition(
                            SwishStatechart.Companion.Event.PaymentAuthorized(
                                paymentRequest.resultingPaymentId!!
                            )
                        )
                    } ?: run {
                        if (this.intentReceived) {
                            _transition(SwishStatechart.Companion.Event.PaymentRejected)
                        } else {
                            _transition(SwishStatechart.Companion.Event.Prompt)
                        }
                    }
                }
            }
        } catch (e: ApolloException) {
            Log.d("SwishViewModel", "Payment Subscription error: $e")
            _transition(
                SwishStatechart.Companion.Event.Error(KronorError.NetworkError(e))
            )
        }
    }

    fun handleIntent(intent: Intent) {
        intent.data?.let { uri ->
            if (uri.getQueryParameter("paymentMethod") == "swish") {
                this.intentReceived = true
            }
        }
    }

}

@SuppressLint("HardwareIds")
private fun getWeakFingerprint(context: Context): String? {
    val contentResolver: ContentResolver = context.contentResolver!!

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

    val gsfId: String? by lazy {
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

    val drmId: String? by lazy {
        try {
            mediaDrmId()
        } catch (e: Exception) {
            null
        }
    }

    return gsfId ?: drmId ?: androidId
}