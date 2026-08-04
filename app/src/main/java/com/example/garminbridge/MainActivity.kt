package com.example.garminbridge

import android.os.Bundle
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.permission.PermissionController
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

          private val permissionLauncher = registerForActivityResult(
                    PermissionController.createRequestPermissionResultContract()
                        ) { granted ->
                    if (granted.containsAll(requiredPermissions)) {
                                  statusText.text = "✅ Health-Connect-Schreibrecht erhalten"
                    } else {
                                  statusText.text = "❌ Health-Connect-Schreibrecht fehlt!"
                    }
          }

              override fun onCreate(savedInstanceState: Bundle?) {
                        super.onCreate(savedInstanceState)
                                setContentView(R.layout.activity_main)

                                        webView = findViewById(R.id.webView)
                                                statusText = findViewById(R.id.statusText)        val loginButton: Button = findViewById(R.id.loginButton)
                                                        val syncButton: Button = findViewById(R.id.syncButton)

                                                                healthWriter = HealthConnectWriter(this)

                                                                        setupWebView()
                                                                                checkPermissions()

                                                                                        loginButton.setOnClickListener {
                                                                                                      statusText.text = "🔐 Bitte bei Garmin einloggen..."
                                                                                                      webView.loadUrl(GarminEndpoints.START_URL)
                                                                                        }

                                                                                                syncButton.setOnClickListener {
                                                                                                              statusText.text = "🔄 Lade Tages-Daten..."
                                                                                                              val today = LocalDate.now().toString()
                                                                                                                          webView.loadUrl(GarminEndpoints.dailySummaryUrl(today))
                                                                                                }
              }

                  private fun setupWebView() {
                            webView.settings.javaScriptEnabled = true
                            webView.settings.domStorageEnabled = true
                            webView.settings.userAgentString =
                                "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"

                            // Cookies erlauben (wichtig für die Garmin-Session)
                            CookieManager.getInstance().setAcceptCookie(true)
                                    CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

                                            // JS-Hook VOR Garmins Code injizieren
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

                                                            // Brücke: JavaScript -> Kotlin
                                                                    webView.addJavascriptInterface(object {
                                                                                  @android.webkit.JavascriptInterface
                                                                                  fun onGarminData(url: String, json: String) {
                                                                                                    Log.d(TAG, "=== Daten von: $url")
                                                                                                                    // VERIFY: Hier das rohe JSON ansehen, um die echten Feldnamen zu finden
                                                                                                                                    Log.d(TAG, "RAW JSON: " + json.take(800))
                                                                                                                                                    handleGarminData(url, json)            }
                                                                    }, "GarminBridge")

                                                                            webView.webViewClient = WebViewClient()
                  }

                      private fun checkPermissions() {
                                val controller = PermissionController.create(this)
                                        lifecycleScope.launch {
                                                      try {
                                                                        val granted = controller.getGrantedPermissions()
                                                                                        if (granted.containsAll(requiredPermissions)) {
                                                                                                              statusText.text = "✅ Bereit. Tippe auf '1. Login'."
                                                                                        } else {
                                                                                                              permissionLauncher.launch(requiredPermissions)
                                                                                        }
                                                      } catch (e: Exception) {
                                                                        statusText.text = "❌ Health Connect nicht installiert?"
                                                                        Log.e(TAG, "Permission-Check fehlgeschlagen", e)
                                                      }
                                        }
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
                                                                                                                            statusText.text = "❌ Fehler: ${e.message}"
                                                                                                  }
                                                                                                                      Log.e(TAG, "Schreibfehler", e)
                                                                            }
                                                          }
                                            } else {
                                                          Log.d(TAG, "Keine Schritte in dieser Antwort gefunden")
                                            }
                          }
}
