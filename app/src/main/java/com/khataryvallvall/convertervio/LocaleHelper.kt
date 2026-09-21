package com.khataryvallvall.convertervio

import android.content.Context

// يحفظ لغة واجهة التطبيق الحالية (كما اختارها المستخدم داخل الصفحة) لاستخدامها
// عند بناء نصوص الإشعارات (التذكير اليومي وتنبيهات الأسعار)
object LocaleHelper {
    private const val PREFS_NAME = "app_locale_prefs"
    private const val KEY_LANG = "app_lang"
    val SUPPORTED_LANGS = setOf("ar", "en", "fr")

    fun setLanguage(context: Context, lang: String) {
        val normalized = if (lang in SUPPORTED_LANGS) lang else "ar"
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANG, normalized)
            .apply()
    }

    fun getLanguage(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LANG, "ar") ?: "ar"
    }
}
