package com.sankailife.ui.screens.island

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sankailife.ui.components.SankaiButton
import com.sankailife.ui.theme.sankaiColors

/**
 * Choix de l'île, au premier lancement.
 *
 * Générer une île d'office serait plus simple et priverait le joueur du seul
 * choix qui engage toute sa partie : il gardera cette carte pendant des mois.
 * Trois propositions, quelques chiffres pour les départager, et le droit d'en
 * redemander — mais pas indéfiniment, sinon on cherche l'île parfaite au lieu
 * de commencer à jouer.
 */
@Composable
fun AssistantIle(
    etat: IslandViewModel.Etat,
    onChoisir: (Int) -> Unit,
    onNom: (String) -> Unit,
    onRelancer: () -> Unit,
    onValider: () -> Unit,
    modifier: Modifier = Modifier
) {
    val couleurs = MaterialTheme.sankaiColors

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(64.dp))
        Text(
            "Choisis ton île",
            color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Elle sera définitive. Tu pourras l'aménager, mais pas la redessiner.",
            color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp
        )

        Spacer(Modifier.height(20.dp))

        etat.candidates.forEachIndexed { index, candidate ->
            CarteCandidate(
                candidate = candidate,
                rang = index + 1,
                selectionnee = index == etat.choisie,
                onClic = { onChoisir(index) }
            )
            Spacer(Modifier.height(12.dp))
        }

        Spacer(Modifier.height(4.dp))

        OutlinedTextField(
            value = etat.nom,
            onValueChange = onNom,
            singleLine = true,
            label = { Text("Nom de l'île", color = Color.White.copy(alpha = 0.7f)) },
            placeholder = { Text("Facultatif", color = Color.White.copy(alpha = 0.4f)) },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = couleurs.accent,
                unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = couleurs.accent
            ),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(Modifier.height(16.dp))

        SankaiButton(
            "Choisir cette île",
            onClick = onValider,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))

        if (etat.relancesRestantes > 0) {
            SankaiButton(
                "Proposer trois autres îles (${etat.relancesRestantes})",
                onClick = onRelancer,
                secondary = true,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            // On le dit, au lieu de laisser un bouton mort ou de le faire
            // disparaître sans explication.
            Text(
                "Plus de nouvelles propositions. Choisis parmi ces trois îles.",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun CarteCandidate(
    candidate: IslandViewModel.Candidate,
    rang: Int,
    selectionnee: Boolean,
    onClic: () -> Unit
) {
    val couleurs = MaterialTheme.sankaiColors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = if (selectionnee) 0.45f else 0.28f))
            .border(
                width = if (selectionnee) 2.dp else 1.dp,
                color = if (selectionnee) couleurs.accent else Color.White.copy(alpha = 0.18f),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClic)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Miniature dessinée par la même fonction que la carte plein écran :
        // l'aperçu ne peut donc pas montrer autre chose que ce qu'on obtient.
        Box(
            Modifier
                .height(104.dp)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(10.dp))
                .background(PaletteIle.couleur(com.sankailife.core.island.domain.IslandTileType.DEEP_WATER))
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val (camera, pas) = cadrerEntier(candidate.ile, size.width, size.height)
                dessinerIle(candidate.ile, camera, pas)
            }
        }

        Spacer(Modifier.fillMaxWidth(0.04f))

        Column(Modifier.padding(start = 12.dp)) {
            Text(
                "Île $rang",
                color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(6.dp))
            Ligne("Cultivable", "${candidate.cultivables} cases")
            Ligne("Bois", if (candidate.boise == 0) "aucun" else "${candidate.boise} cases")
            Ligne(
                "Rivière",
                if (candidate.rivieres == 0) "aucune" else "${candidate.rivieres} cases"
            )
        }
    }
}

@Composable
private fun Ligne(cle: String, valeur: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(cle, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
        Text(valeur, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}
