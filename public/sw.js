// =========================================================================
// ZESTY - SERVICE WORKER (OFFLINE PWA ENGINE)
// =========================================================================

const CACHE_NAME = 'zesty-v3'; // v2: nuova icona

// Asset critici per il caricamento immediato offline
const PRECACHE_ASSETS = [
  './',
  './index.html',
  './manifest.json',
  './assets/aistudio/deepseek_html_20261030_ba07c6_cf515316.html',
  './assets/icons/favicon-32.png',
  './assets/icons/apple-touch-icon.png',
  './assets/icons/icon-maskable-512.png',
  './assets/icons/icon-192.png',
  './assets/icons/icon-512.png'
];

// 1. INSTALL: Scarica e memorizza gli asset critici
self.addEventListener('install', event => {
  event.waitUntil(
    caches.open(CACHE_NAME)
      .then(cache => {
        console.log('[SW] Pre-caching asset Zesty completato');
        return cache.addAll(PRECACHE_ASSETS);
      })
      .then(() => self.skipWaiting())
  );
});

// 2. ACTIVATE: Pulisci vecchie cache se aggiornato
self.addEventListener('activate', event => {
  event.waitUntil(
    caches.keys().then(keys => {
      return Promise.all(
        keys.map(key => {
          if (key !== CACHE_NAME) {
            console.log('[SW] Rimozione vecchia cache:', key);
            return caches.delete(key);
          }
        })
      );
    }).then(() => self.clients.claim())
  );
});

// 3. FETCH: Strategia Cache-First con Network Fallback e Caching Dinamico
self.addEventListener('fetch', event => {
  // Ignora richieste non GET o protocolli diversi da http/https
  if (event.request.method !== 'GET' || !event.request.url.startsWith('http')) {
    return;
  }

  event.respondWith(
    caches.match(event.request)
      .then(cachedResponse => {
        if (cachedResponse) {
          // Trovato in cache! Ritorna subito la risposta offline
          return cachedResponse;
        }

        // Non in cache: prova a scaricarlo dalla rete e memorizzalo per la prossima volta
        return fetch(event.request).then(networkResponse => {
          if (!networkResponse || networkResponse.status !== 200 || networkResponse.type !== 'basic') {
            return networkResponse;
          }

          const responseToCache = networkResponse.clone();
          caches.open(CACHE_NAME).then(cache => {
            cache.put(event.request, responseToCache);
          });

          return networkResponse;
        }).catch(() => {
          // Se la rete è assente e si sta caricando la pagina principale, ritorna l'editor HTML
          if (event.request.mode === 'navigate') {
            return caches.match('./assets/aistudio/deepseek_html_20261030_ba07c6_cf515316.html');
          }
        });
      })
  );
});

