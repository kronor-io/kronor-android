package io.kronor.component.swish

import android.graphics.Bitmap
import android.graphics.Color
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
        merchantLogo = merchantLogo,
        state = state,
        transitioner = { event ->
            swishStateMachine.transition(event)
        }
    )
}

@Composable
fun SwishScreen(
    @DrawableRes merchantLogo: Int? = null,
    state: SwishStatechart.Companion.State,
    transitioner: suspend (SwishStatechart.Companion.Event) -> Unit = {},
) {
/*
    val context = LocalContext.current
    var fingerprint by remember {
        mutableStateOf("")
    }

    var waitToken: String? by remember {
        mutableStateOf(null)
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

    // A surface container using the 'background' color from the theme
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colors.background
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
                    SwishPromptMethods(onQrCode = {
                        transitioner(SwishStatechart.Companion.Event.UseQR)
                    })
                }
                is SwishStatechart.Companion.State.CreatingPaymentRequest ->
                    SwishCreatingPaymentRequest()
                is SwishStatechart.Companion.State.WaitingForPaymentRequest ->
                    SwishCreatingPaymentRequest()
                is SwishStatechart.Companion.State.PaymentRequestInitialized -> {
                    if (state.selected == SelectedMethod.QrCode) {
                        val qrToken = "DpXc79mMZT7CJCMiLoLYzQ6FjHV46bk6p"
                        Spacer(modifier = Modifier.height(100.dp))
                        SwishPaymentWithQrCode(qrToken)
                    } else {
                        Text("Cannot create payment for methods other than qr code")
                    }
                }
                SwishStatechart.Companion.State.PaymentCompleted -> {
                    SwishPaymentCompleted()
                }
                else -> Text("Implementing")
            }
        }
    }
}

@Composable
fun SwishPaymentCompleted() {
    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Payment received")
    }
}

@Composable
fun SwishPaymentWithQrCode(qrToken: String) {
    Column(
        modifier = Modifier.fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SwishQrCode(qrToken)
        Text("Open Swish App and Scan qr code")
    }
}

@Composable
fun SwishCreatingPaymentRequest() {
    Column(
        modifier = Modifier.fillMaxHeight(),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Creating secure Swish Transaction")
    }
}

@Composable
fun SwishPromptMethods(onQrCode: suspend () -> Unit) {
    val composableScope = rememberCoroutineScope()
    Button(
        onClick = {}
    ) {
        Text("Open Swish App")
    }
    Text("or pay using another phone")
    Button(
        onClick = {
            GlobalScope.launch {
                withContext(Dispatchers.Main) {
                    onQrCode()
                }
            }
        }
    ) {
        Text("Scan QR Code")
    }
    Button(
        onClick = {}
    ) {
        Text("Pay using phone number")
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
