package com.huevoman77.cazahosts.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.huevoman77.cazahosts.core.Net
import com.huevoman77.cazahosts.data.CheckerStore
import com.huevoman77.cazahosts.data.ScannerState
import com.huevoman77.cazahosts.data.SubdomainRepository
import com.huevoman77.cazahosts.data.SubdomainResult
import com.huevoman77.cazahosts.dns.DnsType
import com.huevoman77.cazahosts.dns.SubdomainConfig
import com.huevoman77.cazahosts.dns.SubdomainEngine
import com.huevoman77.cazahosts.dns.SubdomainWordlist
import com.huevoman77.cazahosts.ui.components.EmptyState
import com.huevoman77.cazahosts.ui.components.KeyValueRow
import com.huevoman77.cazahosts.ui.components.LabeledSwitch
import com.huevoman77.cazahosts.ui.components.Pill
import com.huevoman77.cazahosts.ui.components.PrimaryButton
import com.huevoman77.cazahosts.ui.components.SecondaryButton
import com.huevoman77.cazahosts.ui.components.SectionCard
import com.huevoman77.cazahosts.ui.components.SliderRow
import com.huevoman77.cazahosts.ui.theme.Amber

private enum class ResolverMode(val label: String) {
    AUTO("Auto"),
    LOCAL("Solo local"),
    CLOUDFLARE("1.1.1.1"),
    GOOGLE("8.8.8.8"),
    QUAD9("9.9.9.9")
}

private enum class ResultFilter(val label: String) {
    TODOS("Todos"),
    CON_WEB("Con web"),
    CON_CNAME("Con CNAME"),
    CON_IPV6("IPv6")
}

