package de.kalorienapp.account

import de.kalorienapp.data.remote.Http
import de.kalorienapp.data.remote.HttpStatusException
import de.kalorienapp.data.remote.asObject
import de.kalorienapp.data.remote.lenientJson
import de.kalorienapp.data.remote.number
import de.kalorienapp.data.remote.string
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** Ein Freund – nur, was er freigegeben hat (null = privat). Gewicht und Kalorien gibt es hier nie. */
data class Friend(
    val code: String,
    val name: String,
    val badge: String?,
    /** Avatar-Schlüssel (immer sichtbar wie der Name) oder null. */
    val avatar: String? = null,
    val achievements: List<String>?,
    val streak: Int?,
    /** Längste Serie (zusammen mit der Serie freigegeben). */
    val best: Int? = null,
)

/** Offene Anfrage (eingehend oder ausgehend). */
data class FriendRequest(val code: String, val name: String)

data class SocialData(
    /** Eigener Freundescode (null = noch kein Profil beim Server). */
    val code: String?,
    val friends: List<Friend>,
    val incoming: List<FriendRequest>,
    val outgoing: List<FriendRequest>,
) {
    companion object {
        fun parse(json: String): SocialData? {
            val o = runCatching { lenientJson.parseToJsonElement(json).asObject() }.getOrNull() ?: return null
            fun requests(key: String) = (o[key] as? JsonArray).orEmpty().mapNotNull { e ->
                val r = e.asObject() ?: return@mapNotNull null
                FriendRequest(r.string("code") ?: return@mapNotNull null, r.string("name").orEmpty())
            }
            return SocialData(
                code = o.string("code"),
                friends = (o["friends"] as? JsonArray).orEmpty().mapNotNull { e ->
                    val f = e.asObject() ?: return@mapNotNull null
                    Friend(
                        code = f.string("code") ?: return@mapNotNull null,
                        name = f.string("name").orEmpty(),
                        badge = f.string("badge"),
                        avatar = f.string("avatar"),
                        achievements = (f["achievements"] as? JsonArray)?.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content },
                        streak = f.number("streak")?.toInt(),
                        best = f.number("best")?.toInt(),
                    )
                },
                incoming = requests("incoming"),
                outgoing = requests("outgoing"),
            )
        }
    }
}

/** Was die eigene Person teilt – jedes einzeln freigegeben, standardmäßig privat. */
data class SocialProfile(
    val name: String,
    val badge: String?,
    val avatar: String?,
    val streak: Int,
    /** Längste Serie (inkl. aus YAZIO übernommener). */
    val best: Int,
    val achievements: List<String>,
    val shareBadges: Boolean,
    val shareAchievements: Boolean,
    val shareStreak: Boolean,
)

/** Ergebnis einer Freundschaftsanfrage. */
enum class FriendRequestResult { SENT, ACCEPTED, UNKNOWN_CODE, SELF, ALREADY, TOO_MANY, NETWORK }

/** Freunde über den Worker (nur mit Konto). */
class SocialClient(
    private val baseUrl: String,
    /** Token eines echten Kontos (kein Gast) – sonst null. */
    private val token: suspend () -> String?,
) {
    private suspend fun headers(): Map<String, String>? =
        runCatching { token() }.getOrNull()?.let { mapOf("Authorization" to "Bearer $it") }

    suspend fun load(): SocialData? {
        val h = headers() ?: return null
        return runCatching { SocialData.parse(Http.get("$baseUrl/v1/social", h, timeoutMs = 15_000)) }.getOrNull()
    }

    suspend fun saveProfile(p: SocialProfile): SocialData? = post("/v1/social/profile", buildJsonObject {
        put("name", p.name)
        p.badge?.let { put("badge", it) }
        p.avatar?.let { put("avatar", it) }
        put("streak", p.streak)
        put("best", p.best)
        putJsonArray("achievements") { p.achievements.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } }
        putJsonObject("share") {
            put("badges", p.shareBadges)
            put("achievements", p.shareAchievements)
            put("streak", p.shareStreak)
        }
    })?.let(SocialData::parse)

    suspend fun request(code: String): Pair<FriendRequestResult, SocialData?> {
        val h = headers() ?: return FriendRequestResult.NETWORK to null
        return try {
            val body = Http.postJson("$baseUrl/v1/social/request", buildJsonObject { put("code", code) }.toString(), h, timeoutMs = 15_000)
            val result = runCatching { lenientJson.parseToJsonElement(body).asObject()?.string("result") }.getOrNull()
            (if (result == "accepted") FriendRequestResult.ACCEPTED else FriendRequestResult.SENT) to SocialData.parse(body)
        } catch (e: HttpStatusException) {
            requestError(e.body) to null
        } catch (e: java.io.IOException) {
            FriendRequestResult.NETWORK to null
        }
    }

    suspend fun respond(code: String, accept: Boolean): SocialData? =
        post("/v1/social/respond", buildJsonObject { put("code", code); put("accept", accept) })?.let(SocialData::parse)

    suspend fun remove(code: String): SocialData? = post("/v1/social/remove", buildJsonObject { put("code", code) })?.let(SocialData::parse)

    /** Beim Löschen des Kontos: Profil, Freunde und Anfragen beim Server entfernen. */
    suspend fun deleteAll() {
        post("/v1/social/delete", buildJsonObject { })
    }

    private suspend fun post(path: String, body: JsonObject): String? {
        val h = headers() ?: return null
        return runCatching { Http.postJson("$baseUrl$path", body.toString(), h, timeoutMs = 15_000) }.getOrNull()
    }

    internal companion object {
        fun requestError(body: String): FriendRequestResult =
            when (runCatching { lenientJson.parseToJsonElement(body).asObject()?.string("error") }.getOrNull()) {
                "unknown_code", "invalid_code" -> FriendRequestResult.UNKNOWN_CODE
                "self" -> FriendRequestResult.SELF
                "already_friends" -> FriendRequestResult.ALREADY
                "too_many_friends", "too_many_requests" -> FriendRequestResult.TOO_MANY
                else -> FriendRequestResult.NETWORK
            }
    }
}
