package io.kronor.example

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.StrictMode
import android.os.StrictMode.ThreadPolicy
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.kronor.api.Environment
import io.kronor.api.PaymentConfiguration
import io.kronor.api.PaymentEvent
import io.kronor.api.PaymentMethod
import io.kronor.api.toPaymentGatewayMethod
import io.kronor.component.credit_card.CreditCardComponent
import io.kronor.component.credit_card.creditCardViewModel
import io.kronor.component.fallback.FallbackComponent
import io.kronor.component.fallback.fallbackViewModel
import io.kronor.component.mobilepay.MobilePayComponent
import io.kronor.component.mobilepay.mobilePayViewModel
import io.kronor.component.paypal.PayPalComponent
import io.kronor.component.paypal.paypalViewModel
import io.kronor.component.swish.SwishComponent
import io.kronor.component.swish.swishViewModel
import io.kronor.component.vipps.VippsComponent
import io.kronor.component.vipps.vippsViewModel
import io.kronor.example.type.Country
import io.kronor.example.type.SupportedCurrencyEnum
import io.kronor.example.ui.theme.KronorSDKTheme
import kotlinx.coroutines.*
import java.util.*


class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        StrictMode.setThreadPolicy(
            ThreadPolicy.Builder().detectDiskReads().detectDiskWrites()
                .detectAll() //for all detectable problems

                .penaltyLog().build()
        )
        StrictMode.noteSlowCall("SlowCall")
        super.onCreate(savedInstanceState)
        Log.d("onCreate", "${viewModel.paymentSessionToken}")
        setContent {
            val newIntent = produceState(initialValue = null as Intent?) {
                val consumer = androidx.core.util.Consumer<Intent> {
                    this.value = it
                    viewModel.handleIntent(it)
                }
                this.value = intent
                viewModel.handleIntent(intent)
                addOnNewIntentListener(consumer)
                awaitDispose {
                    removeOnNewIntentListener(consumer)
                }
            }
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
    KronorSDKTheme {
        NavHost(navController = navController, startDestination = "paymentMethods") {
            composable("paymentMethods") {
                PaymentMethodsScreen(viewModel, onNavigateToSwish = { sessionToken ->
                    navController.navigate("swishScreen/${sessionToken}")
                }, onNavigateToCreditCard = { sessionToken ->
                    navController.navigate("creditCardScreen/${sessionToken}")
                }, onNavigateToMobilePay = { sessionToken ->
                    navController.navigate("mobilePayScreen/${sessionToken}")
                }, onNavigateToVipps = { sessionToken ->
                    navController.navigate("vippsScreen/$sessionToken")
                }, onNavigateToPayPal = { sessionToken ->
                    navController.navigate("paypalScreen/$sessionToken")
                }, onNavigateToFallback = { pm, sessionToken ->
                    navController.navigate("fallbackScreen/$pm/$sessionToken")
                })
            }
            composable(
                "swishScreen/{sessionToken}", arguments = listOf(navArgument("sessionToken") {
                    type = NavType.StringType
                })
            ) {
                it.arguments?.getString("sessionToken")?.let { sessionToken ->
                    val svm = swishViewModel(
                        PaymentConfiguration(
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
                                    svm.events.collect { event ->
                                        when (event) {
                                            PaymentEvent.PaymentFailure -> {
                                                withContext(Dispatchers.Main) {
                                                    viewModel.resetPaymentState()
                                                    navController.navigate("paymentMethods")
                                                }
                                            }

                                            is PaymentEvent.PaymentSuccess -> {
                                                withContext(Dispatchers.Main) {
                                                    viewModel.resetPaymentState()
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
                    SwishComponent(svm)
                }
            }
            composable(
                "creditCardScreen/{sessionToken}", arguments = listOf(navArgument("sessionToken") {
                    type = NavType.StringType
                })
            ) {
                it.arguments?.getString("sessionToken")?.let { sessionToken ->
                    val ccvm = creditCardViewModel(
                        creditCardConfiguration = PaymentConfiguration(
                            sessionToken = sessionToken,
                            merchantLogo = R.drawable.kronor_logo,
                            environment = Environment.Staging,
                            appName = "kronor-android-test",
                            appVersion = "0.1.0",
                            redirectUrl = Uri.parse("kronorcheckout://io.kronor.example/"),
                            locale = Locale("en_US")
                        )
                    )

                    val lifecycle = LocalLifecycleOwner.current.lifecycle
                    LaunchedEffect(Unit) {
                        launch {
                            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                                launch {
                                    ccvm.events.collect {
                                        when (it) {
                                            PaymentEvent.PaymentFailure -> {
                                                withContext(Dispatchers.Main) {
                                                    viewModel.resetPaymentState()
                                                    navController.navigate("paymentMethods")
                                                }
                                            }

                                            is PaymentEvent.PaymentSuccess -> {
                                                withContext(Dispatchers.Main) {
                                                    viewModel.resetPaymentState()
                                                    navController.navigate("paymentMethods")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    CreditCardComponent(ccvm)
                }
            }
            composable(
                "mobilePayScreen/{sessionToken}", arguments = listOf(navArgument("sessionToken") {
                    type = NavType.StringType
                })
            ) {
                it.arguments?.getString("sessionToken")?.let { sessionToken ->
                    val mpvm = mobilePayViewModel(
                        mobilePayConfiguration = PaymentConfiguration(
                            sessionToken = sessionToken,
                            merchantLogo = R.drawable.kronor_logo,
                            environment = Environment.Staging,
                            appName = "kronor-android-test",
                            appVersion = "0.1.0",
                            redirectUrl = Uri.parse("kronorcheckout://io.kronor.example/"),
                            locale = Locale("en_US")
                        )
                    )
                    val lifecycle = LocalLifecycleOwner.current.lifecycle
                    LaunchedEffect(Unit) {
                        launch {
                            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                                launch {
                                    mpvm.events.collect {
                                        when (it) {
                                            PaymentEvent.PaymentFailure -> {
                                                withContext(Dispatchers.Main) {
                                                    viewModel.resetPaymentState()
                                                    navController.navigate("paymentMethods")
                                                }
                                            }

                                            is PaymentEvent.PaymentSuccess -> {
                                                withContext(Dispatchers.Main) {
                                                    viewModel.resetPaymentState()
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
                            Log.d("MobilePayComponent", "${it.data}")
                            mpvm.handleIntent(it)
                        }
                    }
                    MobilePayComponent(mpvm)
                }
            }
            composable(
                "vippsScreen/{sessionToken}", arguments = listOf(navArgument("sessionToken") {
                    type = NavType.StringType
                })
            ) {
                it.arguments?.getString("sessionToken")?.let { sessionToken ->
                    val vvm = vippsViewModel(
                        PaymentConfiguration(
                            sessionToken = sessionToken,
                            merchantLogo = R.drawable.kronor_logo,
                            environment = Environment.Staging,
                            appName = "kronor-android-test",
                            appVersion = "0.1.0",
                            redirectUrl = Uri.parse("kronorcheckout://io.kronor.example/"),
                            locale = Locale("en_US")
                        )
                    )

                    val lifecycle = LocalLifecycleOwner.current.lifecycle
                    LaunchedEffect(Unit) {
                        launch {
                            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                                launch {
                                    vvm.events.collect {
                                        when (it) {
                                            PaymentEvent.PaymentFailure -> {
                                                withContext(Dispatchers.Main) {
                                                    viewModel.resetPaymentState()
                                                    navController.navigate("paymentMethods")
                                                }
                                            }

                                            is PaymentEvent.PaymentSuccess -> {
                                                withContext(Dispatchers.Main) {
                                                    viewModel.resetPaymentState()
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
                            Log.d("VippsComponent", "${it.data}")
                            vvm.handleIntent(it)
                        }
                    }
                    VippsComponent(vvm)
                }
            }
            composable(
                "paypalScreen/{sessionToken}", arguments = listOf(navArgument("sessionToken") {
                    type = NavType.StringType
                })
            ) {
                it.arguments?.getString("sessionToken")?.let { sessionToken ->
                    val ppvm = paypalViewModel(
                        paypalConfiguration = PaymentConfiguration(
                            sessionToken = sessionToken,
                            merchantLogo = R.drawable.kronor_logo,
                            environment = Environment.Staging,
                            appName = "kronor-android-test",
                            appVersion = "0.1.0",
                            redirectUrl = Uri.parse("kronorcheckout://io.kronor.example/"),
                            locale = Locale("en_US")
                        )
                    )

                    val lifecycle = LocalLifecycleOwner.current.lifecycle
                    LaunchedEffect(Unit) {
                        launch {
                            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                                launch {
                                    ppvm.events.collect {
                                        when (it) {
                                            PaymentEvent.PaymentFailure -> {
                                                withContext(Dispatchers.Main) {
                                                    viewModel.resetPaymentState()
                                                    navController.navigate("paymentMethods")
                                                }
                                            }

                                            is PaymentEvent.PaymentSuccess -> {
                                                withContext(Dispatchers.Main) {
                                                    viewModel.resetPaymentState()
                                                    navController.navigate("paymentMethods")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    PayPalComponent(ppvm)
                }
            }
            composable(
                "fallbackScreen/{pm}/{sessionToken}", arguments = listOf(navArgument("pm") {
                    type = NavType.StringType
                }, navArgument("sessionToken") {
                    type = NavType.StringType
                })
            ) {
                it.arguments?.getString("pm")?.let { pm ->
                    it.arguments?.getString("sessionToken")?.let { sessionToken ->
                        val fbvm = fallbackViewModel(
                            fallbackConfiguration = PaymentConfiguration(
                                sessionToken = sessionToken,
                                merchantLogo = R.drawable.kronor_logo,
                                environment = Environment.Staging,
                                appName = "kronor-android-test",
                                appVersion = "0.1.0",
                                redirectUrl = Uri.parse("kronorcheckout://io.kronor.example/"),
                                locale = Locale("en_US")
                            ), paymentMethod = pm
                        )

                        val lifecycle = LocalLifecycleOwner.current.lifecycle
                        LaunchedEffect(Unit) {
                            launch {
                                lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                                    launch {
                                        fbvm.events.collect {
                                            when (it) {
                                                PaymentEvent.PaymentFailure -> {
                                                    withContext(Dispatchers.Main) {
                                                        viewModel.resetPaymentState()
                                                        navController.navigate("paymentMethods")
                                                    }
                                                }

                                                is PaymentEvent.PaymentSuccess -> {
                                                    withContext(Dispatchers.Main) {
                                                        viewModel.resetPaymentState()
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
                                Log.d("FallbackComponent", "${it.data}")
                                fbvm.handleIntent(it)
                            }
                        }
                        FallbackComponent(fbvm)
                    }
                }
            }
        }
    }
}


@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun PaymentMethodsScreen(
    viewModel: MainViewModel,
    onNavigateToSwish: (String) -> Unit,
    onNavigateToCreditCard: (String) -> Unit,
    onNavigateToMobilePay: (String) -> Unit,
    onNavigateToVipps: (String) -> Unit,
    onNavigateToPayPal: (String) -> Unit,
    onNavigateToFallback: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var amount by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(""))
    }
    var availableCountries: Array<Country> by remember {
        mutableStateOf(arrayOf(Country.SE))
    }
    var availableCurrencies: Array<SupportedCurrencyEnum> by remember {
        mutableStateOf(arrayOf(SupportedCurrencyEnum.SEK))
    }
    var selectedPaymentMethod: PaymentMethod by remember { mutableStateOf(PaymentMethod.Swish()) }
    var selectedCountry by remember { mutableStateOf(Country.SE) }
    var selectedCurrency by remember { mutableStateOf(SupportedCurrencyEnum.SEK) }
    var useFallbackState by remember { mutableStateOf(false) }
    var showErrorDialog by remember { mutableStateOf(false) }
    var errorMessage: String? by remember { mutableStateOf(null) }

    LaunchedEffect(viewModel.paymentMethodSelected) {
        when (viewModel.paymentMethodSelected.value) {
            is PaymentMethod.Swish -> {
                onNavigateToSwish(viewModel.paymentSessionToken!!)
            }

            PaymentMethod.CreditCard -> {
                onNavigateToCreditCard(viewModel.paymentSessionToken!!)
            }

            PaymentMethod.MobilePay -> {
                Log.d("PaymentMethodsScreen", "I am here!")
                onNavigateToMobilePay(viewModel.paymentSessionToken!!)
            }

            PaymentMethod.Vipps -> {
                onNavigateToVipps(viewModel.paymentSessionToken!!)
            }

            PaymentMethod.PayPal -> {
                onNavigateToPayPal(viewModel.paymentSessionToken!!)
            }
            is PaymentMethod.Fallback -> {
                onNavigateToFallback((viewModel.paymentMethodSelected.value as PaymentMethod.Fallback).paymentMethod, viewModel.paymentSessionToken!!)
            }
            null -> {

            }
        }
    }

    val scaffoldState = rememberScaffoldState()
    val scope = rememberCoroutineScope()

    Scaffold(
        scaffoldState = scaffoldState, modifier = modifier.fillMaxSize(), topBar = {
            TopAppBar(title = { Text("Kronor Payments Demo") })
        }, contentWindowInsets = ScaffoldDefaults.contentWindowInsets
    ) { paddingValues ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val pattern = remember { Regex("^\\d+\$") }

                val focusManager = LocalFocusManager.current
                var fieldError by remember { mutableStateOf(false) }

                OutlinedTextField(value = amount, onValueChange = {
                    if (it.text.matches(pattern)) {
                        amount = it
                        fieldError = false
                    } else {
                        fieldError = true
                    }
                    if (it.text.isEmpty()) {
                        amount = it
                    }
                }, keyboardOptions = KeyboardOptions.Default.copy(
                    keyboardType = KeyboardType.Number, imeAction = ImeAction.Done
                ), keyboardActions = KeyboardActions(onDone = {
                    focusManager.clearFocus()
                }), label = { Text("Amount") }, isError = fieldError
                )
            }

            if (showErrorDialog) {
                AlertDialog(onDismissRequest = { showErrorDialog = false }, title = {
                    Text(text = "Session error")
                }, text = {
                    Text(text = errorMessage ?: "Something went wrong. Check logs")
                }, buttons = {
                    Row(
                        modifier = Modifier.padding(all = 8.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(modifier = Modifier.fillMaxWidth(),
                            onClick = { showErrorDialog = false }) {
                            Text("Ok")
                        }
                    }
                })
            }

            PaymentMethodsDropDown(selectedPaymentMethod) { pm ->
                selectedPaymentMethod = pm
                if (!nativeImplementationExists(selectedPaymentMethod)) {
                    useFallbackState = true
                }
                setDefaultConfiguration(pm, {
                    selectedCountry = it
                }, {
                    selectedCurrency = it
                })
                setSupportedCountriesAndCurrencies(pm, {
                    availableCountries = it
                }, {
                    availableCurrencies = it
                })
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .selectableGroup(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    Modifier
                        .height(56.dp)
                        .selectable(
                            selected = !useFallbackState, onClick = {
                                if (nativeImplementationExists(selectedPaymentMethod)) {
                                    useFallbackState = false
                                } else {
                                    scope.launch {
                                        scaffoldState.snackbarHostState.showSnackbar(
                                            "Payment method ${selectedPaymentMethod.toPaymentGatewayMethod()} doesn't have a native implementation"
                                        )
                                    }
                                }
                            }, role = Role.RadioButton
                        )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = !useFallbackState,
                            onClick = { useFallbackState = false })
                        Text(
                            text = "Use Native",
                            style = MaterialTheme.typography.body1.merge(),
                            modifier = Modifier.padding(start = 16.dp)
                        )
                    }
                }
                Column(
                    Modifier
                        .height(56.dp)
                        .selectable(
                            selected = useFallbackState,
                            onClick = { useFallbackState = true },
                            role = Role.RadioButton
                        )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = useFallbackState,
                            onClick = { useFallbackState = true })
                        Text(
                            text = "Use Fallback",
                            style = MaterialTheme.typography.body1.merge(),
                            modifier = Modifier.padding(start = 16.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {

                CountriesDropDown(availableCountries, selectedCountry) {
                    selectedCountry = it
                }

                CurrenciesDropDown(availableCurrencies, selectedCurrency) {
                    selectedCurrency = it
                }
            }
            Button(onClick = {
                if (amount.text.isEmpty()) {
                    errorMessage = "Please enter a valid Amount"
                    showErrorDialog = true
                    return@Button
                }
                GlobalScope.launch {
                    withContext(Dispatchers.Main) {
                        val sessionResponse = viewModel.createNewPaymentSession(
                            amount.text, selectedCountry, selectedCurrency
                        )

                        when (sessionResponse) {
                            is KronorApiResponse.Error -> {
                                errorMessage = sessionResponse.e
                                showErrorDialog = true
                            }

                            is KronorApiResponse.Response -> {
                                val paymentMethod = if (useFallbackState) {
                                    PaymentMethod.Fallback(selectedPaymentMethod.toPaymentGatewayMethod())
                                } else {
                                    selectedPaymentMethod
                                }
                                when (paymentMethod) {
                                    is PaymentMethod.Swish -> onNavigateToSwish(sessionResponse.token)
                                    PaymentMethod.CreditCard -> onNavigateToCreditCard(
                                        sessionResponse.token
                                    )

                                    PaymentMethod.MobilePay -> onNavigateToMobilePay(sessionResponse.token)
                                    PaymentMethod.Vipps -> onNavigateToVipps(sessionResponse.token)
                                    PaymentMethod.PayPal -> onNavigateToPayPal(sessionResponse.token)
                                    is PaymentMethod.Fallback -> onNavigateToFallback(
                                        paymentMethod.paymentMethod,
                                        sessionResponse.token
                                    )
                                }
                            }
                        }
                    }
                }
            }) {
                Text("Pay with ${selectedPaymentMethod.toPaymentGatewayMethod()}")
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterialApi::class)
private fun PaymentMethodsDropDown(
    selectedPaymentMethod: PaymentMethod, setSelectedPaymentMethod: (PaymentMethod) -> (Unit)
) {
    var paymentMethodsDropDownExpanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(expanded = paymentMethodsDropDownExpanded,
        onExpandedChange = { paymentMethodsDropDownExpanded = it }) {
        TextField(
            readOnly = true,
            value = selectedPaymentMethod.toPaymentGatewayMethod(),
            onValueChange = { },
            label = { Text("Payment Methods") },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(
                    expanded = paymentMethodsDropDownExpanded
                )
            },
            colors = ExposedDropdownMenuDefaults.textFieldColors()
        )

        ExposedDropdownMenu(expanded = paymentMethodsDropDownExpanded,
            onDismissRequest = { paymentMethodsDropDownExpanded = false }) {
            arrayOf(
                PaymentMethod.Swish(),
                PaymentMethod.CreditCard,
                PaymentMethod.MobilePay,
                PaymentMethod.Vipps,
                PaymentMethod.PayPal,
                PaymentMethod.Fallback("p24")
            ).forEach {
                DropdownMenuItem(onClick = {
                    setSelectedPaymentMethod(it)
                    paymentMethodsDropDownExpanded = false
                }) {
                    Text(it.toPaymentGatewayMethod())
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterialApi::class)
private fun CurrenciesDropDown(
    availableCurrencies: Array<SupportedCurrencyEnum>,
    selectedCurrency: SupportedCurrencyEnum,
    setSelectedCurrency: (SupportedCurrencyEnum) -> (Unit)
) {
    var currenciesDropDownExpanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(modifier = Modifier.width(156.dp),
        expanded = currenciesDropDownExpanded,
        onExpandedChange = { currenciesDropDownExpanded = it }) {
        TextField(readOnly = true,
            value = selectedCurrency.toString(),
            onValueChange = { },
            label = { Text("Currency") },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(
                    expanded = currenciesDropDownExpanded
                )
            },
            colors = ExposedDropdownMenuDefaults.textFieldColors()
        )

        ExposedDropdownMenu(expanded = currenciesDropDownExpanded,
            onDismissRequest = { currenciesDropDownExpanded = false }) {
            availableCurrencies.forEach {
                DropdownMenuItem(onClick = {
                    setSelectedCurrency(it)
                    currenciesDropDownExpanded = false
                }) {
                    Text(it.toString())
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterialApi::class)
private fun CountriesDropDown(
    availableCountries: Array<Country>,
    selectedCountry: Country,
    setSelectedCountry: (Country) -> (Unit),
) {
    var countriesDropDownExpanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(modifier = Modifier.width(156.dp),
        expanded = countriesDropDownExpanded,
        onExpandedChange = { countriesDropDownExpanded = it }) {
        TextField(readOnly = true,
            value = selectedCountry.toString(),
            onValueChange = { },
            label = { Text("Country") },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(
                    expanded = countriesDropDownExpanded
                )
            },
            colors = ExposedDropdownMenuDefaults.textFieldColors()
        )

        ExposedDropdownMenu(expanded = countriesDropDownExpanded,
            onDismissRequest = { countriesDropDownExpanded = false }) {
            availableCountries.forEach {
                DropdownMenuItem(onClick = {
                    setSelectedCountry(it)
                    countriesDropDownExpanded = false
                }) {
                    Text(it.toString())
                }
            }
        }
    }
}

fun setSupportedCountriesAndCurrencies(
    paymentMethod: PaymentMethod,
    setSupportedCountries: (Array<Country>) -> (Unit),
    setSupportedCurrencies: (Array<SupportedCurrencyEnum>) -> (Unit)
) {
    return when (paymentMethod) {
        is PaymentMethod.Swish -> {
            setSupportedCountries(arrayOf(Country.SE))
            setSupportedCurrencies(arrayOf(SupportedCurrencyEnum.SEK))
        }

        PaymentMethod.MobilePay -> {
            setSupportedCountries(arrayOf(Country.DK))
            setSupportedCurrencies(arrayOf(SupportedCurrencyEnum.DKK))
        }

        PaymentMethod.Vipps -> {
            setSupportedCountries(arrayOf(Country.NO))
            setSupportedCurrencies(arrayOf(SupportedCurrencyEnum.NOK))
        }

        PaymentMethod.Fallback("p24") -> {
            setSupportedCountries(arrayOf(Country.PL))
            setSupportedCurrencies(arrayOf(SupportedCurrencyEnum.PLN))
        }

        else -> {
            setSupportedCountries(enumValues())
            setSupportedCurrencies(enumValues())
        }
    }
}

fun nativeImplementationExists(selectedPaymentMethod: PaymentMethod): Boolean {
    return when (selectedPaymentMethod) {
        is PaymentMethod.Swish -> true
        PaymentMethod.CreditCard -> true
        PaymentMethod.MobilePay -> true
        PaymentMethod.Vipps -> true
        PaymentMethod.PayPal -> true
        is PaymentMethod.Fallback -> false
    }
}

fun setDefaultConfiguration(
    paymentMethod: PaymentMethod,
    setSupportedCountry: (Country) -> Unit,
    setSupportedCurrency: (SupportedCurrencyEnum) -> Unit
) {
    return when (paymentMethod) {
        is PaymentMethod.Swish -> {
            setSupportedCountry(Country.SE)
            setSupportedCurrency(SupportedCurrencyEnum.SEK)
        }

        PaymentMethod.MobilePay -> {
            setSupportedCountry(Country.DK)
            setSupportedCurrency(SupportedCurrencyEnum.DKK)
        }

        PaymentMethod.Vipps -> {
            setSupportedCountry(Country.NO)
            setSupportedCurrency(SupportedCurrencyEnum.NOK)
        }

        PaymentMethod.Fallback("p24") -> {
            setSupportedCountry(Country.PL)
            setSupportedCurrency(SupportedCurrencyEnum.PLN)
        }

        else -> {}
    }
}


/*
@RequiresApi(Build.VERSION_CODES.O)
@Preview(showBackground = true)
@Composable
fun DefaultPaymentMethodsPreview() {
    PaymentMethodsScreen(
        onNavigateToSwish = {},
        onNavigateToCreditCard = {},
        onNavigateToMobilePay = {},
        onNavigateToVipps = {},
        onNavigateToPayPal = {}
    )
}
*/
