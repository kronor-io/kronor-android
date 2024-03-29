package io.kronor.example

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.setFragmentResultListener

/**
 * A simple [Fragment] subclass as the second destination in the navigation.
 */
class Second2Fragment : Fragment() {
    private var sessionToken: String? by mutableStateOf(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        setFragmentResultListener("sessionKey") { _, bundle ->
            sessionToken = bundle.getString("sessionToken")
        }
        return ComposeView(requireContext()).apply {
//            setContent {
//                if (sessionToken != null) {
//                    val swishConfiguration = SwishConfiguration(sessionToken = sessionToken!!,
//                        merchantLogo = R.drawable.kronor_logo,
//                        environment = Environment.Staging,
//                        appName = "kronor-android-test",
//                        appVersion = "0.1.0",
//                        locale = Locale("en_US"),
//                        redirectUrl = Uri.parse("kronor_test://"),
//                        )
//                    GetSwishComponent(LocalContext.current, swishConfiguration)
//                }
//            }

        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
    }

    override fun onDestroyView() {
        super.onDestroyView()
    }
}