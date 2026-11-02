package com.svoemesto.karaokeapp

import org.slf4j.LoggerFactory
import java.io.EOFException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.sql.SQLException

/**
 * KARAOKE-386: прозрачная устойчивость к «протухшим» соединениям БД.
 *
 * ## Проблема
 *
 * `KaraokeConnection` кэширует физическое соединение **на поток** (specs/087) и лечит его
 * после `SQLException` (specs/236, BR-274: прокси помечает соединение «битым», следующий
 * `getConnection()` переоткрывает). Но **текущий** вызов, на котором умер сокет, всё равно
 * падает: пользователь видит «Ошибка ввода/вывода при отправке бэкенду» / «Connection reset».
 *
 * Так было 2026-11-02: после восстановления allow-list на проде соединения, открытые до
 * reload'а nginx, оказались мёртвыми — и несколько запросов подряд упали в интерфейс, прежде
 * чем кэш потоков пересоздал каналы.
 *
 * ## Решение
 *
 * Тонкая обёртка вокруг **идемпотентных** операций (чтение): если операция упала ошибкой
 * уровня соединения, соединение помечается битым и операция выполняется ещё раз — уже на
 * свежем канале. Ошибки логики SQL (например нарушение уникальности `23505`) не повторяются
 * никогда: повтор не должен маскировать настоящую ошибку данных.
 *
 * После исчерпания попыток бросается [DatabaseUnavailableException] — с машинным кодом и
 * человекочитаемым текстом для интерфейса; исходная причина остаётся в `cause` для разбора
 * (см. KARAOKE-387 про формат логов).
 */
object DbAvailability {
    private val log = LoggerFactory.getLogger("infra.db.availability")

    /** Код «связи нет» — для интерфейса и для грепа в логах. */
    const val CODE_UNAVAILABLE = "DB-UNAVAILABLE-001"

    /** Код «повтор после обрыва». */
    const val CODE_RETRY = "DB-RETRY-001"

    /** Сколько всего попыток делает одна идемпотентная операция (1 + один повтор). */
    const val MAX_ATTEMPTS = 2

    /**
     * Признак ошибки уровня соединения: сеть/сокет/канал умер, а не «неверный SQL».
     *
     * Смотрим всю цепочку `cause` (pgjdbc оборачивает `SocketException` в `PSQLException`) и
     * `SQLState`: класс `08` — connection exception, `57P01/57P02/57P03` — сервер выключается
     * или не принимает соединения.
     */
    fun isConnectionFailure(error: Throwable?): Boolean {
        var current = error
        var depth = 0
        while (current != null && depth < 10) {
            when (current) {
                is SocketException, is SocketTimeoutException, is EOFException,
                is ConnectException, is NoRouteToHostException,
                -> return true
            }
            if (current is SQLException) {
                val sqlState = current.sqlState
                if (sqlState != null) {
                    if (sqlState.startsWith("08")) return true
                    if (sqlState == "57P01" || sqlState == "57P02" || sqlState == "57P03") return true
                }
            }
            val message = current.message?.lowercase()
            if (message != null) {
                if (message.contains("connection reset") ||
                    message.contains("broken pipe") ||
                    message.contains("ввода/вывода") ||
                    message.contains("соединение уже было закрыто") ||
                    message.contains("connection is closed") ||
                    message.contains("connection has been closed")
                ) {
                    return true
                }
            }
            current = current.cause
            depth++
        }
        return false
    }

    /**
     * Человекочитаемый текст для интерфейса — без Java-классов и стектрейсов
     * (см. KARAOKE-386 и KARAOKE-387).
     */
    fun userMessage(operation: String): String =
        "Нет связи с сервером базы данных. Операция «$operation» не выполнена — повторите через несколько секунд."

    /**
     * Выполняет [block] и, если он упал ошибкой уровня соединения, повторяет его **один раз**
     * на свежем соединении. Идемпотентность операции — ответственность вызывающего: обёртка
     * предназначена для чтения и для точечных записей по ключу.
     *
     * @param database соединение, чей кэш нужно пометить битым перед повтором.
     * @param operation человекочитаемое имя операции (для лога и сообщения пользователю).
     * @throws DatabaseUnavailableException если повтор не помог.
     */
    fun <T> withRetryOnConnectionFailure(
        database: KaraokeConnection,
        operation: String,
        block: () -> T,
    ): T {
        var attempt = 1
        while (true) {
            try {
                return block()
            } catch (e: Exception) {
                if (!isConnectionFailure(e)) throw e
                if (attempt >= MAX_ATTEMPTS) {
                    throw DatabaseUnavailableException(
                        code = CODE_UNAVAILABLE,
                        operation = operation,
                        userMessage = userMessage(operation),
                        cause = e,
                    )
                }
                attempt++
                // Помечаем соединение битым: следующий getConnection() в этом потоке
                // откроет новый канал (specs/236).
                database.markConnectionBroken()
                log.warn(
                    "db.retry operation={} target={} attempt={} reason={} code={}",
                    operation,
                    database.name,
                    attempt - 1,
                    e.javaClass.simpleName,
                    CODE_RETRY,
                )
            }
        }
    }
}

/**
 * Связь с БД потеряна и повтор не помог (KARAOKE-386).
 *
 * [code] — машинный код для логов и поддержки, [userMessage] — готовый текст для интерфейса;
 * исходная причина лежит в `cause`, поэтому разбор не теряется.
 */
class DatabaseUnavailableException(
    val code: String,
    val operation: String,
    val userMessage: String,
    cause: Throwable? = null,
) : RuntimeException("$code operation=$operation: $userMessage", cause)
