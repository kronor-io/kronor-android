# Kronor Android

Kronor Android provides payment components that you can use to create a custom checkout solution for your customers by using any of our provided payment methods.

## Installation

You can use our official releases via Maven Central by adding the following dependencies:

```gradle
    implementation 'io.kronor:api:1.1'
    implementation 'io.kronor.component:swish:1.1'
```

## Additional Setup

In your project's `build.gradle` file ensure you have `compose` enabled:

```gradle
android {
    ...
    buildFeatures {
        viewBinding true
        compose true
    }
    composeOptions {
        kotlinCompilerExtensionVersion '1.4.0'
    }
}
```

Invoking the component:

```kotlin
val swishConfiguration = SwishConfiguration(
    sessionToken = "sessionToken", // the token as received from the `newPaymentSession` mutation
    merchantLogo = null, // a logo to display to the user when the payment is in progress
    environment = Environment.Staging, // environment to point to
    appName = "your_app_name",
    appVersion = "your_app_version",
    locale = Locale("en_US"),
    redirectUrl = Uri.parse("your_app_uri")
)

SwishComponent(LocalContext.current, swishConfiguration)
```

You can also refer to the [example-app](example-app) for reference. `Second2Fragment` files shows using the swish component in traditional android views
