package com.svoemesto.karaokeapp

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.svoemesto.karaokeapp.model.*
import com.svoemesto.karaokeapp.services.KSS_APP
import com.svoemesto.karaokeapp.services.KaraokeStorageService
import com.svoemesto.karaokeapp.services.SAC_APP
import com.svoemesto.karaokeapp.tuple.TupleJanitor
import com.svoemesto.karaokeapp.tuple.JdbcTupleStore
import com.svoemesto.karaokeapp.tuple.TupleExecutor
import com.svoemesto.karaokeapp.tuple.TupleScope
import com.svoemesto.karaokeapp.services.SNS
import com.svoemesto.karaokeapp.services.StorageApiClient
import com.svoemesto.karaokeapp.services.StorageMetadataCache
import com.svoemesto.karaokeapp.services.TelegramAutoPublishService
import com.svoemesto.karaokeapp.services.startOneShotThread
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
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.temporal.ChronoUnit

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
 * @see archive/docs/features/async-process-queue.md
 * @see archive/docs/features/approve-pipeline.md (фича 131: пост-хук публикации в Telegram сразу после DONE для RENDER_MP4_DEMO)
 * @see KaraokeProcessWorker главный воркер, создаёт и запускает потоки
 * @see KaraokeProcess модель задания
 */

/**
 * Класс Karaoke Process Thread.
 *
 * @see archive/docs/features/async-process-queue.md
 */
