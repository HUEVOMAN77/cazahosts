package com.huevoman77.cazahosts.ui.screens

import android.content.Intent
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.huevoman77.cazahosts.data.ResultStore
import com.huevoman77.cazahosts.data.ScanRepository
import com.huevoman77.cazahosts.ui.components.EmptyState
import com.huevoman77.cazahosts.ui.components.FindingRow
import com.huevoman77.cazahosts.ui.components.KeyValueRow
import com.huevoman77.cazahosts.ui.components.Pill
import com.huevoman77.cazahosts.ui.components.SecondaryButton
import com.huevoman77.cazahosts.ui.components.SectionCard
import com.huevoman77.cazahosts.ui.components.StatTile
import com.huevoman77.cazahosts.ui.theme.Coral
import com.huevoman77.cazahosts.ui.theme.Mint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Detalle de un escaneo guardado: resultados, hallazgos y exportacion del
 * informe en CSV o JSON.
 */
@Composable
fun SessionDetailScreen(sessionId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val sessions by ScanRepository.sessions.collectAsState()
    val session = sessions.firstOrNull { it.id == sessionId }
    val store = remember(sessionId) { ResultStore(context) }

    if (session == null) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Volver", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("Escaneo", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
            }
            EmptyState(
                icon = Icons.Outlined.Timer,
                title = "Escaneo no disponible",
                message = "Este registro ya no esta en el dispositivo."
            )
        }
        return
    }

    val csv = remember(session.id) { store.exportCsv(session) }
    val json = remember(session.id) { store.exportJson(session) }
    val fmt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US)

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 30.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Volver",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column {
                    Text(
                        "Escaneo guardado",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        fmt.format(Date(session.startedAt)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            SectionCard(
                title = "Resumen",
                subtitle = session.target,
                icon = Icons.Outlined.Timer,
                accent = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile(
                        value = "${session.hostsAlive}",
                        label = "VIVOS",
                        icon = Icons.Outlined.Timer,
                        accent = Mint,
                        modifier = Modifier.weight(1f)
                    )
                    StatTile(
                        value = "${session.portsFound}",
                        label = "PUERTOS",
                        icon = Icons.Outlined.Timer,
                        accent = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.weight(1f)
                    )
                    StatTile(
                        value = "${session.findingsCount}",
                        label = "HALLAZGOS",
                        icon = Icons.Outlined.Timer,
                        accent = Coral,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(10.dp))
                KeyValueRow(key = "Objetivo", value = session.target)
                KeyValueRow(key = "Modo", value = session.mode.ifEmpty { "estandar" })
                KeyValueRow(key = "Revisados", value = "${session.hostsScanned}")
                if (session.finishedAt > 0) {
                    KeyValueRow(key = "Duración", value = "${(session.finishedAt - session.startedAt) / 1000} s")
                }
            }
        }

        item {
            SectionCard(
                title = "Exportar informe",
                subtitle = "CSV para tablas y JSON para procesar",
                icon = Icons.Outlined.Download,
                accent = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryButton(
                        text = "Copiar CSV",
                        icon = Icons.Outlined.ContentCopy,
                        onClick = { clipboard.setText(AnnotatedString(csv)) },
                        modifier = Modifier.weight(1f)
                    )
                    SecondaryButton(
                        text = "Copiar JSON",
                        icon = Icons.Outlined.ContentCopy,
                        onClick = { clipboard.setText(AnnotatedString(json)) },
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryButton(
                        text = "Compartir",
                        icon = Icons.Outlined.Share,
                        onClick = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, csv)
                            }
                            context.startActivity(Intent.createChooser(intent, "Compartir informe"))
                        },
                        modifier = Modifier.weight(1f)
                    )
                    SecondaryButton(
                        text = "Borrar",
                        icon = Icons.Outlined.Delete,
                        onClick = {
                            ScanRepository.deleteSession(session.id)
                            onBack()
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        if (session.hosts.isNotEmpty()) {
            item {
                Text(
                    "Hosts (${session.hosts.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 6.dp)
                )
            }
            items(session.hosts, key = { it.ip }) { host ->
                SectionCard(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    accent = when {
                        host.findings.any { it.severityEnum.peso >= 4 } -> Coral
                        host.openPorts.isNotEmpty() -> MaterialTheme.colorScheme.secondary
                        else -> MaterialTheme.colorScheme.primary
                    }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = host.ip,
                            style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Pill(text = "${host.openPorts.size} puertos", accent = MaterialTheme.colorScheme.secondary)
                    }
                    if (host.hostname.isNotEmpty()) {
                        Text(
                            text = host.hostname,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (host.openPorts.isNotEmpty()) {
                        Text(
                            text = host.openPorts.joinToString(", ") { "${it.port}/${it.service}" },
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    host.findings.forEach { finding ->
                        Spacer(Modifier.height(4.dp))
                        FindingRow(
                            title = finding.title,
                            detail = finding.detail,
                            severity = finding.severityEnum,
                            cve = finding.cve,
                            cvss = finding.cvss
                        )
                    }
                }
            }
        }
    }
}
