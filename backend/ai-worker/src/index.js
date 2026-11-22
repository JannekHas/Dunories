// Dunories KI-Worker: hält den Gemini-Schlüssel, prüft das Firebase-Token (auch Gast-Konten),
// zählt KI-Aufrufe pro Person und Tag (Uhr des Servers, deutsche Zeit) und leitet an Gemini weiter.
// Inhalte (Fotos, Texte, Antworten) werden weder gespeichert noch geloggt – nur Zähler.
import { DurableObject } from "cloudflare:workers";
import {
  KINDS, MAX_BODY, base64UrlDecode, berlinDay, checkClaims, decide, fileFromPath, isoWeek, minusDays, modelFromPath, safeEqual,
} from "./logic.js";

const GEMINI = "https://generativelanguage.googleapis.com";
const JWK_URL = "https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com";
const MAX_VIDEO = 300 * 1024 * 1024;
/** Zähler werden nach so vielen Tagen gelöscht (steht so in der Datenschutzerklärung). */
const KEEP_DAYS = 90;

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    try {
      if (url.pathname.startsWith("/admin/")) return await admin(request, env, url);
      return await app(request, env, url);
    } catch (e) {
      return json({ error: "server" }, 500);
    }
  },
};

// ---------- App ----------

async function app(request, env, url) {
  const uid = await verifyToken(request.headers.get("Authorization"), env.FIREBASE_PROJECT_ID);
  if (!uid) return json({ error: "unauthorized" }, 401);
  const usage = usageObject(env);
  const now = Date.now();
  const day = berlinDay(now);

  if (url.pathname === "/v1/usage" && request.method === "GET") {
    return json(await usage.usage(uid, day, defaultLimit(env)));
  }

  const model = modelFromPath(url.pathname);
  if (model && request.method === "POST") {
    const kind = request.headers.get("X-Ai-Kind") ?? "";
    const req = request.headers.get("X-Ai-Request") ?? "";
    if (!KINDS.has(kind) || !/^[A-Za-z0-9\-]{8,64}$/.test(req)) return json({ error: "bad_request" }, 400);
    const length = Number(request.headers.get("Content-Length") ?? "NaN");
    if (!Number.isFinite(length)) return json({ error: "length_required" }, 411);
    if (length > MAX_BODY) return json({ error: "too_large" }, 413);

    const week = isoWeek(day);
    const check = await usage.check(uid, req, kind, day, week, defaultLimit(env));
    if (check.result !== "ok") {
      return json({ error: check.result, used: check.used, limit: check.limit }, 429, { "X-Dunories-Limit": check.result });
    }
    const upstream = await fetch(`${GEMINI}/v1beta/models/${model}:generateContent`, {
      method: "POST",
      headers: { "Content-Type": "application/json", "x-goog-api-key": env.GEMINI_API_KEY },
      body: request.body,
    });
    const body = await upstream.arrayBuffer();
    if (upstream.ok) await usage.record(uid, req, kind, day, week);
    else await usage.error(day, upstream.status);
    return new Response(body, { status: upstream.status, headers: { "Content-Type": "application/json" } });
  }

  // Videos (Rezept-Import): Upload starten – die Upload-Adresse von Google braucht danach keinen Schlüssel mehr.
  if (url.pathname === "/v1/files:start" && request.method === "POST") {
    const length = Number(request.headers.get("X-Upload-Length") ?? "NaN");
    const type = request.headers.get("X-Upload-Type") ?? "";
    if (!Number.isFinite(length) || length <= 0 || length > MAX_VIDEO || !/^video\/[a-z0-9.+\-]+$/.test(type)) {
      return json({ error: "bad_request" }, 400);
    }
    const start = await fetch(`${GEMINI}/upload/v1beta/files`, {
      method: "POST",
      headers: {
        "x-goog-api-key": env.GEMINI_API_KEY,
        "X-Goog-Upload-Protocol": "resumable",
        "X-Goog-Upload-Command": "start",
        "X-Goog-Upload-Header-Content-Length": String(length),
        "X-Goog-Upload-Header-Content-Type": type,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ file: { display_name: "rezept-video" } }),
    });
    const uploadUrl = start.headers.get("X-Goog-Upload-URL");
    if (!start.ok || !uploadUrl) return json({ error: "upstream" }, start.ok ? 502 : start.status);
    return json({ uploadUrl });
  }

  const file = fileFromPath(url.pathname);
  if (file && (request.method === "GET" || request.method === "DELETE")) {
    const upstream = await fetch(`${GEMINI}/v1beta/${file}`, { method: request.method, headers: { "x-goog-api-key": env.GEMINI_API_KEY } });
    return new Response(await upstream.arrayBuffer(), { status: upstream.status, headers: { "Content-Type": "application/json" } });
  }

  return json({ error: "not_found" }, 404);
}

