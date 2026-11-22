// Dunories KI-Worker: hält den Gemini-Schlüssel, prüft das Firebase-Token (auch Gast-Konten),
// entscheidet über Gast / Konto / Premium, zählt KI-Aufrufe pro Person und Tag (Uhr des Servers, deutsche Zeit)
// und leitet an Gemini weiter. Inhalte (Fotos, Texte, Antworten) werden weder gespeichert noch geloggt – nur Zähler.
import { DurableObject } from "cloudflare:workers";
import {
  KINDS, MAX_BODY, base64UrlDecode, berlinDay, checkClaims, decide, extend, fileFromPath, isoWeek, makeCode, minusDays,
  modelFromPath, normalizeCode, safeEqual, tierOf,
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
  const who = await verifyToken(request.headers.get("Authorization"), env.FIREBASE_PROJECT_ID);
  if (!who) return json({ error: "unauthorized" }, 401);
  const person = { uid: who.uid, anonymous: who.anonymous, trialKey: who.email ? await sha256(who.email) : null };
  const usage = usageObject(env);
  const now = Date.now();
  const day = berlinDay(now);

  if (url.pathname === "/v1/status" && request.method === "GET") {
    return json(await usage.status(person, day, now, defaults(env)));
  }

  if (url.pathname === "/v1/redeem" && request.method === "POST") {
    if (person.anonymous) return json({ error: "needs_account" }, 403);
    const body = await request.json().catch(() => null);
    const code = normalizeCode(body?.code);
    if (!code) return json({ error: "invalid_code" }, 400);
    const result = await usage.redeem(person, code, now);
    if (result !== "ok") return json({ error: result }, result === "too_many" ? 429 : 400);
    return json(await usage.status(person, day, now, defaults(env)));
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
    const check = await usage.check(person, req, kind, day, week, now, defaults(env));
    if (check.result !== "ok") {
      return json({ error: check.result, used: check.used, limit: check.limit }, 429, { "X-Dunories-Limit": check.result });
    }
    const upstream = await fetch(`${GEMINI}/v1beta/models/${model}:generateContent`, {
      method: "POST",
      headers: { "Content-Type": "application/json", "x-goog-api-key": env.GEMINI_API_KEY },
      body: request.body,
    });
    const body = await upstream.arrayBuffer();
    if (upstream.ok) await usage.record(person.uid, req, kind, day, week);
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

function num(value, fallback) {
  const n = Number(value);
  return Number.isFinite(n) && n >= 0 ? n : fallback;
}

/** Startwerte aus wrangler.toml; im Dashboard änderbar (dann gelten die gespeicherten Werte). */
function defaults(env) {
  return {
    dailyLimit: num(env.DEFAULT_DAILY_LIMIT, 40),
    guestFree: num(env.DEFAULT_GUEST_FREE, 5),
    trialDays: num(env.DEFAULT_TRIAL_DAYS, 7),
    testAllPremium: String(env.DEFAULT_TEST_ALL_PREMIUM ?? "true") === "true",
  };
}

async function sha256(text) {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(text));
  return [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, "0")).join("");
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

/** @return {uid, anonymous, email} oder null */
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
  "Access-Control-Allow-Methods": "GET, POST, PUT, OPTIONS",
};

