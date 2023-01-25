package io.kronor.api

import com.apollographql.apollo3.ApolloClient
import com.apollographql.apollo3.network.okHttpClient
import com.apollographql.apollo3.network.ws.SubscriptionWsProtocol
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response

private class AuthorizationInterceptor(val token: String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .addHeader("Authorization", "Bearer $token")
            .build()

        return chain.proceed(request)
    }
}

private var instance: ApolloClient? = null

enum class Environment {
    Staging, Production
}

fun kronorApolloClient(token: String, env: Environment): ApolloClient? {
    if (instance != null) {
        return instance!!
    }

    val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(AuthorizationInterceptor(token))
        .build()

    instance = ApolloClient.Builder()
        .httpServerUrl(
            when (env) {
                Environment.Staging -> "https://staging.kronor.io/v1/graphql"
                Environment.Production -> "https://kronor.io/v1/graphql"
            }
        )
        .webSocketServerUrl(
            when (env) {
                Environment.Staging -> "wss://staging.kronor.io/v1/graphql"
                Environment.Production -> "wss://kronor.io/v1/graphql"
            }
        )
        .wsProtocol(
            SubscriptionWsProtocol.Factory (
                connectionPayload =
                    { mapOf("headers" to (mapOf("Authorization" to "Bearer $token"))) }
            )
        )
        .okHttpClient(okHttpClient)
        .build()

    return instance
}