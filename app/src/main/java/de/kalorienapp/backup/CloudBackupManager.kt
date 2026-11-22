package de.kalorienapp.backup

import android.content.Context
import android.net.Uri
import android.util.Base64
import de.kalorienapp.BuildConfig
import de.kalorienapp.account.AccountManager
import de.kalorienapp.data.AppSettings
import de.kalorienapp.data.remote.Http
import de.kalorienapp.data.remote.HttpStatusException
import de.kalorienapp.data.remote.firebase.FirestoreRest
import de.kalorienapp.data.remote.firebase.FirestoreRest.Write
import de.kalorienapp.domain.CloudBackup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant

/**
 * Cloud-Sicherung (Phase 2): die normale Sicherungs-ZIP ([BackupManager]) in Firestore unter `users/{uid}/backups`,
 * Base64 in Stücken, zwei Stände. Firestore per REST mit dem Firebase-Token des Nutzers; die Regeln erlauben
 * jedem nur seinen eigenen Bereich.
 */
class CloudBackupManager(
    private val context: Context,
    private val account: AccountManager,
    private val backup: BackupManager,
    private val settings: AppSettings,
    private val identity: Map<String, String>,
) {
    private val projectId = BuildConfig.FIREBASE_PROJECT_ID
    private val lock = Mutex()

    sealed interface UploadResult {
        data object Uploaded : UploadResult
        data object Unchanged : UploadResult

        /** Ein anderes Gerät hat inzwischen gesichert → nicht überschreiben, Nutzer vergleicht. */
        data object Conflict : UploadResult
    }

    private suspend fun headers() = identity + ("Authorization" to "Bearer ${account.idToken()}")

    private fun uid() = account.user.value?.uid ?: error("Nicht angemeldet")

    private suspend fun commit(writes: List<Write>) {
        Http.postJson(FirestoreRest.commitUrl(projectId), FirestoreRest.commitBody(projectId, writes), headers(), timeoutMs = 60_000)
    }

    private suspend fun getOrNull(path: String): String? = try {
        Http.get("${FirestoreRest.documentsUrl(projectId)}/$path", headers(), timeoutMs = 60_000)
    } catch (e: HttpStatusException) {
        if (e.code == 404) null else throw e
    }

    suspend fun metas(): List<CloudBackup.Meta> {
        val uid = uid()
        return CloudBackup.SLOTS.mapNotNull { slot -> getOrNull(FirestoreRest.backupPath(uid, slot))?.let { FirestoreRest.parseMeta(slot, it) } }
    }

    /** Neuester vollständiger Stand in der Cloud (null = leer). */
    suspend fun newest(): CloudBackup.Meta? = CloudBackup.newest(metas())

    /** Einwilligung beim ersten Anmelden speichern. */
    suspend fun saveConsent(version: Int) {
        commit(listOf(Write.Update(FirestoreRest.userPath(uid()), FirestoreRest.userFields(Instant.now().toString(), version))))
    }

    /**
     * Sicherung hochladen. [auto] = automatisch (täglich/beim Verlassen): nur bei Änderungen und nie über den Stand
     * eines anderen Geräts.
     */
    suspend fun upload(auto: Boolean): UploadResult = lock.withLock {
        val uid = uid()
        val created = backup.create()
        if (auto && created.contentHash == settings.cloudLastHash && settings.cloudUid == uid) return UploadResult.Unchanged
        val metas = metas()
        val newest = CloudBackup.newest(metas)
        if (auto && newest != null && (settings.cloudUid != uid || newest.createdAt != settings.cloudLastUploadAt)) {
            settings.setCloudConflict(true)
            return UploadResult.Conflict
        }
        val slot = CloudBackup.targetSlot(metas)
        val old = metas.firstOrNull { it.slot == slot }
        val base64 = withContext(Dispatchers.IO) { Base64.encodeToString(created.file.readBytes(), Base64.NO_WRAP) }
        val parts = CloudBackup.split(base64)
        val createdAt = created.summary.createdAt ?: System.currentTimeMillis()
        val s = created.summary
        val meta = CloudBackup.Meta(
            slot = slot, createdAt = createdAt, chunks = parts.size, complete = false,
            appVersion = BuildConfig.VERSION_NAME,
            foodEntries = s.foodEntries, weights = s.weights, measurements = s.measurements, reports = s.reports, recipes = s.recipes,
            firstDay = s.firstDay?.toEpochDay(), lastDay = s.lastDay?.toEpochDay(),
        )
        // 1. Stand als unvollständig markieren, 2. Stücke einzeln (je ≤ 1 MiB), 3. vollständig + Reste löschen – in einem Schritt.
        commit(listOf(Write.Update(FirestoreRest.backupPath(uid, slot), FirestoreRest.metaFields(meta))))
        parts.forEachIndexed { i, part ->
            commit(listOf(Write.Update(FirestoreRest.chunkPath(uid, slot, i), FirestoreRest.chunkFields(part))))
        }
        val leftovers = (parts.size until (old?.chunks ?: 0)).map { Write.Delete(FirestoreRest.chunkPath(uid, slot, it)) }
        commit(listOf(Write.Update(FirestoreRest.backupPath(uid, slot), FirestoreRest.metaFields(meta.copy(complete = true)))) + leftovers)
        settings.setCloudUploaded(uid, createdAt, created.contentHash)
        UploadResult.Uploaded
    }

    /** Neuesten Stand laden und als Sicherung vorbereiten (eingespielt wird erst nach Bestätigung). */
    suspend fun prepareRestore(): BackupManager.PrepareResult = lock.withLock {
        val uid = uid()
        val newest = newest() ?: return BackupManager.PrepareResult.Failed(BackupFormat.Problem.NOT_A_BACKUP)
        val base64 = buildString {
            for (i in 0 until newest.chunks) {
                append(getOrNull(FirestoreRest.chunkPath(uid, newest.slot, i))?.let(FirestoreRest::parseChunk) ?: error("Stück $i fehlt"))
            }
        }
        val file = File(context.cacheDir, "cloud-restore.zip")
        withContext(Dispatchers.IO) { file.writeBytes(Base64.decode(base64, Base64.NO_WRAP)) }
        val result = backup.prepareRestore(Uri.fromFile(file))
        file.delete()
        pendingRestore = newest.takeIf { result is BackupManager.PrepareResult.Ready }
        result
    }

    private var pendingRestore: CloudBackup.Meta? = null

    /**
     * Direkt vor dem Einspielen aufrufen: danach gilt der Cloud-Stand als „von diesem Handy“,
     * das automatische Sichern macht normal weiter (kein Konflikt mit sich selbst).
     */
    fun markRestoring() {
        val meta = pendingRestore ?: return
        settings.setCloudUploaded(uid(), meta.createdAt, "")
        settings.setCloudConflict(false)
    }

    /** Alles dieses Nutzers in der Cloud löschen (vor dem Löschen des Kontos). */
    suspend fun deleteAll() = lock.withLock {
        val uid = uid()
        val writes = metas().flatMap { m ->
            (0 until m.chunks).map { Write.Delete(FirestoreRest.chunkPath(uid, m.slot, it)) } + Write.Delete(FirestoreRest.backupPath(uid, m.slot))
        } + Write.Delete(FirestoreRest.userPath(uid))
        writes.chunked(400).forEach { commit(it) }
        settings.clearCloud()
    }
}
