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
import io.kronor.api.ApiError
import io.kronor.api.PaymentMethod
import io.kronor.example.type.AddressInput
import io.kronor.example.type.Country
import io.kronor.example.type.Language
import io.kronor.example.type.PaymentSessionAdditionalData
import io.kronor.example.type.PaymentSessionInput
import io.kronor.example.type.PaymentSessionWithReferenceCheckInput
import io.kronor.example.type.PurchaseOrderLineInput
import io.kronor.example.type.SupportedCurrencyEnum
import java.io.IOException
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketException
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.Result.Companion.failure
import kotlin.Result.Companion.success

sealed class KronorApiResponse {
    data class Error(val e: String?) : KronorApiResponse()

    data class Response(val token: String) : KronorApiResponse()
}

class MainViewModel(private val savedStateHandle: SavedStateHandle) : ViewModel() {
    var paymentSessionToken: String?
        get() = savedStateHandle.get<String>("sessionToken")
        set(value) = savedStateHandle.set("sessionToken", value)

    private var _paymentMethodSelected: MutableState<PaymentMethod?> = mutableStateOf(null)
    val paymentMethodSelected: State<PaymentMethod?> = _paymentMethodSelected

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun createNewPaymentSession(
        amountToPay: String, country: Country, currency: SupportedCurrencyEnum
    ): KronorApiResponse {
        val expiresAt = Instant.now().plusSeconds(86000).toString()
        Log.d("NewPaymentSession", "test")
        val response = try {
            apolloClient().mutation(
                NewPaymentSessionMutation(
                    PaymentSessionInput(
                        amount = amountToPay.toInt(),
                        currency = Optional.present(currency),
                        country = Optional.present(country),
                        expiresAt = expiresAt,
                        idempotencyKey = UUID.randomUUID().toString(),
                        merchantReference = "bz1223232",
                        message = "payment testing hello world",
                        preferredGateway = Optional.present("Kronor"),
                        additionalData = Optional.present(
                            PaymentSessionAdditionalData(
                                name = "Normal Android User",
                                ip = "127.0.0.1",
                                language = Language.EN,
                                email = "normal@android.com",
                                phoneNumber = Optional.absent(),
                                shippingAddress = Optional.present(
                                    AddressInput(
                                        firstName = "test",
                                        lastName = "user",
                                        streetAddress = "Hyllie Boulevard",
                                        postalCode = "21537",
                                        city = "Malmö",
                                        country = Country.SE,
                                        email = "normal@android.com",
                                        phoneNumber = "+46111111111"
                                    )
                                ),
                                orderLines = Optional.present(
                                    listOf(
                                        PurchaseOrderLineInput(
                                            pricePerItem = amountToPay.toInt(),
                                            totalAmount = amountToPay.toInt(),
                                            totalTaxAmount = 0,
                                            quantity = 1,
                                            taxRate = 0,
                                            name = "Item 1",
                                            reference = Optional.present("ref1")
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            ).execute()
        } catch (e: ApolloException) {
            Log.e("NewPaymentSession", "Failed because: ${e.message}")
            return KronorApiResponse.Error(e.message)
        }

        return response.data?.newPaymentSession?.let { it ->
            Log.d("NewPaymentSession", "Success ${it.token}")
            this.paymentSessionToken = it.token
            KronorApiResponse.Response(it.token)
        } ?: run {
            var extensionMsgs : String = ""
            response.errors?.let {
                extensionMsgs += it.joinToString("\n")
            } ?: run {
                response.extensions.forEach {
                    key, value ->
                        extensionMsgs += "$key: $value\n"
                }
            }
            if (extensionMsgs.isEmpty()) {
                extensionMsgs = "Something went wrong"
            }
            return KronorApiResponse.Error(extensionMsgs)
        }
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
                        this._paymentMethodSelected.value = PaymentMethod.Swish()
                    } else if (it == "creditcard") {
                        this._paymentMethodSelected.value = PaymentMethod.CreditCard
                    } else if (it == "mobilepay") {
                        this._paymentMethodSelected.value = PaymentMethod.MobilePay
                    } else if (it == "vipps") {
                        this._paymentMethodSelected.value = PaymentMethod.Vipps
                    } else if (it == "paypal") {
                        this._paymentMethodSelected.value = PaymentMethod.PayPal
                    } else {
                        this._paymentMethodSelected.value = PaymentMethod.Fallback(it)
                    }
                }
            }
        }
    }

    fun resetPaymentState(): Unit {
        this._paymentMethodSelected.value = null
        this.paymentSessionToken = null
    }
}