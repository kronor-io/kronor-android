package io.kronor.example

import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.apollographql.apollo3.api.Optional
import com.apollographql.apollo3.exception.ApolloException
import io.kronor.example.type.Language
import io.kronor.example.type.PaymentSessionAdditionalData
import io.kronor.example.type.PaymentSessionInput
import io.kronor.example.type.SupportedCurrencyEnum
import io.kronor.example.ui.theme.KronorSDKTheme
import io.kronor.api.Environment
import io.kronor.component.swish.GetSwishComponent
import io.kronor.component.swish.SwishConfiguration
import kotlinx.coroutines.*
import java.io.IOException
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

class MainActivity : ComponentActivity() {

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KronorTestApp()
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun KronorTestApp() {
    KronorSDKTheme {
        val navController = rememberNavController()
        NavHost(navController = navController, startDestination = "paymentMethods") {
            composable("paymentMethods") {
                paymentMethodsScreen(onNavigateToSwish = { sessionToken ->
                    navController.navigate("swishScreen/${sessionToken}")
                })
            }
            composable(
                "swishScreen/{sessionToken}",
                arguments = listOf(navArgument("sessionToken") { type = NavType.StringType })
            ) {

                it.arguments?.getString("sessionToken")?.let { sessionToken ->
                    val swishConfiguration = SwishConfiguration(
                        sessionToken = sessionToken,
                        merchantLogo = R.drawable.kronor_logo,
                        environment = Environment.Staging,
                        appName = "kronor-android-test",
                        appVersion = "0.1.0",
                        locale = Locale("en_US"),
                        redirectUrl = Uri.parse("kronor_test://"),
                        onPaymentSuccess = {
                            navController.navigate("paymentMethods")
                        },
                        onPaymentFailure = {
                            navController.navigate("paymentMethods")
                        }
                    )
                    GetSwishComponent(LocalContext.current, swishConfiguration)
                }
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun paymentMethodsScreen(onNavigateToSwish: (String) -> Unit) {
    var amount by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(""))
    }

    Surface(
        modifier = Modifier.fillMaxSize(), color = MaterialTheme.colors.background
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Amount: ")
                TextField(
                    value = amount,
                    onValueChange = { amount = it },
                    keyboardOptions = KeyboardOptions.Default.copy(
                        keyboardType = KeyboardType.Decimal
                    )
                )
            }
            Button(onClick = {
                GlobalScope.launch {
                    withContext(Dispatchers.Main) {
                        val sessionToken = createNewPaymentSession(amount.text)

                        sessionToken?.let {
                            onNavigateToSwish(it)
                        }
                    }
                }
            }) {
                Text("Pay ${amount.text}")
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
suspend fun createNewPaymentSession(amountToPay: String): String? {
    val expiresAt = LocalDateTime.now().plusMinutes(5)
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'"))
    Log.d("NewPaymentSession", "test")
    val response = try {
        apolloClient().mutation(
            NewPaymentSessionMutation(
                PaymentSessionInput(
                    amount = amountToPay.toInt() * 100,
                    currency = Optional.present(SupportedCurrencyEnum.SEK),
                    expiresAt = expiresAt,
                    idempotencyKey = UUID.randomUUID().toString(),
                    merchantReference = "reference",
                    message = "random message",
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

@RequiresApi(Build.VERSION_CODES.O)
@Preview(showBackground = true)
@Composable
fun DefaultPaymentMethodsPreview() {
    paymentMethodsScreen(onNavigateToSwish = {})
}