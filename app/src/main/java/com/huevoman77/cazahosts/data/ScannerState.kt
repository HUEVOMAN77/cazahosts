package com.huevoman77.cazahosts.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Estado compartido de la pantalla de escaneo. Vive fuera de la pantalla para
 * que el objetivo no se pierda al cambiar de pestana y para que otras
 * pantallas (por ejemplo la de subdominios) puedan dejarlo preparado.
 */
object ScannerState {

    private val _target = MutableStateFlow("")
    val target: StateFlow<String> = _target

    fun setTarget(text: String) {
        _target.value = text
    }
}
