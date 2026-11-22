package de.kalorienapp.account

import android.app.Activity
import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.gms.tasks.Task
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.GoogleAuthProvider
import de.kalorienapp.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Angemeldetes Konto (nur, was die App braucht). */
data class AccountUser(val uid: String, val email: String?)

/**
 * Freiwilliges Konto (Phase 2): „Mit Google weiter“ über den Credential Manager → Firebase-Anmeldung.
 * Es gibt kein getrenntes Registrieren – Firebase legt das Konto beim ersten Mal selbst an.
 * Firebase wird von Hand gestartet (Werte aus google-services.json über BuildConfig), ohne Google-Services-Plugin.
 */
class AccountManager(private val context: Context) {

    /** false, wenn google-services.json fehlt oder Google-Anmeldung in der Konsole noch nicht aktiv ist. */
    val available: Boolean =
        BuildConfig.FIREBASE_APP_ID.isNotBlank() && BuildConfig.FIREBASE_API_KEY.isNotBlank() && BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()

    private val auth: FirebaseAuth? = if (!available) null else runCatching {
        val app = FirebaseApp.getApps(context).firstOrNull() ?: FirebaseApp.initializeApp(
            context,
            FirebaseOptions.Builder()
                .setApplicationId(BuildConfig.FIREBASE_APP_ID)
                .setApiKey(BuildConfig.FIREBASE_API_KEY)
                .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                .build(),
        )
        FirebaseAuth.getInstance(app)
    }.getOrNull()

    private val _user = MutableStateFlow(auth?.currentUser?.let { AccountUser(it.uid, it.email) })
    val user: StateFlow<AccountUser?> = _user.asStateFlow()

    init {
        auth?.addAuthStateListener { a -> _user.value = a.currentUser?.let { AccountUser(it.uid, it.email) } }
    }

    /** Google-Kontoauswahl zeigen und anmelden. Abbruch durch den Nutzer → Exception (wird in der UI still behandelt). */
    suspend fun signInWithGoogle(activity: Activity): AccountUser {
        val a = auth ?: error("Konto nicht verfügbar")
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID).build())
            .build()
        val credential = CredentialManager.create(activity).getCredential(activity, request).credential
        check(credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            "Unerwartete Anmeldung"
        }
        val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
        val result = a.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
        val user = result.user ?: error("Anmeldung fehlgeschlagen")
        return AccountUser(user.uid, user.email).also { _user.value = it }
    }

    /** Frisches Firebase-Token für Firestore (wird bei Bedarf erneuert). */
    suspend fun idToken(): String {
        val u = auth?.currentUser ?: error("Nicht angemeldet")
        return u.getIdToken(false).await().token ?: error("Kein Token")
    }

    suspend fun signOut() {
        auth?.signOut()
        runCatching { CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest()) }
        _user.value = null
    }

    /**
     * Firebase-Konto löschen. Verlangt Firebase eine frische Anmeldung, wird kurz die Google-Auswahl gezeigt
     * und erneut versucht (die eigentliche Bestätigung ist der 6-stellige Code davor).
     */
    suspend fun deleteAccount(activity: Activity) {
        val u = auth?.currentUser ?: return
        try {
            u.delete().await()
        } catch (_: FirebaseAuthRecentLoginRequiredException) {
            signInWithGoogle(activity)
            auth.currentUser?.delete()?.await()
        }
        signOut()
    }
}

/** Play-Services-Task in Coroutine abwarten (ohne Zusatzbibliothek). */
internal suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}
