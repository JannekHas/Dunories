// Erzeugt die nutzbare Admin-Datei mit Schlüssel: admin/Dunories-Admin.html (gitignored, nicht weitergeben).
// Aufruf im Projektordner: node backend/admin-dashboard/build.mjs
import { readFileSync, writeFileSync, mkdirSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..", "..");
const key = readFileSync(join(root, "admin", "admin-key.txt"), "utf8").trim();
const worker = "https://dunories-ai.dunories-ai-worker.workers.dev";
if (!/^[A-Za-z0-9_-]{20,}$/.test(key)) throw new Error("admin/admin-key.txt fehlt oder ist ungültig");
const html = readFileSync(join(root, "backend", "admin-dashboard", "dashboard.html"), "utf8")
  .replace("__WORKER_URL__", worker)
  .replace("__ADMIN_KEY__", key);
mkdirSync(join(root, "admin"), { recursive: true });
writeFileSync(join(root, "admin", "Dunories-Admin.html"), html);
console.log("admin/Dunories-Admin.html erzeugt");
