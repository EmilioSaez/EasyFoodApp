package app.luxion.fitarchiveapp.data

import app.luxion.fitarchiveapp.model.Nutrients
import app.luxion.fitarchiveapp.model.Product
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

class ProductRepositoryTest {
    private val product = Product("123", "Leche", "Hacendado", null, Nutrients(63.0, 3.1, 4.7, 3.6))

    private class FakeRemote(var response: ProductLookup) : ProductRemoteSource {
        var calls = 0
        override suspend fun findByBarcode(barcode: String): ProductLookup {
            calls++
            return response
        }
    }

    private class FakeStore(var entry: CachedProduct? = null) : ProductLocalStore {
        override suspend fun find(barcode: String) = entry?.takeIf { it.product.barcode == barcode }
        override suspend fun save(product: Product, fetchedAtMillis: Long) {
            entry = CachedProduct(product, fetchedAtMillis)
        }
    }

    private val now = 100L * 24 * 60 * 60 * 1000
    private fun repo(remote: FakeRemote, store: FakeStore) =
        ProductRepository(remote, store, ttl = 30.days, nowMillis = { now })

    @Test
    fun cacheHitDoesNotCallNetwork() = runTest {
        val remote = FakeRemote(ProductLookup.NotFound)
        val result = repo(remote, FakeStore(CachedProduct(product, now - 1.days.inWholeMilliseconds))).getByBarcode("123")
        val found = assertIs<ProductResult.Found>(result)
        assertTrue(found.fromCache && !found.stale)
        assertEquals(0, remote.calls)
    }

    @Test
    fun cacheMissFetchesAndStores() = runTest {
        val remote = FakeRemote(ProductLookup.Found(product))
        val store = FakeStore()
        val found = assertIs<ProductResult.Found>(repo(remote, store).getByBarcode(" 123 "))
        assertTrue(!found.fromCache)
        assertEquals(CachedProduct(product, now), store.entry)
    }

    @Test
    fun expiredEntryIsRefreshed() = runTest {
        val fresh = product.copy(name = "Leche nueva")
        val remote = FakeRemote(ProductLookup.Found(fresh))
        val store = FakeStore(CachedProduct(product, now - 31.days.inWholeMilliseconds))
        val found = assertIs<ProductResult.Found>(repo(remote, store).getByBarcode("123"))
        assertEquals("Leche nueva", found.product.name)
        assertEquals(now, store.entry?.fetchedAtMillis)
    }

    @Test
    fun networkErrorWithoutCacheFails() = runTest {
        val boom = RuntimeException("sin red")
        val result = repo(FakeRemote(ProductLookup.Failure(boom)), FakeStore()).getByBarcode("123")
        assertEquals(boom, assertIs<ProductResult.Failure>(result).cause)
    }

    @Test
    fun networkErrorWithExpiredCacheReturnsStale() = runTest {
        val store = FakeStore(CachedProduct(product, now - 40.days.inWholeMilliseconds))
        val found = assertIs<ProductResult.Found>(
            repo(FakeRemote(ProductLookup.Failure(RuntimeException())), store).getByBarcode("123"),
        )
        assertTrue(found.fromCache && found.stale)
    }

    @Test
    fun unknownProductIsNotFoundAndNotCached() = runTest {
        val store = FakeStore()
        assertEquals(ProductResult.NotFound, repo(FakeRemote(ProductLookup.NotFound), store).getByBarcode("999"))
        assertEquals(null, store.entry)
    }
}
