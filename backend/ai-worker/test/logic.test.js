import { test } from "node:test";
import assert from "node:assert/strict";
import { cleanFeedback, berlinDay, checkClaims, decide, extend, fileFromPath, isoWeek, makeCode, minusDays, modelFromPath, normalizeCode, safeEqual, tierOf } from "../src/logic.js";

test("Tag nach deutscher Zeit: 23:30 UTC im Winter ist schon der nächste Tag", () => {
  assert.equal(berlinDay(Date.UTC(2026, 10, 22, 22, 59)), "2026-11-22");
  assert.equal(berlinDay(Date.UTC(2026, 10, 22, 23, 0)), "2026-11-23");
  // Sommerzeit: Wechsel um 22:00 UTC
  assert.equal(berlinDay(Date.UTC(2026, 6, 1, 22, 0)), "2026-07-02");
});

test("ISO-Woche", () => {
  assert.equal(isoWeek("2026-11-22"), "2026-W47");
  assert.equal(isoWeek("2026-11-23"), "2026-W48");
  assert.equal(isoWeek("2027-01-01"), "2026-W53");
  assert.equal(isoWeek("2026-01-01"), "2026-W01");
});

test("minusDays über Monatsgrenze", () => {
  assert.equal(minusDays("2026-12-01", 1), "2026-11-30");
  assert.equal(minusDays("2026-01-01", 90), "2025-10-03");
});

test("nur erlaubte Modelle und Dateinamen", () => {
  assert.equal(modelFromPath("/v1/models/gemini-3.1-flash-lite:generateContent"), "gemini-3.1-flash-lite");
  assert.equal(modelFromPath("/v1/models/gemini-3-pro:generateContent"), null);
  assert.equal(modelFromPath("/v1/models/gemini-3.1-flash-lite:streamGenerateContent"), null);
  assert.equal(fileFromPath("/v1/files/abc-123"), "files/abc-123");
  assert.equal(fileFromPath("/v1/files/../models"), null);
});

test("Token-Inhalt", () => {
  const now = 1_800_000_000;
  const ok = { iss: "https://securetoken.google.com/dunories", aud: "dunories", exp: now + 100, iat: now - 10, sub: "u1",
    email: "A@b.de", email_verified: true, firebase: { sign_in_provider: "google.com" } };
  assert.deepEqual(checkClaims(ok, "dunories", now), { uid: "u1", anonymous: false, email: "a@b.de" });
  const guest = { ...ok, email: undefined, firebase: { sign_in_provider: "anonymous" } };
  assert.equal(checkClaims(guest, "dunories", now).anonymous, true);
  assert.equal(checkClaims({ ...ok, aud: "fremd" }, "dunories", now), null);
  assert.equal(checkClaims({ ...ok, exp: now - 100 }, "dunories", now), null);
  assert.equal(checkClaims({ ...ok, sub: "" }, "dunories", now), null);
});

const base = { usedToday: 0, limit: 40, alreadyCounted: false, weeklyDone: false, freeLeft: 5 };

test("Limit: Premium 40 pro Tag, pro Anfrage einmal gezählt", () => {
  assert.equal(decide({ ...base, kind: "food", tier: "premium", usedToday: 39 }), "ok");
  assert.equal(decide({ ...base, kind: "food", tier: "premium", usedToday: 40 }), "daily_limit");
  assert.equal(decide({ ...base, kind: "food", tier: "premium", usedToday: 40, alreadyCounted: true }), "ok");
  assert.equal(decide({ ...base, kind: "weekly", tier: "premium", usedToday: 40 }), "daily_limit");
});

test("Gast: 5 gratis, dann Konto; Konto ohne Premium: Paywall", () => {
  assert.equal(decide({ ...base, kind: "food", tier: "guest", freeLeft: 1 }), "ok");
  assert.equal(decide({ ...base, kind: "food", tier: "guest", freeLeft: 0 }), "needs_account");
  assert.equal(decide({ ...base, kind: "food", tier: "free" }), "needs_premium");
});

test("Automatische Wochenauswertung: nur Premium, einmal pro Woche, zählt nicht", () => {
  assert.equal(decide({ ...base, kind: "weekly-auto", tier: "premium", usedToday: 40 }), "ok");
  assert.equal(decide({ ...base, kind: "weekly-auto", tier: "premium", weeklyDone: true }), "weekly_done");
  assert.equal(decide({ ...base, kind: "weekly-auto", tier: "guest" }), "needs_premium");
});

test("Stufe: Testphase, Freischaltung, Probezeit", () => {
  const now = 1_000_000_000_000;
  const day = 86400000;
  const p = { now, anonymous: false, testAllPremium: false, ent: null, trialStart: null, trialDays: 7 };
  assert.equal(tierOf({ ...p, testAllPremium: true, anonymous: true }).tier, "premium");
  assert.equal(tierOf({ ...p, anonymous: true }).tier, "guest");
  assert.equal(tierOf(p).tier, "free");
  assert.equal(tierOf({ ...p, trialStart: now - 6 * day }).source, "trial");
  assert.equal(tierOf({ ...p, trialStart: now - 8 * day }).tier, "free");
  assert.equal(tierOf({ ...p, ent: { lifetime: false, until: now + day, source: "code" } }).source, "code");
  assert.equal(tierOf({ ...p, ent: { lifetime: false, until: now - 1, source: "code" } }).tier, "free");
  assert.equal(tierOf({ ...p, ent: { lifetime: true, until: null, source: "code" } }).lifetime, true);
});

test("Freischaltung verlängert die Restzeit", () => {
  const now = 1_000_000_000_000;
  const day = 86400000;
  assert.equal(extend(null, now, 30).until, now + 30 * day);
  assert.equal(extend({ lifetime: false, until: now + 5 * day }, now, 30).until, now + 35 * day);
  assert.equal(extend({ lifetime: false, until: now - day }, now, 30).until, now + 30 * day);
  assert.equal(extend(null, now, 0).lifetime, true);
  assert.equal(extend({ lifetime: true }, now, 30).lifetime, true);
});

test("Codes", () => {
  const code = makeCode(new Uint8Array([0, 1, 2, 3, 4, 5, 6, 7]));
  assert.match(code, /^DUNO-[A-Z2-9]{4}-[A-Z2-9]{4}$/);
  assert.equal(normalizeCode(" duno abcd efgh "), "DUNO-ABCD-EFGH");
  assert.equal(normalizeCode("abcd-efgh"), "DUNO-ABCD-EFGH");
  assert.equal(normalizeCode("abc"), null);
});

test("Feedback prüfen", () => {
  assert.equal(cleanFeedback({ type: "spam", text: "x" }), null);
  assert.equal(cleanFeedback({ type: "bug", text: "  " }), null);
  assert.equal(cleanFeedback({ type: "bug", text: "", screenshot: "<script>" }), null);
  const f = cleanFeedback({ type: "idea", text: " Hallo ", name: "Tim", credits: true, screenshot: "QUJD", device: "Pixel" });
  assert.equal(f.text, "Hallo");
  assert.equal(f.credits, true);
  assert.equal(f.screenshot, "QUJD");
  assert.equal(cleanFeedback({ type: "praise", text: "x".repeat(6000) }).text.length, 5000);
});

test("safeEqual", () => {
  assert.ok(safeEqual("abc", "abc"));
  assert.ok(!safeEqual("abc", "abd"));
  assert.ok(!safeEqual("abc", "abcd"));
});
