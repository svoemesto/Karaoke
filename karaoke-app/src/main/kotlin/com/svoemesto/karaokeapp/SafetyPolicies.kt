package com.svoemesto.karaokeapp

import com.svoemesto.karaokeapp.model.Song
import com.svoemesto.karaokeapp.model.SongField
import java.net.InetAddress
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

/**
 * Политики безопасности и целостности пакета `PKG-S2-10` (аудит #336, OpenProject #373).
 *
 * Каждая политика — чистая функция/набор функций без обращения к БД и сети (кроме
 * [AlbumCoverUrlPolicy], которой нужен резолв имени). Так их можно проверить
 * регрессионным тестом «до/после» без живого JDBC и без поднятия Spring.
 *
 * @see specs/373-pkg-s2-10/report.md
 */
object ProcessArgsPolicy {
    /**
     * BR-033: `process_args` — «непустой список непустых списков». Пустой внешний список
     * или пустой шаг роняли главный цикл воркера (`args[0]` / `args[0][0]`) либо навсегда
     * оставляли лейн в WAITING.
     *
     * @return текст причины, если аргументы невалидны; `null` — если валидны.
     */
    fun validationError(args: List<List<String>>): String? =
        when {
            args.isEmpty() -> "process_args пуст: нет ни одного шага"
            args.any { it.isEmpty() } -> "process_args содержит шаг без аргументов"
            else -> null
        }
}

/**
 * BR-048: whitelist копируемых полей для `/api/song/copyfieldsfromanother`.
 *
 * Раньше принимался любой `SongField` из запроса, включая `ID`; запись `ID` в `fields`
 * приводила к `UPDATE tbl_songs ... WHERE id = <idAnother>` — затиранию чужой песни.
 * Разрешены только контентные поля; всё остальное отклоняется целиком (fail-closed).
 */
object SongCopyFieldsPolicy {
    /** Поля контента, которые безопасно копировать между песнями. */
    val allowed: Set<SongField> =
        setOf(
            SongField.SOURCE_TEXT,
            SongField.RESULT_TEXT,
            SongField.SOURCE_MARKERS,
            SongField.FORMATTED_TEXT_SONG,
            SongField.FORMATTED_TEXT_TABS,
            SongField.FORMATTED_TEXT_CHORDS,
            SongField.DIFFBEATS,
            SongField.DESCRIPTION,
            SongField.SHORT_DESCRIPTION,
            SongField.WARNING,
        )

    data class Decision(
        val accepted: List<SongField>,
        val rejected: List<String>,
    ) {
        val isValid: Boolean get() = accepted.isNotEmpty() && rejected.isEmpty()
    }

    /** Разбирает строку `A;B;C` и делит поля на разрешённые и отклонённые. */
    fun decide(raw: String): Decision {
        val accepted = mutableListOf<SongField>()
        val rejected = mutableListOf<String>()
        raw
            .split(";")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { name ->
                val field = runCatching { SongField.valueOf(name) }.getOrNull()
                if (field != null && field in allowed) {
                    if (field !in accepted) accepted.add(field)
                } else {
                    rejected.add(name)
                }
            }
        return Decision(accepted, rejected)
    }
}

/**
 * BR-049: SSRF-политика для `/api/song/albumcoverproxy`.
 *
 * Прокси обязан ходить только по публичным http(s)-адресам: без userinfo, без
 * нестандартных портов, без loopback/приватных/link-local/служебных диапазонов.
 * Доменный allowlist источников обложек (Яндекс.Музыка / SearXNG) — решение владельца:
 * SearXNG отдаёт произвольные внешние хосты, поэтому жёсткий allowlist сломал бы фичу
 * (см. отчёт). Здесь реализован максимум безопасного по умолчанию.
 */
object AlbumCoverUrlPolicy {
    /** Предел размера тела ответа прокси. */
    const val MAX_BYTES: Long = 10L * 1024 * 1024

    private val deniedHostSuffixes = listOf(".localhost", ".local", ".internal")

