package com.huevoman77.cazahosts.ui.screens

import android.content.Intent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.huevoman77.cazahosts.generators.Generators
import com.huevoman77.cazahosts.ui.components.LabeledSwitch
import com.huevoman77.cazahosts.ui.components.Pill
import com.huevoman77.cazahosts.ui.components.PrimaryButton
import com.huevoman77.cazahosts.ui.components.SecondaryButton
import com.huevoman77.cazahosts.ui.components.SectionCard
import com.huevoman77.cazahosts.ui.components.SliderRow

private enum class GenType(val label: String) {
    CONTRASENAS("Contraseñas"),
    SUBDOMINIOS("Subdominios"),
    RANGOS("Rangos IP"),
    SUBNETES("Subredes"),
    USUARIOS("Usuarios"),
    DICCIONARIOS("Diccionarios")
}

@Composable
fun GeneratorsScreen() {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var type by remember { mutableStateOf(GenType.CONTRASENAS) }
    var results by remember { mutableStateOf<List<String>>(emptyList()) }

    var count by remember { mutableStateOf(20f) }
    var length by remember { mutableStateOf(14f) }
    var useUpper by remember { mutableStateOf(true) }
    var useDigits by remember { mutableStateOf(true) }
    var useSymbols by remember { mutableStateOf(true) }
    var avoidSimilar by remember { mutableStateOf(true) }
    var pronounceable by remember { mutableStateOf(false) }
    var wordsInput by remember { mutableStateOf("") }
    var domainsInput by remember { mutableStateOf("com,net,org,io,local") }
    var depth by remember { mutableStateOf(1f) }
    var cidrInput by remember { mutableStateOf("192.168.1.0/24") }
    var newPrefix by remember { mutableStateOf(28f) }
    var minLen by remember { mutableStateOf(8f) }
    var maxLen by remember { mutableStateOf(14f) }

    fun generate() {
        results = when (type) {
            GenType.CONTRASENAS -> Generators.passwords(
                count = count.toInt(),
                length = length.toInt(),
                useUpper = useUpper,
                useDigits = useDigits,
                useSymbols = useSymbols,
                avoidSimilar = avoidSimilar,
                pronounceable = pronounceable
            )
            GenType.SUBDOMINIOS -> Generators.subdomains(
                words = wordsInput.split(',').map { it.trim() }.filter { it.isNotEmpty() },
                suffixList = domainsInput.split(',').map { it.trim() }.filter { it.isNotEmpty() },
                depth = depth.toInt()
            )
            GenType.RANGOS -> Generators.ipRanges(
                seed = System.currentTimeMillis(),
                count = count.toInt(),
                maxHosts = 254
            )
            GenType.SUBNETES -> Generators.subnetBlocks(cidrInput, newPrefix.toInt())
            GenType.USUARIOS -> Generators.users(count.toInt())
            GenType.DICCIONARIOS -> Generators.wordlist(
                count = count.toInt(),
                minLength = minLen.toInt(),
                maxLength = maxLen.toInt()
            )
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 30.dp)
    ) {
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
                Text(
                    "Generadores",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    "Listas listas para usar en tus pruebas y auditorías autorizadas",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GenType.entries.forEach { t ->
                    FilterChip(
                        selected = type == t,
                        onClick = {
                            type = t
                            results = emptyList()
                        },
                        label = { Text(t.label) }
                    )
                }
            }
        }

        item {
            SectionCard(
                title = "Opciones",
                subtitle = "Ajusta los parámetros del generador",
                icon = Icons.Outlined.AutoAwesome,
                accent = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                when (type) {
                    GenType.CONTRASENAS -> {
                        SliderRow("Cuántas", "${count.toInt()}", count, 1f..500f) { count = it }
                        SliderRow("Longitud", "${length.toInt()}", length, 6f..64f) { length = it }
                        LabeledSwitch("Mayúsculas", checked = useUpper, onChange = { useUpper = it })
                        LabeledSwitch("Números", checked = useDigits, onChange = { useDigits = it })
                        LabeledSwitch("Símbolos", checked = useSymbols, onChange = { useSymbols = it })
                        LabeledSwitch("Evitar caracteres ambiguos", "Il1 O0", checked = avoidSimilar, onChange = { avoidSimilar = it })
                        LabeledSwitch("Pronunciable", "Fácil de dictar", checked = pronounceable, onChange = { pronounceable = it })
                    }
                    GenType.SUBDOMINIOS -> {
                        OutlinedTextField(
                            value = wordsInput,
                            onValueChange = { wordsInput = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("palabras base: dev, staging, api") },
                            singleLine = true
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = domainsInput,
                            onValueChange = { domainsInput = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("dominios: com, net, org") },
                            singleLine = true
                        )
                        Spacer(Modifier.height(8.dp))
                        SliderRow("Niveles", "${depth.toInt()}", depth, 1f..3f) { depth = it }
                    }
                    GenType.RANGOS -> {
                        SliderRow("Cuántos rangos", "${count.toInt()}", count, 1f..100f) { count = it }
                        Text(
                            "Cada rango tiene hasta 254 direcciones, ideal para barrer redes completas.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    GenType.SUBNETES -> {
                        OutlinedTextField(
                            value = cidrInput,
                            onValueChange = { cidrInput = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("10.0.0.0/22") },
                            singleLine = true
                        )
                        Spacer(Modifier.height(8.dp))
                        SliderRow("Prefijo de salida", "/${newPrefix.toInt()}", newPrefix, 8f..32f) { newPrefix = it }
                    }
                    GenType.USUARIOS -> {
                        SliderRow("Cuántos", "${count.toInt()}", count, 1f..200f) { count = it }
                    }
                    GenType.DICCIONARIOS -> {
                        SliderRow("Cuántos", "${count.toInt()}", count, 1f..500f) { count = it }
                        SliderRow("Longitud mínima", "${minLen.toInt()}", minLen, 4f..20f) { minLen = it }
                        SliderRow("Longitud máxima", "${maxLen.toInt()}", maxLen, 6f..32f) { maxLen = it }
                    }
                }
                Spacer(Modifier.height(14.dp))
                PrimaryButton(
                    text = "Generar",
                    icon = Icons.Outlined.AutoAwesome,
                    onClick = { generate() },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (results.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SecondaryButton(
                        text = "Copiar",
                        icon = Icons.Outlined.ContentCopy,
                        onClick = { clipboard.setText(AnnotatedString(results.joinToString("\n"))) },
                        modifier = Modifier.weight(1f)
                    )
                    SecondaryButton(
                        text = "Compartir",
                        icon = Icons.Outlined.Share,
                        onClick = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, results.joinToString("\n"))
                            }
                            context.startActivity(Intent.createChooser(intent, "Compartir lista"))
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            item {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Text(
                        "${results.size} resultados",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
            itemsIndexed(results.take(300)) { index, value ->
                SectionCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 3.dp)) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text(
                            text = "${index + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 10.dp)
                        )
                        Text(
                            text = value,
                            style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}
