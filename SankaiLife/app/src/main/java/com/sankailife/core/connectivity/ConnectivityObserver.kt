package com.sankailife.core.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Observe l'état de la connexion.
 *
 * Sankai Life fonctionne à 100 % hors ligne : cet observateur ne sert **qu'à**
 * griser les rares boutons qui ont besoin du réseau (pubs, lien Ko-fi, site).
 * Aucune donnée de jeu ne dépend de la connexion.
 */
class ConnectivityObserver(context: Context) {

    private val manager = context.applicationContext
        .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    /** Émet true dès qu'un réseau réellement utilisable est disponible. */
    val isOnline: Flow<Boolean> = callbackFlow {
        trySend(currentlyOnline())

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { trySend(true) }
            override fun onLost(network: Network) { trySend(currentlyOnline()) }
            override fun onUnavailable() { trySend(false) }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                trySend(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
            }
        }

        // Si le service réseau est indisponible on considère l'app hors ligne
        // plutôt que de faire planter l'écran : le mode offline reste complet.
        val enregistre = runCatching {
            manager.registerDefaultNetworkCallback(callback)
        }.isSuccess

        awaitClose {
            if (enregistre) runCatching { manager.unregisterNetworkCallback(callback) }
        }
    }.distinctUntilChanged()

    /** Lecture ponctuelle de l'état réseau. */
    fun currentlyOnline(): Boolean = runCatching {
        val reseau = manager.activeNetwork ?: return false
        val caps = manager.getNetworkCapabilities(reseau) ?: return false
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }.getOrDefault(false)
}
