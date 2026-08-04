package com.example.garminbridge

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.DataOrigin
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class HealthConnectWriter(private val context: Context) {

    private val client: HealthConnectClient by lazy {
        HealthConnectClient.getOrCreate(context)
    }

    private val prefs =
        context.getSharedPreferences("garmin_bridge", Context.MODE_PRIVATE)

    suspend fun writeSteps(currentTotal: Long) {
        if (HealthConnectClient.getSdkStatus(context) != HealthConnectClient.SDK_AVAILABLE) {
            throw IllegalStateException(
                "Health Connect ist nicht verfügbar. Bitte die Health-Connect-App aus dem Play Store installieren."
            )
        }

        val todayKey = "last_steps_" + LocalDate.now().toString()
        val lastTotal = prefs.getLong(todayKey, 0L)
        val delta = currentTotal - lastTotal

        if (delta <= 0) {
            Log.d("GarminBridge", "Keine neuen Schritte (delta=$delta)")
            return
        }

        val now = Instant.now()
        val zone: ZoneId = ZoneId.systemDefault()
        val zoneOffset = zone.rules.getOffset(now)

        val lastWriteMillis = prefs.getLong("last_write_millis", now.toEpochMilli() - 3_600_000)
        var startTime = Instant.ofEpochMilli(lastWriteMillis)
        if (!startTime.isBefore(now)) {
            startTime = now.minusSeconds(60)
        }

        val record = StepsRecord(
            count = delta,
            startTime = startTime,            endTime = now,
            startZoneOffset = zoneOffset,
            endZoneOffset = zoneOffset
        )

        client.insertRecords(listOf(record))

        prefs.edit()
            .putLong(todayKey, currentTotal)
            .putLong("last_write_millis", now.toEpochMilli())
            .apply()

        Log.d("GarminBridge", "Delta $delta Schritte geschrieben (Gesamt $currentTotal)")
    }

    suspend fun writeSleep(sleepData: SleepData) {
        if (HealthConnectClient.getSdkStatus(context) != HealthConnectClient.SDK_AVAILABLE) {
            throw IllegalStateException(
                "Health Connect ist nicht verfügbar. Bitte die Health-Connect-App aus dem Play Store installieren."
            )
        }

        // Verhindere doppelte Schreibvorgänge für dieselbe Nacht
        val todayKey = "last_sleep_" + LocalDate.now().toString()
        if (prefs.getBoolean(todayKey, false)) {
            Log.d("GarminBridge", "Schlaf für heute schon geschrieben")
            return
        }

        val startInstant = Instant.ofEpochMilli(sleepData.sleepStartMillis)
        val endInstant = Instant.ofEpochMilli(sleepData.sleepEndMillis)
        val zone: ZoneId = ZoneId.systemDefault()
        val startOffset = zone.rules.getOffset(startInstant)
        val endOffset = zone.rules.getOffset(endInstant)

        // Schlafphasen als Stages hinzufügen
        val stages = mutableListOf<SleepSessionRecord.Stage>()
        var currentMillis = startInstant.toEpochMilli()
        
        if (sleepData.lightSleepMinutes > 0) {
            val stageEnd = currentMillis + (sleepData.lightSleepMinutes * 60 * 1000)
            stages.add(SleepSessionRecord.Stage(
                Instant.ofEpochMilli(currentMillis),
                Instant.ofEpochMilli(stageEnd),
                SleepSessionRecord.STAGE_TYPE_LIGHT
            ))
            currentMillis = stageEnd
        }
        
        if (sleepData.deepSleepMinutes > 0) {            val stageEnd = currentMillis + (sleepData.deepSleepMinutes * 60 * 1000)
            stages.add(SleepSessionRecord.Stage(
                Instant.ofEpochMilli(currentMillis),
                Instant.ofEpochMilli(stageEnd),
                SleepSessionRecord.STAGE_TYPE_DEEP
            ))
            currentMillis = stageEnd
        }
        
        if (sleepData.remSleepMinutes > 0) {
            val stageEnd = currentMillis + (sleepData.remSleepMinutes * 60 * 1000)
            stages.add(SleepSessionRecord.Stage(
                Instant.ofEpochMilli(currentMillis),
                Instant.ofEpochMilli(stageEnd),
                SleepSessionRecord.STAGE_TYPE_REM
            ))
            currentMillis = stageEnd
        }
        
        if (sleepData.awakeMinutes > 0) {
            val stageEnd = currentMillis + (sleepData.awakeMinutes * 60 * 1000)
            stages.add(SleepSessionRecord.Stage(
                Instant.ofEpochMilli(currentMillis),
                Instant.ofEpochMilli(stageEnd),
                SleepSessionRecord.STAGE_TYPE_AWAKE
            ))
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

        Log.d("GarminBridge", "Schlaf geschrieben: ${sleepData.totalSleepMinutes} Minuten")
    }
}