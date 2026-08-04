package com.example.garminbridge

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.*
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Percentage
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class HealthConnectWriter(private val context: Context) {
    private val client = HealthConnectClient.getOrCreate(context)
    private val prefs = context.getSharedPreferences("garmin_bridge", Context.MODE_PRIVATE)

    suspend fun writeSteps(total: Long) {
        val key = "last_steps_" + LocalDate.now().toString()
        val last = prefs.getLong(key, 0L)
        val delta = total - last
        if (delta <= 0) return
        val now = Instant.now()
        val offset = ZoneId.systemDefault().rules.getOffset(now)
        val start = now.minusSeconds(60)
        client.insertRecords(listOf(StepsRecord(delta, start, now, offset, offset)))
        prefs.edit().putLong(key, total).apply()
        Log.d("GarminBridge", "Steps: $delta")
    }

    suspend fun writeSleep(data: SleepData) {
        val key = "last_sleep_" + LocalDate.now().toString()
        if (prefs.getBoolean(key, false)) return
        val start = Instant.ofEpochMilli(data.sleepStartMillis)
        val end = Instant.ofEpochMilli(data.sleepEndMillis)
        val zone = ZoneId.systemDefault()
        val stages = mutableListOf<SleepSessionRecord.Stage>()
        var current = start.toEpochMilli()
        if (data.lightSleepMinutes > 0) {
            val stageEnd = current + (data.lightSleepMinutes * 60000L)
            stages.add(SleepSessionRecord.Stage(Instant.ofEpochMilli(current), Instant.ofEpochMilli(stageEnd), SleepSessionRecord.STAGE_TYPE_LIGHT))
            current = stageEnd
        }
        if (data.deepSleepMinutes > 0) {
            val stageEnd = current + (data.deepSleepMinutes * 60000L)
            stages.add(SleepSessionRecord.Stage(Instant.ofEpochMilli(current), Instant.ofEpochMilli(stageEnd), SleepSessionRecord.STAGE_TYPE_DEEP))
            current = stageEnd
        }
        if (data.remSleepMinutes > 0) {
            val stageEnd = current + (data.remSleepMinutes * 60000L)
            stages.add(SleepSessionRecord.Stage(Instant.ofEpochMilli(current), Instant.ofEpochMilli(stageEnd), SleepSessionRecord.STAGE_TYPE_REM))            current = stageEnd
        }
        if (data.awakeMinutes > 0) {
            val stageEnd = current + (data.awakeMinutes * 60000L)
            stages.add(SleepSessionRecord.Stage(Instant.ofEpochMilli(current), Instant.ofEpochMilli(stageEnd), SleepSessionRecord.STAGE_TYPE_AWAKE))
        }
        client.insertRecords(listOf(SleepSessionRecord(start, end, zone.rules.getOffset(start), zone.rules.getOffset(end), stages)))
        prefs.edit().putBoolean(key, true).apply()
        Log.d("GarminBridge", "Sleep: ${data.totalSleepMinutes} min")
    }

    suspend fun writeHeartRate(data: HeartRateData) {
        val now = Instant.now()
        val offset = ZoneId.systemDefault().rules.getOffset(now)
        client.insertRecords(listOf(RestingHeartRateRecord(now, offset, data.restingHeartRate.toLong())))
        Log.d("GarminBridge", "HR: ${data.restingHeartRate}")
    }

    suspend fun writeSpO2(data: SpO2Data) {
        val now = Instant.now()
        val offset = ZoneId.systemDefault().rules.getOffset(now)
        client.insertRecords(listOf(OxygenSaturationRecord(now, offset, Percentage(data.avgSpO2.toDouble() / 100.0))))
        Log.d("GarminBridge", "SpO2: ${data.avgSpO2}")
    }

    suspend fun writeRespiration(data: RespirationData) {
        val now = Instant.now()
        val offset = ZoneId.systemDefault().rules.getOffset(now)
        client.insertRecords(listOf(RespiratoryRateRecord(now, offset, data.avgRespiration.toDouble())))
        Log.d("GarminBridge", "Resp: ${data.avgRespiration}")
    }

    suspend fun writeFloors(data: FloorsData) {
        val now = Instant.now()
        val offset = ZoneId.systemDefault().rules.getOffset(now)
        client.insertRecords(listOf(FloorsClimbedRecord(now.minusSeconds(60), now, offset, offset, data.floorsClimbed.toDouble())))
        Log.d("GarminBridge", "Floors: ${data.floorsClimbed}")
    }

    suspend fun writeCalories(data: CaloriesData) {
        val key = "last_calories_" + LocalDate.now().toString()
        val last = prefs.getLong(key, 0L)
        val delta = data.totalCalories.toLong() - last
        if (delta <= 0) return
        val now = Instant.now()
        val offset = ZoneId.systemDefault().rules.getOffset(now)
        client.insertRecords(listOf(TotalCaloriesBurnedRecord(now.minusSeconds(60), now, offset, offset, Energy.kilocalories(delta.toDouble()))))
        prefs.edit().putLong(key, data.totalCalories.toLong()).apply()
        Log.d("GarminBridge", "Calories: $delta")
    }}