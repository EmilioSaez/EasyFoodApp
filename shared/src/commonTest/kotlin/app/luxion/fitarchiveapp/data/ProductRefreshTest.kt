package app.luxion.fitarchiveapp.data

import app.luxion.fitarchiveapp.model.Nutrients
import app.luxion.fitarchiveapp.model.Product
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

class ProductRefreshTest {
    private val product = Product("123", "Leche", "Hacendado", null, Nutrients(63.0, 3.1, 4.7, 3.6))
    private val moreKcal = product.copy(nutrients = Nutrients(80.0, 3.1, 4.7, 3.6))
    private val now = 100L * 24 * 60 * 60 * 1000

    private class FakeRemote(var response: ProductLookup) : ProductRemoteSource {
        var calls = 0
        override suspend fun findByBarcode(barcode: String): ProductLookup {
            calls++
            return response
        }
    }

    private class FakeStore(var entry: CachedProduct?) : ProductLocalStore {
        var savedChange: String? = null
        var checkedAt: Long? = null
        var candidates: List<String> = emptyList()

        override suspend fun find(barcode: String) = entry?.takeIf { it.product.barcode == barcode }
        override suspend fun save(product: Product, fetchedAtMillis: Long) {
            entry = CachedProduct(product, fetchedAtMillis)
        }
        override suspend fun saveRefreshed(product: Product, nowMillis: Long, changeSummary: String?): Boolean {
            entry = CachedProduct(product, nowMillis)
            savedChange = changeSummary
            return true
        }
        override suspend fun markChecked(barcode: String, nowMillis: Long) {
            checkedAt = nowMillis
        }
        override suspend fun refreshCandidates(staleBeforeMillis: Long, limit: Int) = candidates.take(limit)
    }

    private fun repo(remote: FakeRemote, store: FakeStore) =
        ProductRepository(remote, store, ttl = 30.days, nowMillis = { now })

    private fun cachedTwoDaysAgo() = FakeStore(CachedProduct(product, now - 2.days.inWholeMilliseconds))

    @Test
    fun refreshIgnoresTtlAndReportsRelevantChange() = runTest {
        val remote = FakeRemote(ProductLookup.Found(moreKcal))
        val store = cachedTwoDaysAgo()
        val result = assertIs<RefreshResult.Updated>(repo(remote, store).refresh("123"))
        assertEquals("Energía 63 → 80 kcal", result.change)
        assertEquals(moreKcal, store.entry?.product)
        assertEquals("Energía 63 → 80 kcal", store.savedChange)
        assertEquals(1, remote.calls)
    }

    @Test
    fun refreshWithoutRelevantChangeReportsNone() = runTest {
        val store = cachedTwoDaysAgo()
        val renamed = product.copy(name = "Leche 1 L")
        val result = assertIs<RefreshResult.Updated>(repo(FakeRemote(ProductLookup.Found(renamed)), store).refresh("123"))
        assertEquals(null, result.change)
        assertEquals("Leche 1 L", store.entry?.product?.name)
    }

    @Test
    fun refreshRejectsWorseDataAndKeepsTheLocalCopy() = runTest {
        val broken = product.copy(nutrients = Nutrients(null, null, null, null))
        val store = cachedTwoDaysAgo()
        assertEquals(RefreshResult.Rejected, repo(FakeRemote(ProductLookup.Found(broken)), store).refresh("123"))
        assertEquals(product, store.entry?.product)
        assertEquals(now, store.checkedAt)
    }

    @Test
    fun refreshOfProductGoneFromOffKeepsTheLocalCopy() = runTest {
        val store = cachedTwoDaysAgo()
        assertEquals(RefreshResult.NotFound, repo(FakeRemote(ProductLookup.NotFound), store).refresh("123"))
        assertEquals(product, store.entry?.product)
        assertEquals(now, store.checkedAt)
    }

    @Test
    fun refreshWithoutNetworkFailsWithoutTouchingTheCopy() = runTest {
        val store = cachedTwoDaysAgo()
        val before = store.entry
        assertIs<RefreshResult.Failure>(repo(FakeRemote(ProductLookup.Failure(RuntimeException())), store).refresh("123"))
        assertEquals(before, store.entry)
    }

    @Test
    fun refreshSkipsGenericsCustomsAndUncachedProducts() = runTest {
        val remote = FakeRemote(ProductLookup.Found(product))
        val store = FakeStore(CachedProduct(product.copy(barcode = "generic:1"), 0))
        val repo = repo(remote, store)
        assertEquals(RefreshResult.Skipped, repo.refresh("generic:1"))
        assertEquals(RefreshResult.Skipped, repo.refresh("custom:2"))
        assertEquals(RefreshResult.Skipped, repo.refresh("999")) // no está en la caché
        assertEquals(0, remote.calls)
    }

    @Test
    fun expiredLazyRefreshAlsoReportsTheChange() = runTest {
        val store = FakeStore(CachedProduct(product, now - 31.days.inWholeMilliseconds))
        val found = assertIs<ProductResult.Found>(repo(FakeRemote(ProductLookup.Found(moreKcal)), store).getByBarcode("123"))
        assertEquals("Energía 63 → 80 kcal", found.changeSummary)
        assertEquals(now, found.changedAtMillis)
    }

    @Test
    fun refresherChecksCandidatesUpToTheLimit() = runTest {
        val store = cachedTwoDaysAgo().apply { candidates = listOf("123", "123", "123") }
        val remote = FakeRemote(ProductLookup.Found(moreKcal))
        val run = ProductRefresher(repo(remote, store), maxPerRun = 2).run()
        assertEquals(2, run.checked)
        assertEquals(2, remote.calls)
        assertEquals("123" to "Energía 63 → 80 kcal", run.changed.first())
        assertTrue(!run.offline)
    }

    @Test
    fun refresherStopsAtTheFirstNetworkFailure() = runTest {
        val store = cachedTwoDaysAgo().apply { candidates = listOf("123", "123", "123") }
        val remote = FakeRemote(ProductLookup.Failure(RuntimeException("sin red")))
        val run = ProductRefresher(repo(remote, store)).run()
        assertTrue(run.offline)
        assertEquals(0, run.checked)
        assertEquals(1, remote.calls)
    }
}
