package com.sankailife.core.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.sankailife.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Raison pour laquelle une pub n'a pas pu être affichée.
 * Chaque valeur porte un message prêt à afficher à l'utilisateur.
 */
enum class AdUnavailableReason(val message: String) {
    NO_NETWORK("Connexion internet requise"),
    NOT_LOADED("Pub en cours de chargement…"),
    COOLDOWN("Patiente un peu avant la prochaine pub"),
    DAILY_LIMIT("Limite de pubs atteinte pour aujourd'hui"),
    ERROR("La pub n'a pas pu être chargée")
}

/** Résultat d'une tentative d'affichage de pub récompensée. */
sealed interface AdResult {
    /** L'utilisateur a regardé la pub jusqu'au bout : la récompense est due. */
    data object Rewarded : AdResult

    /** La pub s'est ouverte mais a été fermée trop tôt : aucune récompense. */
    data object Dismissed : AdResult

    /** Aucune pub n'a pu être affichée. L'app doit continuer normalement. */
    data class Unavailable(val reason: AdUnavailableReason) : AdResult
}

/**
 * Gestion des pubs récompensées AdMob.
 *
 * Règle non négociable de Sankai Life : **la pub est toujours un bonus, jamais
 * une condition**. Chaque appel peut échouer silencieusement et l'app doit
 * rester entièrement utilisable — hors ligne, en avion, ou si AdMob est down.
 * Aucune fonctionnalité ne doit être placée derrière [showRewarded].
 *
 * Les identifiants viennent de `admob.properties` via BuildConfig ; sans ce
 * fichier ce sont les identifiants de TEST officiels de Google qui servent.
 */
object AdsManager {

    private const val TAG = "SankaiAds"

    /** Délai minimum entre deux pubs, aligné sur EconomyEngine.AD_COOLDOWN_SEC. */
    const val COOLDOWN_SECONDS = 25

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var initialised = false
    private var loadingAd = false
    private var rewardedAd: RewardedAd? = null
    private var lastRewardAtMillis = 0L

    private val _adReady = MutableStateFlow(false)

    /** true quand une pub est chargée et prête à être montrée instantanément. */
    val adReady: StateFlow<Boolean> = _adReady.asStateFlow()

    /** true si le build utilise de vrais identifiants AdMob (pas ceux de test). */
    val usesRealAdUnits: Boolean get() = BuildConfig.ADMOB_IS_REAL

    /**
     * Initialise le SDK AdMob. Appelé une fois au démarrage de l'app.
     * L'initialisation part sur un thread d'arrière-plan : elle fait des I/O
     * disque et réseau et bloquerait le premier affichage sinon.
     */
    fun initialize(context: Context) {
        if (initialised) return
        initialised = true
        val appContext = context.applicationContext
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { MobileAds.initialize(appContext) {} }.isSuccess
            }
            if (ok) preload(appContext) else Log.w(TAG, "SDK AdMob indisponible, mode sans pub")
        }
    }

    /**
     * Précharge une pub en tâche de fond pour que le bouton « Regarder » soit
     * instantané. Sans réseau l'appel échoue en silence, ce qui est voulu.
     */
    fun preload(context: Context) {
        if (loadingAd || rewardedAd != null || !initialised) return
        loadingAd = true
        val request = AdRequest.Builder().build()
        RewardedAd.load(
            context.applicationContext,
            BuildConfig.ADMOB_REWARDED_UNIT_ID,
            request,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                    loadingAd = false
                    _adReady.value = true
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewardedAd = null
                    loadingAd = false
                    _adReady.value = false
                    Log.d(TAG, "Chargement pub impossible : ${error.message}")
                }
            }
        )
    }

    /** Secondes restantes avant de pouvoir relancer une pub (0 si dispo). */
    fun cooldownRemainingSeconds(): Int {
        if (lastRewardAtMillis == 0L) return 0
        val ecoule = (System.currentTimeMillis() - lastRewardAtMillis) / 1000
        return (COOLDOWN_SECONDS - ecoule).coerceAtLeast(0).toInt()
    }

    /**
     * Affiche une pub récompensée et suspend jusqu'à sa fermeture.
     *
     * Ne lève jamais d'exception : tout échec devient [AdResult.Unavailable],
     * charge à l'appelant de continuer sans récompense.
     *
     * @param estEnLigne état réseau observé, pour renvoyer un message clair
     *                   plutôt qu'un échec de chargement générique.
     */
    suspend fun showRewarded(
        activity: Activity,
        estEnLigne: Boolean = true
    ): AdResult = withContext(Dispatchers.Main) {

        if (!estEnLigne) return@withContext AdResult.Unavailable(AdUnavailableReason.NO_NETWORK)

        val restant = cooldownRemainingSeconds()
        if (restant > 0) return@withContext AdResult.Unavailable(AdUnavailableReason.COOLDOWN)

        val ad = rewardedAd
        if (ad == null) {
            preload(activity)
            return@withContext AdResult.Unavailable(AdUnavailableReason.NOT_LOADED)
        }

        // Une instance RewardedAd ne peut servir qu'une fois.
        rewardedAd = null
        _adReady.value = false

        var recompenseObtenue = false

        // Type explicite : sans lui Kotlin déduit le type du premier `resume`
        // rencontré et refuse les autres branches de AdResult.
        suspendCancellableCoroutine<AdResult> { continuation ->
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    preload(activity)
                    if (continuation.isActive) {
                        continuation.resume(
                            if (recompenseObtenue) AdResult.Rewarded else AdResult.Dismissed
                        )
                    }
                }

                override fun onAdFailedToShowFullScreenContent(error: AdError) {
                    Log.d(TAG, "Affichage pub impossible : ${error.message}")
                    preload(activity)
                    if (continuation.isActive) {
                        continuation.resume(AdResult.Unavailable(AdUnavailableReason.ERROR))
                    }
                }
            }

            ad.show(activity) {
                recompenseObtenue = true
                lastRewardAtMillis = System.currentTimeMillis()
            }
        }
    }
}
