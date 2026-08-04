package com.example.garminbridge

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.StepsRecord
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

                                // Doppelzählung vermeiden: nur die DIFFERENZ seit dem letzten Sync schreiben
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
                                                                                              startTime = startTime,
                                                                                              endTime = now,
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
}
