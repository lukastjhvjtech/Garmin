package com.example.garminbridge

object GarminEndpoints {

    const val START_URL = "https://connect.garmin.com/"

    fun dailySummaryUrl(date: String): String {
        return "https://connect.garmin.com/app/daily-summary/$date"
    }
    
    fun sleepUrl(date: String): String {
        return "https://connect.garmin.com/app/sleep/$date"
    }
    
    fun heartRateUrl(date: String): String {
        return "https://connect.garmin.com/app/health-stats/heart-rate/$date"
    }
    
    fun stressUrl(date: String): String {
        return "https://connect.garmin.com/app/health-stats/stress/$date"
    }
    
    fun bodyBatteryUrl(date: String): String {
        return "https://connect.garmin.com/app/health-stats/body-battery/$date"
    }
}