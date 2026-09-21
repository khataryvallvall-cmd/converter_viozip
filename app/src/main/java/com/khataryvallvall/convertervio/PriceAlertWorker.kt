package com.khataryvallvall.convertervio

import android.content.Context
import android.content.SharedPreferences
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.abs

// يراقب أسعار العملات الرقمية الرئيسية في الخلفية، ويطلق إشعارًا فقط عند
// حركة سعرية "حادة"، أو عند تغيّر ملحوظ في عملة المستخدم المفضّلة تحديدًا
class PriceAlertWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    companion object {
        const val PREFS_NAME = "price_alerts_prefs"
        const val KEY_FAVORITE_SYMBOL = "favorite_symbol"

        // نفس العملات الظاهرة في شاشة الأسعار الرئيسية بالتطبيق
        val WATCHED_SYMBOLS = listOf("BTCUSDT", "ETHUSDT", "BNBUSDT", "XRPUSDT")

        // نسبة تغيّر تُعتبر "حادة" وتستحق تنبيهًا للعملات العامة
        const val SHARP_MOVE_THRESHOLD_PERCENT = 5.0
        // نسبة أقل تكفي لتنبيه خاص بالعملة المفضّلة، لأنها أهم للمستخدم
        const val FAVORITE_MOVE_THRESHOLD_PERCENT = 2.0
    }

    override fun doWork(): Result {
        return try {
            val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val favoriteSymbol = prefs.getString(KEY_FAVORITE_SYMBOL, null)

            val symbolsToCheck = LinkedHashSet(WATCHED_SYMBOLS)
            if (!favoriteSymbol.isNullOrBlank()) symbolsToCheck.add(favoriteSymbol)

            val prices = fetchPrices(symbolsToCheck)

            for (symbol in symbolsToCheck) {
                val price = prices[symbol] ?: continue
                val isFavorite = symbol == favoriteSymbol
                val threshold = if (isFavorite) FAVORITE_MOVE_THRESHOLD_PERCENT else SHARP_MOVE_THRESHOLD_PERCENT
                checkAndNotify(prefs, symbol, price, threshold, isFavorite)
            }

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun fetchPrices(symbols: Set<String>): Map<String, Double> {
        val url = URL("https://api.binance.com/api/v3/ticker/price")
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 15000
        connection.readTimeout = 15000

        val text = connection.inputStream.bufferedReader().use { it.readText() }
        connection.disconnect()

        val array = JSONArray(text)
        val result = mutableMapOf<String, Double>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val symbol = obj.getString("symbol")
            if (symbol in symbols) {
                result[symbol] = obj.getString("price").toDouble()
            }
        }
        return result
    }

    private fun checkAndNotify(
        prefs: SharedPreferences,
        symbol: String,
        currentPrice: Double,
        thresholdPercent: Double,
        isFavorite: Boolean
    ) {
        val refKey = "ref_price_$symbol"
        val refPrice = prefs.getFloat(refKey, -1f).toDouble()

        if (refPrice <= 0) {
            // أول مرة تُراقب فيها هذه العملة — تُحفظ كنقطة مرجعية فقط، دون إشعار
            prefs.edit().putFloat(refKey, currentPrice.toFloat()).apply()
            return
        }

        val changePercent = ((currentPrice - refPrice) / refPrice) * 100.0

        if (abs(changePercent) >= thresholdPercent) {
            val coinName = symbol.removeSuffix("USDT")
            val isUp = changePercent >= 0
            val roundedChange = String.format("%.1f", abs(changePercent))
            val roundedPrice = if (currentPrice >= 1)
                String.format("%,.2f", currentPrice)
            else
                currentPrice.toString()

            val lang = LocaleHelper.getLanguage(applicationContext)
            val (title, text) = buildLocalizedAlert(lang, coinName, isUp, isFavorite, roundedChange, roundedPrice)

            NotificationHelper.showPriceAlert(applicationContext, symbol.hashCode(), title, text)

            // تُحدَّث نقطة المرجع إلى السعر الحالي حتى لا يتكرر نفس التنبيه
            // إلا بعد حركة إضافية بنفس الحجم من هذه النقطة الجديدة
            prefs.edit().putFloat(refKey, currentPrice.toFloat()).apply()
        }
    }

    private fun buildLocalizedAlert(
        lang: String,
        coinName: String,
        isUp: Boolean,
        isFavorite: Boolean,
        changePercent: String,
        price: String
    ): Pair<String, String> {
        return when (lang) {
            "en" -> {
                val direction = if (isUp) "rise" else "drop"
                val title = if (isFavorite) "Update on your favorite coin: $coinName" else "Sharp $direction in $coinName"
                val text = "${direction.replaceFirstChar { it.uppercase() }} of $changePercent% — current price: $$price"
                title to text
            }
            "fr" -> {
                val direction = if (isUp) "hausse" else "baisse"
                val title = if (isFavorite) "Mise à jour de votre crypto favorite : $coinName" else "Forte $direction de $coinName"
                val text = "${direction.replaceFirstChar { it.uppercase() }} de $changePercent% — prix actuel : $$price"
                title to text
            }
            else -> {
                val direction = if (isUp) "ارتفاع" else "انخفاض"
                val title = if (isFavorite) "تحديث في عملتك المفضّلة: $coinName" else "$direction حاد في $coinName"
                val text = "$direction بنسبة $changePercent% — السعر الحالي: $$price"
                title to text
            }
        }
    }
}