class KaraokeProcessThread(
    var karaokeProcess: KaraokeProcess? = null,
    var percentage: String? = null,
) : Thread() {
    // Принудительная остановка (форс-стоп): выставляется извне (KaraokeProcessWorker.forceStop) ДО убийства
    // docker-контейнера/подпроцесса. Пока флаг взведён, завершившийся поток выставляет WAITING (а не DONE/ERROR),
    // чтобы задание переиграло заново, и пропускает пост-хук HealthReport.
    @Volatile var forceStopped: Boolean = false

    // Ссылка на родительский OS-процесс (docker/docker compose/ffmpeg) — чтобы форс-стоп мог его добить.
    @Volatile var osProcess: java.lang.Process? = null

    /**
     * Выполняет один шаг кортежа. Возвращает статус шага после завершения.
     *
     * `runStep` живёт здесь, в `KaraokeProcessWorker.kt`, а не в `tuple/`: это сохраняет
     * `ProcessExitWiringTest` (source-contract: exit code проверяется до DONE) зелёным.
     */
    private fun executeStep(): String {
        val kp = karaokeProcess ?: return KaraokeProcessStatuses.ERROR.name
        // Одноразовый поток задания (в отличие от переиспользуемых потоков Tomcat/doStart()) —
        // явно освобождает своё ThreadLocal-соединение по завершении (specs/091-fix-connection-leak),
        // иначе оно остаётся открытым в PostgreSQL навсегда, так как поток больше не обратится к БД.
        // BR-031 (аудит #336, пакет PKG-S2-10): атомарный claim строки.
        // Раньше WORKKING писался безусловным save() — если строку успели удалить
        // или взять другим воркером, UPDATE шёл по чужой/несуществующей строке.
        if (!kp.claimForWorking()) {
            println(
                "[${Timestamp.from(Instant.now())}] KaraokeProcessThread[${kp.threadId}]: " +
                    "claim не получен (задание ${kp.id} уже не WAITING) — поток не запускает работу",
            )
            return KaraokeProcessStatuses.WAITING.name
        }

        if (kp.args.firstOrNull()?.firstOrNull() == "runFunctionWithArgs") {
            val params =
                parseRunFunctionWithArgsParams(
                    TupleScope.expand(kp.args[0], kp.scopeId()),
                )
            percentage = "0"
            println(
                "[${Timestamp.from(
                    Instant.now(),
                )}] KaraokeProcessThread[${kp.threadId}]: Начинаем работу с заданием: ${kp.name} - [${kp.type}] - ${kp.description}",
            )
            try {
                val success =
                    when (KaraokeProcessTypes.valueOf(kp.type)) {
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
                        )}] KaraokeProcessThread[${kp.threadId}]: WAITING (форс-стоп) задание: ${kp.name} - [${kp.type}] - ${kp.description}",
                    )
                    kp.status = KaraokeProcessStatuses.WAITING.name
                    kp.save()
                } else {
                    println(
                        "[${Timestamp.from(
                            Instant.now(),
                        )}] KaraokeProcessThread[${kp.threadId}]: ${if (success) "DONE успешно завершенное" else "ERROR (данные не найдены)"} задание: ${kp.name} - [${kp.type}] - ${kp.description}",
                    )
                    kp.status = (if (success) KaraokeProcessStatuses.DONE else KaraokeProcessStatuses.ERROR).name
                    kp.end = Timestamp.from(Instant.now())
                    kp.priority = if (success) 999 else -1
                    percentage = "100"
                    kp.save()
                }
            } catch (e: Exception) {
                if (forceStopped) {
                    println(
                        "[${Timestamp.from(
                            Instant.now(),
                        )}] KaraokeProcessThread[${kp.threadId}]: WAITING (форс-стоп) задание: ${kp.name} - [${kp.type}] - ${kp.description}",
                    )
                    kp.status = KaraokeProcessStatuses.WAITING.name
                    kp.save()
                } else {
                    println(
                        "[${Timestamp.from(
                            Instant.now(),
                        )}] KaraokeProcessThread[${kp.threadId}]: ERROR задание: ${kp.name} - [${kp.type}] - ${kp.description}: ${e.message}",
                    )
                    kp.status = KaraokeProcessStatuses.ERROR.name
                    kp.end = Timestamp.from(Instant.now())
                    percentage = "100"
                    kp.save()
                }
            }
        } else {
            val regex = Regex("Current Frame:\\s+(\\d+), percentage:\\s+(\\d+)")
            val regexDuration = Regex("Duration:\\s+(\\d\\d:\\d\\d:\\d\\d\\.\\d\\d),")
            val regexCurrent = Regex("time=(\\d\\d:\\d\\d:\\d\\d\\.\\d\\d)")
            val regexPercentageSheetsage = Regex("^\\s{0,2}(\\d{1,3})%\\|")
            val karaokeProcessType = kp.type
            val typeEnum = runCatching { KaraokeProcessTypes.valueOf(karaokeProcessType) }.getOrNull()
            // Лимит CPU пересобирается заново прямо перед стартом (не берётся "как есть" из БД) -
            // настройки могли поменяться, пока задание стояло в очереди WAITING.
            val rawArgv = TupleScope.expand(kp.args[0], kp.scopeId())
            val args = if (typeEnum != null) refreshArgvCpuLimit(typeEnum, rawArgv) else rawArgv
            val envs = if (typeEnum != null) refreshEnvCpuLimit(typeEnum, kp.envs) else kp.envs
            val processBuilder = ProcessBuilder(args)

            val processBuilderEnvironment = processBuilder.environment()
            processBuilderEnvironment.putAll(envs)

            processBuilder.redirectErrorStream(true)
            // FR-004 (674): SHEETSAGE пишет вывод относительно cwd — запускаем в своём scope-каталоге.
            if (typeEnum == KaraokeProcessTypes.SHEETSAGE || typeEnum == KaraokeProcessTypes.SHEETSAGE2) {
                processBuilder.directory(
                    java.io.File(PATH_TO_TEMP_SHEETSAGE_FOLDER, kp.scopeId().toString()),
                )
            }

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
                    if (kp.command != "tail" || kp.args[0][0] !in KaraokeProcessWorker.argsIgnoredToLog) {
                        println(
                            "[${Timestamp.from(
                                Instant.now(),
                            )}] KaraokeProcessThread[${kp.threadId}]: Установка приоритета задания: ${kp.name} - [${kp.type}] - ${kp.description}",
                        )
                    }
                    setProcessPriority(startedProcess.pid(), kp.prioritet)
                }

                if (kp.command != "tail" || kp.args[0][0] !in KaraokeProcessWorker.argsIgnoredToLog) {
                    println(
                        "[${Timestamp.from(
                            Instant.now(),
                        )}] KaraokeProcessThread[${kp.threadId}]: Начинаем работу с заданием: ${kp.name} - [${kp.type}] - ${kp.description}",
                    )
                    KaraokeProcessWorker.sendCountWaitingMessage(KaraokeProcess.getCountWaiting(database = kp.database))
                    // specs/118 #397: отдельный SSE-канал для cache-очереди.
                    KaraokeProcessWorker.sendCacheQueueSizeMessage(StorageMetadataCache.cacheQueueSize())
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
                // BR-029 (аудит #336): раньше EOF stdout безусловно означал DONE, а код
                // возврата subprocess (docker compose run / ffmpeg / melt) не проверялся —
                // сбой выглядел успехом. Теперь дожидаемся завершения и проверяем exit code.
                // stderr слит в stdout (ADR-0006), поэтому хвост log содержит и stderr.
                val exitCode = awaitProcessExit(startedProcess, args, outputTail = { outputTail(log) })
                if (kp.command != "tail" || kp.args[0][0] !in KaraokeProcessWorker.argsIgnoredToLog) {
                    println(
                        "[${Timestamp.from(
                            Instant.now(),
                        )}] KaraokeProcessThread[${kp.threadId}]: Завершаем работу с заданием: ${kp.name} - [${kp.type}] - ${kp.description}",
                    )
                }
                if (log != "") {
                    if (kp.command != "tail" || kp.args[0][0] !in KaraokeProcessWorker.argsIgnoredToLog) {
                        println(
                            "[${Timestamp.from(
                                Instant.now(),
                            )}] KaraokeProcessThread[${kp.threadId}]: Выводим лог задания: ${kp.name} - [${kp.type}] - ${kp.description}",
                        )
                    }
                    log = args.joinToString(" ") + "\n\n" + log
                    val logFileName =
                        "$PATH_TO_LOGS/[${Timestamp.from(
                            Instant.now(),
                        )}] ${kp.name} - ${kp.description}.log".rightFileName()
                    try {
                        File(logFileName).writeText(log, Charsets.UTF_8)
                        runCommand(listOf("chmod", "666", logFileName))
                        KaraokeProcessWorker.cleanupOldLogs()
                    } catch (e: Exception) {
                        println(e.message)
                    }
                }

                // Известный восстановимый случай: SHEETSAGE печатает NotImplementedError,
                // после чего создаётся SHEETSAGE2. Его ненулевой код возврата — не «успех»,
                // но и не повод помечать задание ERROR: цепочка уже переиграна.
                val sheetsageFallback =
                    kp.type == "SHEETSAGE" &&
                        lastLine == "NotImplementedError: Dynamic chunking not implemented. Try halving measures_per_chunk."
                if (exitCode != 0 && !sheetsageFallback && !forceStopped) {
                    throw processExitFailure(args, exitCode, outputTail(log))
                }

                if (sheetsageFallback) {
                    // Если процесс SHEETSAGE завершился ошибкой - создаём для этой же песни процесс SHEETSAGE2 с таким же приоритетом
                    val song =
                        Song.loadFromDbById(
                            id = kp.songId.toLong(),
                            database = WORKING_DATABASE,
                            storageService = KSS_APP,
                            storageApiClient = SAC_APP,
                        )
                    song?.let {
                        KaraokeProcess.createProcess(
                            song = song,
                            action = KaraokeProcessTypes.SHEETSAGE2,
                            doWait = true,
                            prior = kp.priority,
                            threadId = 0,
                        )
                    }
                }

                if (forceStopped) {
                    // Форс-стоп: подпроцесс завершился из-за убитого docker-контейнера — возвращаем в очередь.
                    println(
                        "[${Timestamp.from(
                            Instant.now(),
                        )}] KaraokeProcessThread[${kp.threadId}]: WAITING (форс-стоп) задание: ${kp.name} - [${kp.type}] - ${kp.description}",
                    )
                    kp.status = KaraokeProcessStatuses.WAITING.name
                    kp.save()
                } else {
                    if (kp.command != "tail" || kp.args[0][0] !in KaraokeProcessWorker.argsIgnoredToLog) {
                        println(
                            "[${Timestamp.from(
                                Instant.now(),
                            )}] KaraokeProcessThread[${kp.threadId}]: DONE успешно завершенное задание: ${kp.name} - [${kp.type}] - ${kp.description}",
                        )
                    }
                    kp.status = KaraokeProcessStatuses.DONE.name
                    kp.end = Timestamp.from(Instant.now())
                    kp.save()
                }

