# Kronor Android

[![api](https://maven-badges.sml.io/sonatype-central/io.kronor/api/badge.svg?style=plastic&subject=api)](https://maven-badges.sml.io/sonatype-central/io.kronor/api/)
[![Swish](https://maven-badges.sml.io/sonatype-central/io.kronor.component/swish/badge.svg?style=plastic&subject=swish)](https://maven-badges.sml.io/sonatype-central/io.kronor.component/swish/)
[![credit_card](https://maven-badges.sml.io/sonatype-central/io.kronor.component/credit_card/badge.svg?style=plastic&subject=credit_card)](https://maven-badges.sml.io/sonatype-central/io.kronor.component/credit_card/)
[![mobilepay](https://maven-badges.sml.io/sonatype-central/io.kronor.component/mobilepay/badge.svg?style=plastic&subject=mobilepay)](https://maven-badges.sml.io/sonatype-central/io.kronor.component/mobilepay/)
[![vipps](https://maven-badges.sml.io/sonatype-central/io.kronor.component/vipps/badge.svg?style=plastic&subject=vipps)](https://maven-badges.sml.io/sonatype-central/io.kronor.component/vipps/)
[![paypal](https://maven-badges.sml.io/sonatype-central/io.kronor.component/paypal/badge.svg?style=plastic&subject=paypal)](https://maven-badges.sml.io/sonatype-central/io.kronor.component/paypal/)
[![googlepay](https://maven-badges.sml.io/sonatype-central/io.kronor.component/googlepay/badge.svg?style=plastic&subject=googlepay)](https://maven-badges.sml.io/sonatype-central/io.kronor.component/googlepay/)
[![fallback](https://maven-badges.sml.io/sonatype-central/io.kronor.component/fallback/badge.svg?style=plastic&subject=fallback)](https://maven-badges.sml.io/sonatype-central/io.kronor.component/fallback/)

Kronor Android provides Jetpack Compose payment components for Swish, credit cards, MobilePay,
Vipps, PayPal, bank transfer, Google Pay, and payment-gateway fallback methods.

## Setup

Enable Compose in the consuming Android application and add the API plus the required payment
component. Replace `VERSION` with the Kronor Android version used by the application.

```kotlin
dependencies {
    implementation("io.kronor:api:VERSION")
    implementation("io.kronor.component:credit_card:VERSION")
}
```

Available component artifacts are:

| Payment method | Artifact |
| --- | --- |
| Swish | `io.kronor.component:swish` |
| Credit card | `io.kronor.component:credit_card` |
| MobilePay | `io.kronor.component:mobilepay` |
| Vipps | `io.kronor.component:vipps` |
| PayPal | `io.kronor.component:paypal` |
| Bank transfer | `io.kronor.component:bank_transfer` |
| Google Pay | `io.kronor.component:googlepay` |
| Fallback | `io.kronor.component:fallback` |

## Payment Component

Create a `PaymentConfiguration` from the payment-session token returned by the Kronor API:

```kotlin
val configuration = PaymentConfiguration(
    sessionToken = sessionToken,
    merchantLogo = R.drawable.merchant_logo,
    environment = Environment.Staging,
    appName = "your_app_name",
    appVersion = "your_app_version",
    locale = Locale.Builder().setRegion("US").setLanguage("en").build(),
    redirectUrl = "your-app://payment-redirect".toUri(),
)
```

Pass the configuration directly to the component. The component owns its ViewModel and reports one
terminal `PaymentEvent` through a synchronous callback. Hand the event to an application-owned
ViewModel for confirmation, persistence, analytics, and navigation state:

```kotlin
class CheckoutViewModel(
    private val orderRepository: OrderRepository,
) : ViewModel() {
    fun onPaymentEvent(event: PaymentEvent) {
        viewModelScope.launch {
            when (event) {
                is PaymentEvent.PaymentSuccess -> {
                    orderRepository.confirmPayment(event.paymentId)
                    // Update application state to show the receipt.
                }

                PaymentEvent.PaymentFailure -> {
                    // Update application state to return to payment methods.
                }
            }
        }
    }
}

CreditCardComponent(
    configuration = configuration,
    redirectIntent = redirectIntent,
    onRedirectHandled = { handledIntent ->
        if (redirectIntent === handledIntent) {
            redirectIntent = null
        }
    },
    onResult = checkoutViewModel::onPaymentEvent,
)
```

The callback is a notification boundary; the SDK does not await or manage application work. A caller
can also launch a coroutine from the callback, but a ViewModel scope is preferred for work that must
survive recomposition or continue after the payment screen leaves composition.

The component ViewModel uses the nearest `ViewModelStoreOwner`. With Navigation 3, install
`rememberViewModelStoreNavEntryDecorator()` on `NavDisplay` to scope it to the payment entry.

## Payment Methods

All native components use the same result and redirect contract:

```kotlin
SwishComponent(
    configuration = configuration,
    redirectIntent = redirectIntent,
    onRedirectHandled = onRedirectHandled,
    onResult = onResult,
)

CreditCardComponent(
    configuration = configuration,
    redirectIntent = redirectIntent,
    onRedirectHandled = onRedirectHandled,
    onResult = onResult,
)

MobilePayComponent(
    configuration = configuration,
    redirectIntent = redirectIntent,
    onRedirectHandled = onRedirectHandled,
    onResult = onResult,
)

VippsComponent(
    configuration = configuration,
    redirectIntent = redirectIntent,
    onRedirectHandled = onRedirectHandled,
    onResult = onResult,
)

PayPalComponent(
    configuration = configuration,
    redirectIntent = redirectIntent,
    onRedirectHandled = onRedirectHandled,
    onResult = onResult,
)

BankTransferComponent(
    configuration = configuration,
    redirectIntent = redirectIntent,
    onRedirectHandled = onRedirectHandled,
    onResult = onResult,
)

GooglePayComponent(
    configuration = configuration,
    redirectIntent = redirectIntent,
    onRedirectHandled = onRedirectHandled,
    onResult = onResult,
)
```

The fallback component additionally requires the payment-gateway method name:

```kotlin
FallbackComponent(
    configuration = configuration,
    paymentMethod = "p24",
    redirectIntent = redirectIntent,
    onRedirectHandled = onRedirectHandled,
    onResult = onResult,
)
```

## Redirects

Forward redirect intents received by the activity to the active payment component through its
`redirectIntent` parameter. Keep the intent in observable state so an intent received by
`onNewIntent` causes recomposition:

```kotlin
private var redirectIntent by mutableStateOf<Intent?>(null)

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    redirectIntent = intent.takeIf { it.data != null }
    setContent {
        PaymentRoute(
            redirectIntent = redirectIntent,
            onRedirectHandled = { handledIntent ->
                if (redirectIntent === handledIntent) {
                    redirectIntent = null
                }
            },
        )
    }
}

override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    redirectIntent = intent.takeIf { it.data != null }
}
```

See the [`example-app`](example-app) for a complete Navigation 3 integration.
