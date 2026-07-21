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
                            KaraokeProcessTypes.RENDER_MP4_LYRICS,
                            KaraokeProcessTypes.RENDER_MP4_KARAOKE,
                            KaraokeProcessTypes.RENDER_MP4_CHORDS,
                            KaraokeProcessTypes.RENDER_MP4_TABS,
                            KaraokeProcessTypes.RENDER_MP4_DEMO,
                            -> executeRenderMp4(params) { pct -> percentage = pct.toString() }
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

                val process = processBuilder.start()
                osProcess = process
                if (process.isAlive) {
                    if (karaokeProcess.command != "tail" || karaokeProcess.args[0][0] !in KaraokeProcessWorker.argsIgnoredToLog) {
                        println(
                            "[${Timestamp.from(
                                Instant.now(),
                            )}] KaraokeProcessThread[${karaokeProcess.threadId}]: Установка приоритета задания: ${karaokeProcess.name} - [${karaokeProcess.type}] - ${karaokeProcess.description}",
                        )
                    }
                    setProcessPriority(process.pid(), karaokeProcess.prioritet)
                }

                try {
                    if (karaokeProcess.command != "tail" || karaokeProcess.args[0][0] !in KaraokeProcessWorker.argsIgnoredToLog) {
                        println(
                            "[${Timestamp.from(
                                Instant.now(),
                            )}] KaraokeProcessThread[${karaokeProcess.threadId}]: Начинаем работу с заданием: ${karaokeProcess.name} - [${karaokeProcess.type}] - ${karaokeProcess.description}",
                        )
                        KaraokeProcessWorker.sendCountWaitingMessage(KaraokeProcess.getCountWaiting(database = karaokeProcess.database))
                    }
                    val inputStream = process.inputStream
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
                        val settings =
                            Settings.loadFromDbById(
                                id = karaokeProcess.settingsId.toLong(),
                                database = WORKING_DATABASE,
                                storageService = KSS_APP,
                                storageApiClient = SAC_APP,
                            )
                        settings?.let {
                            KaraokeProcess.createProcess(
                                settings = settings,
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
                } catch (_: Exception) {
                    process.destroy()
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
                            )}] KaraokeProcessThread[${karaokeProcess.threadId}]: ERROR задание: ${karaokeProcess.name} - [${karaokeProcess.type}] - ${karaokeProcess.description}",
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
            if (!forceStopped && kp.settingsId > 0 && typeEnum in HealthReport.HR_REPAIR_PROCESS_TYPES) {
                try {
                    HealthReport.onRepairProcessFinished(
                        settingsId = kp.settingsId.toLong(),
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
        }
    }
}

/**
 * Главный Spring-компонент очереди задач Karaoke. Цикл, который берёт
 * `WAITING` задания из `tbl_processes` (отсортированные по приоритету),
 * запускает их через [KaraokeProcessThread] в соответствующем `threadId`-
 * лейне, и обрабатывает периодические проверки (SearchAsync, Settings.
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
         */
        var isWork: Boolean = false

        /**
         * Если `true` — после завершения текущего потока воркер остановится
         * (используется для «мягкой» остановки с ожиданием завершения).
         */
        var stopAfterThreadIsDone: Boolean = false

        /**
         * Режим без UI-контроля (для batch-прогонов на admin-машине).
         */
        var withoutControl = false

        // Периодическая проверка активных потоков вне очереди (для SSE-прогресса).
        // Каждые ~500мс (50 итераций × 10мс) — достаточно для плавного прогресс-бара.
        var runningThreadsCheckCounter: Int = 0
        const val RUNNING_THREADS_CHECK_INTERVAL = 50

        val threadsMap: MutableMap<Int, KaraokeProcessThread?> = mutableMapOf()

