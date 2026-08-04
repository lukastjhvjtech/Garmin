package com.example.garminbridge

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

data class SleepData(val totalSleepMinutes: Int, val sleepStartMillis: Long, val sleepEndMillis: Long, val deepSleepMinutes: Int, val lightSleepMinutes: Int, val remSleepMinutes: Int, val awakeMinutes: Int)
data class HeartRateData(val restingHeartRate: Int, val avgHeartRate: Int, val maxHeartRate: Int, val timestamp: Long)
data class SpO2Data(val avgSpO2: Float, val minSpO2: Float, val maxSpO2: Float, val timestamp: Long)
data class RespirationData(val avgRespiration: Float, val timestamp: Long)
data class FloorsData(val floorsClimbed: Int, val timestamp: Long)
data class CaloriesData(val totalCalories: Int, val activeCalories: Int, val timestamp: Long)

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
            Log.e(TAG, "Parse error", e)
            null
        }
    }

    private fun parseStepsObject(obj: JSONObject): Long? {
        listOf("totalSteps", "totalStepsInteger", "steps").forEach { field ->
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
            } else if (obj.has("steps")) {                val v = obj.opt("steps")
                if (v is Number) { total += v.toLong(); found = true }
            }
        }
        return if (found) total else null
    }

    fun extractSleep(json: String): SleepData? {
        return try {
            val trimmed = json.trim()
            if (!trimmed.startsWith("{")) return null
            val obj = JSONObject(trimmed)
            val total = obj.optLong("sleepingSeconds", 0) / 60L
            val deep = obj.optLong("deepSleepSeconds", 0) / 60L
            val light = obj.optLong("lightSleepSeconds", 0) / 60L
            val rem = obj.optLong("remSleepSeconds", 0) / 60L
            val awake = obj.optLong("awakeSeconds", 0) / 60L
            if (total <= 0) return null
            var start = System.currentTimeMillis() - (total * 60000L)
            var end = System.currentTimeMillis()
            try {
                val s = obj.optString("sleepStartTimestampGMT", "")
                val e = obj.optString("sleepEndTimestampGMT", "")
                if (s.isNotEmpty() && s.all { it.isDigit() }) {
                    start = s.toLong()
                    if (start < 1000000000000L) start *= 1000L
                }
                if (e.isNotEmpty() && e.all { it.isDigit() }) {
                    end = e.toLong()
                    if (end < 1000000000000L) end *= 1000L
                }
            } catch (e: Exception) { Log.w(TAG, "Parse time error", e) }
            SleepData(total.toInt(), start, end, deep.toInt(), light.toInt(), rem.toInt(), awake.toInt())
        } catch (e: Exception) { Log.e(TAG, "Sleep parse error", e); null }
    }

    fun extractHeartRate(json: String): HeartRateData? {
        return try {
            val trimmed = json.trim()
            if (!trimmed.startsWith("{")) return null
            val obj = JSONObject(trimmed)
            val resting = obj.optInt("restingHeartRate", 0)
            val avg = obj.optInt("avgHeartRate", resting)
            val max = obj.optInt("maxHeartRate", avg)
            if (resting <= 0 && avg <= 0) return null
            HeartRateData(if (resting > 0) resting else avg, avg, max, System.currentTimeMillis())
        } catch (e: Exception) { Log.e(TAG, "HR parse error", e); null }
    }

    fun extractSpO2(json: String): SpO2Data? {        return try {
            val trimmed = json.trim()
            if (!trimmed.startsWith("{")) return null
            val obj = JSONObject(trimmed)
            val avg = obj.optDouble("averageSPO2", 0.0).toFloat()
            val min = obj.optDouble("lowestSPO2", avg.toDouble()).toFloat()
            val max = obj.optDouble("highestSPO2", avg.toDouble()).toFloat()
            if (avg <= 0) return null
            SpO2Data(avg, min, max, System.currentTimeMillis())
        } catch (e: Exception) { Log.e(TAG, "SpO2 parse error", e); null }
    }

    fun extractRespiration(json: String): RespirationData? {
        return try {
            val trimmed = json.trim()
            if (!trimmed.startsWith("{")) return null
            val obj = JSONObject(trimmed)
            val avg = obj.optDouble("avgRespiration", 0.0).toFloat()
            if (avg <= 0) return null
            RespirationData(avg, System.currentTimeMillis())
        } catch (e: Exception) { Log.e(TAG, "Resp parse error", e); null }
    }

    fun extractFloors(json: String): FloorsData? {
        return try {
            val trimmed = json.trim()
            if (!trimmed.startsWith("{")) return null
            val obj = JSONObject(trimmed)
            val floors = obj.optInt("floorsClimbed", obj.optInt("floorsAscended", 0))
            if (floors <= 0) return null
            FloorsData(floors, System.currentTimeMillis())
        } catch (e: Exception) { Log.e(TAG, "Floors parse error", e); null }
    }

    fun extractCalories(json: String): CaloriesData? {
        return try {
            val trimmed = json.trim()
            if (!trimmed.startsWith("{")) return null
            val obj = JSONObject(trimmed)
            val total = obj.optInt("totalKilocalories", obj.optInt("totalCalories", 0))
            val active = obj.optInt("activeKilocalories", obj.optInt("activeCalories", 0))
            if (total <= 0) return null
            CaloriesData(total, active, System.currentTimeMillis())
        } catch (e: Exception) { Log.e(TAG, "Calories parse error", e); null }
    }
}