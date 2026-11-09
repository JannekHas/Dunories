package de.kalorienapp.data.remote.gemini

import android.content.Context
import android.content.pm.PackageManager
import java.security.MessageDigest

/**
 * Kennung der App für API-Schlüssel mit Einschränkung „Android-Apps“ (Google Cloud → Anmeldedaten):
 * Google lässt Anfragen nur durch, wenn Paketname und SHA-1 des Signaturzertifikats passen.
 * Hält Gelegenheits-Missbrauch des Schlüssels aus der APK ab (kein vollständiger Schutz).
 */
object AppIdentity {

    fun headers(context: Context): Map<String, String> {
        val sha1 = signingSha1(context) ?: return emptyMap()
        return mapOf("X-Android-Package" to context.packageName, "X-Android-Cert" to sha1)
    }

    private fun signingSha1(context: Context): String? = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        val cert = info.signingInfo?.apkContentsSigners?.firstOrNull() ?: return null
        MessageDigest.getInstance("SHA-1").digest(cert.toByteArray()).joinToString("") { "%02X".format(it) }
    }.getOrNull()
}
