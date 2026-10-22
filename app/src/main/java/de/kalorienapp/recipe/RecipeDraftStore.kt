package de.kalorienapp.recipe

import de.kalorienapp.domain.model.RecipeDraft

/** Übergibt einen Import-Vorschlag vom Import-Screen an den Editor (nur im Speicher). */
class RecipeDraftStore {
    private var draft: RecipeDraft? = null

    fun put(value: RecipeDraft) {
        draft = value
    }

    fun take(): RecipeDraft? = draft.also { draft = null }
}
