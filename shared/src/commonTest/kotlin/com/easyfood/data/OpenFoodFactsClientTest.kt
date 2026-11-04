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
import kotlin.test.assertNull
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

    @Test
    fun mapsFullMacrosAndServing() = runTest {
        val json = """{"status":1,"product":{"code":"1","product_name":"Galletas","serving_size":"30 g","serving_quantity":30,
            "nutriments":{"energy-kcal_100g":480,"proteins_100g":6,"carbohydrates_100g":70,"fat_100g":20,
            "saturated-fat_100g":9.5,"sugars_100g":25,"fiber_100g":3.2,"salt_100g":0.8}}}"""
        val p = assertIs<ProductLookup.Found>(client(json).findByBarcode("1")).product
        assertEquals(9.5, p.nutrients.saturatedFatG)
        assertEquals(25.0, p.nutrients.sugarsG)
        assertEquals(3.2, p.nutrients.fiberG)
        assertEquals(0.8, p.nutrients.saltG)
        assertEquals(30.0, p.servingSizeG)
        assertEquals("30 g", p.servingLabel)
    }

    @Test
    fun missingMacrosAndServingAreNull() = runTest {
        val json = """{"status":1,"product":{"product_name":"X","nutriments":{"energy-kcal_100g":100}}}"""
        val p = assertIs<ProductLookup.Found>(client(json).findByBarcode("1")).product
        assertNull(p.nutrients.saturatedFatG)
        assertNull(p.nutrients.sugarsG)
        assertNull(p.nutrients.fiberG)
        assertNull(p.nutrients.saltG)
        assertNull(p.servingSizeG)
        assertNull(p.servingLabel)
    }

    @Test
    fun servingQuantityAsStringIsAccepted() = runTest {
        val json = """{"status":1,"product":{"product_name":"X","serving_quantity":"25.5"}}"""
        val p = assertIs<ProductLookup.Found>(client(json).findByBarcode("1")).product
        assertEquals(25.5, p.servingSizeG)
    }

    @Test
    fun servingGramsFallsBackToParsingLabel() = runTest {
        val json = """{"status":1,"product":{"product_name":"X","serving_size":"125,5 ml"}}"""
        val p = assertIs<ProductLookup.Found>(client(json).findByBarcode("1")).product
        assertEquals(125.5, p.servingSizeG)
    }

    @Test
    fun unparseableServingKeepsLabelOnly() = runTest {
        val json = """{"status":1,"product":{"product_name":"X","serving_size":"1 loncha"}}"""
        val p = assertIs<ProductLookup.Found>(client(json).findByBarcode("1")).product
        assertNull(p.servingSizeG)
        assertEquals("1 loncha", p.servingLabel)
    }
}
