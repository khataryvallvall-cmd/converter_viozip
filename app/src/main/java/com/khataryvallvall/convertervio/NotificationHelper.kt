package com.khataryvallvall.convertervio

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.util.Calendar

// كل ما يخص جدولة التذكير اليومي وإنشاء قناة الإشعارات، في مكان واحد
object NotificationHelper {

    const val CHANNEL_ID = "daily_reminder_channel"
    const val PRICE_ALERT_CHANNEL_ID = "price_alert_channel"
    private const val REQUEST_CODE = 1001

    // الوقت الافتراضي للتذكير اليومي — الساعة 6:00 مساءً بالتوقيت المحلي للجهاز
    // يمكن تغييره لاحقًا بسهولة من هنا فقط
    private const val DEFAULT_HOUR = 18
    private const val DEFAULT_MINUTE = 0

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val dailyChannel = NotificationChannel(
                CHANNEL_ID,
                "تذكير يومي",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "تذكير يومي بسيط لفتح تطبيق Converter Vio"
            }
            manager.createNotificationChannel(dailyChannel)

            val priceChannel = NotificationChannel(
                PRICE_ALERT_CHANNEL_ID,
                "تنبيهات الأسعار",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "تنبيهات عند حدوث تغيّر حاد في أسعار العملات الرقمية"
            }
            manager.createNotificationChannel(priceChannel)
        }
    }

    // يُستدعى من العامل الخلفي (PriceAlertWorker) عند رصد تغيّر يستحق تنبيهًا
    fun showPriceAlert(context: Context, notificationId: Int, title: String, text: String) {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, PRICE_ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    fun scheduleDailyReminder(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, DailyReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, DEFAULT_HOUR)
            set(Calendar.MINUTE, DEFAULT_MINUTE)
            set(Calendar.SECOND, 0)
            // إذا كان الوقت المحدد اليوم قد فات، يبدأ التذكير من الغد
            if (before(Calendar.getInstance())) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        // منبّه غير دقيق بالميلي ثانية (فرق بضع دقائق مقبول لتذكير بسيط)
        // ولا يحتاج صلاحية خاصة من المستخدم على أندرويد 12+
        alarmManager.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            AlarmManager.INTERVAL_DAY,
            pendingIntent
        )
    }

    fun cancelDailyReminder(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, DailyReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }
}
