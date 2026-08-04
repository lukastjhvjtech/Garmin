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
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
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
        HealthPermission.getWritePermission(SleepSessionRecord::class)
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
        
        findViewById<Button>(R.id.syncStepsButton).setOnClickListener {            syncType = "steps"
            loadPage("daily")
        }
        
        findViewById<Button>(R.id.syncSleepButton).setOnClickListener {
            syncType = "sleep"
            loadPage("sleep")
        }
    }

    private fun loadPage(type: String) {
        lifecycleScope.launch {
            checkPermissions()
            statusText.text = "🔄 Loading..."
            val today = LocalDate.now().toString()
            val url = when (type) {
                "daily" -> GarminEndpoints.dailySummaryUrl(today)
                "sleep" -> GarminEndpoints.sleepUrl(today)
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
                runOnUiThread {
                    Toast.makeText(this, "Bitte 'Schritte' und 'Schlaf' aktivieren!", Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: Exception) {
            Log.e("GarminBridge", "Permission check failed", e)
        }
    }

    private fun setupWebView() {
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(
                webView,                GarminJsHook.SCRIPT,
                setOf("https://connect.garmin.com")
            )
        }
        
        webView.addJavascriptInterface(object {
            @android.webkit.JavascriptInterface
            fun onGarminData(url: String, json: String) {
                Log.d("GarminBridge", "Data: $url")
                handleData(url, json)
            }
        }, "GarminBridge")
        
        webView.webViewClient = WebViewClient()
    }

    private fun handleData(url: String, json: String) {
        lifecycleScope.launch {
            try {
                if (syncType == "steps") {
                    val steps = GarminDataParser.extractSteps(json)
                    if (steps != null) {
                        healthWriter.writeSteps(steps)
                        runOnUiThread { statusText.text = "✅ $steps Schritte" }
                    }
                }
                
                if (syncType == "sleep") {
                    val sleep = GarminDataParser.extractSleep(json)
                    if (sleep != null) {
                        healthWriter.writeSleep(sleep)
                        runOnUiThread { statusText.text = "😴 ${sleep.totalSleepMinutes} Min. Schlaf" }
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { statusText.text = "❌ Fehler: ${e.message}" }
                Log.e("GarminBridge", "Sync error", e)
            }
        }
    }
}