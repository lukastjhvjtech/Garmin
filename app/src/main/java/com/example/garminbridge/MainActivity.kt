package com.example.garminbridge

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.TimePicker
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.lifecycle.lifecycleScope
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.Instant
import java.time.LocalTime

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var statusText: TextView
    private lateinit var healthWriter: HealthConnectWriter
    private lateinit var manualStepsInput: EditText
    private lateinit var syncTimePicker: TimePicker
    private var syncType = ""
    
    private val requiredPermissions = setOf(
        HealthPermission.getWritePermission(StepsRecord::class),
        HealthPermission.getWritePermission(SleepSessionRecord::class)
    )
    
    private val NOTIFICATION_PERMISSION_CODE = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        webView = findViewById(R.id.webView)
        statusText = findViewById(R.id.statusText)
        healthWriter = HealthConnectWriter(this)
        manualStepsInput = findViewById(R.id.manualStepsInput)
        syncTimePicker = findViewById(R.id.syncTimePicker)
        
        // Standardzeit auf 8:00 Uhr setzen
        syncTimePicker.hour = 8
        syncTimePicker.minute = 0
        syncTimePicker.setIs24HourView(true)
        
        createNotificationChannel()
        setupWebView()
        
        findViewById<Button>(R.id.loginButton).setOnClickListener {
            statusText.text = "🔐 Login..."
            webView.loadUrl(GarminEndpoints.START_URL)
        }
        
        findViewById<Button>(R.id.syncStepsButton).setOnClickListener {
            syncType = "steps"
            loadPage("daily")
        }
        
        findViewById<Button>(R.id.syncSleepButton).setOnClickListener {
            syncType = "sleep"
            loadPage("sleep")
        }
        
        findViewById<Button>(R.id.addManualStepsButton).setOnClickListener {
            val stepsText = manualStepsInput.text.toString()
            if (stepsText.isNotEmpty()) {
                val steps = stepsText.toLongOrNull()
                if (steps != null && steps > 0) {
                    lifecycleScope.launch {
                        checkPermissions()
                        healthWriter.writeSteps(steps)
                        runOnUiThread { 
                            statusText.text = "✅ $steps Schritte manuell hinzugefügt"
                            manualStepsInput.text.clear()
                        }
                    }
                } else {
                    Toast.makeText(this, "Ungültige Schrittzahl", Toast.LENGTH_SHORT).show()
                }
            }
        }
        
        findViewById<Button>(R.id.setAutoSyncButton).setOnClickListener {
            scheduleAutoSync()
        }
        
        requestNotificationPermission()
    }
    
    private fun createNotificationChannel() {
        val channel = NotificationChannelCompat.Builder(
            "sync_channel",
            NotificationManagerCompat.IMPORTANCE_DEFAULT
        )
            .setName("Synchronisation")
            .setDescription("Benachrichtigungen für automatische Synchronisation")
            .build()
        
        NotificationManagerCompat.from(this).createNotificationChannel(channel)
    }
    
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    NOTIFICATION_PERMISSION_CODE
                )
            }
        }
    }
    
    private fun scheduleAutoSync() {
        val hour = syncTimePicker.hour
        val minute = syncTimePicker.minute
        
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, SyncAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Alarmzeit berechnen
        val calendar = java.util.Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            set(java.util.Calendar.HOUR_OF_DAY, hour)
            set(java.util.Calendar.MINUTE, minute)
            set(java.util.Calendar.SECOND, 0)
            
            // Wenn die Zeit bereits heute vergangen ist, auf morgen setzen
            if (timeInMillis <= System.currentTimeMillis()) {
                add(java.util.Calendar.DAY_OF_YEAR, 1)
            }
        }
        
        // Exact Alarm Permission für Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                Toast.makeText(
                    this,
                    "Bitte erlaube exakte Alarme in den Einstellungen",
                    Toast.LENGTH_LONG
                ).show()
                
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
                return
            }
        }
        
        // Täglichen Alarm setzen
        alarmManager.setRepeating(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            AlarmManager.INTERVAL_DAY,
            pendingIntent
        )
        
        Toast.makeText(
            this,
            "⏰ Auto-Sync täglich um ${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')} Uhr",
            Toast.LENGTH_LONG
        ).show()
        
        statusText.text = "⏰ Auto-Sync aktiviert: ${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
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