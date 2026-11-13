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

/** Produktsuche per Barcode über die öffentliche Open-Food-Facts-API (kein Key nötig). */
class OpenFoodFactsClient {

    /** @return Vorschlag mit Nährwerten pro 100 g/ml, oder null wenn das Produkt unbekannt ist. */
    suspend fun lookup(barcode: String): FoodDraft? {
        val url = "https://world.openfoodfacts.org/api/v2/product/$barcode.json?fields=$FIELDS"
        val body = try {
            Http.get(url)
        } catch (e: HttpStatusException) {
            if (e.code == 404) return null else throw e
        }
        return OpenFoodFactsParser.parse(body)
    }

    private companion object {
        const val FIELDS = "product_name,product_name_de,brands,nutriments," +
            "product_quantity_unit,serving_quantity,nutrition_data_per,categories_tags"
    }
}

internal object OpenFoodFactsParser {
    private const val KJ_PER_KCAL = 4.184

    fun parse(body: String): FoodDraft? {
        val root = runCatching { lenientJson.parseToJsonElement(body).asObject() }.getOrNull() ?: return null
        if (root.number("status") != 1.0) return null
        val product = root["product"].asObject() ?: return null
        val n = product["nutriments"].asObject() ?: JsonObject(emptyMap())

        val kcal = n.number("energy-kcal_100g")
            ?: n.number("energy-kj_100g")?.div(KJ_PER_KCAL)
            ?: n.number("energy_100g")?.div(KJ_PER_KCAL)
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
                fatG = n.number("fat_100g") ?: 0.0,
                saturatedFatG = n.number("saturated-fat_100g"),
                carbsG = n.number("carbohydrates_100g") ?: 0.0,
                sugarG = n.number("sugars_100g"),
                fiberG = n.number("fiber_100g"),
                proteinG = n.number("proteins_100g") ?: 0.0,
                saltG = n.number("salt_100g"),
                micros = MicroFields.readOpenFoodFacts(n, categories),
            ),
            source = EntrySource.BARCODE,
        )
    }
}
