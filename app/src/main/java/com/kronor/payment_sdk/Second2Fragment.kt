package com.kronor.payment_sdk

import android.net.Uri
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.fragment.findNavController
import com.kronor.payment_sdk.databinding.FragmentFirst2Binding
import com.kronor.payment_sdk.databinding.FragmentSecond2Binding
import io.kronor.api.Environment
import io.kronor.component.swish.GetSwishComponent
import io.kronor.component.swish.SwishConfiguration
import java.util.*

/**
 * A simple [Fragment] subclass as the second destination in the navigation.
 */
class Second2Fragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        return ComposeView(requireContext()).apply {
            setContent {
                val swishConfiguration = SwishConfiguration(
                    sessionToken = "sessionToken",
                    merchantLogo = R.drawable.boozt_logo,
                    environment = Environment.Staging,
                    appName = "kronor-android-test",
                    appVersion = "0.1.0",
                    locale = Locale("en_US"),
                    redirectUrl = Uri.parse("kronor_test://"),
                    onPaymentSuccess = {

                    },
                    onPaymentFailure = {

                    }
                )
                GetSwishComponent(LocalContext.current, swishConfiguration)
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
    }

    override fun onDestroyView() {
        super.onDestroyView()
    }
}