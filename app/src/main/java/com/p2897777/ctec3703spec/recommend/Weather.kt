package com.p2897777.ctec3703spec.recommend

import org.json.JSONException
import org.json.JSONObject

enum class WeatherKind { CLEAR, CLOUDY, FOG, RAIN, SNOW, STORM, UNKNOWN }

data class Weather(
    val temperatureC: Double,
    val kind: WeatherKind,
    val fetchedAt: Long
) {
    val isCold: Boolean get() = temperatureC < 12.0
    val isWarm: Boolean get() = temperatureC >= 20.0
    val isWet: Boolean get() = kind == WeatherKind.RAIN || kind == WeatherKind.SNOW || kind == WeatherKind.STORM

    fun summary(): String {
        val t = String.format(java.util.Locale.UK, "%.0f°C", temperatureC)
        val k = when (kind) {
            WeatherKind.CLEAR -> "clear skies"
            WeatherKind.CLOUDY -> "cloudy"
            WeatherKind.FOG -> "foggy"
            WeatherKind.RAIN -> "rainy"
            WeatherKind.SNOW -> "snowy"
            WeatherKind.STORM -> "stormy"
            WeatherKind.UNKNOWN -> "mixed weather"
        }
        return "$t and $k in Leicester"
    }
}

/**
 * Parses the Open-Meteo "current" response. Kept separate from networking so it can be
 * unit tested with sample JSON, including malformed and incomplete responses.
 *
 * Example: {"current":{"time":"2026-11-03T21:00","temperature_2m":8.4,"weather_code":61}}
 */
object WeatherParser {

    class ParseException(message: String) : Exception(message)

    fun parse(json: String, now: Long): Weather {
        try {
            val current = JSONObject(json).getJSONObject("current")
            val temp = current.getDouble("temperature_2m")
            if (temp.isNaN() || temp < -60 || temp > 60) throw ParseException("Temperature out of range: $temp")
            val code = current.optInt("weather_code", -1)
            return Weather(temp, kindFor(code), now)
        } catch (e: JSONException) {
            throw ParseException("Unexpected weather response: ${e.message}")
        }
    }

    /** WMO weather interpretation codes used by Open-Meteo. */
    fun kindFor(code: Int): WeatherKind = when (code) {
        0 -> WeatherKind.CLEAR
        1, 2, 3 -> WeatherKind.CLOUDY
        45, 48 -> WeatherKind.FOG
        in 51..67, in 80..82 -> WeatherKind.RAIN
        in 71..77, 85, 86 -> WeatherKind.SNOW
        in 95..99 -> WeatherKind.STORM
        else -> WeatherKind.UNKNOWN
    }
}