//                if (kp.type == KaraokeProcessTypes.DEMUCS2.name) {
//                    KaraokeProcess.delete(kp.id, kp.database)
//                }
            } catch (e: Exception) {
                process?.destroy()
                if (forceStopped) {
                    println(
                        "[${Timestamp.from(
                            Instant.now(),
                        )}] KaraokeProcessThread[${kp.threadId}]: WAITING (форс-стоп) задание: ${kp.name} - [${kp.type}] - ${kp.description}",
                    )
                    kp.status = KaraokeProcessStatuses.WAITING.name
                    kp.save()
                } else {
                    println(
                        "[${Timestamp.from(
                            Instant.now(),
                        )}] KaraokeProcessThread[${kp.threadId}]: ERROR задание: ${kp.name} - [${kp.type}] - ${kp.description}: ${e.message}",
                    )
                    kp.status = KaraokeProcessStatuses.ERROR.name
                    kp.end = Timestamp.from(Instant.now())
                    // BR-036 (аудит #336, пакет PKG-S3-22): причина сбоя больше не
                    // теряется в stdout контейнера — она дописывается к описанию
                    // задания, которое видит оператор в админке.
                    kp.description =
                        KaraokeProcess.appendErrorReason(kp.description, e)
                    kp.save()
                }
            }
        }

        // После завершения subprocess (DONE / ERROR / форс-стоп → WAITING) —
        // отправляем актуальный countWaiting в SSE, чтобы UI-бейдж «start-stop»
        // отражал текущий размер очереди. Дедупликация в sendCountWaitingMessage
        // подавляет случай, когда countWaiting не изменился (например, force-stop
        // вернул задание в WAITING — WAITING-число не изменилось).
        //
        // Регрессия после specs/177-fix-process-count-waiting-spam: устранение
        // периодического вызова sendCountWaitingMessage в doStart() (старая
        // строка 1107 «if (karaokeProcessesToStartIds.isEmpty()) ...») убрало
        // этот сигнал вместе со спамом. Этот hook в run() восстанавливает
        // доставку реальных изменений countWaiting после завершения subprocess.
        try {
            KaraokeProcessWorker.sendCountWaitingMessage(
                KaraokeProcess.getCountWaiting(database = kp.database),
            )
            // specs/118 #397: отдельный SSE-канал для cache-очереди.
            KaraokeProcessWorker.sendCacheQueueSizeMessage(StorageMetadataCache.cacheQueueSize())
        } catch (e: Exception) {
            println(
                "[${Timestamp.from(
                    Instant.now(),
                )}] KaraokeProcessThread[${kp.threadId}]: " +
                    "ошибка отправки countWaiting после завершения subprocess: ${e.message}",
            )
        }

        // Пост-хук: после завершения репаир-задания пересчитать HealthReport песни и разослать SSE
        // (иначе счётчик ошибок в таблице «застывает» на IN_PROGRESS). Для песен в каскаде
        // «Исправить всё» — поставить следующий ставший решаемым шаг. Ограничение по типам исключает
        // многократный пересчёт на sub-шагах тяжёлых MELT_*-рендеров.
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

        // Спека 131 (US1): пост-хук публикации в Telegram сразу после успешного завершения
        // RENDER_MP4_DEMO. Запускается в отдельном thread, чтобы не задерживать освобождение
        // ThreadLocal-соединения и не блокировать worker-поток (HTTP-вызов к TelegramBot
        // занимает 5-30 с, см. research.md п.2.3 и contracts/pipeline.md §3).
        //
        // Условие срабатывания:
        //   - тип задания — RENDER_MP4_DEMO;
        //   - статус — DONE (ERROR — пропускаем, scheduler ретраит позже);
        //   - задание не было форс-стопнуто;
        //   - songId > 0 (защита от задач без привязки к песне);
        //   - задание помечено trigger=approve (спека 144) — рендер был поставлен именно
        //     сменой idStatus на 6 (SongEditorController.triggerRenderMp4DemoIfNeeded), а не
        //     ручным рендером DEMO из интерфейса (ApiController) — там этого токена нет.
        //
        // Идемпотентность публикации обеспечивается внутри TelegramAutoPublishService.
        // publishToTelegram по song.idTelegramDemo (ранний return PUBLISHED).
        val renderKp = kp
        val isApproveTriggered = renderKp.args.firstOrNull()?.contains("trigger=approve") == true
        // Отладочный лог: только для задач типа RENDER_MP4_DEMO (чтобы не шуметь на остальных).
        // Помогает понять, достигает ли условие срабатывания пост-хука.
        if (renderKp.type == KaraokeProcessTypes.RENDER_MP4_DEMO.name) {
            println(
                "[render-demo/post-hook] CHECK: type=${renderKp.type} status=${renderKp.status} " +
                    "songId=${renderKp.songId} forceStopped=$forceStopped isApproveTriggered=$isApproveTriggered",
            )
        }
        if (!forceStopped &&
            renderKp.songId > 0 &&
            renderKp.type == KaraokeProcessTypes.RENDER_MP4_DEMO.name &&
            renderKp.status == KaraokeProcessStatuses.DONE.name &&
            isApproveTriggered
        ) {
            println("[render-demo/post-hook] CONDITION MATCHED — firing thread for songId=${renderKp.songId}")
            // Одноразовый поток post-hook'а: закрывает своё thread-local соединение
            // в `finally` (`startOneShotThread`, BR-038/BR-060, аудит #336) — иначе
            // каждое отрендеренное демо оставляло открытый канал к БД.
            startOneShotThread(kp.database, "render-demo-post-hook") {
                println("[render-demo/post-hook] thread START for songId=${renderKp.songId}")
                try {
                    // Спека 131 (фича 134): заменили прямой publishToTelegram(AIR, persist=true)
                    // на onRenderCompleted(), который сам разруливает шаблон и persistMessageId
                    // по song.newsPremiumPublishPending (см. TelegramAutoPublishService.onRenderCompleted:169-172).
                    // Логика:
                    //   - newsPremiumPublishPending=true → PREMIUM + persist=false (не заполняем idTelegramDemo,
                    //     оставляя слот для будущей AIR-публикации по расписанию).
                    //   - newsPremiumPublishPending=false → AIR + persist=true (заполняем idTelegramDemo).
                    // Это правильнее, чем жёстко зашитый AIR-шаблон: после approve ставится
                    // newsPremiumPublishPending=true (см. Song.markNewsAvailableIfReady:5113-5131),
                    // и approve-flow автоматически идёт по PREMIUM-шаблону ("В коллекции").
                    val result =
                        TelegramAutoPublishService.onRenderCompleted(
                            songId = renderKp.songId.toLong(),
                            success = true,
                            error = null,
                        )
                    if (result == null) {
                        println("[render-demo/post-hook] песня ${renderKp.songId} не найдена — публикация пропущена")
                    } else {
                        println(
                            "[render-demo/post-hook] onRenderCompleted вернул state=${result.state.code} " +
                                "messageId=${result.messageId ?: "<нет>"} for songId=${renderKp.songId}",
                        )
                    }
                } catch (e: Exception) {
                    println("[render-demo/post-hook] ошибка onRenderCompleted: ${e.message}")
                }
            }
        }
        return kp.status
    }

    override fun run() {
        super.run()
        val head = karaokeProcess ?: return
        val database = head.database
        val chainId = head.processChainId ?: head.id
        try {
            // 674 (FR-001/FR-002/FR-003): шаги кортежа 0..N-1 исполняются подряд, лейн занят кортежем.
            val executor =
                TupleExecutor(JdbcTupleStore(database)) { step ->
                    karaokeProcess = step
                    executeStep()
                }
            executor.execute(chainId)
        } finally {
            database.closeThreadConnection()
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
 * Архитектура (см. [archive/docs/features/async-process-queue.md]):
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
 * @see archive/docs/features/async-process-queue.md
 * @see KaraokeProcessThread обёртка subprocess
 * @see KaraokeProcess модель задания
 * @see KaraokeProcessTypes типы заданий
 */

/**
 * Класс Karaoke Process Worker.
 *
 * @see archive/docs/features/async-process-queue.md
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
         * Размер чанка SELECT LOCAL в sync-цикле `doStart()` (push LOCAL→SERVER).
         * Равен `SongSyncTarget.rowChunkSize = 25` — валидировано под `socketTimeout=30`
         * на тяжёлых строках Song (Song — самая «толстая» сущность с текстовыми полями
         * и маркерами). См. parent спека 241, A.4.
         */
        private const val SYNC_SELECT_CHUNK_SIZE = 25

        /**
         * Размер чанка DELETE sync-записей в sync-цикле `doStart()`. Равен
         * `SyncRegistry.DELETE_CHUNK_SIZE = 200` — DELETE лёгкий (`DELETE FROM ... WHERE id=?`),
         * можно крупные пачки без риска упереться в таймаут.
         */
        private const val SYNC_DELETE_CHUNK_SIZE = 200

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
         * Последнее значение `countWaiting`, фактически отправленное в SSE-канал
         * через [sendCountWaitingMessage]. Используется для подавления дублей:
         * если новое значение совпадает с последним отправленным — сообщение
         * не рассылается повторно. `null` — «ещё ни разу не отправляли»
         * (после рестарта бэкенда или после [start], который сбрасывает в
         * `null` для гарантии FR-007 — ровно одно начальное сообщение при
         * старте воркера, даже если значение совпало с предыдущим).
         *
         * `@Volatile` — пишется минимум из [sendCountWaitingMessage] (поток
         * [doStart] и HTTP-потоки call-sites), читается там же. Без
         * `@Volatile` запись могла быть не видна другому потоку вовремя
         * (JMM не гарантирует visibility для обычного `var`).
         *
         * @see sendCountWaitingMessage
         * @see archive/docs/features/async-process-queue.md
         */
        @Volatile private var lastSentCountWaiting: Long? = null

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
            // BR-036 (аудит #336, пакет PKG-S3-22): восстановление при старте больше не
            // «молчит о сбое». Раньше deleteDone/setWorkingToWaiting глушили исключение
            // (println без стека), и очередь стартовала с осиротевшими WORKING-строками:
            // воркер их не выбирает, лейн стоит, а очередь выглядит работающей.
            // Теперь причина уходит в лог со стеком, а оператор видит SSE-уведомление.
            // 674 (FR-011): нормализация до очистки, чтобы legacy-одиночки получили chain_id = id.
            KaraokeProcess.normalizeTuples(database)
            val doneCleaned = KaraokeProcess.deleteDone(database)
            val workingReset = KaraokeProcess.setWorkingToWaiting(database)
            // 674 (FR-005): janitor осиротевших временных каталогов кортежей (не трогает живые scope).
            runCatching {
                val ttl = KaraokeProperties.getLong("queue.tuple.temp_ttl_hours").takeIf { it > 0L } ?: 24L
                TupleJanitor(
                    roots =
                        listOf(
                            PATH_TO_TEMP_DEMUCS_FOLDER,
                            PATH_TO_TEMP_KEYBPMFINDER_FOLDER,
                            PATH_TO_TEMP_SHEETSAGE_FOLDER,
                        ),
                    ttlHours = ttl,
                    isLive = { scope -> KaraokeProcess.isScopeLive(scope, database) },
                ).sweep()
            }.onFailure { println("ProcessWorker: janitor не выполнен: ${it.message}") }
            if (!doneCleaned || !workingReset) {
                val failed =
                    listOfNotNull(
                        "удаление DONE".takeIf { !doneCleaned },
                        "сброс WORKING → WAITING".takeIf { !workingReset },
                    )
                val failedSteps = failed.joinToString(", ")
                SNS.send(
                    SseNotification.error(
                        Message(
                            type = "error",
                            head = "Очередь заданий",
                            body =
                                "Сбой восстановления при старте воркера: $failedSteps. " +
                                    "Задания могут остаться в WORKING и не запуститься — проверьте лог.",
                        ),
                    ),
                )
            }
            // FR-007: сбрасываем «последнее отправленное значение», чтобы гарантировать
            // ровно одно начальное сообщение `countWaiting` при старте воркера —
            // даже если число совпало с предыдущим значением до остановки.
            lastSentCountWaiting = null
            // specs/118 #397: сбрасываем дедупликацию cache-очереди для гарантии
            // начального SSE-события при старте воркера.
            lastSentCacheQueueSize = null
            sendCountWaitingMessage(KaraokeProcess.getCountWaiting(database))
            // specs/118 #397: начальное SSE-событие для cache-очереди (отдельный канал).
            sendCacheQueueSizeMessage(StorageMetadataCache.cacheQueueSize())
            // Поток главного цикла очереди ОДНОРАЗОВЫЙ: создаётся заново на каждый `start()`
            // и умирает при остановке очереди — переиспользовать его thread-local соединение
            // некому. Поэтому соединение потока закрывается в `finally` (BR-038, аудит #336):
            // `startOneShotThread` делает это гарантированно (и при успехе, и при исключении).
            // Без этого каждый цикл stop/start оставлял открытым физический канал к БД
            // до недетерминированной финализации сборщиком мусора.
            // Поток очереди ходит и в SERVER (ветка sync-batch, см. processRemoteSongsSyncBatch):
            // у экземпляров LOCAL и SERVER разные thread-local кеши, поэтому закрываем оба
            // (BR-627, аудит #336, OpenProject #410).
            startOneShotThread(
                database = database,
                name = "karaoke-process-worker",
                alsoClose = listOf(Connection.remote()),
            ) {
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

        /**
         * Рассылает SSE-сообщение с текущим количеством `WAITING`-заданий
         * (исключая `command = "tail"`). UI использует это для обновления
         * бейджа счётчика в шапке админки.
         *
         * Подавляет дубли: если новое значение совпадает с последним
         * отправленным ([lastSentCountWaiting]) — событие не отправляется.
         * Это устраняет спам в SSE-канале из периодических вызовов внутри
         * [doStart] (старый код слал `countWaiting` на каждой итерации
         * `while (isWork)` с `Thread.sleep(10L)`, ~10–15 мс между событиями).
         *
         * Реальные изменения `countWaiting` (создание нового WAITING-задания,
         * переход в WORKING, завершение, форс-стоп, перезапуск воркера)
         * по-прежнему доходят до UI в течение 1–2 секунд — call-sites
         * `createDbInstance`/`run`/`forceStop`/`start` идут через этот же
         * метод, и при реальном изменении значения early-return не срабатывает.
         *
         * Безопасно вызывать в любом потоке — `SNS.send` ловит исключения
         * и пишет в stdout (не пробрасывает). Фикс
         * `178-fix-process-count-waiting-spam`.
         *
         * @param countWaiting актуальное число WAITING-заданий
         * @see lastSentCountWaiting состояние «последнее отправленное»
         * @see archive/docs/features/async-process-queue.md
         */
        fun sendCountWaitingMessage(countWaiting: Long) {
            // Подавление дублей: если значение не изменилось с прошлой отправки —
            // не рассылаем повторно (FR-001). `null` (старт/рестарт) — всегда шлём.
            val previous = lastSentCountWaiting
            if (previous != null && previous == countWaiting) return
            lastSentCountWaiting = countWaiting
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

        /**
         * specs/118 #397: аналог [sendCountWaitingMessage] для размера cache-очереди
         * StorageMetadataCache. Отдельный канал SSE (CACHE_QUEUE_SIZE), не смешивается с
         * PROCESS_COUNT_WAITING. Дедупликация через [lastSentCacheQueueSize].
         *
         * Вызывается из [submitFront] и [submitBack] companion-объекта
         * StorageMetadataCache после каждого submit'а (асинхронно через Executor SNS).
         */
        @Volatile private var lastSentCacheQueueSize: Int? = null

        fun sendCacheQueueSizeMessage(cacheQueueSize: Int) {
            // Подавление дублей: если значение не изменилось — не рассылаем повторно.
            // `null` (старт/рестарт) — всегда шлём.
            val previous = lastSentCacheQueueSize
            if (previous != null && previous == cacheQueueSize) return
            lastSentCacheQueueSize = cacheQueueSize
            val message =
                SseNotification.cacheQueueSize(
                    CacheQueueSizeMessage(
                        cacheQueueSize = cacheQueueSize,
                    ),
                )
            try {
                SNS.send(message)
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
                            val syncResult =
                                processRemoteSongsSyncBatch(
                                    database = database,
                                    storageService = storageService,
                                    storageApiClient = storageApiClient,
                                )
                            if (syncResult.localUpdates + syncResult.localInserts > 0 || syncResult.renderSideEffects > 0) {
                                println(
                                    "[${Timestamp.from(Instant.now())}] ProcessWorker: Sync-batch обработан: " +
                                        "updates=${syncResult.localUpdates}, inserts=${syncResult.localInserts}, " +
                                        "renderSideEffects=${syncResult.renderSideEffects}, sql=${syncResult.sqlQueryCount}",
                                )
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
                            // FR-003 (US2): периодический вызов sendCountWaitingMessage
                            // удалён — реальные изменения счётчика доходят до UI через
                            // call-sites createDbInstance/run/forceStop/start, которые
                            // проходят через sendCountWaitingMessage с дедупликацией.
                            // Прежний код вызывал getCountWaiting + sendCountWaitingMessage
                            // на каждой итерации цикла, создавая спам и лишнюю нагрузку
                            // на БД (SELECT count(*) на tbl_processes каждые ~10 мс).
                            if (karaokeProcess != null && (!stopAfterThreadIsDone || karaokeProcess.command == "tail")) {
                                // BR-033 (аудит #336, пакет PKG-S2-10): невалидные args
                                // (пустой список / пустой шаг) раньше роняли главный цикл
                                // (args[0]) или навсегда блокировали лейн. Переводим задание
                                // в ERROR с причиной и продолжаем цикл — лейн не блокируется.
                                val argsError = ProcessArgsPolicy.validationError(karaokeProcess.args)
                                if (argsError != null) {
                                    println(
                                        "[${Timestamp.from(Instant.now())}] ProcessWorker: ERROR задания ${karaokeProcess.id} " +
                                            "(${karaokeProcess.type}): $argsError",
                                    )
                                    karaokeProcess.status = KaraokeProcessStatuses.ERROR.name
                                    karaokeProcess.description = "${karaokeProcess.description} [$argsError]"
                                    karaokeProcess.save()
                                } else {
                                    if (id > 0) {
                                        val kp = KaraokeProcess.load(id, database)
                                        val diffs = KaraokeProcess.getDiff(kp)
                                        if (diffs.isNotEmpty()) {
                                            karaokeProcess.saveProgress()
                                        }
                                    }
                                    threadsMap[threadId] = KaraokeProcessThread(karaokeProcess)

                                    id = karaokeProcess.id
                                    val firstArg = karaokeProcess.args.firstOrNull()?.firstOrNull()
                                    if (karaokeProcess.command != "tail" || firstArg !in argsIgnoredToLog) {
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
                                        threadsMap[threadId]?.karaokeProcess?.saveProgress()
                                    }
                                }
                            }
                        } else {
                            // Признак "без контроля" берётся КОНКРЕТНОГО задания этого лейна, а не у
                            // общего флага воркера - иначе batch-задание в ДРУГОМ лейне могло бы отключить
                            // обычное сохранение диффа для этого лейна (и наоборот). См. FR-002/US3.
                            if (threadsMap[threadId]?.karaokeProcess?.withoutControl != true) {
                                val kp = threadsMap[threadId]?.karaokeProcess
                                val diffs = KaraokeProcess.getDiff(kp)
                                if (diffs.isNotEmpty()) {
                                    threadsMap[threadId]?.karaokeProcess?.saveProgress()
                                }
                            }
                        }
                    }

                    // FR-003 (US2): периодический вызов sendCountWaitingMessage
                    // для пустой очереди удалён — счётчик обновляется через call-sites
                    // run() (завершение задания) и deleteDone() на следующей итерации
                    // цикла, а периодический SELECT count(*) + send был источником спама.

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
                                thread?.karaokeProcess?.saveProgress()
                            }
                        }
                    }
                }
            }
        }

        private fun doStop() {
            stopAfterThreadIsDone = true
        }

        /**
         * Результат одной итерации sync-цикла `doStart()` (push LOCAL→SERVER). Используется
         * для логирования и подсчёта SQL-запросов в целях верификации эффекта (см. SC-001/SC-002
         * в [specs/242-db-sync-batch-worker/spec.md]).
         */
        private data class SyncBatchResult(
            val localUpdates: Int,
            val localInserts: Int,
            val renderSideEffects: Int,
            val sqlQueryCount: Int,
        )

        /**
         * Батч-обработка sync-записей из REMOTE-БД. Заменяет N+1 (1 + 2N SQL) на пакетные
         * операции: `loadListFromDbByIds` для SELECT LOCAL чанками по [SYNC_SELECT_CHUNK_SIZE]
         * (= `SongSyncTarget.rowChunkSize`, 25 — валидировано под `socketTimeout=30` на тяжёлых
         * строках Song) и `KaraokeDbTable.deleteIn` для DELETE sync чанками по
         * [SYNC_DELETE_CHUNK_SIZE] (= `SyncRegistry.DELETE_CHUNK_SIZE`, 200 — DELETE лёгкий,
         * можно крупные пачки). На 100 sync-записях: 201 → ≤ 11 SQL.
         *
         * Per-record side-effects сохранены 1-в-1: UPDATE/INSERT через существующую diff-логику,
         * а RENDER-логика (генерация `.srt` + `KaraokeProcess.createProcess(MELT_*)`) выполняется
         * per-song без объединения — это тяжёлая операция, специфичная для конкретной песни
         * (см. Edge Cases в spec.md).
         *
         * @return [SyncBatchResult] со счётчиками и общим числом SQL.
         * @see archive/docs/features/dual-db-sync.md
         * @see specs/242-db-sync-batch-worker/spec.md (FR-101 из parent FR-A spec 241)
         */
        private fun processRemoteSongsSyncBatch(
            database: KaraokeConnection,
            storageService: KaraokeStorageService,
            storageApiClient: StorageApiClient,
        ): SyncBatchResult {
            // 1 SQL: получаем все sync-записи из REMOTE-БД
            val listSongsSync =
                Song.loadListFromDb(
                    database = Connection.remote(),
                    sync = true,
                    storageService = storageService,
                    storageApiClient = storageApiClient,
                )
            if (listSongsSync.isEmpty()) return SyncBatchResult(0, 0, 0, 1)

            // Чанкованный SELECT LOCAL по всем id разом (N SELECT → ceil(N / SYNC_SELECT_CHUNK_SIZE))
            val localIds = listSongsSync.map { it.id }
            val localSongsMap = mutableMapOf<Long, Song>()
            for (chunk in localIds.chunked(SYNC_SELECT_CHUNK_SIZE)) {
                val songsForChunk =
                    Song.loadListFromDbByIds(
                        ids = chunk,
                        database = database,
                        storageService = storageService,
                        storageApiClient = storageApiClient,
                    )
                for (song in songsForChunk.values) {
                    localSongsMap[song.id] = song
                }
            }

            var updates = 0
            var inserts = 0
            var renderSideEffects = 0

            // Per-record логика (UPDATE/INSERT + RENDER-side-effect) — без изменений, кроме того
            // что songLocal берётся из pre-loaded map вместо отдельного SELECT на каждой итерации.
            listSongsSync.forEach { songSync ->
                val songLocal = localSongsMap[songSync.id]
                if (songLocal != null) {
                    val diff = Song.getDiff(songSync, songLocal)
                    val setStr =
                        diff
                            .filter { it.recordDiffRealField }
                            .joinToString(", ") { "${it.recordDiffName} = ?" }
                    if (setStr != "") {
                        val sql = "UPDATE tbl_songs SET $setStr WHERE id = ?"
                        val connection = Connection.local().getConnection()
                        if (connection == null) {
                            println("[${Timestamp.from(Instant.now())}] Невозможно установить соединение с базой данных LOCAL")
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
                                    updateRemoteSongFromLocalDatabase(songLocal.id)
                                if (listCreate.size + listUpdate.size + listDelete.size != 0) {
                                    SNS.send(SseNotification.crud(listOf(listCreate, listUpdate, listDelete)))
                                }
                            }
                        }
                        updates++
                    }
                } else {
                    val sqlToInsert = songSync.getSqlToInsert()
                    val connection = Connection.local().getConnection()
                    if (connection == null) {
                        println("[${Timestamp.from(Instant.now())}] Невозможно установить соединение с базой данных LOCAL")
                    } else {
                        val ps = connection.prepareStatement(sqlToInsert)
                        ps.executeUpdate()
                        ps.close()
                    }
                    inserts++
                }

                // RENDER-side-effect: per-song операция (генерация .srt + создание karaoke-процессов),
                // объединение в батч невозможно — каждая песня обрабатывается индивидуально.
                if (songSync.tags == "RENDER" && songLocal != null) {
                    applyRenderSideEffect(songLocal)
                    renderSideEffects++
                }
            }

            // Чанкованный DELETE sync-записей из REMOTE (N DELETE → ceil(N / SYNC_DELETE_CHUNK_SIZE))
            val deleteChunks = localIds.chunked(SYNC_DELETE_CHUNK_SIZE)
            deleteChunks.forEach { chunk ->
                KaraokeDbTable.deleteIn(
                    tableName = Song.TABLE_NAME,
                    ids = chunk,
                    database = Connection.remote(),
                    sync = true,
                )
            }

            val selectChunkCount = localIds.chunked(SYNC_SELECT_CHUNK_SIZE).size
            val totalSql = 1 + selectChunkCount + deleteChunks.size
            return SyncBatchResult(updates, inserts, renderSideEffects, totalSql)
        }

        /**
         * Side-effect для sync-записи с `tags == "RENDER"`: генерирует `.srt`-файлы по голосам,
         * запускает `createKaraoke(lyrics, karaoke)` и создаёт два `KaraokeProcess`
         * (MELT_LYRICS + MELT_KARAOKE). Вынесено из основного цикла для читаемости
         * (`processRemoteSongsSyncBatch` ≤ 60 строк, см. SC-004). Per-song операция — не
         * батчится.
         *
         * @see archive/docs/features/dual-db-sync.md
         */
        private fun applyRenderSideEffect(songLocal: Song) {
            // specs/544-song-artifact-cleanup (FR-008): .srt-файлы больше не пишутся;
            // разметка голосов сохраняется в БД, экспорт — функцией Song.convertMarkersToSrt.
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

        // Принудительная (жёсткая) остановка очереди: в отличие от doStop() (мягкое ожидание
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

        /**
         * Удаляет регулярные файлы из [PATH_TO_LOGS], чей mtime старше [LOG_RETENTION_DAYS] дней.
         * Fail-open (FR-4): все исключения логируются через println и НЕ пробрасываются.
         * Вызывается после успешного [File.writeText] лога в [KaraokeProcessThread.run] (FR-3).
         *
         * @see specs/310-ochistka-papki-logov/spec.md
         */
        fun cleanupOldLogs() {
            cleanupOldLogsIn(Paths.get(PATH_TO_LOGS), LOG_RETENTION_DAYS)
        }

        /**
         * Internal-видимая имплементация cleanup. Выделена отдельно, потому что:
         * - [PATH_TO_LOGS] — `const val` (top-level), не подменяется в unit-тестах;
         * - [LOG_RETENTION_DAYS] — `const val` (top-level), порог в днях для retention.
         *
         * Тесты вызывают эту функцию напрямую с [Path] из @TempDir и явным retentionDays.
         *
         * Граница (AC-2): файл с mtime == threshold удаляется. KEEP-условие —
         * `mtime.isAfter(threshold)` (strict `>`), иначе delete. Защита от clock skew
         * (FR-6): файлы с `mtime.isAfter(now)` пропускаются.
         *
         * Per-file [IOException] (FR-4): пропустить файл, продолжить; счётчик
         * `removed` отражает только успешные удаления.
         *
         * @see specs/310-ochistka-papki-logov/spec.md
         */
        internal fun cleanupOldLogsIn(dir: Path, retentionDays: Int) {
            try {
                val now = Instant.now()
                val threshold =
                    now.minus(
                        retentionDays.toLong() * 24L * 60L * 60L * 1000L,
                        ChronoUnit.MILLIS,
                    )
                var removed = 0
                Files.list(dir).use { stream ->
                    stream.forEach { p ->
                        try {
                            if (Files.isRegularFile(p)) {
                                val mtime = Files.getLastModifiedTime(p).toInstant()
                                val isFuture = mtime.isAfter(now)
                                val olderThanThreshold = !mtime.isAfter(threshold)
                                if (olderThanThreshold && !isFuture) {
                                    Files.delete(p)
                                    removed++
                                }
                            }
                        } catch (e: IOException) {
                            println("cleanup: failed to process ${p.fileName}: ${e.message}")
                        }
                    }
                }
                if (removed > 0) {
                    println("cleanup: removed $removed files older than $retentionDays days from $dir")
                }
            } catch (e: Exception) {
                println("cleanup: unexpected error in cleanupOldLogsIn($dir, $retentionDays): ${e.message}")
            }
        }
    }
}
