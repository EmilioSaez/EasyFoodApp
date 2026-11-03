package com.easyfood.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class OpenFoodFactsClientTest {
    private fun client(body: String): OpenFoodFactsClient {
        val engine = MockEngine {
            respond(body, headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
        }
        val http = HttpClient(engine) { install(ContentNegotiation) { json(OpenFoodFactsClient.lenientJson) } }
        return OpenFoodFactsClient(http)
    }

    @Test
    fun parsesFoundProduct() = runTest {
        val json = """{"status":1,"product":{"code":"8480000000001","product_name":"Leche entera","brands":"Hacendado,Otra",
            "nutriments":{"energy-kcal_100g":63,"proteins_100g":3.1,"carbohydrates_100g":4.7,"fat_100g":3.6}}}"""
        val result = client(json).findByBarcode("8480000000001")
        val product = assertIs<ProductLookup.Found>(result).product
        assertEquals("Leche entera", product.name)
        assertEquals("Hacendado", product.brand)
        assertEquals(63.0, product.nutrients.kcal)
        assertTrue(!product.nutrients.needsReview)
    }

    @Test
    fun missingProductIsNotFound() = runTest {
        assertEquals(ProductLookup.NotFound, client("""{"status":0}""").findByBarcode("123"))
    }

    @Test
    fun productWithoutKcalNeedsReview() = runTest {
        val result = client("""{"status":1,"product":{"product_name":"X","nutriments":{}}}""").findByBarcode("123")
        assertTrue(assertIs<ProductLookup.Found>(result).product.nutrients.needsReview)
    }

    @Test
    fun nonNumericBarcodeIsRejectedWithoutNetwork() = runTest {
        assertEquals(ProductLookup.NotFound, client("{}").findByBarcode("abc"))
    }
}
