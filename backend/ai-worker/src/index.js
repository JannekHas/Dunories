// Dunories KI-Worker: hält den Gemini-Schlüssel, prüft das Firebase-Token (auch Gast-Konten),
// entscheidet über Gast / Konto / Premium, zählt KI-Aufrufe pro Person und Tag (Uhr des Servers, deutsche Zeit)
// und leitet an Gemini weiter. Inhalte (Fotos, Texte, Antworten) werden weder gespeichert noch geloggt – nur Zähler.
import { DurableObject } from "cloudflare:workers";
import {
  FEEDBACK_POINTS, KINDS, MAX_FRIENDS, cleanSocialProfile, friendView, makeFriendCode, normalizeFriendCode, MAX_BODY, MAX_FEEDBACK_PER_MINUTE, base64UrlDecode, cleanFeedback, berlinDay, checkClaims, decide, extend, fileFromPath, isoWeek, makeCode, minusDays,
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

  // Feedback (Tester-Belohnungsprogramm): landet im Postfach des Admin-Dashboards.
  if (url.pathname === "/v1/feedback" && request.method === "POST") {
    const length = Number(request.headers.get("Content-Length") ?? "NaN");
    if (!Number.isFinite(length) || length > 1_200_000) return json({ error: "too_large" }, 413);
    const feedback = cleanFeedback(await request.json().catch(() => null));
    if (!feedback) return json({ error: "bad_request" }, 400);
    const result = await usage.addFeedback(person.uid, feedback, day, now);
    return result === "ok" ? json({ ok: true }) : json({ error: result }, 429);
  }

  // Tester-Profil: aktueller Name und ob er in die Danke-Liste darf (am Ende zählt, was gerade eingestellt ist).
  if (url.pathname === "/v1/tester" && request.method === "POST") {
    const body = await request.json().catch(() => null);
    if (!body || typeof body !== "object") return json({ error: "bad_request" }, 400);
    await usage.setTester(person.uid, String(body.name ?? "").trim().slice(0, 60), body.credits === true, now);
    return json({ ok: true });
  }

  // Danke-Nachricht gezeigt → neu bewertete Feedbacks als gesehen markieren.
  if (url.pathname === "/v1/rewards/seen" && request.method === "POST") {
    await usage.rewardsSeen(person.uid);
    return json({ ok: true });
  }

  // ---------- Freunde (nur mit Konto) ----------
  if (url.pathname.startsWith("/v1/social")) {
    // ALLOW_ANON_SOCIAL nur für lokale Tests (`wrangler dev --var ALLOW_ANON_SOCIAL:true`), nie im echten Worker gesetzt.
    if (person.anonymous && env.ALLOW_ANON_SOCIAL !== "true") return json({ error: "needs_account" }, 403);
    const body = request.method === "POST" ? await request.json().catch(() => null) : null;
    if (url.pathname === "/v1/social" && request.method === "GET") return json(await usage.socialState(person.uid));
    if (url.pathname === "/v1/social/profile" && request.method === "POST") {
      const profile = cleanSocialProfile(body);
      if (!profile) return json({ error: "bad_request" }, 400);
      return json(await usage.saveSocialProfile(person.uid, profile, now));
    }
    if (url.pathname === "/v1/social/request" && request.method === "POST") {
      const code = normalizeFriendCode(body?.code);
      if (!code) return json({ error: "invalid_code" }, 400);
      const result = await usage.friendRequest(person.uid, code, now);
      return result === "ok" || result === "accepted" ? json({ result, ...(await usage.socialState(person.uid)) }) : json({ error: result }, 400);
    }
    if (url.pathname === "/v1/social/respond" && request.method === "POST") {
      const code = normalizeFriendCode(body?.code);
      if (!code) return json({ error: "invalid_code" }, 400);
      await usage.friendRespond(person.uid, code, body?.accept === true, now);
      return json(await usage.socialState(person.uid));
    }
    if (url.pathname === "/v1/social/remove" && request.method === "POST") {
      const code = normalizeFriendCode(body?.code);
      if (!code) return json({ error: "invalid_code" }, 400);
      await usage.friendRemove(person.uid, code);
      return json(await usage.socialState(person.uid));
    }
    // Konto löschen: alles zu Freunden und Profil entfernen.
    if (url.pathname === "/v1/social/delete" && request.method === "POST") {
      await usage.socialDelete(person.uid);
      return json({ ok: true });
    }
    return json({ error: "not_found" }, 404);
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
  if (value === null || value === undefined || value === "") return fallback;
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
    await usage.disableCode(code, body?.disabled !== false);
    return ok({ ok: true });
  }
  // Bearbeiten: nur mitgeschickte Felder ändern. validDays = ab jetzt einlösbar für … Tage (0 = unbegrenzt).
  if (url.pathname === "/admin/codes/update" && request.method === "POST") {
    const code = normalizeCode(body?.code);
    if (!code) return bad();
    const changes = {};
    if (body.days !== undefined) {
      if (!Number.isInteger(body.days) || body.days < 0 || body.days > 3650) return bad();
      changes.days = body.days;
    }
    if (body.maxUses !== undefined) {
      if (!Number.isInteger(body.maxUses) || body.maxUses < 1 || body.maxUses > 100000) return bad();
      changes.max_uses = body.maxUses;
    }
    if (body.validDays !== undefined) {
      if (!Number.isInteger(body.validDays) || body.validDays < 0 || body.validDays > 3650) return bad();
      changes.expires = body.validDays ? now + body.validDays * 86400000 : null;
    }
    if (body.note !== undefined) changes.note = String(body.note).slice(0, 80) || null;
    await usage.updateCode(code, changes);
    return ok({ ok: true });
  }
  // Zurücksetzen: Zähler auf 0, alle dürfen ihn wieder einlösen (bereits vergebene Premium-Zeit bleibt).
  if (url.pathname === "/admin/codes/reset" && request.method === "POST") {
    const code = normalizeCode(body?.code);
    if (!code) return bad();
    await usage.resetCode(code);
    return ok({ ok: true });
  }
  if (url.pathname === "/admin/codes/delete" && request.method === "POST") {
    const code = normalizeCode(body?.code);
    if (!code) return bad();
    await usage.deleteCode(code);
    return ok({ ok: true });
  }
  if (url.pathname === "/admin/feedback" && request.method === "GET") {
    const filter = url.searchParams.get("filter") === "all" ? "all" : "unrated";
    return ok(await usage.listFeedback(filter, Math.min(num(url.searchParams.get("limit"), 100), 500)));
  }
  if (url.pathname === "/admin/feedback/shot" && request.method === "GET") {
    const id = Number(url.searchParams.get("id"));
    if (!Number.isInteger(id)) return bad();
    return ok({ screenshot: await usage.feedbackShot(id) });
  }
  if (url.pathname === "/admin/feedback/rate" && request.method === "POST") {
    const id = Number(body?.id);
    const points = Number(body?.points);
    if (!Number.isInteger(id) || !FEEDBACK_POINTS.has(points)) return bad();
    await usage.rateFeedback(id, points, now);
    return ok({ ok: true });
  }
  if (url.pathname === "/admin/feedback/delete" && request.method === "POST") {
    const id = Number(body?.id);
    if (!Number.isInteger(id)) return bad();
    await usage.deleteFeedback(id);
    return ok({ ok: true });
  }
  if (url.pathname === "/admin/testers" && request.method === "GET") return ok(await usage.testers());
  // Sperren über ein Feedback (id) oder direkt über die Kennung aus der Rangliste (key).
  if (url.pathname === "/admin/block" && request.method === "POST") {
    const uid = Number.isInteger(body?.id) ? await usage.feedbackAuthor(body.id) : typeof body?.key === "string" ? body.key : null;
    if (!uid || uid.length > 128) return bad();
    await usage.setBlocked(uid, body.blocked !== false, body.purge === true, now);
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
      CREATE TABLE IF NOT EXISTS feedback (
        id INTEGER PRIMARY KEY AUTOINCREMENT, uid TEXT NOT NULL, ts INTEGER NOT NULL, day TEXT NOT NULL, type TEXT NOT NULL,
        text TEXT NOT NULL, name TEXT, credits INTEGER NOT NULL, screenshot TEXT, app TEXT, version_code INTEGER, device TEXT,
        android INTEGER, points INTEGER, rated INTEGER, notified INTEGER NOT NULL DEFAULT 0);
      CREATE TABLE IF NOT EXISTS testers (uid TEXT PRIMARY KEY, name TEXT, credits INTEGER NOT NULL, updated INTEGER NOT NULL);
      CREATE TABLE IF NOT EXISTS blocked (uid TEXT PRIMARY KEY, ts INTEGER NOT NULL);
      CREATE TABLE IF NOT EXISTS profiles (
        uid TEXT PRIMARY KEY, code TEXT NOT NULL UNIQUE, name TEXT, badge TEXT, streak INTEGER NOT NULL DEFAULT 0,
        achievements TEXT NOT NULL DEFAULT '[]', share_badges INTEGER NOT NULL DEFAULT 0,
        share_achievements INTEGER NOT NULL DEFAULT 0, share_streak INTEGER NOT NULL DEFAULT 0, updated INTEGER NOT NULL);
      CREATE TABLE IF NOT EXISTS friend_requests (from_uid TEXT NOT NULL, to_uid TEXT NOT NULL, ts INTEGER NOT NULL, PRIMARY KEY (from_uid, to_uid));
      CREATE TABLE IF NOT EXISTS friends (a TEXT NOT NULL, b TEXT NOT NULL, since INTEGER NOT NULL, PRIMARY KEY (a, b));
    `);
    // Spalte „best“ (längste Serie) kam nach dem ersten Deploy dazu.
    const profileCols = this.sql.exec("PRAGMA table_info(profiles)").toArray().map((c) => c.name);
    if (!profileCols.includes("best")) this.sql.exec("ALTER TABLE profiles ADD COLUMN best INTEGER NOT NULL DEFAULT 0");
    // Avatar (feste Motive aus der App, kein Foto-Upload) – kam mit den Stufen-Erfolgen dazu.
    if (!profileCols.includes("avatar")) this.sql.exec("ALTER TABLE profiles ADD COLUMN avatar TEXT");
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
    const pts = this.points(person.uid);
    return {
      ...t,
      anonymous: person.anonymous,
      testerPoints: pts.total,
      // Für den Erfolg „Mitgestalter“: nur von dir bewertetes Feedback mit Punkten zählt (kein Spam).
      ratedFeedback: pts.rated,
      newRewards: pts.newCount,
      newPoints: pts.newPoints,
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

  /** Punkte einer Person; „neu“ = bewertet, aber Danke-Nachricht noch nicht gezeigt. */
  points(uid) {
    const row = this.sql.exec(
      `SELECT COALESCE(SUM(points), 0) AS total,
              COALESCE(SUM(CASE WHEN rated IS NOT NULL AND notified = 0 AND points > 0 THEN 1 ELSE 0 END), 0) AS newCount,
              COALESCE(SUM(CASE WHEN rated IS NOT NULL AND notified = 0 AND points > 0 THEN points ELSE 0 END), 0) AS newPoints,
              COALESCE(SUM(CASE WHEN rated IS NOT NULL AND points > 0 THEN 1 ELSE 0 END), 0) AS rated
       FROM feedback WHERE uid = ?`, uid,
    ).toArray()[0];
    return { total: row.total, newCount: row.newCount, newPoints: row.newPoints, rated: row.rated };
  }

  addFeedback(uid, f, day, now) {
    // Gesperrt: still annehmen und verwerfen (die Person merkt nichts, das Postfach bleibt sauber).
    if (this.sql.exec("SELECT 1 FROM blocked WHERE uid = ?", uid).toArray().length) return "ok";
    const lastMinute = this.sql.exec("SELECT COUNT(*) AS n FROM feedback WHERE uid = ? AND ts > ?", uid, now - 60000).toArray()[0].n;
    if (lastMinute >= MAX_FEEDBACK_PER_MINUTE) return "too_many";
    if (f.name) this.setTester(uid, f.name, f.credits, now);
    this.sql.exec(
      `INSERT INTO feedback (uid, ts, day, type, text, name, credits, screenshot, app, version_code, device, android)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
      uid, now, day, f.type, f.text, f.name || null, f.credits ? 1 : 0, f.screenshot, f.appVersion, f.versionCode, f.device, f.android,
    );
    return "ok";
  }

  setTester(uid, name, credits, now) {
    this.sql.exec(
      `INSERT INTO testers (uid, name, credits, updated) VALUES (?, ?, ?, ?)
       ON CONFLICT (uid) DO UPDATE SET name = excluded.name, credits = excluded.credits, updated = excluded.updated`,
      uid, name || null, credits && name ? 1 : 0, now,
    );
  }

  // ---------------- Freunde ----------------

  profileRow(uid) {
    const p = this.sql.exec("SELECT * FROM profiles WHERE uid = ?", uid).toArray()[0];
    if (!p) return null;
    return {
      uid: p.uid, code: p.code, name: p.name, badge: p.badge, avatar: p.avatar, streak: p.streak, best: p.best, achievements: JSON.parse(p.achievements || "[]"),
      shareBadges: !!p.share_badges, shareAchievements: !!p.share_achievements, shareStreak: !!p.share_streak,
    };
  }

  /** Profil anlegen/aktualisieren; beim ersten Mal bekommt die Person ihren festen Freundescode. */
  saveSocialProfile(uid, p, now) {
    let code = this.sql.exec("SELECT code FROM profiles WHERE uid = ?", uid).toArray()[0]?.code;
    if (!code) {
      do { code = makeFriendCode(crypto.getRandomValues(new Uint8Array(8))); }
      while (this.sql.exec("SELECT 1 FROM profiles WHERE code = ?", code).toArray().length);
    }
    this.sql.exec(
      `INSERT INTO profiles (uid, code, name, badge, avatar, streak, best, achievements, share_badges, share_achievements, share_streak, updated)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
       ON CONFLICT (uid) DO UPDATE SET name = excluded.name, badge = excluded.badge, avatar = excluded.avatar, streak = excluded.streak, best = excluded.best,
         achievements = excluded.achievements, share_badges = excluded.share_badges,
         share_achievements = excluded.share_achievements, share_streak = excluded.share_streak, updated = excluded.updated`,
      uid, code, p.name || null, p.badge, p.avatar, p.streak, p.best, JSON.stringify(p.achievements),
      p.shareBadges ? 1 : 0, p.shareAchievements ? 1 : 0, p.shareStreak ? 1 : 0, now,
    );
    return this.socialState(uid);
  }

  uidByCode(code) {
    return this.sql.exec("SELECT uid FROM profiles WHERE code = ?", code).toArray()[0]?.uid ?? null;
  }

  areFriends(a, b) {
    return this.sql.exec("SELECT 1 FROM friends WHERE a = ? AND b = ?", a, b).toArray().length > 0;
  }

  friendCount(uid) {
    return this.sql.exec("SELECT COUNT(*) AS n FROM friends WHERE a = ?", uid).toArray()[0].n;
  }

  makeFriends(a, b, now) {
    this.sql.exec("INSERT OR IGNORE INTO friends (a, b, since) VALUES (?, ?, ?)", a, b, now);
    this.sql.exec("INSERT OR IGNORE INTO friends (a, b, since) VALUES (?, ?, ?)", b, a, now);
    this.sql.exec("DELETE FROM friend_requests WHERE (from_uid = ? AND to_uid = ?) OR (from_uid = ? AND to_uid = ?)", a, b, b, a);
  }

  /** Anfrage senden. Hat die andere Person schon angefragt, seid ihr direkt befreundet. */
  friendRequest(uid, code, now) {
    if (!this.profileRow(uid)) return "no_profile";
    const target = this.uidByCode(code);
    if (!target) return "unknown_code";
    if (target === uid) return "self";
    if (this.areFriends(uid, target)) return "already_friends";
    if (this.friendCount(uid) >= MAX_FRIENDS || this.friendCount(target) >= MAX_FRIENDS) return "too_many_friends";
    const recent = this.sql.exec("SELECT COUNT(*) AS n FROM friend_requests WHERE from_uid = ? AND ts > ?", uid, now - 3600000).toArray()[0].n;
    if (recent >= 20) return "too_many_requests";
    if (this.sql.exec("SELECT 1 FROM friend_requests WHERE from_uid = ? AND to_uid = ?", target, uid).toArray().length) {
      this.makeFriends(uid, target, now);
      return "accepted";
    }
    this.sql.exec("INSERT OR IGNORE INTO friend_requests (from_uid, to_uid, ts) VALUES (?, ?, ?)", uid, target, now);
    return "ok";
  }

  friendRespond(uid, code, accept, now) {
    const from = this.uidByCode(code);
    if (!from) return;
    const exists = this.sql.exec("SELECT 1 FROM friend_requests WHERE from_uid = ? AND to_uid = ?", from, uid).toArray().length;
    if (!exists) return;
    if (accept && this.friendCount(uid) < MAX_FRIENDS && this.friendCount(from) < MAX_FRIENDS) this.makeFriends(uid, from, now);
    else this.sql.exec("DELETE FROM friend_requests WHERE from_uid = ? AND to_uid = ?", from, uid);
  }

  /** Freund entfernen oder eigene Anfrage zurückziehen – sofort, für beide. */
  friendRemove(uid, code) {
    const other = this.uidByCode(code);
    if (!other) return;
    this.sql.exec("DELETE FROM friends WHERE (a = ? AND b = ?) OR (a = ? AND b = ?)", uid, other, other, uid);
    this.sql.exec("DELETE FROM friend_requests WHERE (from_uid = ? AND to_uid = ?) OR (from_uid = ? AND to_uid = ?)", uid, other, other, uid);
  }

  socialDelete(uid) {
    this.sql.exec("DELETE FROM friends WHERE a = ? OR b = ?", uid, uid);
    this.sql.exec("DELETE FROM friend_requests WHERE from_uid = ? OR to_uid = ?", uid, uid);
    this.sql.exec("DELETE FROM profiles WHERE uid = ?", uid);
  }

  /** Alles für die App: eigener Code, Freunde (nur Freigegebenes), eingehende und ausgehende Anfragen. */
  socialState(uid) {
    const me = this.profileRow(uid);
    const friends = this.sql.exec("SELECT b FROM friends WHERE a = ?", uid).toArray()
      .map((r) => this.profileRow(r.b)).filter(Boolean).map(friendView);
    const incoming = this.sql.exec("SELECT from_uid FROM friend_requests WHERE to_uid = ? ORDER BY ts DESC", uid).toArray()
      .map((r) => this.profileRow(r.from_uid)).filter(Boolean).map((p) => ({ code: p.code, name: p.name || "" }));
    const outgoing = this.sql.exec("SELECT to_uid FROM friend_requests WHERE from_uid = ? ORDER BY ts DESC", uid).toArray()
      .map((r) => this.profileRow(r.to_uid)).filter(Boolean).map((p) => ({ code: p.code, name: p.name || "" }));
    return { code: me?.code ?? null, friends, incoming, outgoing };
  }

  /** Sperren/entsperren; beim Sperren auf Wunsch alle unbewerteten Feedbacks dieser Person löschen. */
  setBlocked(uid, blocked, purge, now) {
    if (blocked) {
      this.sql.exec("INSERT OR IGNORE INTO blocked (uid, ts) VALUES (?, ?)", uid, now);
      if (purge) this.sql.exec("DELETE FROM feedback WHERE uid = ? AND rated IS NULL", uid);
    } else {
      this.sql.exec("DELETE FROM blocked WHERE uid = ?", uid);
    }
  }

  feedbackAuthor(id) {
    return this.sql.exec("SELECT uid FROM feedback WHERE id = ?", id).toArray()[0]?.uid ?? null;
  }

  rewardsSeen(uid) {
    this.sql.exec("UPDATE feedback SET notified = 1 WHERE uid = ? AND rated IS NOT NULL AND notified = 0", uid);
  }

  listFeedback(filter, limit) {
    const where = filter === "all" ? "" : "WHERE rated IS NULL";
    return this.sql.exec(
      `SELECT id, uid, ts, type, text, name, credits, screenshot IS NOT NULL AS hasShot, app, version_code AS versionCode, device,
              android, points, rated, EXISTS (SELECT 1 FROM blocked b WHERE b.uid = feedback.uid) AS blocked
       FROM feedback ${where} ORDER BY ts DESC LIMIT ${Math.max(1, Math.floor(limit))}`,
    ).toArray().map((f) => ({ ...f, uid: f.uid.slice(0, 8), credits: !!f.credits, hasShot: !!f.hasShot, blocked: !!f.blocked }));
  }

  feedbackShot(id) {
    return this.sql.exec("SELECT screenshot FROM feedback WHERE id = ?", id).toArray()[0]?.screenshot ?? null;
  }

  /** Bewerten (auch umbewerten: dann wird die Danke-Nachricht erneut fällig, falls es jetzt Punkte gibt). */
  rateFeedback(id, points, now) {
    this.sql.exec("UPDATE feedback SET points = ?, rated = ?, notified = 0 WHERE id = ?", points, now, id);
  }

  deleteFeedback(id) {
    this.sql.exec("DELETE FROM feedback WHERE id = ?", id);
  }

  /** Rangliste fürs Release: Punkte, Anzahl Feedbacks, letzter Name und ob er in die Danke-Liste darf. */
  testers() {
    return this.sql.exec(
      `SELECT u.uid AS uid, COALESCE(SUM(f.points), 0) AS points, COUNT(f.id) AS feedbacks,
              COALESCE(SUM(CASE WHEN f.id IS NOT NULL AND f.rated IS NULL THEN 1 ELSE 0 END), 0) AS unrated,
              t.name AS name, COALESCE(t.credits, 0) AS credits, MAX(f.ts) AS last,
              EXISTS (SELECT 1 FROM blocked b WHERE b.uid = u.uid) AS blocked
       FROM (SELECT uid FROM feedback UNION SELECT uid FROM blocked) u
       LEFT JOIN feedback f ON f.uid = u.uid LEFT JOIN testers t ON t.uid = u.uid
       GROUP BY u.uid ORDER BY blocked ASC, points DESC, feedbacks DESC LIMIT 300`,
    ).toArray().map((t) => ({ ...t, key: t.uid, uid: t.uid.slice(0, 8), credits: !!t.credits, blocked: !!t.blocked }));
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
    // Feedback spätestens nach zwei Jahren löschen (so in der Datenschutzerklärung).
    this.sql.exec("DELETE FROM feedback WHERE ts < ?", Date.now() - 730 * 86400000);
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

  /** Code einlösen: höchstens 10 Fehlversuche pro Stunde (gegen Raten). */
  redeem(person, code, now) {
    const attempts = this.sql.exec("SELECT COUNT(*) AS n FROM attempts WHERE uid = ? AND ts > ?", person.uid, now - 3600000).toArray()[0].n;
    if (attempts >= 10) return "too_many";
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

  disableCode(code, disabled = true) {
    this.sql.exec("UPDATE codes SET disabled = ? WHERE code = ?", disabled ? 1 : 0, code);
  }

  updateCode(code, changes) {
    for (const [column, value] of Object.entries(changes)) {
      if (!["days", "max_uses", "expires", "note"].includes(column)) continue;
      this.sql.exec(`UPDATE codes SET ${column} = ? WHERE code = ?`, value, code);
    }
  }

  resetCode(code) {
    this.sql.exec("UPDATE codes SET used = 0 WHERE code = ?", code);
    this.sql.exec("DELETE FROM redemptions WHERE code = ?", code);
  }

  /** Löschen: Code verschwindet, bereits vergebene Premium-Zeit bleibt bei den Personen. */
  deleteCode(code) {
    this.sql.exec("DELETE FROM codes WHERE code = ?", code);
    this.sql.exec("DELETE FROM redemptions WHERE code = ?", code);
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