async function admin(request, env, url) {
  if (request.method === "OPTIONS") return new Response(null, { status: 204, headers: CORS });
  if (!env.ADMIN_KEY || !safeEqual(request.headers.get("X-Admin-Key") ?? "", env.ADMIN_KEY)) {
    return json({ error: "unauthorized" }, 401, CORS);
  }
  const usage = usageObject(env);
  const now = Date.now();
  const day = berlinDay(now);
  const ok = (data) => json(data, 200, CORS);
  const bad = () => json({ error: "bad_request" }, 400, CORS);
  const body = request.method === "POST" || request.method === "PUT" ? await request.json().catch(() => null) : null;

  if (url.pathname === "/admin/stats" && request.method === "GET") {
    const days = Math.min(Math.max(num(url.searchParams.get("days"), 30), 1), KEEP_DAYS);
    return ok(await usage.stats(day, days, now, defaults(env)));
  }
  if (url.pathname === "/admin/config" && request.method === "PUT") {
    const changes = {};
    for (const key of ["dailyLimit", "guestFree", "trialDays"]) {
      if (body?.[key] === undefined) continue;
      const n = Number(body[key]);
      if (!Number.isInteger(n) || n < 0 || n > 10000) return bad();
      changes[key] = String(n);
    }
    if (body?.testAllPremium !== undefined) changes.testAllPremium = body.testAllPremium ? "true" : "false";
    await usage.setConfig(changes);
    return ok(await usage.config(defaults(env)));
  }
  if (url.pathname === "/admin/codes" && request.method === "GET") return ok(await usage.codes(now));
  if (url.pathname === "/admin/codes" && request.method === "POST") {
    const days = Number(body?.days);
    const maxUses = Number(body?.maxUses ?? 1);
    const validDays = Number(body?.validDays ?? 0);
    const note = String(body?.note ?? "").slice(0, 80);
    if (!Number.isInteger(days) || days < 0 || days > 3650) return bad();
    if (!Number.isInteger(maxUses) || maxUses < 1 || maxUses > 100000) return bad();
    if (!Number.isInteger(validDays) || validDays < 0 || validDays > 3650) return bad();
    const code = makeCode(crypto.getRandomValues(new Uint8Array(8)));
    await usage.createCode(code, days, maxUses, validDays ? now + validDays * 86400000 : null, note, now);
    return ok({ code });
  }
  if (url.pathname === "/admin/codes/disable" && request.method === "POST") {
    const code = normalizeCode(body?.code);
    if (!code) return bad();
    await usage.disableCode(code);
    return ok({ ok: true });
  }
  if (url.pathname === "/admin/users" && request.method === "GET") {
    const prefix = (url.searchParams.get("prefix") ?? "").trim();
    if (prefix.length < 4 || prefix.length > 128) return bad();
    return ok(await usage.findUsers(prefix, day, now, defaults(env)));
  }
  if (url.pathname === "/admin/user" && request.method === "PUT") {
    const uid = String(body?.uid ?? "");
    if (!uid || uid.length > 128) return bad();
    if (body?.remove) await usage.removeEntitlement(uid);
    else {
      const days = Number(body?.days);
      if (!Number.isInteger(days) || days < 0 || days > 3650) return bad();
      await usage.grant(uid, days, "admin", String(body?.note ?? "").slice(0, 80), now);
    }
    return ok(await usage.findUsers(uid, day, now, defaults(env)));
  }
  return json({ error: "not_found" }, 404, CORS);
}

function json(data, status = 200, headers = {}) {
  return new Response(JSON.stringify(data), { status, headers: { "Content-Type": "application/json", ...headers } });
}

