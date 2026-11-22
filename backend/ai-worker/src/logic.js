// Reine Hilfsfunktionen (ohne Cloudflare-Abhängigkeiten) – getestet mit `node --test`.

/** Erlaubte Gemini-Modelle (die App nutzt nur Flash Lite). */
export const MODELS = new Set(["gemini-3.1-flash-lite", "gemini-3.1-flash-lite-preview", "gemini-flash-lite-latest"]);

/** Art des KI-Aufrufs (Header X-Ai-Kind). weekly-auto = automatische Wochenauswertung, zählt nicht mit. */
export const KINDS = new Set(["food", "body", "plan", "recipe", "weekly", "weekly-auto"]);

/** Größte erlaubte Anfrage (Fotos/PDF inline): 20 MB. */
export const MAX_BODY = 20 * 1024 * 1024;

/** Kalendertag in Deutschland als „YYYY-MM-DD“ – nach der Uhr des Servers, nie des Handys. */
export function berlinDay(ms) {
  return new Intl.DateTimeFormat("en-CA", { timeZone: "Europe/Berlin", year: "numeric", month: "2-digit", day: "2-digit" })
    .format(new Date(ms));
}

/** ISO-Woche („2026-W47“) zu einem Tag „YYYY-MM-DD“. */
export function isoWeek(day) {
  const [y, m, d] = day.split("-").map(Number);
  const date = new Date(Date.UTC(y, m - 1, d));
  const weekday = date.getUTCDay() || 7;
  date.setUTCDate(date.getUTCDate() + 4 - weekday);
  const yearStart = new Date(Date.UTC(date.getUTCFullYear(), 0, 1));
  const week = Math.ceil(((date - yearStart) / 86400000 + 1) / 7);
  return `${date.getUTCFullYear()}-W${String(week).padStart(2, "0")}`;
}

/** Tag minus n Tage („YYYY-MM-DD“). */
export function minusDays(day, n) {
  const [y, m, d] = day.split("-").map(Number);
  return new Date(Date.UTC(y, m - 1, d - n)).toISOString().slice(0, 10);
}

/** Pfad /v1/models/{model}:generateContent → Modell (nur erlaubte), sonst null. */
export function modelFromPath(path) {
  const match = /^\/v1\/models\/([a-z0-9.\-]+):generateContent$/.exec(path);
  return match && MODELS.has(match[1]) ? match[1] : null;
}

/** Dateiname „files/abc123“ aus /v1/files/abc123, sonst null. */
export function fileFromPath(path) {
  const match = /^\/v1\/(files\/[a-z0-9\-]{1,64})$/.exec(path);
  return match ? match[1] : null;
}

/** Firebase-ID-Token: Inhalt prüfen (Signatur prüft index.js). @return uid oder null */
export function checkClaims(payload, projectId, nowSeconds) {
  if (!payload || typeof payload !== "object") return null;
  if (payload.iss !== `https://securetoken.google.com/${projectId}`) return null;
  if (payload.aud !== projectId) return null;
  if (typeof payload.exp !== "number" || payload.exp < nowSeconds - 30) return null;
  if (typeof payload.iat !== "number" || payload.iat > nowSeconds + 300) return null;
  if (typeof payload.sub !== "string" || payload.sub.length === 0 || payload.sub.length > 128) return null;
  return payload.sub;
}

export function base64UrlDecode(text) {
  const b64 = text.replace(/-/g, "+").replace(/_/g, "/") + "===".slice((text.length + 3) % 4);
  const bin = atob(b64);
  const bytes = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
  return bytes;
}

/** Vergleich ohne Zeitunterschied je nach Übereinstimmung (für den Admin-Schlüssel). */
export function safeEqual(a, b) {
  if (typeof a !== "string" || typeof b !== "string") return false;
  let diff = a.length ^ b.length;
  for (let i = 0; i < Math.max(a.length, b.length); i++) diff |= (a.charCodeAt(i) || 0) ^ (b.charCodeAt(i) || 0);
  return diff === 0;
}

/**
 * Darf dieser Aufruf laufen? usedToday = schon gezählte Aufrufe heute, alreadyCounted = gleiche Anfrage-Kennung
 * (Wiederholung/zweite Spur) wurde schon gezählt, weeklyDone = automatische Auswertung diese Woche schon gelaufen.
 */
export function decide({ kind, usedToday, limit, alreadyCounted, weeklyDone }) {
  if (kind === "weekly-auto") return weeklyDone ? "weekly_done" : "ok";
  if (alreadyCounted) return "ok";
  return usedToday >= limit ? "daily_limit" : "ok";
}
