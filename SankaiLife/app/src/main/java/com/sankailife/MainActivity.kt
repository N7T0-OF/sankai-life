package com.sankailife

import android.Manifest
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.sankailife.core.haptics.AndroidHapticManager
import com.sankailife.core.haptics.LocalHaptics
import com.sankailife.ui.navigation.SankaiNavGraph
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.sankailife.ui.theme.SankaiTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    private val navigationRequest = MutableStateFlow<String?>(null)

    private val demandeNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* refus = pas de mémos, l'app marche quand même */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // Barres systeme reellement transparentes.
        //
        // `enableEdgeToEdge()` sans argument pose un voile clair ou sombre
        // derriere les barres pour garantir le contraste des icones systeme.
        // Sous la capsule de navigation, ce voile se lit comme un bandeau — et
        // avec la navigation a trois boutons, comme un calque portant le
        // triangle de retour. Le fond de la page passe desormais dessous, ce
        // qui est le comportement attendu d'une barre flottante.
        //
        // Le contraste des icones reste gere par le systeme via
        // `isAppearanceLightNavigationBars`, pilote plus bas par le theme.
        enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = androidx.activity.SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        )

        demanderPermissionNotifications()

        val app = application as SankaiApplication
        val haptics = AndroidHapticManager(this)

        lirePartage(intent)
        lireDestination(intent)

        setContent {
            val themeMode by app.preferences.themeMode.collectAsState(initial = "auto")
            val vibrations by app.preferences.vibrations.collectAsState(initial = true)
            val requestedRoute by navigationRequest.collectAsState()

            // Le réglage est relu à chaque changement : couper les vibrations
            // prend effet immédiatement, sans relancer l'application.
            LaunchedEffect(vibrations) { haptics.enabled = vibrations }

            // Relu en continu : basculer les couleurs du telephone prend effet
            // immediatement, sans relancer l'application ni perdre l'ecran en
            // cours.
            val couleursSysteme by app.preferences.couleursSysteme.collectAsState(initial = true)

            // Thème cosmétique équipé, relu en continu comme le reste : équiper
            // un thème dans la personnalisation doit se voir immédiatement, pas
            // au prochain lancement.
            val utilisateur by app.database.userDao().getUser()
                .collectAsState(initial = null)
            val accentTheme = remember(utilisateur?.equippedThemeId) {
                com.sankailife.core.domain.model.ALL_THEMES
                    .firstOrNull { it.id == utilisateur?.equippedThemeId }
                    ?.let { com.sankailife.ui.theme.Contraste.depuisHex(it.accentHex) }
            }

            SankaiTheme(
                darkTheme = when (themeMode) {
                    "light" -> false
                    "auto" -> isSystemDarkTheme()
                    else -> true
                },
                couleursSysteme = couleursSysteme,
                amoled = themeMode == "amoled",
                accentTheme = accentTheme
            ) {
                CompositionLocalProvider(LocalHaptics provides haptics) {
                    // Le fond de l'application, peint une fois et une seule.
                    // Chaque ecran qui repeignait le sien produisait des
                    // raccords visibles des que la palette changeait.
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        SankaiNavGraph(
                            externalRoute = requestedRoute,
                            onExternalRouteConsumed = { navigationRequest.value = null }
                        )
                    }
                }
            }
        }

    }

    /**
     * Android 13+ demande la permission d'envoyer des notifications.
     * Un refus ne bloque rien : seuls les rappels mémo sont perdus.
     */
    private fun demanderPermissionNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            demandeNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun isSystemDarkTheme(): Boolean {
        val uiMode = resources.configuration.uiMode
        return (uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        // L'application deja ouverte recoit aussi les partages : sans cela, il
        // faudrait la fermer avant de pouvoir importer quoi que ce soit.
        setIntent(intent)
        lirePartage(intent)
        lireDestination(intent)
    }

    /**
     * Recupere ce qui a ete partage vers l'application.
     *
     * Rien n'est ecrit en base ici : on depose le contenu, l'ecran d'import
     * l'affichera et demandera confirmation. Installer sans montrer serait
     * accepter n'importe quoi de n'importe qui.
     */
    private fun lirePartage(intent: android.content.Intent?) {
        if (intent == null) return
        val action = intent.action
        if (action != android.content.Intent.ACTION_SEND &&
            action != android.content.Intent.ACTION_VIEW
        ) return

        val flux: android.net.Uri? = when {
            action == android.content.Intent.ACTION_VIEW -> intent.data
            android.os.Build.VERSION.SDK_INT >= 33 -> intent.getParcelableExtra(
                android.content.Intent.EXTRA_STREAM, android.net.Uri::class.java
            )
            else -> @Suppress("DEPRECATION")
            intent.getParcelableExtra(android.content.Intent.EXTRA_STREAM)
        }

        if (flux != null) {
            com.sankailife.core.modules.PartageEntrant.deposer(
                com.sankailife.core.modules.PartageEntrant.Contenu.Fichier(
                    uri = flux,
                    nom = nomDuFichier(flux)
                )
            )
            return
        }

        val texte = intent.getStringExtra(android.content.Intent.EXTRA_TEXT)
        if (!texte.isNullOrBlank()) {
            com.sankailife.core.modules.PartageEntrant.deposer(
                com.sankailife.core.modules.PartageEntrant.Contenu.Texte(
                    valeur = texte,
                    titre = intent.getStringExtra(android.content.Intent.EXTRA_SUBJECT).orEmpty()
                )
            )
        }
    }

    private fun lireDestination(intent: android.content.Intent?) {
        if (intent == null) return

        // Un rappel mémo ouvre le module précis, jamais la bibliothèque
        // entière. Le module peut avoir été supprimé entre-temps : l'écran de
        // session le dit et propose d'importer à nouveau, au lieu de planter
        // sur une référence orpheline.
        val profileId = intent.getLongExtra(
            com.sankailife.core.notifications.SankaiNotifications.EXTRA_MEMO_PROFILE_ID,
            -1L
        )
        if (profileId > 0L) {
            navigationRequest.value = "flashcards/$profileId"
            // Une rotation ne doit pas rejouer une navigation déjà consommée.
            intent.removeExtra(
                com.sankailife.core.notifications.SankaiNotifications.EXTRA_MEMO_PROFILE_ID
            )
            intent.removeExtra(
                com.sankailife.core.notifications.SankaiNotifications.EXTRA_DESTINATION
            )
            return
        }

        val route = intent.getStringExtra(
            com.sankailife.core.notifications.SankaiNotifications.EXTRA_DESTINATION
        ) ?: return
        if (route in setOf(
                "memo", "capsules", "academy",
                // Route de la révision libre, utilisée par le widget.
                "flashcards/${com.sankailife.ui.screens.life.flashcards.FlashcardsViewModel.PROFIL_ERREURS}"
            )
        ) {
            navigationRequest.value = route
            // Une rotation ne doit pas rejouer une navigation déjà consommée.
            intent.removeExtra(
                com.sankailife.core.notifications.SankaiNotifications.EXTRA_DESTINATION
            )
        }
    }

    /** Nom lisible d'un fichier recu, ou vide si le fournisseur n'en donne pas. */
    private fun nomDuFichier(uri: android.net.Uri): String = runCatching {
        contentResolver.query(uri, null, null, null, null)?.use { curseur ->
            val colonne = curseur.getColumnIndex(
                android.provider.OpenableColumns.DISPLAY_NAME
            )
            if (colonne >= 0 && curseur.moveToFirst()) curseur.getString(colonne) else ""
        }.orEmpty()
    }.getOrDefault("")
}
