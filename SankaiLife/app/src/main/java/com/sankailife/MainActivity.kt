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
import com.sankailife.core.ads.AdsManager
import com.sankailife.core.ads.PrivacyConsentManager
import com.sankailife.core.haptics.AndroidHapticManager
import com.sankailife.core.haptics.LocalHaptics
import com.sankailife.ui.navigation.SankaiNavGraph
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.sankailife.ui.theme.SankaiTheme

class MainActivity : ComponentActivity() {

    private val demandeNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* refus = pas de mémos, l'app marche quand même */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        demanderPermissionNotifications()

        val app = application as SankaiApplication
        val haptics = AndroidHapticManager(this)

        setContent {
            val themeMode by app.preferences.themeMode.collectAsState(initial = "dark")
            val vibrations by app.preferences.vibrations.collectAsState(initial = true)

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
                        SankaiNavGraph()
                    }
                }
            }
        }

        // UMP met à jour le choix à chaque lancement et n'autorise AdMob
        // qu'une fois la collecte terminée (ou jugée non nécessaire).
        PrivacyConsentManager.recueillir(this)
    }

    override fun onResume() {
        super.onResume()
        // Garde une pub prête d'avance pour que le bouton soit instantané.
        AdsManager.preload(this)
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
}
