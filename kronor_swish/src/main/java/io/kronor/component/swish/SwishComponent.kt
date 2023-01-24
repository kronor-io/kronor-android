package io.kronor.component.swish

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fingerprintjs.android.fingerprint.Fingerprinter
import com.fingerprintjs.android.fingerprint.FingerprinterFactory
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import io.kronor.api.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SwishScreen(sessionToken: String, @DrawableRes merchantLogo: Int? = null) {
    val context = LocalContext.current
    val fingerprinterFactory = FingerprinterFactory.create(context)
    var fingerprint = remember {
        mutableStateOf("")
    }

    var waitToken : MutableState<String?> = remember {
        mutableStateOf(null)
    }

    fingerprinterFactory.getFingerprint(
        version = Fingerprinter.Version.V_5,
        listener = {
            fingerprint.value = it
        }
    )

    remember {
        GlobalScope.launch {
            withContext(
                Dispatchers.Main
            ) {
                makeNewPaymentRequest(
                    SwishComponentInput(sessionToken, returnUrl = "https://google.com"),
                    "fingerprint",
                    Environment.Staging
                )?.let {
                    Log.d("NewSwishPayment", "$it")
                    waitToken.value = it
                }
            }
        }
    }

    Log.d("SwishScreen", "here")
    waitToken.value?.let {
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
                Spacer(modifier = Modifier.height(100.dp))
                SwishQrCode()
                Text("Open Swish App")
                Text("Enter phone number")
            }
        }
    }
}

@Composable
fun SwishQrCode() {
    val qrToken = "DpXc79mMZT7CJCMiLoLYzQ6FjHV46bk6p"
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
fun DefaultPreview() {
    SwishScreen("")
}