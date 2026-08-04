package com.example.garminbridge

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.FloorsClimbedRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.lifecycle.lifecycleScope
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.launch
import java.time.LocalDate

class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "GarminBridge"
    }
    private lateinit var webView: WebView
    private lateinit var statusText: TextView
    private lateinit var healthWriter: HealthConnectWriter
    private var syncType = ""
    private val requiredPermissions = setOf(
        HealthPermission.getWritePermission(StepsRecord::class),
        HealthPermission.getWritePermission(SleepSessionRecord::class),
        HealthPermission.getWritePermission(RestingHeartRateRecord::class),
        HealthPermission.getWritePermission(OxygenSaturationRecord::class),
        HealthPermission.getWritePermission(RespiratoryRateRecord::class),
        HealthPermission.getWritePermission(FloorsClimbedRecord::class),
        HealthPermission.getWritePermission(TotalCaloriesBurnedRecord::class)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        webView = findViewById(R.id.webView)        statusText = findViewById(R.id.statusText)
        val loginButton: Button = findViewById(R.id.loginButton)
        val syncAllButton: Button = findViewById(R.id.syncAllButton)
        val syncStepsButton: Button = findViewById(R.id.syncStepsButton)
        val syncSleepButton: Button = findViewById(R.id.syncSleepButton)
        val syncHeartRateButton: Button = findViewById(R.id.syncHeartRateButton)
        val syncStressButton: Button = findViewById(R.id.syncStressButton)
        val syncCaloriesButton: Button = findViewById(R.id.syncCaloriesButton)
        val syncSpO2Button: Button = findViewById(R.id.syncSpO2Button)
        healthWriter = HealthConnectWriter(this)
        setupWebView()
        loginButton.setOnClickListener {
            statusText.text = "🔐 Bitte bei Garmin einloggen..."
            webView.loadUrl(GarminEndpoints.START_URL)
        }
        syncAllButton.setOnClickListener { syncAll() }
        syncStepsButton.setOnClickListener {
            syncType = "steps"
            loadPage("daily")
        }
        syncSleepButton.setOnClickListener {
            syncType = "sleep"
            loadPage("sleep")
        }
        syncHeartRateButton.setOnClickListener {
            syncType = "hr"
            loadPage("hr")
        }
        syncStressButton.setOnClickListener {
            syncType = "stress"
            loadPage("stress")
        }
        syncCaloriesButton.setOnClickListener {
            syncType = "calories"
            loadPage("daily")
        }
        syncSpO2Button.setOnClickListener {
            syncType = "spo2"
            loadPage("daily")
        }
    }

    private fun syncAll() {
        lifecycleScope.launch {
            checkAndRequestPermissions()
            statusText.text = "🔄 Sync alle Daten..."
            val today = LocalDate.now().toString()
            syncType = "all"
            webView.loadUrl(GarminEndpoints.dailySummaryUrl(today))
        }    }

    private fun loadPage(type: String) {
        lifecycleScope.launch {
            checkAndRequestPermissions()
            val today = LocalDate.now().toString()
            statusText.text = "🔄 Lade $type-Daten..."
            val url = when (type) {
                "daily" -> GarminEndpoints.dailySummaryUrl(today)
                "sleep" -> GarminEndpoints.sleepUrl(today)
                "hr" -> GarminEndpoints.heartRateUrl(today)
                "stress" -> GarminEndpoints.stressUrl(today)
                else -> GarminEndpoints.dailySummaryUrl(today)
            }
            webView.loadUrl(url)
        }
    }

    private suspend fun checkAndRequestPermissions() {
        try {
            val client = HealthConnectClient.getOrCreate(this)
            val granted = client.permissionController.getGrantedPermissions()
            if (!granted.containsAll(requiredPermissions)) {
                val intent = Intent().apply {
                    action = "androidx.health.ACTION_HEALTH_CONNECT_SETTINGS"
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
                runOnUiThread {
                    Toast.makeText(this, "Bitte alle Berechtigungen aktivieren!", Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Permission-Check fehlgeschlagen", e)
        }
    }

    private fun setupWebView() {
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.userAgentString = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36"
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(webView, GarminJsHook.SCRIPT, setOf("https://connect.garmin.com"))
        }
        webView.addJavascriptInterface(object {
            @android.webkit.JavascriptInterface
            fun onGarminData(url: String, json: String) {
                Log.d(TAG, "=== Daten von: $url")                handleGarminData(url, json)
            }
        }, "GarminBridge")
        webView.webViewClient = WebViewClient()
    }

    private fun handleGarminData(url: String, json: String) {
        lifecycleScope.launch {
            try {
                when {
                    syncType == "steps" || syncType == "all" -> {
                        val steps = GarminDataParser.extractSteps(json)
                        if (steps != null) {
                            healthWriter.writeSteps(steps)
                            runOnUiThread {
                                statusText.text = "✅ $steps Schritte"
                            }
                        }
                    }
                    syncType == "sleep" -> {
                        val sleep = GarminDataParser.extractSleep(json)
                        if (sleep != null) {
                            healthWriter.writeSleep(sleep)
                            runOnUiThread {
                                statusText.text = "😴 ${sleep.totalSleepMinutes} Min. Schlaf"
                            }
                        }
                    }
                    syncType == "hr" || syncType == "all" -> {
                        val hr = GarminDataParser.extractHeartRate(json)
                        if (hr != null) {
                            healthWriter.writeHeartRate(hr)
                            runOnUiThread {
                                statusText.text = "❤️ ${hr.restingHeartRate} bpm"
                            }
                        }
                    }
                    syncType == "calories" || syncType == "all" -> {
                        val calories = GarminDataParser.extractCalories(json)
                        if (calories != null) {
                            healthWriter.writeCalories(calories)
                            runOnUiThread {
                                statusText.text = "🔥 ${calories.totalCalories} kcal"
                            }
                        }
                    }
                    syncType == "spo2" || syncType == "all" -> {
                        val spo2 = GarminDataParser.extractSpO2(json)
                        if (spo2 != null) {
                            healthWriter.writeSpO2(spo2)                            runOnUiThread {
                                statusText.text = "🫁 SpO2: ${spo2.avgSpO2}%"
                            }
                        }
                    }
                }
                if (syncType == "all") {
                    val floors = GarminDataParser.extractFloors(json)
                    val resp = GarminDataParser.extractRespiration(json)
                    if (floors != null) healthWriter.writeFloors(floors)
                    if (resp != null) healthWriter.writeRespiration(resp)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    statusText.text = "❌ Fehler: ${e.message}"
                }
                Log.e(TAG, "Sync-Fehler", e)
            }
        }
    }
}