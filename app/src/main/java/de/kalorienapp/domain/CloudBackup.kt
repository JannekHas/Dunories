package de.kalorienapp.domain

/**
 * Cloud-Sicherung (Phase 2, Entscheidungen des Nutzers Nov. 2026): die normale Sicherungs-ZIP in Firestore,
 * in Stücke geteilt (gratis, ohne Cloud Storage), **zwei Stände** („a“/„b“) – neu wird immer in den älteren
 * geschrieben, damit bei einem abgebrochenen Hochladen der letzte gute Stand bleibt. Reine Logik, unit-getestet.
 */
object CloudBackup {
    val SLOTS = listOf("a", "b")

    /** Firestore-Dokumente dürfen 1 MiB groß sein → Base64-Stücke mit Luft darunter. */
    const val CHUNK_CHARS = 900_000

    /** Beim Verlassen der App höchstens so oft automatisch sichern. */
    const val MIN_AUTO_INTERVAL_MS = 6 * 60 * 60 * 1000L

    /** Beschreibung eines Stands (Dokument `users/{uid}/backups/{slot}`). */
    data class Meta(
        val slot: String,
        val createdAt: Long,
        val chunks: Int,
        /** Erst true, wenn alle Stücke geschrieben sind. */
        val complete: Boolean,
        val appVersion: String = "",
        val foodEntries: Long = 0,
        val weights: Long = 0,
        val measurements: Long = 0,
        val reports: Long = 0,
        val recipes: Long = 0,
        val firstDay: Long? = null,
        val lastDay: Long? = null,
    )

    /** Neuester vollständiger Stand (null = Cloud leer). */
    fun newest(metas: List<Meta>): Meta? = metas.filter { it.complete }.maxByOrNull { it.createdAt }

    /** Wohin der nächste Stand kommt: nie über den neuesten vollständigen. */
    fun targetSlot(metas: List<Meta>): String {
        val keep = newest(metas)?.slot ?: return SLOTS.first()
        return SLOTS.first { it != keep }
    }

    fun split(base64: String, size: Int = CHUNK_CHARS): List<String> =
        if (base64.isEmpty()) listOf("") else base64.chunked(size)

    /** Was beim Anmelden bzw. vor dem automatischen Sichern passiert. */
    enum class Decision {
        /** Cloud leer oder dieses Handy hat den neuesten Stand geschrieben → einfach hochladen. */
        UPLOAD,

        /** Handy leer, Cloud hat Daten → „Sicherung wiederherstellen?“ */
        OFFER_RESTORE,

        /** Beide haben (verschiedene) Daten → Vergleich nebeneinander, Nutzer wählt. */
        COMPARE,
    }

    /**
     * @param localEmpty Tagebuch, Gewichte, Maße, Auswertungen und Rezepte auf dem Handy leer
     * @param lastUploadAt Zeitpunkt des Stands, den **dieses Handy** zuletzt für dieses Konto hochgeladen hat
     */
    fun decide(localEmpty: Boolean, cloud: Meta?, lastUploadAt: Long?): Decision = when {
        cloud == null -> Decision.UPLOAD
        localEmpty -> Decision.OFFER_RESTORE
        lastUploadAt != null && lastUploadAt == cloud.createdAt -> Decision.UPLOAD
        else -> Decision.COMPARE
    }

    /** 6 Ziffern zum Abtippen vor „Konto und Daten löschen“ (Wunsch des Nutzers statt Neu-Anmeldung). */
    fun deleteCode(random: kotlin.random.Random = kotlin.random.Random.Default): String =
        (0 until 6).joinToString("") { random.nextInt(10).toString() }
}
