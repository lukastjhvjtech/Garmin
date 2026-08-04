package com.example.garminbridge

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

object GarminDataParser {

      private const val TAG = "GarminParser"

      // VERIFY: Die Feldnamen ("totalSteps", "steps") sind eine fundierte Annahme.
      // Schau nach dem ersten Sync in Logcat (Tag "GarminBridge") das rohe JSON an
      // und passe die Feldnamen hier an, falls sie anders heißen.
      fun extractSteps(json: String): Long? {
                return try {
                              val trimmed = json.trim()
                                          when {
                                                            trimmed.startsWith("{") -> parseObject(JSONObject(trimmed))
                                                                            trimmed.startsWith("[") -> parseArray(JSONArray(trimmed))
                                                                                            else -> null
                                          }
                } catch (e: Exception) {
                              Log.e(TAG, "Parse-Fehler", e)
                                          null
                }
      }

          private fun parseObject(obj: JSONObject): Long? {
                    val candidates = listOf("totalSteps", "totalStepsInteger", "steps")
                            for (field in candidates) {
                                          if (obj.has(field)) {
                                                            val v = obj.optLong(field, -1)
                                                                            if (v >= 0) return v
                                          }
                            }
                                    return null
          }

              private fun parseArray(arr: JSONArray): Long? {
                        if (arr.length() == 0) return null
                        var total = 0L
                        var found = false

                        for (i in 0 until arr.length()) {
                                      val obj = arr.optJSONObject(i) ?: continue

                                      // Bevorzugt das Gesamt-Feld, sonst stündliche "steps" aufsummieren
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
}