// ---------- Zähler und Freischaltungen (ein Durable Object mit SQLite) ----------

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
      CREATE TABLE IF NOT EXISTS totals (uid TEXT PRIMARY KEY, n INTEGER NOT NULL, last_day TEXT NOT NULL);
      CREATE TABLE IF NOT EXISTS entitlements (uid TEXT PRIMARY KEY, lifetime INTEGER NOT NULL, until INTEGER, source TEXT NOT NULL, note TEXT, updated INTEGER NOT NULL);
      CREATE TABLE IF NOT EXISTS trials (key TEXT PRIMARY KEY, started INTEGER NOT NULL);
      CREATE TABLE IF NOT EXISTS codes (code TEXT PRIMARY KEY, days INTEGER NOT NULL, max_uses INTEGER NOT NULL, used INTEGER NOT NULL, expires INTEGER, note TEXT, created INTEGER NOT NULL, disabled INTEGER NOT NULL DEFAULT 0);
      CREATE TABLE IF NOT EXISTS redemptions (code TEXT NOT NULL, uid TEXT NOT NULL, ts INTEGER NOT NULL, PRIMARY KEY (code, uid));
      CREATE TABLE IF NOT EXISTS attempts (uid TEXT NOT NULL, ts INTEGER NOT NULL);
    `);
  }

  config(d) {
    const rows = Object.fromEntries(this.sql.exec("SELECT key, value FROM config").toArray().map((r) => [r.key, r.value]));
    return {
      dailyLimit: rows.dailyLimit !== undefined ? Number(rows.dailyLimit) : d.dailyLimit,
      guestFree: rows.guestFree !== undefined ? Number(rows.guestFree) : d.guestFree,
      trialDays: rows.trialDays !== undefined ? Number(rows.trialDays) : d.trialDays,
      testAllPremium: rows.testAllPremium !== undefined ? rows.testAllPremium === "true" : d.testAllPremium,
    };
  }

  setConfig(changes) {
    for (const [key, value] of Object.entries(changes)) {
      this.sql.exec("INSERT INTO config (key, value) VALUES (?, ?) ON CONFLICT (key) DO UPDATE SET value = excluded.value", key, value);
    }
  }

  used(uid, day) {
    return this.sql.exec("SELECT n FROM usage WHERE uid = ? AND day = ?", uid, day).toArray()[0]?.n ?? 0;
  }

  total(uid) {
    return this.sql.exec("SELECT n FROM totals WHERE uid = ?", uid).toArray()[0]?.n ?? 0;
  }

  entitlement(uid) {
    const row = this.sql.exec("SELECT lifetime, until, source, note FROM entitlements WHERE uid = ?", uid).toArray()[0];
    return row ? { lifetime: !!row.lifetime, until: row.until ?? 0, source: row.source, note: row.note } : null;
  }

  /** Probezeit startet automatisch beim ersten Kontakt mit Konto – einmal pro Google-Konto (gehashte E-Mail). */
  trialStart(person, now, startIfNew) {
    if (person.anonymous || !person.trialKey) return null;
    const row = this.sql.exec("SELECT started FROM trials WHERE key = ?", person.trialKey).toArray()[0];
    if (row) return row.started;
    if (!startIfNew) return null;
    this.sql.exec("INSERT INTO trials (key, started) VALUES (?, ?)", person.trialKey, now);
    return now;
  }

  tier(person, now, cfg) {
    return tierOf({
      now, anonymous: person.anonymous, testAllPremium: cfg.testAllPremium, ent: this.entitlement(person.uid),
      trialStart: this.trialStart(person, now, cfg.trialDays > 0), trialDays: cfg.trialDays,
    });
  }

  status(person, day, now, d) {
    const cfg = this.config(d);
    const t = this.tier(person, now, cfg);
    return {
      ...t,
      anonymous: person.anonymous,
      usedToday: this.used(person.uid, day),
      dailyLimit: cfg.dailyLimit,
      freeLeft: t.tier === "guest" ? Math.max(cfg.guestFree - this.total(person.uid), 0) : null,
      trialDays: cfg.trialDays,
      id: person.uid.slice(0, 8),
    };
  }

  check(person, req, kind, day, week, now, d) {
    const cfg = this.config(d);
    const t = this.tier(person, now, cfg);
    const usedToday = this.used(person.uid, day);
    const alreadyCounted = this.sql.exec("SELECT 1 FROM seen WHERE req = ? AND uid = ?", req, person.uid).toArray().length > 0;
    const weeklyDone = this.sql.exec("SELECT 1 FROM weekly WHERE uid = ? AND week = ?", person.uid, week).toArray().length > 0;
    const freeLeft = cfg.guestFree - this.total(person.uid);
    return {
      result: decide({ kind, tier: t.tier, usedToday, limit: cfg.dailyLimit, alreadyCounted, weeklyDone, freeLeft }),
      used: usedToday,
      limit: cfg.dailyLimit,
    };
  }

  /** Erfolgreicher Aufruf: einmal pro Anfrage-Kennung zählen (die App fragt parallel/wiederholt). */
  record(uid, req, kind, day, week) {
    if (kind === "weekly-auto") {
      this.sql.exec("INSERT OR IGNORE INTO weekly (uid, week) VALUES (?, ?)", uid, week);
      return;
    }
    const fresh = this.sql.exec("INSERT OR IGNORE INTO seen (req, uid, ts) VALUES (?, ?, ?) RETURNING req", req, uid, Date.now()).toArray();
    if (fresh.length === 0) return;
    this.sql.exec("INSERT INTO usage (uid, day, n) VALUES (?, ?, 1) ON CONFLICT (uid, day) DO UPDATE SET n = n + 1", uid, day);
    this.sql.exec(
      "INSERT INTO totals (uid, n, last_day) VALUES (?, 1, ?) ON CONFLICT (uid) DO UPDATE SET n = n + 1, last_day = excluded.last_day", uid, day,
    );
    this.cleanup(day);
  }

  error(day, code) {
    this.sql.exec("INSERT INTO errors (day, code, n) VALUES (?, ?, 1) ON CONFLICT (day, code) DO UPDATE SET n = n + 1", day, code);
  }

  cleanup(day) {
    const oldest = minusDays(day, KEEP_DAYS);
    this.sql.exec("DELETE FROM seen WHERE ts < ?", Date.now() - 2 * 86400000);
    this.sql.exec("DELETE FROM attempts WHERE ts < ?", Date.now() - 3600000);
    this.sql.exec("DELETE FROM usage WHERE day < ?", oldest);
    this.sql.exec("DELETE FROM errors WHERE day < ?", oldest);
    this.sql.exec("DELETE FROM weekly WHERE week < ?", isoWeek(oldest));
  }

  grant(uid, days, source, note, now) {
    const next = extend(this.entitlement(uid), now, days);
    this.sql.exec(
      `INSERT INTO entitlements (uid, lifetime, until, source, note, updated) VALUES (?, ?, ?, ?, ?, ?)
       ON CONFLICT (uid) DO UPDATE SET lifetime = excluded.lifetime, until = excluded.until, source = excluded.source,
       note = excluded.note, updated = excluded.updated`,
      uid, next.lifetime ? 1 : 0, next.until, source, note || null, now,
    );
  }

  removeEntitlement(uid) {
    this.sql.exec("DELETE FROM entitlements WHERE uid = ?", uid);
  }

  /** Code einlösen: höchstens 5 Fehlversuche pro Stunde (gegen Raten). */
  redeem(person, code, now) {
    const attempts = this.sql.exec("SELECT COUNT(*) AS n FROM attempts WHERE uid = ? AND ts > ?", person.uid, now - 3600000).toArray()[0].n;
    if (attempts >= 5) return "too_many";
    const row = this.sql.exec("SELECT days, max_uses, used, expires, note, disabled FROM codes WHERE code = ?", code).toArray()[0];
    const fail = (reason) => {
      this.sql.exec("INSERT INTO attempts (uid, ts) VALUES (?, ?)", person.uid, now);
      return reason;
    };
    if (!row || row.disabled) return fail("invalid_code");
    if (row.expires && row.expires < now) return fail("expired_code");
    if (this.sql.exec("SELECT 1 FROM redemptions WHERE code = ? AND uid = ?", code, person.uid).toArray().length) return "already_redeemed";
    if (row.used >= row.max_uses) return fail("used_up_code");
    this.sql.exec("UPDATE codes SET used = used + 1 WHERE code = ?", code);
    this.sql.exec("INSERT INTO redemptions (code, uid, ts) VALUES (?, ?, ?)", code, person.uid, now);
    this.grant(person.uid, row.days, "code", row.note ? `${code} · ${row.note}` : code, now);
    return "ok";
  }

  createCode(code, days, maxUses, expires, note, now) {
    this.sql.exec("INSERT INTO codes (code, days, max_uses, used, expires, note, created) VALUES (?, ?, ?, 0, ?, ?, ?)",
      code, days, maxUses, expires, note || null, now);
  }

  disableCode(code) {
    this.sql.exec("UPDATE codes SET disabled = 1 WHERE code = ?", code);
  }

  codes(now) {
    return this.sql.exec("SELECT code, days, max_uses AS maxUses, used, expires, note, created, disabled FROM codes ORDER BY created DESC LIMIT 200")
      .toArray().map((c) => ({ ...c, disabled: !!c.disabled, expired: !!(c.expires && c.expires < now) }));
  }

  /** Personen über den Anfang ihrer Kennung finden (die App zeigt die ersten 8 Zeichen). */
  findUsers(prefix, day, now, d) {
    const cfg = this.config(d);
    const uids = this.sql.exec(
      `SELECT uid FROM totals WHERE uid LIKE ? || '%' UNION SELECT uid FROM entitlements WHERE uid LIKE ? || '%' LIMIT 20`,
      prefix, prefix,
    ).toArray().map((r) => r.uid);
    return uids.map((uid) => {
      const ent = this.entitlement(uid);
      return {
        uid,
        entitlement: ent,
        premiumByEntitlement: !!(ent && (ent.lifetime || ent.until > now)),
        usedToday: this.used(uid, day),
        total: this.total(uid),
        lastDay: this.sql.exec("SELECT last_day FROM totals WHERE uid = ?", uid).toArray()[0]?.last_day ?? null,
        testAllPremium: cfg.testAllPremium,
      };
    });
  }

  /** Für das Dashboard: Aufrufe je Tag, Personen je Tag, Fehler, Spitzenreiter heute (gekürzte Kennungen). */
  stats(today, days, now, d) {
    const from = minusDays(today, days - 1);
    const perDay = this.sql.exec(
      "SELECT day, SUM(n) AS calls, COUNT(*) AS users, MAX(n) AS top FROM usage WHERE day >= ? GROUP BY day ORDER BY day", from,
    ).toArray();
    const errors = this.sql.exec("SELECT day, code, n FROM errors WHERE day >= ? ORDER BY day", from).toArray();
    const todayUsers = this.sql.exec("SELECT uid, n FROM usage WHERE day = ? ORDER BY n DESC LIMIT 20", today)
      .toArray().map((r) => ({ user: r.uid.slice(0, 8), calls: r.n }));
    const weekly = this.sql.exec("SELECT COUNT(*) AS n FROM weekly WHERE week = ?", isoWeek(today)).toArray()[0]?.n ?? 0;
    const allUsers = this.sql.exec("SELECT COUNT(DISTINCT uid) AS n FROM usage WHERE day >= ?", from).toArray()[0]?.n ?? 0;
    const premium = this.sql.exec("SELECT COUNT(*) AS n FROM entitlements WHERE lifetime = 1 OR until > ?", now).toArray()[0]?.n ?? 0;
    const trials = this.sql.exec("SELECT COUNT(*) AS n FROM trials").toArray()[0]?.n ?? 0;
    return {
      today, days, config: this.config(d), perDay, errors, todayUsers, weeklyAutoThisWeek: weekly, users: allUsers,
      premiumEntitlements: premium, trialsStarted: trials,
    };
  }
}
