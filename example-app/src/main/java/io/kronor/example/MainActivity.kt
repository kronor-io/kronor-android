package io.kronor.example

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.StrictMode
import android.os.StrictMode.ThreadPolicy
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import io.kronor.api.PaymentMethod
import io.kronor.api.toPaymentGatewayMethod
import io.kronor.example.type.Country
import io.kronor.example.type.GatewayEnum
import io.kronor.example.type.SupportedCurrencyEnum
import kotlinx.coroutines.launch
import kotlin.enums.enumEntries


class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private var redirectIntent by mutableStateOf<Intent?>(null)

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        StrictMode.setThreadPolicy(
            ThreadPolicy.Builder().detectDiskReads().detectDiskWrites()
                .detectAll() //for all detectable problems

                .penaltyLog().build()
        )
        StrictMode.noteSlowCall("SlowCall")
        super.onCreate(savedInstanceState)
        redirectIntent = intent.takeIf { it.data != null }
        viewModel.handleIntent(intent)
        setContent {
            KronorTestApp(
                viewModel = viewModel,
                redirectIntent = redirectIntent,
                onRedirectHandled = { handledIntent ->
                    if (redirectIntent === handledIntent) {
                        redirectIntent = null
                    }
                },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        redirectIntent = intent.takeIf { it.data != null }
        viewModel.handleIntent(intent)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun PaymentMethodsScreen(
    viewModel: MainViewModel,
    onStartPayment: (PaymentMethod) -> Unit,
    modifier: Modifier = Modifier
) {
    var amount by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(""))
    }
    var availableCountries: Array<Country> by remember {
        mutableStateOf(arrayOf(Country.SE))
    }
    var availableGateways: Array<GatewayEnum> by remember {
        mutableStateOf(arrayOf(GatewayEnum.KRONOR))
    }
    var availableCurrencies: Array<SupportedCurrencyEnum> by remember {
        mutableStateOf(arrayOf(SupportedCurrencyEnum.SEK))
    }
    var selectedPaymentMethod: PaymentMethod by remember { mutableStateOf(PaymentMethod.Swish()) }
    var selectedGateway: GatewayEnum by remember { mutableStateOf(GatewayEnum.KRONOR) }
    var selectedCountry by remember { mutableStateOf(Country.SE) }
    var selectedCurrency by remember { mutableStateOf(SupportedCurrencyEnum.SEK) }
    var useFallbackState by remember { mutableStateOf(false) }
    var showErrorDialog by remember { mutableStateOf(false) }
    var errorMessage: String? by remember { mutableStateOf(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(title = { Text("Kronor Payments Demo") }, )
        }
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
                BasicAlertDialog(onDismissRequest = { showErrorDialog = false }) {
                    Surface(
                        modifier = Modifier
                            .wrapContentWidth()
                            .wrapContentHeight(),
                        shape = MaterialTheme.shapes.large,
                        tonalElevation = AlertDialogDefaults.TonalElevation,
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text =
                                    "Session error"
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Text(text = errorMessage ?: "Something went wrong. Check logs")
                            Row(
                                modifier = Modifier.padding(all = 8.dp),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Button(
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = { showErrorDialog = false }) {
                                    Text("Ok")
                                }
                            }
                        }
                    }
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .selectableGroup(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ){
                PaymentMethodsDropDown(selectedPaymentMethod) { pm ->
                    selectedPaymentMethod = pm
                    if (!nativeImplementationExists(selectedPaymentMethod)) {
                        useFallbackState = true
                    }
                    setDefaultConfiguration(pm, {
                        selectedCountry = it
                    }, {
                        selectedCurrency = it
                    }, {
                        selectedGateway = it
                    })
                    setSupportedCountriesAndCurrencies(pm, {
                        availableCountries = it
                    }, {
                        availableCurrencies = it
                    }, setSupportedGateways = {
                        availableGateways = it
                    })
                }

                GatewaysDropDown(availableGateways, selectedGateway) {gateway ->
                    selectedGateway = gateway
                }
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
                                        snackbarHostState.showSnackbar(
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
                            style = MaterialTheme.typography.bodyMedium.merge(),
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
                            style = MaterialTheme.typography.bodyMedium.merge(),
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

                CountriesDropDown(availableCountries, selectedCountry) { it ->
                    selectedCountry = it
                    setSupportedCurrencyGivenPaymentMethodAndCountry(selectedPaymentMethod, it) {
                        selectedCurrency = it
                    }
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
                scope.launch {
                    val sessionResponse = viewModel.createNewPaymentSession(
                        amount.text, selectedCountry, selectedCurrency, selectedGateway
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
                            onStartPayment(paymentMethod)
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
@OptIn(ExperimentalMaterial3Api::class)
private fun GatewaysDropDown(
    availableGateways: Array<GatewayEnum>,
    selectedGateway: GatewayEnum, setSelectedGateway: (GatewayEnum) -> (Unit)
) {
    var gatewaysDropDownExpanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(expanded = gatewaysDropDownExpanded,
        onExpandedChange = { gatewaysDropDownExpanded = it }) {
        TextField(
            readOnly = true,
            value = selectedGateway.toString(),
            onValueChange = { },
            label = { Text("Gateways") },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(
                    expanded = gatewaysDropDownExpanded
                )
            },
            colors = ExposedDropdownMenuDefaults.textFieldColors(),
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
        )

        ExposedDropdownMenu(expanded = gatewaysDropDownExpanded,
            onDismissRequest = { gatewaysDropDownExpanded = false }) {
            availableGateways
                .forEach {
                DropdownMenuItem(
                    onClick = {
                        setSelectedGateway(it)
                        gatewaysDropDownExpanded = false
                    },
                    text =
                        { Text(it.toString())}
                )
            }
        }
    }
}


@Composable
@OptIn(ExperimentalMaterial3Api::class)
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
            colors = ExposedDropdownMenuDefaults.textFieldColors(),
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
        )

        ExposedDropdownMenu(expanded = paymentMethodsDropDownExpanded,
            onDismissRequest = { paymentMethodsDropDownExpanded = false }) {
            arrayOf(
                PaymentMethod.Swish(),
                PaymentMethod.CreditCard,
                PaymentMethod.MobilePay,
                PaymentMethod.Vipps,
                PaymentMethod.PayPal,
                PaymentMethod.BankTransfer,
                PaymentMethod.GooglePay,
                PaymentMethod.Fallback("p24"),
            ).forEach {
                DropdownMenuItem(
                    onClick = {
                        setSelectedPaymentMethod(it)
                        paymentMethodsDropDownExpanded = false
                    },
                    text =
                        { Text(it.toPaymentGatewayMethod())}
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
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
            colors = ExposedDropdownMenuDefaults.textFieldColors(),
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
        )

        ExposedDropdownMenu(expanded = currenciesDropDownExpanded,
            onDismissRequest = { currenciesDropDownExpanded = false }) {
            availableCurrencies.forEach {
                DropdownMenuItem(
                    onClick = {
                        setSelectedCurrency(it)
                        currenciesDropDownExpanded = false
                    },
                    text = {Text(it.toString())}
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
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
            colors = ExposedDropdownMenuDefaults.textFieldColors(),
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
        )

        ExposedDropdownMenu(expanded = countriesDropDownExpanded,
            onDismissRequest = { countriesDropDownExpanded = false }) {
            availableCountries.forEach {
                DropdownMenuItem(
                    onClick = {
                        setSelectedCountry(it)
                        countriesDropDownExpanded = false
                    },
                    text = { Text(it.toString())})
            }
        }
    }
}

fun setSupportedCountriesAndCurrencies(
    paymentMethod: PaymentMethod,
    setSupportedCountries: (Array<Country>) -> (Unit),
    setSupportedCurrencies: (Array<SupportedCurrencyEnum>) -> (Unit),
    setSupportedGateways: (Array<GatewayEnum>) -> (Unit)
) {
    return when (paymentMethod) {
        is PaymentMethod.Swish -> {
            setSupportedCountries(arrayOf(Country.SE))
            setSupportedCurrencies(arrayOf(SupportedCurrencyEnum.SEK))
            setSupportedGateways(arrayOf(GatewayEnum.KRONOR))
        }

        PaymentMethod.MobilePay -> {
            setSupportedCountries(arrayOf(Country.DK))
            setSupportedCurrencies(arrayOf(SupportedCurrencyEnum.DKK))
            setSupportedGateways(arrayOf(GatewayEnum.KRONOR, GatewayEnum.REEPAY))
        }

        PaymentMethod.Vipps -> {
            setSupportedCountries(arrayOf(Country.NO))
            setSupportedCurrencies(arrayOf(SupportedCurrencyEnum.NOK))
            setSupportedGateways(arrayOf(GatewayEnum.KRONOR, GatewayEnum.REEPAY))
        }

        PaymentMethod.BankTransfer -> {
            setSupportedCountries(arrayOf(Country.SE, Country.DE, Country.FI, Country.LT, Country.EE, Country.NL))
            setSupportedCurrencies(arrayOf(SupportedCurrencyEnum.SEK, SupportedCurrencyEnum.EUR))
            setSupportedGateways(arrayOf(GatewayEnum.TRUSTLY))
        }

        PaymentMethod.CreditCard -> {
            setSupportedCountries(arrayOf(Country.SE, Country.DE, Country.DK, Country.CH, Country.FO))
            setSupportedCurrencies(arrayOf(SupportedCurrencyEnum.SEK, SupportedCurrencyEnum.DKK, SupportedCurrencyEnum.CHF, SupportedCurrencyEnum.EUR))
            setSupportedGateways(arrayOf(GatewayEnum.KRONOR, GatewayEnum.REEPAY))
        }

        PaymentMethod.GooglePay -> {
            setSupportedCountries(arrayOf(Country.SE, Country.DE, Country.DK, Country.CH, Country.FO))
            setSupportedCurrencies(arrayOf(SupportedCurrencyEnum.SEK, SupportedCurrencyEnum.DKK, SupportedCurrencyEnum.CHF, SupportedCurrencyEnum.EUR))
            setSupportedGateways(arrayOf(GatewayEnum.KRONOR, GatewayEnum.REEPAY))
        }

        PaymentMethod.Fallback("p24") -> {
            setSupportedCountries(arrayOf(Country.PL))
            setSupportedCurrencies(arrayOf(SupportedCurrencyEnum.PLN))
            setSupportedGateways(GatewayEnum.entries.toTypedArray())
        }

        else -> {
            setSupportedCountries(enumEntries<Country>().toTypedArray())
            setSupportedCurrencies(enumEntries<SupportedCurrencyEnum>().toTypedArray())
            setSupportedGateways(enumEntries<GatewayEnum>().toTypedArray())
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
        PaymentMethod.BankTransfer -> true
        PaymentMethod.GooglePay -> true
        is PaymentMethod.Fallback -> false
    }
}

fun setDefaultConfiguration(
    paymentMethod: PaymentMethod,
    setSupportedCountry: (Country) -> Unit,
    setSupportedCurrency: (SupportedCurrencyEnum) -> Unit,
    setDefaultGateway: (GatewayEnum) -> Unit
) {
    return when (paymentMethod) {
        is PaymentMethod.Swish -> {
            setSupportedCountry(Country.SE)
            setSupportedCurrency(SupportedCurrencyEnum.SEK)
            setDefaultGateway(GatewayEnum.KRONOR)
        }

        PaymentMethod.CreditCard -> {
            setSupportedCountry(Country.SE)
            setSupportedCurrency(SupportedCurrencyEnum.SEK)
            setDefaultGateway(GatewayEnum.KRONOR)
        }

        PaymentMethod.MobilePay -> {
            setSupportedCountry(Country.DK)
            setSupportedCurrency(SupportedCurrencyEnum.DKK)
            setDefaultGateway(GatewayEnum.KRONOR)
        }

        PaymentMethod.Vipps -> {
            setSupportedCountry(Country.NO)
            setSupportedCurrency(SupportedCurrencyEnum.NOK)
            setDefaultGateway(GatewayEnum.KRONOR)
        }

        PaymentMethod.BankTransfer -> {
            setSupportedCountry(Country.SE)
            setSupportedCurrency(SupportedCurrencyEnum.SEK)
            setDefaultGateway(GatewayEnum.TRUSTLY)
        }

        PaymentMethod.GooglePay -> {
            setSupportedCountry(Country.SE)
            setSupportedCurrency(SupportedCurrencyEnum.SEK)
            setDefaultGateway(GatewayEnum.KRONOR)
        }

        PaymentMethod.Fallback("p24") -> {
            setSupportedCountry(Country.PL)
            setSupportedCurrency(SupportedCurrencyEnum.PLN)
        }

        PaymentMethod.Fallback("bankTransfer") -> {
            setSupportedCountry(Country.SE)
            setSupportedCurrency(SupportedCurrencyEnum.SEK)
        }

        else -> {}
    }
}

fun setSupportedCurrencyGivenPaymentMethodAndCountry(
    paymentMethod: PaymentMethod,
    country: Country,
    setSupportedCurrency: (SupportedCurrencyEnum) -> Unit
) {
    return when (paymentMethod) {
        PaymentMethod.CreditCard -> {
            when (country) {
                Country.FO -> {
                    setSupportedCurrency(SupportedCurrencyEnum.DKK)
                }
                else -> {}
            }
        }
        else -> {}
    }
}
