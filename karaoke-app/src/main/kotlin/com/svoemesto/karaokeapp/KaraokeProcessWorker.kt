package com.svoemesto.karaokeapp

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.svoemesto.karaokeapp.model.*
import com.svoemesto.karaokeapp.services.KSS_APP
import com.svoemesto.karaokeapp.services.KaraokeStorageService
import com.svoemesto.karaokeapp.services.SAC_APP
import com.svoemesto.karaokeapp.services.SNS
import com.svoemesto.karaokeapp.services.StorageApiClient
import org.springframework.stereotype.Component
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.URL
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDateTime
import java.util.Base64
import javax.net.ssl.HttpsURLConnection

/**
 * Поток-обёртка вокруг OS-процесса (`ProcessBuilder`) для одного задания
 * `KaraokeProcess`. Создаётся на каждое задание, парсит stdout (ffmpeg
 * `time=`, Sheetsage `NN%|` и др.) и обновляет [percentage] в реальном
 * времени. После завершения subprocess обновляет статус задания
 * (`DONE`/`ERROR`/`WAITING`) и (если применимо) пересчитывает
 * `HealthReport` песни.
 *
 * Жизненный цикл:
 * 1. `WORKING` (начало), `start = now()` сохраняется в БД.
 * 2. Если `args[0][0] == "runFunctionWithArgs"` — выполняется Kotlin-функция
 *    (например, `KEY_BPM_FROM_FILE`, `UPLOAD_TO_REMOTE_STORE`).
 *    Иначе — запускается subprocess.
 * 3. Stdout читается построчно, регексы извлекают `percentage`.
 * 4. По завершении — статус `DONE` (успех) / `ERROR` (без success) /
 *    `WAITING` (если [forceStopped]).
 * 5. Пост-хук: если задание относится к [com.svoemesto.karaokeapp.HealthReport.HR_REPAIR_PROCESS_TYPES]
 *    и не было форс-стопа, вызывается `HealthReport.onRepairProcessFinished`.
 *
 * Потокобезопасность: [forceStopped] и [osProcess] помечены `@Volatile` —
 * читаются из `KaraokeProcessWorker.forceStop()` (другой поток) для
 * принудительной остановки.
 *
 * @property karaokeProcess задание из `tbl_processes` (null в тестах).
 * @property percentage прогресс выполнения 0..100 (строка, обновляется из stdout).
 * @property forceStopped взводится извне ДО убийства subprocess, чтобы
 *   завершение перевело задание в `WAITING` (а не в `DONE`/`ERROR`).
 * @property osProcess ссылка на OS-процесс для force-stop (`process.destroyForcibly()`).
 * @see docs/features/async-process-queue.md
 * @see KaraokeProcessWorker главный воркер, создаёт и запускает потоки
 * @see KaraokeProcess модель задания
 */

/**
 * Класс Karaoke Process Thread.
 *
 * @see docs/features/async-process-queue.md
 */
