package com.svoemesto.karaokeapp.services

import com.svoemesto.karaokeapp.KaraokeProperties
import com.svoemesto.karaokeapp.runCommand
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * Управление VLESS-конфигом xray-контейнера (`karaoke-telegram-proxy`) прямо из
 * Karaoke.properties (specs/113-telegram-demo-publish, Фаза 2 автопубликации).
 *
 * Проблема, которую решает: внешний `config.json` монтируется `:ro` в
 * xray-контейнер и редактируется вручную — при смене VPS / UUID / transport
 * нужно править файл на хосте и рестартить контейнер. Здесь — все параметры
 * задаются через Properties UI (`webvue3` → Properties → Telegram VLESS), при
 * старте `karaoke-app` генерируется новый `config.json` и через `docker restart`
 * контейнер поднимается с актуальной конфигурацией.
 *
 * Если `telegramVlessEnabled=false` (по умолчанию) — никаких действий: файл
 * не трогается, контейнер не рестартится (можно редактировать вручную).
 *
 * `TelegramApiClient` при этом **не меняется** — прокси-URL
 * `http://karaoke-telegram-proxy:1082` остаётся прежним; меняется только
 * содержимое прокси (его outbound к Telegram).
 *
 * @see archive/docs/features/telegram-auto-publish.md
 */
@Component
class TelegramProxyManager {
    companion object {
        private val JSON = Json { prettyPrint = true }
    }

    @EventListener(ApplicationReadyEvent::class)
    fun onApplicationReady() {
        if (!KaraokeProperties.getBoolean("telegramVlessEnabled")) {
            return
        }

        val configPath = KaraokeProperties.getString("telegramProxyConfigPath")
        val containerName = KaraokeProperties.getString("telegramProxyContainerName")

        val address = KaraokeProperties.getString("telegramVlessAddress")
        val port = KaraokeProperties.getLong("telegramVlessPort").let { if (it <= 0) 443L else it }
        val uuid = KaraokeProperties.getString("telegramVlessUuid")
        val flow = KaraokeProperties.getString("telegramVlessFlow")
        val network = KaraokeProperties.getString("telegramVlessNetwork").ifBlank { "tcp" }
        val security = KaraokeProperties.getString("telegramVlessSecurity").ifBlank { "none" }
        val path = KaraokeProperties.getString("telegramVlessPath").ifBlank { "/" }
        val host = KaraokeProperties.getString("telegramVlessHost")
        val sni = KaraokeProperties.getString("telegramVlessSni")
        val alpn = KaraokeProperties.getString("telegramVlessAlpn").ifBlank { "h2,http/1.1" }
        val fingerprint = KaraokeProperties.getString("telegramVlessFingerprint").ifBlank { "chrome" }
        val padding = KaraokeProperties.getString("telegramVlessPadding").ifBlank { "100-1000" }

        if (address.isBlank() || uuid.isBlank()) {
            println("TelegramProxyManager: telegramVlessAddress/uuid не заполнены — пропуск")
            return
        }

        try {
            val configJson =
                buildXrayConfig(
                    address = address,
                    port = port,
                    uuid = uuid,
                    flow = flow,
                    network = network,
                    security = security,
                    path = path,
                    host = host,
                    sni = sni,
                    alpn = alpn,
                    fingerprint = fingerprint,
                    padding = padding,
                )

            java.io.File(configPath).writeText(configJson)
            println("TelegramProxyManager: config.json записан ($configPath)")

            // Перезапуск контейнера — docker-socket доступен karaoke-app (см. AGENTS.md).
            runCommand(
                listOf("docker", "restart", containerName),
                ignoreErrors = true,
            )
            println("TelegramProxyManager: docker restart $containerName выполнен")
        } catch (e: Exception) {
            println("TelegramProxyManager: ошибка: ${e.message}")
        }
    }

