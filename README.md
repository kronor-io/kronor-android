# Kronor Android

Kronor Android provides payment components that you can use to create a custom checkout solution for your customers by using any of our provided payment methods.

## Installation

Add `jitpack` to your repositories

```gradle
repositories {
   ...
   maven { url 'https://jitpack.io' }
}
```

Add the dependency:

```gradle
dependencies {
    implementation 'io.kronor.kronor-android:kronor_api:1.0'
    implementation 'io.kronor.kronor-android:kronor_swish:1.0'
}
```

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
    redirectUrl = Uri.parse("your_app_uri"),
    onPaymentSuccess = { /* success callback */ },
    onPaymentFailure = { /* failure callback */ }
)

GetSwishComponent(LocalContext.current, swishConfiguration)
```

You can also refer to the [example-app](example-app) for reference
