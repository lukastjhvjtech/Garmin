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

data class HeartRateData(
    val restingHeartRate: Int,
    val avgHeartRate: Int,
    val maxHeartRate: Int,
    val timestamp: Long
)

data class StressData(
    val avgStress: Int,
    val stressLevel: String,
    val timestamp: Long
)

data class SpO2Data(
    val avgSpO2: Float,
    val minSpO2: Float,
    val maxSpO2: Float,
    val timestamp: Long
)

data class RespirationData(
    val avgRespiration: Float,
    val timestamp: Long
)

data class FloorsData(
    val floorsClimbed: Int,
    val timestamp: Long
)

data class CaloriesData(
    val totalCalories: Int,
    val activeCalories: Int,
    val timestamp: Long)

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
                            total += stepsVal.optLong(j, 0)                        }
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
            
            val totalSleep = obj.optLong("sleepingSeconds", 0) / 60
            val deepSleep = obj.optLong("deepSleepSeconds", 0) / 60
            val lightSleep = obj.optLong("lightSleepSeconds", 0) / 60
            val remSleep = obj.optLong("remSleepSeconds", 0) / 60
            val awakeTime = obj.optLong("awakeSeconds", 0) / 60
            val altTotal = obj.optLong("totalSleepDuration", 0) / 60
            
            val finalTotal = if (totalSleep > 0) totalSleep else altTotal.toInt()
            
            if (finalTotal <= 0) return null
            
            val sleepStartStr = obj.optString("sleepStartTimestampGMT", "")
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
                Log.w(TAG, "Konnte Schlafenszeiten nicht parsen", e)
            }
            
            SleepData(
                totalSleepMinutes = finalTotal.toInt(),
                sleepStartMillis = startMillis,
                sleepEndMillis = endMillis,                deepSleepMinutes = deepSleep.toInt(),
                lightSleepMinutes = lightSleep.toInt(),
                remSleepMinutes = remSleep.toInt(),
                awakeMinutes = awakeTime.toInt()
            ).also { Log.d(TAG, "Schlaf: $it") }
        } catch (e: Exception) {
            Log.e(TAG, "Schlaf-Parse-Fehler", e)
            null
        }
    }

    fun extractHeartRate(json: String): HeartRateData? {
        return try {
            val trimmed = json.trim()
            if (!trimmed.startsWith("{")) return null
            
            val obj = JSONObject(trimmed)
            val restingHR = obj.optInt("restingHeartRate", 0)
            val avgHR = obj.optInt("avgHeartRate", restingHR)
            val maxHR = obj.optInt("maxHeartRate", avgHR)
            
            if (restingHR <= 0 && avgHR <= 0) return null
            
            HeartRateData(
                restingHeartRate = if (restingHR > 0) restingHR else avgHR,
                avgHeartRate = avgHR,
                maxHeartRate = maxHR,
                timestamp = System.currentTimeMillis()
            ).also { Log.d(TAG, "Herzfrequenz: $it") }
        } catch (e: Exception) {
            Log.e(TAG, "HR-Parse-Fehler", e)
            null
        }
    }

    fun extractStress(json: String): StressData? {
        return try {
            val trimmed = json.trim()
            if (!trimmed.startsWith("{")) return null
            
            val obj = JSONObject(trimmed)
            val avgStress = obj.optInt("averageStressLevel", 0)
            val stressLevel = obj.optString("stressQualifier", "UNKNOWN")
            
            if (avgStress <= 0) return null
            
            StressData(
                avgStress = avgStress,
                stressLevel = stressLevel,
                timestamp = System.currentTimeMillis()            ).also { Log.d(TAG, "Stress: $it") }
        } catch (e: Exception) {
            Log.e(TAG, "Stress-Parse-Fehler", e)
            null
        }
    }

    fun extractSpO2(json: String): SpO2Data? {
        return try {
            val trimmed = json.trim()
            if (!trimmed.startsWith("{")) return null
            
            val obj = JSONObject(trimmed)
            val avg = obj.optDouble("averageSPO2", 0.0).toFloat()
            val min = obj.optDouble("lowestSPO2", avg.toDouble()).toFloat()
            val max = obj.optDouble("highestSPO2", avg.toDouble()).toFloat()
            
            if (avg <= 0) return null
            
            SpO2Data(
                avgSpO2 = avg,
                minSpO2 = min,
                maxSpO2 = max,
                timestamp = System.currentTimeMillis()
            ).also { Log.d(TAG, "SpO2: $it") }
        } catch (e: Exception) {
            Log.e(TAG, "SpO2-Parse-Fehler", e)
            null
        }
    }

    fun extractRespiration(json: String): RespirationData? {
        return try {
            val trimmed = json.trim()
            if (!trimmed.startsWith("{")) return null
            
            val obj = JSONObject(trimmed)
            val avg = obj.optDouble("avgRespiration", 0.0).toFloat()
            
            if (avg <= 0) return null
            
            RespirationData(
                avgRespiration = avg,
                timestamp = System.currentTimeMillis()
            ).also { Log.d(TAG, "Atmung: $it") }
        } catch (e: Exception) {
            Log.e(TAG, "Respiration-Parse-Fehler", e)
            null
        }
    }
    fun extractFloors(json: String): FloorsData? {
        return try {
            val trimmed = json.trim()
            if (!trimmed.startsWith("{")) return null
            
            val obj = JSONObject(trimmed)
            val floors = obj.optInt("floorsClimbed", obj.optInt("floorsAscended", 0))
            
            if (floors <= 0) return null
            
            FloorsData(
                floorsClimbed = floors,
                timestamp = System.currentTimeMillis()
            ).also { Log.d(TAG, "Stockwerke: $it") }
        } catch (e: Exception) {
            Log.e(TAG, "Floors-Parse-Fehler", e)
            null
        }
    }

    fun extractCalories(json: String): CaloriesData? {
        return try {
            val trimmed = json.trim()
            if (!trimmed.startsWith("{")) return null
            
            val obj = JSONObject(trimmed)
            val total = obj.optInt("totalKilocalories", obj.optInt("totalCalories", 0))
            val active = obj.optInt("activeKilocalories", obj.optInt("activeCalories", 0))
            
            if (total <= 0) return null
            
            CaloriesData(
                totalCalories = total,
                activeCalories = active,
                timestamp = System.currentTimeMillis()
            ).also { Log.d(TAG, "Kalorien: $it") }
        } catch (e: Exception) {
            Log.e(TAG, "Calories-Parse-Fehler", e)
            null
        }
    }
}