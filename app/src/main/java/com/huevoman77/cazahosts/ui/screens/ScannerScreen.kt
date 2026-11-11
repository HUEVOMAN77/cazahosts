package com.huevoman77.cazahosts.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.huevoman77.cazahosts.core.Net
import com.huevoman77.cazahosts.data.AppSettings
import com.huevoman77.cazahosts.data.HostResult
import com.huevoman77.cazahosts.data.ScanRepository
import com.huevoman77.cazahosts.scan.Fingerprint
import com.huevoman77.cazahosts.scan.ScanConfig
import com.huevoman77.cazahosts.scan.ScanMode
import com.huevoman77.cazahosts.ui.components.Divider
import com.huevoman77.cazahosts.ui.components.FindingRow
import com.huevoman77.cazahosts.ui.components.HostCard
import com.huevoman77.cazahosts.ui.components.KeyValueRow
import com.huevoman77.cazahosts.ui.components.LabeledSwitch
import com.huevoman77.cazahosts.ui.components.Pill
import com.huevoman77.cazahosts.ui.components.PrimaryButton
import com.huevoman77.cazahosts.ui.components.SecondaryButton
import com.huevoman77.cazahosts.ui.components.SliderRow
import com.huevoman77.cazahosts.ui.components.StatTile
import com.huevoman77.cazahosts.ui.theme.Coral
import com.huevoman77.cazahosts.ui.theme.Mint

