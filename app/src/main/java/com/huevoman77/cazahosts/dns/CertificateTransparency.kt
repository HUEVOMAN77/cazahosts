package com.huevoman77.cazahosts.dns

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Certificate Transparency: los registros publicos de certificados guardan los
 * nombres para los que se emitio un certificado. crt.sh expone esa informacion
 * en JSON, y asi se descubren subdominios que no estan en ningun diccionario.
 */
object CertificateTransparency {

    data class Entry(
        val name: String,
        val issuer: String = "",
        val notBefore: String = "",
        val notAfter: String = ""
    )

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * Busca los nombres publicados en los registros de certificados. Primero
     * consulta Cert Spotter, que responde en menos de un segundo; si no hay
     * respuesta (o el limite de peticiones se agota) cae a crt.sh, que es mas
     * lento pero mas completo.
     */
    suspend fun search(domain: String, timeoutMs: Int): List<Entry> = withContext(Dispatchers.IO) {
        val rapido = certspotter(domain, timeoutMs)
        if (rapido.isNotEmpty()) return@withContext rapido
        crtsh(domain, maxOf(timeoutMs, CRTSH_TIMEOUT_MS))
    }

    /** Cert Spotter: respuesta rapida con los nombres de cada certificado. */
    private suspend fun certspotter(domain: String, timeoutMs: Int): List<Entry> =
        withContext(Dispatchers.IO) {
            val target = domain.trim().lowercase().trim('.')
            val url = "https://api.certspotter.com/v1/issuances" +
                "?domain=$target&include_subdomains=true&expand=dns_names"
            val body = fetch(url, timeoutMs) ?: return@withContext emptyList()
            parseCertspotter(body, target)
        }

    /** crt.sh: mas datos (incluye el emisor) pero tarda bastante en responder. */
    private suspend fun crtsh(domain: String, timeoutMs: Int): List<Entry> =
        withContext(Dispatchers.IO) {
            val target = domain.trim().lowercase().trim('.')
            val body = fetch("https://crt.sh/?q=%25.$target&output=json&exclude=expired", timeoutMs)
                ?: return@withContext emptyList()
            parse(body, target)
        }

    private suspend fun fetch(url: String, timeoutMs: Int): String? {
        val client = OkHttpClient.Builder()
            .connectTimeout(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
            .readTimeout(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
            .callTimeout(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(true)
            .build()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "CazaHosts/1.1")
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) null else response.body?.string()
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Extrae los nombres del JSON de crt.sh y se queda solo con los que
     * pertenecen al dominio buscado.
     */
    fun parse(body: String, domain: String): List<Entry> {
        val array = try {
            json.parseToJsonElement(body) as? JsonArray ?: return emptyList()
        } catch (e: Exception) {
            return emptyList()
        }
        val target = domain.trim().lowercase().trim('.')
        val suffix = ".$target"
        val out = LinkedHashMap<String, Entry>()
        for (item in array) {
            val obj = item as? JsonObject ?: continue
            val names = obj.string("name_value")
            if (names.isEmpty()) continue
            val issuer = obj.string("issuer_name")
            val notBefore = obj.string("not_before")
            val notAfter = obj.string("not_after")
            for (raw in names.split('\n', ' ', '\r')) {
                val name = raw.trim().lowercase().removePrefix("*.")
                if (name.isEmpty()) continue
                if (name != target && !name.endsWith(suffix)) continue
                out.putIfAbsent(name, Entry(name, issuer, notBefore, notAfter))
            }
        }
        return out.values.toList()
    }

    /**
     * Extrae los nombres de la respuesta de Cert Spotter, donde cada
     * certificado trae su lista de nombres en `dns_names`.
     */
    fun parseCertspotter(body: String, domain: String): List<Entry> {
        val array = try {
            json.parseToJsonElement(body) as? JsonArray ?: return emptyList()
        } catch (e: Exception) {
            return emptyList()
        }
        val target = domain.trim().lowercase().trim('.')
        val suffix = ".$target"
        val out = LinkedHashMap<String, Entry>()
        for (item in array) {
            val obj = item as? JsonObject ?: continue
            val notBefore = obj.string("not_before")
            val notAfter = obj.string("not_after")
            val names = obj["dns_names"] as? JsonArray ?: continue
            for (element in names) {
                val raw = (element as? JsonPrimitive)?.contentOrNull ?: continue
                val name = raw.trim().lowercase().removePrefix("*.")
                if (name.isEmpty()) continue
                if (name != target && !name.endsWith(suffix)) continue
                out.putIfAbsent(name, Entry(name, "", notBefore, notAfter))
            }
        }
        return out.values.toList()
    }

    private fun JsonObject.string(key: String): String =
        (this[key] as? JsonPrimitive)?.contentOrNull?.trim() ?: ""
}

/** Margen para crt.sh: el servidor tarda hasta un minuto en responder. */
const val CRTSH_TIMEOUT_MS = 60000
