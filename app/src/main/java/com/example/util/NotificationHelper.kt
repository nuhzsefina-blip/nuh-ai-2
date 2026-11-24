package com.example.util

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.security.SecurePrefsManager
import java.util.Calendar
import java.util.TimeZone

object NotificationHelper {
    const val CHANNEL_ADJOURNMENT = "redat_dagna_adjournment"
    const val CHANNEL_DRAFT_STATUS = "redat_dagna_draft_status"
    const val CHANNEL_SYNC_BACKUP = "redat_dagna_sync_backup"

    const val EXTRA_CASE_ID = "extra_open_case_id"
    const val EXTRA_NOTIFICATION_TARGET = "extra_notification_target"

    private const val ID_DRAFT_BASE = 20_000
    private const val ID_ADJOURNMENT_BASE = 30_000
    private const val ID_DRIVE_SYNC = 40_001
    private const val ID_TEST_NOTIFICATION = 50_001

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val appContext = context.applicationContext
        val manager = appContext.getSystemService(NotificationManager::class.java) ?: return

        val adjournmentChannel = NotificationChannel(
            CHANNEL_ADJOURNMENT,
            "የችሎት ቀጠሮ ማስታወሻዎች",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "ለመዝገቦች የተያዙ የችሎት ቀጠሮ ቀናትን አስቀድሞ ያስታውሳል።"
            enableVibration(true)
        }

        val draftChannel = NotificationChannel(
            CHANNEL_DRAFT_STATUS,
            "የረቂቅ ውሳኔ ዝግጅት",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "ረቂቅ ውሳኔ ሲጠናቀቅ እና ከኔትወርክ ወረፋ ሲሰራ ያሳውቃል።"
        }

        val syncChannel = NotificationChannel(
            CHANNEL_SYNC_BACKUP,
            "Google Drive እና Cloud ምትኬ",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "የተመሰጠሩ መዝገቦች ምትኬ ሲጠናቀቅ ያሳውቃል።"
        }

