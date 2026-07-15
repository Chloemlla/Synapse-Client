package com.chloemlla.synapse.mobile.core.notify

import android.annotation.SuppressLint
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.chloemlla.synapse.mobile.MainActivity
import com.chloemlla.synapse.mobile.R

/**
 * Posts Android Live Update–eligible ongoing notifications for short, user-initiated waits.
 *
 * Requirements follow:
 * https://developer.android.com/develop/ui/views/notifications/live-update
 */
class SynapseLiveUpdateNotifier(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val notificationManager = NotificationManagerCompat.from(appContext)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val autoDismissRunnables = mutableMapOf<Int, Runnable>()

    fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = appContext.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            appContext.getString(R.string.live_update_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = appContext.getString(R.string.live_update_channel_description)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    fun canPostNotifications(): Boolean {
        if (!notificationManager.areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun canPostPromotedNotifications(): Boolean {
        if (Build.VERSION.SDK_INT < 36) return false
        val manager = appContext.getSystemService(NotificationManager::class.java) ?: return false
        return runCatching {
            val method = NotificationManager::class.java.getMethod("canPostPromotedNotifications")
            method.invoke(manager) as? Boolean ?: false
        }.getOrDefault(false)
    }

    fun openPromotedNotificationSettings(): Boolean {
        if (Build.VERSION.SDK_INT < 36) return false
        val intent = Intent(ACTION_MANAGE_APP_PROMOTED_NOTIFICATIONS).apply {
            putExtra(EXTRA_APP_PACKAGE, appContext.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            appContext.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    fun publish(snapshot: SynapseLiveUpdateSnapshot) {
        ensureChannels()
        if (!canPostNotifications()) return

        val notificationId = notificationIdFor(snapshot.kind)
        cancelScheduledDismiss(notificationId)
        val contentIntent = PendingIntent.getActivity(
            appContext,
            notificationId,
            Intent(appContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_OPEN_TAB, snapshot.openTab)
                putExtra(EXTRA_LIVE_UPDATE_KIND, snapshot.kind.name)
                action = ACTION_OPEN_FROM_LIVE_UPDATE
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_synapse)
            .setContentTitle(snapshot.title)
            .setContentText(snapshot.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(snapshot.text))
            .setContentIntent(contentIntent)
            .setOnlyAlertOnce(!snapshot.alert)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(snapshot.ongoing)
            .setAutoCancel(!snapshot.ongoing)
            .setPriority(
                if (snapshot.alert) {
                    NotificationCompat.PRIORITY_DEFAULT
                } else {
                    NotificationCompat.PRIORITY_LOW
                },
            )
            .setSilent(!snapshot.alert)

        snapshot.shortCriticalText
            ?.takeIf { it.isNotBlank() }
            ?.let { applyShortCriticalText(builder, it) }

        snapshot.whenEpochMillis?.let { epochMillis ->
            builder.setWhen(epochMillis)
            builder.setShowWhen(true)
            builder.setUsesChronometer(false)
        }

        if (snapshot.showIndeterminateProgress) {
            builder.setProgress(0, 0, true)
        }

        if (snapshot.requestPromoted && snapshot.ongoing) {
            applyRequestPromotedOngoing(builder)
        }

        val notification = builder.build()
        postNotification(notificationId, notification)

        snapshot.autoDismissAfterMs
            ?.takeIf { it > 0L && !snapshot.ongoing }
            ?.let { delayMs -> scheduleAutoDismiss(notificationId, delayMs) }
    }

    fun cancel(kind: SynapseLiveUpdateKind) {
        val notificationId = notificationIdFor(kind)
        cancelScheduledDismiss(notificationId)
        notificationManager.cancel(notificationId)
    }

    fun cancelAll() {
        SynapseLiveUpdateKind.entries.forEach(::cancel)
    }

    private fun notificationIdFor(kind: SynapseLiveUpdateKind): Int = when (kind) {
        SynapseLiveUpdateKind.WebQrLogin -> NOTIFICATION_ID_WEB_QR
        SynapseLiveUpdateKind.LinuxDoAuth -> NOTIFICATION_ID_LINUXDO
    }

    private fun scheduleAutoDismiss(notificationId: Int, delayMs: Long) {
        val runnable = Runnable {
            autoDismissRunnables.remove(notificationId)
            notificationManager.cancel(notificationId)
        }
        autoDismissRunnables[notificationId] = runnable
        mainHandler.postDelayed(runnable, delayMs)
    }

    private fun cancelScheduledDismiss(notificationId: Int) {
        autoDismissRunnables.remove(notificationId)?.let(mainHandler::removeCallbacks)
    }

    /**
     * Lint cannot prove [canPostNotifications] covers [Manifest.permission.POST_NOTIFICATIONS]
     * across a helper call. Re-check immediately before posting and swallow revoke races.
     */
    @SuppressLint("MissingPermission")
    private fun postNotification(notificationId: Int, notification: android.app.Notification) {
        if (!canPostNotifications()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }
        try {
            notificationManager.notify(notificationId, notification)
        } catch (_: SecurityException) {
            // Permission can be revoked between the check and the notify call.
        }
    }

    private fun applyRequestPromotedOngoing(builder: NotificationCompat.Builder) {
        // Preferred public API when present on the compile classpath / device support library.
        val applied = runCatching {
            val method = NotificationCompat.Builder::class.java.getMethod(
                "setRequestPromotedOngoing",
                Boolean::class.javaPrimitiveType,
            )
            method.invoke(builder, true)
            true
        }.getOrDefault(false)

        if (!applied) {
            // Framework extra documented for Live Update promotion requests.
            builder.extras.putBoolean(EXTRA_REQUEST_PROMOTED_ONGOING, true)
        }
    }

    private fun applyShortCriticalText(builder: NotificationCompat.Builder, text: String) {
        val applied = runCatching {
            val method = NotificationCompat.Builder::class.java.getMethod(
                "setShortCriticalText",
                CharSequence::class.java,
            )
            method.invoke(builder, text)
            true
        }.getOrDefault(false)
        if (!applied) {
            builder.setSubText(text)
        }
    }

    companion object {
        const val CHANNEL_ID = "synapse_live_updates"
        const val ACTION_OPEN_FROM_LIVE_UPDATE = "com.chloemlla.synapse.mobile.action.OPEN_FROM_LIVE_UPDATE"
        const val EXTRA_OPEN_TAB = "synapse_live_update_open_tab"
        const val EXTRA_LIVE_UPDATE_KIND = "synapse_live_update_kind"
        private const val NOTIFICATION_ID_WEB_QR = 41001
        private const val NOTIFICATION_ID_LINUXDO = 41002

        // android.app.Notification.EXTRA_REQUEST_PROMOTED_ONGOING
        private const val EXTRA_REQUEST_PROMOTED_ONGOING = "android.requestPromotedOngoing"
        // android.provider.Settings.ACTION_MANAGE_APP_PROMOTED_NOTIFICATIONS
        private const val ACTION_MANAGE_APP_PROMOTED_NOTIFICATIONS =
            "android.settings.MANAGE_APP_PROMOTED_NOTIFICATIONS"
        private const val EXTRA_APP_PACKAGE = "android.provider.extra.APP_PACKAGE"
    }
}
