package com.sankailife.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sankailife.core.domain.engine.DeblocageEngine
import com.sankailife.ui.theme.sankaiColors

/**
 * Ce qu'un cadenas raconte.
 *
 * Toujours trois choses : ce qu'on obtient, à quel niveau, et combien il
 * reste. Un cadenas qui dit seulement « verrouillé » donne l'impression d'un
 * mur ; celui-ci donne un cap.
 *
 * La liste de ce qui vient ensuite est affichée aussi. Savoir qu'il y a une
 * suite change la façon dont on vit un refus.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeuilleVerrou(
    verrou: DeblocageEngine.Verrou,
    onFermer: () -> Unit
) {
    val c = MaterialTheme.sankaiColors

    ModalBottomSheet(onDismissRequest = onFermer, containerColor = c.surface1) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(bottom = 30.dp)) {

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(verrou.fonction.emoji, fontSize = 30.sp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(verrou.titre, color = c.textPrimary,
                        fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(verrou.fonction.description, color = c.textSecondary, fontSize = 12.sp)
                }
            }

            Spacer(Modifier.height(18.dp))
            LinearProgressIndicator(
                progress = {
                    if (verrou.fonction.niveauRequis <= 0) 1f
                    else verrou.niveauActuel.toFloat() / verrou.fonction.niveauRequis
                },
                modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(4.dp)),
                color = c.accent,
                trackColor = c.surface3
            )
            Spacer(Modifier.height(8.dp))
            Text(verrou.explication, color = c.textSecondary, fontSize = 13.sp)

            // Ce qui suit, pour montrer que le chemin continue.
            val suivantes = DeblocageEngine.Fonction.entries
                .filter { it.niveauRequis > verrou.niveauActuel && it != verrou.fonction }
                .sortedBy { it.niveauRequis }
                .take(3)

            if (suivantes.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                Text("ENSUITE", color = c.textSecondary, fontSize = 10.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)
                Spacer(Modifier.height(8.dp))

                suivantes.forEach { f ->
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 6.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(c.surface2)
                            .padding(11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(f.emoji, fontSize = 17.sp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(f.libelle, color = c.textPrimary, fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold)
                            Text(f.description, color = c.textSecondary, fontSize = 11.sp)
                        }
                        Text("niv. ${f.niveauRequis}", color = c.textDisabled, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
