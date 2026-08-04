package com.example.garminbridge

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.FloorsClimbedRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Percentage
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class HealthConnectWriter(private val context: Context) {
    private val client: HealthConnectClient by lazy {
        HealthConnectClient.getOrCreate(context)
    }
    private val prefs = context.getSharedPreferences("garmin_bridge", Context.MODE_PRIVATE)

    suspend fun writeSteps(currentTotal: Long) {
        if (HealthConnectClient.getSdkStatus(context) != HealthConnectClient.SDK_AVAILABLE) {
            throw IllegalStateException("Health Connect nicht verfügbar")
        }
        val todayKey = "last_steps_" + LocalDate.now().toString()
        val lastTotal = prefs.getLong(todayKey, 0L)
        val delta = currentTotal - lastTotal
        if (delta <= 0) {
            Log.d("GarminBridge", "Keine neuen Schritte")
            return
        }
        val now = Instant.now()
        val zone = ZoneId.systemDefault()
        val zoneOffset = zone.rules.getOffset(now)
        val lastWriteMillis = prefs.getLong("last_write_millis", now.toEpochMilli() - 3600000)
        var startTime = Instant.ofEpochMilli(lastWriteMillis)
        if (!startTime.isBefore(now)) startTime = now.minusSeconds(60)
        val record = StepsRecord(
            count = delta,
            startTime = startTime,
            endTime = now,
            startZoneOffset = zoneOffset,
            endZoneOffset = zoneOffset
        )
        client.insertRecords(listOf(record))
        prefs.edit().putLong(todayKey, currentTotal).putLong("last_write_millis", now.toEpochMilli()).apply()        Log.d("GarminBridge", "Delta $delta Schritte geschrieben")
    }

    suspend fun writeSleep(sleepData: SleepData) {
        if (HealthConnectClient.getSdkStatus(context) != HealthConnectClient.SDK_AVAILABLE) {
            throw IllegalStateException("Health Connect nicht verfügbar")
        }
        val todayKey = "last_sleep_" + LocalDate.now().toString()
        if (prefs.getBoolean(todayKey, false)) {
            Log.d("GarminBridge", "Schlaf schon geschrieben")
            return
        }
        val startInstant = Instant.ofEpochMilli(sleepData.sleepStartMillis)
        val endInstant = Instant.ofEpochMilli(sleepData.sleepEndMillis)
        val zone = ZoneId.systemDefault()
        val startOffset = zone.rules.getOffset(startInstant)
        val endOffset = zone.rules.getOffset(endInstant)
        val stages = mutableListOf<SleepSessionRecord.Stage>()
        var currentMillis = startInstant.toEpochMilli()
        if (sleepData.lightSleepMinutes > 0) {
            val stageEnd = currentMillis + (sleepData.lightSleepMinutes * 60L * 1000L)
            stages.add(SleepSessionRecord.Stage(Instant.ofEpochMilli(currentMillis), Instant.ofEpochMilli(stageEnd), SleepSessionRecord.STAGE_TYPE_LIGHT))
            currentMillis = stageEnd
        }
        if (sleepData.deepSleepMinutes > 0) {
            val stageEnd = currentMillis + (sleepData.deepSleepMinutes * 60L * 1000L)
            stages.add(SleepSessionRecord.Stage(Instant.ofEpochMilli(currentMillis), Instant.ofEpochMilli(stageEnd), SleepSessionRecord.STAGE_TYPE_DEEP))
            currentMillis = stageEnd
        }
        if (sleepData.remSleepMinutes > 0) {
            val stageEnd = currentMillis + (sleepData.remSleepMinutes * 60L * 1000L)
            stages.add(SleepSessionRecord.Stage(Instant.ofEpochMilli(currentMillis), Instant.ofEpochMilli(stageEnd), SleepSessionRecord.STAGE_TYPE_REM))
            currentMillis = stageEnd
        }
        if (sleepData.awakeMinutes > 0) {
            val stageEnd = currentMillis + (sleepData.awakeMinutes * 60L * 1000L)
            stages.add(SleepSessionRecord.Stage(Instant.ofEpochMilli(currentMillis), Instant.ofEpochMilli(stageEnd), SleepSessionRecord.STAGE_TYPE_AWAKE))
        }
        val record = SleepSessionRecord(
            startTime = startInstant,
            endTime = endInstant,
            startZoneOffset = startOffset,
            endZoneOffset = endOffset,
            stages = stages
        )
        client.insertRecords(listOf(record))
        prefs.edit().putBoolean(todayKey, true).apply()
        Log.d("GarminBridge", "Schlaf geschrieben: ${sleepData.totalSleepMinutes} Min.")
    }
    suspend fun writeHeartRate(data: HeartRateData) {
        val now = Instant.now()
        val zone = ZoneId.systemDefault()
        val zoneOffset = zone.rules.getOffset(now)
        val record = RestingHeartRateRecord(
            time = now,
            zoneOffset = zoneOffset,
            beatsPerMinute = data.restingHeartRate.toLong()
        )
        client.insertRecords(listOf(record))
        Log.d("GarminBridge", "Herzfrequenz geschrieben: ${data.restingHeartRate} bpm")
    }

    suspend fun writeSpO2(data: SpO2Data) {
        val now = Instant.now()
        val zone = ZoneId.systemDefault()
        val zoneOffset = zone.rules.getOffset(now)
        val percentageValue = data.avgSpO2.toDouble() / 100.0
        val record = OxygenSaturationRecord(
            time = now,
            zoneOffset = zoneOffset,
            percentage = Percentage(percentageValue)
        )
        client.insertRecords(listOf(record))
        Log.d("GarminBridge", "SpO2 geschrieben: ${data.avgSpO2}%")
    }

    suspend fun writeRespiration(data: RespirationData) {
        val now = Instant.now()
        val zone = ZoneId.systemDefault()
        val zoneOffset = zone.rules.getOffset(now)
        val record = RespiratoryRateRecord(
            time = now,
            zoneOffset = zoneOffset,
            rate = data.avgRespiration.toDouble()
        )
        client.insertRecords(listOf(record))
        Log.d("GarminBridge", "Atmung geschrieben: ${data.avgRespiration}")
    }

    suspend fun writeFloors(data: FloorsData) {
        val now = Instant.now()
        val zone = ZoneId.systemDefault()
        val zoneOffset = zone.rules.getOffset(now)
        val startTime = now.minusSeconds(60)
        val record = FloorsClimbedRecord(
            startTime = startTime,
            endTime = now,
            startZoneOffset = zoneOffset,
            endZoneOffset = zoneOffset,            floors = data.floorsClimbed.toDouble()
        )
        client.insertRecords(listOf(record))
        Log.d("GarminBridge", "Stockwerke geschrieben: ${data.floorsClimbed}")
    }

    suspend fun writeCalories(data: CaloriesData) {
        val todayKey = "last_calories_" + LocalDate.now().toString()
        val lastTotal = prefs.getLong(todayKey, 0L)
        val delta = data.totalCalories.toLong() - lastTotal
        if (delta <= 0) {
            Log.d("GarminBridge", "Keine neuen Kalorien")
            return
        }
        val now = Instant.now()
        val zone = ZoneId.systemDefault()
        val zoneOffset = zone.rules.getOffset(now)
        val lastWriteMillis = prefs.getLong("last_calories_write", now.toEpochMilli() - 3600000)
        var startTime = Instant.ofEpochMilli(lastWriteMillis)
        if (!startTime.isBefore(now)) startTime = now.minusSeconds(60)
        val record = TotalCaloriesBurnedRecord(
            startTime = startTime,
            endTime = now,
            startZoneOffset = zoneOffset,
            endZoneOffset = zoneOffset,
            energy = Energy.kilocalories(delta.toDouble())
        )
        client.insertRecords(listOf(record))
        prefs.edit().putLong(todayKey, data.totalCalories.toLong()).putLong("last_calories_write", now.toEpochMilli()).apply()
        Log.d("GarminBridge", "Kalorien geschrieben: $delta kcal")
    }
}