@Composable
fun ScannerScreen(settings: AppSettings) {
    val context = LocalContext.current
    val prefs by settings.state.collectAsState()
    val progress by ScanRepository.progress.collectAsState()
    val hosts by ScanRepository.hosts.collectAsState()

    var target by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(ScanMode.HOSTS_PORTS) }
    var portPreset by remember { mutableStateOf(prefs.portPreset) }
    var customPorts by remember { mutableStateOf("") }
    var concurrency by remember { mutableStateOf(prefs.concurrency.toFloat()) }
    var timeout by remember { mutableStateOf(prefs.timeoutMs.toFloat()) }
    var showAdvanced by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<HostResult?>(null) }
    var expandedHost by remember { mutableStateOf<HostResult?>(null) }

    val running = progress.running
    val localSubnet = remember { Net.localSubnet(context) }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 30.dp)
    ) {
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
                Text(
                    "Escanear",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    "Objetivo, alcance y opciones del barrido",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            com.huevoman77.cazahosts.ui.components.SectionCard(
                title = "Objetivo",
                subtitle = "Acepta CIDR, rango, comodín, un nombre de host o lista separada por comas",
                icon = Icons.Outlined.Search
            ) {
                OutlinedTextField(
                    value = target,
                    onValueChange = { target = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    placeholder = { Text("192.168.1.0/24  o  10.0.0.1-254  o  10.0.0.*") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace)
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = target == localSubnet && localSubnet != null,
                        onClick = { localSubnet?.let { target = it } },
                        label = { Text(localSubnet ?: "Mi red") }
                    )
                    FilterChip(
                        selected = false,
                        onClick = { target = "10.0.0.1-254" },
                        label = { Text("Rango") }
                    )
                    FilterChip(
                        selected = false,
                        onClick = { target = "192.168.0.0/16" },
                        label = { Text("/16 completo") }
                    )
                    FilterChip(
                        selected = false,
                        onClick = { target = "" },
                        label = { Text("Limpiar") }
                    )
                }
            }
        }

        item {
            com.huevoman77.cazahosts.ui.components.SectionCard(
                title = "Modo de escaneo",
                subtitle = mode.description,
                icon = Icons.Outlined.TravelExplore,
                accent = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ScanMode.entries.forEach { m ->
                        FilterChip(
                            selected = mode == m,
                            onClick = { mode = m },
                            label = { Text(m.label) }
                        )
                    }
                }
            }
        }

        item {
            com.huevoman77.cazahosts.ui.components.SectionCard(
                title = "Puertos a revisar",
                subtitle = "Qué puertos se prueban en cada host vivo",
                icon = Icons.Outlined.BugReport,
                accent = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("comunes" to "Comunes", "expandido" to "Expandido", "todos" to "1-65535", "personalizado" to "Personalizado")
                        .forEach { (key, label) ->
                            FilterChip(
                                selected = portPreset == key,
                                onClick = {
                                    portPreset = key
                                    settings.update { it.copy(portPreset = key) }
                                },
                                label = { Text(label) }
                            )
                        }
                }
                if (portPreset == "personalizado") {
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = customPorts,
                        onValueChange = { customPorts = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("22,80,443,8000-8010") },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace)
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill(text = "${Fingerprint.topPortsList().size} puertos base", accent = MaterialTheme.colorScheme.primary)
                    if (portPreset == "todos") Pill(text = "65535 por host", accent = Coral)
                }
            }
        }

        item {
            com.huevoman77.cazahosts.ui.components.SectionCard(
                title = "Motor",
                subtitle = "Velocidad, tiempo de espera y comprobaciones",
                icon = Icons.Outlined.TravelExplore,
                accent = Mint,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                SliderRow(
                    title = "Hilos simultáneos",
                    valueLabel = "${concurrency.toInt()}",
                    value = concurrency,
                    range = 8f..512f,
                    steps = 0
                ) { value ->
                    concurrency = value
                    settings.update { it.copy(concurrency = value.toInt()) }
                }
                SliderRow(
                    title = "Espera por intento",
                    valueLabel = "${timeout.toInt()} ms",
                    value = timeout,
                    range = 150f..3000f,
                    steps = 0
                ) { value ->
                    timeout = value
                    settings.update { it.copy(timeoutMs = value.toInt()) }
                }
                Divider()
                LabeledSwitch(
                    title = "Capturar banners",
                    subtitle = "Identifica el servicio y la versión exacta",
                    checked = prefs.grabBanner,
                    onChange = { value -> settings.update { it.copy(grabBanner = value) } }
                )
                LabeledSwitch(
                    title = "Auditoría activa",
                    subtitle = "Comprueba FTP anónimo, Redis abierto, rutas sensibles y cabeceras",
                    checked = prefs.activeChecks,
                    onChange = { value -> settings.update { it.copy(activeChecks = value) } }
                )
                LabeledSwitch(
                    title = "Usar tabla ARP",
                    subtitle = "Detecta equipos de la red local aunque no abran puertos",
                    checked = prefs.useArp,
                    onChange = { value -> settings.update { it.copy(useArp = value) } }
                )
                LabeledSwitch(
                    title = "DNS inverso",
                    subtitle = "Resuelve el nombre de cada equipo encontrado",
                    checked = prefs.reverseDns,
                    onChange = { value -> settings.update { it.copy(reverseDns = value) } }
                )
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (running) {
                    SecondaryButton(
                        text = "Detener",
                        icon = Icons.Outlined.Stop,
                        onClick = { ScanRepository.stop() },
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    PrimaryButton(
                        text = "Iniciar escaneo",
                        icon = Icons.Outlined.PlayArrow,
                        onClick = {
                            ScanRepository.start(
                                ScanConfig(
                                    targets = target,
                                    mode = mode,
                                    portPreset = portPreset,
                                    customPorts = customPorts,
                                    concurrency = concurrency.toInt(),
                                    timeoutMs = timeout.toInt(),
                                    grabBanner = prefs.grabBanner,
                                    activeChecks = prefs.activeChecks,
                                    useArp = prefs.useArp,
                                    reverseDns = prefs.reverseDns
                                )
                            )
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        if (running || progress.scanned > 0) {
            item {
                com.huevoman77.cazahosts.ui.components.SectionCard(
                    title = if (running) "Escaneando" else "Último escaneo",
                    subtitle = progress.phase,
                    icon = Icons.Outlined.TravelExplore,
                    accent = Mint,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    val fraction = if (progress.total > 0) {
                        (progress.scanned.toFloat() / progress.total.toFloat()).coerceIn(0f, 1f)
                    } else 0f
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatTile(
                            value = "${progress.scanned}",
                            label = "REVISADAS",
                            icon = Icons.Outlined.Search,
                            accent = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.weight(1f)
                        )
                        StatTile(
                            value = "${progress.alive}",
                            label = "VIVAS",
                            icon = Icons.Outlined.TravelExplore,
                            accent = Mint,
                            modifier = Modifier.weight(1f)
                        )
                        StatTile(
                            value = "${progress.portsFound}",
                            label = "PUERTOS",
                            icon = Icons.Outlined.BugReport,
                            accent = Coral,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (progress.currentIp.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Analizando ${progress.currentIp}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (hosts.isNotEmpty()) {
            item {
                Text(
                    "Hosts encontrados (${hosts.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 8.dp)
                )
            }
            items(hosts, key = { it.ip }) { host ->
                HostCard(
                    host = host,
                    onClick = { expandedHost = host },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
        }
    }

    val detail = expandedHost
    if (detail != null) {
        HostDetailSheet(
            host = detail,
            onDismiss = { expandedHost = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HostDetailSheet(
    host: HostResult,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 40.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            host.ip,
                            style = MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = host.hostname.ifEmpty { "sin nombre de host" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "Cerrar",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(14.dp))
                KeyValueRow("Latencia", "${host.latencyMs} ms")
                KeyValueRow("Fabricante", host.vendor.ifEmpty { "desconocido" })
                KeyValueRow("Sistema", host.os.ifEmpty { "desconocido" })
                KeyValueRow("MAC", host.mac.ifEmpty { "no disponible" })
                KeyValueRow("Puertos abiertos", "${host.openPorts.size}")
                KeyValueRow("Hallazgos", "${host.findings.size}")
                Spacer(Modifier.height(12.dp))
            }

            if (host.openPorts.isNotEmpty()) {
                item {
                    Text("Puertos", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(6.dp))
                }
                items(host.openPorts, key = { it.port }) { port ->
                    Column(modifier = Modifier.padding(vertical = 5.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${port.port}",
                                style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.width(56.dp)
                            )
                            Text(
                                text = port.service,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            if (port.tls) Pill(text = port.tlsVersion.ifEmpty { "TLS" }, accent = Mint)
                        }
                        if (port.version.isNotEmpty()) {
                            Text(
                                text = "${port.product} ${port.version}".trim(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 56.dp)
                            )
                        }
                        if (port.certDaysLeft >= 0) {
                            Text(
                                text = "Certificado: ${if (port.certDaysLeft >= 0) "${port.certDaysLeft} días restantes" else "vencido"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (port.certDaysLeft < 30) Coral else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 56.dp)
                            )
                        }
                    }
                }
            }

            if (host.findings.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Hallazgos",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(4.dp))
                }
                items(host.findings) { finding ->
                    FindingRow(
                        title = finding.title,
                        detail = finding.detail,
                        severity = finding.severityEnum,
                        cve = finding.cve,
                        cvss = finding.cvss
                    )
                    if (finding.fix.isNotEmpty()) {
                        Text(
                            text = "Solución: ${finding.fix}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 18.dp, bottom = 8.dp)
                        )
                    }
                }
            }
        }
    }
}
