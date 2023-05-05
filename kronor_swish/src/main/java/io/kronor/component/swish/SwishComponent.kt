package io.kronor.component.swish

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager.MATCH_DEFAULT_ONLY
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaDrm
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Button
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat.startActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.apollographql.apollo3.exception.ApolloException
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import io.kronor.api.KronorError
import io.kronor.api.PaymentConfiguration
import io.kronor.api.PaymentStatusSubscription
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.util.UUID

@Composable
fun swishViewModel(swishConfiguration: PaymentConfiguration): SwishViewModel {
    return viewModel(factory = SwishViewModelFactory(swishConfiguration))
}

@Composable
fun SwishComponent(
    viewModel: SwishViewModel,
) {
    val context = LocalContext.current

    if (!LocalInspectionMode.current) {
        LaunchedEffect(Unit) {
            viewModel.setDeviceFingerPrint(getWeakFingerprint(context))
        }
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle

    LaunchedEffect(Unit) {
        viewModel.transition(SwishStatechart.Companion.Event.SubscribeToPaymentStatus)
        launch {
            Log.d("SwishComponent", "lifecycle scope launched")
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.subscription()
                }
            }
        }
    }

    SwishScreen(
        transition = viewModel::transition,
        state = viewModel.swishState,
        selectedMethod = viewModel.selectedMethod,
        updateSelectedMethod = viewModel::updateSelectedMethod,
        paymentRequest = viewModel.paymentRequest,
        merchantLogo = viewModel.merchantLogo()
    )
}


@Composable
private fun SwishScreen(
    transition: (SwishStatechart.Companion.Event) -> Unit,
    state: State<SwishStatechart.Companion.State>,
    selectedMethod: State<SelectedMethod?>,
    updateSelectedMethod: (SelectedMethod) -> Unit,
    paymentRequest: PaymentStatusSubscription.PaymentRequest?,
    @DrawableRes merchantLogo: Int? = null
) {

    SwishWrapper(merchantLogo) {
        when (state.value) {
            SwishStatechart.Companion.State.WaitingForSubscription -> {
                SwishInitializing()
            }

            SwishStatechart.Companion.State.PromptingMethod -> {
                SwishPromptScreen(
                    onAppOpen = {
                        updateSelectedMethod(SelectedMethod.SwishApp)
                        transition(SwishStatechart.Companion.Event.UseSwishApp)
                    },
                    onQrCode = {
                        updateSelectedMethod(SelectedMethod.QrCode)
                        transition(SwishStatechart.Companion.Event.UseQR)
                    },
                    onPhoneNumberPayNow = { phoneNumber ->
                        updateSelectedMethod(SelectedMethod.PhoneNumber)
                        transition(
                            SwishStatechart.Companion.Event.PhoneNumberInserted(
                                phoneNumber
                            )
                        )
                    }
                )
            }

            SwishStatechart.Companion.State.InsertingPhoneNumber -> {
                SwishPromptPhoneNumber(onPayNow = {
                })
            }

            is SwishStatechart.Companion.State.CreatingPaymentRequest -> SwishCreatingPaymentRequest()
            is SwishStatechart.Companion.State.WaitingForPaymentRequest -> SwishCreatingPaymentRequest()
            is SwishStatechart.Companion.State.PaymentRequestInitialized -> {
                when (selectedMethod.value) {
                    SelectedMethod.QrCode -> {
                        val qrToken = paymentRequest?.transactionSwishDetails?.first()?.qrCode
                        SwishPaymentWithQrCode(qrToken, onCancelPayment = {
                            transition(SwishStatechart.Companion.Event.CancelFlow)
                        })
                    }

                    SelectedMethod.SwishApp -> {
                        val returnUrl = paymentRequest?.transactionSwishDetails?.first()?.returnUrl
                        OpenSwishApp(
                            context = LocalContext.current,
                            returnUrl = returnUrl,
                            onAppOpened = {
                                transition(SwishStatechart.Companion.Event.SwishAppOpened)
                            })
                    }

                    SelectedMethod.PhoneNumber -> {
                        SwishWaitingForPayment(onCancelPayment = {
                            transition(SwishStatechart.Companion.Event.CancelFlow)
                        })
                    }

                    else -> {}
                }
            }

            is SwishStatechart.Companion.State.PaymentCompleted -> {
                SwishPaymentCompleted()
            }

            SwishStatechart.Companion.State.PaymentRejected -> {
                SwishPaymentRejected(onPaymentRetry = { transition(SwishStatechart.Companion.Event.Retry) },
                    onGoBack = { transition(SwishStatechart.Companion.Event.CancelFlow) })
            }

            is SwishStatechart.Companion.State.Errored -> {
                SwishPaymentErrored(
                    error = (state.value as SwishStatechart.Companion.State.Errored).error,
                    onPaymentRetry = {
                        transition(SwishStatechart.Companion.Event.Retry)
                    },
                    onGoBack = {
                        transition(SwishStatechart.Companion.Event.CancelFlow)
                    })
            }

            SwishStatechart.Companion.State.WaitingForPayment -> {
                SwishWaitingForPayment(onCancelPayment = {
                    transition(SwishStatechart.Companion.Event.CancelFlow)
                })
            }
        }
    }
}