@Composable
fun SubdomainsScreen(onOpenScanner: () -> Unit) {
    val context = LocalContext.current
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val progress by SubdomainRepository.progress.collectAsState()
    val results by SubdomainRepository.results.collectAsState()

    var domain by remember { mutableStateOf("") }
    var size by remember { mutableStateOf(SubdomainWordlist.Size.MEDIO) }
    var usePatterns by remember { mutableStateOf(true) }
    var useNumbering by remember { mutableStateOf(false) }
    var depth by remember { mutableStateOf(1f) }
    var customWords by remember { mutableStateOf<List<String>>(emptyList()) }
    var types by remember { mutableStateOf(setOf(DnsType.A)) }
    var concurrency by remember { mutableStateOf(40f) }
    var timeout by remember { mutableStateOf(2500f) }
    var resolverMode by remember { mutableStateOf(ResolverMode.AUTO) }
    var detectWildcard by remember { mutableStateOf(true) }
    var chaseCname by remember { mutableStateOf(true) }
    var probeHttp by remember { mutableStateOf(true) }
    var reverseDns by remember { mutableStateOf(true) }
    var filter by remember { mutableStateOf(ResultFilter.TODOS) }
    var search by remember { mutableStateOf("") }

    val localDns = remember { Net.dnsServers(context) }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
            customWords = text.split('\n', ',', ' ', '\t')
                .map { it.trim().lowercase() }
                .filter { it.isNotEmpty() }
                .distinct()
        } catch (e: Exception) {
            customWords = emptyList()
        }
    }

    val resolvers = when (resolverMode) {
        ResolverMode.AUTO -> (localDns + listOf("1.1.1.1", "8.8.8.8")).distinct()
        ResolverMode.LOCAL -> localDns.ifEmpty { listOf("1.1.1.1") }
        ResolverMode.CLOUDFLARE -> listOf("1.1.1.1")
        ResolverMode.GOOGLE -> listOf("8.8.8.8")
        ResolverMode.QUAD9 -> listOf("9.9.9.9")
    }

    val words = remember(size, customWords, usePatterns, useNumbering, depth) {
        val base = if (customWords.isNotEmpty()) customWords else SubdomainWordlist.words(size)
        val patterns = if (usePatterns) {
            SubdomainWordlist.patterns(base, depth.toInt(), useNumbering, true)
        } else {
            emptyList()
        }
        (base + patterns).distinct()
    }

    val filtered = remember(results, filter, search) {
        results.filter { item ->
            val matchesFilter = when (filter) {
                ResultFilter.TODOS -> true
                ResultFilter.CON_WEB -> item.httpStatus > 0
                ResultFilter.CON_CNAME -> item.cname.isNotEmpty()
                ResultFilter.CON_IPV6 -> item.ipv6.isNotEmpty()
            }
            val q = search.trim().lowercase()
            val matchesSearch = q.isEmpty() || item.host.contains(q) ||
                item.addresses.any { it.contains(q) } || item.cname.contains(q)
            matchesFilter && matchesSearch
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 30.dp)
    ) {
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
                Text(
                    "Subdominios",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    "Fuerza bruta DNS para descubrir hosts que no estan enlazados",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            SectionCard(
                title = "Objetivo",
                subtitle = "Dominio contra el que se probaran los nombres",
                icon = Icons.Outlined.Dns,
                accent = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                OutlinedTextField(
                    value = domain,
                    onValueChange = { domain = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("ejemplo.com") },
                    singleLine = true
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Solo contra dominios que te pertenecen o sobre los que tienes autorizacion.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            SectionCard(
                title = "Diccionario",
                subtitle = "De donde salen los nombres a probar",
                icon = Icons.Outlined.FolderOpen,
                accent = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SubdomainWordlist.Size.entries.forEach { s ->
                        FilterChip(
                            selected = size == s && customWords.isEmpty(),
                            onClick = {
                                size = s
                                customWords = emptyList()
                            },
                            label = { Text(s.label) }
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    if (customWords.isEmpty()) size.description
                    else "Lista propia cargada: ${customWords.size} palabras",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                SecondaryButton(
                    text = if (customWords.isEmpty()) "Cargar mi lista" else "Quitar mi lista",
                    icon = Icons.Outlined.FolderOpen,
                    onClick = {
                        if (customWords.isEmpty()) {
                            filePicker.launch(arrayOf("text/plain", "text/*"))
                        } else {
                            customWords = emptyList()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                LabeledSwitch(
                    title = "Agregar patrones",
                    subtitle = "dev-web, web-prod, qa-01 y combinaciones",
                    checked = usePatterns,
                    onChange = { usePatterns = it }
                )
                LabeledSwitch(
                    title = "Agregar numeracion",
                    subtitle = "web1, web2, app1, app2...",
                    checked = useNumbering,
                    onChange = { useNumbering = it }
                )
                SliderRow(
                    title = "Niveles de combinacion",
                    valueLabel = "${depth.toInt()}",
                    value = depth,
                    range = 1f..3f
                ) { depth = it }
                Spacer(Modifier.height(4.dp))
                Pill(text = "${words.size} nombres a probar", accent = MaterialTheme.colorScheme.secondary)
            }
        }

        item {
            SectionCard(
                title = "Tipos de registro",
                subtitle = "Que se le pregunta al servidor DNS por cada nombre",
                icon = Icons.Outlined.Dns,
                accent = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        DnsType.A to "IPv4",
                        DnsType.AAAA to "IPv6",
                        DnsType.CNAME to "Alias",
                        DnsType.NS to "Servidores",
                        DnsType.MX to "Correo",
                        DnsType.TXT to "Texto",
                        DnsType.SOA to "Zona",
                        DnsType.SRV to "Servicios"
                    ).forEach { (type, label) ->
                        FilterChip(
                            selected = types.contains(type),
                            onClick = {
                                types = if (types.contains(type)) {
                                    if (types.size == 1) types else types - type
                                } else {
                                    types + type
                                }
                            },
                            label = { Text(label) }
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                SliderRow(
                    title = "Consultas simultaneas",
                    valueLabel = "${concurrency.toInt()}",
                    value = concurrency,
                    range = 5f..120f
                ) { concurrency = it }
                SliderRow(
                    title = "Espera maxima",
                    valueLabel = "${timeout.toInt()} ms",
                    value = timeout,
                    range = 500f..6000f,
                    steps = 10
                ) { timeout = it }
                LabeledSwitch(
                    title = "Detectar DNS comodin",
                    subtitle = "Descarta los falsos positivos del dominio",
                    checked = detectWildcard,
                    onChange = { detectWildcard = it }
                )
                LabeledSwitch(
                    title = "Seguir alias CNAME",
                    subtitle = "Resuelve tambien el destino del alias",
                    checked = chaseCname,
                    onChange = { chaseCname = it }
                )
                LabeledSwitch(
                    title = "Revisar el sitio web",
                    subtitle = "Consulta cada subdominio y lee su titulo",
                    checked = probeHttp,
                    onChange = { probeHttp = it }
                )
                LabeledSwitch(
                    title = "Nombre inverso de la IP",
                    subtitle = "Busca el PTR de cada direccion",
                    checked = reverseDns,
                    onChange = { reverseDns = it }
                )
            }
        }

        item {
            SectionCard(
                title = "Servidores DNS",
                subtitle = "A quien se le pregunta",
                icon = Icons.Outlined.Dns,
                accent = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ResolverMode.entries.forEach { mode ->
                        FilterChip(
                            selected = resolverMode == mode,
                            onClick = { resolverMode = mode },
                            label = { Text(mode.label) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                KeyValueRow(
                    key = "En uso",
                    value = resolvers.joinToString(", ")
                )
                if (localDns.isNotEmpty()) {
                    KeyValueRow(key = "DNS de la red", value = localDns.joinToString(", "))
                }
                if (localDns.isEmpty() && resolverMode == ResolverMode.LOCAL) {
                    Text(
                        "Tu red no entrega un DNS propio: se usara 1.1.1.1.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Amber
                    )
                }
            }
        }

        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                if (progress.running) {
                    PrimaryButton(
                        text = "Detener",
                        icon = Icons.Outlined.Cancel,
                        onClick = { SubdomainRepository.stop() },
                        container = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    PrimaryButton(
                        text = "Iniciar fuerza bruta",
                        icon = Icons.Outlined.PlayArrow,
                        onClick = {
                            SubdomainRepository.start(
                                SubdomainConfig(
                                    domain = domain,
                                    words = words,
                                    recordTypes = types,
                                    concurrency = concurrency.toInt(),
                                    resolvers = resolvers,
                                    timeoutMs = timeout.toInt(),
                                    detectWildcard = detectWildcard,
                                    chaseCname = chaseCname,
                                    probeHttp = probeHttp,
                                    reverseDns = reverseDns
                                )
                            )
                        },
                        enabled = domain.isNotBlank() && words.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        if (progress.running || progress.phase.isNotEmpty()) {
            item {
                SectionCard(
                    title = "Progreso",
                    subtitle = progress.phase,
                    icon = Icons.Outlined.PlayArrow,
                    accent = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    KeyValueRow(key = "Probados", value = "${progress.tested} / ${progress.total}")
                    KeyValueRow(key = "Encontrados", value = "${progress.found}")
                    if (progress.running) {
                        KeyValueRow(key = "Transcurrido", value = formatTime(progress.elapsedMs))
                        KeyValueRow(key = "Tiempo estimado", value = formatTime(progress.etaMs))
                    }
                    if (progress.wildcard) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "El dominio usa DNS comodin: se descartaron ${progress.discarded} nombres que en realidad no existen.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Amber
                        )
                    }
                    if (progress.error.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            progress.error,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        if (results.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SecondaryButton(
                        text = "Copiar",
                        icon = Icons.Outlined.ContentCopy,
                        onClick = { clipboard.setText(AnnotatedString(SubdomainRepository.exportText())) },
                        modifier = Modifier.weight(1f)
                    )
                    SecondaryButton(
                        text = "Compartir",
                        icon = Icons.Outlined.Share,
                        onClick = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, SubdomainRepository.exportText())
                            }
                            context.startActivity(Intent.createChooser(intent, "Compartir subdominios"))
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SecondaryButton(
                        text = "Al escaner",
                        icon = Icons.Outlined.TravelExplore,
                        onClick = {
                            ScannerState.setTarget(results.joinToString(",") { it.host })
                            onOpenScanner()
                        },
                        modifier = Modifier.weight(1f)
                    )
                    SecondaryButton(
                        text = "Al checker",
                        icon = Icons.Outlined.FolderOpen,
                        onClick = {
                            CheckerStore.addAll(results.map { it.primaryAddress }.filter { it.isNotEmpty() })
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            item {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text("filtrar por nombre o IP") },
                    singleLine = true
                )
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ResultFilter.entries.forEach { f ->
                        FilterChip(
                            selected = filter == f,
                            onClick = { filter = f },
                            label = { Text(f.label) }
                        )
                    }
                }
            }
            if (filtered.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Outlined.Dns,
                        title = "Sin coincidencias",
                        message = "Ajusta el filtro o la busqueda para ver los subdominios encontrados."
                    )
                }
            } else {
                items(filtered, key = { it.host }) { item ->
                    SubdomainCard(item)
                }
            }
        } else if (!progress.running) {
            item {
                EmptyState(
                    icon = Icons.Outlined.Dns,
                    title = "Todavia no hay subdominios",
                    message = "Escribe un dominio y pulsa Iniciar. El motor prueba el diccionario completo, detecta el DNS comodin y revisa que sitio web expone cada host."
                )
            }
        }
    }
}

@Composable
private fun SubdomainCard(item: SubdomainResult) {
    SectionCard(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp),
        accent = when {
            item.httpStatus in 200..399 -> MaterialTheme.colorScheme.primary
            item.httpStatus > 0 -> Amber
            else -> MaterialTheme.colorScheme.secondary
        }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = item.host,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            if (item.httpStatus > 0) {
                Pill(text = "HTTP ${item.httpStatus}", accent = MaterialTheme.colorScheme.primary)
            }
        }
        if (item.addresses.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = item.addresses.joinToString(", "),
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (item.ipv6.isNotEmpty()) {
            Text(
                text = item.ipv6.joinToString(", "),
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (item.cname.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "CNAME ${item.cname}",
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.secondary
            )
        }
        if (item.reverseName.isNotEmpty()) {
            Text(
                text = "PTR ${item.reverseName}",
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (item.httpTitle.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = item.httpTitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        if (item.records.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            item.records.take(6).forEach { rec ->
                Text(
                    text = "${rec.type}: ${rec.value}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    if (ms <= 0) return "-"
    val total = ms / 1000
    val minutes = total / 60
    val seconds = total % 60
    return if (minutes > 0) "${minutes}m ${seconds}s" else "${seconds}s"
}
