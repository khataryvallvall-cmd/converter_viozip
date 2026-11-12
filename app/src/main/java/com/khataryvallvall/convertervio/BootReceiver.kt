package com.khataryvallvall.convertervio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // إعادة الجدولة بعد إعادة التشغيل أو تغيير المنطقة الزمنية/الوقت أو تحديث التطبيق
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_TIMEZONE_CHANGED ||
            intent.action == Intent.ACTION_TIME_CHANGED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            NotificationHelper.createNotificationChannel(context)
            NotificationHelper.scheduleDailyReminder(context)
        }
    }
}
