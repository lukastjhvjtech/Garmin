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
import androidx.health.connect.client.records.*
import androidx.lifecycle.lifecycleScope
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.launch
import java.time.LocalDate

class MainActivity : AppCompatActivity() {
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
        webView = findViewById(R.id.webView)
        statusText = findViewById(R.id.statusText)
        healthWriter = HealthConnectWriter(this)
        setupWebView()
        findViewById<Button>(R.id.loginButton).setOnClickListener {
            statusText.text = "🔐 Login..."
            webView.loadUrl(GarminEndpoints.START_URL)
        }
        findViewById<Button>(R.id.syncAllButton).setOnClickListener { syncAll() }
        findViewById<Button>(R.id.syncStepsButton).setOnClickListener { syncType = "steps"; loadPage("daily") }        findViewById<Button>(R.id.syncSleepButton).setOnClickListener { syncType = "sleep"; loadPage("sleep") }
        findViewById<Button>(R.id.syncHeartRateButton).setOnClickListener { syncType = "hr"; loadPage("hr") }
        findViewById<Button>(R.id.syncStressButton).setOnClickListener { syncType = "stress"; loadPage("stress") }
        findViewById<Button>(R.id.syncCaloriesButton).setOnClickListener { syncType = "calories"; loadPage("daily") }
        findViewById<Button>(R.id.syncSpO2Button).setOnClickListener { syncType = "spo2"; loadPage("daily") }
    }

    private fun syncAll() {
        lifecycleScope.launch {
            checkPermissions()
            statusText.text = "🔄 Syncing all..."
            syncType = "all"
            webView.loadUrl(GarminEndpoints.dailySummaryUrl(LocalDate.now().toString()))
        }
    }

    private fun loadPage(type: String) {
        lifecycleScope.launch {
            checkPermissions()
            statusText.text = "🔄 Loading $type..."
            val today = LocalDate.now().toString()
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

    private suspend fun checkPermissions() {
        try {
            val client = HealthConnectClient.getOrCreate(this)
            val granted = client.permissionController.getGrantedPermissions()
            if (!granted.containsAll(requiredPermissions)) {
                startActivity(Intent("androidx.health.ACTION_HEALTH_CONNECT_SETTINGS").apply {
                    data = Uri.parse("package:$packageName")
                })
                Toast.makeText(this, "Enable all permissions!", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Log.e("GarminBridge", "Permission check failed", e)
        }
    }

    private fun setupWebView() {
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(webView, GarminJsHook.SCRIPT, setOf("https://connect.garmin.com"))
        }
        webView.addJavascriptInterface(object {
            @android.webkit.JavascriptInterface
            fun onGarminData(url: String, json: String) {
                Log.d("GarminBridge", "Data from: $url")
                handleData(url, json)
            }
        }, "GarminBridge")
        webView.webViewClient = WebViewClient()
    }

    private fun handleData(url: String, json: String) {
        lifecycleScope.launch {
            try {
                if (syncType == "steps" || syncType == "all") {
                    GarminDataParser.extractSteps(json)?.let {
                        healthWriter.writeSteps(it)
                        runOnUiThread { statusText.text = "✅ $it steps" }
                    }
                }
                if (syncType == "sleep") {
                    GarminDataParser.extractSleep(json)?.let {
                        healthWriter.writeSleep(it)
                        runOnUiThread { statusText.text = "😴 ${it.totalSleepMinutes} min sleep" }
                    }
                }
                if (syncType == "hr" || syncType == "all") {
                    GarminDataParser.extractHeartRate(json)?.let {
                        healthWriter.writeHeartRate(it)
                        runOnUiThread { statusText.text = "❤️ ${it.restingHeartRate} bpm" }
                    }
                }
                if (syncType == "calories" || syncType == "all") {
                    GarminDataParser.extractCalories(json)?.let {
                        healthWriter.writeCalories(it)
                        runOnUiThread { statusText.text = "🔥 ${it.totalCalories} kcal" }
                    }
                }
                if (syncType == "spo2" || syncType == "all") {
                    GarminDataParser.extractSpO2(json)?.let {
                        healthWriter.writeSpO2(it)
                        runOnUiThread { statusText.text = "🫁 SpO2: ${it.avgSpO2}%" }
                    }
                }
                if (syncType == "all") {
                    GarminDataParser.extractFloors(json)?.let { healthWriter.writeFloors(it) }                    GarminDataParser.extractRespiration(json)?.let { healthWriter.writeRespiration(it) }
                }
            } catch (e: Exception) {
                runOnUiThread { statusText.text = "❌ Error: ${e.message}" }
                Log.e("GarminBridge", "Sync error", e)
            }
        }
    }
}