    /**
     * @param resolve резолвер имени (подменяется в тестах).
     * @return причина отказа или `null`, если URL допустим.
     */
    fun rejectionReason(
        url: String,
        resolve: (String) -> Array<InetAddress> = { InetAddress.getAllByName(it) },
    ): String? {
        if (url.isBlank()) return "пустой url"
        val uri =
            runCatching { URI.create(url) }.getOrNull()
                ?: return "некорректный url"
        if (uri.scheme !in setOf("http", "https")) return "разрешены только http/https"
        if (uri.userInfo != null) return "userinfo в url запрещён"
        val host =
            uri.host
                ?.trim()
                ?.lowercase()
                ?.trimEnd('.')
                ?: return "в url нет хоста"
        if (host == "localhost" || deniedHostSuffixes.any { host.endsWith(it) }) {
            return "локальные/служебные имена запрещены: $host"
        }
        if (uri.port != -1 && uri.port != 80 && uri.port != 443) {
            return "разрешены только порты 80/443, получен ${uri.port}"
        }
        // IP-литералы проверяем напрямую, не отдавая резолверу: иначе подменённый/общий
        // резолвер «разрешил» бы loopback.
        val literal =
            when {
                host.startsWith("[") && host.endsWith("]") -> host.substring(1, host.length - 1)
                host.matches(Regex("""\d{1,3}(\.\d{1,3}){3}""")) -> host
                else -> null
            }
        val addresses =
            if (literal != null) {
                runCatching { arrayOf(InetAddress.getByName(literal)) }.getOrElse { return "некорректный IP-адрес $literal" }
            } else {
                runCatching { resolve(host) }.getOrElse { return "имя $host не резолвится" }
            }
        if (addresses.isEmpty()) return "имя $host не резолвится"
        if (addresses.any { isBlockedAddress(it) }) {
            return "адрес $host указывает на приватный/служебный диапазон"
        }
        return null
    }

    fun isAllowed(
        url: String,
        resolve: (String) -> Array<InetAddress> = { InetAddress.getAllByName(it) },
    ): Boolean = rejectionReason(url, resolve) == null

    /** Приватные, loopback, link-local, multicast и «нулевые» адреса. */
    fun isBlockedAddress(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress ||
            address.isLoopbackAddress ||
            address.isLinkLocalAddress ||
            address.isSiteLocalAddress ||
            address.isMulticastAddress
        ) {
            return true
        }
        val bytes = address.address
        if (bytes.size == 4) {
            val b0 = bytes[0].toInt() and 0xFF
            val b1 = bytes[1].toInt() and 0xFF
            // 0.0.0.0/8, 100.64.0.0/10 (CGNAT), 169.254.0.0/16, 127.0.0.0/8 уже покрыт isLoopback
            if (b0 == 0) return true
            if (b0 == 100 && b1 in 64..127) return true
            if (b0 == 169 && b1 == 254) return true
            if (b0 == 192 && b1 == 0) return true
            if (b0 == 198 && b1 in 18..19) return true
            if (b0 >= 240) return true
        } else if (bytes.size == 16) {
            // unique local fc00::/7 и link-local fe80::/10
            val b0 = bytes[0].toInt() and 0xFF
            val b1 = bytes[1].toInt() and 0xFF
            if ((b0 and 0xFE) == 0xFC) return true
            if (b0 == 0xFE && (b1 and 0xC0) == 0x80) return true
            if ((b0 and 0xFF) == 0xFF) return true
            // IPv4-mapped 0:0:0:0:0:ffff:a.b.c.d
            val mapped =
                bytes.slice(0 until 10).all { it == 0.toByte() } &&
                    bytes[10] == 0xFF.toByte() &&
                    bytes[11] == 0xFF.toByte()
            if (mapped) {
                return isBlockedAddress(InetAddress.getByAddress(bytes.copyOfRange(12, 16)))
            }
        }
        return false
    }

    /** Проверка сигнатуры картинки: JPEG, PNG, GIF, WebP. */
    fun looksLikeImage(bytes: ByteArray): Boolean {
        if (bytes.size < 12) return false
        val jpeg = bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()
        val pngMagic =
            bytes[0] == 0x89.toByte() &&
                bytes[1] == 'P'.code.toByte() &&
                bytes[2] == 'N'.code.toByte() &&
                bytes[3] == 'G'.code.toByte()
        val gifMagic =
            bytes.copyOfRange(0, 6).contentEquals("GIF87a".toByteArray()) ||
                bytes.copyOfRange(0, 6).contentEquals("GIF89a".toByteArray())
        val webpMagic =
            bytes.copyOfRange(0, 4).contentEquals("RIFF".toByteArray()) &&
                bytes.copyOfRange(8, 12).contentEquals("WEBP".toByteArray())
        return jpeg || pngMagic || gifMagic || webpMagic
    }
}

