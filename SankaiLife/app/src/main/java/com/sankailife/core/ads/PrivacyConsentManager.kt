package com.sankailife.core.ads

import android.app.Activity
import android.util.Log
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Point d'entrée unique pour le consentement publicitaire UMP.
 *
 * AdMob n'est jamais initialisé avant que `canRequestAds()` ne l'autorise.
 * L'information est rafraîchie à chaque lancement, comme l'exige UMP, afin de
 * ne pas réutiliser une décision expirée stockée localement.
 */
object PrivacyConsentManager {
    private const val TAG = "SankaiConsent"

    private val _adsAutorisees = MutableStateFlow(false)
    val adsAutorisees: StateFlow<Boolean> = _adsAutorisees.asStateFlow()

    private val _optionsRequises = MutableStateFlow(false)
    val optionsRequises: StateFlow<Boolean> = _optionsRequises.asStateFlow()

    private var demandeEnCours = false
    private var adsDemarrees = false

    fun recueillir(activity: Activity) {
        if (demandeEnCours) return
        demandeEnCours = true

        val information = UserMessagingPlatform.getConsentInformation(activity)
        val parametres = ConsentRequestParameters.Builder().build()

        information.requestConsentInfoUpdate(
            activity,
            parametres,
            {
                actualiserEtat(information)
                demarrerAdsSiAutorisees(activity, information)
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { erreur ->
                    demandeEnCours = false
                    if (erreur != null) Log.w(TAG, "Formulaire UMP indisponible: ${erreur.message}")
                    actualiserEtat(information)
                    demarrerAdsSiAutorisees(activity, information)
                }
            },
            { erreur ->
                demandeEnCours = false
                Log.w(TAG, "Mise à jour UMP impossible: ${erreur.message}")
                // UMP peut conserver une décision valide d'une session
                // précédente ; `canRequestAds` reste la seule autorité.
                actualiserEtat(information)
                demarrerAdsSiAutorisees(activity, information)
            }
        )
    }

    fun afficherOptions(activity: Activity, apresFermeture: (() -> Unit)? = null) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { erreur ->
            if (erreur != null) Log.w(TAG, "Options UMP indisponibles: ${erreur.message}")
            val information = UserMessagingPlatform.getConsentInformation(activity)
            actualiserEtat(information)
            demarrerAdsSiAutorisees(activity, information)
            apresFermeture?.invoke()
        }
    }

    private fun actualiserEtat(information: ConsentInformation) {
        _adsAutorisees.value = information.canRequestAds()
        _optionsRequises.value =
            information.privacyOptionsRequirementStatus ==
                ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
    }

    private fun demarrerAdsSiAutorisees(
        activity: Activity,
        information: ConsentInformation
    ) {
        if (!information.canRequestAds() || adsDemarrees) return
        adsDemarrees = true
        AdsManager.initialize(activity.applicationContext)
        AdsManager.preload(activity)
    }
}
