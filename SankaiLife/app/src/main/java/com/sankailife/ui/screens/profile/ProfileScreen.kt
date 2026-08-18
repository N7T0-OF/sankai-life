package com.sankailife.ui.screens.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sankailife.R
import com.sankailife.SankaiApplication
import com.sankailife.core.data.sauvegarde.SauvegardeRepository
import com.sankailife.ui.navigation.Screen
import com.sankailife.ui.theme.DangerRed
import com.sankailife.ui.theme.ProfileAvatar
import com.sankailife.ui.theme.SuccessGreen
import com.sankailife.ui.theme.sankaiColors
import java.text.NumberFormat
import kotlinx.coroutines.launch

/**
 * Profil, structure de la maquette « Swann » : avatar, niveau, trois cartes
 * de progression et un menu court.
 *
 * La structure vient de la maquette ; les couleurs suivent le thème comme
 * tous les autres écrans — le profil partage le fond de l'application.
 */
@Composable
fun ProfileScreen(viewModel: ProfileViewModel, onNavigate: (String) -> Unit) {
    val c = MaterialTheme.sankaiColors
    val user by viewModel.user.collectAsStateWithLifecycle()
    val langueProgression by viewModel.languePrincipale.collectAsStateWithLifecycle()
    val memorisation by viewModel.memorisation.collectAsStateWithLifecycle()
    val progressionReelle by viewModel.progressionReelle.collectAsStateWithLifecycle()

    // Le pourcentage de Culture : la même borne douce que la progression
    // réelle (14 découvertes) — une carte dit combien du chemin est parcouru.
    val culturePct = progressionReelle
        .firstOrNull { it.libelle == R.string.progression_culture }
        ?.let { (it.progression * 100).toInt().coerceIn(0, 100) }
    val memosPct = if (memorisation.total > 0) {
        (memorisation.maitrisees * 100 / memorisation.total).coerceIn(0, 100)
    } else 0

    // Export direct depuis le profil : le sélecteur Android choisit la
    // destination, l'application n'écrit jamais où elle veut. Le format est le
    // même que dans Paramètres — ZIP contenant du JSON, lisible après migration.
    val contexte = LocalContext.current
    val app = contexte.applicationContext as SankaiApplication
    val portee = rememberCoroutineScope()
    val depot = remember { SauvegardeRepository(contexte, app.database) }
    var messageExport by remember { mutableStateOf<String?>(null) }
    var exportReussi by remember { mutableStateOf(false) }

    val creerExport = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        portee.launch {
            runCatching { depot.exporter(uri) }
                .onSuccess { octets ->
                    exportReussi = true
                    messageExport = contexte.getString(
                        R.string.profile_export_done, octets / 1024
                    )
                }
                .onFailure {
                    exportReussi = false
                    messageExport = contexte.getString(
                        R.string.settings_backup_failed, it.message
                    )
                }
        }
    }

    // Un message qui s'efface seul : le confirmer, puis rendre la main à la vie.
    LaunchedEffect(messageExport) {
        if (messageExport != null) {
            kotlinx.coroutines.delay(5_000)
            messageExport = null
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(c.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 20.dp)
    ) {
        // ── En-tête : avatar, prénom, niveau ─────────────────────────────
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(96.dp)
                    .shadow(16.dp, CircleShape, clip = false)
                    .clip(CircleShape)
                    .background(ProfileAvatar),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    user.pseudo.take(1).uppercase(),
                    color = Color.White,
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                user.pseudo,
                color = c.textPrimary,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(
                    R.string.profile_level_xp,
                    user.level,
                    NumberFormat.getIntegerInstance().format(user.xp)
                ),
                color = c.textSecondary,
                fontSize = 14.sp
            )
        }

        Spacer(Modifier.height(18.dp))

        // ── Trois cartes de progression ─────────────────────────────────
        val langue = langueProgression
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (langue != null) {
                CarteMetrique("${langue.pourcentage}%", langue.libelle)
            }
            if (culturePct != null) {
                CarteMetrique(
                    "$culturePct%",
                    stringResource(R.string.profile_metric_culture)
                )
            }
            CarteMetrique("$memosPct%", stringResource(R.string.profile_metric_memos))
        }

        Spacer(Modifier.height(22.dp))

        // ── Menu ─────────────────────────────────────────────────────────
        Text(
            stringResource(R.string.profile_menu_title).uppercase(),
            color = c.textSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.2.sp,
            modifier = Modifier.padding(bottom = 10.dp)
        )
        MenuItemProfil(
            emoji = "🎨",
            fond = Color(0x24C77D5E),
            libelle = stringResource(R.string.profile_customization)
        ) { onNavigate(Screen.Customization.route) }
        MenuItemProfil(
            emoji = "📊",
            fond = Color(0x246E86B8),
            libelle = stringResource(R.string.stats_title)
        ) { onNavigate(Screen.AllStats.route) }
        MenuItemProfil(
            emoji = "⚙️",
            fond = Color(0x1F2E5A48),
            libelle = stringResource(R.string.settings_title)
        ) { onNavigate(Screen.Settings.route) }
        MenuItemProfil(
            emoji = "💾",
            fond = Color(0x297EA88A),
            libelle = stringResource(R.string.profile_menu_export)
        ) { creerExport.launch(SauvegardeRepository.nomProposé()) }
        messageExport?.let { texte ->
            Spacer(Modifier.height(6.dp))
            Text(
                texte,
                color = if (exportReussi) SuccessGreen else DangerRed,
                fontSize = 12.sp
            )
        }

        // ── Progression réelle : cinq dimensions, aucune obligation ──────
        Spacer(Modifier.height(22.dp))
        Text(
            stringResource(R.string.progression_title).uppercase(),
            color = c.textSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.2.sp,
            modifier = Modifier.padding(bottom = 10.dp)
        )
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(c.surface2)
                .border(1.dp, c.border, RoundedCornerShape(16.dp))
                .padding(14.dp)
        ) {
            progressionReelle.forEach { dim ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "${dim.emoji}  ${stringResource(dim.libelle)}",
                        color = c.textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(dim.valeur, color = c.textSecondary, fontSize = 12.sp)
                }
                LinearProgressIndicator(
                    progress = { dim.progression },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = c.accent,
                    trackColor = c.surface3
                )
                Spacer(Modifier.height(6.dp))
            }
            Text(
                stringResource(R.string.progression_hint),
                color = c.textDisabled,
                fontSize = 11.sp
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun RowScope.CarteMetrique(valeur: String, libelle: String) {
    val c = MaterialTheme.sankaiColors
    Column(
        Modifier
            .weight(1f)
            .clip(RoundedCornerShape(16.dp))
            .background(c.surface2)
            .border(1.dp, c.border, RoundedCornerShape(16.dp))
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            valeur,
            color = c.textPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(3.dp))
        Text(
            libelle,
            color = c.textSecondary,
            fontSize = 10.5.sp,
            maxLines = 1
        )
    }
}

@Composable
private fun MenuItemProfil(
    emoji: String,
    fond: Color,
    libelle: String,
    onClick: () -> Unit
) {
    val c = MaterialTheme.sankaiColors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(c.surface2)
            .border(1.dp, c.border, RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(fond),
            contentAlignment = Alignment.Center
        ) {
            Text(emoji, fontSize = 20.sp)
        }
        Spacer(Modifier.width(14.dp))
        Text(
            libelle,
            color = c.textPrimary,
            fontSize = 14.5.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        Text("›", color = c.textSecondary, fontSize = 22.sp)
    }
}