/**
 * BR-050: валидация `rootFolder` из `POST /api/song/update`.
 *
 * `rootFolder` — вычисляемый путь библиотеки, а не редактируемое пользователем поле.
 * Принимается только неизменённое текущее значение, лежащее под разрешённым корнем
 * библиотеки; любой другой путь отклоняется (400), а не записывается в БД.
 */
object LibraryRootPolicy {
    fun canonical(path: String): Path? =
        runCatching { Path.of(path).toAbsolutePath().normalize() }.getOrNull()

    fun isUnderRoot(path: Path, root: Path): Boolean = path.startsWith(root)

    /**
     * @return нормализованный путь, если он допустим; `null` — если запрос нужно отклонить.
     */
    fun validateRequestedRoot(
        requested: String,
        current: String,
        roots: List<String> = PROJECT_ROOT_FOLDERS,
        pathExists: (Path) -> Boolean = { Files.isDirectory(it) },
    ): String? {
        if (requested.isBlank()) return null
        val raw = runCatching { Path.of(requested) }.getOrNull() ?: return null
        if (!raw.isAbsolute) return null
        val path = raw.normalize()
        if (requested.contains("..")) return null
        val allowed = roots.mapNotNull { canonical(it) }
        if (allowed.isEmpty()) return null
        val root = allowed.firstOrNull { isUnderRoot(path, it) } ?: return null
        if (Files.isSymbolicLink(path)) return null
        val realRoot = runCatching { root.toRealPath() }.getOrNull() ?: return null
        val realPath = runCatching { path.toRealPath() }.getOrNull() ?: return null
        if (!realPath.startsWith(realRoot)) return null
        if (!pathExists(path)) return null
        val currentCanonical = canonical(current)
        if (currentCanonical != null && currentCanonical != path) return null
        return path.toString()
    }
}

/**
 * BR-063: сохранение флагов публикации после сетевого вызова.
 *
 * Объект `song`, загруженный до `sendVideo` (десятки секунд), устаревает. Писать его
 * целиком нельзя: `getDiff` увидит чужие изменения и перезапишет их старым значением
 * (терялся `idTelegramDemo`). Финальный save выполняется на СВЕЖЕЙ строке, куда
 * переносятся только флаги публикации.
 */
object SongPublicationFlagsPolicy {
    fun copyFlags(
        from: Song,
        to: Song,
    ) {
        to.newsPremiumPublishPending = from.newsPremiumPublishPending
        to.newsPremiumTelegramSent = from.newsPremiumTelegramSent
        to.newsPremiumVkSent = from.newsPremiumVkSent
        to.premiumAutoPublishState = from.premiumAutoPublishState
        to.premiumAttemptCount = from.premiumAttemptCount
        to.premiumAutoPublishLastError = from.premiumAutoPublishLastError
    }
}

/**
 * BR-064: пауза между запросами массового поиска текста.
 *
 * Таймаут — «минимальный интервал между последовательными запросами», а не пауза на
 * каждой итерации цикла. Песни, для которых запрос не выполняется, не должны спать.
 */
object LyricsSearchPacing {
    fun shouldRequest(
        haveSourceText: Boolean,
        idsCount: Int,
    ): Boolean = !haveSourceText || idsCount == 1

