# لازم نحافظ على أي كلاس فيه @JavascriptInterface وأسماء الدوال بتاعته زي ما هي —
# لو R8 غيّر أو حذف الأسماء دي، كود الجافاسكريبت (اللي بينادي عليها بالاسم من جوه WebView)
# هيفشل فورًا لأنه مش هيلاقي الدالة بنفس الاسم القديم.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# نحافظ كمان على الكلاس نفسه (ClipboardBridge) عشان الـ reflection call من WebView يلاقيه
-keep class com.khataryvallvall.convertervio.MainActivity$ClipboardBridge { *; }