//        var workThread: KaraokeProcessThread? = null

        /**
         * Запустить воркер (если ещё не запущен).
         *
         * Перед стартом: очищает `DONE` задания (`KaraokeProcess.deleteDone`),
         * восстанавливает `WORKING` → `WAITING` после возможного падения
         * (`setWorkingToWaiting`), рассылает SSE-сообщение с количеством
         * ожидающих.
         *
         * Если воркер уже запущен — сбрасывает `stopAfterThreadIsDone` и
         * отправляет текущее состояние через SSE.
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
            if (!isWork) {
                KaraokeProcess.deleteDone(database)
                KaraokeProcess.setWorkingToWaiting(database)
                sendCountWaitingMessage(KaraokeProcess.getCountWaiting(database))
                doStart(database = database, storageService = storageService, storageApiClient = storageApiClient)
            } else {
                stopAfterThreadIsDone = false
                sendStateMessage()
            }
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

        private fun getKaraokeProcessesToStart(database: KaraokeConnection): Map<Int, KaraokeProcess> =
            KaraokeProcess.getProcessesToStart(database)

        private fun doStart(
            database: KaraokeConnection,
            storageService: KaraokeStorageService,
            storageApiClient: StorageApiClient,
        ) {
            val timeout = 10L
            var counter = 0L
            var id = 0L
//            var settingsId = 0L
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
                                                    Settings
                                                        .loadFromDbById(
                                                            id = songId,
                                                            database = database,
                                                            storageService = storageService,
                                                            storageApiClient = storageApiClient,
                                                        )?.let { settings ->
                                                            if (settings.sourceText.isBlank() && settings.idStatus == 0L) {
                                                                println(
                                                                    "Первое из найденных не пустых значений применяем для текста песни ${settings.fileName}",
                                                                )
                                                                settings.sourceText = searchedRightResults.first().text
                                                                settings.fields[SettingField.ID_STATUS] = "1"
                                                                settings.saveToDb()
                                                            }
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
                if (!withoutControl) {
                    Thread.sleep(timeout)

                    if (counter % (intervalCheckFiles / timeout) == 0L) {
                        if (Karaoke.monitoringRemoteSettingsSync) {
//                            println("ProcessWorker: Проверка sync-записей по таймеру...")
                            // Получаем список sync-записей из REMOTE DATABASE
                            val listSettingsSync =
                                Settings.loadListFromDb(
                                    database = Connection.remote(),
                                    sync = true,
                                    storageService = KSS_APP,
                                    storageApiClient = SAC_APP,
                                )
                            listSettingsSync.forEach { settingsSync ->
                                val settingsLocal =
                                    Settings.loadFromDbById(
                                        id = settingsSync.id,
                                        database = Connection.local(),
                                        storageService = KSS_APP,
                                        storageApiClient = SAC_APP,
                                    )
                                if (settingsLocal != null) {
                                    // Запись в локальной БД есть, надо обновить
                                    val diff = Settings.getDiff(settingsSync, settingsLocal)
                                    val setStr =
                                        diff
                                            .filter { it.recordDiffRealField }
                                            .joinToString(", ") { "${it.recordDiffName} = ?" }
                                    if (setStr != "") {
                                        val sql = "UPDATE tbl_settings SET $setStr WHERE id = ?"

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
                                            ps.setLong(index, settingsLocal.id)
                                            ps.executeUpdate()
                                            ps.close()
                                            if (Karaoke.autoUpdateRemoteSettings && Karaoke.allowUpdateRemote) {
                                                val (listCreate, listUpdate, listDelete) =
                                                    updateRemoteSettingFromLocalDatabase(
                                                        settingsLocal.id,
                                                    )
                                                if (listCreate.size + listUpdate.size + listDelete.size != 0) {
                                                    SNS.send(SseNotification.crud(listOf(listCreate, listUpdate, listDelete)))
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    // Записи в локальной БД нет, надо создать
                                    val sqlToInsert = settingsSync.getSqlToInsert()
                                    val connection = Connection.local().getConnection()
                                    if (connection == null) {
                                        println("[${Timestamp.from(Instant.now())}] Невозможно установить соединение с базой данных LOCAL")
                                    } else {
                                        val ps = connection.prepareStatement(sqlToInsert)
                                        ps.executeUpdate()
                                        ps.close()
                                    }
                                }

                                if (settingsSync.tags == "RENDER") {
                                    val settingsLocal =
                                        Settings.loadFromDbById(
                                            id = settingsSync.id,
                                            database = Connection.local(),
                                            storageService = KSS_APP,
                                            storageApiClient = SAC_APP,
                                        )
                                    if (settingsLocal != null) {
                                        settingsLocal.sourceMarkersList.forEachIndexed { voice, _ ->
                                            val strText = settingsLocal.convertMarkersToSrt(voice)
                                            val fileName = "${settingsLocal.rootFolder}/${settingsLocal.fileName}.voice${voice + 1}.srt"
                                            File(fileName).writeText(strText)
                                            runCommand(listOf("chmod", "666", fileName))
                                        }

                                        settingsLocal.createKaraoke(createLyrics = true, createKaraoke = true)

                                        KaraokeProcess.createProcess(
                                            settings = settingsLocal,
                                            action = KaraokeProcessTypes.MELT_LYRICS,
                                            doWait = true,
                                            prior = 0,
                                            threadId = 0,
                                        )
                                        KaraokeProcess.createProcess(
                                            settings = settingsLocal,
                                            action = KaraokeProcessTypes.MELT_KARAOKE,
                                            doWait = true,
                                            prior = 1,
                                            threadId = 0,
                                        )
                                    }
                                }
                            }
                            // Удаляем записи из sync-таблицы
                            listSettingsSync.map { it.id }.forEach { idToDel ->
                                Settings.deleteFromDb(id = idToDel, database = Connection.remote(), sync = true)
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
                            val countWaiting = KaraokeProcess.getCountWaiting(database)
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
                                    withoutControl = karaokeProcess.withoutControl
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
                            if (!withoutControl) {
                                val kp = threadsMap[threadId]?.karaokeProcess
                                val diffs = KaraokeProcess.getDiff(kp)
                                if (diffs.isNotEmpty()) {
                                    threadsMap[threadId]?.karaokeProcess?.save()
                                }
                            }
                        }
                    }

                    // Если очередь пуста — отправляем актуальный счётчик, чтобы бейдж сбросился в 0
                    if (karaokeProcessesToStartIds.isEmpty()) {
                        sendCountWaitingMessage(KaraokeProcess.getCountWaiting(database))
                    }

                    // Периодическая отправка SSE для активных потоков, которые уже не WAITING
                    // (выпали из getProcessesToStart). Без этого прогресс long-running заданий
                    // (RENDER_MP4 и т.п.) не обновляется в прогрессометре шапки webvue3.
                    runningThreadsCheckCounter++
                    if (runningThreadsCheckCounter >= RUNNING_THREADS_CHECK_INTERVAL) {
                        runningThreadsCheckCounter = 0
                        val startThreadIds = karaokeProcessesToStartIds.toSet()
                        threadsMap.filter { it.value != null && it.value!!.isAlive }.forEach { (threadId, thread) ->
                            if (threadId !in startThreadIds && !withoutControl) {
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
