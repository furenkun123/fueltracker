package com.fueltracker.notif

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object ParkingNotification {

    private const val CHANNEL_ID = "parking_active"
    private const val CHANNEL_NAME = "停车提醒"
    private const val NOTIFICATION_ID = 1001

    /**
     * 确保通知渠道已创建（Android 8+）。
     * 可以重复调用，系统会自动跳过已存在的渠道。
     */
    fun ensureChannel(context: Context) {

        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val existing = manager.getNotificationChannel(CHANNEL_ID)
        if (existing != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW // 不出声、不震动
        ).apply {
            description = "停车位置常驻提醒"
            setShowBadge(false)
        }

        manager.createNotificationChannel(channel)
    }

    /**
     * 发一条停车通知。
     * 点击通知会打开 App 主界面。
     */
    fun show(
        context: Context,
        vehicleId: Long,
        locationName: String?,
        floor: String?,
        area: String?,
        spotNumber: String?
    ) {
        ensureChannel(context)

        val detail = buildString {
            floor?.takeIf { it.isNotBlank() }?.let {
                if (isNotEmpty()) append(" · ")
                append(it)
            }
            area?.takeIf { it.isNotBlank() }?.let {
                if (isNotEmpty()) append(" · ")
                append(it)
            }
            spotNumber?.takeIf { it.isNotBlank() }?.let {
                if (isNotEmpty()) append(" · ")
                append(it)
            }
        }

        val title = locationName?.takeIf { it.isNotBlank() } ?: "当前停车"
        val contentText = if (detail.isBlank()) "点击查看停车详情" else detail

        // 点通知主体 → 打开 App
        val launchIntent = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

        val contentPendingIntent = if (launchIntent != null) {
            PendingIntent.getActivity(
                context,
                0,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else {
            null
        }

        // 忽略按钮
        val ignorePendingIntent = PendingIntent.getBroadcast(
            context,
            1,
            Intent(context, ParkingActionReceiver::class.java).apply {
                action = ParkingActionReceiver.ACTION_IGNORE
                putExtra(ParkingActionReceiver.EXTRA_VEHICLE_ID, vehicleId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 已取车按钮
        val clearPendingIntent = PendingIntent.getBroadcast(
            context,
            2,
            Intent(context, ParkingActionReceiver::class.java).apply {
                action = ParkingActionReceiver.ACTION_CLEAR
                putExtra(ParkingActionReceiver.EXTRA_VEHICLE_ID, vehicleId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("📍 $title")
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setAutoCancel(false)
            .setShowWhen(false)
            .addAction(0, "忽略", ignorePendingIntent)
            .addAction(0, "已取车", clearPendingIntent)

        if (contentPendingIntent != null) {
            builder.setContentIntent(contentPendingIntent)
        }

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
        } catch (_: SecurityException) {
        }
    }

    /**
     * 清除停车通知。
     */
    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }
}