@Composable
private fun SwishInitializing() {
    Column(
        modifier = Modifier.fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(stringResource(R.string.initializing))
        Spacer(modifier = Modifier.height(30.dp))
        CircularProgressIndicator()
    }
}

@Composable
private fun SwishWrapper(@DrawableRes merchantLogo: Int? = null, content: @Composable () -> Unit) {
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
private fun SwishPromptPhoneNumber(onPayNow: (String) -> Unit) {
    var phoneNumber by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(""))
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxHeight()
    ) {
        val pattern = remember { Regex("^\\d+\$") }
        val focusManager = LocalFocusManager.current

        OutlinedTextField(value = phoneNumber, onValueChange = {
            if (it.text.matches(pattern)) {
                phoneNumber = it
            }
        }, placeholder = {
            Text(stringResource(R.string.enter_swish_number))
        }, keyboardOptions = KeyboardOptions.Default.copy(
            keyboardType = KeyboardType.Phone,
            imeAction = ImeAction.Done
        ),
            keyboardActions = KeyboardActions(
                onDone = {
                    focusManager.clearFocus()
                }
            )
        )
        Spacer(modifier = Modifier.height(100.dp))
        Button(onClick = {
            onPayNow(phoneNumber.text)
        }) {
            Text(stringResource(R.string.pay_now))
        }
    }
}

@Composable
private fun SwishPaymentCompleted() {
    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxHeight()
    ) {
        Text(stringResource(R.string.payment_completed))
    }
}

@Composable
private fun SwishPaymentRejected(onPaymentRetry: () -> Unit, onGoBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxHeight(),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(100.dp))
        Text(stringResource(R.string.payment_rejected))
        Button(onClick = {
            onPaymentRetry()
        }) {
            Text(stringResource(id = R.string.try_again))
        }

        Button(onClick = {
            onGoBack()
        }) {
            Text(stringResource(id = R.string.go_back))
        }
    }
}

@Composable
private fun SwishPaymentErrored(
    error: KronorError,
    onPaymentRetry: () -> Unit,
    onGoBack: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxHeight(),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(100.dp))
        when (error) {
            is KronorError.NetworkError -> {
                Text(
                    stringResource(R.string.network_error), textAlign = TextAlign.Center
                )
            }

            is KronorError.GraphQlError -> {
                Text(
                    stringResource(R.string.graphql_error), textAlign = TextAlign.Center
                )
            }
        }
        Button(onClick = {
            onPaymentRetry()
        }) {
            Text(stringResource(R.string.try_again))
        }

        Button(onClick = {
            onGoBack()
        }) {
            Text(stringResource(R.string.go_back))
        }
    }
}

