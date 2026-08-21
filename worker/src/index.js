/**
 * SelfGuard Reputationsdienst.
 *
 * Zweck: Die App soll Datei-Pruefsummen gegen MalwareBazaar und VirusTotal
 * pruefen koennen, ohne dass ein API-Schluessel im APK liegt. Ein Schluessel
 * im Programmcode ist in Minuten ausgelesen -- deshalb kennt die App nur die
 * Adresse dieses Workers, und die Schluessel liegen ausschliesslich hier als
 * Cloudflare-Secrets.
 *
 * Einrichtung:
 *   npx wrangler secret put MALWAREBAZAAR_KEY
 *   npx wrangler secret put VIRUSTOTAL_KEY      (freiwillig)
 *   npx wrangler deploy
 *
 * Schnittstelle:
 *   GET /v1/hash/<sha256>
 *   -> {"known":false}
 *   -> {"known":true,"source":"MalwareBazaar","family":"Cerberus","detections":0}
 */

const SHA256_PATTERN = /^[a-f0-9]{64}$/;

/** Treffer werden lange, Nicht-Treffer kurz zwischengespeichert. */
const CACHE_TTL_HIT_SECONDS = 60 * 60 * 24 * 7;
const CACHE_TTL_MISS_SECONDS = 60 * 60 * 6;

/**
 * VirusTotal meldet fuer nahezu jede Datei ein paar Fehlalarme einzelner
 * Motoren. Erst ab mehreren unabhaengigen Erkennungen ist die Aussage
 * belastbar -- darunter wuerde die App bei harmlosen Dateien Alarm schlagen.
 */
const VIRUSTOTAL_MIN_DETECTIONS = 3;

/** Zeitfenster der Durchsatzbegrenzung, passend zu wrangler.toml. */
const RATE_LIMIT_WINDOW_SECONDS = 60;

export default {
  async fetch(request, env, ctx) {
    if (request.method !== 'GET') {
      return json({ error: 'nur GET' }, 405);
    }

    // Der Dienst haengt an fremden Kontingenten: VirusTotal erlaubt kostenlos
    // rund 500 Abfragen am Tag, MalwareBazaar arbeitet nach Fair Use. Ohne
    // Begrenzung koennte jeder, der die Adresse kennt, beides leerlaufen
    // lassen -- und der Dienst waere fuer den eigentlichen Zweck tot.
    const limited = await rateLimited(request, env);
    if (limited) {
      return json(
        { error: 'zu viele Anfragen' },
        429,
        0,
        { 'Retry-After': String(RATE_LIMIT_WINDOW_SECONDS) },
      );
    }

    const url = new URL(request.url);
    const match = url.pathname.match(/^\/v1\/hash\/([a-fA-F0-9]{64})$/);
    if (!match) {
      return json({ error: 'Pfad erwartet /v1/hash/<sha256>' }, 400);
    }

    const hash = match[1].toLowerCase();
    if (!SHA256_PATTERN.test(hash)) {
      return json({ error: 'keine gueltige SHA-256' }, 400);
    }

    // Cloudflares Randspeicher haelt die Antwort, damit dieselbe Pruefsumme
    // nicht wiederholt gegen die Fremdkonten laeuft.
    const cache = caches.default;
    const cacheKey = new Request(url.toString(), request);
    const cached = await cache.match(cacheKey);
    if (cached) return cached;

    const verdict =
      (await lookupMalwareBazaar(hash, env)) ??
      (await lookupVirusTotal(hash, env)) ??
      { known: false };

    const ttl = verdict.known ? CACHE_TTL_HIT_SECONDS : CACHE_TTL_MISS_SECONDS;
    const response = json(verdict, 200, ttl);
    ctx.waitUntil(cache.put(cacheKey, response.clone()));
    return response;
  },
};

/**
 * MalwareBazaar von abuse.ch. Kostenlos nutzbar, benoetigt seit 2024 einen
 * Auth-Key von https://auth.abuse.ch/.
 *
 * Wird zuerst gefragt, weil der Datensatz auf Android-Schadsoftware deutlich
 * ergiebiger ist als der Querschnitt bei VirusTotal.
 */
async function lookupMalwareBazaar(hash, env) {
  if (!env.MALWAREBAZAAR_KEY) return null;

  const response = await fetch('https://mb-api.abuse.ch/api/v1/', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded',
      'Auth-Key': env.MALWAREBAZAAR_KEY,
    },
    body: new URLSearchParams({ query: 'get_info', hash }),
  }).catch(() => null);

  if (!response || !response.ok) return null;

  const body = await response.json().catch(() => null);
  if (!body || body.query_status !== 'ok') return null;

  const entry = Array.isArray(body.data) ? body.data[0] : null;
  if (!entry) return null;

  return {
    known: true,
    source: 'MalwareBazaar',
    family: entry.signature ?? null,
    detections: 0,
  };
}

/**
 * VirusTotal als zweite Meinung. Der kostenlose Zugang erlaubt rund vier
 * Abfragen je Minute und 500 am Tag -- deshalb nur als Rueckfallebene und
 * hinter dem Zwischenspeicher.
 */
async function lookupVirusTotal(hash, env) {
  if (!env.VIRUSTOTAL_KEY) return null;

  const response = await fetch(`https://www.virustotal.com/api/v3/files/${hash}`, {
    headers: { 'x-apikey': env.VIRUSTOTAL_KEY },
  }).catch(() => null);

  // 404 bedeutet schlicht: unbekannte Datei, kein Fehler.
  if (!response || !response.ok) return null;

  const body = await response.json().catch(() => null);
  const stats = body?.data?.attributes?.last_analysis_stats;
  if (!stats) return null;

  const detections = (stats.malicious ?? 0) + (stats.suspicious ?? 0);
  if (detections < VIRUSTOTAL_MIN_DETECTIONS) return null;

  return {
    known: true,
    source: 'VirusTotal',
    family: body?.data?.attributes?.popular_threat_classification
      ?.suggested_threat_label ?? null,
    detections,
  };
}

/**
 * Begrenzt die Abfragen je Absenderadresse.
 *
 * Nutzt das Rate-Limiting-Binding aus `wrangler.toml`. Fehlt das Binding --
 * etwa bei einer aelteren Wrangler-Fassung -- laesst der Dienst die Anfrage
 * durch, statt alles zu blockieren. Das ist die bewusste Entscheidung: ein
 * Reputationsdienst, der nach einer Konfigurationsluecke gar nichts mehr
 * beantwortet, faellt beim Nutzer als kaputte App auf, waehrend das Kontingent
 * ohnehin durch den Zwischenspeicher geschuetzt bleibt.
 */
async function rateLimited(request, env) {
  if (!env.REPUTATION_LIMITER) return false;

  const clientIp = request.headers.get('CF-Connecting-IP') ?? 'unbekannt';
  const { success } = await env.REPUTATION_LIMITER.limit({ key: clientIp });
  return !success;
}

function json(payload, status = 200, cacheSeconds = 0, extraHeaders = {}) {
  const headers = {
    'Content-Type': 'application/json; charset=utf-8',
    // Der Dienst beantwortet nur Pruefsummen und speichert nichts vom Nutzer.
    'Cache-Control': cacheSeconds > 0 ? `public, max-age=${cacheSeconds}` : 'no-store',
    // Der Dienst wird von der App aufgerufen, nie aus einem Browser-Kontext.
    // Ohne CORS-Freigabe kann keine fremde Webseite ihn im Namen eines
    // Besuchers abfragen.
    'X-Content-Type-Options': 'nosniff',
    ...extraHeaders,
  };
  return new Response(JSON.stringify(payload), { status, headers });
}
