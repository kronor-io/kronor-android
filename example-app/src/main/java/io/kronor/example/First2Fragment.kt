package io.kronor.example

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import io.kronor.example.databinding.FragmentFirst2Binding
import io.kronor.example.type.Country
import io.kronor.example.type.SupportedCurrencyEnum
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class First2Fragment : Fragment() {

    private val viewModel: MainViewModel by viewModels()
    private var _binding: FragmentFirst2Binding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentFirst2Binding.inflate(inflater, container, false)
        return binding.root
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.buttonPayWithSwish.setOnClickListener {
            lifecycleScope.launchWhenResumed {
                withContext(Dispatchers.IO) {
                    val sessionToken = viewModel.createNewPaymentSession(
                        binding.amountField.text.toString(), Country.SE, SupportedCurrencyEnum.SEK
                    )
                    sessionToken?.let {
                        setFragmentResult("sessionKey", bundleOf("sessionToken" to it))
                        withContext(Dispatchers.Main) {
                            findNavController().navigate(R.id.action_First2Fragment_to_Second2Fragment)
                        }
                    }
                }
            }
        }
        binding.buttonPayWithCreditCard.setOnClickListener {
            lifecycleScope.launchWhenResumed {
                withContext(Dispatchers.IO) {
                    val sessionToken = viewModel.createNewPaymentSession(
                        binding.amountField.text.toString(), Country.SE, SupportedCurrencyEnum.SEK
                    )
                    sessionToken?.let {
                        setFragmentResult("sessionKey", bundleOf("sessionToken" to it))
                        withContext(Dispatchers.Main) {
                            findNavController().navigate(R.id.action_First2Fragment_to_Second2Fragment)
                        }
                    }
                }
            }
        }
        binding.buttonPayWithMobilepay.setOnClickListener {
            lifecycleScope.launchWhenResumed {
                withContext(Dispatchers.IO) {
                    val sessionToken = viewModel.createNewPaymentSession(
                        binding.amountField.text.toString(), Country.DK, SupportedCurrencyEnum.DKK
                    )
                    sessionToken?.let {
                        setFragmentResult("sessionKey", bundleOf("sessionToken" to it))
                        withContext(Dispatchers.Main) {
                            findNavController().navigate(R.id.action_First2Fragment_to_Second2Fragment)
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}