package com.kronor.payment_sdk

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.compose.material.Text
import androidx.compose.foundation.layout.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Surface
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.TextField
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
import com.kronor.payment_sdk.type.PaymentSessionInput
import com.kronor.payment_sdk.type.SupportedCurrencyEnum
import com.kronor.payment_sdk.ui.theme.KronorSDKTheme
import io.kronor.api.Environment
import io.kronor.component.swish.MainSwishScreen
import io.kronor.component.swish.SwishConfiguration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
                    MainSwishScreen(
                        SwishConfiguration(
                            sessionToken = sessionToken                            ,
                            merchantLogo = R.drawable.boozt_logo,
                            environment = Environment.Staging,
                            appName= "kronor-android-test",
                            appVersion="0.1.0",
                            locale=Locale("en_US"),
                            redirectUrl = Uri.parse("kronor_test://")
                        )
                    )
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
            val context = LocalContext.current
            Button(onClick = {
                GlobalScope.launch {
                    withContext(Dispatchers.Main) {
                        val sessionToken = createNewPaymentSession(context, amount.text)
//                            val sessionToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJleHAiOiAxNjc0NjYzNjA5LjAwMDAwMCwgImlhdCI6IDE2NzQ2NDM1MTUuNzY1ODcwLCAidGlkIjogIjFmMDdhNmU1LTQ0YTktNDE1MS1hMGUyLWE4M2FkYTUyODFjOCIsICJ0bmFtZSI6IG51bGwsICJ0dHlwZSI6ICJiYWNrZW5kIiwgImh0dHBzOi8vaGFzdXJhLmlvL2p3dC9jbGFpbXMiOiB7IngtaGFzdXJhLW1lcmNoYW50LWlkIjogIjIiLCAieC1oYXN1cmEtZGVmYXVsdC1yb2xlIjogInBheW1lbnQtZ2F0ZXdheSIsICJ4LWhhc3VyYS1hbGxvd2VkLXJvbGVzIjogWyJwYXltZW50LWdhdGV3YXkiXSwgIngtaGFzdXJhLXBheW1lbnQtYW1vdW50IjogIjExMTAwIiwgIngtaGFzdXJhLXBheWVlLXJlZmVyZW5jZSI6ICJyZWZlcmVuY2UiLCAieC1oYXN1cmEtcGF5bWVudC1tZXNzYWdlIjogInJhbmRvbSBtZXNzYWdlIiwgIngtaGFzdXJhLXBheW1lbnQtY2F0ZWdvcnkiOiAiU3RhbmRBbG9uZSIsICJ4LWhhc3VyYS1wYXltZW50LXJlZmVyZW5jZSI6ICI3NzM5Y2Y0MC1kZGQ5LTQ2MjAtODM2Yi0zNzFkOGVkNWI1OWUiLCAieC1oYXN1cmEtcGF5bWVudC1leHBpcmVzLWF0IjogIjIwMjMtMDEtMjVUMTY6MjA6MDlaIn19.p2jVXSGQ8kuTLY2-LWljoSDh8HnH5uWBwKhhpmQQbqY"

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
suspend fun createNewPaymentSession(context: Context, amountToPay: String): String? {
    val expiresAt = LocalDateTime.now().plusMinutes(5)
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'"))
    Log.d("NewPaymentSession", "test")
    val response = try {
        apolloClient(context).mutation(
            NewPaymentSessionMutation(
                PaymentSessionInput(
                    amount = amountToPay.toInt() * 100,
                    currency = Optional.present(SupportedCurrencyEnum.SEK),
                    expiresAt = expiresAt,
                    idempotencyKey = UUID.randomUUID().toString(),
                    merchantReference = "reference",
                    message = "random message",
                    additionalData = Optional.absent()
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


@RequiresApi(Build.VERSION_CODES.O)
@Preview(showBackground = true)
@Composable
fun DefaultPaymentMethodsPreview() {
    paymentMethodsScreen(onNavigateToSwish = {})
}