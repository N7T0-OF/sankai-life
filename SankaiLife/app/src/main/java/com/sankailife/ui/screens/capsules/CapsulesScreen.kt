package com.sankailife.ui.screens.capsules

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sankailife.R
import com.sankailife.core.audio.rememberVoix
import com.sankailife.core.culture.ContentRightsStatus
import com.sankailife.core.culture.CultureEntryType
import com.sankailife.core.culture.DailyCultureEntry
import com.sankailife.ui.components.SankaiButton
import com.sankailife.ui.theme.CultureBg
import com.sankailife.ui.theme.CultureBlue
import com.sankailife.ui.theme.CultureBorder
import com.sankailife.ui.theme.CultureCard
import com.sankailife.ui.theme.CultureInk
import com.sankailife.ui.theme.CultureInkSoft

/**
 * Lecture calme d'une unique capsule quotidienne.
 *
 * Pas de carrousel, pagination, score ou recommandation suivante : le seul
 * mouvement possible dans le contenu est de retourner la carte pour lire sa
 * provenance.
 *
 * La page garde une palette claire et fixe (papier chaud, carte blanche,
 * encre), volontairement lumineuse même en thème sombre — c'est l'identité
 * de la lecture culturelle dans les maquettes, et le correctif de l'écran
 * qui apparaissait sombre.
 */
