import { test } from "node:test";
import assert from "node:assert/strict";
import { berlinDay, checkClaims, decide, fileFromPath, isoWeek, minusDays, modelFromPath, safeEqual } from "../src/logic.js";

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
  const ok = { iss: "https://securetoken.google.com/dunories", aud: "dunories", exp: now + 100, iat: now - 10, sub: "u1" };
  assert.equal(checkClaims(ok, "dunories", now), "u1");
  assert.equal(checkClaims({ ...ok, aud: "fremd" }, "dunories", now), null);
  assert.equal(checkClaims({ ...ok, exp: now - 100 }, "dunories", now), null);
  assert.equal(checkClaims({ ...ok, sub: "" }, "dunories", now), null);
});

test("Limit: zählt pro Anfrage einmal, automatische Wochenauswertung einmal pro Woche", () => {
  assert.equal(decide({ kind: "food", usedToday: 39, limit: 40, alreadyCounted: false, weeklyDone: false }), "ok");
  assert.equal(decide({ kind: "food", usedToday: 40, limit: 40, alreadyCounted: false, weeklyDone: false }), "daily_limit");
  assert.equal(decide({ kind: "food", usedToday: 40, limit: 40, alreadyCounted: true, weeklyDone: false }), "ok");
  assert.equal(decide({ kind: "weekly", usedToday: 40, limit: 40, alreadyCounted: false, weeklyDone: false }), "daily_limit");
  assert.equal(decide({ kind: "weekly-auto", usedToday: 40, limit: 40, alreadyCounted: false, weeklyDone: false }), "ok");
  assert.equal(decide({ kind: "weekly-auto", usedToday: 0, limit: 40, alreadyCounted: false, weeklyDone: true }), "weekly_done");
});

test("safeEqual", () => {
  assert.ok(safeEqual("abc", "abc"));
  assert.ok(!safeEqual("abc", "abd"));
  assert.ok(!safeEqual("abc", "abcd"));
});
