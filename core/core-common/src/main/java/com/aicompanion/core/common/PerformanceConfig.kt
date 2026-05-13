package com.aicompanion.core.common

object PerformanceConfig {
    // Live2D rendering
    const val LIVE2D_TARGET_FPS = 30
    const val LIVE2D_MAX_MODEL_SIZE_MB = 15
    const val LIVE2D_TEXTURE_MAX_SIZE = 2048
    const val LIVE2D_ENABLE_PHYSICS = false
    const val LIVE2D_MOTION_INTERPOLATION_MS = 10

    // Chat list
    const val MESSAGE_PAGE_SIZE = 30
    const val MESSAGE_PRELOAD_THRESHOLD = 10
    const val MESSAGE_CACHE_MAX_ITEMS = 200
    const val SSE_TOKEN_BATCH_MS = 48L

    // Memory
    const val IMAGE_CACHE_MAX_MB = 50
    const val MEMORY_CACHE_MAX_ENTRIES = 500
    const val VECTOR_CACHE_MAX_SIZE = 100

    // Network
    const val HTTP_CONNECTION_POOL_SIZE = 5
    const val HTTP_KEEP_ALIVE_MS = 300_000L
    const val SSE_RECONNECT_DELAY_MS = 1000L
    const val SSE_MAX_RECONNECT_ATTEMPTS = 3
}

class MemoryCache<K, V>(private val maxSize: Int) {
    private val cache = LinkedHashMap<K, V>(maxSize, 0.75f, true)

    @Synchronized
    fun get(key: K): V? = cache[key]

    @Synchronized
    fun put(key: K, value: V) {
        if (cache.size >= maxSize) {
            val eldest = cache.keys.firstOrNull()
            if (eldest != null) cache.remove(eldest)
        }
        cache[key] = value
    }

    @Synchronized
    fun remove(key: K): V? = cache.remove(key)

    @Synchronized
    fun clear() = cache.clear()

    @Synchronized
    fun size(): Int = cache.size
}

class LruBitmapCache(maxSizeMb: Int = 50) {
    private val maxSizeBytes = maxSizeMb * 1024L * 1024L
    private var currentSize = 0L
    private val cache = LinkedHashMap<String, android.graphics.Bitmap>(16, 0.75f, true)

    @Synchronized
    fun get(key: String): android.graphics.Bitmap? = cache[key]

    @Synchronized
    fun put(key: String, bitmap: android.graphics.Bitmap) {
        val bitmapSize = bitmap.rowBytes.toLong() * bitmap.height
        while (currentSize + bitmapSize > maxSizeBytes && cache.isNotEmpty()) {
            val eldest = cache.entries.first()
            cache.remove(eldest.key)
            currentSize -= eldest.value.rowBytes.toLong() * eldest.value.height
        }
        cache[key] = bitmap
        currentSize += bitmapSize
    }

    @Synchronized
    fun clear() {
        cache.values.forEach { it.recycle() }
        cache.clear()
        currentSize = 0
    }
}
