# Kronor Android

[![api](https://maven-badges.herokuapp.com/maven-central/io.kronor/api/badge.svg?style=plastic&subject=api)](https://maven-badges.herokuapp.com/maven-central/io.kronor/api/)
[![Swish](https://maven-badges.herokuapp.com/maven-central/io.kronor.component/swish/badge.svg?style=plastic&subject=swish)](https://maven-badges.herokuapp.com/maven-central/io.kronor.component/swish/)
[![credit_card](https://maven-badges.herokuapp.com/maven-central/io.kronor.component/credit_card/badge.svg?style=plastic&subject=credit_card)](https://maven-badges.herokuapp.com/maven-central/io.kronor.component/credit_card/)
[![mobilepay](https://maven-badges.herokuapp.com/maven-central/io.kronor.component/mobilepay/badge.svg?style=plastic&subject=mobilepay)](https://maven-badges.herokuapp.com/maven-central/io.kronor.component/mobilepay/)
[![vipps](https://maven-badges.herokuapp.com/maven-central/io.kronor.component/vipps/badge.svg?style=plastic&subject=vipps)](https://maven-badges.herokuapp.com/maven-central/io.kronor.component/vipps/)
[![paypal](https://maven-badges.herokuapp.com/maven-central/io.kronor.component/paypal/badge.svg?style=plastic&subject=paypal)](https://maven-badges.herokuapp.com/maven-central/io.kronor.component/paypal/)
[![webview_payment_gateway](https://maven-badges.herokuapp.com/maven-central/io.kronor.component/webview_payment_gateway/badge.svg?style=plastic&subject=webview_payment_gateway)](https://maven-badges.herokuapp.com/maven-central/io.kronor.component/webview_payment_gateway/)
[![fallback](https://maven-badges.herokuapp.com/maven-central/io.kronor.component/fallback/badge.svg?style=plastic&subject=fallback)](https://maven-badges.herokuapp.com/maven-central/io.kronor.component/fallback/)

Kronor Android provides payment components that you can use to create a custom checkout solution for your customers by using any of our provided payment methods.

## Payment methods

These are the payment methods that are currently provided and supported by this sdk

### Native

- [Swish](#swish)
- [CreditCard](#credit-card)
- [MobilePay](#mobilepay)
- [Vipps](#vipps)
- [PayPal](#paypal)

### Fallback

- [P24](#p24)
- [Bank Transfer](#bank-transfer)

## Additional Setup

In your project's `build.gradle` file ensure you have `compose` enabled:

```groovy
android {
    buildFeatures {
        viewBinding true
        compose true
    }
    composeOptions {
        kotlinCompilerExtensionVersion '1.5.2'
    }
}
```

### Swish

Dependencies:

```groovy
dependencies {
    implementation 'io.kronor:api:2.3.4'
    implementation 'io.kronor.component:swish:2.3.4'
}
```

Imports:

```kotlin
import io.kronor.api.Environment
import io.kronor.api.PaymentEvent
import io.kronor.component.swish.SwishComponent
import io.kronor.component.swish.SwishConfiguration
import io.kronor.component.swish.swishViewModel
```

Invoking the Swish component:

```kotlin
val viewModelForSwish : SwishViewModel = swishViewModel(swishConfiguration = SwishConfiguration(
    sessionToken = "sessionToken", // the token as received from the `newPaymentSession` mutation
    merchantLogo = null, // a logo to display to the user when the payment is in progress
    environment = Environment.Staging, // environment to point to
    appName = "your_app_name",
    appVersion = "your_app_version",
    locale = Locale("en_US"),
    redirectUrl = Uri.parse("your_app_uri")
))

SwishComponent(swishConfiguration)
```

Handling the payment events:

```kotlin
lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
    launch {
        viewModelForSwish.events.collect { event ->
            when (event) {
                PaymentEvent.PaymentFailure -> {
                    // handle the success event here, example:
                    withContext(Dispatchers.Main) {
                        navController.navigate("paymentMethods")
                    }
                }

                is PaymentEvent.PaymentSuccess -> {
                    // handle the success event here, example:
                    withContext(Dispatchers.Main) {
                        navController.navigate("paymentMethods")
                    }
                }
            }
        }
    }
}
```

### Credit Card

Dependencies:

```groovy
dependencies {
    implementation 'io.kronor:api:2.3.4'
    implementation 'io.kronor.component:credit_card:2.3.4'
    implementation 'io.kronor.component:webview_payment_gateway:2.3.4'
}
```

Imports:

```kotlin
import io.kronor.api.Environment
import io.kronor.api.PaymentEvent
import io.kronor.component.credit_card.CreditCardComponent
import io.kronor.component.credit_card.CreditCardConfiguration
import io.kronor.component.credit_card.creditCardViewModel
```

Invoking the CreditCard component:

```kotlin
val viewModelForCreditCard : CreditCardViewModel = creditCardViewModel(creditCardConfiguration = CreditCardConfiguration(
    sessionToken = "sessionToken", // the token as received from the `newPaymentSession` mutation
    merchantLogo = R.id.kronor_logo, // a logo to display to the user when the payment is in progress
    environment = Environment.Staging, // environment to point to
    appName = "your_app_name",
    appVersion = "your_app_version",
    redirectUrl = Uri.parse("your_app_uri")
))

CreditCardComponent(viewModelForCreditCard)
```

Handling the payment events:

```kotlin
lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
    launch {
        viewModelForCreditCard.events.collect { event ->
            when (event) {
                PaymentEvent.PaymentFailure -> {
                    // handle the event here, example:
                    withContext(Dispatchers.Main) {
                        navController.navigate("paymentMethods")
                    }
                }

                is PaymentEvent.PaymentSuccess -> {
                    // handle the success event here, example:
                    withContext(Dispatchers.Main) {
                        navController.navigate("paymentMethods")
                    }
                }
            }
        }
    }
}
```

### MobilePay

Dependencies:

```groovy
dependencies {
    implementation 'io.kronor:api:2.3.4'
    implementation 'io.kronor.component:mobilepay:2.3.4'
    implementation 'io.kronor.component:webview_payment_gateway:2.3.4'
}
```

Imports:

```kotlin
import io.kronor.api.Environment
import io.kronor.api.PaymentEvent
import io.kronor.component.mobilepay.MobilePayComponent
import io.kronor.component.mobilepay.MobilePayConfiguration
import io.kronor.component.mobilepay.mobilePayViewModel
```

Invoking the MobilePay component:

```kotlin
val viewModelForMobilePay : MobilePayViewModel = mobilePayViewModel(mobilePayConfiguration = MobilePayConfiguration(
    sessionToken = "sessionToken", // the token as received from the `newPaymentSession` mutation
    merchantLogo = R.id.kronor_logo, // a logo to display to the user when the payment is in progress or null
    environment = Environment.Staging, // environment to point to
    appName = "your_app_name",
    appVersion = "your_app_version",
    redirectUrl = Uri.parse("your_app_uri")
))

MobilePayComponent(viewModelForMobilePay)
```

Handling the payment events:

```kotlin
lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
    launch {
        viewModelForMobilePay.events.collect { event ->
            when (event) {
                PaymentEvent.PaymentFailure -> {
                    // handle the event here, example:
                    withContext(Dispatchers.Main) {
                        navController.navigate("paymentMethods")
                    }
                }

                is PaymentEvent.PaymentSuccess -> {
                    // handle the success event here, example:
                    withContext(Dispatchers.Main) {
                        navController.navigate("paymentMethods")
                    }
                }
            }
        }
    }
}
```

### Vipps

Dependencies:

```groovy
dependencies {
    implementation 'io.kronor:api:2.3.4'
    implementation 'io.kronor.component:vipps:2.3.4'
    implementation 'io.kronor.component:webview_payment_gateway:2.3.4'
}
```

Imports:

```kotlin
import io.kronor.api.Environment
import io.kronor.api.PaymentEvent
import io.kronor.component.vipps.VippsComponent
import io.kronor.component.vipps.VippsConfiguration
import io.kronor.component.vipps.vippsViewModel
```

Invoking the Vipps component:

```kotlin
val viewModelForVipps : VippsViewModel = vippsViewModel(vippsConfiguration = VippsConfiguration(
    sessionToken = "sessionToken", // the token as received from the `newPaymentSession` mutation
    merchantLogo = R.id.kronor_logo, // a logo to display to the user when the payment is in progress or null
    environment = Environment.Staging, // environment to point to
    appName = "your_app_name",
    appVersion = "your_app_version",
    redirectUrl = Uri.parse("your_app_uri")
))

VippsComponent(viewModelForVipps)
```

Handling the payment events:

```kotlin
lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
    launch {
        viewModelForVipps.events.collect { event ->
            when (event) {
                PaymentEvent.PaymentFailure -> {
                    // handle the event here, example:
                    withContext(Dispatchers.Main) {
                        navController.navigate("paymentMethods")
                    }
                }

                is PaymentEvent.PaymentSuccess -> {
                    // handle the success event here, example:
                    withContext(Dispatchers.Main) {
                        navController.navigate("paymentMethods")
                    }
                }
            }
        }
    }
}
```

### PayPal

Dependencies:

```groovy
dependencies {
    implementation 'io.kronor:api:2.3.4'
    implementation 'io.kronor.component:paypal:2.3.4'
    implementation 'io.kronor.component:webview_payment_gateway:2.3.4'
}
```

Imports:

```kotlin
import io.kronor.api.Environment
import io.kronor.api.PaymentEvent
import io.kronor.component.paypal.PayPalComponent
import io.kronor.component.paypal.PayPalConfiguration
import io.kronor.component.paypal.paypalViewModel
```

Invoking the PayPal component:

```kotlin
val viewModelForPayPal : PayPalViewModel = paypalViewModel(paypalConfiguration = paypalConfiguration(
    sessionToken = "sessionToken", // the token as received from the `newPaymentSession` mutation
    merchantLogo = R.id.kronor_logo, // a logo to display to the user when the payment is in progress or null
    environment = Environment.Staging, // environment to point to
    appName = "your_app_name",
    appVersion = "your_app_version",
    redirectUrl = Uri.parse("your_app_uri")
))

PayPalComponent(viewModelForPayPal)
```

Handling the payment events:

```kotlin
lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
    launch {
        viewModelForPayPal.events.collect { event ->
            when (event) {
                PaymentEvent.PaymentFailure -> {
                    // handle the event here, example:
                    withContext(Dispatchers.Main) {
                        navController.navigate("paymentMethods")
                    }
                }

                is PaymentEvent.PaymentSuccess -> {
                    // handle the success event here, example:
                    withContext(Dispatchers.Main) {
                        navController.navigate("paymentMethods")
                    }
                }
            }
        }
    }
}
```

### Fallback Component

All payment methods are also supported in the fallback component, in case there is no native implementation.  
Payment method to fallback mapping:

| Payment Method  | Fallback  |
|--- |--- |
| P24   | p24   |
| Bank Transfer   | bankTransfer   |

#### Example

##### P24

Dependencies:

```groovy
dependencies {
    implementation 'io.kronor:api:2.3.4'
    implementation 'io.kronor.component:fallback:2.3.4'
    implementation 'io.kronor.component:webview_payment_gateway:2.3.4'
}
```

Imports:

```kotlin
import io.kronor.api.Environment
import io.kronor.api.PaymentEvent
import io.kronor.component.fallback.FallbackComponent
import io.kronor.component.fallback.fallbackViewModel
```

Invoking the Fallback component:

```kotlin
val viewModelForFallback = fallbackViewModel(fallbackConfiguration = PaymentConfiguration(
        sessionToken = "sessionToken", // the token as received from the `newPaymentSession` mutation
        merchantLogo = R.drawable.kronor_logo, // a logo to display to the user when the payment is in progress or null
        environment = Environment.Staging, // environment to point to
        appName = "your_app_name",
        appVersion = "your_app_version",
        redirectUrl = Uri.parse("your_app_uri"),
        locale = Locale("en_US")
    ), paymentMethod = "p24" // note the usage of "p24". refer to the mapping table for other fallback payment methods
)

FallbackComponent(viewModelForFallback)
```

Handling the payment events:

```kotlin
val lifecycle = LocalLifecycleOwner.current.lifecycle
LaunchedEffect(Unit) {
    launch {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            launch {
                viewModelForFallback.events.collect {
                    when (it) {
                        PaymentEvent.PaymentFailure -> {
                            // handle the event here, example:
                            withContext(Dispatchers.Main) {
                                viewModel.resetPaymentState()
                                navController.navigate("paymentMethods")
                            }
                        }

                        is PaymentEvent.PaymentSuccess -> {
                            // handle the success event here, example:
                            withContext(Dispatchers.Main) {
                                viewModel.resetPaymentState()
                                navController.navigate("paymentMethods")
                            }
                        }
                    }
                }
            }
        }
    }
}
```

### Bank Transfer

Dependencies:

```groovy
dependencies {
    implementation 'io.kronor:api:2.3.4'
    implementation 'io.kronor.component:fallback:2.3.4'
    implementation 'io.kronor.component:webview_payment_gateway:2.3.4'
}
```

Imports:

```kotlin
import io.kronor.api.Environment
import io.kronor.api.PaymentEvent
import io.kronor.component.fallback.FallbackComponent
import io.kronor.component.fallback.fallbackViewModel
```

Invoking the Fallback component:

```kotlin
val viewModelForFallback = fallbackViewModel(fallbackConfiguration = PaymentConfiguration(
        sessionToken = "sessionToken", // the token as received from the `newPaymentSession` mutation
        merchantLogo = R.drawable.kronor_logo, // a logo to display to the user when the payment is in progress or null
        environment = Environment.Staging, // environment to point to
        appName = "your_app_name",
        appVersion = "your_app_version",
        redirectUrl = Uri.parse("your_app_uri"),
        locale = Locale("en_US")
    ), paymentMethod = "bankTransfer" // note the usage of "bankTransfer". refer to the mapping table for other fallback payment methods
)

FallbackComponent(viewModelForFallback)
```

Handling the payment events:

```kotlin
val lifecycle = LocalLifecycleOwner.current.lifecycle
LaunchedEffect(Unit) {
    launch {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            launch {
                viewModelForFallback.events.collect {
                    when (it) {
                        PaymentEvent.PaymentFailure -> {
                            // handle the event here, example:
                            withContext(Dispatchers.Main) {
                                viewModel.resetPaymentState()
                                navController.navigate("paymentMethods")
                            }
                        }

                        is PaymentEvent.PaymentSuccess -> {
                            // handle the success event here, example:
                            withContext(Dispatchers.Main) {
                                viewModel.resetPaymentState()
                                navController.navigate("paymentMethods")
                            }
                        }
                    }
                }
            }
        }
    }
}
```

## Handling redirects

For payment methods that redirect to other apps or the browser, you need to handle a redirect to the
app. Pass the intent on redirect to `viewModelFor{Swish,CreditCard,MobilePay,Vipps,PayPal,Fallback}.handleIntent(intent)`.
The redirect uri passed to the view model, will have a paymentMethod and sessionToken added as
query parameters.

You can also refer to the `MainActivity` in [example-app](example-app) for reference.
