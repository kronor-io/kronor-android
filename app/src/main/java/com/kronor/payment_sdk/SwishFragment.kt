package com.kronor.payment_sdk

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import com.apollographql.apollo3.api.Optional
import com.kronor.payment_sdk.type.PaymentSessionInput
import com.kronor.payment_sdk.type.SupportedCurrencyEnum

// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val SESSION_TOKEN = "sessionToken"

/**
 * A simple [Fragment] subclass.
 * Use the [SwishFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class SwishFragment : Fragment() {
    private var sessionToken: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            sessionToken = it.getString(SESSION_TOKEN)
        }
    }

//    val response = apolloClient.mutation(
//        NewPaymentSessionMutation(
//            PaymentSessionInput(
//                amount = 100,
//                currency = Optional.present(SupportedCurrencyEnum.SEK),
//                expiresAt = "",
//                idempotencyKey = "",
//                merchantReference = "",
//                message = "",
//                additionalData = Optional.absent()
//            )
//        )
//    ).execute()
//    Log.d("NewPaymentSession", "Success ${response.data}")

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_swish, container, false)
    }


    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param param1 Parameter 1.
         * @return A new instance of fragment SwishFragment.
         */
        @JvmStatic
        fun newInstance(param1: String) =
            SwishFragment().apply {
                arguments = Bundle().apply {
                    putString(SESSION_TOKEN, param1)
                }
            }
    }
}