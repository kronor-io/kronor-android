package io.kronor.component.swish

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat.startActivity
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import io.kronor.api.Environment
import io.kronor.api.PaymentStatusSubscription
import io.kronor.api.Requests
import kotlinx.coroutines.launch

@Composable
fun MainSwishScreen(
    sessionToken: String,
    @DrawableRes merchantLogo: Int? = null,
) {
    val swishStateMachine = remember {
        SwishStateMachine(sessionToken)
    }
    val state = swishStateMachine.swishState

    SwishScreen(
        merchantLogo = merchantLogo, state = state, transitioner = { event ->
            swishStateMachine.transition(event)
        }, paymentRequest = swishStateMachine.paymentRequest
    )
}

@Composable
fun SwishScreen(
    @DrawableRes merchantLogo: Int? = null,
    state: SwishStatechart.Companion.State,
    transitioner: suspend (SwishStatechart.Companion.Event) -> Unit = {},
    paymentRequest: PaymentStatusSubscription.PaymentRequest? = null
) {
/*
    val context = LocalContext.current
    var fingerprint by remember {
        mutableStateOf("")
    }

    if (LocalInspectionMode.current) {
        fingerprint = "preview fingerprint"
        waitToken = "preview waitToken"
    } else {
        val fingerprinterFactory = FingerprinterFactory.create(context)
        fingerprinterFactory.getFingerprint(
            version = Fingerprinter.Version.V_5,
            listener = {
                fingerprint = it
            }
        )
    }
*/

    val composableScope = rememberCoroutineScope()

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
            when (state) {
                SwishStatechart.Companion.State.PromptingMethod -> {
                    Spacer(modifier = Modifier.height(100.dp))
                    SwishPromptMethods(onAppOpen = {
                        composableScope.launch {
                            transitioner(SwishStatechart.Companion.Event.UseSwishApp)
                        }
                    }, onQrCode = {
                        composableScope.launch {
                            transitioner(SwishStatechart.Companion.Event.UseQR)

                        }
                    }, onPhoneNumber = {
                        composableScope.launch {
                            transitioner(SwishStatechart.Companion.Event.UsePhoneNumber)
                        }
                    })
                }
                SwishStatechart.Companion.State.InsertingPhoneNumber -> {
                    SwishPaymentWithPhoneNumber(onPayNow = { phoneNumber ->
                        composableScope.launch {
                            transitioner(
                                SwishStatechart.Companion.Event.PhoneNumberInserted(
                                    phoneNumber
                                )
                            )
                        }
                    })
                }
                is SwishStatechart.Companion.State.CreatingPaymentRequest -> SwishCreatingPaymentRequest()
                is SwishStatechart.Companion.State.WaitingForPaymentRequest -> SwishCreatingPaymentRequest()
                is SwishStatechart.Companion.State.PaymentRequestInitialized -> {
                    when (state.selected) {
                        SelectedMethod.QrCode -> {
                            val qrToken = paymentRequest?.transactionSwishDetails?.first()?.qrCode
                            Spacer(modifier = Modifier.height(100.dp))
                            SwishPaymentWithQrCode(qrToken)
                        }
                        SelectedMethod.SwishApp -> {
                            val returnUrl = paymentRequest?.transactionSwishDetails?.first()?.returnUrl
                            OpenSwishApp(context = LocalContext.current, returnUrl = returnUrl)
                        }
                        SelectedMethod.PhoneNumber -> {
                            Text("Open Swish App on your phone and accept the payment request")
                        }
                    }
                }
                SwishStatechart.Companion.State.PaymentCompleted -> {
                    SwishPaymentCompleted()
                }
                else -> Text("Implementing $state")
            }
        }
    }
}

@Composable
fun SwishPaymentWithPhoneNumber(onPayNow: (String) -> Unit) {
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
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Payment received")
    }
}

@Composable
fun SwishPaymentWithQrCode(qrToken: String?) {
    Column(
        modifier = Modifier.fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (qrToken != null) {
            SwishQrCode(qrToken)
            Text("Open Swish App and Scan qr code")
        } else {
            Text("Brewing a QR code")
        }

    }
}

@Composable
fun SwishCreatingPaymentRequest() {
    Column(
        modifier = Modifier.fillMaxHeight(), verticalArrangement = Arrangement.Center
    ) {
        Text("Creating secure Swish Transaction")
    }
}

@Composable
fun SwishPromptMethods(onAppOpen: () -> Unit, onQrCode: () -> Unit, onPhoneNumber: () -> Unit) {
    Button(onClick = {
        onAppOpen()
    }) {
        Text("Open Swish App")
    }
    Text("or pay using another phone")
    Button(onClick = {
        onQrCode()
    }) {
        Text("Scan QR Code")
    }
    Button(onClick = {
        onPhoneNumber()
    }) {
        Text("Pay using phone number")
    }
}

@Composable
fun OpenSwishApp(context: Context, returnUrl: String?) {
    val swishUrl = Uri.parse(returnUrl)
    val intent = Intent(Intent.ACTION_VIEW, swishUrl)
    val appIntentMatch = context.packageManager.queryIntentActivities(intent, MATCH_DEFAULT_ONLY)
    val doesSwishAppExist = appIntentMatch.any{
            resolveInfo ->
        Log.d("AppCheck", "${resolveInfo.activityInfo}")
        resolveInfo.activityInfo.packageName == "se.bankgirot.swish"
    }

    if (doesSwishAppExist) {
       startActivity(context, intent, null)
    } else {
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
    SwishScreen(
        state = SwishStatechart.Companion.State.PromptingMethod,
    )
}

@Preview
@Composable
fun PreviewCreatingPaymentRequest() {
    SwishScreen(
        state = SwishStatechart.Companion.State.CreatingPaymentRequest(SelectedMethod.QrCode),
    )
}

@Preview
@Composable
fun PreviewQrCodeScreen() {
    SwishScreen(
        state = SwishStatechart.Companion.State.PaymentRequestInitialized(SelectedMethod.QrCode),
    )
}

@Preview
@Composable
fun PreviewInsertingPhoneNumber() {
    SwishScreen(
        state = SwishStatechart.Companion.State.InsertingPhoneNumber,
    )
}
