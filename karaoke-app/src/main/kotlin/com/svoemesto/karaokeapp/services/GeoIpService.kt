package com.svoemesto.karaokeapp.services

import com.svoemesto.karaokeapp.Connection
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.sql.SQLException
import java.sql.Statement
import java.util.concurrent.ConcurrentHashMap

/**
 * Определение страны по IP клиента для админ-дашборда статистики.
 *
 * Резолв — через api.country.is/<ip> (единственный гео-сервис, работающий из Docker-контейнера
 * без ограничений; тот же, что использует isVpnActive() в Utils.kt). Результаты кэшируются в
 * памяти (ConcurrentHashMap) и в таблице tbl_ip_country. Кэш-таблица живёт ТОЛЬКО на LOCAL
 * админ-БД (deploy/karaoke-db/08_ip_country.sql) и на прод не едет — поэтому все обращения к
 * кэшу идут через Connection.local() независимо от того, из какой БД (local/remote) пришли
 * события: IP есть IP, страна есть страна, кэш общий для админ-машины.
 *
 * Пустая строка "" — это ВАЛИДНЫЙ закэшированный результат «страна не определена» (приватный/
 * локальный IP, «серый» адрес, сервис не ответил): кэшируем и его, чтобы не долбить внешний
 * сервис повторно по тем же IP.
 */
object GeoIpService {
    private val memCache = ConcurrentHashMap<String, String>()
    private val COUNTRY_RE = Regex(""""country"\s*:\s*"([A-Za-z]{2})"""")

    // client_ip в теории может содержать цепочку X-Forwarded-For — берём первый адрес.
    private fun normalize(rawIp: String): String = rawIp.split(",").first().trim()

    // Приватные/зарезервированные диапазоны — страну по ним не определить, сеть не дёргаем.
    private fun isPrivate(ip: String): Boolean {
        if (ip.startsWith("10.") || ip.startsWith("127.") || ip.startsWith("192.168.")) return true
        if (ip.startsWith("172.")) {
            val second = ip.split(".").getOrNull(1)?.toIntOrNull()
            if (second != null && second in 16..31) return true
        }
        if (ip == "::1" || ip.startsWith("fc") || ip.startsWith("fd") || ip.startsWith("fe80")) return true
        return false
    }

    /** Страна по одному IP (ISO-код в верхнем регистре или "" если не определилась). */
    fun country(rawIp: String): String {
        val ip = normalize(rawIp)
        if (ip.isEmpty()) return ""
        memCache[ip]?.let { return it }
        loadFromDb(listOf(ip))[ip]?.let {
            memCache[ip] = it
            return it
        }
        val resolved = if (isPrivate(ip)) "" else fetchFromService(ip)
        memCache[ip] = resolved
        saveToDb(ip, resolved)
        return resolved
    }

    /**
     * Батч для агрегаций: normalized ip -> ISO-код страны. Сначала память, затем один IN-запрос в
     * кэш-таблицу, недостающие резолвит по одному с мягким троттлингом (~12 req/s) и кэширует.
     *
     * [maxFetch] ограничивает число ВНЕШНИХ резолвов за один вызов — чтобы запрос дашборда не
     * блокировался на минуты, когда кэш ещё холодный и уникальных IP тысячи. Не поместившиеся в
     * лимит IP возвращаются как "" (Не определено) и НЕ кэшируются — их подхватит следующий вызов
     * (кэш наполняется за несколько обновлений страницы). Приватные IP в лимит не считаются
     * (сеть не дёргается).
     */
    fun resolveMany(
        rawIps: Collection<String>,
        maxFetch: Int = Int.MAX_VALUE,
    ): Map<String, String> {
        val ips = rawIps.map { normalize(it) }.filter { it.isNotEmpty() }.distinct()
        val out = HashMap<String, String>(ips.size)
        val missing = mutableListOf<String>()
        for (ip in ips) {
            val m = memCache[ip]
            if (m != null) out[ip] = m else missing.add(ip)
        }
        if (missing.isNotEmpty()) {
            val fromDb = loadFromDb(missing)
            for ((ip, c) in fromDb) {
                out[ip] = c
                memCache[ip] = c
            }
            missing.removeAll(fromDb.keys)
        }
        var fetched = 0
        for (ip in missing) {
            val priv = isPrivate(ip)
            if (!priv && fetched >= maxFetch) {
                out[ip] = ""
                continue
            } // лимит исчерпан — не кэшируем, попробуем позже
            val resolved = if (priv) "" else fetchFromService(ip)
            out[ip] = resolved
            memCache[ip] = resolved
            saveToDb(ip, resolved)
            if (!priv) {
                fetched++
                try {
                    Thread.sleep(80)
                } catch (_: InterruptedException) {
                }
            }
        }
        return out
    }

    private fun fetchFromService(ip: String): String =
        try {
            val url = URL("https://api.country.is/" + URLEncoder.encode(ip, "UTF-8"))
            val conn = url.openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", "Mozilla/5.0")
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            val body = conn.inputStream.bufferedReader().readText()
            COUNTRY_RE
                .find(body)
                ?.groupValues
                ?.getOrNull(1)
                ?.uppercase() ?: ""
        } catch (e: Exception) {
            println("GeoIpService: не удалось определить страну для $ip: ${e.message}")
            ""
        }

    private fun loadFromDb(ips: List<String>): Map<String, String> {
        if (ips.isEmpty()) return emptyMap()
        val conn = Connection.local().getConnection() ?: return emptyMap()
        val inList = ips.joinToString(",") { "'${it.replace("'", "''")}'" }
        var st: Statement? = null
        val res = HashMap<String, String>()
        try {
            st = conn.createStatement()
            val rs = st.executeQuery("select ip, country from tbl_ip_country where ip in ($inList)")
            while (rs.next()) res[rs.getString("ip")] = rs.getString("country") ?: ""
            rs.close()
        } catch (e: SQLException) {
            e.printStackTrace()
        } finally {
            try {
                st?.close()
            } catch (_: SQLException) {
            }
        }
        return res
    }

    private fun saveToDb(
        ip: String,
        country: String,
    ) {
        val conn = Connection.local().getConnection() ?: return
        var st: Statement? = null
        try {
            st = conn.createStatement()
            val ipS = ip.replace("'", "''")
            val cS = country.replace("'", "''")
            st.executeUpdate(
                "insert into tbl_ip_country (ip, country) values ('$ipS', '$cS') " +
                    "on conflict (ip) do update set country = excluded.country, resolved_at = now()",
            )
        } catch (e: SQLException) {
            e.printStackTrace()
        } finally {
            try {
                st?.close()
            } catch (_: SQLException) {
            }
        }
    }
}
