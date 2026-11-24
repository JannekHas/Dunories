// Gemeinsame Barcode-Sammlung (Entscheidung des Nutzers, Nov. 2026, Variante A):
// Kennt Open Food Facts ein Produkt nicht und liest jemand die Nährwerttabelle per Etikett-Foto ab, schickt die App
// Barcode, Name und Werte pro 100 g/ml (ohne Konto-Daten). Andere bekommen das Produkt beim Scan sofort – als Vorschlag
// „von anderen Nutzern – kurz prüfen“. Gegen Fehler/Unfug: je Person nur eine Meldung pro Barcode, gezeigt wird die
// Variante, auf die sich die meisten Meldungen einigen.

const FIELDS = ["kcal", "proteinG", "carbsG", "fatG", "saturatedFatG", "sugarG", "fiberG", "saltG"];
const LIMITS = { kcal: 900, proteinG: 100, carbsG: 100, fatG: 100, saturatedFatG: 100, sugarG: 100, fiberG: 100, saltG: 100 };

export const MAX_BARCODE_SUBMISSIONS_PER_DAY = 30;

/** Nur Ziffern-Barcodes (EAN/UPC), 6–14 Stellen. */
export function cleanBarcode(raw) {
  const s = String(raw ?? "").trim();
  return /^\d{6,14}$/.test(s) ? s : null;
}

/** Meldung aus der App prüfen; null = unbrauchbar. */
export function cleanBarcodeSubmission(body) {
  if (!body || typeof body !== "object") return null;
  const barcode = cleanBarcode(body.barcode);
  const name = String(body.name ?? "").trim().replace(/\s+/g, " ").slice(0, 80);
  const unit = body.unit === "ml" ? "ml" : body.unit === "g" ? "g" : null;
  const p = body.per100 && typeof body.per100 === "object" ? body.per100 : null;
  if (!barcode || name.length < 2 || !unit || !p) return null;
  const per100 = {};
  for (const f of FIELDS) {
    const v = p[f];
    if (v === null || v === undefined) {
      if (f === "kcal" || f === "proteinG" || f === "carbsG" || f === "fatG") return null;
      per100[f] = null;
      continue;
    }
    if (typeof v !== "number" || !Number.isFinite(v) || v < 0 || v > LIMITS[f]) return null;
    per100[f] = Math.round(v * 10) / 10;
  }
  // Grober Plausibilitäts-Check: Makros zusammen höchstens 100 g pro 100 g.
  if (unit === "g" && per100.proteinG + per100.carbsG + per100.fatG > 105) return null;
  return { barcode, name, unit, per100 };
}

/** Gleiche Meinung: kcal höchstens 5 % (mind. 5 kcal) und Makros höchstens 1,5 g auseinander. */
function similar(a, b) {
  const p = a.per100, q = b.per100;
  return Math.abs(p.kcal - q.kcal) <= Math.max(5, 0.05 * Math.max(p.kcal, q.kcal)) &&
    ["proteinG", "carbsG", "fatG"].every((f) => Math.abs(p[f] - q[f]) <= 1.5);
}

/**
 * Aus allen Meldungen zu einem Barcode die Variante wählen, auf die sich die meisten einigen (bei Gleichstand die
 * älteste). Ergebnis mit [count] = Zahl der Meldungen dieser Variante.
 */
export function pickBarcodeEntry(submissions) {
  if (!submissions?.length) return null;
  const groups = [];
  for (const s of [...submissions].sort((a, b) => a.ts - b.ts)) {
    const group = groups.find((g) => similar(g[0], s));
    if (group) group.push(s); else groups.push([s]);
  }
  let best = null;
  for (const g of groups) if (!best || g.length > best.length) best = g;
  const latest = best[best.length - 1];
  return { name: latest.name, unit: latest.unit, per100: latest.per100, count: best.length };
}