class KaraokeProcessThread(
    val karaokeProcess: KaraokeProcess? = null,
    var percentage: String? = null,
) : Thread() {
    // Принудительная остановка (форс-стоп): выставляется извне (KaraokeProcessWorker.forceStop) ДО убийства
    // docker-контейнера/подпроцесса. Пока флаг взведён, завершившийся поток выставляет WAITING (а не DONE/ERROR),
    // чтобы задание переиграло заново, и пропускает пост-хук HealthReport.
    @Volatile var forceStopped: Boolean = false

    // Ссылка на родительский OS-процесс (docker/docker compose/ffmpeg) — чтобы форс-стоп мог его добить.
    @Volatile var osProcess: java.lang.Process? = null

    override fun run() {
        super.run()
        if (karaokeProcess != null) {
            // Одноразовый поток задания (в отличие от переиспользуемых потоков Tomcat/doStart()) —
            // явно освобождает своё ThreadLocal-соединение по завершении (specs/091-fix-connection-leak),
            // иначе оно остаётся открытым в PostgreSQL навсегда, так как поток больше не обратится к БД.
            try {
                karaokeProcess.status = KaraokeProcessStatuses.WORKING.name
                karaokeProcess.start = Timestamp.from(Instant.now())
                karaokeProcess.save()

                if (karaokeProcess.args[0][0] == "runFunctionWithArgs") {
                    val params = parseRunFunctionWithArgsParams(karaokeProcess.args[0])
                    percentage = "0"
                    println(
                        "[${Timestamp.from(
                            Instant.now(),
                        )}] KaraokeProcessThread[${karaokeProcess.threadId}]: Начинаем работу с заданием: ${karaokeProcess.name} - [${karaokeProcess.type}] - ${karaokeProcess.description}",
                    )
                    try {
                        val success =
                            when (KaraokeProcessTypes.valueOf(karaokeProcess.type)) {
                                KaraokeProcessTypes.KEY_BPM_FROM_FILE -> executeGetKeyBpmFromFile(params)
                                KaraokeProcessTypes.UPLOAD_TO_LOCAL_STORE ->
                                    executeUploadToLocalStore(
                                        params,
                                    ) { pct -> percentage = pct.toString() }
                                KaraokeProcessTypes.UPLOAD_TO_REMOTE_STORE ->
                                    executeUploadToRemoteStore(params) { pct ->
                                        percentage =
                                            pct.toString()
                                    }
                                KaraokeProcessTypes.STEM_JOB_DEMUCS2,
                                KaraokeProcessTypes.STEM_JOB_DEMUCS5,
                                -> executeFinalizeStemJob(params)
                                KaraokeProcessTypes.DEMUCS2,
                                KaraokeProcessTypes.DEMUCS5,
                                -> executeFinalizeDemucs(params)
                                KaraokeProcessTypes.RENDER_MP4_LYRICS,
                                KaraokeProcessTypes.RENDER_MP4_KARAOKE,
                                KaraokeProcessTypes.RENDER_MP4_CHORDS,
                                KaraokeProcessTypes.RENDER_MP4_TABS,
                                KaraokeProcessTypes.RENDER_MP4_DEMO,
                                -> executeRenderMp4(params) { pct -> percentage = pct.toString() }
                                KaraokeProcessTypes.FORCED_ALIGN_MARKERS -> executeForcedAlignMarkers(params)
                                else -> false
                            }
                        if (forceStopped) {
                            // Форс-стоп: возвращаем задание в очередь, чтобы оно переиграло заново.
                            println(
                                "[${Timestamp.from(
                                    Instant.now(),
                                )}] KaraokeProcessThread[${karaokeProcess.threadId}]: WAITING (форс-стоп) задание: ${karaokeProcess.name} - [${karaokeProcess.type}] - ${karaokeProcess.description}",
                            )
                            karaokeProcess.status = KaraokeProcessStatuses.WAITING.name
                            karaokeProcess.save()
                        } else {
                            println(
                                "[${Timestamp.from(
                                    Instant.now(),
                                )}] KaraokeProcessThread[${karaokeProcess.threadId}]: ${if (success) "DONE успешно завершенное" else "ERROR (данные не найдены)"} задание: ${karaokeProcess.name} - [${karaokeProcess.type}] - ${karaokeProcess.description}",
                            )
                            karaokeProcess.status = (if (success) KaraokeProcessStatuses.DONE else KaraokeProcessStatuses.ERROR).name
                            karaokeProcess.end = Timestamp.from(Instant.now())
                            karaokeProcess.priority = if (success) 999 else -1
                            percentage = "100"
                            karaokeProcess.save()
                        }
                    } catch (e: Exception) {
                        if (forceStopped) {
                            println(
                                "[${Timestamp.from(
                                    Instant.now(),
                                )}] KaraokeProcessThread[${karaokeProcess.threadId}]: WAITING (форс-стоп) задание: ${karaokeProcess.name} - [${karaokeProcess.type}] - ${karaokeProcess.description}",
                            )
                            karaokeProcess.status = KaraokeProcessStatuses.WAITING.name
                            karaokeProcess.save()
                        } else {
                            println(
                                "[${Timestamp.from(
                                    Instant.now(),
                                )}] KaraokeProcessThread[${karaokeProcess.threadId}]: ERROR задание: ${karaokeProcess.name} - [${karaokeProcess.type}] - ${karaokeProcess.description}: ${e.message}",
                            )
                            karaokeProcess.status = KaraokeProcessStatuses.ERROR.name
                            karaokeProcess.end = Timestamp.from(Instant.now())
                            karaokeProcess.priority = -1
                            percentage = "100"
                            karaokeProcess.save()
                        }
                    }
                } else {
                    val regex = Regex("Current Frame:\\s+(\\d+), percentage:\\s+(\\d+)")
                    val regexDuration = Regex("Duration:\\s+(\\d\\d:\\d\\d:\\d\\d\\.\\d\\d),")
                    val regexCurrent = Regex("time=(\\d\\d:\\d\\d:\\d\\d\\.\\d\\d)")
                    val regexPercentageSheetsage = Regex("^\\s{0,2}(\\d{1,3})%\\|")
                    val karaokeProcessType = karaokeProcess.type
                    val typeEnum = runCatching { KaraokeProcessTypes.valueOf(karaokeProcessType) }.getOrNull()
                    // Лимит CPU пересобирается заново прямо перед стартом (не берётся "как есть" из БД) -
                    // настройки могли поменяться, пока задание стояло в очереди WAITING.
                    val args = if (typeEnum != null) refreshArgvCpuLimit(typeEnum, karaokeProcess.args[0]) else karaokeProcess.args[0]
                    val envs = if (typeEnum != null) refreshEnvCpuLimit(typeEnum, karaokeProcess.envs) else karaokeProcess.envs
                    val processBuilder = ProcessBuilder(args)

                    val processBuilderEnvironment = processBuilder.environment()
                    processBuilderEnvironment.putAll(envs)

                    processBuilder.redirectErrorStream(true)

                    // try охватывает и сам запуск subprocess (не только чтение stdout) - если
                    // processBuilder.start() бросит исключение (например, недоступен бинарник/docker),
                    // задание всё равно должно получить терминальный статус (ERROR/WAITING), а не остаться
                    // в WORKING навсегда и заблокировать очередь своего лейна. См. specs/029-fix-queue-lane-stall/research.md.
                    var process: Process? = null
                    try {
                        val startedProcess = processBuilder.start()
                        process = startedProcess
                        osProcess = startedProcess
                        if (startedProcess.isAlive) {
                            if (karaokeProcess.command != "tail" || karaokeProcess.args[0][0] !in KaraokeProcessWorker.argsIgnoredToLog) {
                                println(
                                    "[${Timestamp.from(
                                        Instant.now(),
                                    )}] KaraokeProcessThread[${karaokeProcess.threadId}]: Установка приоритета задания: ${karaokeProcess.name} - [${karaokeProcess.type}] - ${karaokeProcess.description}",
                                )
                            }
                            setProcessPriority(startedProcess.pid(), karaokeProcess.prioritet)
                        }

                        if (karaokeProcess.command != "tail" || karaokeProcess.args[0][0] !in KaraokeProcessWorker.argsIgnoredToLog) {
                            println(
                                "[${Timestamp.from(
                                    Instant.now(),
                                )}] KaraokeProcessThread[${karaokeProcess.threadId}]: Начинаем работу с заданием: ${karaokeProcess.name} - [${karaokeProcess.type}] - ${karaokeProcess.description}",
                            )
                            KaraokeProcessWorker.sendCountWaitingMessage(KaraokeProcess.getCountWaiting(database = karaokeProcess.database))
                        }
                        val inputStream = startedProcess.inputStream
                        var duration: String? = null
                        val reader = BufferedReader(InputStreamReader(inputStream))
                        var line: String? = reader.readLine()
                        var log = ""
                        var lastLine = ""
                        while (line != null) {
                            lastLine = line
                            log += "[${Timestamp.from(Instant.now())}] $line\n"
                            val matchResult = regex.find(line)
                            if (matchResult != null) {
//                        val currentFrame = matchResult.groupValues[1]
                                val percentage = matchResult.groupValues[2]
                                this.percentage = percentage
                            } else {
                                if (duration != null) {
                                    val matchResultCurrent = regexCurrent.find(line)
                                    if (matchResultCurrent != null) {
                                        val current = matchResultCurrent.groupValues[1]
                                        this.percentage =
                                            (
                                                (
                                                    (
                                                        convertTimecodeToMilliseconds(current).toDouble() /
                                                            convertTimecodeToMilliseconds(duration).toDouble()
                                                    ) *
                                                        10000
                                                ).toInt().toDouble() /
                                                    100
                                            ).toString()
                                    }
                                } else {
                                    val matchResultDuration = regexDuration.find(line)
                                    if (matchResultDuration != null) {
                                        duration = matchResultDuration.groupValues[1]
                                    } else {
                                        val matchResultPercentageSheetsage = regexPercentageSheetsage.find(line)
                                        if (matchResultPercentageSheetsage != null) {
                                            val percentage = matchResultPercentageSheetsage.groupValues[1]
                                            this.percentage = percentage
                                        }
                                    }
                                }
                            }
                            line = reader.readLine()
                        }
                        if (karaokeProcess.command != "tail" || karaokeProcess.args[0][0] !in KaraokeProcessWorker.argsIgnoredToLog) {
                            println(
                                "[${Timestamp.from(
                                    Instant.now(),
                                )}] KaraokeProcessThread[${karaokeProcess.threadId}]: Завершаем работу с заданием: ${karaokeProcess.name} - [${karaokeProcess.type}] - ${karaokeProcess.description}",
                            )
                        }
                        if (log != "") {
                            if (karaokeProcess.command != "tail" || karaokeProcess.args[0][0] !in KaraokeProcessWorker.argsIgnoredToLog) {
                                println(
                                    "[${Timestamp.from(
                                        Instant.now(),
                                    )}] KaraokeProcessThread[${karaokeProcess.threadId}]: Выводим лог задания: ${karaokeProcess.name} - [${karaokeProcess.type}] - ${karaokeProcess.description}",
                                )
                            }
                            log = args.joinToString(" ") + "\n\n" + log
                            val logFileName =
                                "$PATH_TO_LOGS/[${Timestamp.from(
                                    Instant.now(),
                                )}] ${karaokeProcess.name} - ${karaokeProcess.description}.log".rightFileName()
                            try {
                                File(logFileName).writeText(log, Charsets.UTF_8)
                                runCommand(listOf("chmod", "666", logFileName))
                            } catch (e: Exception) {
                                println(e.message)
                            }
                        }

                        if (karaokeProcess.type == "SHEETSAGE" &&
                            lastLine == "NotImplementedError: Dynamic chunking not implemented. Try halving measures_per_chunk."
                        ) {
                            // Если процесс SHEETSAGE завершился ошибкой - создаём для этой же песни процесс SHEETSAGE2 с таким же приоритетом
                            val song =
                                Song.loadFromDbById(
                                    id = karaokeProcess.songId.toLong(),
                                    database = WORKING_DATABASE,
                                    storageService = KSS_APP,
                                    storageApiClient = SAC_APP,
                                )
                            song?.let {
                                KaraokeProcess.createProcess(
                                    song = song,
                                    action = KaraokeProcessTypes.SHEETSAGE2,
                                    doWait = true,
                                    prior = karaokeProcess.priority,
                                    threadId = 0,
                                )
                            }
                        }

                        if (forceStopped) {
                            // Форс-стоп: подпроцесс завершился из-за убитого docker-контейнера — возвращаем в очередь.
                            println(
                                "[${Timestamp.from(
                                    Instant.now(),
                                )}] KaraokeProcessThread[${karaokeProcess.threadId}]: WAITING (форс-стоп) задание: ${karaokeProcess.name} - [${karaokeProcess.type}] - ${karaokeProcess.description}",
                            )
                            karaokeProcess.status = KaraokeProcessStatuses.WAITING.name
                            karaokeProcess.save()
                        } else {
                            if (karaokeProcess.command != "tail" || karaokeProcess.args[0][0] !in KaraokeProcessWorker.argsIgnoredToLog) {
                                println(
                                    "[${Timestamp.from(
                                        Instant.now(),
                                    )}] KaraokeProcessThread[${karaokeProcess.threadId}]: DONE успешно завершенное задание: ${karaokeProcess.name} - [${karaokeProcess.type}] - ${karaokeProcess.description}",
                                )
                            }
                            karaokeProcess.status = KaraokeProcessStatuses.DONE.name
                            karaokeProcess.end = Timestamp.from(Instant.now())
                            karaokeProcess.priority = 999
                            karaokeProcess.save()
                        }

