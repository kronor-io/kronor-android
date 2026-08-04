package io.kronor.api

import com.apollographql.apollo3.ApolloClient
import com.apollographql.apollo3.api.Optional
import com.apollographql.apollo3.exception.ApolloNetworkException
import io.kronor.api.type.AddSessionDeviceInformationInput
import io.kronor.api.type.CreditCardPaymentInput
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RequestsRetryTest {
    private lateinit var server: MockWebServer
    private lateinit var client: ApolloClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = ApolloClient.Builder()
            .httpServerUrl(server.url("/graphql").toString())
            .build()
    }

    @After
    fun tearDown() {
        client.close()
        server.shutdown()
    }

    @Test
    fun temporaryFailureRetriesWithTheSameIdempotencyKey() = runBlocking {
        server.enqueue(graphQlErrorResponse("TEMPORARY_FAILURE"))
        server.enqueue(graphQlErrorResponse("TEMPORARY_FAILURE"))
        server.enqueue(successResponse())

        val idempotencyKey = "logical-payment-attempt"
        var retryNotifications = 0
        val result = retryTransientErrors(
            delaysMillis = listOf(0L, 0L, 0L),
            onRetry = { retryNotifications += 1 }
        ) {
            client.mutation(creditCardMutation(idempotencyKey)).executeMapKronorError()
        }

        assertTrue(result.isSuccess)
        assertEquals("wait-token", result.getOrThrow().newCreditCardPayment.waitToken)
        assertEquals(3, server.requestCount)
        assertEquals(2, retryNotifications)
        repeat(3) {
            val requestBody = server.takeRequest().body.readUtf8()
            assertTrue(requestBody.contains("\"idempotencyKey\":\"$idempotencyKey\""))
        }
    }

    @Test
    fun fatalGraphQlFailureIsNotRetried() = runBlocking {
        server.enqueue(graphQlErrorResponse("VALIDATION_FAILURE"))
        server.enqueue(successResponse())

        val result = retryTransientErrors(listOf(0L, 0L)) {
            client.mutation(creditCardMutation("fatal-attempt")).executeMapKronorError()
        }

        assertTrue(result.isFailure)
        assertEquals(1, server.requestCount)
        assertFalse(result.exceptionOrNull()?.isRetryableKronorError() == true)
    }

    @Test
    fun subscriptionTransportFailureResubscribesUntilItRecovers() = runBlocking {
        var subscriptions = 0
        var retryNotifications = 0
        var recoveryNotifications = 0
        val status = flow {
            subscriptions += 1
            if (subscriptions < 3) {
                throw ApolloNetworkException("socket closed")
            }
            emit("paid")
        }.retryTransientErrors(
            delaysMillis = listOf(0L, 0L, 0L),
            onRetry = { retryNotifications += 1 },
            onRecovered = { recoveryNotifications += 1 }
        ).first()

        assertEquals("paid", status)
        assertEquals(3, subscriptions)
        assertEquals(2, retryNotifications)
        assertEquals(1, recoveryNotifications)
    }

    @Test
    fun successfulSubscriptionUpdateResetsTheConsecutiveFailureBudget() = runBlocking {
        var subscriptions = 0
        val statuses = flow {
            subscriptions += 1
            when (subscriptions) {
                1 -> throw ApolloNetworkException("socket closed")
                2 -> {
                    emit("processing")
                    throw ApolloNetworkException("socket closed again")
                }
                3 -> throw ApolloNetworkException("socket closed once more")
                else -> emit("paid")
            }
        }.retryTransientErrors(listOf(0L, 0L)).take(2).toList()

        assertEquals(listOf("processing", "paid"), statuses)
        assertEquals(4, subscriptions)
    }

    @Test
    fun subscriptionFailureStopsAfterTheRetryBudget() = runBlocking {
        var subscriptions = 0
        val result = runCatching {
            flow<String> {
                subscriptions += 1
                throw ApolloNetworkException("socket closed")
            }.retryTransientErrors(listOf(0L, 0L)).first()
        }

        assertTrue(result.exceptionOrNull() is ApolloNetworkException)
        assertEquals(3, subscriptions)
    }

    @Test
    fun retryBudgetIsBounded() = runBlocking {
        var attempts = 0
        val result: Result<Unit> = retryTransientErrors(listOf(0L, 0L, 0L, 0L)) {
            attempts += 1
            Result.failure(ApolloNetworkException("offline"))
        }

        assertTrue(result.isFailure)
        assertEquals(5, attempts)
    }

    private fun creditCardMutation(idempotencyKey: String) = CreditCardPaymentMutation(
        payment = CreditCardPaymentInput(
            idempotencyKey = idempotencyKey,
            returnUrl = "https://merchant.example/return",
            merchantReturnUrl = Optional.present("https://merchant.example/return")
        ),
        deviceInfo = AddSessionDeviceInformationInput(
            browserName = "test-app",
            browserVersion = "1.0",
            fingerprint = "fingerprint",
            osName = "android",
            osVersion = "15",
            userAgent = "kronor_android_sdk/test"
        )
    )

    private fun graphQlErrorResponse(type: String) = MockResponse()
        .setHeader("Content-Type", "application/json")
        .setBody(
            """{"errors":[{"message":"failed","extensions":{"type":"$type"}}]}"""
        )

    private fun successResponse() = MockResponse()
        .setHeader("Content-Type", "application/json")
        .setBody(
            """{"data":{"newCreditCardPayment":{"waitToken":"wait-token","gateway":"REEPAY"},"addSessionDeviceInformation":{"result":true}}}"""
        )
}
