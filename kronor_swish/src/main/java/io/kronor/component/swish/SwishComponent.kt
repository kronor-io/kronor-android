package io.kronor.component.swish

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager.MATCH_DEFAULT_ONLY
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat.startActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fingerprintjs.android.fingerprint.Fingerprinter
import com.fingerprintjs.android.fingerprint.FingerprinterFactory
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import io.kronor.api.PaymentStatusSubscription

@Composable
fun swishViewModel(swishConfiguration: SwishConfiguration): SwishViewModel {
    return viewModel(factory = SwishViewModelFactory(swishConfiguration))
}

@Composable
fun GetSwishComponent(
    context: Context,
    swishConfiguration: SwishConfiguration,
    viewModel: SwishViewModel = swishViewModel(swishConfiguration)
) {

    if (!LocalInspectionMode.current) {
        LaunchedEffect(Unit) {
            val fingerprinterFactory = FingerprinterFactory.create(context)
            fingerprinterFactory.getFingerprint(version = Fingerprinter.Version.V_5, listener = {
                viewModel.deviceFingerprint = it
            })
        }
    }

    SwishScreen(
        merchantLogo = swishConfiguration.merchantLogo, viewModel = viewModel
    )
}


@Composable
fun SwishScreen(
    @DrawableRes merchantLogo: Int? = null, viewModel: SwishViewModel
) {
    val state = viewModel.swishState
    val paymentRequest: PaymentStatusSubscription.PaymentRequest? = viewModel.paymentRequest

    LaunchedEffect(Unit) {
        viewModel.transition(SwishStatechart.Companion.Event.SubscribeToPaymentStatus)
    }

    SwishWrapper(merchantLogo) {
        when (state) {
            SwishStatechart.Companion.State.WaitingForSubscription -> {
                SwishInitializing()
            }
            SwishStatechart.Companion.State.PromptingMethod -> {
                SwishPromptMethods(onAppOpen = { viewModel.transition(SwishStatechart.Companion.Event.UseSwishApp) },
                    onQrCode = { viewModel.transition(SwishStatechart.Companion.Event.UseQR) },
                    onPhoneNumber = { viewModel.transition(SwishStatechart.Companion.Event.UsePhoneNumber) })
            }
            SwishStatechart.Companion.State.InsertingPhoneNumber -> {
                SwishPromptPhoneNumber(onPayNow = { phoneNumber ->
                    viewModel.transition(
                        SwishStatechart.Companion.Event.PhoneNumberInserted(
                            phoneNumber
                        )
                    )
                })
            }
            is SwishStatechart.Companion.State.CreatingPaymentRequest -> SwishCreatingPaymentRequest()
            is SwishStatechart.Companion.State.WaitingForPaymentRequest -> SwishCreatingPaymentRequest()
            is SwishStatechart.Companion.State.PaymentRequestInitialized -> {
                when (state.selected) {
                    SelectedMethod.QrCode -> {
                        val qrToken = paymentRequest?.transactionSwishDetails?.first()?.qrCode
                        SwishPaymentWithQrCode(qrToken, onCancelPayment = {
                            viewModel.transition(SwishStatechart.Companion.Event.CancelFlow)
                        })
                    }
                    SelectedMethod.SwishApp -> {
                        val returnUrl = paymentRequest?.transactionSwishDetails?.first()?.returnUrl
                        OpenSwishApp(context = LocalContext.current, returnUrl = returnUrl)
                    }
                    SelectedMethod.PhoneNumber -> {
                        SwishPaymentWithPhoneNumber(onCancelPayment = {
                            viewModel.transition(SwishStatechart.Companion.Event.CancelFlow)
                        })
                    }
                }
            }
            is SwishStatechart.Companion.State.PaymentCompleted -> {
                SwishPaymentCompleted()
            }
            SwishStatechart.Companion.State.PaymentRejected -> {
                SwishPaymentRejected(onPaymentRetry = { viewModel.transition(SwishStatechart.Companion.Event.Retry) },
                    onGoBack = { viewModel.transition(SwishStatechart.Companion.Event.CancelFlow) })
            }
            is SwishStatechart.Companion.State.Errored -> {
                SwishPaymentErrored(state.error, onPaymentRetry = {
                    viewModel.transition(SwishStatechart.Companion.Event.Retry)
                }, onGoBack = {
                    viewModel.transition(SwishStatechart.Companion.Event.CancelFlow)
                })
            }
            else -> {
                Log.d("SwishComponent", "Rendering $state")
                Text("Implementing $state")
            }
        }
    }
}

