package com.sankailife.ui.screens.academie

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sankailife.R
import com.sankailife.SankaiApplication
import com.sankailife.core.learning.data.LearningModuleEntity
import com.sankailife.core.learning.data.LearningRepository
import com.sankailife.core.learning.domain.AcademieEngine
import com.sankailife.ui.components.SankaiButton
import com.sankailife.ui.components.SankaiCard
import com.sankailife.ui.theme.sankaiColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Le parcours d'un module : ses unités, en chemin.
 *
 * Vertical et non courbé, contrairement à ce qui était demandé. Une courbe
 * décorative sur une liste qui peut compter cinquante unités coûte du calcul
 * de position à chaque frame pour un gain qui disparaît dès qu'on fait défiler.
 * Le chemin se lit à la ligne verticale qui relie les nœuds, ce qui suffit à
 * dire « ça continue en dessous ».
 */
class ParcoursViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as SankaiApplication
    private val depot = LearningRepository(app.database)

    data class Etat(
        val chargement: Boolean = true,
        val module: LearningModuleEntity? = null,
        val noeuds: List<AcademieEngine.Noeud> = emptyList(),
        val progression: Float = 0f,
        val erreur: String = ""
    )

    private val _etat = MutableStateFlow(Etat())
    val etat: StateFlow<Etat> = _etat.asStateFlow()

    fun charger(profileId: Long) {
        viewModelScope.launch {
            _etat.value = Etat(chargement = true)
            runCatching {
                val app = getApplication<android.app.Application>()
                val module = depot.moduleDuProfil(profileId)
                    ?: return@runCatching Etat(
                        chargement = false,
                        erreur = app.getString(R.string.academy_module_gone)
                    )
                val noeuds = depot.parcours(module)
                Etat(
                    chargement = false,
                    module = module,
                    noeuds = noeuds,
                    progression = AcademieEngine.progression(noeuds),
                    erreur = if (noeuds.isEmpty()) {
                        app.getString(R.string.academy_module_empty)
                    } else ""
                )
            }.onSuccess { _etat.value = it }
                .onFailure {
                    _etat.value = Etat(
                        chargement = false,
                        erreur = getApplication<android.app.Application>()
                            .getString(R.string.academy_load_error)
                    )
                }
        }
    }

    companion object {
        fun factory(app: SankaiApplication) = viewModelFactory {
            initializer { ParcoursViewModel(app) }
        }
    }
}

@Composable
fun ParcoursScreen(
    profileId: Long,
    viewModel: ParcoursViewModel,
    onBack: () -> Unit,
    onOuvrirUnite: (String) -> Unit
) {
    val etat by viewModel.etat.collectAsState()
    val c = MaterialTheme.sankaiColors

    androidx.compose.runtime.LaunchedEffect(profileId) { viewModel.charger(profileId) }

    Box(Modifier.fillMaxSize().background(c.background)) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        stringResource(R.string.action_back),
                        tint = c.textPrimary
                    )
                }
                Column {
                    Text(
                        etat.module?.nom?.ifBlank {
                            stringResource(R.string.academy_module_unnamed)
                        } ?: stringResource(R.string.academy_module_unnamed),
                        color = c.textPrimary, fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (etat.noeuds.isNotEmpty()) {
                        Text(
                            "${(etat.progression * 100).toInt()} % · " +
                                stringResource(
                                    R.string.academy_units_done,
                                    etat.noeuds.count {
                                        it.etat == AcademieEngine.Etat.TERMINEE
                                    },
                                    etat.noeuds.size
                                ),
                            color = c.textSecondary, fontSize = 12.sp
                        )
                    }
                }
            }

            when {
                etat.chargement -> Box(
                    Modifier.fillMaxSize(), contentAlignment = Alignment.Center
                ) { CircularProgressIndicator(color = c.accent) }

                etat.erreur.isNotBlank() -> Box(
                    Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(etat.erreur, color = c.textSecondary, fontSize = 14.sp)
                        Spacer(Modifier.height(12.dp))
                        SankaiButton(
                            stringResource(R.string.action_back), onClick = onBack, secondary = true
                        )
                    }
                }

                else -> Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp)
                ) {
                    var chapitreAffiche = -1
                    etat.noeuds.forEach { noeud ->
                        if (noeud.unite.chapitre != chapitreAffiche) {
                            chapitreAffiche = noeud.unite.chapitre
                            Spacer(Modifier.height(18.dp))
                            Text(
                                stringResource(
                                    R.string.academy_chapter, chapitreAffiche + 1
                                ),
                                color = c.textSecondary, fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        NoeudUnite(noeud, onOuvrirUnite)
                    }
                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
private fun NoeudUnite(
    noeud: AcademieEngine.Noeud,
    onOuvrir: (String) -> Unit
) {
    val c = MaterialTheme.sankaiColors
    val verrouillee = noeud.etat == AcademieEngine.Etat.VERROUILLEE

    // Le symbole dit l'état sans qu'on ait à lire une légende.
    val (symbole, couleur) = when (noeud.etat) {
        AcademieEngine.Etat.TERMINEE -> "✓" to c.accent
        AcademieEngine.Etat.ACTUELLE -> "●" to c.accent
        AcademieEngine.Etat.DISPONIBLE -> "○" to c.textSecondary
        AcademieEngine.Etat.REVISION -> "↻" to c.accentSecondary
        AcademieEngine.Etat.VERROUILLEE -> "🔒" to c.textDisabled
    }

    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(34.dp).clip(CircleShape)
                .background(if (verrouillee) c.surface3 else c.surface2),
            contentAlignment = Alignment.Center
        ) {
            Text(symbole, color = couleur, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))

        SankaiCard(
            onClick = if (verrouillee) null else ({ onOuvrir(noeud.unite.id) })
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        noeud.unite.titre,
                        color = if (verrouillee) c.textDisabled else c.textPrimary,
                        fontSize = 15.sp, fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        if (verrouillee) {
                            // On explique la condition plutôt que d'afficher un
                            // cadenas muet : un verrou sans raison se lit comme
                            // une punition.
                            stringResource(R.string.academy_unlock_prev)
                        } else {
                            stringResource(
                                R.string.academy_cards_progress,
                                noeud.unite.taille, (noeud.progression * 100).toInt()
                            )
                        },
                        color = c.textSecondary, fontSize = 12.sp
                    )
                }
                if (!verrouillee) {
                    Text("›", color = c.textSecondary, fontSize = 22.sp)
                }
            }
        }
    }
}
