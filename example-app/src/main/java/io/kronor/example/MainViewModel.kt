package io.kronor.example

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import com.apollographql.apollo.api.Optional
import com.apollographql.apollo.exception.ApolloException
import io.kronor.api.ApiError
import io.kronor.example.type.GatewayEnum
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

class MainViewModel : ViewModel() {
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun createNewPaymentSession(
        amountToPay: String, country: Country, currency: SupportedCurrencyEnum, gateway: GatewayEnum?
    ): KronorApiResponse {
        val expiresAt = Instant.now().plusSeconds(300).toString()
        Log.d("NewPaymentSession", "test")
        val response = try {
            apolloClient().mutation(
                NewPaymentSessionWithReferenceCheckMutation(
                    PaymentSessionWithReferenceCheckInput(
                        amount = amountToPay.toInt(),
                        currency = currency,
                        country = country,
                        expiresAt = expiresAt,
                        idempotencyKey = UUID.randomUUID().toString(),
                        merchantReference = "android-" + UUID.randomUUID().toString(),
                        message = "random message from android",
                        preferredGateway = Optional.present(gateway),
                        additionalData = Optional.present(
                            PaymentSessionAdditionalData(
                                name = "Normal Android User",
                                ip = "192.168.1.1",
                                language = Language.EN,
                                email = "normal@android.com",
                                phoneNumber = Optional.present("+46740555111"),
                                shippingAddress = Optional.present(
                                    AddressInput(
                                        firstName = "test",
                                        lastName = "user",
                                        streetAddress = "Hyllie Boulevard",
                                        postalCode = "21537",
                                        city = "Malmö",
                                        country = Country.SE,
                                        email = "normal@android.com",
                                        phoneNumber = "+46740555111"
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
        if (response.exception != null) {
            return KronorApiResponse.Error(response.exception!!.toString())
        }
        return response.data?.newPaymentSessionWithReferenceCheck?.let { it ->
            Log.d("NewPaymentSession", "Success")
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

}