@Composable
fun SwishInitializing() {
    Column(
        modifier = Modifier.fillMaxHeight(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) { Text("Initializing...") }
}

@Composable
fun SwishWrapper(@DrawableRes merchantLogo: Int? = null, content: @Composable () -> Unit) {
    // A surface container using the 'background' color from the theme
    Surface(
        modifier = Modifier.fillMaxSize(), color = MaterialTheme.colors.background,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(30.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.height(IntrinsicSize.Min),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SwishLogo()
                merchantLogo?.let {
                    Divider(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(1.dp)
                    )
                    MerchantLogo(it)
                }
            }
            Spacer(modifier = Modifier.height(100.dp))
            content.invoke()
        }
    }
}

@Composable
fun SwishPromptPhoneNumber(onPayNow: (String) -> Unit) {
    var phoneNumber by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(""))
    }
    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxHeight()
    ) {
        TextField(value = phoneNumber, onValueChange = { phoneNumber = it }, placeholder = {
            Text("Enter your Swish phone number")
        }, keyboardOptions = KeyboardOptions.Default.copy(
            keyboardType = KeyboardType.Phone
        )
        )
        Spacer(modifier = Modifier.height(50.dp))
        Button(onClick = {
            onPayNow(phoneNumber.text)
        }) {
            Text("Pay Now")
        }
    }
}

@Composable
fun SwishPaymentCompleted() {
    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxHeight()
    ) {
        Text("Payment received")
    }
}

@Composable
fun SwishPaymentRejected(onPaymentRetry: () -> Unit, onGoBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxHeight(),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(100.dp))
        Text("Your payment got rejected")
        Button(onClick = {
            onPaymentRetry()
        }) {
            Text("Try again")
        }

        Button(onClick = {
            onGoBack()
        }) {
            Text("Go Back")
        }
    }
}

@Composable
fun SwishPaymentErrored(error: String, onPaymentRetry: () -> Unit, onGoBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxHeight(),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(100.dp))
        Text("Received an error: $error")
        Button(onClick = {
            onPaymentRetry()
        }) {
            Text("Try again")
        }

        Button(onClick = {
            onGoBack()
        }) {
            Text("Go Back")
        }
    }
}

@Composable
fun SwishPaymentWithQrCode(qrToken: String?, onCancelPayment: () -> Unit) {
    Spacer(modifier = Modifier.height(100.dp))
    Column(
        modifier = Modifier.fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (qrToken != null) {
                SwishQrCode(qrToken)
                Text(stringResource(R.string.scan_qr))
            } else {
                Text(stringResource(R.string.generate_qr))
            }
        }
        Button(onClick = { onCancelPayment() }) {
            Text("Cancel Payment")
        }
    }
}

@Composable
fun SwishPaymentWithPhoneNumber(onCancelPayment: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxHeight(),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.accept_swish_phone), textAlign = TextAlign.Center
        )
        Button(onClick = { onCancelPayment() }) {
            Text("Cancel Payment")
        }
    }
}

@Composable
fun SwishCreatingPaymentRequest() {
    Column(
        modifier = Modifier.fillMaxHeight(), verticalArrangement = Arrangement.Center
    ) {
        Text(stringResource(R.string.creating_swish))
    }
}

fun swishAppExists(context: Context): Boolean {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("swish://"))
    val appIntentMatch = context.packageManager.queryIntentActivities(
        intent, MATCH_DEFAULT_ONLY
    )
    return appIntentMatch.any { resolveInfo ->
        resolveInfo.activityInfo.packageName == "se.bankgirot.swish.sandbox" || resolveInfo.activityInfo.packageName == "se.bankgirot.swish"
    }
}

