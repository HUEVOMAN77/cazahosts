## CazaHosts 1.1.0

Escanear tu red, encontrarte con todos sus subdominios y saber qué está expuesto de verdad.

### Motor de fuerza bruta DNS

- Motor DNS propio: arma las consultas a nivel de protocolo (UDP y TCP) sin depender de librerías externas.
- Fuerza bruta de subdominios con diccionario rápido, medio o completo, más tu propia lista de palabras.
- Patrones automáticos: `dev-web`, `api-prod`, `1` a `999`, combinaciones de dos niveles.
- Tipos de registro: A, AAAA, CNAME, NS, MX, TXT, SOA y SRV.
- Detección de DNS comodín: prueba nombres aleatorios y descarta los que resuelven siempre igual.
- Seguimiento de CNAME para saber a qué apunta realmente cada subdominio.
- Resolución con rotación de servidores DNS, reintentos y paso a TCP cuando el servidor recorta la respuesta.
- Enriquecimiento: título de la página web, código HTTP, IPv6 y nombre inverso (PTR).

### Motor de escaneo mejorado

- Chequeos UDP: SNMP con comunidad pública, DNS recursivo abierto, NTP y SSDP/UPnP.
- Pausar y reanudar un escaneo a mitad de camino.
- Doble clic en cualquier servicio para ver el titular del certificado TLS.
- Objetivo que ya no se pierde al cambiar de pestaña ni al cerrar la app.

### Interfaz

- Pantalla nueva de DNS con filtros por web, CNAME e IPv6, y buscador.
- Envío directo de los subdominios encontrados al escanear y al verificador de vivos.
- Detalle de cada escaneo guardado, con exportación a CSV y JSON.
- Pantalla principal con la información de tu red: IP, puerta de enlace, servidor DNS y subred local.
- Verificador de vivos que ahora sí revisa solo mientras la app está abierta.

### Correcciones

- Las reglas de auditoría que dependían solo del puerto o del servicio ya se evalúan bien (Docker API, Redis, MongoDB y compañía).
- El verificador de vivos ya no promete que siga revisando con la app cerrada.

### Detalles técnicos

- Compilado con Kotlin 2.0.21, Jetpack Compose (BOM 2024.12.01), Material 3 y coroutines.
- Requiere Android 8.0 (API 26) o superior.
- 31 pruebas unitarias en verde: parser DNS, base de datos de vulnerabilidades, generadores, huellas de servicio y utilidades de IP.