    /**
     * Генерация xray-config из VLESS-свойств. Структура соответствует
     * xray-core inbound=HTTP (port 1082), outbound=VLESS с заданным transport.
     * @see archive/docs/features/telegram-auto-publish.md
     */
    private fun buildXrayConfig(
        address: String,
        port: Long,
        uuid: String,
        flow: String,
        network: String,
        security: String,
        path: String,
        host: String,
        sni: String,
        alpn: String,
        fingerprint: String,
        padding: String,
    ): String {
        // vnext.users[0]
        val userObj =
            buildJsonObject {
                put("id", JsonPrimitive(uuid))
                put("encryption", JsonPrimitive("none"))
                if (flow.isNotBlank()) put("flow", JsonPrimitive(flow))
            }
        val vnextArr =
            JsonArray(
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("address", JsonPrimitive(address))
                            put("port", JsonPrimitive(port.toInt()))
                            put("users", JsonArray(listOf(userObj)))
                        }
                    )
                }
            )

        // streamSettings — собирается по выбранному network/security
        val streamSettings = buildStreamSettings(network, security, path, host, sni, alpn, fingerprint, padding)

        // outbounds[0] — VLESS
        val vlessOutbound =
            buildJsonObject {
                put("tag", JsonPrimitive("telegram-vless"))
                put("protocol", JsonPrimitive("vless"))
                put("settings", buildJsonObject { put("vnext", vnextArr) })
                put("streamSettings", streamSettings)
            }

        // outbounds[1] — direct (fallback)
        val directOutbound =
            buildJsonObject {
                put("tag", JsonPrimitive("direct"))
                put("protocol", JsonPrimitive("freedom"))
            }

        // inbounds[0] — HTTP на 1082
        val httpInbound =
            buildJsonObject {
                put("listen", JsonPrimitive("0.0.0.0"))
                put("port", JsonPrimitive(1082))
                put("protocol", JsonPrimitive("http"))
                put(
                    "settings",
                    buildJsonObject {
                        put("allowTransparent", JsonPrimitive(false))
                    }
                )
            }

        // routing — весь трафик через telegram-vless
        val routing =
            buildJsonObject {
                put("domainStrategy", JsonPrimitive("AsIs"))
                put(
                    "rules",
                    JsonArray(
                        buildJsonArray {
                            add(
                                buildJsonObject {
                                    put("type", JsonPrimitive("field"))
                                    put(
                                        "ip",
                                        JsonArray(
                                            buildJsonArray {
                                                add(JsonPrimitive("0.0.0.0/8"))
                                                add(JsonPrimitive("10.0.0.0/8"))
                                                add(JsonPrimitive("172.16.0.0/12"))
                                                add(JsonPrimitive("192.168.0.0/16"))
                                            }
                                        )
                                    )
                                    put("outboundTag", JsonPrimitive("direct"))
                                }
                            )
                        }
                    )
                )
            }

        val root =
            buildJsonObject {
                put("log", buildJsonObject { put("loglevel", JsonPrimitive("warning")) })
                put("inbounds", JsonArray(listOf(httpInbound)))
                put("outbounds", JsonArray(listOf(vlessOutbound, directOutbound)))
                put("routing", routing)
            }

        return JSON.encodeToString(JsonObject.serializer(), root)
    }

    /**
     * Сборка streamSettings по выбранному transport/security. Поддерживаются
     * комбинации tcp+tls/reality, ws+tls/reality, grpc+tls/reality, xhttp+tls/reality.
     */
    private fun buildStreamSettings(
        network: String,
        security: String,
        path: String,
        host: String,
        sni: String,
        alpn: String,
        fingerprint: String,
        padding: String,
    ): JsonObject {
        val networkLower = network.lowercase()
        val securityLower = security.lowercase()

        return buildJsonObject {
            put("network", JsonPrimitive(networkLower))
            // tcp без настроек transport — не пишем <network>Settings (xray не любит пустой {})
            if (networkLower != "tcp") {
                val networkSettings =
                    when (networkLower) {
                        "ws" ->
                            buildJsonObject {
                                put("path", JsonPrimitive(path))
                                put("headers", buildJsonObject { put("Host", JsonPrimitive(host)) })
                            }
                        "grpc" ->
                            buildJsonObject {
                                put("serviceName", JsonPrimitive(path.removePrefix("/")))
                            }
                        "xhttp" ->
                            buildJsonObject {
                                put("path", JsonPrimitive(path))
                                put("mode", JsonPrimitive("auto"))
                                put("extra", buildJsonObject { put("xPaddingBytes", JsonPrimitive(padding)) })
                            }
                        else -> buildJsonObject { }
                    }
                if (networkSettings.isNotEmpty()) {
                    put("${networkLower}Settings", networkSettings)
                }
            }
            // security != none — добавляем security + securitySettings
            if (securityLower != "none") {
                put("security", JsonPrimitive(securityLower))
                val securitySettings =
                    when (securityLower) {
                        "tls" ->
                            buildJsonObject {
                                put("serverName", JsonPrimitive(sni.ifBlank { host }))
                                put(
                                    "alpn",
                                    JsonArray(
                                        alpn
                                            .split(",")
                                            .map { it.trim() }
                                            .filter { it.isNotBlank() }
                                            .map { JsonPrimitive(it) },
                                    ),
                                )
                                put("fingerprint", JsonPrimitive(fingerprint))
                                put("allowInsecure", JsonPrimitive(false))
                            }
                        "reality" ->
                            buildJsonObject {
                                put("serverName", JsonPrimitive(sni.ifBlank { host }))
                                put("fingerprint", JsonPrimitive(fingerprint))
                            }
                        else -> buildJsonObject { }
                    }
                if (securitySettings.isNotEmpty()) {
                    put("${securityLower}Settings", securitySettings)
                }
            }
        }
    }
}
