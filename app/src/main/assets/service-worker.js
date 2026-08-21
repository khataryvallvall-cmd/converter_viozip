const CACHE_NAME = 'converter-vio-shell-v1';
const SHELL_FILES = [
  './',
  './index.html',
  './manifest.json',
  './icon-192.png',
  './icon-512.png'
];

// أول مرة يفتح فيها التطبيق بإنترنت، يحفظ نسخة كاملة من الواجهة محلياً
self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME).then((cache) => cache.addAll(SHELL_FILES))
  );
  self.skipWaiting();
});

// حذف أي نسخ قديمة من الكاش عند تحديث الملف
self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys().then((keys) =>
      Promise.all(keys.filter((k) => k !== CACHE_NAME).map((k) => caches.delete(k)))
    )
  );
  self.clients.claim();
});

self.addEventListener('fetch', (event) => {
  const req = event.request;

  // فقط نتعامل مع طلبات GET لملفات نفس الموقع (الواجهة نفسها)
  if (req.method !== 'GET' || new URL(req.url).origin !== self.location.origin) {
    return; // اطلبات API الخارجية (أسعار العملات وغيرها) تمر مباشرة للنت بشكل طبيعي
  }

  event.respondWith(
    caches.match(req).then((cached) => {
      // يفتح فوراً من النسخة المحفوظة إن وجدت (سريع جداً، ويشتغل بدون نت)
      const network = fetch(req)
        .then((res) => {
          caches.open(CACHE_NAME).then((cache) => cache.put(req, res.clone()));
          return res;
        })
        .catch(() => cached); // لو مافيش نت، يرجع للنسخة المحفوظة

      return cached || network;
    })
  );
});
