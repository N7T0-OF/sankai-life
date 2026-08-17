package com.sankailife.ui.screens.capsules

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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
import com.sankailife.ui.components.SankaiCard
import com.sankailife.ui.theme.sankaiColors

/**
 * Lecture calme d'une unique capsule quotidienne.
 *
 * Pas de carrousel, pagination, score ou recommandation suivante : le seul
 * mouvement possible dans le contenu est de retourner la carte pour lire sa
 * provenance.
 */
@Composable
fun CapsulesScreen(
    viewModel: CapsulesViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = MaterialTheme.sankaiColors
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

    Box(Modifier.fillMaxSize().background(colors.background)) {
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
                    CircularProgressIndicator(color = colors.accent)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.culture_loading),
                        color = colors.textSecondary
                    )
                }
                state.loadError -> CenteredState {
                    Text(
                        stringResource(R.string.culture_error_title),
                        color = colors.textPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.culture_error_body),
                        color = colors.textSecondary,
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
                        color = colors.textPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.culture_empty_body),
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center
                    )
                }
                else -> CapsuleContent(
                    entry = entry,
                    detailsVisible = state.detailsVisible,
                    reflectionVisible = state.reflectionVisible,
                    reflection = state.reflection,
                    savingReflection = state.savingReflection,
                    reflectionSaved = state.reflectionSaved,
                    onToggleDetails = viewModel::toggleDetails,
                    onToggleReflection = viewModel::toggleReflection,
                    onReflectionChange = viewModel::updateReflection,
                    onSaveReflection = viewModel::saveReflection
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
    val colors = MaterialTheme.sankaiColors
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.action_back),
                tint = colors.textPrimary
            )
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                stringResource(R.string.culture_title),
                color = colors.textPrimary,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                stringResource(R.string.culture_subtitle),
                color = colors.textSecondary,
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
                    tint = if (favorite) colors.accent else colors.textSecondary
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
                color = if (importEnabled) colors.accent else colors.textDisabled,
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
    reflectionVisible: Boolean,
    reflection: String,
    savingReflection: Boolean,
    reflectionSaved: Boolean,
    onToggleDetails: () -> Unit,
    onToggleReflection: () -> Unit,
    onReflectionChange: (String) -> Unit,
    onSaveReflection: () -> Unit
) {
    val colors = MaterialTheme.sankaiColors
    val voice = rememberVoix()
    val activity = LocalContext.current as? Activity
    val canRead = entry.body != null && voice.disponiblePour(entry.languageCode)

    DisposableEffect(entry.id, voice) {
        onDispose { voice.arreter() }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            stringResource(R.string.culture_today_label),
            color = colors.accent,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.4.sp
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(typeLabel(entry.type)),
            color = colors.textSecondary,
            fontSize = 13.sp
        )
        Spacer(Modifier.height(18.dp))

        SankaiCard {
            if (detailsVisible) DetailsFace(entry) else ReadingFace(entry)
        }

        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SankaiButton(
                text = stringResource(
                    if (detailsVisible) R.string.culture_flip_to_text
                    else R.string.culture_flip_to_details
                ),
                onClick = {
                    voice.arreter()
                    onToggleDetails()
                },
                secondary = true,
                modifier = Modifier.weight(1f)
            )
            if (canRead) {
                SankaiButton(
                    text = stringResource(R.string.culture_listen),
                    onClick = { entry.body?.let { voice.dire(it, entry.languageCode) } },
                    secondary = true,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(Modifier.height(18.dp))
        SankaiButton(
            text = stringResource(R.string.culture_reflection_title),
            onClick = onToggleReflection,
            secondary = true,
            modifier = Modifier.fillMaxWidth()
        )

        if (reflectionVisible) {
            Spacer(Modifier.height(12.dp))
            SankaiCard {
                Text(
                    stringResource(R.string.culture_reflection_prompt),
                    color = colors.textPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.culture_local_only),
                    color = colors.textSecondary,
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = reflection,
                    onValueChange = onReflectionChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.culture_reflection_placeholder)) },
                    minLines = 3,
                    maxLines = 7,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onSaveReflection() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary,
                        focusedBorderColor = colors.accent,
                        unfocusedBorderColor = colors.border,
                        focusedContainerColor = colors.surface1,
                        unfocusedContainerColor = colors.surface1
                    )
                )
                Spacer(Modifier.height(10.dp))
                SankaiButton(
                    text = stringResource(
                        if (reflectionSaved) R.string.culture_reflection_saved
                        else R.string.culture_reflection_save
                    ),
                    onClick = onSaveReflection,
                    enabled = !savingReflection,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(R.string.culture_end_note),
            color = colors.textSecondary,
            fontSize = 12.sp,
            fontStyle = FontStyle.Italic,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(14.dp))
        SankaiButton(
            text = stringResource(R.string.culture_close_app),
            onClick = { activity?.finishAndRemoveTask() },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun ReadingFace(entry: DailyCultureEntry) {
    val colors = MaterialTheme.sankaiColors
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            entry.title,
            color = colors.textPrimary,
            fontSize = 25.sp,
            lineHeight = 32.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        entry.author?.let { author ->
            Spacer(Modifier.height(8.dp))
            Text(author, color = colors.textSecondary, fontSize = 14.sp)
        }
        entry.body?.let { body ->
            Spacer(Modifier.height(28.dp))
            SelectionContainer {
                Text(
                    body,
                    modifier = Modifier.fillMaxWidth(),
                    color = colors.textPrimary,
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
    val colors = MaterialTheme.sankaiColors
    val authorYears = listOfNotNull(
        entry.authorBirthYear?.toString(),
        entry.authorDeathYear?.toString()
    ).joinToString(" – ").ifBlank { null }

    Column(Modifier.fillMaxWidth()) {
        Text(
            entry.author ?: stringResource(R.string.culture_author_unknown),
            color = colors.textPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        authorYears?.let {
            Text(it, color = colors.textSecondary, fontSize = 13.sp)
        }
        Spacer(Modifier.height(18.dp))
        HorizontalDivider(color = colors.border)
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
    val colors = MaterialTheme.sankaiColors
    Column(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
        Text(
            label,
            color = colors.textSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(2.dp))
        Text(
            value,
            color = colors.textPrimary,
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
