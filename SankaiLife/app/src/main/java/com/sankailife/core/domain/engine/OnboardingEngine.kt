package com.sankailife.core.domain.engine

/** Navigation pure du tutoriel court. Les textes restent dans les ressources. */
object OnboardingEngine {

    enum class Topic {
        WELCOME,
        LEARNING,
        CULTURE,
        INTENTIONAL_USE,
        DAILY_TIME
    }

    val pages: List<Topic> = Topic.entries
    val derniere: Int get() = pages.lastIndex

    /** Ramene toute valeur restauree ou fournie par l'UI dans le parcours valide. */
    fun borner(index: Int): Int = index.coerceIn(0, derniere)

    fun estDerniere(index: Int): Boolean = borner(index) == derniere

    fun suivante(index: Int): Int = when {
        // Tester avant l'addition evite le debordement de Int.MAX_VALUE.
        index >= derniere -> derniere
        else -> (index + 1).coerceIn(0, derniere)
    }

    fun precedente(index: Int): Int = when {
        // Tester avant la soustraction evite le debordement de Int.MIN_VALUE.
        index <= 0 -> 0
        else -> (index - 1).coerceIn(0, derniere)
    }
}
