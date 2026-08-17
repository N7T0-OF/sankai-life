package com.sankailife.core.concentration

/**
 * La source Concentration : le minuteur du téléphone fait l'action, Sankai
 * transforme sa fin en progression symbolique.
 *
 * Sankai ne recrée pas de minuteur — il s'y connecte. La seule fenêtre
 * honnête est la notification « minuteur terminé » que l'application Horloge
 * affiche : Sankai la reconnaît, crédite une fois, plafonné et dégressif par
 * le moteur anti-farm, et ne lit jamais rien d'autre.
 */
object ConcentrationIntegration {

    /**
     * Les paquets des applications Horloge connues, par constructeur.
     *
     * Le service d'écoute n'est actif qu'après l'accord explicite de
     * l'utilisateur ; cette liste ne sert qu'à ne regarder que des Horloges
     * et ignorer le reste des notifications de l'appareil.
     */
    val PAQUETS_HORLOGES = setOf(
        "com.google.android.deskclock",       // Horloge Google / Pixel / AOSP
        "com.android.deskclock",              // AOSP et ses forks (Xiaomi…)
        "com.sec.android.app.clockpackage",   // Samsung
        "com.coloros.alarmclock",             // Oppo / OnePlus (ColorOS)
        "com.oplus.alarmclock",               // Oppo récent
        "com.hihonor.android.alarmclock",     // Honor
        "com.vivo.appclock",                  // Vivo
        "com.transsion.alarmclock"            // Infinix / Tecno
    )

    /** Une fin de minuteur, réduite à ce dont Sankai a besoin. */
    data class MinuteurFini(val cle: String)

    /**
     * La décision : une notification est-elle la fin d'un minuteur ?
     *
     * Trois signaux, tous nécessaires :
     * - l'application est une Horloge connue ;
     * - le canal est celui des minuteurs (un identifiant stable, jamais
     *   localisé — le libellé affiché, lui, change de langue) ;
     * - la notification n'est pas persistante : un minuteur en marche l'est,
     *   sa fin ne l'est plus.
     *
     * Un réveil, un chronomètre, un message ou n'importe quelle autre
     * notification ne passent pas ce filtre : Sankai ne lit rien d'autre.
     */
    fun estMinuteurFini(
        paquet: String,
        canal: String?,
        ongoing: Boolean
    ): Boolean {
        if (paquet !in PAQUETS_HORLOGES) return false
        if (ongoing) return false
        val idCanal = canal?.lowercase().orEmpty()
        return idCanal.contains("timer")
    }

    /**
     * Les fins de minuteur pas encore créditées aujourd'hui, dédupliquées.
     *
     * La clé d'une notification est stable pour une même fin : la répéter
     * (même notification relue, ou notification toujours présente au
     * démarrage) ne crédite pas deux fois la même chose.
     */
    fun aCrediter(dejaCredites: Set<String>, minutes: List<MinuteurFini>): List<MinuteurFini> =
        minutes.asSequence()
            .filter { it.cle !in dejaCredites }
            .distinctBy { it.cle }
            .toList()
}
