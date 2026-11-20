package app.luxion.easyfoodapp.data

import app.luxion.easyfoodapp.db.FavoriteFoodQueries
import app.luxion.easyfoodapp.model.Product
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** [FavoriteStore] respaldado por la tabla `FavoriteFoodEntity` de SQLDelight. */
class SqlDelightFavoriteStore(
    private val queries: FavoriteFoodQueries,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : FavoriteStore {
    override suspend fun keys(): List<String> = withContext(dispatcher) {
        queries.selectKeys().executeAsList()
    }

    override suspend fun products(): List<Product> = withContext(dispatcher) {
        queries.selectProducts().executeAsList().map { it.toCached().product }
    }

    override suspend fun setFavorite(key: String, favorite: Boolean, nowMillis: Long): Unit = withContext(dispatcher) {
        require(key.isNotBlank()) { "La clave del favorito no puede estar vacía" }
        if (favorite) queries.insert(key, nowMillis) else queries.deleteByKey(key)
    }
}
