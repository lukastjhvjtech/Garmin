package com.example.garminbridge

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
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
        
        val record = StepsRecord(
            count = delta,
            startTime = start,
            endTime = now,
            startZoneOffset = offset,
            endZoneOffset = offset
        )
        
        client.insertRecords(listOf(record))
        prefs.edit().putLong(key, total).apply()
        Log.d("GarminBridge", "Steps: $delta")
    }

    suspend fun writeSleep(data: SleepData) {
        val key = "last_sleep_" + LocalDate.now().toString()
        if (prefs.getBoolean(key, false)) return
        
        val start = Instant.ofEpochMilli(data.sleepStartMillis)
        val end = Instant.ofEpochMilli(data.sleepEndMillis)
        val offset = ZoneId.systemDefault().rules.getOffset(start)
        
        val record = SleepSessionRecord(
            startTime = start,
            endTime = end,
            startZoneOffset = offset,
            endZoneOffset = offset,
            stages = emptyList()
        )
        
        client.insertRecords(listOf(record))
        prefs.edit().putBoolean(key, true).apply()
        Log.d("GarminBridge", "Sleep: ${data.totalSleepMinutes} min")
    }
}