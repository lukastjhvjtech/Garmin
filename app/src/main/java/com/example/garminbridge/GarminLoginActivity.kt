package com.example.garminbridge

import android.os.Bundle
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.TextView
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

class GarminLoginActivity : AppCompatActivity() {
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
        setContentView(R.layout.activity_garmin_login)
        
        webView = findViewById(R.id.loginWebView)
        statusText = findViewById(R.id.loginStatusText)
        healthWriter = HealthConnectWriter(this)
        
        // Sync-Typ aus Intent holen
        syncType = intent.getStringExtra("sync_type") ?: "steps"
        
        setupWebView()
        
        // Fertig-Button
        findViewById<Button>(R.id.doneButton).setOnClickListener {
            finish()
        }
        
        // Login starten
        statusText.text = "🔐 Bitte bei Garmin einloggen..."
        webView.loadUrl(GarminEndpoints.START_URL)
    }
    
    private fun setupWebView() {
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(
                webView,
                GarminJsHook.SCRIPT,
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
        
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                Log.d("GarminBridge", "Page loaded: $url")
            }
        }
    }

    private fun handleData(url: String, json: String) {
        lifecycleScope.launch {
            try {
                if (syncType == "steps") {
                    val steps = GarminDataParser.extractSteps(json)
                    if (steps != null) {
                        checkPermissions()
                        healthWriter.writeSteps(steps)
                        runOnUiThread { 
                            statusText.text = "✅ $steps Schritte synchronisiert"
                            // Nach erfolgreichem Sync automatisch schließen
                            webView.postDelayed({ finish() }, 1500)
                        }
                    }
                }
                
                if (syncType == "sleep") {
                    val sleep = GarminDataParser.extractSleep(json)
                    if (sleep != null) {
                        checkPermissions()
                        healthWriter.writeSleep(sleep)
                        runOnUiThread { 
                            statusText.text = "😴 ${sleep.totalSleepMinutes} Min. Schlaf synchronisiert"
                            // Nach erfolgreichem Sync automatisch schließen
                            webView.postDelayed({ finish() }, 1500)
                        }
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { 
                    statusText.text = "❌ Fehler: ${e.message}"
                    Log.e("GarminBridge", "Sync error", e)
                }
            }
        }
    }
    
    private suspend fun checkPermissions() {
        try {
            val client = HealthConnectClient.getOrCreate(this)
            val granted = client.permissionController.getGrantedPermissions()
            if (!granted.containsAll(requiredPermissions)) {
                startActivity(android.content.Intent("androidx.health.ACTION_HEALTH_CONNECT_SETTINGS").apply {
                    data = android.net.Uri.parse("package:$packageName")
                })
                runOnUiThread {
                    android.widget.Toast.makeText(this, "Bitte 'Schritte' und 'Schlaf' aktivieren!", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: Exception) {
            Log.e("GarminBridge", "Permission check failed", e)
        }
    }
    
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
