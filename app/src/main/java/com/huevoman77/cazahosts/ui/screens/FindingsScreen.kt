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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.huevoman77.cazahosts.data.ScanRepository
import com.huevoman77.cazahosts.data.Severity
import com.huevoman77.cazahosts.data.VulnFinding
import com.huevoman77.cazahosts.ui.components.EmptyState
import com.huevoman77.cazahosts.ui.components.FindingRow
import com.huevoman77.cazahosts.ui.components.Pill
import com.huevoman77.cazahosts.ui.components.SecondaryButton
import com.huevoman77.cazahosts.ui.components.SectionCard
import com.huevoman77.cazahosts.ui.theme.Coral

@Composable
fun FindingsScreen() {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val hosts by ScanRepository.hosts.collectAsState()
    val summary by ScanRepository.summary.collectAsState()
    var filter by remember { mutableStateOf<Severity?>(null) }

    val all = hosts.flatMap { host -> host.findings.map { host.ip to it } }
        .sortedByDescending { it.second.severityEnum.peso }
    val filtered = if (filter == null) all else all.filter { it.second.severityEnum == filter }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 30.dp)
    ) {
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
                Text(
                    "Hallazgos",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    "Vulnerabilidades y malas prácticas confirmadas en tus equipos",
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
                FilterChip(
                    selected = filter == null,
                    onClick = { filter = null },
                    label = { Text("Todos (${all.size})") }
                )
                Severity.entries.forEach { sev ->
                    val count = all.count { it.second.severityEnum == sev }
                    FilterChip(
                        selected = filter == sev,
                        onClick = { filter = if (filter == sev) null else sev },
                        label = { Text("${sev.label} ($count)") }
                    )
                }
            }
        }

        if (all.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Outlined.Security,
                    title = "Todavía no hay hallazgos",
                    message = "Ejecuta un escaneo desde la pestaña Escanear. Cada puerto abierto se revisa contra la base de CVEs y las pruebas activas."
                )
            }
        } else {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SecondaryButton(
                        text = "Copiar",
                        icon = Icons.Outlined.ContentCopy,
                        onClick = {
                            clipboard.setText(AnnotatedString(buildReport(all)))
                        },
                        modifier = Modifier.weight(1f)
                    )
                    SecondaryButton(
                        text = "Compartir",
                        icon = Icons.Outlined.Download,
                        onClick = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, buildReport(all))
                            }
                            context.startActivity(Intent.createChooser(intent, "Compartir informe"))
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            items(filtered, key = { it.second.id + it.first }) { (ip, finding) ->
                FindingCard(ip = ip, finding = finding)
            }
        }
    }
}

private fun buildReport(items: List<Pair<String, VulnFinding>>): String {
    val sb = StringBuilder()
    sb.appendLine("CazaHosts - Informe de hallazgos")
    sb.appendLine("Generado: ${java.util.Date()}")
    sb.appendLine("Total: ${items.size}")
    sb.appendLine()
    items.forEach { (ip, f) ->
        sb.appendLine("[${f.severityEnum.label}] $ip - ${f.title}")
        if (f.cve.isNotEmpty()) sb.appendLine("   CVE: ${f.cve}  CVSS: ${f.cvss}")
        sb.appendLine("   ${f.detail}")
        if (f.fix.isNotEmpty()) sb.appendLine("   Solución: ${f.fix}")
        sb.appendLine()
    }
    return sb.toString()
}

@Composable
private fun FindingCard(ip: String, finding: VulnFinding) {
    SectionCard(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp),
        accent = when (finding.severityEnum) {
            Severity.CRITICA, Severity.ALTA -> Coral
            else -> MaterialTheme.colorScheme.secondary
        }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Pill(text = ip, accent = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Pill(
                text = finding.severityEnum.label,
                accent = when (finding.severityEnum) {
                    Severity.CRITICA, Severity.ALTA -> Coral
                    else -> MaterialTheme.colorScheme.secondary
                }
            )
            if (finding.port > 0) {
                Spacer(Modifier.width(6.dp))
                Pill(text = "puerto ${finding.port}", accent = MaterialTheme.colorScheme.tertiary)
            }
        }
        Spacer(Modifier.height(10.dp))
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
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
