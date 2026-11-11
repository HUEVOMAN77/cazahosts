package com.huevoman77.cazahosts.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.huevoman77.cazahosts.BuildConfig
import com.huevoman77.cazahosts.data.AppSettings
import com.huevoman77.cazahosts.data.ScanRepository
import com.huevoman77.cazahosts.ui.components.Divider
import com.huevoman77.cazahosts.ui.components.KeyValueRow
import com.huevoman77.cazahosts.ui.components.LabeledSwitch
import com.huevoman77.cazahosts.ui.components.Pill
import com.huevoman77.cazahosts.ui.components.SecondaryButton
import com.huevoman77.cazahosts.ui.components.SectionCard
import com.huevoman77.cazahosts.ui.components.SliderRow
import com.huevoman77.cazahosts.ui.theme.Mint

@Composable
fun SettingsScreen(settings: AppSettings, onBack: () -> Unit) {
    val prefs by settings.state.collectAsState()
    val sessions by ScanRepository.sessions.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 30.dp)
    ) {
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
                Text(
                    "Ajustes",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    "Motor, comprobaciones y datos de la app",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            SectionCard(
                title = "Motor de escaneo",
                subtitle = "Velocidad y espera de las conexiones",
                icon = Icons.Outlined.Speed,
                accent = Mint
            ) {
                SliderRow(
                    title = "Hilos simultáneos",
                    valueLabel = "${prefs.concurrency}",
                    value = prefs.concurrency.toFloat(),
                    range = 8f..512f
                ) { value -> settings.update { it.copy(concurrency = value.toInt()) } }
                SliderRow(
                    title = "Espera por intento",
                    valueLabel = "${prefs.timeoutMs} ms",
                    value = prefs.timeoutMs.toFloat(),
                    range = 150f..3000f
                ) { value -> settings.update { it.copy(timeoutMs = value.toInt()) } }
                Text(
                    "En redes lentas sube la espera; en redes locales puedes bajar los hilos para no saturar el router.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            SectionCard(
                title = "Comprobaciones",
                subtitle = "Qué se revisa en cada host",
                icon = Icons.Outlined.Storage,
                accent = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                LabeledSwitch(
                    title = "Capturar banners",
                    subtitle = "Nombre del servicio y versión",
                    checked = prefs.grabBanner,
                    onChange = { value -> settings.update { it.copy(grabBanner = value) } }
                )
                LabeledSwitch(
                    title = "Auditoría activa",
                    subtitle = "FTP anónimo, Redis, Docker API, rutas sensibles",
                    checked = prefs.activeChecks,
                    onChange = { value -> settings.update { it.copy(activeChecks = value) } }
                )
                LabeledSwitch(
                    title = "Tabla ARP",
                    subtitle = "Detecta equipos que no abren puertos",
                    checked = prefs.useArp,
                    onChange = { value -> settings.update { it.copy(useArp = value) } }
                )
                LabeledSwitch(
                    title = "DNS inverso",
                    subtitle = "Nombre del equipo",
                    checked = prefs.reverseDns,
                    onChange = { value -> settings.update { it.copy(reverseDns = value) } }
                )
            }
        }

        item {
            SectionCard(
                title = "Apariencia",
                icon = Icons.Outlined.Palette,
                accent = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                LabeledSwitch(
                    title = "Tema oscuro",
                    subtitle = "Más cómodo de noche y ahorra batería",
                    checked = prefs.themeDark,
                    onChange = { value -> settings.update { it.copy(themeDark = value) } }
                )
            }
        }

        item {
            SectionCard(
                title = "Datos guardados",
                subtitle = "${sessions.size} escaneos en el dispositivo",
                icon = Icons.Outlined.Storage,
                accent = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                if (sessions.isEmpty()) {
                    Text(
                        "Todavía no hay escaneos guardados.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    sessions.take(8).forEach { session ->
                        KeyValueRow(
                            key = session.target.take(18),
                            value = "${session.hostsAlive} vivos · ${session.findingsCount} hallazgos",
                            valueColor = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    SecondaryButton(
                        text = "Borrar historial",
                        icon = Icons.Outlined.Delete,
                        onClick = {
                            sessions.forEach { ScanRepository.deleteSession(it.id) }
                            ScanRepository.reloadSessions()
                        }
                    )
                }
            }
        }

        item {
            SectionCard(
                title = "Acerca de",
                icon = Icons.Outlined.Info,
                accent = Mint,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill(text = "CazaHosts ${BuildConfig.VERSION_NAME}", accent = Mint)
                    Pill(text = "offline", accent = MaterialTheme.colorScheme.secondary)
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "Escáner de red para uso autorizado. Todo el análisis ocurre en el dispositivo: la app no envía datos a ningún servidor y la base de CVEs viaja dentro del APK.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Divider()
                Spacer(Modifier.height(8.dp))
                Text(
                    "Uso responsable: escanea únicamente redes y equipos sobre los que tengas autorización o permiso explícito. Eludir controles de acceso o probar credenciales ajenas puede ser un delito.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.height(12.dp))
                SecondaryButton(
                    text = "Volver",
                    icon = Icons.Outlined.Delete,
                    onClick = onBack
                )
            }
        }
    }
}