function usageObject(env) {
  return env.USAGE.get(env.USAGE.idFromName("main"));
}

function defaultLimit(env) {
  const n = Number(env.DEFAULT_DAILY_LIMIT);
  return Number.isFinite(n) && n > 0 ? n : 40;
}

// ---------- Firebase-Token ----------

let jwkCache = { keys: new Map(), until: 0 };

async function signingKey(kid) {
  if (Date.now() > jwkCache.until || !jwkCache.keys.has(kid)) {
    const response = await fetch(JWK_URL);
    if (!response.ok) return null;
    const { keys } = await response.json();
    const map = new Map();
    for (const jwk of keys ?? []) {
      const key = await crypto.subtle.importKey("jwk", jwk, { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["verify"]);
      map.set(jwk.kid, key);
    }
    const maxAge = Number(/max-age=(\d+)/.exec(response.headers.get("Cache-Control") ?? "")?.[1] ?? 3600);
    jwkCache = { keys: map, until: Date.now() + Math.min(maxAge, 6 * 3600) * 1000 };
  }
  return jwkCache.keys.get(kid) ?? null;
}

/** @return uid oder null */
async function verifyToken(header, projectId) {
  const token = /^Bearer (.+)$/.exec(header ?? "")?.[1];
  if (!token || token.length > 4096) return null;
  const parts = token.split(".");
  if (parts.length !== 3) return null;
  try {
    const head = JSON.parse(new TextDecoder().decode(base64UrlDecode(parts[0])));
    if (head.alg !== "RS256" || typeof head.kid !== "string") return null;
    const key = await signingKey(head.kid);
    if (!key) return null;
    const valid = await crypto.subtle.verify(
      "RSASSA-PKCS1-v1_5", key, base64UrlDecode(parts[2]), new TextEncoder().encode(`${parts[0]}.${parts[1]}`),
    );
    if (!valid) return null;
    const payload = JSON.parse(new TextDecoder().decode(base64UrlDecode(parts[1])));
    return checkClaims(payload, projectId, Math.floor(Date.now() / 1000));
  } catch {
    return null;
  }
}

// ---------- Admin (nur mit Admin-Schlüssel aus der lokalen Dashboard-Datei) ----------

const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "X-Admin-Key, Content-Type",
  "Access-Control-Allow-Methods": "GET, PUT, OPTIONS",
};

async function admin(request, env, url) {
  if (request.method === "OPTIONS") return new Response(null, { status: 204, headers: CORS });
  if (!env.ADMIN_KEY || !safeEqual(request.headers.get("X-Admin-Key") ?? "", env.ADMIN_KEY)) {
    return json({ error: "unauthorized" }, 401, CORS);
  }
  const usage = usageObject(env);
  const day = berlinDay(Date.now());
  if (url.pathname === "/admin/stats" && request.method === "GET") {
    const days = Math.min(Math.max(Number(url.searchParams.get("days") ?? 30), 1), KEEP_DAYS);
    return json(await usage.stats(day, days, defaultLimit(env)), 200, CORS);
  }
  if (url.pathname === "/admin/config" && request.method === "PUT") {
    const body = await request.json().catch(() => null);
    const limit = Number(body?.dailyLimit);
    if (!Number.isInteger(limit) || limit < 0 || limit > 10000) return json({ error: "bad_request" }, 400, CORS);
    await usage.setConfig("dailyLimit", String(limit));
    return json({ ok: true, dailyLimit: limit }, 200, CORS);
  }
  return json({ error: "not_found" }, 404, CORS);
}

function json(data, status = 200, headers = {}) {
  return new Response(JSON.stringify(data), { status, headers: { "Content-Type": "application/json", ...headers } });
}

// ---------- Zähler (ein Durable Object mit SQLite) ----------

export class Usage extends DurableObject {
  constructor(ctx, env) {
    super(ctx, env);
    this.sql = ctx.storage.sql;
    this.sql.exec(`
      CREATE TABLE IF NOT EXISTS usage (uid TEXT NOT NULL, day TEXT NOT NULL, n INTEGER NOT NULL, PRIMARY KEY (uid, day));
      CREATE TABLE IF NOT EXISTS seen (req TEXT PRIMARY KEY, uid TEXT NOT NULL, ts INTEGER NOT NULL);
      CREATE TABLE IF NOT EXISTS weekly (uid TEXT NOT NULL, week TEXT NOT NULL, PRIMARY KEY (uid, week));
      CREATE TABLE IF NOT EXISTS errors (day TEXT NOT NULL, code INTEGER NOT NULL, n INTEGER NOT NULL, PRIMARY KEY (day, code));
      CREATE TABLE IF NOT EXISTS config (key TEXT PRIMARY KEY, value TEXT NOT NULL);
    `);
  }

