# CazaHosts

Escáner de red nativo para Android. Descubre qué equipos están vivos en un rango, qué
puertos tienen abiertos, qué versión de software corre en cada uno, qué subdominios
existen en un dominio y qué riesgos verificables existen en ellos. Todo el análisis ocurre
dentro del teléfono: la app no manda datos a ningún servidor y la base de vulnerabilidades
viaja dentro del APK.

<img src="docs/icono.png" width="96" alt="Icono de CazaHosts">

## Qué hace

- **Barrido de hosts**: recorre rangos CIDR, rangos simples, comodines (`10.0.0.*`) o
  listas de IP y detecta los equipos que responden. Hasta 65 536 direcciones por pasada.
- **Escaneo de puertos**: con perfiles *Comunes*, *Expandido*, *1-65535* o una lista
  propia (`22,80,443,8000-8010`). Captura banners e identifica servicio y versión.
- **Fuerza bruta DNS de subdominios**: motor DNS construido sobre `dnsjava` (la librería de
  referencia en Java) que resuelve nombres de verdad, lee la respuesta completa (CNAME, NS,
  MX, TXT, SOA, SRV), reintenta por TCP cuando el servidor recorta la respuesta y rota
  entre varios resolvers. Prueba diccionarios, patrones y fuentes externas contra un dominio
  y devuelve los nombres que existen.
- **Buscador de subdominios**: cada subdominio se revisa a fondo y se informa si responde
  con **HTTP 200** o con otro código, qué **puertos** tiene abiertos, si es funcional para
  **SNI** (TLS con su propio nombre), para **payload** (acepta un POST con cuerpo) o para
  **HTTP directo**, quién emitió el certificado y cuánto le falta para vencer.
- **Bases de vulnerabilidades**: correlaciona cada banner con CVEs conocidos (regreSSHion,
  vsftpd 2.3.4, ProFTPD mod_copy, Apache, nginx, Redis, Jenkins, Grafana, Docker API,
  Kubernetes API, y más) y con malas prácticas de exposición.
- **Chequeos activos no destructivos**: FTP anónimo, Redis sin contraseña, MongoDB,
  Elasticsearch, Docker API, archivos sensibles (`.env`, `.git/config`, respaldos),
  paneles administrativos, cabeceras HTTP faltantes y certificados TLS vencidos.
- **Chequeos UDP**: SNMP con comunidad pública, DNS recursivo abierto, NTP y SSDP/UPnP.
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
4. Tocá **Iniciar escaneo**. Los resultados aparecen en vivo y podés pausar o reanudar.
5. Revisá **Riesgos** para el detalle de cada hallazgo y la solución sugerida.

## Fuerza bruta de subdominios

En la pestaña **DNS** escribís el dominio (`ejemplo.com`) y el motor prueba nombres
candidatos hasta encontrar los que resuelven.

- **Diccionario**: rápido (150 nombres), medio o completo (~900 nombres), o tu propia
  lista en formato texto, que se abre desde el teléfono.
- **Patrones**: prefijos y sufijos (`dev-web`, `api-prod`), numeración (`api1` … `api999`)
  y combinaciones de dos niveles.
- **Tipos de registro**: A, AAAA, CNAME, NS, MX, TXT, SOA y SRV.
- **DNS comodín**: antes de empezar el motor genera nombres aleatorios y descarta todo
  lo que responda siempre igual, así no te llena la pantalla de falsos positivos.
- **CNAME**: si el nombre apunta a otro host, se sigue la cadena para saber a dónde
  termina realmente.
- **Enriquecido**: cada subdominio trae su IPv4, su IPv6, el código y el título de la
  página web y el nombre inverso (PTR) cuando existe.
- **Salida**: podés copiar los resultados, compartirlos, mandarlos al escanear de puertos
  o dejarlos vigilados en el checker de vivos.

Los servidores DNS se eligen a mano (automático, el de tu red, 1.1.1.1, 8.8.8.8 o
9.9.9.9) y el motor rota entre ellos y reintenta ante respuesta truncada.

### Fuentes de nombres

El motor no se limita al diccionario. En la sección **Fuentes** se pueden activar:

- **Transferencia de zona (AXFR)**: pide la zona completa al servidor de nombres. La
  mayoría de los servidores la niegan, pero cuando la publican aparecen de una sola vez
  todos los hosts del dominio, incluidos los que no están en ningún diccionario.
- **Registros de certificados (Certificate Transparency)**: consulta los logs públicos de
  certificados para saber qué nombres recibieron un certificado. Primero usa Cert Spotter
  (responde en menos de un segundo) y, si no hay respuesta, cae a crt.sh.

### Qué se revisa de cada subdominio

Con **Revisar el sitio web** y **Revisar puertos abiertos** activados, cada host se
clasifica así:

| Dato | Qué significa |
| --- | --- |
| **HTTP 200** | El sitio responde con 200. Si aparece otro código (301, 302, 403, 404, 500…) se muestra tal cual: el 200 es el que significa que la página está viva y usable. |
| **HTTP directo** | Responde por HTTP sin cifrar (puerto 80). |
| **Con SNI** | El TLS funciona enviando el nombre propio y el certificado cubre ese nombre. |
| **Solo con SNI** | Sin SNI el servidor entrega otro certificado o no completa el saludo: depende del nombre. |
| **Payload** | Acepta un POST con cuerpo y contesta con 2xx o 3xx en vez de rechazarlo. |
| **Puertos** | Puertos abiertos en la dirección del subdominio, con el nombre del servicio. |
| **Certificado** | Quién lo emitió, su SAN y los días que le quedan para vencer. |

Los filtros de la lista dejan ver solo los *HTTP 200*, los *SNI ok*, los *Payload ok* o
los que tienen puertos abiertos.

## Compilar desde cero

```bash
git clone https://github.com/HUEVOMAN77/cazahosts.git
cd cazahosts
./gradlew assembleRelease
```

La llave de firma se toma de las variables de entorno `KS_BASE64` (llave en base64),
`KS_PASS` y `KS_ALIAS`. Sin ellas, Gradle firma con la llave de depuración.

Para correr las pruebas:

```bash
./gradlew testDebugUnitTest
```

## Estructura

```
app/src/main/java/com/huevoman77/cazahosts/
├── core/      utilidades de IP, sockets, tabla ARP y fabricantes (MAC)
├── data/      modelos, ajustes, guardado de resultados y repositorio de escaneo
├── dns/       cliente DNS (dnsjava), diccionario, fuentes y motor de subdominios
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
