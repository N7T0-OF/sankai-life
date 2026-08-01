package com.sankailife.ui.screens.settings

import android.app.LocaleManager
import android.os.Build
import android.os.LocaleList
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.annotation.RequiresApi
import com.sankailife.R
import com.sankailife.ui.theme.sankaiColors

/**
 * Choix de la langue.
 *
 * **L'essentiel marche sans ce réglage.** L'application suit la langue du
 * téléphone toute seule : Android choisit `values-en` ou `values-pt` d'après
 * les paramètres du système, sans une ligne de code. C'est vrai sur toutes les
 * versions d'Android supportées.
 *
 * Ce réglage ne sert qu'à forcer une langue différente de celle du téléphone.
 * Il s'appuie sur `LocaleManager`, disponible à partir d'Android 13. La voie
 * rétroportée d'AppCompat couvrirait aussi les versions antérieures, mais elle
 * impose de faire hériter l'activité d'`AppCompatActivity` et d'adopter un
 * thème AppCompat — un thème incompatible fait planter l'application au
 * lancement. Ce n'est pas un risque à prendre pour un réglage secondaire que
 * les Paramètres Android proposent déjà eux-mêmes.
 */
@Composable
fun LangueSection() {
    val c = MaterialTheme.sankaiColors
    val contexte = LocalContext.current

    val supporte = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    // La langue courante est relue du système : elle peut avoir été changée
    // depuis les Paramètres Android, hors de l'application.
    //
    // La garde de version est écrite en toutes lettres à chaque appel plutôt
    // que déduite d'un objet nul : l'analyse statique ne sait pas suivre une
    // nullité conditionnelle, et elle a raison de s'en méfier — un jour
    // quelqu'un supprimera la condition qui garantit ce null.
    var choix by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                langueForcee(contexte)
            } else null
        )
    }

    Column(Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.settings_language),
            color = c.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.settings_language_hint),
            color = c.textSecondary, fontSize = 11.sp
        )
        Spacer(Modifier.height(12.dp))

        if (!supporte) {
            // Sur Android 12 et antérieur, l'application suit le système et
            // rien d'autre. Le dire vaut mieux qu'afficher des boutons inertes.
            Text(
                "Ton téléphone suit automatiquement sa propre langue. " +
                    "Le choix manuel demande Android 13 ou plus récent.",
                color = c.textDisabled, fontSize = 11.sp
            )
            return@Column
        }

        listOf(
            null to stringResource(R.string.settings_language_auto),
            "fr" to "Français",
            "en" to "English",
            "pt" to "Português"
        ).forEach { (code, libelle) ->
            val actif = choix == code
            Row(
                Modifier.fillMaxWidth().padding(bottom = 6.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (actif) c.accent.copy(alpha = 0.15f) else c.surface2)
                    .border(
                        if (actif) 1.5.dp else 1.dp,
                        if (actif) c.accent else c.border,
                        RoundedCornerShape(10.dp)
                    )
                    .clickable {
                        choix = code
                        // Une liste vide signifie « suis le système ». C'est le
                        // seul moyen de revenir en arrière : y écrire la langue
                        // actuelle figerait l'application sur elle, même si le
                        // téléphone change de langue plus tard.
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            forcerLangue(contexte, code)
                        }
                    }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (actif) "◉" else "○",
                    fontSize = 14.sp,
                    color = if (actif) c.accent else c.textDisabled
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    libelle,
                    color = if (actif) c.accent else c.textPrimary,
                    fontSize = 13.sp
                )
            }
        }
    }
}

/** Langue forcée par l'utilisateur, ou null s'il suit le système. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun langueForcee(contexte: android.content.Context): String? =
    contexte.getSystemService(LocaleManager::class.java)
        ?.applicationLocales
        ?.takeIf { !it.isEmpty }
        ?.get(0)
        ?.language

/**
 * Force une langue, ou rend la main au système si [code] est null.
 *
 * Une liste vide signifie « suis le système ». C'est le seul moyen de revenir
 * en arrière : y écrire la langue actuelle figerait l'application sur elle,
 * même si le téléphone change de langue plus tard.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun forcerLangue(contexte: android.content.Context, code: String?) {
    contexte.getSystemService(LocaleManager::class.java)?.applicationLocales =
        if (code == null) LocaleList.getEmptyLocaleList()
        else LocaleList.forLanguageTags(code)
}