    /** Сколько миллисекунд ждать перед фактическим запросом. */
    fun delayBeforeRequest(
        lastRequestAtMs: Long?,
        nowMs: Long,
        intervalMs: Long,
    ): Long {
        if (lastRequestAtMs == null || intervalMs <= 0) return 0
        return (intervalMs - (nowAtLeast(lastRequestAtMs, nowMs))).coerceAtLeast(0)
    }

    private fun nowAtLeast(
        lastRequestAtMs: Long,
        nowMs: Long,
    ): Long = (nowMs - lastRequestAtMs).coerceAtLeast(0)
}

/**
 * BR-109: политика путей SmartCopy.
 *
 * Экспортный каталог задаёт администратор (может быть внешним диском), поэтому жёсткий
 * корень по умолчанию не навязывается. Fail-closed: путь обязан быть абсолютным и
 * нормализованным, не указывать в системные каталоги и в исходную библиотеку; если задан
 * список разрешённых корней (`-Dkaraoke.smartcopy.allowed-roots` или
 * `SMARTCOPY_ALLOWED_ROOTS`), путь обязан лежать внутри него. Имя файла из шаблона
 * санитайзится, `..` и разделители запрещены.
 */
object SmartCopyPathPolicy {
    private val systemRoots =
        listOf(
            "/bin",
            "/boot",
            "/dev",
            "/etc",
            "/lib",
            "/lib32",
            "/lib64",
            "/proc",
            "/root",
            "/sbin",
            "/sys",
            "/usr",
            "/var",
        )

    fun configuredAllowedRoots(
        systemProperty: String = System.getProperty("karaoke.smartcopy.allowed-roots").orEmpty(),
        env: String = System.getenv("SMARTCOPY_ALLOWED_ROOTS").orEmpty(),
    ): List<String> =
        (if (systemProperty.isNotBlank()) systemProperty else env)
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    /**
     * @return нормализованный каталог экспорта либо `null`, если путь запрещён.
     */
    fun validateExportRoot(
        scPath: String,
        allowedRoots: List<String> = configuredAllowedRoots(),
        protectedRoots: List<String> = PROJECT_ROOT_FOLDERS,
    ): String? {
        if (scPath.isBlank()) return null
        if (scPath.contains("..")) return null
        val raw = runCatching { Path.of(scPath) }.getOrNull() ?: return null
        // Относительный путь резолвился бы от CWD процесса — это не экспортный корень.
        if (!raw.isAbsolute) return null
        val path = raw.normalize()
        if (!path.isAbsolute) return null
        if (path.nameCount == 0) return null
        val normalized = path.toString()
        if (systemRoots.any { normalized == it || normalized.startsWith("$it/") }) return null
        val protectedCanonical = protectedRoots.mapNotNull { LibraryRootPolicy.canonical(it) }
        if (protectedCanonical.any { path.startsWith(it) }) return null
        if (allowedRoots.isNotEmpty()) {
            val allowed = allowedRoots.mapNotNull { LibraryRootPolicy.canonical(it) }
            if (allowed.none { path.startsWith(it) }) return null
        }
        return normalized
    }

    /**
     * Санитайзит шаблон имени (плейсхолдеры `{author}` и т.п. остаются как есть).
     *
     * @return безопасный шаблон либо `null`, если он даёт недопустимое имя.
     */
    fun sanitizeDestinationTemplate(template: String): String? {
        if (template.isBlank()) return null
        val sanitized = template.sanitizeSongFileName()
        if (sanitized.isBlank()) return null
        if (sanitized == "." || sanitized == ".." || sanitized.contains("..")) return null
        if (sanitized.contains('/') || sanitized.contains('\\') || sanitized.contains('\u0000')) return null
        return sanitized
    }

    /**
     * Санитайзит итоговое имя файла, собранное из шаблона.
     *
     * @return безопасное имя либо `null`, если шаблон даёт недопустимое имя.
     */
    fun sanitizeDestinationFileName(raw: String): String? = sanitizeDestinationTemplate(raw)
}
