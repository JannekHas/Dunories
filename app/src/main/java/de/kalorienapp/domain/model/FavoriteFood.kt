package de.kalorienapp.domain.model

/** Favorit: Lebensmittel mit Werten pro 100 g/ml und üblicher Portion. */
data class FavoriteFood(
    val id: Long = 0,
    val name: String,
    val amount: Double,
    val unit: AmountUnit,
    val per100: Nutrients,
)

/** Häufig gegessenes Lebensmittel (aus den bisherigen Einträgen abgeleitet). */
data class FrequentFood(
    val name: String,
    val amount: Double,
    val unit: AmountUnit,
    val per100: Nutrients,
    val timesEaten: Int,
)
