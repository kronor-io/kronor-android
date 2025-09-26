package io.kronor.component.bank_transfer

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
import androidx.core.net.toUri
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
import io.kronor.api.type.GatewayEnum
import io.kronor.api.type.PaymentStatusEnum
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.UUID


class BankTransferViewModelFactory(
    private val BankTransferConfiguration: PaymentConfiguration
) : ViewModelProvider.NewInstanceFactory() {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return BankTransferViewModel(BankTransferConfiguration) as T
    }
}

class BankTransferViewModel(
    private val BankTransferConfiguration: PaymentConfiguration
) : ViewModel() {
    private val _subscribe: MutableState<Boolean> = mutableStateOf(false)
    internal val subscribe : Boolean by _subscribe
    private var intentReceived: Boolean = false
    private var deviceFingerprint: String? = null
    private val constructedRedirectUrl: Uri =
        BankTransferConfiguration.redirectUrl.buildUpon().appendQueryParameter("paymentMethod", "BankTransfer")
            .appendQueryParameter("sessionToken", BankTransferConfiguration.sessionToken).build()

    private val requests = Requests(BankTransferConfiguration.sessionToken, BankTransferConfiguration.environment)
    private var stateMachine: StateMachine<BankTransferStatechart.Companion.State, BankTransferStatechart.Companion.Event, BankTransferStatechart.Companion.SideEffect> =
        BankTransferStatechart().stateMachine
    private var _BankTransferState: MutableState<BankTransferStatechart.Companion.State> = mutableStateOf(
        BankTransferStatechart.Companion.State.Initializing
    )
    internal val bankTransferState: State<BankTransferStatechart.Companion.State> = _BankTransferState
    var paymentRequest: PaymentStatusSubscription.PaymentRequest? by mutableStateOf(null)
    private var waitToken: String? by mutableStateOf(null)

    private val _events = MutableSharedFlow<PaymentEvent>()
    val events: Flow<PaymentEvent> = _events

    internal fun transition(event: BankTransferStatechart.Companion.Event) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                _transition(event)
            }
        }
    }

    fun merchantLogo(): Int? {
        return BankTransferConfiguration.merchantLogo
    }

    fun setDeviceFingerPrint(context: Context) {
        this.deviceFingerprint ?: {
            this.deviceFingerprint = getWeakFingerprint(context)?.take(64)
        }
    }

    private suspend fun _transition(event: BankTransferStatechart.Companion.Event) {

        when (val result = stateMachine.transition(event)) {
            is StateMachine.Transition.Valid -> {
                _BankTransferState.value = result.toState
                result.sideEffect?.let {
                    handleSideEffect(it)
                }
            }

            is StateMachine.Transition.Invalid -> {
                Log.d(
                    "BankTransferViewModel", "Cannot transition to $event from ${result.fromState}"
                )
            }
        }
    }

    private suspend fun _transitionToError(t: Throwable?) {
        _transition(
            BankTransferStatechart.Companion.Event.Error(
                (t ?: KronorError.GraphQlError(ApiError(emptyList(), emptyMap()))) as KronorError
            )
        )
    }

    private suspend fun handleSideEffect(sideEffect: BankTransferStatechart.Companion.SideEffect) {
        when (sideEffect) {
            is BankTransferStatechart.Companion.SideEffect.CreatePaymentRequest -> {
                this.setDeviceFingerPrint(sideEffect.context)
                val waitToken = requests.makeNewPaymentRequest(
                    paymentRequestArgs = PaymentRequestArgs(
                        returnUrl = constructedRedirectUrl.toString(),
                        merchantReturnUrl = constructedRedirectUrl.toString(),
                        deviceFingerprint = deviceFingerprint ?: "fingerprint not found",
                        appName = BankTransferConfiguration.appName,
                        appVersion = BankTransferConfiguration.appVersion,
                        paymentMethod = PaymentMethod.BankTransfer
                    )
                )
                when {
                    waitToken.isFailure -> {
                        Log.d(
                            "BankTransferViewModel",
                            "Error creating payment request: ${waitToken.exceptionOrNull()}"
                        )
                        _transitionToError(waitToken.exceptionOrNull())
                    }

                    waitToken.isSuccess -> {
                        _transition(
                            BankTransferStatechart.Companion.Event.PaymentRequestCreated(
                                waitToken = waitToken.getOrNull()!!.paymentId
                            )
                        )
                    }
                }
            }

            is BankTransferStatechart.Companion.SideEffect.ListenOnPaymentRequest -> {
                this.waitToken = sideEffect.waitToken
                this._subscribe.value = true
            }

            is BankTransferStatechart.Companion.SideEffect.CancelPaymentRequest -> {
                Log.d("BankTransferViewModel", "reset payment flow")
                val waitToken = requests.cancelPayment()
                when {
                    waitToken.isFailure -> {
                        Log.d(
                            "BankTransferViewModel",
                            "Failed to cancel payment request: ${waitToken.exceptionOrNull()}"
                        )
                        _transitionToError(waitToken.exceptionOrNull())
                    }

                    waitToken.isSuccess -> {
                    }
                }
            }

            is BankTransferStatechart.Companion.SideEffect.ResetState -> {
                this._subscribe.value = false;
                this.waitToken = null;
            }

            is BankTransferStatechart.Companion.SideEffect.NotifyPaymentSuccess -> {
                Log.d("BankTransferViewModel", "Emitting success")
                _events.emit(PaymentEvent.PaymentSuccess(sideEffect.paymentId))
            }

            is BankTransferStatechart.Companion.SideEffect.NotifyPaymentFailure -> {
                Log.d("BankTransferViewModel", "Emitting failure")
                _events.emit(PaymentEvent.PaymentFailure)
            }

            BankTransferStatechart.Companion.SideEffect.OpenBankTransferWebView -> {
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
                Log.d("BankTransferViewModel", "Inside Collect")
                this.waitToken?.let {
                    this.paymentRequest = paymentRequestList.firstOrNull { paymentRequest ->
                        (paymentRequest.waitToken == this.waitToken) and (paymentRequest.status?.all { paymentStatus ->
                            (paymentStatus.status != PaymentStatusEnum.INITIALIZING)
                        } ?: false)
                    }

                    this.paymentRequest?.let { paymentRequest ->

                        paymentRequest.status?.let { statuses ->
                            if (statuses.any { it.status == PaymentStatusEnum.PAID || it.status == PaymentStatusEnum.FLOW_COMPLETED}) {
                                _transition(
                                    BankTransferStatechart.Companion.Event.PaymentAuthorized(
                                        paymentRequest.resultingPaymentId!!
                                    )
                                )
                            } else if (statuses.any { it.status == PaymentStatusEnum.ERROR || it.status == PaymentStatusEnum.DECLINED}) {
                                _transition(BankTransferStatechart.Companion.Event.PaymentRejected)
                            } else if (statuses.any { it.status == PaymentStatusEnum.CANCELLED}) {
                                _transition(BankTransferStatechart.Companion.Event.Retry)
                            } else if (_BankTransferState.value is BankTransferStatechart.Companion.State.WaitingForPaymentRequest) {
                                paymentRequest.paymentMethodUsed?.let { paymentMethod ->
                                    when (paymentMethod.gateway) {
                                        GatewayEnum.TRUSTLY -> {
                                            paymentRequest.transactionBankTransferDetails?.let {
                                                it[0].payUrl?.let { authorizationUrl ->
                                                    _transition(
                                                        BankTransferStatechart.Companion.Event.PaymentRequestInitialized(
                                                            authorizationUrl
                                                        )
                                                    )
                                                }
                                            }
                                        }
                                        else -> {
                                           _transition(BankTransferStatechart.Companion.Event.Error(KronorError.FlowError("Unsupported gateway: ${paymentMethod.gateway}")))
                                        }
                                    }

                                }
                            }
                        }
                    }
                } ?: run {
                    // When no waitToken is set, we should create a new payment request
                    Log.d("BankTransferViewModel", "${this.waitToken}")
                    Log.d("BankTransferViewModel", "intentReceived : ${this.intentReceived}")
                    this.paymentRequest = paymentRequestList.firstOrNull { paymentRequest ->
                        paymentRequest.status?.any {
                            it.status == PaymentStatusEnum.PAID || it.status == PaymentStatusEnum.AUTHORIZED
                        } ?: false
                    }
                    this.paymentRequest?.let { paymentRequest ->
                        _transition(
                            BankTransferStatechart.Companion.Event.PaymentAuthorized(
                                paymentRequest.resultingPaymentId!!
                            )
                        )
                    } ?: run {
                        if (this.intentReceived) {
                            _transition(BankTransferStatechart.Companion.Event.PaymentRejected)
                        } else {
                            _transition(BankTransferStatechart.Companion.Event.Initialize(context))
                        }
                    }
                }
            }
        } catch (e: ApolloException) {
            Log.d("BankTransferViewModel", "Payment Subscription error: $e")
            _transition(
                BankTransferStatechart.Companion.Event.Error(KronorError.NetworkError(e))
            )
        }
    }

    fun handleIntent(intent: Intent) {
        intent.data?.let { uri ->
            if (uri.getQueryParameter("paymentMethod") == "BankTransfer") {
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
        val URI = "content://com.google.android.gsf.gservices".toUri()
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