        manager.createNotificationChannels(
            listOf(adjournmentChannel, draftChannel, syncChannel)
        )
    }

    fun hasSystemPermission(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
    }

    fun areNotificationsAllowed(context: Context): Boolean {
        val appContext = context.applicationContext
        if (!hasSystemPermission(appContext)) return false
        if (!NotificationManagerCompat.from(appContext).areNotificationsEnabled()) return false
        return SecurePrefsManager.getInstance(appContext).settings.value.notificationsEnabled
    }

    fun showAdjournmentReminder(
        context: Context,
        caseId: Long,
        caseNumber: String,
        ethiopianDate: String,
        partiesSummary: String,
        isDayBefore: Boolean,
    ): Boolean {
        val caseLabel = caseNumber.ifBlank { "ያልተሰየመ መዝገብ" }
        val dayPrefix = if (isDayBefore) "ነገ" else "ዛሬ"
        val title = "የችሎት ቀጠሮ ማስታወሻ ($dayPrefix)"
        val body = buildString {
            append("መ/ቁ ").append(caseLabel)
            append(" — ቀጠሮ: ").append(ethiopianDate).append(" ዓ.ም")
            if (partiesSummary.isNotBlank()) {
                append("\n").append(partiesSummary)
            }
        }
        val notifId = ID_ADJOURNMENT_BASE + ((caseNumber.ifBlank { ethiopianDate }.hashCode()) and 0x0FFF)
        return showNotification(
            context = context,
            channelId = CHANNEL_ADJOURNMENT,
            notificationId = notifId,
            title = title,
            body = body,
            caseId = caseId,
            priority = NotificationCompat.PRIORITY_HIGH,
        )
    }

    fun showDraftReadyNotification(
        context: Context,
        caseId: Long,
        caseNumber: String,
        version: Int,
        isFromOfflineQueue: Boolean,
    ): Boolean {
        val caseLabel = caseNumber.ifBlank { "አዲስ መዝገብ" }
        val title = if (isFromOfflineQueue) {
            "ከወረፋ የተሰራ ረቂቅ ተጠናቋል"
        } else {
            "የውሳኔ ረቂቅ ተዘጋጅቷል"
        }
        val body = "መ/ቁ $caseLabel (ስሪት v$version) ለዳኛው እይታ እና ማሻሻያ ዝግጁ ነው።"
        val notifId = ID_DRAFT_BASE + ((caseId.takeIf { it > 0L }?.toInt() ?: caseLabel.hashCode()) and 0x0FFF)
        return showNotification(
            context = context,
            channelId = CHANNEL_DRAFT_STATUS,
            notificationId = notifId,
            title = title,
            body = body,
            caseId = caseId,
            priority = NotificationCompat.PRIORITY_DEFAULT,
        )
    }

    fun showBackupCompletedNotification(
        context: Context,
        syncedCount: Int,
    ): Boolean {
        if (syncedCount <= 0) return false
        return showNotification(
            context = context,
            channelId = CHANNEL_SYNC_BACKUP,
            notificationId = ID_DRIVE_SYNC,
            title = "የመዝገቦች ምትኬ ተመሳስሏል",
            body = "$syncedCount መዝገብ(ዎች) በAES-256-GCM ተመስጥረው ተቀምጠዋል።",
            caseId = -1L,
            priority = NotificationCompat.PRIORITY_LOW,
        )
    }

    fun showTestNotification(context: Context): Boolean {
        return showNotification(
            context = context,
            channelId = CHANNEL_ADJOURNMENT,
            notificationId = ID_TEST_NOTIFICATION,
            title = "የረዳት ዳኛ Notification ንቁ ነው",
            body = "የችሎት ቀጠሮዎች፣ የውሳኔ ረቂቆች እና የDrive ምትኬ ማሳወቂያዎች በትክክል ይደርሱዎታል።",
            caseId = -1L,
            priority = NotificationCompat.PRIORITY_HIGH,
            ignoreUserToggle = false,
        )
    }

    @SuppressLint("MissingPermission")
    fun showNotification(
        context: Context,
        channelId: String,
        notificationId: Int,
        title: String,
        body: String,
        caseId: Long = -1L,
        priority: Int = NotificationCompat.PRIORITY_DEFAULT,
        ignoreUserToggle: Boolean = false,
    ): Boolean {
        val appContext = context.applicationContext
        ensureChannels(appContext)
        if (!hasSystemPermission(appContext)) return false
        val managerCompat = NotificationManagerCompat.from(appContext)
        if (!managerCompat.areNotificationsEnabled()) return false
        if (!ignoreUserToggle && !SecurePrefsManager.getInstance(appContext).settings.value.notificationsEnabled) {
            return false
        }

        val launchIntent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (caseId > 0L) {
                putExtra(EXTRA_CASE_ID, caseId)
                putExtra(EXTRA_NOTIFICATION_TARGET, "case")
            }
        }
        val contentPendingIntent = PendingIntent.getActivity(
            appContext,
            notificationId,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(appContext, channelId)
            .setSmallIcon(R.drawable.ic_stat_redat_dagna)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(priority)
            .setCategory(
                if (channelId == CHANNEL_ADJOURNMENT) NotificationCompat.CATEGORY_REMINDER
                else NotificationCompat.CATEGORY_STATUS
            )
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .build()

        return runCatching {
            managerCompat.notify(notificationId, notification)
            true
        }.getOrDefault(false)
    }

    /**
     * Calculates delay in milliseconds until a court hearing reminder should fire.
     * Targets 08:30 AM local time on the day BEFORE the hearing if still in the future;
     * otherwise targets 07:30 AM local time on the hearing morning itself if still in the future;
     * returns null if the hearing date/morning has already passed or the date string is invalid.
     */
    fun computeAdjournmentReminderDelayMs(
        ethiopianDateStr: String,
        nowMs: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault(),
    ): Long? {
        val parsed = EthiopianCalendarUtil.parseEthiopianDate(ethiopianDateStr) ?: return null
        val (gy, gm, gd) = EthiopianCalendarUtil.ethiopianToGregorian(parsed.year, parsed.month, parsed.day)

        val hearingMorningCal = Calendar.getInstance(timeZone).apply {
            clear()
            set(Calendar.YEAR, gy)
            set(Calendar.MONTH, gm - 1)
            set(Calendar.DAY_OF_MONTH, gd)
            set(Calendar.HOUR_OF_DAY, 7)
            set(Calendar.MINUTE, 30)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val dayBeforeCal = (hearingMorningCal.clone() as Calendar).apply {
            add(Calendar.DAY_OF_MONTH, -1)
            set(Calendar.HOUR_OF_DAY, 8)
            set(Calendar.MINUTE, 30)
        }

        val dayBeforeMs = dayBeforeCal.timeInMillis
        if (dayBeforeMs > nowMs) {
            return dayBeforeMs - nowMs
        }

        val morningMs = hearingMorningCal.timeInMillis
        if (morningMs > nowMs) {
            return morningMs - nowMs
        }

        return null
    }

    fun isDayBeforeReminderTarget(
        ethiopianDateStr: String,
        nowMs: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault(),
    ): Boolean {
        val parsed = EthiopianCalendarUtil.parseEthiopianDate(ethiopianDateStr) ?: return false
        val (gy, gm, gd) = EthiopianCalendarUtil.ethiopianToGregorian(parsed.year, parsed.month, parsed.day)
        val hearingMorning = Calendar.getInstance(timeZone).apply {
            clear()
            set(Calendar.YEAR, gy)
            set(Calendar.MONTH, gm - 1)
            set(Calendar.DAY_OF_MONTH, gd)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return nowMs < hearingMorning
    }
}