//                if (karaokeProcess.type == KaraokeProcessTypes.DEMUCS2.name) {
//                    KaraokeProcess.delete(karaokeProcess.id, karaokeProcess.database)
//                }
                    } catch (e: Exception) {
                        process?.destroy()
                        if (forceStopped) {
                            println(
                                "[${Timestamp.from(
                                    Instant.now(),
                                )}] KaraokeProcessThread[${karaokeProcess.threadId}]: WAITING (форс-стоп) задание: ${karaokeProcess.name} - [${karaokeProcess.type}] - ${karaokeProcess.description}",
                            )
                            karaokeProcess.status = KaraokeProcessStatuses.WAITING.name
                            karaokeProcess.save()
                        } else {
                            println(
                                "[${Timestamp.from(
                                    Instant.now(),
                                )}] KaraokeProcessThread[${karaokeProcess.threadId}]: ERROR задание: ${karaokeProcess.name} - [${karaokeProcess.type}] - ${karaokeProcess.description}: ${e.message}",
                            )
                            karaokeProcess.status = KaraokeProcessStatuses.ERROR.name
                            karaokeProcess.end = Timestamp.from(Instant.now())
                            karaokeProcess.priority = -1
                            karaokeProcess.save()
                        }
                    }
                }

                // Пост-хук: после завершения репаир-задания пересчитать HealthReport песни и разослать SSE
                // (иначе счётчик ошибок в таблице «застывает» на IN_PROGRESS). Для песен в каскаде
                // «Исправить всё» — поставить следующий ставший решаемым шаг. Ограничение по типам исключает
                // многократный пересчёт на sub-шагах тяжёлых MELT_*-рендеров.
                val kp = karaokeProcess
                val typeEnum = runCatching { KaraokeProcessTypes.valueOf(kp.type) }.getOrNull()
                if (!forceStopped && kp.songId > 0 && typeEnum in HealthReport.HR_REPAIR_PROCESS_TYPES) {
                    try {
                        HealthReport.onRepairProcessFinished(
                            songId = kp.songId.toLong(),
                            success = kp.status == KaraokeProcessStatuses.DONE.name,
                            database = WORKING_DATABASE,
                            storageService = KSS_APP,
                            storageApiClient = SAC_APP,
                        )
                    } catch (e: Exception) {
                        println(
                            "[${Timestamp.from(
                                Instant.now(),
                            )}] KaraokeProcessThread[${kp.threadId}]: ошибка пересчёта HealthReport после задания: ${e.message}",
                        )
                    }
                }
            } finally {
                karaokeProcess.database.closeThreadConnection()
            }
        }
    }
}

