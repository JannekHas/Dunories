package de.kalorienapp.data.remote.openfoodfacts

import de.kalorienapp.data.remote.Http
import de.kalorienapp.data.remote.MicroFields
import de.kalorienapp.data.remote.HttpStatusException
import de.kalorienapp.data.remote.asObject
import de.kalorienapp.data.remote.lenientJson
import de.kalorienapp.data.remote.number
import de.kalorienapp.data.remote.string
import de.kalorienapp.domain.model.AmountUnit
import de.kalorienapp.domain.model.EntrySource
import de.kalorienapp.domain.model.FoodDraft
import de.kalorienapp.domain.model.Nutrients
import kotlinx.serialization.json.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Produktsuche per Barcode über die öffentliche Open-Food-Facts-API (kein Key nötig).
 * Gefundene Produkte werden [CACHE_DAYS] Tage in [cacheDir] gemerkt: der zweite Scan ist sofort da und
 * klappt auch ohne Netz (Nov. 2026).
 */
class OpenFoodFactsClient(private val cacheDir: File? = null) {

    /** @return Vorschlag mit Nährwerten pro 100 g/ml, oder null wenn das Produkt unbekannt ist. */
    suspend fun lookup(barcode: String): FoodDraft? = withContext(Dispatchers.IO) { lookupBlocking(barcode) }

    private suspend fun lookupBlocking(barcode: String): FoodDraft? {
        // Nur echte Strichcodes (EAN/UPC) – nie beliebigen Text in die URL bzw. den Dateinamen.
        if (!BARCODE.matches(barcode)) return null
        val cached = cacheFile(barcode)
        val fresh = cached?.takeIf {
            it.exists() && System.currentTimeMillis() - it.lastModified() < CACHE_DAYS * DAY_MS
        }
        fresh?.let { file -> runCatching { OpenFoodFactsParser.parse(file.readText()) }.getOrNull()?.let { return it } }

        val url = "https://world.openfoodfacts.org/api/v2/product/$barcode.json?fields=$FIELDS"
        val body = try {
            Http.get(url)
        } catch (e: HttpStatusException) {
            if (e.code == 404) return null else throw e
        } catch (e: IOException) {
            // Kein Netz: notfalls ein älterer gemerkter Stand
            cached?.takeIf { it.exists() }?.let { file ->
                runCatching { OpenFoodFactsParser.parse(file.readText()) }.getOrNull()?.let { return it }
            }
            throw e
        }
        val draft = OpenFoodFactsParser.parse(body)
        if (draft != null && cached != null) {
            runCatching {
                cached.parentFile?.mkdirs()
                cached.writeText(body)
            }
        }
        return draft
    }

    private fun cacheFile(barcode: String): File? = cacheDir?.let { File(File(it, "off"), "$barcode.json") }

    private companion object {
        const val FIELDS = "product_name,product_name_de,brands,nutriments," +
            "product_quantity_unit,serving_quantity,nutrition_data_per,categories_tags"
        val BARCODE = Regex("[0-9]{6,14}")
        const val CACHE_DAYS = 30L
        const val DAY_MS = 24L * 60 * 60 * 1000
    }
}

internal object OpenFoodFactsParser {
    private const val KJ_PER_KCAL = 4.184

    fun parse(body: String): FoodDraft? {
        val root = runCatching { lenientJson.parseToJsonElement(body).asObject() }.getOrNull() ?: return null
        if (root.number("status") != 1.0) return null
        val product = root["product"].asObject() ?: return null
        val n = product["nutriments"].asObject() ?: JsonObject(emptyMap())

        // Fehlerhafte Datensätze in OFF können negative Werte haben → nie unter 0.
        fun JsonObject.amount(key: String) = number(key)?.coerceAtLeast(0.0)
        val kcal = n.amount("energy-kcal_100g")
            ?: n.amount("energy-kj_100g")?.div(KJ_PER_KCAL)
            ?: n.amount("energy_100g")?.div(KJ_PER_KCAL)
        val name = product.string("product_name_de") ?: product.string("product_name")
        // Ohne Namen und ohne Energie ist der Datensatz unbrauchbar.
        if (name == null && kcal == null) return null

        val brand = product.string("brands")?.split(',')?.firstOrNull()?.trim()
        val displayName = when {
            // Leerer Name → der Nutzer trägt ihn im Vorschlag selbst ein.
            name == null -> brand.orEmpty()
            brand != null && !name.contains(brand, ignoreCase = true) -> "$name ($brand)"
            else -> name
        }
        // Kategorien für die Koffein-Schätzung (Wert fehlt bei Kaffee/Cola/Energy Drinks fast immer)
        val categories = (product["categories_tags"] as? kotlinx.serialization.json.JsonArray).orEmpty()
            .mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
        val liquid = product.string("product_quantity_unit")?.lowercase() == "ml" ||
            product.string("nutrition_data_per")?.lowercase() == "100ml"

        return FoodDraft(
            name = displayName,
            amount = product.number("serving_quantity")?.takeIf { it > 0 },
            unit = if (liquid) AmountUnit.MILLILITER else AmountUnit.GRAM,
            per100 = Nutrients(
                kcal = kcal ?: 0.0,
                fatG = n.amount("fat_100g") ?: 0.0,
                saturatedFatG = n.amount("saturated-fat_100g"),
                carbsG = n.amount("carbohydrates_100g") ?: 0.0,
                sugarG = n.amount("sugars_100g"),
                fiberG = n.amount("fiber_100g"),
                proteinG = n.amount("proteins_100g") ?: 0.0,
                saltG = n.amount("salt_100g"),
                micros = MicroFields.readOpenFoodFacts(n, categories),
            ),
            source = EntrySource.BARCODE,
        )
    }
}
