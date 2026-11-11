# CazaHosts

Escáner de red nativo para Android. Descubre qué equipos están vivos en un rango, qué
puertos tienen abiertos, qué versión de software corre en cada uno y qué riesgos
verificables existen en ellos. Todo el análisis ocurre dentro del teléfono: la app no
manda datos a ningún servidor y la base de vulnerabilidades viaja dentro del APK.

<img src="docs/icono.png" width="96" alt="Icono de CazaHosts">

## Qué hace

- **Barrido de hosts**: recorre rangos CIDR, rangos simples, comodines (`10.0.0.*`) o
  listas de IP y detecta los equipos que responden. Hasta 65 536 direcciones por pasada.
- **Escaneo de puertos**: con perfiles *Comunes*, *Expandido*, *1-65535* o una lista
  propia (`22,80,443,8000-8010`). Captura banners e identifica servicio y versión.
- **Base de vulnerabilidades**: correlaciona cada banner con CVEs conocidos (regreSSHion,
  vsftpd 2.3.4, ProFTPD mod_copy, Apache, nginx, Redis, Jenkins, Grafana, Docker API,
  Kubernetes API, y más) y con malas prácticas de exposición.
- **Chequeos activos no destructivos**: FTP anónimo, Redis sin contraseña, MongoDB,
  Elasticsearch, Docker API, archivos sensibles (`.env`, `.git/config`, respaldos),
  paneles administrativos, cabeceras HTTP faltantes y certificados TLS vencidos.
- **Checker de disponibilidad**: vigila tus equipos y te avisa cuando uno cae o vuelve.
- **Generadores**: contraseñas (incluido modo pronunciable), subdominios, rangos de IP,
  subredes, usuarios y diccionarios.
- **Auditoría de credenciales**: prueba contraseñas de fábrica contra FTP, Telnet y HTTP
  Basic, con ritmo limitado, tope de intentos por equipo y casilla de autorización.

## Requisitos

- Android 8.0 (API 26) o superior
- Sin root y sin permisos especiales más allá de Internet y notificaciones

## Instalación

1. Bajar el APK de la sección *Releases* de este repositorio.
2. Abrir el archivo en el teléfono y permitir la instalación desde fuentes desconocidas.
3. Darle permiso de notificaciones para ver el avance de los escaneos y los avisos del
   checker.

## Uso rápido

1. En **Escanear**, tocá *Mi red* para cargar el `/24` de tu Wi-Fi o escribí tu rango, una IP sola o el nombre de un host (`ejemplo.com`, que se resuelve por DNS).
2. Elegí el modo: *Solo hosts*, *Hosts y puertos* o *Auditoría completa*.
3. Ajustá hilos y tiempo de espera si tu red es lenta.
4. Tocá **Iniciar escaneo**. Los resultados aparecen en vivo.
5. Revisá **Riesgos** para el detalle de cada hallazgo y la solución sugerida.

## Compilar desde cero

```bash
git clone https://github.com/HUEVOMAN77/cazahosts.git
cd cazahosts
./gradlew assembleRelease
```

La llave de firma se toma de las variables de entorno `KS_BASE64` (llave en base64),
`KS_PASS` y `KS_ALIAS`. Sin ellas, Gradle firma con la llave de depuración.

## Estructura

```
app/src/main/java/com/huevoman77/cazahosts/
├── core/      utilidades de IP, sockets, tabla ARP y fabricantes (MAC)
├── data/      modelos, ajustes, guardado de resultados y repositorio de escaneo
├── scan/      motor de escaneo, captura de banners y fingerprinting de servicios
├── vuln/      base de reglas CVE y chequeos activos
├── creds/     auditoría de credenciales
├── check/     verificador de disponibilidad
├── generators/generadores de listas
├── service/   servicio en primer plano durante los escaneos
└── ui/        pantallas Compose, tema y componentes
```

## Base de vulnerabilidades

Las reglas viven en `vuln/VulnDb.kt` y se evalúan contra el banner y la versión
detectada. Cada regla trae severidad, CVSS, descripción y la solución recomendada.
La auditoría activa complementaria está en `vuln/Checks.kt`.

## Uso responsable

Esta app está pensada para redes y equipos sobre los que tengas autorización o permiso
explícito. Escanear redes de terceros, eludir controles de acceso o probar credenciales
ajenas puede ser un delito en la mayoría de los países. La función de credenciales exige
confirmar la autorización antes de activarse.

## Licencia

Uso interno. El código se entrega tal cual, sin garantía.
