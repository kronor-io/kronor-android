package io.kronor.component.credit_card

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import com.fingerprintjs.android.fingerprint.Fingerprinter
import com.fingerprintjs.android.fingerprint.FingerprinterFactory

@Composable
fun creditCardViewModel(creditCardConfiguration: CreditCardConfiguration): CreditCardViewModel {
    return viewModel(factory = CreditCardViewModelFactory(creditCardConfiguration))
}

@Composable
fun GetCreditCardComponent(
    context: Context,
    creditCardConfiguration: CreditCardConfiguration,
    viewModel: CreditCardViewModel = creditCardViewModel(creditCardConfiguration = creditCardConfiguration)
) {

    if (!LocalInspectionMode.current) {
        LaunchedEffect(Unit) {
            val fingerprinterFactory = FingerprinterFactory.create(context)
            fingerprinterFactory.getFingerprint(version = Fingerprinter.Version.V_5, listener = {
                viewModel.deviceFingerprint = it
            })
        }
    }

    CreditCardScreen(
        merchantLogo = creditCardConfiguration.merchantLogo, viewModel = viewModel
    )
}

@Composable
fun CreditCardScreen(merchantLogo: Int?, viewModel: CreditCardViewModel) {
    TODO("Not yet implemented")
}


@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
}