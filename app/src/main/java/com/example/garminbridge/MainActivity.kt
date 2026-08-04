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
import androidx.health.connect.client.records.StepsRecord
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

    private val requiredPermissions = setOf(
        HealthPermission.getWritePermission(StepsRecord::class)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        statusText = findViewById(R.id.statusText)
        val loginButton: Button = findViewById(R.id.loginButton)
        val syncButton: Button = findViewById(R.id.syncButton)

        healthWriter = HealthConnectWriter(this)

        setupWebView()

        loginButton.setOnClickListener {            statusText.text = "🔐 Bitte bei Garmin einloggen..."
            webView.loadUrl(GarminEndpoints.START_URL)
        }

        syncButton.setOnClickListener {
            lifecycleScope.launch {
                checkAndRequestPermissions()
                statusText.text = "🔄 Lade Tages-Daten..."
                val today = LocalDate.now().toString()
                webView.loadUrl(GarminEndpoints.dailySummaryUrl(today))
            }
        }
    }

    private suspend fun checkAndRequestPermissions() {
        try {
            val client = HealthConnectClient.getOrCreate(this)
            val granted = client.permissionController.getGrantedPermissions()
            if (granted.containsAll(requiredPermissions)) {
                Log.d(TAG, "Health-Connect-Rechte bereits erteilt")
            } else {
                // Öffne Health Connect Einstellungen – Nutzer kann Rechte manuell erteilen
                val intent = Intent().apply {
                    action = "androidx.health.ACTION_HEALTH_CONNECT_SETTINGS"
                    data = Uri.parse("package:$packageName")
                }
                try {
                    startActivity(intent)
                    runOnUiThread {
                        Toast.makeText(
                            this@MainActivity,
                            "Bitte 'Schritte schreiben' aktivieren!",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Konnte Health Connect nicht öffnen", e)
                    runOnUiThread {
                        Toast.makeText(
                            this@MainActivity,
                            "Health Connect nicht installiert?",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Permission-Check fehlgeschlagen", e)
        }
    }
    private fun setupWebView() {
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.userAgentString =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(
                webView,
                GarminJsHook.SCRIPT,
                setOf("https://connect.garmin.com")
            )
            Log.d(TAG, "JS-Hook injiziert")
        } else {
            Log.w(TAG, "DOCUMENT_START_SCRIPT wird nicht unterstützt")
        }

        webView.addJavascriptInterface(object {
            @android.webkit.JavascriptInterface
            fun onGarminData(url: String, json: String) {
                Log.d(TAG, "=== Daten von: $url")
                Log.d(TAG, "RAW JSON: " + json.take(800))
                handleGarminData(url, json)
            }
        }, "GarminBridge")

        webView.webViewClient = WebViewClient()
    }

    private fun handleGarminData(url: String, json: String) {
        val steps = GarminDataParser.extractSteps(json)
        if (steps != null && steps > 0) {
            lifecycleScope.launch {
                try {
                    healthWriter.writeSteps(steps)
                    runOnUiThread {
                        statusText.text = "✅ $steps Schritte verarbeitet"
                        Toast.makeText(
                            this@MainActivity,
                            "Sync OK: $steps Schritte",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        statusText.text = "❌ Fehler: ${e.message}"                    }
                    Log.e(TAG, "Schreibfehler", e)
                }
            }
        } else {
            Log.d(TAG, "Keine Schritte in dieser Antwort gefunden")
        }
    }
}