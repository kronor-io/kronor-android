package com.kronor.payment_sdk

import android.content.Context
import com.apollographql.apollo3.ApolloClient
import com.apollographql.apollo3.network.okHttpClient
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response

private class AuthorizationInterceptor(val context: Context): Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .addHeader("Authorization", "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJleHAiOiAxNjgxNTY2NjU5LjcxNTA1NSwgImlhdCI6IDE2NzQwNDk4NTkuNzE1MDU1LCAidGlkIjogIjk1ZDc0MzBlLTIzNGItNDlkMi04MzUwLWU5YWViNTVkYTgxOSIsICJ0bmFtZSI6ICJtcnB1IHRlc3QgdG9rZW4iLCAidHR5cGUiOiAiYmFja2VuZCIsICJhc3NvY2lhdGVkX2VtYWlsIjogIm1yaW5hbEBrcm9ub3IuaW8iLCAiaHR0cHM6Ly9oYXN1cmEuaW8vand0L2NsYWltcyI6IHsieC1oYXN1cmEtdXNlciI6ICJtcmluYWxAa3Jvbm9yLmlvIiwgIngtaGFzdXJhLW1lcmNoYW50LWlkIjogIjIiLCAieC1oYXN1cmEtZGVmYXVsdC1yb2xlIjogIm1lcmNoYW50LWJhY2tlbmQiLCAieC1oYXN1cmEtYWxsb3dlZC1yb2xlcyI6IFsibWVyY2hhbnQtYmFja2VuZCJdfX0.lt7nCcwMIkao59D7H3x9xx28jI2vckB5ACyZ-YP5mRU")
            .build()

        return chain.proceed(request)
    }
}

private var instance: ApolloClient? = null

fun apolloClient(context: Context): ApolloClient {
    if (instance != null) {
        return instance!!
    }

    val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(AuthorizationInterceptor(context))
        .build()

    instance = ApolloClient.Builder()
        .serverUrl("https://staging.kronor.io/v1/graphql")
        .okHttpClient(okHttpClient)
        .build()

    return instance!!
}