// Öffentliche Einladungsseite (…/i/ABCD-2345): Vorschaubild für WhatsApp & Co. (Open Graph), Freundescode zum
// Kopieren und – sobald Dunories im Play Store ist (Variable STORE_URL) – der Store-Link mit dem Code als
// „referrer“ (später liest die App ihn per Play Install Referrer aus und trägt ihn automatisch ein).
// Gezeigt wird nur der Profilname zum Code – nichts anderes.

const esc = (s) => String(s).replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]);

/** Store-Link mit dem Code als Referrer (für den automatischen Eintrag nach der Installation). */
export function storeLink(storeUrl, code) {
  if (!storeUrl) return null;
  const sep = storeUrl.includes("?") ? "&" : "?";
  return `${storeUrl}${sep}referrer=${encodeURIComponent(`code=${code}`)}`;
}

/** HTML der Einladungsseite. [name] leer → „Ein Freund“. */
export function invitePage({ code, name, base, storeUrl }) {
  const who = name && name.trim() ? name.trim().slice(0, 60) : "";
  const title = who ? `${who} lädt dich zu Dunories ein` : "Du bist zu Dunories eingeladen";
  const description = `Freundescode: ${code} – Kalorien & Makros tracken mit Foto, Serien und Freunden.`;
  const link = storeLink(storeUrl, code);
  const person = who ? esc(who) : "deinen Freund";
  const install = link
    ? `<a class="btn" href="${esc(link)}">Im Play Store laden</a>`
    : `<p class="note">Dunories ist gerade in der Testphase. Frag ${person} nach der Testversion.</p>`;
  return `<!doctype html>
<html lang="de">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="robots" content="noindex">
<title>${esc(title)}</title>
<meta property="og:type" content="website">
<meta property="og:site_name" content="Dunories">
<meta property="og:title" content="${esc(title)}">
<meta property="og:description" content="${esc(description)}">
<meta property="og:image" content="${esc(base)}/i/card.png">
<meta property="og:image:width" content="1200">
<meta property="og:image:height" content="630">
<meta name="twitter:card" content="summary_large_image">
<meta name="theme-color" content="#14141B">
<style>
  :root { color-scheme: dark; }
  body { margin: 0; background: radial-gradient(circle at 10% 0%, #3B1F5E 0, transparent 45%), radial-gradient(circle at 100% 100%, #0E4A55 0, transparent 50%), #14141B;
    color: #F2F2F7; font-family: system-ui, -apple-system, "Segoe UI", Roboto, sans-serif; min-height: 100vh; }
  main { max-width: 480px; margin: 0 auto; padding: 24px 16px 40px; }
  img { width: 100%; border-radius: 20px; display: block; }
  h1 { font-size: 26px; line-height: 1.2; margin: 24px 0 8px; }
  p { color: #A3A3B8; line-height: 1.5; margin: 8px 0; }
  .code { margin: 20px 0; padding: 18px; border-radius: 20px; background: rgba(255,255,255,.06); border: 1px solid rgba(255,255,255,.12);
    display: flex; align-items: center; justify-content: space-between; gap: 12px; }
  .code span { font-size: 30px; font-weight: 800; letter-spacing: 2px; color: #FFD400; }
  button, .btn { font: inherit; font-weight: 700; border: 0; border-radius: 999px; padding: 12px 18px; cursor: pointer; text-decoration: none; }
  button { background: rgba(34,211,238,.15); color: #22D3EE; }
  .btn { display: block; text-align: center; background: #22D3EE; color: #0A0A0A; margin: 16px 0; font-size: 18px; }
  ol { color: #A3A3B8; padding-left: 20px; line-height: 1.6; }
  .note { padding: 14px; border-radius: 16px; background: rgba(255,212,0,.08); color: #F2F2F7; }
  small { color: #6B6B80; display: block; margin-top: 28px; }
</style>
</head>
<body>
<main>
  <img src="/i/card.png" alt="Dunories">
  <h1>${esc(title)}</h1>
  <p>Kalorien und Makros tracken – per Foto, Barcode oder Beschreibung. Mit Serien, Erfolgen und Freunden.</p>
  <div class="code"><span id="c">${esc(code)}</span><button onclick="navigator.clipboard.writeText('${esc(code)}').then(()=>this.textContent='Kopiert ✓')">Kopieren</button></div>
  ${install}
  <ol>
    <li>Dunories installieren und kurz einrichten</li>
    <li>Unter <b>Social → Freunde</b> mit Google anmelden</li>
    <li><b>„Eingeladen worden?“</b> antippen und den Code eintragen</li>
  </ol>
  <p>Ihr seid dann direkt Freunde. Gewicht und Kalorien werden nie geteilt.</p>
  <small>Dunories · Diese Seite zeigt nur den Namen zum Freundescode.</small>
</main>
</body>
</html>`;
}