/**
 * Главный Spring-компонент очереди задач Karaoke. Цикл, который берёт
 * `WAITING` задания из `tbl_processes` (отсортированные по приоритету),
 * запускает их через [KaraokeProcessThread] в соответствующем `threadId`-
 * лейне, и обрабатывает периодические проверки (SearchAsync, Song.
 * requestNewSongLastTimeMs, и др.).
 *
 * Потокобезопасность: все mutable-состояния (`isWork`, `stopAfterThreadIsDone`,
 * `runningThreadsCheckCounter`, `threadsMap`) живут в `companion object`
 * и доступны из разных потоков (web-запросы, периодические scheduler'ы).
 *
 * Архитектура (см. [docs/features/async-process-queue.md]):
 * - `KaraokeProcessTypes` — enum типов заданий (ffmpeg, melt, Demucs, Sheetsage,
 *   UPLOAD_TO_REMOTE_STORE, KEY_BPM_FROM_FILE, STEM_JOB_DEMUCS2/5, RENDER_MP4_*).
 * - `THREAD_LANE_HEAVY_RENDER=0` — тяжёлые CPU-задачи (рендер MLT, MP4).
 * - `THREAD_LANE_LIGHT_BACKGROUND=-1` — копирование, symlink, мелочи.
 * - `THREAD_LANE_REMOTE_STORE_UPLOAD=-2` — загрузка в MinIO.
 * - `THREAD_LANE_STEM_JOBS=-3` — премиум-стемы.
 * - CPU-лимит — `MLT_CPU_LIMIT` env (см. `docker update`).
 *
 * Жизненный цикл воркера:
 * - `start()` — если не запущен: `KaraokeProcess.deleteDone()` (cleanup),
 *   `KaraokeProcess.setWorkingToWaiting()` (recovery после падения),
 *   цикл `while (isWork)` берёт задания и запускает потоки.
 * - `stop()` — `doStop()` убивает текущие потоки (`destroyForcibly()`),
 *   ставит флаг `stopAfterThreadIsDone`.
 * - `runningThreadsCheckCounter` (каждые 50 итераций × 10мс = 500мс) — рассылает
 *   SSE `processWorkerState` для UI-прогресса.
 *
 * @see docs/features/async-process-queue.md
 * @see KaraokeProcessThread обёртка subprocess
 * @see KaraokeProcess модель задания
 * @see KaraokeProcessTypes типы заданий
 */

/**
 * Класс Karaoke Process Worker.
 *
 * @see docs/features/async-process-queue.md
 */
