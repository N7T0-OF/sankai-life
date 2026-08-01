package com.sankailife.ui.screens.home

import android.app.Activity
import androidx.compose.animation.*
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Brush
import com.sankailife.core.data.db.entities.ChestEntity
import com.sankailife.core.domain.engine.ArenaEngine
import com.sankailife.core.garden.domain.CropStage
import com.sankailife.core.garden.domain.DayNightEngine
import com.sankailife.ui.art.ArtJardin
import com.sankailife.ui.art.IconeArt
import com.sankailife.ui.components.*
import com.sankailife.ui.navigation.Screen
import com.sankailife.ui.screens.arenas.CarteResumeArene
import com.sankailife.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(viewModel: HomeViewModel, onNavigate: (String) -> Unit) {
    val user    by viewModel.user.collectAsState()
    val chests  by viewModel.chests.collectAsState()
    val toast   by viewModel.toastMessage.collectAsState()
    val adCd    by viewModel.adCooldown.collectAsState()
    val showLvl by viewModel.showLevelUp.collectAsState()
    val lvlNum  by viewModel.levelUpLevel.collectAsState()
    val chestRw by viewModel.chestReward.collectAsState()
    val enLigne by viewModel.isOnline.collectAsState()
    val arenesAReclamer by viewModel.arenesAReclamer.collectAsState()
    val moduleContextuel by viewModel.moduleContextuel.collectAsState()
    val c = MaterialTheme.sankaiColors

    // Les publicités ne partent plus de l'accueil : elles restent accessibles
    // depuis la boutique, sur action volontaire.

    // Dialogs
    if (showLvl) LevelUpDialog(level = lvlNum, coins = lvlNum * 50, onDismiss = { viewModel.dismissLevelUp() })
    chestRw?.let { rw ->
        ChestRewardDialog("Coffre ouvert !", rw.coins, rw.gems, rw.xp, onDismiss = { viewModel.dismissChestReward() })
    }

    Box(Modifier.fillMaxSize().background(c.background)) {
        Column(Modifier.fillMaxSize()) {
            // Resource bar
            ResourceBar(user.level, user.xp, user.xpNext, user.coins, user.gems)

            // L'accueil ne défile plus.
            //
            // Le diorama du jardin en a été retiré : il faisait dépasser le
            // contenu, et l'accueil n'a pas à raconter le jardin — le bouton
            // qui y mène suffit. Restent l'arène, l'entrée du jeu et les
            // coffres.
            //
            // Le défilement est retiré, pas seulement inutilisé : une colonne
            // défilante impose une hauteur infinie à ses enfants, dans laquelle
            // `weight` n'a aucun sens et fait planter la mise en page. C'est
            // l'un ou l'autre, et ici c'est l'arène qui doit absorber la place.
            //
            // Conséquence à surveiller : à très grande taille de police, le
            // contenu ne peut plus déborder — il comprime l'arène. Elle est
            // faite pour ça, mais c'est un compromis, pas un choix gratuit.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                // Header
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Bonjour, ${user.pseudo} 👋", color = c.textPrimary,
                            fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Text("Reste focus et progresse !", color = c.textSecondary, fontSize = 13.sp)
                    }
                    StreakBadge(user.streakDays)
                    Spacer(Modifier.width(8.dp))
                    // Accès direct aux paramètres. Ils vivaient derrière le
                    // profil, ce qui obligeait à passer par un écran qui n'a
                    // rien à voir pour couper une notification.
                    Box(
                        Modifier.size(38.dp)
                            .clip(CircleShape)
                            .background(c.surface2)
                            .border(1.dp, c.border, CircleShape)
                            .clickable { onNavigate(Screen.Settings.route) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Settings,
                            contentDescription = "Paramètres",
                            tint = c.textSecondary,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                // L'arène prend tout l'espace restant : c'est elle l'élément
                // visuel principal, et lui donner le poids libéré par le
                // diorama évite un grand vide sous le bouton.
                Box(Modifier.weight(1f)) {
                    CarteResumeArene(
                        niveau = user.level,
                        nombreAReclamer = arenesAReclamer,
                        onVoirParcours = { onNavigate(Screen.Arenas.route) }
                    )
                }

                Spacer(Modifier.height(12.dp))

                // Entrée du mode jeu. Bouton unique et large, à la façon d'un
                // hub : le jardin est un univers séparé, pas une section.
                SankaiButton(
                    "🌿  Entrer dans le jardin",
                    onClick = { onNavigate(Screen.Garden.route) },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(10.dp))
            }

            // Barre fixe des coffres, hors zone de défilement.
            // Elle porte tout le système : emplacements, minuteries, ouverture.
            // Aucune carte ne doit la doubler dans le contenu principal —
            // c'était la redondance à supprimer, pas la barre elle-même.
            BarreCoffres(
                chests = chests,
                onOpen = { viewModel.openChest(it) },
                formatTimer = { viewModel.formatChestTimer(it) }
            )
        }

        // Toast overlay
        AnimatedVisibility(
            visible = toast.isNotBlank(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 90.dp),
            enter = fadeIn() + slideInVertically { it },
            exit  = fadeOut() + slideOutVertically { it }
        ) {
            Box(
                Modifier.clip(RoundedCornerShape(24.dp)).background(c.surface2)
                    .border(1.dp, c.accent, RoundedCornerShape(24.dp)).padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text(toast, color = c.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * Aperçu du jardin sur l'accueil.
 *
 * Volontairement non interactif : un appui ouvre le jardin, rien d'autre.
 * Y brancher les actions du jeu ferait de l'accueil un second écran de jeu, et
 * obligerait à charger tout le mode jardin dès le démarrage de l'application.
 *
 * Il reflète l'avancement — arène, phase du jour — sans lire l'état complet des
 * parcelles, qui coûterait une requête à chaque retour sur l'accueil.
 */
@Composable
fun DioramaJardin(niveau: Int, onEntrer: () -> Unit) {
    val c = MaterialTheme.sankaiColors
    val arene = ArenaEngine.areneActuelle(niveau)
    val phase = DayNightEngine.phase()
    val nuit = DayNightEngine.intensiteNuit()

    Box(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF1B3A26).copy(alpha = 1f - nuit * 0.5f),
                        Color(0xFF0C1D14)
                    )
                )
            )
            .border(1.dp, Color(0xFF2E5238), RoundedCornerShape(18.dp))
            .clickable { onEntrer() }
            .padding(14.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconeArt(ArtJardin.arbre, taille = 30.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Ton jardin", color = c.textPrimary,
                        fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(phase.libelle, color = c.textSecondary, fontSize = 11.sp)
                }
                IconeArt(ArtJardin.phase(phase), taille = 26.dp)
            }
            Spacer(Modifier.height(10.dp))
            // Rangée décorative : une silhouette de terrain, pas une grille
            // fonctionnelle. Elle donne l'échelle sans rien promettre.
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                listOf(
                    CropStage.GERME, CropStage.POUSSE, CropStage.JEUNE,
                    CropStage.MATURE, CropStage.MATURE
                ).forEachIndexed { index, stade ->
                    Box(
                        Modifier.weight(1f).height(38.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF3E2C1B)),
                        contentAlignment = Alignment.Center
                    ) {
                        IconeArt(
                            ArtJardin.plante(stade, prete = index == 4),
                            taille = 32.dp
                        )
                    }
                }
            }
        }
    }
}

