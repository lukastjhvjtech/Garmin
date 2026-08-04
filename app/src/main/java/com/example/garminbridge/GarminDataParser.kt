package com.example.garminbridge

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

data class SleepData(
    val totalSleepMinutes: Int,
    val sleepStartMillis: Long,
    val sleepEndMillis: Long,
    val deepSleepMinutes: Int,
    val lightSleepMinutes: Int,
    val remSleepMinutes: Int,
    val awakeMinutes: Int
)

object GarminDataParser {

    private const val TAG = "GarminParser"

    fun extractSteps(json: String): Long? {
        return try {
            val trimmed = json.trim()
            when {
                trimmed.startsWith("{") -> parseStepsObject(JSONObject(trimmed))
                trimmed.startsWith("[") -> parseStepsArray(JSONArray(trimmed))
                else -> null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Parse-Fehler", e)
            null
        }
    }

    private fun parseStepsObject(obj: JSONObject): Long? {
        val candidates = listOf("totalSteps", "totalStepsInteger", "steps")
        for (field in candidates) {
            if (obj.has(field)) {
                val v = obj.optLong(field, -1)
                if (v >= 0) return v
            }
        }
        return null
    }

    private fun parseStepsArray(arr: JSONArray): Long? {
        if (arr.length() == 0) return null
        var total = 0L
        var found = false
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            if (obj.has("totalSteps")) {
                total += obj.optLong("totalSteps", 0)
                found = true
            } else if (obj.has("steps")) {
                when (val stepsVal = obj.opt("steps")) {
                    is Number -> {
                        total += stepsVal.toLong()
                        found = true
                    }
                    is JSONArray -> {
                        for (j in 0 until stepsVal.length()) {
                            total += stepsVal.optLong(j, 0)
                        }
                        found = true
                    }
                }
            }
        }
        return if (found) total else null
    }

    fun extractSleep(json: String): SleepData? {
        return try {
            val trimmed = json.trim()
            if (!trimmed.startsWith("{")) return null
            
            val obj = JSONObject(trimmed)
            
            // Garmin liefert verschiedene Felder - wir prüfen mehrere Möglichkeiten
            val totalSleep = obj.optLong("sleepingSeconds", 0) / 60
            val deepSleep = obj.optLong("deepSleepSeconds", 0) / 60
            val lightSleep = obj.optLong("lightSleepSeconds", 0) / 60
            val remSleep = obj.optLong("remSleepSeconds", 0) / 60
            val awakeTime = obj.optLong("awakeSeconds", 0) / 60
            
            // Alternativ: unmeasurableSleep, etc.
            val altTotal = obj.optLong("totalSleepDuration", 0) / 60
            
            val finalTotal = if (totalSleep > 0) totalSleep else altTotal.toInt()
            
            // Falls keine Schlafdaten vorhanden sind
            if (finalTotal <= 0) {
                Log.d(TAG, "Keine Schlafdaten gefunden")
                return null
            }
            
            // Schlafenszeiten berechnen - Garmin liefert oft Unix-Timestamps in Millisekunden
            // oder Datumsangaben im Format "YYYY-MM-DD HH:MM:SS"            val sleepStartStr = obj.optString("sleepStartTimestampGMT", "")
            val sleepEndStr = obj.optString("sleepEndTimestampGMT", "")
            
            var startMillis = System.currentTimeMillis() - (finalTotal * 60 * 1000)
            var endMillis = System.currentTimeMillis()
            
            try {
                if (sleepStartStr.isNotEmpty() && sleepStartStr.all { it.isDigit() }) {
                    startMillis = sleepStartStr.toLong()
                    if (startMillis < 1_000_000_000_000) startMillis *= 1000
                }
                if (sleepEndStr.isNotEmpty() && sleepEndStr.all { it.isDigit() }) {
                    endMillis = sleepEndStr.toLong()
                    if (endMillis < 1_000_000_000_000) endMillis *= 1000
                }
            } catch (e: Exception) {
                Log.w(TAG, "Konnte Schlafenszeiten nicht parsen, verwende Schätzung", e)
            }
            
            SleepData(
                totalSleepMinutes = finalTotal.toInt(),
                sleepStartMillis = startMillis,
                sleepEndMillis = endMillis,
                deepSleepMinutes = deepSleep.toInt(),
                lightSleepMinutes = lightSleep.toInt(),
                remSleepMinutes = remSleep.toInt(),
                awakeMinutes = awakeTime.toInt()
            ).also {
                Log.d(TAG, "Schlaf gefunden: $it")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Schlaf-Parse-Fehler", e)
            null
        }
    }
}