@Component
class KaraokeProcessWorker {
    companion object {
        /**
         * Список имён subprocess, stdout которых не логируется (мелкие
         * файловые операции — `ln`, `rm`, `chmod` и т.п. генерируют
         * слишком много шума в логе).
         */
        val argsIgnoredToLog = listOf("ln", "rm", "chmod", "mkdir", "cp", "mv")

        /**
         * Флаг работы воркера. Управляется через [start] / [stop]. Цикл
         * `while (isWork)` в [doStart] прерывается при `false`.
         *
         * `@Volatile` — читается/пишется как минимум из потока, исполняющего
         * [doStart] (демон-поток), и из HTTP-потоков, вызывающих
         * [stop]/[forceStop] — без этого запись могла быть не видна другому
         * потоку вовремя (JMM не гарантирует visibility для обычного `var`).
         */
        @Volatile var isWork: Boolean = false

        /**
         * Если `true` — после завершения текущего потока воркер остановится
         * (используется для «мягкой» остановки с ожиданием завершения).
         */
        @Volatile var stopAfterThreadIsDone: Boolean = false

        /**
         * Режим без UI-контроля (для batch-прогонов на admin-машине): пока хотя бы один ЖИВОЙ поток
         * в любом лейне обрабатывает задание с `karaokeProcess.withoutControl == true`, главный цикл
         * [doStart] не делает `Thread.sleep` между итерациями. Пересчитывается заново на каждой
         * итерации по фактически живым потокам ([threadsMap]) - НЕ хранит флаг "последнего стартовавшего
         * задания" (в любом лейне), иначе задание одного лейна могло бы молча менять поведение цикла
         * для задания из ДРУГОГО лейна. Per-lane-решения (сохранять ли дифф конкретного задания,
         * слать ли для него SSE-прогресс) используют `karaokeProcess.withoutControl` НАПРЯМУЮ у
         * задания своего потока, а не это поле.
         */
        @Volatile var withoutControl = false

        // Периодическая проверка активных потоков вне очереди (для SSE-прогресса).
        // Каждые ~500мс (50 итераций × 10мс) — достаточно для плавного прогресс-бара.
        var runningThreadsCheckCounter: Int = 0
        const val RUNNING_THREADS_CHECK_INTERVAL = 50

        // ConcurrentHashMap - карта мутируется как минимум из потока doStart() и из forceStop()
        // (вызывается на отдельном HTTP-потоке); обычный mutableMapOf() не даёт гарантий видимости
        // записи между потоками и не защищён от ConcurrentModificationException при одновременном
        // чтении/итерации в doStart() и записи в forceStop(). См. specs/029-fix-queue-lane-stall/research.md.
        val threadsMap: MutableMap<Int, KaraokeProcessThread?> = java.util.concurrent.ConcurrentHashMap()

        // Гарантирует, что цикл doStart() запускается не более чем в одном экземпляре одновременно:
        // без этой блокировки два быстрых подряд HTTP-вызова start() могли проверить `!isWork` оба
        // как true ДО того, как первый успеет выставить isWork=true, и оба запустить свой doStart() -
        // два параллельных воркера на общем threadsMap. См. Кандидат A в research.md.
        private val startStopLock = Any()

//        var workThread: KaraokeProcessThread? = null

        // specs/087-fix-shared-db-connection: если doStart() падает необработанным исключением (например,
        // из-за кратковременного сетевого сбоя до БД), пробуем возобновить его в том же демон-потоке до
        // MAX_START_ATTEMPTS раз, с нарастающей паузой между попытками - вместо немедленной остановки
        // очереди с ожиданием ручного клика оператора по алерту RenderQueueStalledCheck. isWork остаётся
        // true на время retry (для UI/мониторинга очередь всё ещё "работает" - что по сути верно, она
        // пытается продолжить). Если попытки исчерпаны (или isWork сброшен извне, см. forceStop()) -
        // применяется тот же safety-net, что и раньше (specs/029-fix-queue-lane-stall).
        private const val MAX_START_ATTEMPTS = 5
        private val START_RETRY_BACKOFF_MS = longArrayOf(2_000, 5_000, 15_000, 30_000, 60_000)

        /**
         * Запустить воркер (если ещё не запущен).
         *
         * Перед стартом: очищает `DONE` задания (`KaraokeProcess.deleteDone`),
         * восстанавливает `WORKING` → `WAITING` после возможного падения
         * (`setWorkingToWaiting`), рассылает SSE-сообщение с количеством
         * ожидающих. Цикл [doStart] выполняется в отдельном демон-потоке —
         * вызывающий HTTP-поток (`/api/processes/workerstartstop`) не
         * блокируется на всё время работы очереди.
         *
         * Если [doStart] падает необработанным исключением — до
         * [MAX_START_ATTEMPTS] попыток возобновить его в том же потоке, с
         * нарастающей паузой ([START_RETRY_BACKOFF_MS]) между попытками
         * (specs/087-fix-shared-db-connection). После исчерпания попыток —
         * прежний safety-net (`isWork=false`, `RenderQueueStalledCheck`
         * подхватывает одноклик-восстановлением).
         *
         * Если воркер уже запущен — сбрасывает `stopAfterThreadIsDone` и
         * отправляет текущее состояние через SSE. Проверка-и-установка
         * `isWork` защищена [startStopLock], чтобы два быстрых подряд вызова
         * `start()` не запустили два параллельных цикла [doStart].
         *
         * @param database подключение к БД (local/remote/virtual)
         * @param storageService MinIO-клиент (для типов с загрузкой)
         * @param storageApiClient HTTP-клиент (для типов с загрузкой на remote)
         * @see doStart внутренний цикл
         * @see stop остановка
         */
        fun start(
            database: KaraokeConnection,
            storageService: KaraokeStorageService,
            storageApiClient: StorageApiClient,
        ) {
            val shouldLaunch =
                synchronized(startStopLock) {
                    if (isWork) {
                        false
                    } else {
                        isWork = true
                        true
                    }
                }
            if (!shouldLaunch) {
                stopAfterThreadIsDone = false
                sendStateMessage()
                return
            }
            KaraokeProcess.deleteDone(database)
            KaraokeProcess.setWorkingToWaiting(database)
            sendCountWaitingMessage(KaraokeProcess.getCountWaiting(database))
            Thread {
                try {
                    var attempt = 0
                    while (true) {
                        try {
                            doStart(database = database, storageService = storageService, storageApiClient = storageApiClient)
                            break
                        } catch (e: Exception) {
                            attempt++
                            println(
                                "[${Timestamp.from(
                                    Instant.now(),
                                )}] ProcessWorker: главный цикл очереди упал с необработанным исключением " +
                                    "(попытка $attempt/$MAX_START_ATTEMPTS): ${e.message}",
                            )
                            e.printStackTrace()
                            if (attempt >= MAX_START_ATTEMPTS || !isWork) {
                                println(
                                    "[${Timestamp.from(Instant.now())}] ProcessWorker: " +
                                        if (!isWork) {
                                            "очередь остановлена извне во время повторных попыток - retry прекращён"
                                        } else {
                                            "попытки восстановления ($MAX_START_ATTEMPTS) исчерпаны, очередь остановлена"
                                        },
                                )
                                break
                            }
                            val pauseMs = START_RETRY_BACKOFF_MS[(attempt - 1).coerceAtMost(START_RETRY_BACKOFF_MS.lastIndex)]
                            println(
                                "[${Timestamp.from(Instant.now())}] ProcessWorker: пробуем возобновить через ${pauseMs}мс",
                            )
                            Thread.sleep(pauseMs)
                        }
                    }
                } catch (e: Exception) {
                    // Ловит в т.ч. InterruptedException из Thread.sleep(pauseMs) выше - без этого внешнего
                    // catch/finally такое исключение вышло бы из Thread.run() мимо блока очистки ниже,
                    // оставив isWork=true навсегда (тот самый зомби-баг, устранённый в specs/029).
                    println(
                        "[${Timestamp.from(Instant.now())}] ProcessWorker: retry-цикл прерван необработанным исключением: ${e.message}",
                    )
                    e.printStackTrace()
                } finally {
                    // Не оставляем isWork=true, если цикл завершился сам (штатно, после исчерпания retry,
                    // либо из-за исключения выше) - иначе очередь выглядит "работающей" в UI/мониторинге,
                    // но фактически мертва, и RenderQueueStalledCheck (который смотрит только на isWork)
                    // не смог бы это обнаружить.
                    synchronized(startStopLock) {
                        isWork = false
                        stopAfterThreadIsDone = false
                    }
                    sendStateMessage()
                }
            }.apply {
                isDaemon = true
                name = "karaoke-process-worker"
            }.start()
        }

        /**
         * Остановить воркер. Если воркер запущен — вызывает [doStop]
         * (убивает текущие потоки, ставит флаг остановки) и рассылает
         * SSE-сообщение о новом состоянии.
         *
         * @see start запуск
         * @see doStop внутренняя остановка
         */
        fun stop() {
            if (isWork) {
                doStop()
                sendStateMessage()
            }
        }

        /**
         * Рассылает SSE-сообщение с текущим состоянием воркера
         * (`isWork`, `stopAfterThreadIsDone`). UI использует это для
         * обновления индикатора активности очереди.
         *
         * Безопасно вызывать в любом потоке — SNS.send ловит исключения
         * и пишет в stdout (не пробрасывает).
         */
        fun sendStateMessage() {
            val messageProcessWorkerState =
                SseNotification.processWorkerState(
                    ProcessWorkerStateMessage(
                        isWork = isWork,
                        stopAfterThreadIsDone = stopAfterThreadIsDone,
                    ),
                )
            try {
                SNS.send(messageProcessWorkerState)
            } catch (e: Exception) {
                println(e.message)
            }
        }

        fun sendCountWaitingMessage(countWaiting: Long) {
            val messageProcessCountWaiting =
                SseNotification.processCountWaiting(
                    ProcessCountWaitingMessage(
                        countWaiting = countWaiting,
                    ),
                )
            try {
                SNS.send(messageProcessCountWaiting)
            } catch (e: Exception) {
                println(e.message)
            }
        }

        // throwOnError=true - вызывается только из главного цикла doStart(), сбой БД должен
        // пробрасываться наружу и запускать retry в start() (specs/088-fix-queue-swallowed-errors).
        private fun getKaraokeProcessesToStart(database: KaraokeConnection): Map<Int, KaraokeProcess> =
            KaraokeProcess.getProcessesToStart(database, throwOnError = true)

        private fun doStart(
            database: KaraokeConnection,
            storageService: KaraokeStorageService,
            storageApiClient: StorageApiClient,
        ) {
            val timeout = 10L
            var counter = 0L
            var id = 0L
//            var songId = 0L
//            var processType = ""
//            var percentage = 0.0

            val intervalCheckFiles = 24_000
            var requestNewSongTimeoutMs = Karaoke.requestNewSongTimeoutMs
            var requestNewSongLastTimeMs = Karaoke.requestNewSongLastTimeMs
            val requestResultTimeoutMs = Karaoke.requestResultTimeoutMs
            isWork = true
            stopAfterThreadIsDone = false
            sendStateMessage()
            println("[${Timestamp.from(Instant.now())}] ProcessWorker: Стартуем")

            while (isWork) {
                val currentTimeMs = System.currentTimeMillis()

                // Если нужно мониторить SearchAsync
                if (Karaoke.checkSearchAsync) {
                    // Получаем первый элемент из списка "не готовых" и "просроченных" SearchAsync
                    SearchAsync
                        .getSearchAsyncFirstNotDoneAndTimeout(
                            timeoutMs = requestResultTimeoutMs,
                            database = database,
                            storageService = storageService,
                            storageApiClient = storageApiClient,
                        )?.let { searchAsync ->

                            // Если таймаут истёк - надо отправить запрос готовности асинхронного запроса
                            println(
                                "Проверяем готовность асинхронного запроса, song id = ${searchAsync.songId}, id = ${searchAsync.id}, operation id = ${searchAsync.operationId}",
                            )
                            val url = URL("${Karaoke.requestAsyncOperationsUrlPrefix}${searchAsync.operationId}")
                            val connection = url.openConnection() as HttpsURLConnection
                            val iamToken = getIamToken()
                            try {
                                connection.apply {
                                    requestMethod = "GET"
                                    setRequestProperty("Authorization", "Bearer $iamToken")
                                    setRequestProperty("Content-Type", "application/json")
                                    setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                                    doOutput = false
                                    connectTimeout = 10000
                                    readTimeout = 30000
                                }

                                val responseCode = connection.responseCode

                                if (responseCode == 200) {
                                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                                    val mapper = ObjectMapper()
                                    val apiResponse: ApiResponseAsync =
                                        mapper.readValue(
                                            response,
                                            object : TypeReference<ApiResponseAsync>() {},
                                        )
                                    if (apiResponse.done == true) {
                                        // Асинхронный запрос уже выполнился
                                        if (apiResponse.response != null) {
                                            if (!apiResponse.response.rawData.isNullOrEmpty()) {
                                                searchAsync.rawData = String(Base64.getDecoder().decode(apiResponse.response.rawData))
                                                searchAsync.done = true
                                                searchAsync.lastRequestedAt = LocalDateTime.now().toTimestamp()
                                                searchAsync.save()
                                                println(
                                                    "Получен ответ: Асинхронный запрос выполнен, тело ответа ${searchAsync.rawData.length} символов.",
                                                )
                                            /*
                                            Вызываем обработка ссылок
                                             */
                                                val searchResults = SearchResult.getSearchResultsForSearchAsync(searchAsync = searchAsync)
                                                println("Для полученного ответа сформировано записей searchResults: ${searchResults.size}")
                                                val searchedRightResults = searchResults.filter { !it.wrongResult && it.text.isNotEmpty() }
                                                println("Из них записей с наличием текста: ${searchedRightResults.size}")
                                                if (searchedRightResults.isNotEmpty()) {
                                                    val songId = searchAsync.songId
                                                    Song
                                                        .loadFromDbById(
                                                            id = songId,
                                                            database = database,
                                                            storageService = storageService,
                                                            storageApiClient = storageApiClient,
                                                        )?.let { song ->
                                                            applyFoundLyricsIfMissing(song, searchedRightResults.map { it.text })
                                                        }
                                                }
                                            } else {
                                                println("Асинхронный запрос выполнен, но rawData пустой")
                                            }
                                        } else {
                                            println("Асинхронный запрос выполнен, но response пустой")
                                        }
                                    } else {
                                        // Асинхронный запрос еще не выполнился, надо ещё подождать
                                        println("Получен ответ: Асинхронный запрос ещё не выполнился, надо ещё подождать")
                                        searchAsync.lastRequestedAt = LocalDateTime.now().toTimestamp()
                                        searchAsync.save()
                                    }
                                } else {
                                    throw RuntimeException("Failed to search: $responseCode")
                                }
                            } catch (e: SocketTimeoutException) {
                                println("Exception details: ${e.message}, пропускаем.")
                            } catch (e: SocketException) {
                                println("Exception details: ${e.message}, пропускаем.")
                            } catch (e: Exception) {
                                println("Exception details: ${e.message}")
                                e.printStackTrace()
                                throw RuntimeException("HTTP request failed: ${e.message}", e)
                            } finally {
                                connection.disconnect()
                            }
                        }
                }

                if (Karaoke.checkLastAlbum) {
                    if (requestNewSongLastTimeMs + requestNewSongTimeoutMs < currentTimeMs) {
                        requestNewSongLastTimeMs = currentTimeMs
                        if (isVpnActive()) {
                            println(
                                "[${Timestamp.from(
                                    Instant.now(),
                                )}] ProcessWorker: Проверка нового альбома пропущена — ВПН включён. Отключите ВПН.",
                            )
                            Karaoke.requestNewSongLastTimeMs = requestNewSongLastTimeMs
                            Karaoke.requestNewSongLastTimeCode = millisecondsToTimeFormatted(requestNewSongLastTimeMs)
                        } else {
                            println("[${Timestamp.from(Instant.now())}] ProcessWorker: Проверка нового альбома...")
                            val (authorForRequest, album, reason) = checkLastAlbumYm()
                            if (reason >= 0) {
                                // Удачный запрос (может быть найден новый альбом или пустой код страницы)
                                Karaoke.requestNewSongLastSuccessAuthor = authorForRequest
                                Karaoke.requestNewSongLastSuccessTimeMs = currentTimeMs
                                Karaoke.requestNewSongLastSuccessTimeCode = millisecondsToTimeFormatted(currentTimeMs)
                                if (reason == 1) {
                                    // Найден новый альбом - сообщим об этом в сообщении
                                    SNS.send(
                                        SseNotification.message(
                                            Message(
                                                type = "info",
                                                head = "Новый альбом",
                                                body = "У автора «$authorForRequest» найден новый альбом «$album»",
                                            ),
                                        ),
                                    )
                                }
                            } else if (reason == -1) {
                                // Неудачный запрос, увеличиваем время таймаута
                                if (requestNewSongTimeoutMs < 3_600_000) {
                                    requestNewSongTimeoutMs += Karaoke.requestNewSongTimeoutIncreaseMs
                                    Karaoke.requestNewSongTimeoutMs = requestNewSongTimeoutMs
                                    Karaoke.requestNewSongTimeoutMin = requestNewSongTimeoutMs / 60_000L
                                }
                            } else if (reason == -3) {
                                // ВПН или просроченная авторизация — таймаут и автор не меняются
                            } else {
                                // Не удалось найти автора! - считаем что запрос был удачный, не нужно увеличивать таймаут
                                Karaoke.requestNewSongLastSuccessTimeMs = currentTimeMs
                                Karaoke.requestNewSongLastSuccessTimeCode = millisecondsToTimeFormatted(currentTimeMs)
                            }
                            Karaoke.requestNewSongLastTimeMs = requestNewSongLastTimeMs
                            Karaoke.requestNewSongLastTimeCode = millisecondsToTimeFormatted(requestNewSongLastTimeMs)
                            Karaoke.requestNewSongLastAuthor = authorForRequest

                            requestNewSongTimeoutMs = Karaoke.requestNewSongTimeoutMs
                            requestNewSongLastTimeMs = Karaoke.requestNewSongLastTimeMs
                        }
                    }
                }

                counter++
                // Признак "без контроля" пересчитывается на каждой итерации по фактически ЖИВЫМ потокам,
                // а не хранится как флаг последнего стартовавшего задания (в любом лейне) - иначе задание
                // одного лейна могло молча "перетереть" этот флаг для ВСЕХ лейнов при своём старте, ломая
                // паузу/поведение цикла для задания, стартовавшего в ДРУГОМ лейне. См. FR-002/US3,
                // specs/029-fix-queue-lane-stall/spec.md.
                val anyLaneWithoutControl = threadsMap.values.any { it != null && it.isAlive && it.karaokeProcess?.withoutControl == true }
                withoutControl = anyLaneWithoutControl
                if (!anyLaneWithoutControl) {
                    Thread.sleep(timeout)

                    if (counter % (intervalCheckFiles / timeout) == 0L) {
                        if (Karaoke.monitoringRemoteSettingsSync) {
//                            println("ProcessWorker: Проверка sync-записей по таймеру...")
                            // Получаем список sync-записей из REMOTE DATABASE
                            val listSongsSync =
                                Song.loadListFromDb(
                                    database = Connection.remote(),
                                    sync = true,
                                    storageService = KSS_APP,
                                    storageApiClient = SAC_APP,
                                )
                            listSongsSync.forEach { songSync ->
                                val songLocal =
                                    Song.loadFromDbById(
                                        id = songSync.id,
                                        database = Connection.local(),
                                        storageService = KSS_APP,
                                        storageApiClient = SAC_APP,
                                    )
                                if (songLocal != null) {
                                    // Запись в локальной БД есть, надо обновить
                                    val diff = Song.getDiff(songSync, songLocal)
                                    val setStr =
                                        diff
                                            .filter { it.recordDiffRealField }
                                            .joinToString(", ") { "${it.recordDiffName} = ?" }
                                    if (setStr != "") {
                                        val sql = "UPDATE tbl_songs SET $setStr WHERE id = ?"

                                        val connection = Connection.local().getConnection()
                                        if (connection == null) {
                                            println(
                                                "[${Timestamp.from(Instant.now())}] Невозможно установить соединение с базой данных LOCAL",
                                            )
                                        } else {
                                            val ps = connection.prepareStatement(sql)

                                            var index = 1
                                            diff.filter { it.recordDiffRealField }.forEach {
                                                when (it.recordDiffValueNew) {
                                                    is Long -> ps.setLong(index, it.recordDiffValueNew)
                                                    is Int -> ps.setInt(index, it.recordDiffValueNew)
                                                    else -> ps.setString(index, it.recordDiffValueNew.toString())
                                                }
                                                index++
                                            }
                                            ps.setLong(index, songLocal.id)
                                            ps.executeUpdate()
                                            ps.close()
                                            if (Karaoke.autoUpdateRemoteSettings && Karaoke.allowUpdateRemote) {
                                                val (listCreate, listUpdate, listDelete) =
                                                    updateRemoteSongFromLocalDatabase(
                                                        songLocal.id,
                                                    )
                                                if (listCreate.size + listUpdate.size + listDelete.size != 0) {
                                                    SNS.send(SseNotification.crud(listOf(listCreate, listUpdate, listDelete)))
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    // Записи в локальной БД нет, надо создать
                                    val sqlToInsert = songSync.getSqlToInsert()
                                    val connection = Connection.local().getConnection()
                                    if (connection == null) {
                                        println("[${Timestamp.from(Instant.now())}] Невозможно установить соединение с базой данных LOCAL")
                                    } else {
                                        val ps = connection.prepareStatement(sqlToInsert)
                                        ps.executeUpdate()
                                        ps.close()
                                    }
                                }

                                if (songSync.tags == "RENDER") {
                                    val songLocal =
                                        Song.loadFromDbById(
                                            id = songSync.id,
                                            database = Connection.local(),
                                            storageService = KSS_APP,
                                            storageApiClient = SAC_APP,
                                        )
                                    if (songLocal != null) {
                                        songLocal.sourceMarkersList.forEachIndexed { voice, _ ->
                                            val strText = songLocal.convertMarkersToSrt(voice)
                                            val fileName = "${songLocal.rootFolder}/${songLocal.fileName}.voice${voice + 1}.srt"
                                            File(fileName).writeText(strText)
                                            runCommand(listOf("chmod", "666", fileName))
                                        }

                                        songLocal.createKaraoke(createLyrics = true, createKaraoke = true)

                                        KaraokeProcess.createProcess(
                                            song = songLocal,
                                            action = KaraokeProcessTypes.MELT_LYRICS,
                                            doWait = true,
                                            prior = 0,
                                            threadId = 0,
                                        )
                                        KaraokeProcess.createProcess(
                                            song = songLocal,
                                            action = KaraokeProcessTypes.MELT_KARAOKE,
                                            doWait = true,
                                            prior = 1,
                                            threadId = 0,
                                        )
                                    }
                                }
                            }
                            // Удаляем записи из sync-таблицы
                            listSongsSync.map { it.id }.forEach { idToDel ->
                                Song.deleteFromDb(id = idToDel, database = Connection.remote(), sync = true)
                            }
                        }
                    }
                }

                val karaokeProcessesToStart = getKaraokeProcessesToStart(database)
                val karaokeProcessesToStartIds = karaokeProcessesToStart.keys.toList()
                val threadsIds = threadsMap.filter { it.value != null }.keys.toList()

                val hasAliveThreads = threadsMap.any { it.value != null && it.value!!.isAlive }

                if (stopAfterThreadIsDone && !hasAliveThreads) {
                    stopAfterThreadIsDone = false
                    isWork = false
                    withoutControl = false
                    sendStateMessage()
                    println("[${Timestamp.from(Instant.now())}] ProcessWorker: Останавливаемся")
                } else {
                    /*
                    Для каждого id из karaokeProcessesToStartIds проверяем, есть ли такой же id в threadsIds
                    Если такого нет или такой есть и он null или !isAlive - тогда надо запустить новый процесс с таким же id
                    Иначе обновляем персентаж
                     */

                    karaokeProcessesToStartIds.forEach { threadId ->
                        if (!threadsIds.contains(threadId) ||
                            (threadsIds.contains(threadId) && (threadsMap[threadId] == null || !threadsMap[threadId]!!.isAlive))
                        ) {
                            val karaokeProcess = karaokeProcessesToStart[threadId]
                            // throwOnError=true - см. комментарий у getKaraokeProcessesToStart() выше.
                            val countWaiting = KaraokeProcess.getCountWaiting(database, throwOnError = true)
                            sendCountWaitingMessage(countWaiting)
                            if (karaokeProcess != null && (!stopAfterThreadIsDone || karaokeProcess.command == "tail")) {
                                val args = karaokeProcess.args[0]
                                if (args.isNotEmpty()) {
                                    if (id > 0) {
                                        val kp = KaraokeProcess.load(id, database)
                                        val diffs = KaraokeProcess.getDiff(kp)
                                        if (diffs.isNotEmpty()) {
                                            karaokeProcess.save()
                                        }
                                    }
                                    threadsMap[threadId] = KaraokeProcessThread(karaokeProcess)

                                    id = karaokeProcess.id
                                    if (karaokeProcess.command != "tail" || karaokeProcess.args[0][0] !in argsIgnoredToLog) {
                                        println(
                                            "[${Timestamp.from(
                                                Instant.now(),
                                            )}] ProcessWorker: Стартуем новое задание: ${karaokeProcess.name} - [${karaokeProcess.type}] - ${karaokeProcess.description}",
                                        )
                                    }
                                    threadsMap[threadId]!!.start()
                                }
                            } else {
                                if (id != 0L) {
                                    val kp = KaraokeProcess.load(id, database)
                                    val diffs = KaraokeProcess.getDiff(kp)
                                    if (diffs.isNotEmpty()) {
                                        threadsMap[threadId]?.karaokeProcess?.save()
                                    }
                                }
                            }
                        } else {
                            // Признак "без контроля" берётся у КОНКРЕТНОГО задания этого лейна, а не у
                            // общего флага воркера - иначе batch-задание в ДРУГОМ лейне могло бы отключить
                            // обычное сохранение диффа для этого лейна (и наоборот). См. FR-002/US3.
                            if (threadsMap[threadId]?.karaokeProcess?.withoutControl != true) {
                                val kp = threadsMap[threadId]?.karaokeProcess
                                val diffs = KaraokeProcess.getDiff(kp)
                                if (diffs.isNotEmpty()) {
                                    threadsMap[threadId]?.karaokeProcess?.save()
                                }
                            }
                        }
                    }

                    // Если очередь пуста — отправляем актуальный счётчик, чтобы бейдж сбросился в 0
                    // throwOnError=true - см. комментарий у getKaraokeProcessesToStart() выше.
                    if (karaokeProcessesToStartIds.isEmpty()) {
                        sendCountWaitingMessage(KaraokeProcess.getCountWaiting(database, throwOnError = true))
                    }

                    // Периодическая отправка SSE для активных потоков, которые уже не WAITING
                    // (выпали из getProcessesToStart). Без этого прогресс long-running заданий
                    // (RENDER_MP4 и т.п.) не обновляется в прогрессометре шапки webvue3.
                    runningThreadsCheckCounter++
                    if (runningThreadsCheckCounter >= RUNNING_THREADS_CHECK_INTERVAL) {
                        runningThreadsCheckCounter = 0
                        val startThreadIds = karaokeProcessesToStartIds.toSet()
                        threadsMap.filter { it.value != null && it.value!!.isAlive }.forEach { (threadId, thread) ->
                            // Признак "без контроля" - у задания ЭТОГО потока, не общий флаг воркера
                            // (см. пояснение выше про изоляцию лейнов, FR-002/US3).
                            if (threadId !in startThreadIds && thread?.karaokeProcess?.withoutControl != true) {
                                thread?.karaokeProcess?.save()
                            }
                        }
                    }
                }
            }
        }

        private fun doStop() {
            stopAfterThreadIsDone = true
        }

        // Принудительная (жёсткая) остановка очереди: в отличие от doStop() (мягкое ожидание завершения
        // текущей цепочки) — немедленно убивает docker-контейнеры выполняющихся заданий, возвращает
        // незавершённые процессы в WAITING (чтобы переиграли заново) и выходит из главного цикла.
        // Вызывается по двойному клику на задизейбленную кнопку старт/стоп во время мягкого ожидания.
        fun forceStop() {
            println("[${Timestamp.from(Instant.now())}] ProcessWorker: Принудительная остановка (форс-стоп)")
            val threads = threadsMap.values.filterNotNull()
            // (a) взводим флаг ДО убийства — чтобы завершившийся поток выставил WAITING, а не DONE/ERROR
            threads.forEach { it.forceStopped = true }
            // (b) останавливаем главный цикл doStart немедленно. Важно: stopAfterThreadIsDone держим true,
            //     а не сбрасываем в false — иначе цикл (пока не вышел по isWork=false на след. итерации)
            //     мог бы попасть в ветку старта и перезапустить только что убитое задание (оно уже WAITING).
            stopAfterThreadIsDone = true
            withoutControl = false
            isWork = false
            // (c) убиваем docker-контейнеры выполняющихся заданий (читает WORKING из БД — до перевода в WAITING)
            try {
                killRunningDockerContainers()
            } catch (e: Exception) {
                println("[${Timestamp.from(Instant.now())}] ProcessWorker: ошибка убийства контейнеров: ${e.message}")
            }
            // (d) переводим процессы потоков в WAITING (+ SSE recordChange для таблицы «Процессы»),
            //     добиваем родительские CLI-процессы и прерываем потоки
            threads.forEach { thread ->
                thread.karaokeProcess?.let { kp ->
                    kp.status = KaraokeProcessStatuses.WAITING.name
                    kp.save()
                }
                runCatching { thread.osProcess?.destroyForcibly() }
                runCatching { thread.interrupt() }
            }
            // (e) backstop: любые оставшиеся WORKING (уже умершие/функциональные) → WAITING
            KaraokeProcess.setWorkingToWaiting(WORKING_DATABASE)
            // (f) чистим карту потоков и уведомляем фронт (work=false → фронт очистит прогресс-бар шапки)
            threadsMap.clear()
            sendStateMessage()
            sendCountWaitingMessage(KaraokeProcess.getCountWaiting(WORKING_DATABASE))
        }

        fun getPercentage(karaokeProcess: KaraokeProcess): String {
            val workThread =
                threadsMap
                    .filter {
                        it.key == karaokeProcess.threadId && it.value?.karaokeProcess?.id == karaokeProcess.id
                    }.values
                    .toList()
                    .firstOrNull()
            return workThread?.percentage ?: "---"
        }
    }
}
