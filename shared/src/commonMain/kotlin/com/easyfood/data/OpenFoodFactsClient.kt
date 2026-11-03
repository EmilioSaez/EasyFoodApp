package com.easyfood.data

import com.easyfood.model.Nutrients
import com.easyfood.model.Product
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

sealed interface ProductLookup {
    data class Found(val product: Product) : ProductLookup
    data object NotFound : ProductLookup
    data class Failure(val cause: Throwable) : ProductLookup
}

/** Consulta de productos por código de barras (EAN) en Open Food Facts. */
class OpenFoodFactsClient(
    private val http: HttpClient = defaultHttpClient(),
    private val baseUrl: String = "https://world.openfoodfacts.org",
) {
    suspend fun findByBarcode(barcode: String): ProductLookup {
        val ean = barcode.trim()
        if (ean.isEmpty() || !ean.all { it.isDigit() }) return ProductLookup.NotFound
        return try {
            val response: OffResponse = http.get("$baseUrl/api/v2/product/$ean.json") {
                parameter("fields", "code,product_name,brands,image_url,nutriments")
                header(HttpHeaders.UserAgent, USER_AGENT)
            }.body()
            val product = response.product
            if (response.status != 1 || product == null) ProductLookup.NotFound
            else ProductLookup.Found(product.toDomain(fallbackBarcode = ean))
        } catch (e: Exception) {
            ProductLookup.Failure(e)
        }
    }

    companion object {
        const val USER_AGENT = "EasyFoodApp/0.1 (https://github.com/EmilioSaez/EasyFoodApp)"

        fun defaultHttpClient() = HttpClient {
            install(ContentNegotiation) { json(lenientJson) }
        }

        internal val lenientJson = Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }
    }
}

@Serializable
internal data class OffResponse(
    val status: Int = 0,
    val product: OffProduct? = null,
)

@Serializable
internal data class OffProduct(
    val code: String? = null,
    @SerialName("product_name") val name: String? = null,
    val brands: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    val nutriments: OffNutriments? = null,
) {
    fun toDomain(fallbackBarcode: String) = Product(
        barcode = code ?: fallbackBarcode,
        name = name?.takeIf { it.isNotBlank() } ?: "Producto sin nombre",
        brand = brands?.substringBefore(',')?.trim()?.takeIf { it.isNotEmpty() },
        imageUrl = imageUrl,
        nutrients = Nutrients(
            kcal = nutriments?.kcal,
            proteinG = nutriments?.proteins,
            carbsG = nutriments?.carbs,
            fatG = nutriments?.fat,
        ),
    )
}

@Serializable
internal data class OffNutriments(
    @SerialName("energy-kcal_100g") val kcal: Double? = null,
    @SerialName("proteins_100g") val proteins: Double? = null,
    @SerialName("carbohydrates_100g") val carbs: Double? = null,
    @SerialName("fat_100g") val fat: Double? = null,
)
