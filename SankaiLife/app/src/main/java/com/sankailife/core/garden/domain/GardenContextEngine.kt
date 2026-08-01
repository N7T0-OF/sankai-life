package com.sankailife.core.garden.domain

/** Décide l'action courte proposée après avoir touché une parcelle. */
object GardenContextEngine {

    enum class Action { DETAILS, HARVEST, WATER }

    /**
     * La récolte est prioritaire : une plante mûre ne doit jamais proposer un
     * arrosage. L'eau n'est proposée que si elle peut réellement être dépensée.
     */
    fun action(
        cultivable: Boolean,
        ready: Boolean,
        needsWater: Boolean,
        waterAvailable: Boolean
    ): Action = when {
        cultivable && ready -> Action.HARVEST
        cultivable && needsWater && waterAvailable -> Action.WATER
        else -> Action.DETAILS
    }
}
