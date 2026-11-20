package com.muyassir.app.core

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.muyassir.app.MainActivity
import com.muyassir.app.MuyassirApp
import com.muyassir.app.R

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            android.os.Build.VERSION.SDK_INT >= 33) return
        val open = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(ctx, MuyassirApp.CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(intent.getStringExtra("title") ?: "تذكير")
            .setContentText(intent.getStringExtra("text") ?: "")
            .setContentIntent(open).setAutoCancel(true).build()
        NotificationManagerCompat.from(ctx).notify(intent.getIntExtra("id", 0), n)
    }

    companion object {
        /** Inexact-but-idle-safe alarm; no exact-alarm permission needed. */
        fun schedule(ctx: Context, id: Int, at: Long, title: String, text: String) {
            val pi = PendingIntent.getBroadcast(
                ctx, id, Intent(ctx, ReminderReceiver::class.java).putExtra("id", id).putExtra("title", title).putExtra("text", text),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            ctx.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }

        fun cancel(ctx: Context, id: Int) {
            val pi = PendingIntent.getBroadcast(ctx, id, Intent(ctx, ReminderReceiver::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE) ?: return
            ctx.getSystemService(AlarmManager::class.java).cancel(pi)
        }
    }
}
