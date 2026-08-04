package com.example.garminbridge

object GarminEndpoints {

      // Startseite – leitet bei Bedarf automatisch zum Garmin-Login weiter
      const val START_URL = "https://connect.garmin.com/"

      // Tages-Übersicht für ein Datum (Format YYYY-MM-DD)
      // Quelle: garmin-data-bridge scraper.py -> /app/daily-summary/{date}
      fun dailySummaryUrl(date: String): String {
                return "https://connect.garmin.com/app/daily-summary/$date"
      }
}
