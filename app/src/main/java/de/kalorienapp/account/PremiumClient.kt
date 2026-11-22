package de.kalorienapp.account

import de.kalorienapp.data.remote.Http
import de.kalorienapp.data.remote.HttpStatusException
import de.kalorienapp.data.remote.asObject
import de.kalorienapp.data.remote.lenientJson
import de.kalorienapp.data.remote.number
import de.kalorienapp.data.remote.string
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Stufe für die KI. Nur der Worker entscheidet – die App zeigt das nur an. */
enum class AiTier { PREMIUM, GUEST, FREE }

/** Woher Premium kommt. */
enum class PremiumSource { TEST, TRIAL, CODE, ADMIN, LIFETIME, NONE }

data class AiStatus(
    val tier: AiTier,
    val source: PremiumSource,
    /** Ende von Premium (ms), null bei Testphase/Lifetime/ohne Premium. */
    val until: Long?,
    val usedToday: Int,
    val dailyLimit: Int,
    /** Übrige Gratis-Aufrufe (nur Gast). */
    val freeLeft: Int?,
    val trialDays: Int,
    /** Kurze Kennung für den Support (erste 8 Zeichen). */
    val id: String,
    /** Tester-Belohnungsprogramm: Summe der Punkte für bewertetes Feedback. */
    val testerPoints: Int = 0,
    /** Neu bewertete Feedbacks mit Punkten, für die noch keine Danke-Nachricht kam. */
    val newRewards: Int = 0,
    val newPoints: Int = 0,
) {
    val premium get() = tier == AiTier.PREMIUM

    companion object {
        fun parse(json: String): AiStatus? {
            val o = runCatching { lenientJson.parseToJsonElement(json).asObject() }.getOrNull() ?: return null
            val tier = when (o.string("tier")) {
                "premium" -> AiTier.PREMIUM
                "guest" -> AiTier.GUEST
                "free" -> AiTier.FREE
                else -> return null
            }
            val lifetime = (o["lifetime"] as? JsonPrimitive)?.booleanOrNull == true
            val source = when {
                lifetime -> PremiumSource.LIFETIME
                else -> when (o.string("source")) {
                    "test" -> PremiumSource.TEST
                    "trial" -> PremiumSource.TRIAL
                    "code" -> PremiumSource.CODE
                    "admin" -> PremiumSource.ADMIN
                    "lifetime" -> PremiumSource.LIFETIME
                    else -> PremiumSource.NONE
                }
            }
            return AiStatus(
                tier = tier,
                source = source,
                until = o.number("until")?.toLong(),
                usedToday = o.number("usedToday")?.toInt() ?: 0,
                dailyLimit = o.number("dailyLimit")?.toInt() ?: 0,
                freeLeft = o.number("freeLeft")?.toInt(),
                trialDays = o.number("trialDays")?.toInt() ?: 0,
                id = o.string("id").orEmpty(),
                testerPoints = o.number("testerPoints")?.toInt() ?: 0,
                newRewards = o.number("newRewards")?.toInt() ?: 0,
                newPoints = o.number("newPoints")?.toInt() ?: 0,
            )
        }
    }
}

/** Ergebnis beim Code-Einlösen. */
enum class RedeemResult { OK, INVALID, EXPIRED, USED_UP, ALREADY, TOO_MANY, NEEDS_ACCOUNT, NETWORK }

/** Status und Code-Einlösen beim KI-Worker. */
class PremiumClient(
    private val baseUrl: String,
    private val token: suspend () -> String?,
) {
    private suspend fun headers(): Map<String, String>? = token()?.let { mapOf("Authorization" to "Bearer $it") }

    /** null = nicht eingerichtet oder nicht erreichbar. */
    suspend fun status(): AiStatus? {
        if (baseUrl.isBlank()) return null
        val h = runCatching { headers() }.getOrNull() ?: return null
        return runCatching { AiStatus.parse(Http.get("$baseUrl/v1/status", h, timeoutMs = 15_000)) }.getOrNull()
    }

    /** Tester-Name und Danke-Liste beim Worker aktualisieren (nur wenn schon angemeldet – Gast oder Konto). */
    suspend fun saveTester(name: String, credits: Boolean) {
        val h = runCatching { headers() }.getOrNull() ?: return
        val body = buildJsonObject {
            put("name", name)
            put("credits", credits)
        }.toString()
        runCatching { Http.postJson("$baseUrl/v1/tester", body, h, timeoutMs = 15_000) }
    }

    /** Danke-Nachricht wurde gezeigt. */
    suspend fun rewardsSeen() {
        val h = runCatching { headers() }.getOrNull() ?: return
        runCatching { Http.postJson("$baseUrl/v1/rewards/seen", "{}", h, timeoutMs = 15_000) }
    }

    suspend fun redeem(code: String): Pair<RedeemResult, AiStatus?> {
        val h = runCatching { headers() }.getOrNull() ?: return RedeemResult.NETWORK to null
        val body = buildJsonObject { put("code", code) }.toString()
        return try {
            RedeemResult.OK to AiStatus.parse(Http.postJson("$baseUrl/v1/redeem", body, h, timeoutMs = 15_000))
        } catch (e: HttpStatusException) {
            redeemError(e.body) to null
        } catch (e: java.io.IOException) {
            RedeemResult.NETWORK to null
        }
    }

    internal companion object {
        fun redeemError(body: String): RedeemResult {
            val error = runCatching { lenientJson.parseToJsonElement(body).asObject() }.getOrNull()?.string("error")
            return when (error) {
                "invalid_code" -> RedeemResult.INVALID
                "expired_code" -> RedeemResult.EXPIRED
                "used_up_code" -> RedeemResult.USED_UP
                "already_redeemed" -> RedeemResult.ALREADY
                "too_many" -> RedeemResult.TOO_MANY
                "needs_account" -> RedeemResult.NEEDS_ACCOUNT
                else -> RedeemResult.NETWORK
            }
        }
    }
}
