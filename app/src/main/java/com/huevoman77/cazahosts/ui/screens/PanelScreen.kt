package com.huevoman77.cazahosts.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Rocket
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.huevoman77.cazahosts.core.Net
import com.huevoman77.cazahosts.data.AppSettings
import com.huevoman77.cazahosts.data.ScanRepository
import com.huevoman77.cazahosts.ui.components.Divider
import com.huevoman77.cazahosts.ui.components.FindingRow
import com.huevoman77.cazahosts.ui.components.GradientHeader
import com.huevoman77.cazahosts.ui.components.KeyValueRow
import com.huevoman77.cazahosts.ui.components.Pill
import com.huevoman77.cazahosts.ui.components.SectionCard
import com.huevoman77.cazahosts.ui.components.StatTile
import com.huevoman77.cazahosts.ui.theme.Coral
import com.huevoman77.cazahosts.ui.theme.Mint
import com.huevoman77.cazahosts.util.CrashLog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PanelScreen(
    settings: AppSettings,
    onOpenScanner: () -> Unit,
    onOpenFindings: () -> Unit,
    onOpenSettings: () -> Unit = {}
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val hosts by ScanRepository.hosts.collectAsState()
    val progress by ScanRepository.progress.collectAsState()
    val summary by ScanRepository.summary.collectAsState()
    val sessions by ScanRepository.sessions.collectAsState()
    var lastError by remember { mutableStateOf(CrashLog.last(context)) }

    val alive = hosts.count { it.alive }
    val ports = hosts.sumOf { it.openPorts.size }
    val findings = hosts.sumOf { it.findings.size }
    val topFindings = hosts
        .flatMap { host -> host.findings.map { host.ip to it } }
        .sortedByDescending { it.second.severityEnum.peso }
        .take(4)

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 28.dp)
    ) {
        item {
            GradientHeader(
                title = "CazaHosts",
                subtitle = "Localiza hosts, puertos abiertos y riesgos reales en tu red",
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatTile(
                    value = "$alive",
                    label = "HOSTS VIVOS",
                    icon = Icons.Outlined.Router,
                    accent = Mint,
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    value = "$ports",
                    label = "PUERTOS",
                    icon = Icons.Outlined.Dns,
                    accent = MaterialTheme.colorScheme.secondary,
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
                StatTile(
                    value = "$findings",
                    label = "HALLAZGOS",
                    icon = Icons.Outlined.Security,
                    accent = Coral,
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    value = if (summary != null) "${(summary!!.durationMs / 1000)} s" else "--",
                    label = "DURACIÓN",
                    icon = Icons.Outlined.Timer,
                    accent = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            SectionCard(
                title = "Escaneo rápido",
                subtitle = "Elige el objetivo y arranca. Todo ocurre desde este dispositivo.",
                icon = Icons.Outlined.Rocket,
                accent = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                val localSubnet = Net.localSubnet(context)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill(text = localSubnet ?: "Mi red", accent = MaterialTheme.colorScheme.primary)
                    Pill(text = "Sin root", accent = MaterialTheme.colorScheme.secondary)
                    Pill(text = "Sin instalar nada", accent = MaterialTheme.colorScheme.tertiary)
                }
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Estado del motor",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = progress.phase,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            Icons.Outlined.Settings,
                            contentDescription = "Ajustes",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    com.huevoman77.cazahosts.ui.components.PrimaryButton(
                        text = "Iniciar escaneo",
                        icon = Icons.Outlined.Rocket,
                        onClick = onOpenScanner,
                        modifier = Modifier.weight(1f)
                    )
                    com.huevoman77.cazahosts.ui.components.SecondaryButton(
                        text = "Hallazgos",
                        icon = Icons.Outlined.Security,
                        onClick = onOpenFindings,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        if (lastError.isNotEmpty()) {
            item {
                SectionCard(
                    title = "Último error",
                    subtitle = "La app se cerró inesperadamente. Copiá el detalle para reportarlo.",
                    icon = Icons.Outlined.BugReport,
                    accent = Coral,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = lastError.lineSequence().take(4).joinToString("\n"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        com.huevoman77.cazahosts.ui.components.SecondaryButton(
                            text = "Copiar",
                            icon = Icons.Outlined.ContentCopy,
                            onClick = { clipboard.setText(AnnotatedString(lastError)) },
                            modifier = Modifier.weight(1f)
                        )
                        com.huevoman77.cazahosts.ui.components.SecondaryButton(
                            text = "Borrar",
                            icon = Icons.Outlined.Delete,
                            onClick = {
                                CrashLog.clear(context)
                                lastError = ""
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        if (topFindings.isNotEmpty()) {
            item {
                SectionCard(
                    title = "Riesgos destacados",
                    subtitle = "Lo más serio que encontró el último escaneo",
                    icon = Icons.Outlined.Security,
                    accent = Coral,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    topFindings.forEach { (ip, finding) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = ip,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.width(96.dp)
                            )
                            FindingRow(
                                title = finding.title,
                                detail = if (finding.cve.isNotEmpty()) finding.cve else finding.detail,
                                severity = finding.severityEnum,
                                cve = finding.cve,
                                cvss = finding.cvss,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        if (sessions.isNotEmpty()) {
            item {
                SectionCard(
                    title = "Escaneos anteriores",
                    subtitle = "Historial guardado en el dispositivo",
                    icon = Icons.Outlined.Timer,
                    accent = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    val fmt = SimpleDateFormat("dd/MM HH:mm", Locale.US)
                    sessions.take(6).forEachIndexed { index, session ->
                        if (index > 0) Divider()
                        KeyValueRow(
                            key = fmt.format(Date(session.startedAt)),
                            value = session.target.take(22),
                            valueColor = MaterialTheme.colorScheme.onSurface
                        )
                        KeyValueRow(
                            key = "Resultado",
                            value = "${session.hostsAlive} vivos · ${session.portsFound} puertos · ${session.findingsCount} hallazgos",
                            valueColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
