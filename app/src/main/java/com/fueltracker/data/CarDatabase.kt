package com.fueltracker.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlin.coroutines.cancellation.CancellationException

class CarDatabase private constructor(private val appContext: Context) {

    companion object {
        private const val ASSET = "car_brands.json"

        @Volatile private var INSTANCE: CarDatabase? = null

        fun get(context: Context): CarDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: CarDatabase(context.applicationContext).also { INSTANCE = it }
            }
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Volatile private var catalogCache: CarCatalog? = null
    private val loadMutex = Mutex()

    /** 幂等加载：并发调用只会解析一次 */
    suspend fun ensureCatalogLoaded(): CarCatalog? = withContext(Dispatchers.IO) {
        catalogCache?.let { return@withContext it }
        loadMutex.withLock {
            catalogCache?.let { return@withLock it }
            try {
                val text = appContext.assets.open(ASSET)
                    .bufferedReader().use { it.readText() }
                val c = json.decodeFromString(CarCatalog.serializer(), text)
                Log.d("CarDatabase",
                    "catalog 解析: 品牌 ${c.tree.size}, 车系 ${c.tree.values.sumOf { it.size }}")
                catalogCache = c
                c
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.e("CarDatabase", "解析 $ASSET 失败: ${e.message}", e)
                null
            }
        }
    }

    // ---------- 4 级同步查询（必须先 ensureCatalogLoaded）----------

    fun brandsByLetter(): Map<String, List<String>> =
        catalogCache?.brandsByLetter ?: emptyMap()

    fun series(brand: String): List<String> =
        catalogCache?.tree?.get(brand)?.keys?.toList() ?: emptyList()

    fun years(brand: String, series: String): List<String> =
        catalogCache?.tree?.get(brand)?.get(series)?.years ?: emptyList()

    fun trims(brand: String, series: String, year: String): List<Trim> =
        catalogCache?.tree?.get(brand)?.get(series)?.trims?.get(year) ?: emptyList()

}