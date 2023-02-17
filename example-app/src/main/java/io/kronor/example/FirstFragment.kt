package io.kronor.example

import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.lifecycle.lifecycleScope
import com.apollographql.apollo3.api.Optional
import com.apollographql.apollo3.exception.ApolloException
import io.kronor.example.apolloClient
import io.kronor.example.databinding.FragmentFirstBinding
import io.kronor.example.type.PaymentSessionInput
import io.kronor.example.type.SupportedCurrencyEnum
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class FirstFragment : Fragment() {

    private var _binding: FragmentFirstBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        _binding = FragmentFirstBinding.inflate(inflater, container, false)
        return binding.root

    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


        binding.buttonFirst.setOnClickListener {
            lifecycleScope.launchWhenResumed {
                val expiresAt = LocalDateTime.now().plusMinutes(5)
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'"))
                Log.d("NewPaymentSession", "test")
                val response = try {
                    apolloClient(requireContext()).mutation(
                        NewPaymentSessionMutation(
                            PaymentSessionInput(
                                amount = binding.editTextNumber.text.toString().toInt()*100,
                                currency = Optional.present(SupportedCurrencyEnum.SEK),
                                expiresAt = expiresAt,
                                idempotencyKey = UUID.randomUUID().toString(),
                                merchantReference = "reference",
                                message = "random message",
                                additionalData = Optional.absent()
                            )
                        )
                    ).execute()
                } catch (e: ApolloException) {
                    Log.e("NewPaymentSession", "Failed because: ${e.message}")
                    null
                }
                Log.d("NewPaymentSession", "Success ${response?.data?.newPaymentSession?.token}")

                return@launchWhenResumed
            }
//            findNavController().navigate(R.id.action_pay_with_swish)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}