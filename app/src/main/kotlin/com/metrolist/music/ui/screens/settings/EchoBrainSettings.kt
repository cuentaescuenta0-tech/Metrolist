package com.metrolist.music.ui.screens.settings

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.R
import com.metrolist.music.constants.DEFAULT_ECHO_BRAIN_MINIMUM_SIMILARITY
import com.metrolist.music.constants.EchoBrainAllowAlternativeVersionsKey
import com.metrolist.music.constants.EchoBrainArtistDiversity
import com.metrolist.music.constants.EchoBrainArtistDiversityKey
import com.metrolist.music.constants.EchoBrainArtistWhitelistEnabledKey
import com.metrolist.music.constants.EchoBrainArtistWhitelistKey
import com.metrolist.music.constants.EchoBrainDailyLearningEnabledKey
import com.metrolist.music.constants.EchoBrainEnabledKey
import com.metrolist.music.constants.EchoBrainExcludeLiveRemixKey
import com.metrolist.music.constants.EchoBrainLearningLevel
import com.metrolist.music.constants.EchoBrainLearningLevelKey
import com.metrolist.music.constants.EchoBrainListeningConfirmation
import com.metrolist.music.constants.EchoBrainListeningConfirmationKey
import com.metrolist.music.constants.EchoBrainMinimumSimilarityKey
import com.metrolist.music.constants.EchoBrainNeuroProfileKey
import com.metrolist.music.constants.EchoBrainStrictAffinityKey
import com.metrolist.music.playback.EchoBrainArtistWhitelist
import com.metrolist.music.ui.component.EnumDialog
import com.metrolist.music.ui.component.IconButton
import com.metrolist.music.ui.component.Material3SettingsGroup
import com.metrolist.music.ui.component.Material3SettingsItem
import com.metrolist.music.ui.component.TextFieldDialog
import com.metrolist.music.ui.utils.backToMain
import com.metrolist.music.utils.rememberPreference

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EchoBrainSettings(navController: NavController) {
    val context = LocalContext.current
    val (enabled, setEnabled) = rememberPreference(EchoBrainEnabledKey, true)
    val (strictAffinity, setStrictAffinity) = rememberPreference(EchoBrainStrictAffinityKey, true)
    val (dailyLearning, setDailyLearning) = rememberPreference(EchoBrainDailyLearningEnabledKey, true)
    val (learningLevelValue, setLearningLevelValue) = rememberPreference(EchoBrainLearningLevelKey, EchoBrainLearningLevel.BALANCED.name)
    val learningLevel = EchoBrainLearningLevel.fromPreference(learningLevelValue)
    val (minimumSimilarity, setMinimumSimilarity) = rememberPreference(EchoBrainMinimumSimilarityKey, DEFAULT_ECHO_BRAIN_MINIMUM_SIMILARITY)
    val (alternatives, setAlternatives) = rememberPreference(EchoBrainAllowAlternativeVersionsKey, false)
    val (excludeLiveRemix, setExcludeLiveRemix) = rememberPreference(EchoBrainExcludeLiveRemixKey, true)
    val (artistDiversityValue, setArtistDiversityValue) = rememberPreference(EchoBrainArtistDiversityKey, EchoBrainArtistDiversity.BALANCED.name)
    val artistDiversity = EchoBrainArtistDiversity.fromPreference(artistDiversityValue)
    val (whitelistEnabled, setWhitelistEnabled) = rememberPreference(EchoBrainArtistWhitelistEnabledKey, false)
    val (serializedWhitelist, setSerializedWhitelist) = rememberPreference(EchoBrainArtistWhitelistKey, "")
    val whitelist = remember(serializedWhitelist) { EchoBrainArtistWhitelist.parse(serializedWhitelist) }
    val (confirmationValue, setConfirmationValue) = rememberPreference(EchoBrainListeningConfirmationKey, EchoBrainListeningConfirmation.SIXTY_PERCENT.name)
    val confirmation = EchoBrainListeningConfirmation.fromPreference(confirmationValue)
    val (_, profileData) = rememberPreference(EchoBrainNeuroProfileKey, "")

    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    var showWhitelistEditor by rememberSaveable { mutableStateOf(false) }

    if (dialog == "similarity") EnumDialog(onDismiss = { dialog = null }, onSelect = { setMinimumSimilarity(it); dialog = null }, title = "Similitud mínima", current = minimumSimilarity, values = listOf(90, 80, 70, 60), valueText = { "$it%" }, valueDescription = { value -> when (value) { 90 -> "Estricto: solo relaciones muy fuertes"; 80 -> "Equilibrado: variedad controlada"; 70 -> "Descubrimiento: más variedad relacionada"; else -> "Flexible: cruza épocas solo con relación guardada" } })
    if (dialog == "diversity") EnumDialog(onDismiss = { dialog = null }, onSelect = { setArtistDiversityValue(it.name); dialog = null }, title = "Diversidad de artistas", current = artistDiversity, values = EchoBrainArtistDiversity.entries.toList(), valueText = { it.name.lowercase().replaceFirstChar(Char::uppercase) }, valueDescription = { when (it) { EchoBrainArtistDiversity.UNLIMITED -> "Sin límite"; EchoBrainArtistDiversity.BALANCED -> "Evita repeticiones recientes"; EchoBrainArtistDiversity.HIGH -> "Rota también el artista activo antes de repetirlo" } })
    if (dialog == "confirmation") EnumDialog(onDismiss = { dialog = null }, onSelect = { setConfirmationValue(it.name); dialog = null }, title = "Confirmación de escucha", current = confirmation, values = EchoBrainListeningConfirmation.entries.toList(), valueText = { it.name.replace('_', ' ') }, valueDescription = { "Señal positiva después de ${it.percent}%" })
    if (dialog == "learning") EnumDialog(onDismiss = { dialog = null }, onSelect = { setLearningLevelValue(it.name); setDailyLearning(it != EchoBrainLearningLevel.OFF); dialog = null }, title = "Aprendizaje continuo", current = learningLevel, values = EchoBrainLearningLevel.entries.toList(), valueText = { when (it) { EchoBrainLearningLevel.OFF -> "Desactivado"; EchoBrainLearningLevel.CONSERVATIVE -> "Conservador"; EchoBrainLearningLevel.BALANCED -> "Equilibrado"; EchoBrainLearningLevel.ADAPTIVE -> "Adaptativo"; EchoBrainLearningLevel.AGGRESSIVE -> "Profundo" } }, valueDescription = { "Consolida señales localmente una vez al día" })
    if (showWhitelistEditor) TextFieldDialog(onDismiss = { showWhitelistEditor = false }, title = { Text("Pegar o editar artistas") }, initialTextFieldValue = TextFieldValue(serializedWhitelist), placeholder = { Text("Un artista por línea, coma o punto y coma") }, singleLine = false, maxLines = 12, isInputValid = { true }, onDone = { setSerializedWhitelist(EchoBrainArtistWhitelist.serialize(it)); showWhitelistEditor = false })

    fun exportLearning() {
        val body = "Echo Brain / FlowNeuro\nNivel: ${learningLevel.name}\nPerfil local:\n$profileData"
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "application/json"; putExtra(Intent.EXTRA_TEXT, body) }, "Exportar aprendizaje Echo Brain"))
    }

    Column(Modifier.windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Spacer(Modifier.windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top)))
        Text(stringResource(R.string.echo_brain_strict_section_desc), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp, bottom = 20.dp))
        Material3SettingsGroup(title = stringResource(R.string.echo_brain), items = listOf(
            Material3SettingsItem(icon = painterResource(R.drawable.radio), title = { Text("Activar Echo Brain") }, description = { Text("Añade recomendaciones que superen tus filtros") }, trailingContent = { Switch(checked = enabled, onCheckedChange = setEnabled) }, onClick = { setEnabled(!enabled) }),
            Material3SettingsItem(icon = painterResource(R.drawable.tune), title = { Text("Evita música sin relación") }, description = { Text(if (strictAffinity) "Activado: exige artista, álbum o relación local" else "Desactivado: permite candidatas de radio con umbral") }, trailingContent = { Switch(checked = strictAffinity, onCheckedChange = setStrictAffinity) }, onClick = { setStrictAffinity(!strictAffinity) }),
            Material3SettingsItem(icon = painterResource(R.drawable.tune), title = { Text("Similitud mínima") }, description = { Text("${minimumSimilarity}%") }, onClick = { dialog = "similarity" }),
            Material3SettingsItem(icon = painterResource(R.drawable.music_note), title = { Text("Permitir versiones alternativas") }, description = { Text(if (alternatives) "Remixes, directos y acústicas permitidos si pasan filtros" else "Solo grabaciones principales") }, trailingContent = { Switch(checked = alternatives, onCheckedChange = setAlternatives) }, onClick = { setAlternatives(!alternatives) }),
            Material3SettingsItem(icon = painterResource(R.drawable.music_note), title = { Text("Excluir directos y remixes") }, description = { Text(if (excludeLiveRemix) "Bloqueados" else "Permitidos si pasan los demás filtros") }, trailingContent = { Switch(checked = excludeLiveRemix, onCheckedChange = setExcludeLiveRemix) }, onClick = { setExcludeLiveRemix(!excludeLiveRemix) }),
            Material3SettingsItem(icon = painterResource(R.drawable.group), title = { Text("Diversidad de artistas") }, description = { Text(artistDiversity.name) }, onClick = { dialog = "diversity" }),
            Material3SettingsItem(icon = painterResource(R.drawable.group), title = { Text("Lista blanca de artistas") }, description = { Text(if (whitelistEnabled) "Solo ${whitelist.size} artistas guardados" else "Desactivada") }, trailingContent = { Switch(checked = whitelistEnabled, onCheckedChange = setWhitelistEnabled) }, onClick = { setWhitelistEnabled(!whitelistEnabled) }),
            Material3SettingsItem(icon = painterResource(R.drawable.edit), title = { Text("Pegar o editar artistas") }, description = { Text("${whitelist.size} de 20 artistas guardados localmente") }, onClick = { showWhitelistEditor = true }),
            Material3SettingsItem(icon = painterResource(R.drawable.timer), title = { Text("Confirmación de escucha") }, description = { Text(confirmation.name.replace('_', ' ')) }, onClick = { dialog = "confirmation" }),
            Material3SettingsItem(icon = painterResource(R.drawable.refresh), title = { Text("Aprendizaje continuo") }, description = { Text(if (dailyLearning) "${learningLevel.name} · consolida cada día" else "Desactivado") }, trailingContent = { Switch(checked = dailyLearning, onCheckedChange = setDailyLearning) }, onClick = { dialog = "learning" }),
            Material3SettingsItem(icon = painterResource(R.drawable.refresh), title = { Text("Exportar aprendizaje") }, description = { Text("Guarda una copia del conocimiento FlowNeuro local") }, onClick = ::exportLearning),
        ))
        Spacer(Modifier.padding(top = 13.dp))
        Material3SettingsGroup(title = stringResource(R.string.echo_brain_how_it_works), items = listOf(
            Material3SettingsItem(icon = painterResource(R.drawable.tune), title = { Text("Filtro de afinidad") }, description = { Text("Exige el umbral elegido, elimina duplicados y bloquea versiones no permitidas") }),
            Material3SettingsItem(icon = painterResource(R.drawable.refresh), title = { Text("FlowNeuro interno") }, description = { Text("Aprende de escuchas, saltos y señales positivas sin borrar el perfil local") }),
        ))
        Spacer(Modifier.padding(bottom = 16.dp))
    }
    TopAppBar(title = { Text(stringResource(R.string.echo_brain)) }, navigationIcon = { IconButton(onClick = navController::navigateUp, onLongClick = navController::backToMain) { Icon(painterResource(R.drawable.arrow_back), contentDescription = null) } })
}
