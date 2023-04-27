package io.kronor.example

import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.apollographql.apollo3.api.Optional
import com.apollographql.apollo3.exception.ApolloException
import io.kronor.example.type.Language
import io.kronor.example.type.PaymentSessionAdditionalData
import io.kronor.example.type.PaymentSessionInput
import io.kronor.example.type.SupportedCurrencyEnum
import java.io.IOException
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

class MainViewModel(private val savedStateHandle: SavedStateHandle) : ViewModel() {
    var paymentSessionToken: String?
        get() = savedStateHandle.get<String>("sessionToken")
        set(value) = savedStateHandle.set("sessionToken", value)
    
    private var _paymentMethodSelected : MutableState<String?> = mutableStateOf(null)
    val paymentMethodSelected: State<String?> = _paymentMethodSelected

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun createNewPaymentSession(
        amountToPay: String,
        currency: SupportedCurrencyEnum
    ): String? {
        val expiresAt = LocalDateTime.now().plusMinutes(5)
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'"))
        Log.d("NewPaymentSession", "test")
        val response = try {
            apolloClient().mutation(
                NewPaymentSessionMutation(
                    PaymentSessionInput(
                        amount = amountToPay.toInt(),
                        currency = Optional.present(currency),
                        expiresAt = expiresAt,
                        idempotencyKey = UUID.randomUUID().toString(),
                        merchantReference = "android-reference",
                        message = "random message from android",
                        additionalData = Optional.present(
                            PaymentSessionAdditionalData(
                                name = "Normal Android User",
                                ip = getLocalAddress().toString(),
                                language = Language.EN,
                                email = "normal@android.com",
                                phoneNumber = Optional.absent()
                            )
                        )
                    )
                )
            ).execute()
        } catch (e: ApolloException) {
            Log.e("NewPaymentSession", "Failed because: ${e.message}")
            null
        }
        val sessionToken = response?.data?.newPaymentSession?.token
        Log.d("NewPaymentSession", "Success $sessionToken")
        this.paymentSessionToken = sessionToken
        return sessionToken
    }


    @Throws(IOException::class)
    private fun getLocalAddress(): InetAddress? {
        try {
            val en: Enumeration<NetworkInterface> = NetworkInterface.getNetworkInterfaces()
            while (en.hasMoreElements()) {
                val intf: NetworkInterface = en.nextElement()
                val enumIpAddr: Enumeration<InetAddress> = intf.getInetAddresses()
                while (enumIpAddr.hasMoreElements()) {
                    val inetAddress: InetAddress = enumIpAddr.nextElement()
                    if (!inetAddress.isLoopbackAddress) {
                        return inetAddress
                    }
                }
            }
        } catch (ex: SocketException) {
            Log.e("Error", ex.toString())
        }
        return null
    }

    fun handleIntent(intent: Intent): Unit {
        intent.data?.let { uri ->
            uri.getQueryParameter("sessionToken")?.let { st ->
                this.paymentSessionToken = st
                uri.getQueryParameter("paymentMethod")?.let {
                    if (it == "swish") {
                        this._paymentMethodSelected.value = "swish"
                    } else if (it == "creditcard") {
                        this._paymentMethodSelected.value = "creditcard"
                    } else if (it == "mobilepay") {
                        this._paymentMethodSelected.value = "mobilepay"
                    } else if (it == "vipps") {
                        this._paymentMethodSelected.value = "vipps"
                    }
                }
            }
        }
    }

    fun resetPaymentState() : Unit {
        this._paymentMethodSelected.value = null
        this.paymentSessionToken = null
    }
}