  limit(fallback) {
    const row = this.sql.exec("SELECT value FROM config WHERE key = 'dailyLimit'").toArray()[0];
    return row ? Number(row.value) : fallback;
  }

  used(uid, day) {
    return this.sql.exec("SELECT n FROM usage WHERE uid = ? AND day = ?", uid, day).toArray()[0]?.n ?? 0;
  }

  usage(uid, day, fallback) {
    return { used: this.used(uid, day), limit: this.limit(fallback) };
  }

  check(uid, req, kind, day, week, fallback) {
    const limit = this.limit(fallback);
    const usedToday = this.used(uid, day);
    const alreadyCounted = this.sql.exec("SELECT 1 FROM seen WHERE req = ? AND uid = ?", req, uid).toArray().length > 0;
    const weeklyDone = this.sql.exec("SELECT 1 FROM weekly WHERE uid = ? AND week = ?", uid, week).toArray().length > 0;
    return { result: decide({ kind, usedToday, limit, alreadyCounted, weeklyDone }), used: usedToday, limit };
  }

  /** Erfolgreicher Aufruf: einmal pro Anfrage-Kennung zählen (die App fragt parallel/wiederholt). */
  record(uid, req, kind, day, week) {
    if (kind === "weekly-auto") {
      this.sql.exec("INSERT OR IGNORE INTO weekly (uid, week) VALUES (?, ?)", uid, week);
      return;
    }
    const fresh = this.sql.exec("INSERT OR IGNORE INTO seen (req, uid, ts) VALUES (?, ?, ?) RETURNING req", req, uid, Date.now()).toArray();
    if (fresh.length === 0) return;
    this.sql.exec(
      "INSERT INTO usage (uid, day, n) VALUES (?, ?, 1) ON CONFLICT (uid, day) DO UPDATE SET n = n + 1", uid, day,
    );
    this.cleanup(day);
  }

  error(day, code) {
    this.sql.exec("INSERT INTO errors (day, code, n) VALUES (?, ?, 1) ON CONFLICT (day, code) DO UPDATE SET n = n + 1", day, code);
  }

  cleanup(day) {
    const oldest = minusDays(day, KEEP_DAYS);
    this.sql.exec("DELETE FROM seen WHERE ts < ?", Date.now() - 2 * 86400000);
    this.sql.exec("DELETE FROM usage WHERE day < ?", oldest);
    this.sql.exec("DELETE FROM errors WHERE day < ?", oldest);
    this.sql.exec("DELETE FROM weekly WHERE week < ?", isoWeek(oldest));
  }

  setConfig(key, value) {
    this.sql.exec("INSERT INTO config (key, value) VALUES (?, ?) ON CONFLICT (key) DO UPDATE SET value = excluded.value", key, value);
  }

  /** Für das Dashboard: Aufrufe je Tag, Personen je Tag, Fehler, Spitzenreiter heute (gekürzte Kennungen). */
  stats(today, days, fallback) {
    const from = minusDays(today, days - 1);
    const perDay = this.sql.exec(
      "SELECT day, SUM(n) AS calls, COUNT(*) AS users, MAX(n) AS top FROM usage WHERE day >= ? GROUP BY day ORDER BY day", from,
    ).toArray();
    const errors = this.sql.exec("SELECT day, code, n FROM errors WHERE day >= ? ORDER BY day", from).toArray();
    const todayUsers = this.sql.exec("SELECT uid, n FROM usage WHERE day = ? ORDER BY n DESC LIMIT 20", today)
      .toArray().map((r) => ({ user: r.uid.slice(0, 6), calls: r.n }));
    const weekly = this.sql.exec("SELECT COUNT(*) AS n FROM weekly WHERE week = ?", isoWeek(today)).toArray()[0]?.n ?? 0;
    const allUsers = this.sql.exec("SELECT COUNT(DISTINCT uid) AS n FROM usage WHERE day >= ?", from).toArray()[0]?.n ?? 0;
    return { today, days, dailyLimit: this.limit(fallback), perDay, errors, todayUsers, weeklyAutoThisWeek: weekly, users: allUsers };
  }
}
