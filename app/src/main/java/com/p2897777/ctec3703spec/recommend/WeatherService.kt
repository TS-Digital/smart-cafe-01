package com.p2897777.ctec3703spec.recommend


import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

sealed class WeatherResult {
    data class Live(val weather: Weather) : WeatherResult()
    data class Cached(val weather: Weather, val reason: String) : WeatherResult()
    data class Unavailable(val reason: String) : WeatherResult()

    val weatherOrNull: Weather?
        get() = when (this) {
            is Live -> weather
            is Cached -> weather
            is Unavailable -> null
        }
}

/**
 * REST client for the Open-Meteo forecast API (https://open-meteo.com, free, no API key,
 * so there is no secret to leak in the repo).
 *
 * Privacy: we use the café's fixed coordinates (DMU, Leicester), NOT the user's location,
 * so the app needs no location permission and sends nothing about the user.
 *
 * Robustness: HTTPS only, short timeouts, HTTP status checks, response size cap, JSON validation,
 * and a 30-minute cache so the app still works offline and does not spam the API.
 */
class WeatherService(
    context: Context,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("weather_cache", Context.MODE_PRIVATE)

    fun current(forceRefresh: Boolean = false): WeatherResult {
        val cached = readCache()
        if (!forceRefresh && cached != null && clock() - cached.fetchedAt < CACHE_TTL_MS) {
            return WeatherResult.Live(cached)
        }
        if (!isOnline()) {
            return cached?.let { WeatherResult.Cached(it, "Offline, showing earlier weather") }
                ?: WeatherResult.Unavailable("Offline")
        }
        return try {
            val weather = WeatherParser.parse(download(), clock())
            writeCache(weather)
            WeatherResult.Live(weather)
        } catch (e: SocketTimeoutException) {
            fallback(cached, "Weather service timed out")
        } catch (e: IOException) {
            fallback(cached, "Could not reach weather service")
        } catch (e: WeatherParser.ParseException) {
            fallback(cached, "Weather service sent an unexpected response")
        }
    }

    private fun fallback(cached: Weather?, reason: String): WeatherResult =
        cached?.let { WeatherResult.Cached(it, reason) } ?: WeatherResult.Unavailable(reason)

    private fun download(): String {
        val conn = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            return conn.inputStream.bufferedReader().use { r ->
                val sb = StringBuilder()
                val buf = CharArray(2048)
                while (true) {
                    val n = r.read(buf)
                    if (n < 0) break
                    sb.append(buf, 0, n)
                    if (sb.length > MAX_RESPONSE_CHARS) throw IOException("Response too large")
                }
                if (sb.isEmpty()) throw IOException("Empty response")
                sb.toString()
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun isOnline(): Boolean {
        val cm = appContext.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun readCache(): Weather? {
        if (!prefs.contains("temp")) return null
        return Weather(
            prefs.getFloat("temp", 0f).toDouble(),
            runCatching { WeatherKind.valueOf(prefs.getString("kind", "") ?: "") }.getOrDefault(WeatherKind.UNKNOWN),
            prefs.getLong("at", 0L)
        )
    }

    private fun writeCache(w: Weather) {
        prefs.edit()
            .putFloat("temp", w.temperatureC.toFloat())
            .putString("kind", w.kind.name)
            .putLong("at", w.fetchedAt)
            .apply()
    }

    companion object {
        // DMU campus, Leicester
        private const val LAT = 52.6293
        private const val LON = -1.1395
        const val ENDPOINT =
            "https://api.open-meteo.com/v1/forecast?latitude=$LAT&longitude=$LON&current=temperature_2m,weather_code&timezone=Europe%2FLondon"
        private const val TIMEOUT_MS = 5_000
        private const val CACHE_TTL_MS = 30 * 60 * 1000L
        private const val MAX_RESPONSE_CHARS = 16_384
    }
}
