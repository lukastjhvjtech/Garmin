package com.example.garminbridge

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.AlarmManagerCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.WorkManager
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.time.LocalDate
import java.time.LocalTime

class SyncAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("GarminBridge", "Auto-Sync ausgelöst")
        
        val workRequest = OneTimeWorkRequestBuilder<SyncWorker>().build()
        WorkManager.getInstance(context).enqueue(workRequest)
        
        // Erinnerung anzeigen
        showNotification(context)
    }
    
    private fun showNotification(context: Context) {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        
        val notification = NotificationCompat.Builder(context, "sync_channel")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Garmin Health Bridge")
            .setContentText("Automatische Synchronisation gestartet...")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        
        NotificationManagerCompat.from(context).notify(1, notification)
    }
}

class SyncWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {
    
    override fun doWork(): Result {
        Log.d("GarminBridge", "SyncWorker gestartet")
        // Hier könnte die eigentliche Sync-Logik stehen
        // Für jetzt nur ein Log
        return Result.success()
    }
}
