package com.khataryvallvall.convertervio

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private val ALLOWED_URL = "file:///android_asset/index.html"

    // طلب صلاحية الإشعارات وقت التشغيل (مطلوب فقط من أندرويد 13 فما فوق)
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            NotificationHelper.scheduleDailyReminder(this)
        }
    }

    private fun setupDailyReminder() {
        NotificationHelper.createNotificationChannel(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (granted) {
                NotificationHelper.scheduleDailyReminder(this)
            } else {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            // ما قبل أندرويد 13 ما فيه صلاحية وقت تشغيل مطلوبة للإشعارات
            NotificationHelper.scheduleDailyReminder(this)
        }
    }

    // جسر يسمح لصفحة الويب بإخبار الجهة الأصلية بالعملة الرقمية المفضّلة الحالية
    // (يُقرأ من شارة العملة الحية في الصفحة الرئيسية) لمراقبتها في الخلفية
    inner class PriceAlertBridge(private val context: Context) {
        @JavascriptInterface
        fun setFavoriteSymbol(symbol: String) {
            val prefs = context.getSharedPreferences(
                PriceAlertWorker.PREFS_NAME,
                Context.MODE_PRIVATE
            )
            prefs.edit().putString(PriceAlertWorker.KEY_FAVORITE_SYMBOL, symbol).apply()
        }
    }

    // جسر يفتح صفحة تحديث متصفح Chrome في متجر Play بأمان، متجاوزًا قفل
    // الـWebView الذي يمنع أي تنقّل خارجي (موجود أصلاً لحماية التطبيق)
    inner class SystemActionsBridge(private val context: Context) {
        @JavascriptInterface
        fun openChromeUpdatePage() {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.android.chrome"))
                intent.setPackage("com.android.vending")
                context.startActivity(intent)
            } catch (e: Exception) {
                try {
                    val fallback = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://play.google.com/store/apps/details?id=com.android.chrome")
                    )
                    context.startActivity(fallback)
                } catch (e2: Exception) {
                    // لا متجر متاح على الجهاز — لا يوجد إجراء بديل ممكن
                }
            }
        }
    }

    // جسر يسمح لصفحة الويب بإخبار الجهة الأصلية باللغة الحالية (ar/en/fr)
    // لبناء نصوص الإشعارات بنفس لغة المستخدم داخل التطبيق
    inner class LocaleBridge(private val context: Context) {
        @JavascriptInterface
        fun setLanguage(lang: String) {
            LocaleHelper.setLanguage(context, lang)
        }
    }

    private fun schedulePriceAlerts() {
        // فحص كل 3 ساعات: كافٍ لرصد الحركات الحادة دون استنزاف البطارية أو الإزعاج
        val request = PeriodicWorkRequestBuilder<PriceAlertWorker>(3, TimeUnit.HOURS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "price_alert_worker",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    inner class ClipboardBridge(private val context: Context) {
        @JavascriptInterface
        fun getClipboardText(): String {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = cm.primaryClip
            if (clip != null && clip.itemCount > 0) {
                return clip.getItemAt(0).coerceToText(context).toString()
            }
            return ""
        }

        @JavascriptInterface
        fun setClipboardText(text: String) {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("Converter Vio", text))
        }
    }

    // يمنع أي محاولة تنقّل داخل الـWebView لأي صفحة غير صفحتنا المحلية —
    // حتى لو حصل أي خلل مستقبلي (زي كود إعلان فيه رابط)، الصفحة الخارجية
    // مش هتقدر توصل لأي حاجة، لأنها أصلاً مش هتتحمّل جوه التطبيق
    inner class LockedWebViewClient : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            return request.url.toString() != ALLOWED_URL
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // شاشة بداية سودا صافية بدون أيقونة أو اسم — بتختفي فورًا
        installSplashScreen()
        super.onCreate(savedInstanceState)

        // إغلاق صريح لأدوات فحص/تنقيح WebView عن بُعد في نسخة الإصدار النهائي
        WebView.setWebContentsDebuggingEnabled(false)

        webView = WebView(this)
        webView.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        setContentView(webView)

        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.allowFileAccessFromFileURLs = false
        settings.allowUniversalAccessFromFileURLs = false
        settings.mediaPlaybackRequiresUserGesture = false

        webView.webViewClient = LockedWebViewClient()
        webView.webChromeClient = WebChromeClient()
        webView.addJavascriptInterface(ClipboardBridge(this), "AndroidClipboard")
        webView.addJavascriptInterface(PriceAlertBridge(this), "AndroidPriceAlerts")
        webView.addJavascriptInterface(LocaleBridge(this), "AndroidLocale")
        webView.addJavascriptInterface(SystemActionsBridge(this), "AndroidSystem")

        webView.loadUrl(ALLOWED_URL)

        setupDailyReminder()
        schedulePriceAlerts()
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