@Composable
fun SwishPromptMethods(onAppOpen: () -> Unit, onQrCode: () -> Unit, onPhoneNumber: () -> Unit) {
    val doesSwishAppExist: Boolean = if (LocalInspectionMode.current) {
        true
    } else {
        swishAppExists(LocalContext.current)
    }

    if (doesSwishAppExist) {
        Button(onClick = {
            onAppOpen()
        }) {
            Text("Open Swish App")
        }
    }

    Text(stringResource(R.string.pay_another_phone))
    Button(onClick = {
        onQrCode()
    }) {
        Text(stringResource(R.string.scan_qr_code))
    }
    Button(onClick = {
        onPhoneNumber()
    }) {
        Text(stringResource(R.string.enter_phone))
    }
}

@Composable
fun OpenSwishApp(context: Context, returnUrl: String?) {
    val swishUrl = Uri.parse(returnUrl)
    val intent = Intent(Intent.ACTION_VIEW, swishUrl)
    val appIntentMatch = context.packageManager.queryIntentActivities(intent, MATCH_DEFAULT_ONLY)
    Text("$appIntentMatch")
    val doesSwishAppExist = appIntentMatch.any { resolveInfo ->
        Log.d("AppCheck", "${resolveInfo.activityInfo}")
        resolveInfo.activityInfo.packageName == "se.bankgirot.swish.sandbox" || resolveInfo.activityInfo.packageName == "se.bankgirot.swish"
    }

    if (doesSwishAppExist) {
        startActivity(context, intent, null)
    } else {
        Text("No Swish App Found")
        Log.d("SwishApp", "No Swish app")
    }
}

@Composable
fun SwishQrCode(qrToken: String) {
    val size = 512
    val qrBits = QRCodeWriter().encode(qrToken, BarcodeFormat.QR_CODE, size, size)
    val qrBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565).also {
        for (x in 0 until size) {
            for (y in 0 until size) {
                it.setPixel(x, y, if (qrBits[x, y]) Color.BLACK else Color.WHITE)
            }
        }
    }
    Image(bitmap = qrBitmap.asImageBitmap(), contentDescription = "Qr code")
}

@Composable
fun SwishLogo() {
    Image(
        painter = painterResource(id = R.drawable.swish_light),
        contentDescription = "Swish logo",
        modifier = Modifier
            .padding(end = 10.dp)
            .height(100.dp)
            .width(100.dp),
        contentScale = ContentScale.Inside
    )
}

@Composable
fun MerchantLogo(@DrawableRes merchantDrawable: Int) {
    Image(
        painter = painterResource(id = merchantDrawable),
        contentDescription = "Merchant logo",
        modifier = Modifier
            .padding(start = 10.dp)
            .height(128.dp)
            .width(128.dp),
        contentScale = ContentScale.Inside
    )
}


@Preview
@Composable
fun PreviewPromptMethods() {
    SwishWrapper {
        SwishPromptMethods(onAppOpen = { }, onQrCode = { }, onPhoneNumber = { })
    }
}

@Preview
@Composable
fun PreviewInsertingPhoneNumber() {
    SwishWrapper {
        SwishPromptPhoneNumber(onPayNow = {})
    }
}

@Preview
@Composable
fun PreviewCreatingPaymentRequest() {
    SwishWrapper {
        SwishCreatingPaymentRequest()
    }
}

@Preview
@Composable
fun PreviewQrCodeScreen() {
    SwishWrapper {
        SwishPaymentWithQrCode("DpXc79mMZT7CJCMiLoLYzQ6FjHV46bk6p", onCancelPayment = {})
    }
}

@Preview
@Composable
fun PreviewPhonePaymentScreen() {
    SwishWrapper {
        SwishPaymentWithPhoneNumber(onCancelPayment = {})
    }
}

@Preview
@Composable
fun PreviewSwishPaymentCompleted() {
    SwishWrapper {
        SwishPaymentCompleted()
    }
}

@Preview
@Composable
fun PreviewSwishPaymentRejected() {
    SwishWrapper {
        SwishPaymentRejected({}) {}
    }
}
