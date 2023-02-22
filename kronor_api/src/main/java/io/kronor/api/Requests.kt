package io.kronor.api

import com.apollographql.apollo3.ApolloCall
import com.apollographql.apollo3.ApolloClient
import com.apollographql.apollo3.api.Operation
import com.apollographql.apollo3.exception.ApolloException
import com.apollographql.apollo3.network.okHttpClient
import com.apollographql.apollo3.network.ws.SubscriptionWsProtocol
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import kotlin.Result.Companion.failure
import kotlin.Result.Companion.success

private class AuthorizationInterceptor(val token: String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request =
            chain.request().newBuilder().addHeader("Authorization", "Bearer $token").build()

        return chain.proceed(request)
    }
}

enum class Environment {
    Staging, Production
}

class Requests(token: String, env: Environment) {

    private val okHttpClient =
        OkHttpClient.Builder().addInterceptor(AuthorizationInterceptor(token)).build()

    val kronorApolloClient = ApolloClient.Builder().httpServerUrl(
        when (env) {
            Environment.Staging -> "https://staging.kronor.io/v1/graphql"
            Environment.Production -> "https://kronor.io/v1/graphql"
        }
    ).webSocketServerUrl(
        when (env) {
            Environment.Staging -> "wss://staging.kronor.io/v1/graphql"
            Environment.Production -> "wss://kronor.io/v1/graphql"
        }
    )
        .wsProtocol(SubscriptionWsProtocol.Factory(connectionPayload = { mapOf("headers" to (mapOf("Authorization" to "Bearer $token"))) }))
        .okHttpClient(okHttpClient).build()

}

data class ApiError(
    val errors: List<com.apollographql.apollo3.api.Error>, val extensions: Map<String, Any?>
)

sealed class KronorError : Throwable() {
    data class networkError(val e: ApolloException) : KronorError()

    data class graphQlError(val e: ApiError) : KronorError()
}

suspend fun <D : Operation.Data> ApolloCall<D>.executeMapKronorError(): Result<D> {
    return try {
        val response = this.execute()
        response.data?.let {
            success(it)
        } ?: failure(
            KronorError.graphQlError(
                ApiError(
                    response.errors ?: emptyList(), response.extensions
                )
            )
        )
    } catch (e: ApolloException) {
        failure(KronorError.networkError(e))
    }
}