@Composable
fun CapsulesScreen(
    viewModel: CapsulesViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val saveError = stringResource(R.string.culture_save_error)
    val entry = state.entry

    val importerPack = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.importPack(uri)
    }

    LaunchedEffect(state.saveError) {
        if (state.saveError) {
            snackbar.showSnackbar(saveError)
            viewModel.dismissSaveError()
        }
    }

    LaunchedEffect(state.importMessage) {
        state.importMessage?.let { message ->
            snackbar.showSnackbar(message)
            viewModel.dismissImportMessage()
        }
    }

    Box(Modifier.fillMaxSize().background(CultureBg)) {
        Column(Modifier.fillMaxSize()) {
            Header(
                onBack = onBack,
                favorite = state.favorite,
                favoriteVisible = entry != null,
                onToggleFavorite = viewModel::toggleFavorite,
                onImporter = {
                    importerPack.launch(arrayOf("*/*"))
                },
                importEnabled = !state.importing
            )

            when {
                state.loading -> CenteredState {
                    CircularProgressIndicator(color = CultureBlue)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.culture_loading),
                        color = CultureInkSoft
                    )
                }
                state.loadError -> CenteredState {
                    Text(
                        stringResource(R.string.culture_error_title),
                        color = CultureInk,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.culture_error_body),
                        color = CultureInkSoft,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(20.dp))
                    SankaiButton(
                        text = stringResource(R.string.culture_retry),
                        onClick = viewModel::load
                    )
                }
                state.empty || entry == null -> CenteredState {
                    Text(
                        stringResource(R.string.culture_empty_title),
                        color = CultureInk,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.culture_empty_body),
                        color = CultureInkSoft,
                        textAlign = TextAlign.Center
                    )
                }
                else -> CapsuleContent(
                    entry = entry,
                    detailsVisible = state.detailsVisible,
                    onToggleDetails = viewModel::toggleDetails
                )
            }
        }
        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun Header(
    onBack: () -> Unit,
    favorite: Boolean,
    favoriteVisible: Boolean,
    onToggleFavorite: () -> Unit,
    onImporter: () -> Unit,
    importEnabled: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.action_back),
                tint = CultureInk
            )
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                stringResource(R.string.culture_title),
                color = CultureInk,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                stringResource(R.string.culture_subtitle),
                color = CultureInkSoft,
                fontSize = 11.sp
            )
        }
        if (favoriteVisible) {
            IconButton(onClick = onToggleFavorite, enabled = importEnabled) {
                Icon(
                    imageVector = if (favorite) Icons.Filled.Favorite
                    else Icons.Outlined.FavoriteBorder,
                    contentDescription = stringResource(
                        if (favorite) R.string.culture_favorite_remove
                        else R.string.culture_favorite_add
                    ),
                    tint = if (favorite) CultureBlue else CultureInkSoft
                )
            }
        } else {
            Spacer(Modifier.padding(24.dp))
        }
    }

    // Importer un pack culturel : la seule porte d'entrée de contenus
    // supplémentaires, et elle reste discrète.
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.End
    ) {
        TextButton(
            onClick = onImporter,
            enabled = importEnabled
        ) {
            Text(
                stringResource(R.string.culture_import_pack),
                color = if (importEnabled) CultureBlue else CultureInkSoft.copy(alpha = 0.5f),
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun CenteredState(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        content = content
    )
}

@Composable
private fun CapsuleContent(
    entry: DailyCultureEntry,
    detailsVisible: Boolean,
    onToggleDetails: () -> Unit
) {
    val voice = rememberVoix()
    val canRead = entry.body != null && voice.disponiblePour(entry.languageCode)

    DisposableEffect(entry.id, voice) {
        onDispose { voice.arreter() }
    }

    // La page ne défile pas : tout tient à l'écran. Seule la carte, au
    // centre, fait défiler son contenu si le texte est long — l'habillage
    // (type, retournement, écoute) reste toujours visible.
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            stringResource(R.string.culture_today_label),
            color = CultureBlue,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.4.sp
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(typeLabel(entry.type)),
            color = CultureInkSoft,
            fontSize = 13.sp
        )
        Spacer(Modifier.height(12.dp))

        // La carte entière se retourne : le texte devient la provenance, et
        // inversement. Un geste, pas une navigation. Elle occupe tout l'espace
        // restant, pour que l'écran ne défile jamais.
        CarteRetournable(
            entry = entry,
            detailsVisible = detailsVisible,
            onFlip = {
                voice.arreter()
                onToggleDetails()
            },
            modifier = Modifier.fillMaxWidth().weight(1f)
        )

        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(
                    if (detailsVisible) R.string.culture_flip_to_text
                    else R.string.culture_flip_to_details
                ),
                color = CultureInkSoft,
                fontSize = 12.sp
            )
            // L'écoute reste un bouton compact : une icône, pas un bandeau.
            if (canRead) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(CultureBlue.copy(alpha = 0.08f))
                        .border(1.dp, CultureBlue.copy(alpha = 0.30f), RoundedCornerShape(22.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(
                        onClick = { entry.body?.let { voice.dire(it, entry.languageCode) } },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            Icons.Filled.VolumeUp,
                            contentDescription = stringResource(R.string.culture_listen),
                            tint = CultureBlue
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.culture_end_note),
            color = CultureInkSoft,
            fontSize = 12.sp,
            fontStyle = FontStyle.Italic,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
    }
}

/**
 * La capsule en carte à retourner.
 *
 * Deux faces dans le même volume : le texte au recto, la provenance au verso.
 * Un appui fait pivoter la carte d'un demi-tour (animation 3D), et chaque face
 * se dévoile exactement au bon moment — le verso est pré-roté pour être lisible
 * après la bascule.
 */
@Composable
private fun CarteRetournable(
    entry: DailyCultureEntry,
    detailsVisible: Boolean,
    onFlip: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rotation by animateFloatAsState(
        targetValue = if (detailsVisible) 180f else 0f,
        animationSpec = tween(durationMillis = 450),
        label = "capsule_flip"
    )
    val densite = LocalDensity.current.density
    val rectoVisible = rotation <= 90f

    Box(
        modifier.graphicsLayer {
            this.rotationY = rotation
            // Éloigne le pivot pour une rotation profonde, pas un simple
            // aplatissement horizontal.
            cameraDistance = 12f * densite
        }
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    rotationY = 0f
                    alpha = if (rectoVisible) 1f else 0f
                }
        ) {
            CarteCulture(modifier = Modifier.fillMaxSize(), onClick = onFlip) { ReadingFace(entry) }
        }
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    rotationY = 180f
                    alpha = if (rectoVisible) 0f else 1f
                }
        ) {
            CarteCulture(modifier = Modifier.fillMaxSize(), onClick = onFlip) { DetailsFace(entry) }
        }
    }
}