/**
 * Carte de module contextuelle.
 *
 * Retirée de l'accueil : celui-ci ne présente plus que l'arène, le jardin et
 * les coffres. Conservée parce qu'elle reste utilisée par Mode Vie, où un
 * raccourci vers le module en cours est à sa place.
 */
@Composable
fun CarteModuleContextuel(
    module: HomeViewModel.ModuleContextuel,
    onOuvrir: (String) -> Unit
) {
    val c = MaterialTheme.sankaiColors
    SankaiCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(42.dp).clip(RoundedCornerShape(12.dp))
                    .background(c.accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(module.emoji, fontSize = 20.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    module.titre, color = c.textSecondary, fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp
                )
                Text(
                    module.ligne1, color = c.textPrimary,
                    fontSize = 15.sp, fontWeight = FontWeight.SemiBold
                )
                Text(module.ligne2, color = c.textSecondary, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
        SankaiButton(
            module.libelleBouton,
            onClick = { onOuvrir(module.route) },
            small = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Barre de coffres fixe en bas de l'accueil.
 *
 * Quatre emplacements toujours affichés, même vides : voir un emplacement
 * libre donne envie de le remplir, alors qu'une liste qui rétrécit ne dit rien.
 */
@Composable
fun BarreCoffres(
    chests: List<ChestEntity>,
    onOpen: (Long) -> Unit,
    formatTimer: (ChestEntity) -> String
) {
    val c = MaterialTheme.sankaiColors
    // Un tick par seconde suffit à animer les comptes à rebours.
    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { while (true) { delay(1000); tick++ } }

    val prets = chests.count { it.isReady }

    Column(
        Modifier
            .fillMaxWidth()
            .background(c.surface1)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "Coffres  ${chests.size}/4",
                color = c.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold
            )
            if (prets > 0) {
                Text(
                    "$prets prêt${if (prets > 1) "s" else ""} !",
                    color = c.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(4) { slot ->
                val chest = chests.find { it.slotIndex == slot }
                ChestSlotUI(
                    chest = chest,
                    onOpen = { chest?.let { onOpen(it.id) } },
                    timer = chest?.let { if (tick >= 0) formatTimer(it) else "" } ?: "",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * Nom lisible d'une rareté.
 *
 * Le type stocké est en anglais et en majuscules parce qu'il vient de la base ;
 * l'afficher tel quel exposerait une convention interne au joueur.
 */
private fun libelleRarete(type: String): String = when (type.uppercase()) {
    "DAILY" -> "Quotidien"
    "RARE" -> "Rare"
    "EPIC" -> "Épique"
    "LEGENDARY" -> "Légendaire"
    "WEEKLY" -> "Hebdo"
    "ARENA" -> "Arène"
    else -> "Commun"
}

@Composable
fun ChestSlotUI(chest: ChestEntity?, onOpen: () -> Unit, timer: String, modifier: Modifier = Modifier) {
    val c = MaterialTheme.sankaiColors
    val chestColor = when (chest?.type) {
        "RARE"       -> ChestRare
        "EPIC"       -> ChestEpic
        "LEGENDARY"  -> ChestLegendary
        "DAILY"      -> ChestDaily
        else         -> if (chest != null) ChestCommon else c.surface3
    }
    val isReady = chest?.isReady == true

    // Halo réservé au coffre prêt : c'est ce qui remplace la grande carte
    // supprimée du contenu principal, sans encombrer l'écran.
    val transition = rememberInfiniteTransition(label = "coffre")
    val halo by transition.animateFloat(
        initialValue = 0.45f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(950), RepeatMode.Reverse),
        label = "haloCoffre"
    )

    Box(
        modifier = modifier.height(112.dp).clip(RoundedCornerShape(12.dp))
            .background(
                if (chest != null) chestColor.copy(alpha = if (isReady) 0.22f else 0.15f)
                else c.surface2
            )
            .border(
                width = if (isReady) 2.dp else 1.dp,
                color = if (isReady) chestColor.copy(alpha = halo) else c.border,
                shape = RoundedCornerShape(12.dp)
            )
            .then(if (isReady) Modifier.clickable { onOpen() } else Modifier),
        contentAlignment = Alignment.Center
    ) {
        if (chest == null) {
            Text("—", color = c.textDisabled, fontSize = 20.sp)
        } else {
            // Minuterie au-dessus, coffre au centre, rareté en dessous.
            //
            // Rien n'est superposé au dessin, et c'est lui le plus grand des
            // trois : un coffre doit se reconnaître d'un coup d'œil, le texte
            // n'est là que pour préciser.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
            ) {
                Text(
                    if (isReady) "PRÊT" else timer,
                    color = if (isReady) chestColor else c.textSecondary,
                    fontSize = 10.sp,
                    fontWeight = if (isReady) FontWeight.Bold else FontWeight.Normal
                )
                Spacer(Modifier.height(2.dp))
                IconeArt(ArtJardin.coffre(chest.type), taille = 52.dp)
                Spacer(Modifier.height(2.dp))
                Text(
                    libelleRarete(chest.type),
                    color = chestColor, fontSize = 9.sp, fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun QuickActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    sublabel: String,
    color: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val c = MaterialTheme.sankaiColors
    Box(
        modifier = modifier.clip(RoundedCornerShape(12.dp))
            .background(if (enabled) color.copy(0.12f) else c.surface2)
            .border(1.dp, if (enabled) color.copy(0.4f) else c.border, RoundedCornerShape(12.dp))
            .then(if (enabled) Modifier.clickable { onClick() } else Modifier)
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = if (enabled) color else c.textDisabled, modifier = Modifier.size(24.dp))
            Spacer(Modifier.height(4.dp))
            Text(label,    color = if (enabled) c.textPrimary else c.textDisabled, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(sublabel, color = if (enabled) color else c.textDisabled, fontSize = 10.sp)
        }
    }
}

@Composable
fun StatCard(value: String, label: String, valueColor: Color, modifier: Modifier = Modifier) {
    val c = MaterialTheme.sankaiColors
    Box(modifier.clip(RoundedCornerShape(12.dp)).background(c.surface2).border(0.5.dp, c.border, RoundedCornerShape(12.dp)).padding(12.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(value, color = valueColor, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            Text(label, color = c.textSecondary, fontSize = 10.sp)
        }
    }
}
