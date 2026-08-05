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
import android.view.Menu
import android.view.MenuItem
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.TimePicker
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.lifecycle.lifecycleScope
import androidx.navigation.Navigation
import androidx.navigation.ui.NavigationUI
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.Instant
import java.time.LocalTime

class MainActivity : AppCompatActivity() {
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var toolbar: Toolbar
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
        
        // Toolbar als ActionBar setzen
        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        
        // Drawer Layout setup
        drawerLayout = findViewById(R.id.drawerLayout)
        navigationView = findViewById(R.id.navigationView)
        
        // Burger-Menü Button zur Toolbar hinzufügen
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_menu)
        
        // Navigation Menü setup
        NavigationUI.setupWithNavController(navigationView, Navigation.findNavController(this, R.id.nav_host_fragment))
        
        drawerLayout.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
            override fun onDrawerOpened(drawerView: android.view.View) {
                super.onDrawerOpened(drawerView)
            }
            
            override fun onDrawerClosed(drawerView: android.view.View) {
                super.onDrawerClosed(drawerView)
            }
        })
        
        navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_home -> {
                    // Home Aktion - schließe Drawer
                    drawerLayout.closeDrawers()
                    true
                }
                R.id.nav_login -> {
                    // Login Aktion - starte WebView Activity
                    val intent = Intent(this, GarminLoginActivity::class.java)
                    startActivity(intent)
                    drawerLayout.closeDrawers()
                    true
                }
                else -> false
            }
        }
        
        statusText = findViewById(R.id.statusText)
        healthWriter = HealthConnectWriter(this)
        manualStepsInput = findViewById(R.id.manualStepsInput)
        syncTimePicker = findViewById(R.id.syncTimePicker)
        
        // Standardzeit auf 8:00 Uhr setzen
        syncTimePicker.hour = 8
        syncTimePicker.minute = 0
        syncTimePicker.setIs24HourView(true)
        
        createNotificationChannel()
        
        findViewById<Button>(R.id.syncStepsButton).setOnClickListener {
            syncType = "steps"
            startGarminLogin("daily")
        }
        
        findViewById<Button>(R.id.syncSleepButton).setOnClickListener {
            syncType = "sleep"
            startGarminLogin("sleep")
        }
        
        findViewById<Button>(R.id.addManualStepsButton).setOnClickListener {
            addManualSteps()
        }
        
        findViewById<Button>(R.id.setAutoSyncButton).setOnClickListener {
            scheduleAutoSync()
        }
        
        requestNotificationPermission()
    }
    
    private fun addManualSteps() {
        val stepsText = manualStepsInput.text.toString()
        if (stepsText.isEmpty()) {
            Toast.makeText(this, "Bitte eine Schrittzahl eingeben", Toast.LENGTH_SHORT).show()
            return
        }
        
        try {
            val steps = stepsText.toLong()
            
            // Validierung: Positive Zahl und im gültigen Bereich
            if (steps <= 0) {
                Toast.makeText(this, "Schrittzahl muss größer als 0 sein", Toast.LENGTH_SHORT).show()
                return
            }
            
            // Maximalwert prüfen (Long.MAX_VALUE ist ca. 9 Quintillionen)
            val maxReasonableSteps = 1_000_000_000L // 1 Milliarde als vernünftiges Maximum
            if (steps > maxReasonableSteps) {
                Toast.makeText(
                    this, 
                    "Ungültige Schrittzahl. Bitte geben Sie einen Wert unter 1 Milliarde ein.", 
                    Toast.LENGTH_LONG
                ).show()
                return
            }
            
            lifecycleScope.launch {
                try {
                    checkPermissions()
                    healthWriter.writeSteps(steps)
                    runOnUiThread { 
                        statusText.text = "✅ $steps Schritte manuell hinzugefügt"
                        manualStepsInput.text.clear()
                        Toast.makeText(this@MainActivity, "$steps Schritte hinzugefügt!", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        statusText.text = "❌ Fehler beim Schreiben: ${e.message}"
                        Toast.makeText(this@MainActivity, "Fehler: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                    Log.e("GarminBridge", "Fehler beim Schreiben der Schritte", e)
                }
            }
        } catch (e: NumberFormatException) {
            Toast.makeText(this, "Bitte eine gültige Zahl eingeben", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Unerwarteter Fehler: ${e.message}", Toast.LENGTH_SHORT).show()
            Log.e("GarminBridge", "Unerwarteter Fehler", e)
        }
    }
    
    private fun startGarminLogin(type: String) {
        val intent = Intent(this, GarminLoginActivity::class.java)
        intent.putExtra("sync_type", type)
        startActivity(intent)
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