@Composable
private fun SwishPaymentWithQrCode(qrToken: String?, onCancelPayment: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (qrToken != null) {
                SwishQrCode(qrToken)
                Spacer(modifier = Modifier.height(30.dp))
                Text(stringResource(R.string.scan_qr))
            } else {
                Text(stringResource(R.string.generate_qr))
            }
            Spacer(modifier = Modifier.height(30.dp))
        }
        Button(onClick = { onCancelPayment() }) {
            Text(stringResource(id = R.string.cancel_payment))
        }
    }
}

@Composable
private fun SwishWaitingForPayment(onCancelPayment: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxHeight(),
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.accept_swish_phone), textAlign = TextAlign.Center
        )
        CircularProgressIndicator()
        Button(onClick = { onCancelPayment() }) {
            Text(stringResource(R.string.cancel_payment))
        }
    }
}

@Composable
private fun SwishCreatingPaymentRequest() {
    Column(
        modifier = Modifier.fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(stringResource(R.string.creating_swish))
        Spacer(modifier = Modifier.height(30.dp))
        CircularProgressIndicator()
    }
}

private fun swishAppExists(context: Context): Boolean {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("swish://"))
    val appIntentMatch = context.packageManager.queryIntentActivities(
        intent, MATCH_DEFAULT_ONLY
    )
    return appIntentMatch.any { resolveInfo ->
        resolveInfo.activityInfo.packageName == "se.bankgirot.swish.sandbox" || resolveInfo.activityInfo.packageName == "se.bankgirot.swish"
    }
}

@Composable
private fun SwishPromptScreen(
    onAppOpen: () -> Unit,
    onQrCode: () -> Unit,
    onPhoneNumberPayNow: (String) -> Unit
) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "swishPromptMethods") {
        composable("swishPromptMethods") {
            SwishPromptMethods(onAppOpen = onAppOpen, onQrCode = onQrCode, onPhoneNumber = {
                navController.navigate(
                    "swishPromptPhoneNumber"
                )
            })
        }
        composable("swishPromptPhoneNumber") {
            SwishPromptPhoneNumber(onPayNow = onPhoneNumberPayNow)
        }
    }
}

