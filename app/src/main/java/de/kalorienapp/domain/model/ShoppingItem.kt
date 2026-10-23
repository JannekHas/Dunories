package de.kalorienapp.domain.model

/** Ein Eintrag auf der Einkaufsliste. [sources] = Rezepte, aus denen er stammt (leer = selbst hinzugefügt). */
data class ShoppingItem(
    val id: Long = 0,
    val name: String,
    val quantity: Double? = null,
    val unit: String = "",
    val checked: Boolean = false,
    val sources: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
)
