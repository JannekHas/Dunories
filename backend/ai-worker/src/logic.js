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

/** Firebase-ID-Token: Inhalt prüfen (Signatur prüft index.js). @return {uid, anonymous, email} oder null */
export function checkClaims(payload, projectId, nowSeconds) {
  if (!payload || typeof payload !== "object") return null;
  if (payload.iss !== `https://securetoken.google.com/${projectId}`) return null;
  if (payload.aud !== projectId) return null;
  if (typeof payload.exp !== "number" || payload.exp < nowSeconds - 30) return null;
  if (typeof payload.iat !== "number" || payload.iat > nowSeconds + 300) return null;
  if (typeof payload.sub !== "string" || payload.sub.length === 0 || payload.sub.length > 128) return null;
  const provider = payload.firebase?.sign_in_provider;
  const email = typeof payload.email === "string" && payload.email_verified !== false ? payload.email.toLowerCase() : null;
  return { uid: payload.sub, anonymous: provider === "anonymous" || !email, email };
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

/** Feedback-Arten und erlaubte Punkte pro Feedback (Tester-Belohnungsprogramm). */
export const FEEDBACK_TYPES = new Set(["bug", "idea", "praise"]);
export const FEEDBACK_POINTS = new Set([-1, 0, 1, 3, 5]);
export const MAX_FEEDBACK_TEXT = 5000;
export const MAX_FEEDBACK_NAME = 60;
export const MAX_SCREENSHOT = 800000;
/**
 * Nur gegen Programme, die automatisch fluten: höchstens so viele Feedbacks pro Person und Minute.
 * Kein Tageslimit (Wunsch des Nutzers) – Spammer sperrt er im Dashboard.
 */
export const MAX_FEEDBACK_PER_MINUTE = 5;

/** Feedback aus der App prüfen und auf erlaubte Felder kürzen. @return Objekt oder null */
export function cleanFeedback(body) {
  if (!body || typeof body !== "object" || !FEEDBACK_TYPES.has(body.type)) return null;
  const text = String(body.text ?? "").trim().slice(0, MAX_FEEDBACK_TEXT);
  const shot = typeof body.screenshot === "string" && body.screenshot.length > 0 ? body.screenshot : null;
  if (shot && (shot.length > MAX_SCREENSHOT || !/^[A-Za-z0-9+/=]+$/.test(shot))) return null;
  if (!text && !shot) return null;
  return {
    type: body.type,
    text,
    name: String(body.name ?? "").trim().slice(0, MAX_FEEDBACK_NAME),
    credits: body.credits === true,
    screenshot: shot,
    appVersion: String(body.appVersion ?? "").slice(0, 20),
    versionCode: Number.isInteger(body.versionCode) ? body.versionCode : 0,
    device: String(body.device ?? "").slice(0, 80),
    android: Number.isInteger(body.android) ? body.android : 0,
  };
}

/** Code-Alphabet ohne verwechselbare Zeichen (0/O, 1/I/L). */
const CODE_CHARS = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";

/** Höchstzahl Freunde pro Person. */
export const MAX_FRIENDS = 100;

/** Freundescode „ABCD-2345“ (8 Zeichen, ohne verwechselbare Zeichen). */
export function makeFriendCode(bytes) {
  let out = "";
  for (let i = 0; i < 8; i++) out += CODE_CHARS[bytes[i] % CODE_CHARS.length];
  return `${out.slice(0, 4)}-${out.slice(4)}`;
}

/** Eingabe vereinheitlichen; null = kein gültiger Freundescode. */
export function normalizeFriendCode(input) {
  const raw = String(input ?? "").toUpperCase().replace(/[^A-Z0-9]/g, "");
  if (raw.length !== 8 || [...raw].some((c) => !CODE_CHARS.includes(c))) return null;
  return `${raw.slice(0, 4)}-${raw.slice(4)}`;
}

/**
 * Profil aus der App prüfen. Geteilt wird nur, was die Person einzeln freigibt (Standard: privat).
 * Gewicht und Kalorien gibt es hier gar nicht – sie können also nie geteilt werden.
 */
export function cleanSocialProfile(body) {
  if (!body || typeof body !== "object") return null;
  const name = String(body.name ?? "").trim().slice(0, 60);
  const keys = Array.isArray(body.achievements) ? body.achievements.filter((k) => typeof k === "string" && /^[a-z0-9_]{1,30}$/.test(k)).slice(0, 100) : [];
  const badge = typeof body.badge === "string" && /^[a-z0-9_]{1,30}$/.test(body.badge) ? body.badge : null;
  const streak = Number.isInteger(body.streak) && body.streak >= 0 && body.streak < 100000 ? body.streak : 0;
  const share = body.share && typeof body.share === "object" ? body.share : {};
  return {
    name,
    badge,
    streak,
    achievements: keys,
    shareBadges: share.badges === true,
    shareAchievements: share.achievements === true,
    shareStreak: share.streak === true,
  };
}

/** Was ein Freund von dieser Person sieht – nur Freigegebenes. */
export function friendView(p) {
  return {
    code: p.code,
    name: p.name || "",
    badge: p.shareBadges ? p.badge : null,
    achievements: p.shareAchievements ? p.achievements : null,
    streak: p.shareStreak ? p.streak : null,
  };
}


/** Neuer Code „DUNO-XXXX-XXXX“ aus Zufallsbytes. */
export function makeCode(bytes) {
  let out = "";
  for (let i = 0; i < 8; i++) out += CODE_CHARS[bytes[i] % CODE_CHARS.length];
  return `DUNO-${out.slice(0, 4)}-${out.slice(4)}`;
}

/** Eingabe des Nutzers vereinheitlichen (Groß, ohne Leerzeichen, Bindestriche egal). */
export function normalizeCode(input) {
  const raw = String(input ?? "").toUpperCase().replace(/[^A-Z0-9]/g, "");
  const body = raw.startsWith("DUNO") ? raw.slice(4) : raw;
  return body.length === 8 ? `DUNO-${body.slice(0, 4)}-${body.slice(4)}` : null;
}

/**
 * Stufe einer Person. ent = Freischaltung {lifetime, until} oder null, trialStart = Beginn der Probezeit (ms) oder null.
 * @return {tier: "premium"|"guest"|"free", source, until, lifetime}
 */
export function tierOf({ now, anonymous, testAllPremium, ent, trialStart, trialDays }) {
  if (testAllPremium) return { tier: "premium", source: "test", until: null, lifetime: false };
  if (ent && (ent.lifetime || ent.until > now)) {
    return { tier: "premium", source: ent.lifetime ? "lifetime" : ent.source, until: ent.lifetime ? null : ent.until, lifetime: !!ent.lifetime };
  }
  if (!anonymous && trialStart != null) {
    const until = trialStart + trialDays * 86400000;
    if (until > now) return { tier: "premium", source: "trial", until, lifetime: false };
  }
  return { tier: anonymous ? "guest" : "free", source: null, until: null, lifetime: false };
}

/** Neue Freischaltung auf eine bestehende draufrechnen (Restzeit geht nicht verloren). */
export function extend(ent, now, days) {
  if (ent?.lifetime) return { lifetime: true, until: null };
  if (days === 0) return { lifetime: true, until: null };
  const base = ent && ent.until > now ? ent.until : now;
  return { lifetime: false, until: base + days * 86400000 };
}

/**
 * Darf dieser Aufruf laufen? usedToday = schon gezählte Aufrufe heute, alreadyCounted = gleiche Anfrage-Kennung
 * (Wiederholung/zweite Spur) wurde schon gezählt, weeklyDone = automatische Auswertung diese Woche schon gelaufen,
 * freeLeft = übrige Gratis-Aufrufe (nur Gast).
 */
export function decide({ kind, tier, usedToday, limit, alreadyCounted, weeklyDone, freeLeft }) {
  if (kind === "weekly-auto") {
    if (tier !== "premium") return "needs_premium";
    return weeklyDone ? "weekly_done" : "ok";
  }
  if (alreadyCounted) return "ok";
  if (tier === "guest" && freeLeft <= 0) return "needs_account";
  if (tier === "free") return "needs_premium";
  return usedToday >= limit ? "daily_limit" : "ok";
}