@Composable
private fun SwishPromptMethods(
    onAppOpen: () -> Unit,
    onQrCode: () -> Unit,
    onPhoneNumber: () -> Unit
) {
    val doesSwishAppExist: Boolean = if (LocalInspectionMode.current) {
        true
    } else {
        swishAppExists(LocalContext.current)
    }
    Column(
        modifier = Modifier.fillMaxHeight(),
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (doesSwishAppExist) {
            Button(onClick = {
                onAppOpen()
            }) {
                Text(stringResource(id = R.string.open_swish))
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

}

@Composable
private fun OpenSwishApp(context: Context, returnUrl: String?, onAppOpened: () -> Unit) {
    val swishUrl = Uri.parse(returnUrl)
    val intent = Intent(Intent.ACTION_VIEW, swishUrl)
    val appIntentMatch = context.packageManager.queryIntentActivities(intent, MATCH_DEFAULT_ONLY)
    val doesSwishAppExist = appIntentMatch.any { resolveInfo ->
        Log.d("AppCheck", "${resolveInfo.activityInfo}")
        resolveInfo.activityInfo.packageName == "se.bankgirot.swish.sandbox" || resolveInfo.activityInfo.packageName == "se.bankgirot.swish"
    }

    if (doesSwishAppExist) {
        try {
            startActivity(context, intent, null)
            onAppOpened()
        } catch (e: ActivityNotFoundException) {
            Text("No Swish App Found")
            Log.d("SwishApp", "No Swish app")
        }
    } else {
        Text("No Swish App Found")
        Log.d("SwishApp", "No Swish app")
    }
}

@Composable
private fun SwishQrCode(qrToken: String) {
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
private fun SwishLogo() {
    val swishLogo = if (isSystemInDarkTheme()) R.drawable.swish_dark else R.drawable.swish_light

    Image(
        painter = painterResource(id = swishLogo),
        contentDescription = "Swish logo",
        modifier = Modifier
            .padding(end = 10.dp)
            .height(100.dp)
            .width(100.dp),
        contentScale = ContentScale.Inside
    )
}

@Composable
private fun MerchantLogo(@DrawableRes merchantDrawable: Int) {
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
private fun PreviewPromptMethods() {
    SwishWrapper {
        SwishPromptMethods(onAppOpen = { }, onQrCode = { }, onPhoneNumber = { })
    }
}

@Preview
@Composable
private fun PreviewInsertingPhoneNumber() {
    SwishWrapper {
        SwishPromptPhoneNumber(onPayNow = {})
    }
}

@Preview
@Composable
private fun PreviewCreatingPaymentRequest() {
    SwishWrapper {
        SwishCreatingPaymentRequest()
    }
}

@Preview
@Composable
private fun PreviewQrCodeScreen() {
    SwishWrapper {
        SwishPaymentWithQrCode("DpXc79mMZT7CJCMiLoLYzQ6FjHV46bk6p", onCancelPayment = {})
    }
}

@Preview
@Composable
private fun PreviewWaitingForPaymentScreen() {
    SwishWrapper {
        SwishWaitingForPayment(onCancelPayment = {})
    }
}

@Preview
@Composable
private fun PreviewSwishPaymentCompleted() {
    SwishWrapper {
        SwishPaymentCompleted()
    }
}

@Preview
@Composable
private fun PreviewSwishPaymentRejected() {
    SwishWrapper {
        SwishPaymentRejected({}) {}
    }
}

@Preview
@Composable
private fun PreviewSwishPaymentErrored() {
    SwishWrapper {
        SwishPaymentErrored(
            error = KronorError.NetworkError(ApolloException()),
            onPaymentRetry = { }) {}
    }
}

@SuppressLint("HardwareIds")
private fun getWeakFingerprint(context: Context): String {
    val contentResolver: ContentResolver = context.contentResolver!!

    val androidId: String? by lazy {
        try {
            Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        } catch (e: Exception) {
            null
        }
    }

    fun getGsfId(): String? {
        val URI = Uri.parse("content://com.google.android.gsf.gservices")
        val params = arrayOf("android_id")
        return try {
            val cursor: Cursor = contentResolver
                .query(URI, null, null, params, null) ?: return null

            if (!cursor.moveToFirst() || cursor.columnCount < 2) {
                cursor.close()
                return null
            }
            try {
                val result = java.lang.Long.toHexString(cursor.getString(1).toLong())
                cursor.close()
                result
            } catch (e: NumberFormatException) {
                cursor.close()
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    val gsfId: String? by lazy {
        try {
            getGsfId()
        } catch (e: Exception) {
            null
        }
    }


    fun releaseMediaDRM(mediaDrm: MediaDrm) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            mediaDrm.close()
        } else {
            mediaDrm.release()
        }
    }

    fun mediaDrmId(): String {
        val wIDEWINE_UUID_MOST_SIG_BITS = -0x121074568629b532L
        val wIDEWINE_UUID_LEAST_SIG_BITS = -0x5c37d8232ae2de13L
        val widevineUUID = UUID(wIDEWINE_UUID_MOST_SIG_BITS, wIDEWINE_UUID_LEAST_SIG_BITS)
        val wvDrm: MediaDrm?

        wvDrm = MediaDrm(widevineUUID)
        val mivevineId = wvDrm.getPropertyByteArray(MediaDrm.PROPERTY_DEVICE_UNIQUE_ID)
        releaseMediaDRM(wvDrm)
        val md: MessageDigest = MessageDigest.getInstance("SHA-256")
        md.update(mivevineId)

        return md.digest().joinToString("") {
            java.lang.String.format("%02x", it)
        }
    }

    val drmId: String? by lazy {
        try {
            mediaDrmId()
        } catch (e: Exception) {
            null
        }
    }

    return gsfId ?: drmId ?: androidId ?: "nofingerprintandroid"
}