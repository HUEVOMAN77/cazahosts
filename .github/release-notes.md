## Novedades de esta versión

El buscador de subdominios deja de ser solo eso: ahora dice cuáles sirven de verdad.

### Motor DNS sobre dnsjava

- El motor de consultas pasa a **dnsjava 3.7.1**, la última versión publicada: todos los tipos de registro, EDNS, DNSSEC y transferencias de zona AXFR.
- Las consultas se arman con nombres absolutos y resuelven con rotación de servidores, reintentos y paso a TCP cuando el servidor recorta la respuesta.
- Transferencia de zona (AXFR) contra los servidores de nombres del dominio: si el servidor publica la zona completa, se listan todos sus registros de una sola vez.

### Más lugares donde buscar nombres

- **Certificados públicos**: los nombres que aparecen en los certificados emitidos (Cert Spotter y, de respaldo, crt.sh) se agregan a los del diccionario.
- Se marca el origen de cada subdominio: fuerza bruta, transferencia de zona o certificado.

### Qué responde cada subdominio

- **HTTP 200** cuando el sitio responde con éxito, y el código exacto cuando responde otra cosa (301, 302, 403, 405...).
- **HTTP directo**: qué contesta la IP cuando no se le pide SNI.
- **Con SNI**: si el TLS acepta el nombre y si el certificado lo cubre de verdad.
- **Solo con SNI**: aviso cuando el certificado que se presenta sin SNI es distinto del que corresponde al nombre.
- **Payload**: si el servidor acepta una carga útil (2xx o 3xx) o la rechaza (405).
- **Puertos abiertos** de cada subdominio, con el nombre del servicio.
- **Certificado**: emisor, titular y días que le quedan para vencer.

### Interfaz

- Filtros por subdominio con web, HTTP 200, SNI, payload, CNAME, IPv6 y puertos abiertos.
- El certificado vencido o por vencer se marca en ámbar y en rojo.
- La exportación de texto incluye estado HTTP, SNI, payload, puertos y certificado.

### Detalles técnicos

- Compilado con Kotlin 2.0.21, Jetpack Compose (BOM 2024.12.01), Material 3 y coroutines.
- dnsjava 3.7.1, OkHttp 4.12.0 y SLF4J.
- Requiere Android 8.0 (API 26) o superior.
- 58 pruebas unitarias en verde, 9 de ellas de comprobación en vivo contra Internet que se activan a mano.