/** La carte blanche de lecture, dans le papier chaud de l'écran. */
@Composable
private fun CarteCulture(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .shadow(10.dp, RoundedCornerShape(26.dp), clip = false)
            .clip(RoundedCornerShape(26.dp))
            .background(CultureCard)
            .border(1.dp, CultureBorder, RoundedCornerShape(26.dp))
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(20.dp), content = content)
    }
}

@Composable
private fun ReadingFace(entry: DailyCultureEntry) {
    Column(
        // Le texte long défile à l'intérieur de la carte, jamais la page.
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            entry.title,
            color = CultureInk,
            fontSize = 25.sp,
            lineHeight = 32.sp,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        entry.author?.let { author ->
            Spacer(Modifier.height(8.dp))
            Text(author, color = CultureInkSoft, fontSize = 14.sp)
        }
        entry.body?.let { body ->
            Spacer(Modifier.height(28.dp))
            SelectionContainer {
                Text(
                    body,
                    modifier = Modifier.fillMaxWidth(),
                    color = CultureInk,
                    fontSize = 18.sp,
                    lineHeight = 30.sp,
                    fontFamily = FontFamily.Serif,
                    textAlign = if (entry.type == CultureEntryType.POEM) {
                        TextAlign.Center
                    } else {
                        TextAlign.Start
                    }
                )
            }
        }
    }
}

@Composable
private fun DetailsFace(entry: DailyCultureEntry) {
    val authorYears = listOfNotNull(
        entry.authorBirthYear?.toString(),
        entry.authorDeathYear?.toString()
    ).joinToString(" – ").ifBlank { null }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
    ) {
        Text(
            entry.author ?: stringResource(R.string.culture_author_unknown),
            color = CultureInk,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        authorYears?.let {
            Text(it, color = CultureInkSoft, fontSize = 13.sp)
        }
        Spacer(Modifier.height(18.dp))
        HorizontalDivider(color = CultureBorder)
        Spacer(Modifier.height(12.dp))

        entry.workDate?.let {
            DetailRow(stringResource(R.string.culture_work_date), it)
        }
        entry.publicationDate?.let {
            DetailRow(stringResource(R.string.culture_publication_date), it)
        }
        entry.countryCode?.let {
            DetailRow(stringResource(R.string.culture_country), it)
        }
        entry.context?.let {
            DetailRow(stringResource(R.string.culture_context), it)
        }
        DetailRow(
            stringResource(R.string.culture_source),
            entry.sourceLabel ?: stringResource(R.string.culture_source_unknown)
        )
        DetailRow(
            stringResource(R.string.culture_rights),
            stringResource(rightsLabel(entry.rightsStatus))
        )
        entry.license?.let {
            DetailRow(stringResource(R.string.culture_license), it)
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
        Text(
            label,
            color = CultureInkSoft,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(2.dp))
        Text(
            value,
            color = CultureInk,
            fontSize = 14.sp,
            lineHeight = 21.sp
        )
    }
}

@StringRes
private fun typeLabel(type: CultureEntryType): Int = when (type) {
    CultureEntryType.POEM -> R.string.culture_type_poem
    CultureEntryType.QUOTE -> R.string.culture_type_quote
    CultureEntryType.PROVERB -> R.string.culture_type_proverb
    CultureEntryType.ARTWORK -> R.string.culture_type_artwork
    CultureEntryType.HISTORY -> R.string.culture_type_history
    CultureEntryType.SCIENCE -> R.string.culture_type_science
    CultureEntryType.WORD -> R.string.culture_type_word
    CultureEntryType.BIOGRAPHY -> R.string.culture_type_biography
}

@StringRes
private fun rightsLabel(status: ContentRightsStatus): Int = when (status) {
    ContentRightsStatus.PUBLIC_DOMAIN -> R.string.culture_rights_public_domain
    ContentRightsStatus.CREATIVE_COMMONS -> R.string.culture_rights_creative_commons
    ContentRightsStatus.LICENSED -> R.string.culture_rights_licensed
    ContentRightsStatus.METADATA_ONLY -> R.string.culture_rights_metadata_only
}
