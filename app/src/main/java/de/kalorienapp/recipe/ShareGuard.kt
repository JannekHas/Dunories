package de.kalorienapp.recipe

/**
 * Schutz für Inhalte, die andere Apps an Dunories „teilen“ (Sicherheits-Check Nov. 2026):
 * - nur fremde Dateien über `content://` – nie `file://` und nie eigene Dateien (sonst könnte eine fremde App
 *   z. B. die eigene Datenbank als „Video“ unterschieben, die dann zu Gemini hochgeladen würde),
 * - höchstens ein geteilter Inhalt pro [windowMs] – sonst könnte eine App im Sekundentakt teilen und das
 *   gemeinsame KI-Kontingent leeren.
 */
class ShareGuard(private val ownAuthorityPrefix: String, private val windowMs: Long = 60_000L) {
    private var lastAccepted: Long? = null

    /** Darf diese Datei gelesen werden? [scheme]/[authority] aus der geteilten Uri. */
    fun isAllowedFile(scheme: String?, authority: String?): Boolean {
        if (!scheme.equals("content", ignoreCase = true)) return false
        val auth = authority?.lowercase() ?: return false
        return !auth.startsWith(ownAuthorityPrefix.lowercase())
    }

    /** true = annehmen (und Zeit merken), false = zu kurz nach dem letzten geteilten Inhalt. */
    fun tryAccept(now: Long): Boolean {
        val last = lastAccepted
        if (last != null && now - last in 0 until windowMs) return false
        lastAccepted = now
        return true
    }
}
