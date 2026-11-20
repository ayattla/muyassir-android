package com.muyassir.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.muyassir.app.core.Files
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class MuyassirApp : Application() {
    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(applicationContext)
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "التذكيرات", NotificationManager.IMPORTANCE_HIGH)
        )
        // Temporary files policy: outputs older than 24 hours and all cached inputs are removed at launch.
        Files.cleanOld(this)
    }

    companion object { const val CHANNEL = "reminders" }
}
