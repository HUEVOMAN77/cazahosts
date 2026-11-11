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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.huevoman77.cazahosts.check.AliveChecker
import com.huevoman77.cazahosts.data.CheckerStore
import com.huevoman77.cazahosts.data.ScanRepository
import com.huevoman77.cazahosts.ui.components.EmptyState
import com.huevoman77.cazahosts.ui.components.LabeledSwitch
import com.huevoman77.cazahosts.ui.components.Pill
import com.huevoman77.cazahosts.ui.components.PrimaryButton
import com.huevoman77.cazahosts.ui.components.SecondaryButton
import com.huevoman77.cazahosts.ui.components.SectionCard
import com.huevoman77.cazahosts.ui.components.SliderRow
import com.huevoman77.cazahosts.ui.components.WatchedCard
import com.huevoman77.cazahosts.util.Notifier
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun CheckerScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val watched by CheckerStore.watched.collectAsState()
    val hosts by ScanRepository.hosts.collectAsState()
    var input by remember { mutableStateOf("") }
    var autoCheck by remember { mutableStateOf(false) }
    var interval by remember { mutableStateOf(60f) }
    var checking by remember { mutableStateOf(false) }
    val checker = remember { AliveChecker() }

    fun runCheck() {
        if (watched.isEmpty() || checking) return
        checking = true
        scope.launch {
            val results = checker.check(watched) { updated ->
                val before = CheckerStore.watched.value.firstOrNull { it.ip == updated.ip }
                CheckerStore.update(updated)
                if (before != null && before.alive != updated.alive) {
                    Notifier.stateChanged(context, updated.ip, updated.alive)
                }
            }
            results.forEach { CheckerStore.update(it) }
            checking = false
        }
    }

    LaunchedEffect(autoCheck, interval) {
        while (autoCheck) {
            delay((interval * 1000L).toLong())
            if (watched.isNotEmpty() && !checking) {
                val results = checker.check(watched)
                results.forEach { updated ->
                    val before = CheckerStore.watched.value.firstOrNull { it.ip == updated.ip }
                    CheckerStore.update(updated)
                    if (before != null && before.alive != updated.alive) {
                        Notifier.stateChanged(context, updated.ip, updated.alive)
                    }
                }
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 30.dp)
    ) {
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
                Text(
                    "Checker",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    "Vigila tus equipos y te avisa cuando cambian de estado",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            SectionCard(
                title = "Agregar equipos",
                subtitle = "Escribe una IP o usa los hosts del último escaneo",
                icon = Icons.Outlined.Add,
                accent = MaterialTheme.colorScheme.primary
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("192.168.1.10  o  10.0.0.1,10.0.0.2") },
                    singleLine = true
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrimaryButton(
                        text = "Agregar",
                        icon = Icons.Outlined.Add,
                        onClick = {
                            input.split(',', ' ', ';').map { it.trim() }.filter { it.isNotEmpty() }
                                .forEach { CheckerStore.add(it) }
                            input = ""
                        },
                        modifier = Modifier.weight(1f)
                    )
                    SecondaryButton(
                        text = "Del escaneo",
                        icon = Icons.Outlined.Timeline,
                        onClick = { CheckerStore.addAll(hosts.map { it.ip }) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (hosts.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Pill(text = "${hosts.size} hosts disponibles del último escaneo", accent = MaterialTheme.colorScheme.secondary)
                }
            }
        }

        item {
            SectionCard(
                title = "Comprobación automática",
                subtitle = "Revisa el estado de la lista con la frecuencia que elijas",
                icon = Icons.Outlined.NotificationsActive,
                accent = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                LabeledSwitch(
                    title = "Revisar automáticamente",
                    subtitle = "Sigue activo aunque cierres la app",
                    checked = autoCheck,
                    onChange = { autoCheck = it }
                )
                SliderRow(
                    title = "Intervalo",
                    valueLabel = "${interval.toInt()} s",
                    value = interval,
                    range = 15f..600f,
                    steps = 0
                ) { interval = it }
                Spacer(Modifier.height(6.dp))
                PrimaryButton(
                    text = if (checking) "Comprobando..." else "Comprobar ahora",
                    icon = Icons.Outlined.Refresh,
                    onClick = { runCheck() },
                    enabled = watched.isNotEmpty() && !checking,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (watched.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Outlined.Timeline,
                    title = "Sin equipos vigilados",
                    message = "Agrega las IP que quieres seguir. Cada revisión comprueba puertos comunes y la tabla ARP."
                )
            }
        } else {
            item {
                Text(
                    "Vigilados (${watched.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(start = 16.dp, top = 6.dp, bottom = 8.dp)
                )
            }
            items(watched, key = { it.ip }) { host ->
                WatchedCard(
                    host = host,
                    onClick = {},
                    onRemove = { CheckerStore.remove(host.ip) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
        }
    }
}
