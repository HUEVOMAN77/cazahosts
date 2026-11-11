package com.huevoman77.cazahosts.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lan
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.huevoman77.cazahosts.data.HostResult
import com.huevoman77.cazahosts.data.Severity
import com.huevoman77.cazahosts.data.WatchedHost
import com.huevoman77.cazahosts.ui.theme.severityColor

@Composable
fun HostCard(
    host: HostResult,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val maxSeverity = host.findings.maxByOrNull { it.severityEnum.peso }?.severityEnum ?: Severity.INFO
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.13f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.Router,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(21.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = host.ip,
                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (host.findings.isNotEmpty()) {
                        Spacer(Modifier.width(8.dp))
                        RiskBadge(maxSeverity)
                    }
                }
                Spacer(Modifier.size(2.dp))
                Text(
                    text = buildString {
                        if (host.hostname.isNotEmpty()) append(host.hostname).append(" · ")
                        append(host.vendor.ifEmpty { "dispositivo desconocido" })
                        if (host.os.isNotEmpty()) append(" · ").append(host.os)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (host.openPorts.isNotEmpty()) {
                    Spacer(Modifier.size(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        host.openPorts.take(4).forEach { port ->
                            Pill(
                                text = "${port.port} ${port.service}",
                                accent = MaterialTheme.colorScheme.secondary
                            )
                        }
                        if (host.openPorts.size > 4) {
                            Pill(text = "+${host.openPorts.size - 4}", accent = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${host.latencyMs} ms",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${host.openPorts.size} puertos",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun WatchedCard(
    host: WatchedHost,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val statusColor: Color = if (host.alive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = host.ip,
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (host.alive) {
                        "En línea · ${host.portsOpen} puertos abiertos"
                    } else {
                        "Sin respuesta"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = statusColor
                )
            }
            if (host.history.isNotEmpty()) {
                MiniSparkline(host.history, Modifier.width(54.dp).height(22.dp), statusColor)
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = "Quitar",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onRemove() }
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
fun MiniSparkline(values: List<Long>, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    val aliveCount = values.size
    androidx.compose.foundation.Canvas(modifier = modifier) {
        if (aliveCount == 0) return@Canvas
        val barWidth = size.width / aliveCount
        values.forEachIndexed { index, _ ->
            val active = index >= aliveCount - minOf(aliveCount, 20)
            drawRect(
                color = if (active) color else color.copy(alpha = 0.22f),
                topLeft = androidx.compose.ui.geometry.Offset(index * barWidth, 0f),
                size = androidx.compose.ui.geometry.Size((barWidth - 1f).coerceAtLeast(1f), size.height)
            )
        }
    }
}

@Composable
fun FindingRow(
    title: String,
    detail: String,
    severity: Severity,
    cve: String,
    cvss: Double,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val color = severityColor(severity)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            if (detail.isNotEmpty()) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (cve.isNotEmpty() || cvss > 0) {
                Spacer(Modifier.size(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (cve.isNotEmpty()) Pill(text = cve, accent = color)
                    if (cvss > 0) Pill(text = "CVSS ${"%.1f".format(cvss)}", accent = color)
                }
            }
        }
    }
}
