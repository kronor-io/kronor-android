package io.kronor.example

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.kronor.api.Environment
import io.kronor.component.credit_card.CreditCardComponent
import io.kronor.component.credit_card.CreditCardComponent
import io.kronor.component.credit_card.CreditCardConfiguration
import io.kronor.component.credit_card.creditCardViewModel
import io.kronor.component.mobilepay.MobilePayComponent
import io.kronor.component.credit_card.creditCardViewModel
import io.kronor.component.mobilepay.MobilePayComponent
import io.kronor.component.mobilepay.MobilePayConfiguration
import io.kronor.component.mobilepay.mobilePayViewModel
import io.kronor.component.mobilepay.mobilePayViewModel
import io.kronor.component.swish.GetSwishComponent
import io.kronor.component.swish.SwishConfiguration
import io.kronor.component.swish.SwishEvent
import io.kronor.component.swish.swishViewModel
import io.kronor.component.vipps.VippsComponent
import io.kronor.component.vipps.VippsComponent
import io.kronor.component.vipps.VippsConfiguration
import io.kronor.component.vipps.vippsViewModel
import io.kronor.component.webview_payment_gateway.CreditCardEvent
import io.kronor.example.type.SupportedCurrencyEnum
import io.kronor.example.ui.theme.KronorSDKTheme
import kotlinx.coroutines.*
import java.util.*

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val viewModel: MainViewModel by viewModels()

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("onCreate", "${viewModel.paymentSessionToken}")
        Log.d("onCreate", "${viewModel.paymentSessionToken}")
        setContent {
            val newIntent = produceState(initialValue = null as Intent?) {
                val consumer = androidx.core.util.Consumer<Intent> {
                    this.value = it
                }
                this.value = intent
                addOnNewIntentListener(consumer)
                awaitDispose {
                    removeOnNewIntentListener(consumer)
                }
            }
//            Log.d("onCreate", "${newIntent?.data}")
            KronorTestApp(viewModel, newIntent)
        }
    }

    override fun onNewIntent(intent: Intent?) {
        intent?.let(viewModel::handleIntent)
        super.onNewIntent(intent)
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun KronorTestApp(viewModel: MainViewModel, newIntent: State<Intent?>) {
    val navController = rememberNavController()
fun KronorTestApp(viewModel: MainViewModel, newIntent: State<Intent?>) {
    val navController = rememberNavController()
    KronorSDKTheme {
        NavHost(navController = navController, startDestination = "paymentMethods") {
            composable("paymentMethods") {
                PaymentMethodsScreen(viewModel, onNavigateToSwish = { sessionToken ->
                PaymentMethodsScreen(viewModel, onNavigateToSwish = { sessionToken ->
                    navController.navigate("swishScreen/${sessionToken}")
                }, onNavigateToCreditCard = { sessionToken ->
                    navController.navigate("creditCardScreen/${sessionToken}")
                }, onNavigateToMobilePay = { sessionToken ->
                    navController.navigate("mobilePayScreen/${sessionToken}")
                }, onNavigateToVipps = { sessionToken ->
                    navController.navigate("vippsScreen/$sessionToken")
                })
            }
            composable(
                "swishScreen/{sessionToken}",
                arguments = listOf(navArgument("sessionToken") { type = NavType.StringType })
            ) {
                it.arguments?.getString("sessionToken")?.let { sessionToken ->
                    val svm = swishViewModel(
                        SwishConfiguration(
                            sessionToken = sessionToken,
                            merchantLogo = R.drawable.kronor_logo,
                            environment = Environment.Staging,
                            appName = "kronor-android-test",
                            appVersion = "0.1.0",
                            locale = Locale("en_US"),
                            redirectUrl = Uri.parse("kronorcheckout://io.kronor.example/"),
                        )
                    )
                    val lifecycle = LocalLifecycleOwner.current.lifecycle
                    LaunchedEffect(Unit) {
                        launch {
                            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                                launch {
                                    svm.events.collect {event ->
                                        when (event) {
                                            SwishEvent.PaymentFailure -> {
                                                withContext(Dispatchers.Main) {
                                                    navController.navigate("paymentMethods")
                                                }
                                            }

                                            is SwishEvent.PaymentSuccess -> {
                                                withContext(Dispatchers.Main) {
                                                    navController.navigate("paymentMethods")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    LaunchedEffect(newIntent.value?.data) {
                        newIntent.value?.let {
                            Log.d("SwishComponent", "${it.data}")
                            svm.handleIntent(it)
                        }
                    }
                    GetSwishComponent(svm, R.drawable.kronor_logo)
                }
            }
            composable(
                "creditCardScreen/{sessionToken}",
                arguments = listOf(navArgument("sessionToken") { type = NavType.StringType })
            ) {
                it.arguments?.getString("sessionToken")?.let { sessionToken ->
                    val ccvm =
                        creditCardViewModel(
                            creditCardConfiguration = CreditCardConfiguration(
                                sessionToken = sessionToken,
                                merchantLogo = R.drawable.kronor_logo,
                                environment = Environment.Staging,
                                appName = "kronor-android-test",
                                appVersion = "0.1.0",
                                redirectUrl = Uri.parse("kronor-test://?")
                            )
                        )
                    CreditCardComponent(ccvm)
                }
            }
            composable(
                "mobilePayScreen/{sessionToken}",
                arguments = listOf(navArgument("sessionToken") { type = NavType.StringType })
            ) {
                it.arguments?.getString("sessionToken")?.let { sessionToken ->
                    val mpvm = mobilePayViewModel(
                        mobilePayConfiguration = MobilePayConfiguration(
                            sessionToken = sessionToken,
                            merchantLogo = R.drawable.kronor_logo,
                            environment = Environment.Staging,
                            appName = "kronor-android-test",
                            appVersion = "0.1.0",
                            redirectUrl = Uri.parse("kronorcheckout://io.kronor.example/mobilepay")
                        )
                    )
                    val lifecycle = LocalLifecycleOwner.current.lifecycle
                    LaunchedEffect(Unit) {
                        launch {
                            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                                launch {
                                    mpvm.events.collect {
                                        when(it) {
                                            CreditCardEvent.PaymentFailure -> {
                                                withContext(Dispatchers.Main) {
                                                    navController.navigate("paymentMethods")
                                                }
                                            }
                                            is CreditCardEvent.PaymentSuccess -> {
                                                withContext(Dispatchers.Main) {
                                                    navController.navigate("paymentMethods")
                                                }
                                            }
                                            CreditCardEvent.PaymentWaiting -> {}
                                        }
                                    }
                                }
                            }
                        }
                    }
                    LaunchedEffect(newIntent.value?.data) {
                        newIntent.value?.let {
                            Log.d("MobilePayComponent", "${it.data}")
                            mpvm.handleIntent(it)
                        }
                    }
                    MobilePayComponent(mpvm)
                }
            }
            composable(
                "vippsScreen/{sessionToken}",
                arguments = listOf(navArgument("sessionToken") { type = NavType.StringType })
            ) {
                it.arguments?.getString("sessionToken")?.let { sessionToken ->
                    val vvm = vippsViewModel(
                        VippsConfiguration(
                            sessionToken = sessionToken,
                            merchantLogo = R.drawable.kronor_logo,
                            environment = Environment.Staging,
                            appName = "kronor-android-test",
                            appVersion = "0.1.0",
                            redirectUrl = Uri.parse("kronorcheckout://io.kronor.example/vipps")
                        )
                    )
                    VippsComponent(vvm)
                }
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun PaymentMethodsScreen(
    viewModel: MainViewModel,
    viewModel: MainViewModel,
    onNavigateToSwish: (String) -> Unit,
    onNavigateToCreditCard: (String) -> Unit,
    onNavigateToMobilePay: (String) -> Unit,
    onNavigateToVipps: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var amount by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(""))
    }

    LaunchedEffect(viewModel.paymentMethodSelected) {
        if (viewModel.paymentMethodSelected.value == "swish") {
            onNavigateToSwish(viewModel.paymentSessionToken!!)
        } else if (viewModel.paymentMethodSelected.value == "creditcard") {
            onNavigateToCreditCard(viewModel.paymentSessionToken!!)
        } else if (viewModel.paymentMethodSelected.value == "mobilepay") {
            onNavigateToMobilePay(viewModel.paymentSessionToken!!)
        } else if (viewModel.paymentMethodSelected.value == "vipps") {
            onNavigateToVipps(viewModel.paymentSessionToken!!)
        }
    }

    Surface(
        modifier = modifier, color = MaterialTheme.colors.background
        modifier = modifier, color = MaterialTheme.colors.background
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
                        val sessionToken = viewModel.createNewPaymentSession(
                            amount.text,
                            SupportedCurrencyEnum.SEK
                        )

                        sessionToken?.let {
                            onNavigateToSwish(it)
                        }
                    }
                }
            }) {
                Text("Pay ${amount.text} with Swish")
            }
            Button(onClick = {
                GlobalScope.launch {
                    withContext(Dispatchers.Main) {
                        val sessionToken = viewModel.createNewPaymentSession(
                            amount.text,
                            SupportedCurrencyEnum.SEK
                        )

                        sessionToken?.let {
                            onNavigateToCreditCard(it)
                        }
                    }
                }
            }) {
                Text("Pay ${amount.text} with a Credit Card")
            }
            Button(onClick = {
                GlobalScope.launch {
                    withContext(Dispatchers.Main) {
                        val sessionToken = viewModel.createNewPaymentSession(
                            amount.text,
                            SupportedCurrencyEnum.DKK
                        )

                        sessionToken?.let {
                            onNavigateToMobilePay(it)
                        }
                    }
                }
            }) {
                Text("Pay ${amount.text} with MobilePay")
            }
            Button(onClick = {
                GlobalScope.launch {
                    withContext(Dispatchers.Main) {
                        val sessionToken = viewModel.createNewPaymentSession(
                            amount.text,
                            SupportedCurrencyEnum.NOK
                        )

                        sessionToken?.let {
                            onNavigateToVipps(it)
                        }
                    }
                }
            }) {
                Text("Pay ${amount.text} with Vipps")
            }
        }
    }
}


//
//@RequiresApi(Build.VERSION_CODES.O)
//@Preview(showBackground = true)
//@Composable
//fun DefaultPaymentMethodsPreview() {
//    paymentMethodsScreen(onNavigateToSwish = {},
//        onNavigateToCreditCard = {},
//        onNavigateToMobilePay = {})
//}
