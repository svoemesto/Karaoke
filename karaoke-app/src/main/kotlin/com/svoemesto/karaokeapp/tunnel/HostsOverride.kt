package com.svoemesto.karaokeapp.tunnel

import java.io.File

/**
 * Управление переопределением резолвинга имени прод-сайта.
 *
 * Механика (FR-021, FR-022, FR-025): чтобы трафик к `sm-karaoke.ru` физически
 * шёл внутри туннеля, а имя хоста и TLS-сертификат сервера **остались
 * прежними**, в файл резолвинга добавляется строка
 * `<внутренний адрес> <имя хоста>`. SNI и заголовок `Host` не меняются — значит,
 * сертификат сервера остаётся валидным, проверка сертификата остаётся включённой.
 *
 * Строка помечена комментарием-маркером, чтобы её можно было найти и снять
 * безопасно: чужие строки файла не трогаются.
 *
 * @see WireGuardProdTunnel — включает/выключает переопределение вместе с туннелем.
 */
class HostsOverride(
    private val marker: String = HOSTS_MARKER,
) {
    /**
     * Добавляет (или обновляет) переопределение.
     *
     * @param address внутренний адрес прод-хоста (`10.77.0.2`).
     * @param hostName имя хоста (`sm-karaoke.ru`).
     * @param hostsFile путь к файлу резолвинга.
     * @return `true`, если файл изменён.
     */
    fun enable(
        address: String,
        hostName: String,
        hostsFile: String,
    ): Boolean {
        val file = File(hostsFile)
        if (!file.isFile) return false
        val lines = file.readLines().toMutableList()
        val newLine = "$address $hostName"
        val index = lines.indexOfFirst { it.contains(marker) }
        if (index >= 0) {
            if (lines[index].trim() == newLine) return false
            lines[index] = "$newLine $marker"
        } else {
            lines.add("$newLine $marker")
        }
        file.writeText(lines.joinToString("\n", postfix = "\n"))
        return true
    }

    /**
     * Снимает переопределение (возврат к публичному пути — откат).
     *
     * @return `true`, если файл изменён.
     */
    fun disable(
        hostName: String,
        hostsFile: String,
    ): Boolean {
        val file = File(hostsFile)
        if (!file.isFile) return false
        val lines = file.readLines()
        val remaining = lines.filterNot { it.contains(marker) && it.contains(hostName) }
        if (remaining.size == lines.size) return false
        file.writeText(remaining.joinToString("\n", postfix = "\n"))
        return true
    }

    companion object {
        /** Маркер строки переопределения. */
        const val HOSTS_MARKER = "# karaoke-prod-tunnel"
    }
}
