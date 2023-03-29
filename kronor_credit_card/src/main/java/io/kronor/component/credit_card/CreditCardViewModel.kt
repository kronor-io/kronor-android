package io.kronor.component.credit_card

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class CreditCardViewModelFactory(
    private val creditCardConfiguration: CreditCardConfiguration
) : ViewModelProvider.NewInstanceFactory() {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return CreditCardViewModel(creditCardConfiguration) as T
    }
}

class CreditCardViewModel(
    private val creditCardConfiguration: CreditCardConfiguration
) : ViewModel() {
    var deviceFingerprint: String? = null
}
