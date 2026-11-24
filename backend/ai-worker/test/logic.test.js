import { test } from "node:test";
import assert from "node:assert/strict";
import { invitePage, storeLink } from "../src/invite.js";
import { cleanBarcode, cleanBarcodeSubmission, pickBarcodeEntry } from "../src/barcodes.js";
import { referralError, referralQualifies, cleanSocialProfile, friendView, makeFriendCode, normalizeFriendCode, cleanFeedback, berlinDay, checkClaims, decide, extend, fileFromPath, isoWeek, makeCode, minusDays, modelFromPath, normalizeCode, safeEqual, tierOf } from "../src/logic.js";

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

test("Freundescode", () => {
  assert.match(makeFriendCode(new Uint8Array([1, 2, 3, 4, 5, 6, 7, 8])), /^[A-Z2-9]{4}-[A-Z2-9]{4}$/);
  assert.equal(normalizeFriendCode(" abcd efgh "), "ABCD-EFGH");
  assert.equal(normalizeFriendCode("ABCD-EFG0"), null); // 0 gibt es nicht
  assert.equal(normalizeFriendCode("abc"), null);
});

test("Einladen: einmal, nicht selbst, nicht gegenseitig; Punkte ab 7 Tagen", () => {
  assert.equal(referralError({ inviter: "a", invitee: "b", alreadyInvited: false, invitedByInvitee: false }), null);
  assert.equal(referralError({ inviter: null, invitee: "b" }), "unknown_code");
  assert.equal(referralError({ inviter: "b", invitee: "b" }), "self");
  assert.equal(referralError({ inviter: "a", invitee: "b", alreadyInvited: true }), "already_invited");
  assert.equal(referralError({ inviter: "a", invitee: "b", alreadyInvited: false, invitedByInvitee: true }), "mutual");
  assert.equal(referralQualifies(6), false);
  assert.equal(referralQualifies(7), true);
  assert.equal(referralQualifies("7"), false);
});

test("Freunde sehen nur Freigegebenes, standardmäßig privat", () => {
  const p = cleanSocialProfile({ name: "Tim", badge: "days_7", streak: 12, achievements: ["days_7", "<x>"] });
  assert.deepEqual(p.achievements, ["days_7"]);
  const view = friendView({ ...p, code: "ABCD-EFGH" });
  assert.deepEqual(view, { code: "ABCD-EFGH", name: "Tim", avatar: null, badge: null, achievements: null, streak: null, best: null });
  const shared = friendView({ ...cleanSocialProfile({ name: "Tim", badge: "days_7", streak: 12, share: { streak: true, badges: true } }), code: "X" });
  assert.equal(shared.streak, 12);
  assert.equal(shared.best, 12); // ohne Angabe = aktuelle Serie
  assert.equal(cleanSocialProfile({ streak: 5, best: 81 }).best, 81);
  assert.equal(cleanSocialProfile({ avatar: "06_panda" }).avatar, "06_panda");
  assert.equal(cleanSocialProfile({ avatar: "<img>" }).avatar, null);
  assert.equal(cleanSocialProfile({ streak: 9, best: 3 }).best, 9); // nie kleiner als aktuell
  assert.equal(shared.badge, "days_7");
  assert.equal(shared.achievements, null);
  assert.equal("weight" in shared || "kcal" in shared, false);
});

test("safeEqual", () => {
  assert.ok(safeEqual("abc", "abc"));
  assert.ok(!safeEqual("abc", "abd"));
  assert.ok(!safeEqual("abc", "abcd"));
});

test("Einladungsseite: Vorschau, Code, Name maskiert, Store-Link mit Referrer", () => {
  const html = invitePage({ code: "ABCD-2345", name: "<Tim>", base: "https://x.dev", storeUrl: "" });
  assert.ok(html.includes('og:image" content="https://x.dev/i/card.png"'));
  assert.ok(html.includes("&lt;Tim&gt; lädt dich zu Dunories ein"));
  assert.ok(!html.includes("<Tim>"));
  assert.ok(html.includes("Testphase"));
  assert.equal(storeLink("https://play.google.com/store/apps/details?id=de.dunories", "ABCD-2345"),
    "https://play.google.com/store/apps/details?id=de.dunories&referrer=code%3DABCD-2345");
  assert.ok(invitePage({ code: "ABCD-2345", name: "", base: "b", storeUrl: "https://s?id=1" }).includes("Im Play Store laden"));
});

test("Barcode-Sammlung: nur saubere Meldungen, Mehrheit gewinnt", () => {
  assert.equal(cleanBarcode("4260999170016"), "4260999170016");
  assert.equal(cleanBarcode("12ab"), null);
  const ok = { barcode: "4260999170016", name: " Dinkel-Toast ", unit: "g", per100: { kcal: 248, proteinG: 10.5, carbsG: 42, fatG: 3.1, saltG: 1.1 } };
  const clean = cleanBarcodeSubmission(ok);
  assert.equal(clean.name, "Dinkel-Toast");
  assert.equal(clean.per100.sugarG, null);
  assert.equal(cleanBarcodeSubmission({ ...ok, per100: { ...ok.per100, kcal: 2000 } }), null);
  assert.equal(cleanBarcodeSubmission({ ...ok, per100: { kcal: 300, proteinG: 60, carbsG: 60, fatG: 10 } }), null);
  assert.equal(cleanBarcodeSubmission({ ...ok, unit: "stk" }), null);
  const a = { name: "Toast", unit: "g", per100: { kcal: 248, proteinG: 10.5, carbsG: 42, fatG: 3.1 }, ts: 1 };
  const b = { name: "Toast falsch", unit: "g", per100: { kcal: 900, proteinG: 1, carbsG: 1, fatG: 99 }, ts: 2 };
  const c = { name: "Dinkeltoast", unit: "g", per100: { kcal: 249, proteinG: 10.4, carbsG: 42.2, fatG: 3 }, ts: 3 };
  const picked = pickBarcodeEntry([a, b, c]);
  assert.equal(picked.count, 2);
  assert.equal(picked.name, "Dinkeltoast");
  assert.equal(pickBarcodeEntry([]), null);
});
