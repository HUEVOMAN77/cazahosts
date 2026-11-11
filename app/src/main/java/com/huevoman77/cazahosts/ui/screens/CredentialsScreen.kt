package com.huevoman77.cazahosts.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.huevoman77.cazahosts.creds.CredentialAuditor
import com.huevoman77.cazahosts.creds.CredResult
import com.huevoman77.cazahosts.creds.CredTarget
import com.huevoman77.cazahosts.data.AppSettings
import com.huevoman77.cazahosts.data.ScanRepository
import com.huevoman77.cazahosts.ui.components.Divider
import com.huevoman77.cazahosts.ui.components.LabeledSwitch
import com.huevoman77.cazahosts.ui.components.Pill
import com.huevoman77.cazahosts.ui.components.PrimaryButton
import com.huevoman77.cazahosts.ui.components.SecondaryButton
import com.huevoman77.cazahosts.ui.components.SectionCard
import com.huevoman77.cazahosts.ui.components.SliderRow
import com.huevoman77.cazahosts.ui.theme.Coral
import com.huevoman77.cazahosts.ui.theme.Mint
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
fun CredentialsScreen(settings: AppSettings) {
    val prefs by settings.state.collectAsState()
    val hosts by ScanRepository.hosts.collectAsState()
    val scope = rememberCoroutineScope()
    var users by remember { mutableStateOf("") }
    var passwords by remember { mutableStateOf("") }
    var manual by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<CredResult>>(emptyList()) }
    var running by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }

    val autoTargets = hosts.flatMap { host ->
        host.openPorts.filter { port ->
            port.service == "ftp" || port.service == "telnet" || port.service.startsWith("http")
        }.map { port ->
            val protocol = when {
                port.service == "ftp" -> "ftp"
                port.service == "telnet" -> "telnet"
                else -> "http"
            }
            CredTarget(host.ip, port.port, protocol)
        }
    }.distinctBy { "${it.host}:${it.port}" }

    val manualTargets = manual.split(',', ' ', '\n').map { it.trim() }.filter { it.isNotEmpty() }.mapNotNull { entry ->
        val parts = entry.split(':')
        val host = parts.getOrNull(0) ?: return@mapNotNull null
        val port = parts.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null
        val protocol = parts.getOrNull(2) ?: when (port) {
            21 -> "ftp"
            23 -> "telnet"
            else -> "http"
        }
        CredTarget(host, port, protocol)
    }

    val targets = (autoTargets + manualTargets).distinctBy { "${it.host}:${it.port}:${it.protocol}" }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 30.dp)
    ) {
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
                Text(
                    "Credenciales",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    "Prueba de contraseñas por defecto en equipos de tu red",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            SectionCard(
                title = "Uso autorizado",
                subtitle = "Sólo sobre equipos que administras o con permiso explícito",
                icon = Icons.Outlined.WarningAmber,
                accent = Coral
            ) {
                Text(
                    "Esta función verifica credenciales débiles o de fábrica. El uso sin autorización en equipos de terceros es ilegal en la mayoría de los países. Activa la casilla para confirmar que tienes permiso del propietario de la red.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                LabeledSwitch(
                    title = "Confirmo que estoy autorizado",
                    subtitle = "Requiere para poder iniciar la prueba",
                    checked = prefs.attestation,
                    onChange = { value -> settings.update { it.copy(attestation = value) } }
                )
            }
        }

        item {
            SectionCard(
                title = "Objetivos",
                subtitle = "Se detectan solos en el último escaneo; también puedes escribir host:puerto:protocolo",
                icon = Icons.Outlined.Key,
                accent = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                OutlinedTextField(
                    value = manual,
                    onValueChange = { manual = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("192.168.1.1:23:telnet") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace)
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill(text = "${targets.size} objetivos", accent = MaterialTheme.colorScheme.primary)
                    Pill(text = "ritmo ${prefs.credRatePerSec}/s", accent = Mint)
                    Pill(text = "máx ${prefs.credMaxPerHost} por host", accent = MaterialTheme.colorScheme.tertiary)
                }
            }
        }

        item {
            SectionCard(
                title = "Diccionario",
                subtitle = "Vacío = usa la lista de contraseñas de fábrica más comunes",
                icon = Icons.Outlined.Key,
                accent = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                OutlinedTextField(
                    value = users,
                    onValueChange = { users = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("usuarios: admin, root") },
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = passwords,
                    onValueChange = { passwords = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("contraseñas: admin, 1234") },
                    singleLine = true
                )
                Spacer(Modifier.height(12.dp))
                SliderRow(
                    title = "Intentos por segundo",
                    valueLabel = "${prefs.credRatePerSec}",
                    value = prefs.credRatePerSec.toFloat(),
                    range = 1f..20f,
                    steps = 0
                ) { value -> settings.update { it.copy(credRatePerSec = value.toInt()) } }
                SliderRow(
                    title = "Máximo por equipo",
                    valueLabel = "${prefs.credMaxPerHost}",
                    value = prefs.credMaxPerHost.toFloat(),
                    range = 1f..100f,
                    steps = 0
                ) { value -> settings.update { it.copy(credMaxPerHost = value.toInt()) } }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (running) {
                        SecondaryButton(
                            text = "Detener",
                            icon = Icons.Outlined.Stop,
                            onClick = {
                                job?.cancel()
                                running = false
                            },
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        PrimaryButton(
                            text = "Iniciar prueba",
                            icon = Icons.Outlined.PlayArrow,
                            onClick = {
                                results = emptyList()
                                running = true
                                val auditor = CredentialAuditor(prefs.credRatePerSec, prefs.credMaxPerHost)
                                val userList = users.split(',', ' ', ';').map { it.trim() }.filter { it.isNotEmpty() }
                                val passList = passwords.split(',', ' ', ';').map { it.trim() }.filter { it.isNotEmpty() }
                                job = scope.launch {
                                    val out = auditor.audit(targets, userList, passList) { result ->
                                        results = results + result
                                    }
                                    results = out
                                    running = false
                                }
                            },
                            enabled = prefs.attestation && targets.isNotEmpty() && !running,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                if (!prefs.attestation) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Confirma la autorización arriba para habilitar el botón.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Coral
                    )
                }
            }
        }

        if (results.isNotEmpty()) {
            item {
                Text(
                    "Intentos (${results.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 8.dp)
                )
            }
            items(results.take(200)) { result ->
                SectionCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 3.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (result.success) Icons.Outlined.CheckCircle else Icons.Outlined.Key,
                            contentDescription = null,
                            tint = if (result.success) Mint else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Column(modifier = Modifier.padding(start = 10.dp)) {
                            Text(
                                text = "${result.host}:${result.port} · ${result.protocol}",
                                style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (result.success) {
                                    "Acceso con ${result.user} / ${result.password}"
                                } else {
                                    "${result.user} / ${result.password}: ${result.detail}"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (result.success) Mint else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
