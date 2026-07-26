package com.sankailife.core.notifications

/**
 * Plage d'heures silencieuses, exprimée en minutes depuis minuit.
 *
 * Android ne permet pas à une application ordinaire de « s'éteindre » puis de
 * se relancer seule. Les heures silencieuses ne coupent donc pas l'app : elles
 * empêchent les notifications et les vibrations de rappel de partir, et les
 * planifications reprennent d'elles-mêmes après la plage.
 */
data class QuietHours(
    val enabled: Boolean = false,
    val startMinute: Int = 23 * 60,
    val endMinute: Int = 8 * 60
) {

    /**
     * La plage traverse-t-elle minuit ? 23h00 → 08h00 est le cas normal, et
     * c'est justement celui qu'on oublie de gérer.
     */
    val traverseMinuit: Boolean get() = startMinute > endMinute

    /** true si [minuteDuJour] tombe dans la plage silencieuse. */
    fun contient(minuteDuJour: Int): Boolean {
        if (!enabled) return false
        if (startMinute == endMinute) return false
        return if (traverseMinuit) {
            minuteDuJour >= startMinute || minuteDuJour < endMinute
        } else {
            minuteDuJour in startMinute until endMinute
        }
    }

    companion object {
        val DESACTIVE = QuietHours(enabled = false)

        fun formater(minutes: Int): String =
            "%02dh%02d".format((minutes / 60) % 24, minutes % 60)
    }
}
