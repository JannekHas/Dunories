// Erzeugt die nutzbare Studio-Datei mit Schrift, Logo und Beispiel-Screenshots:
// design/werbung/Dunories-Studio.html (lokal, gitignored – Werbematerial bleibt auf deinem PC).
// Aufruf im Projektordner: node design/studio/build.mjs
// Beispielbilder liegen in design/werbung/studio/ (heute.jpg, vorschlag.jpg, spaghetti.jpg).
import { readFileSync, writeFileSync, existsSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..", "..");
const b64 = (p) => (existsSync(p) ? readFileSync(p).toString("base64") : "");
const img = (name) => b64(join(root, "design", "werbung", "studio", name));

const html = readFileSync(join(root, "design", "studio", "studio.html"), "utf8")
  .replaceAll("__FONT__", b64(join(root, "app", "src", "main", "res", "font", "bricolage_grotesque.ttf")))
  .replaceAll("__LOGO__", b64(join(root, "design", "logo", "playstore_512.png")))
  .replace("__IMG_HEUTE__", img("heute.jpg"))
  .replace("__IMG_VORSCHLAG__", img("vorschlag.jpg"))
  .replace("__IMG_SPAGHETTI__", img("spaghetti.jpg"));
writeFileSync(join(root, "design", "werbung", "Dunories-Studio.html"), html);
console.log("design/werbung/Dunories-Studio.html erzeugt");
