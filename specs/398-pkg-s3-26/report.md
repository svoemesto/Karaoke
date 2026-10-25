# PKG-S3-26 (OpenProject #398) — karaoke-app: DATA, ERR, PERF, RACE, RULE, SEC — 19 BR

- **Аудит**: #336, волна E, пакет `PKG-S3-26` (`specs/336-code-audit/backlog.md`, блок `### PKG-S3-26`).
- **Ветка**: `626-pkg-s3-26` от `origin/master` `d5fbab3f` (merge PR #722, реестр волны E).
- **Worktree**: `/home/nsa/Karaoke/.worktrees/waveE-s3-26`.
- **Источник правды**: карточки `specs/336-code-audit/findings/P-01.md`, `P-02.md`, `P-03.md`,
  `P-07b.md`, `P-29.md`, `P-34.md`; порции определены по `registry.csv` (`work_package = 398`).
- **Что НЕ делалось**: миграции не применялись ни к LOCAL, ни к PROD; контейнеры, прод, БД и
  `specs/336-code-audit/registry.csv` не трогались; деплоя не было; push/PR не делались.
- **Режим**: пакет собран одним проходом (19 записей), регрессия — двумя классами тестов.

## Сводка

| Блок | BR | Статус |
|---|---|---|
| Health-отчёты и ремонт | BR-280…BR-287 (8) | ✅ исправлены |
| Настройки, словари, крипта | BR-276, BR-289, BR-290, BR-291, BR-292, BR-294 (6) | ✅ исправлены |
| Хранилище, обложки, гигиена | BR-288, BR-293, BR-526, BR-531 (4) | ✅ исправлены |
| — | закрытых ранее чужими пакетами записей нет | — |

**Все 19 записей пакета закрыты.** Ни одна запись не оказалась «уже исправлена другим
пакетом»: фактическое состояние кода проверено по каждой строке реестра до правки
(см. «Проверка фактического состояния»).

---

## Проверка фактического состояния (до правки)

Для каждой из 19 записей прочитан актуальный код (номера строк в бэклоге — от снимка
аудита `1977838a`; ниже — строки текущей ветки). Ни одна запись не была помечена
«исправлено ранее»: все 19 дефектов присутствовали в коде.

| BR | Где смотрел | Что было в коде на начало пакета |
|---|---|---|
| BR-276 | `Karaoke.kt` (сеттер `voices`) | `KaraokeProperties.set("flashColor", getStringFromVoices(value))` — копипаста |
| BR-277 | `Utils.kt` (`actionToDeleteFileAndFolderIfFolderEmpty`) | `File(pathToFile).exists()` + `rm -f` |
| BR-280 | `HealthReport.kt` (`actionsLocalStorage`/`actionsRemoteStorage`/`actionsLocalFileSystem`) | по одному `KaraokeProcess.loadList(...)` на пару «тип × локация» |
| BR-281 | `HealthReport.kt` (`cachedFileInfo`) | `catch (e: Exception) { null }` без лога; вызывающий `localInfo == null || …` |
| BR-282 | `HealthReport.kt` (`startRepairAll`) | `recomputeAndBroadcast` до `submit` и вне `try` |
| BR-283 | `HealthReport.kt` (`actionsRemoteStorage`) | `catch (e: Exception) { println("Ошибка при удалении …") }` (2 места) |
| BR-284 | `HealthReport.kt` (`executeResolvable`, `repairExecutor.submit`) | без `catch`; в задаче пула только `finally` |
| BR-285 | `HealthReport.kt` + `ApiController.kt` (`executeHealthReportActions`) | `?.executeSolutionActions()` в потоке Tomcat |
| BR-286 | `HealthReport.kt` (`symlinkFolderReports`) | `println(...)` + `return@forEach` |
| BR-287 | `HealthReport.kt` (`recalculatePlayerReadiness`) | `var failed = 0` … `return total` |
| BR-288 | `CacheEtagSizeBackfill.kt` (`loadRowsNeedingBackfill`) | `catch → return result` (пустой список) и `?: return result` при null-соединении |
| BR-289 | `Extentions.kt` (`String.censored`) | `"…$uncensored…".toRegex()` без `Regex.escape` |
| BR-290 | `Functions.kt` (`getVoices`) | `tmpLines.maxOf { it.lineDurationMs() }` → деление на 0 |
| BR-291 | `Crypto.kt` | `ex.printStackTrace()` + `return null` в обеих операциях; длины ключа/IV не проверяются |
| BR-292 | `Converter.kt` (`getVoicesFromString`) | `partsVoiceFields[1]`, `parts[0]`/`parts[1]` без проверки; `Color(...)` вне `try` |
| BR-293 | `AlbumCoverFinder.kt` (`findYandexAlbumCovers`) | `fetchYandexArtistAlbumsHtml(...)` без `try` |
| BR-294 | `Extentions.kt` + `Utils.kt` (`rescanAllCensoredNames`) | `CensoredWordsDictionary(...).dict` + `toRegex()` на каждый вызов, в цикле по песням |
| BR-526 | `karaoke-app/src/main/resources/test.xml` | файл на месте (463 строки, ПДн-подобные поля), потребителей 0 |
| BR-531 | `karaoke-app/src/test/kotlin/Правильный.xml` (+ зеркала `Неправильный.xml`, `1`, `2.xml`) | файлы на месте (1 887 + 1 975 + 1 916 + 1 359 строк), потребителей 0 |

---

## По записям

| BR | Файл:строка (было) | Класс | Что не так | Как правильно | Проверка |
|---|---|---|---|---|---|
| BR-276 | `karaoke-app/.../Karaoke.kt:664-668` | DATA | Сеттер `Karaoke.voices` писал строку голосов в ключ **`flashColor`** (копипаста с соседнего свойства): голоса не сохранялись (геттер читал прежнее значение), а настройка цвета флеша затиралась нецветовой строкой (`getColorFromString` вернул бы белый и напечатал предупреждение) | Ключ **`"voices"`**; добавлен round-trip тест «запись → чтение» (`Converter.getStringFromVoices` → `getVoicesFromString`) | контрактная (1) + поведенческая (1) |
| BR-277 | `karaoke-app/.../Utils.kt:4828-4853` | ERR | `actionToDeleteFileAndFolderIfFolderEmpty` определял существование через `File(path).exists()`; для **битого** симлинка это `false` (Java следует по ссылке) → действие «Удалить файл» для проблемы «Битая символическая ссылка» (`HealthReport.symlinkFolderReports`, `canResolve=true`) было no-op: ссылка оставалась, проблема возвращалась на каждом прогоне | Existence = `File.exists() \|\| Files.isSymbolicLink(path)`; удаление — `Files.deleteIfExists(path)` (единственный корректный способ для битой ссылки; `rm -f` заменён) | контрактная (1) + поведенческая (1) |
| BR-280 | `karaoke-app/.../HealthReport.kt:770-781` (LOCAL), `:1180-1192` (REMOTE), `:489-491`/`:505-507` (LOCAL_FILESYSTEM) | PERF | На каждую пару «тип файла × локация» — свой `KaraokeProcess.loadList` (`SELECT tbl_processes.*`): ~18 запросов на песню, тысячи на страницу «Песни» (ADR-0001: пакетная загрузка, не по одной) | Задания песни читаются **одним** `loadList(args = mapOf("song_id" to …))` в `getHealthReportList` и раздаются в `actions*` параметром `songProcesses`; отбор — `filterSongProcesses(...)` в памяти (те же правила: `song_id`, `process_status`, `process_type`, `thread_id`, `process_args LIKE`) | контрактная (1) + поведенческая (1) |
| BR-281 | `karaoke-app/.../HealthReport.kt:196-211` (`cachedFileInfo`), `:800-805` (LOCAL), `:1207-1212` (REMOTE) | ERR | `cachedFileInfo` ловил любое исключение и возвращал `null` **без лога**; `null` означал сразу три состояния (нет строки кеша / «объекта нет» (`size<0`) / «вызов не удался»), а вызывающий трактовал `null` как «файл актуален» (`localInfo == null \|\| …`) → при недоступном MinIO отчёт был `OK` | Типизированный `StorageFileInfoProbe` (`Known` / `Missing` / `Unknown`) + вердикт `fileActualityVerdict` → `FileActualityVerdict` (`ACTUAL` / `NOT_ACTUAL` / `DISK_VANISHED` / `UNKNOWN`). «Не удалось узнать» → `WARNING` + `storageStateUnknown = true` (флаги готовности не сбрасываются), `WARN` с throwable в `catch` | контрактная (1) + поведенческая (1) |
| BR-282 | `karaoke-app/.../HealthReport.kt:2814-2816` | RACE | `startRepairAll` брал guard (`attemptEnterRepair`), затем **до** `repairExecutor.submit` вызывал `recomputeAndBroadcast` вне `try`. Исключение (сбой БД в `saveToDbLocked`, ошибка `SNS.send`) оставляло `repairInFlight[songId] = true` навсегда → «Исправить всё» и каскад для песни молча умирали до рестарта приложения | Guard берётся и снимается в `try/catch/finally` вокруг **всего** тела (включая синхронный `recomputeAndBroadcast` и `submit`) | контрактная (1) |
| BR-283 | `karaoke-app/.../HealthReport.kt:1229-1237`, `:1442-1451` | ERR | Ошибка удаления объекта из REMOTE гасилась `catch { println(…) }`: лямбда завершалась «нормально», остальные действия выполнялись, UI показывал «исправлено», а объект оставался в MinIO | `deleteRemoteFileOrThrow`: SLF4J `ERROR` (`infra.health.report`) с throwable **и** проброс исключения в repair-цикл, где действие помечается неуспешным | контрактная (1) + поведенческая (1) |
| BR-284 | `karaoke-app/.../HealthReport.kt:2791-2795` (`executeResolvable`), `:2820-2830` (`repairExecutor.submit`) | ERR | Лямбды выполнялись подряд без обработки исключений: исключение одного действия обрывало **весь** каскад (остальные не выполнялись, финальный `recomputeAndBroadcast` и SSE не уходили, пользователь не получал ни ошибки, ни обновления); в задаче пула был только `finally` | Каждое действие — в своём `try/catch` (лог с throwable); `executeResolvable` возвращает число неуспешных действий; в задаче пула — `catch` с логом и SSE `type=error` (`reportRepairFailure`) | контрактная (1) + поведенческая (1) |
| BR-285 | `karaoke-app/.../HealthReport.kt:944-951`, `1335-1341` (в `solutionActions`) + `controllers/ApiController.kt:8357-8381` | RULE | `solutionActions` содержат блокирующие операции MinIO (`downloadFile` — десятки МБ, `.block()`), а `POST /api/song/executeHealthReportActions` выполнял их **синхронно в потоке Tomcat** — нарушение инварианта storage-домена № 4 («MinIO-операции НЕ выполняются в HTTP-handler-треде») | Действие уходит в `repairExecutor` (`HealthReport.executeHealthReportActionsAsync`), эндпоинт отвечает **`202 Accepted`**, фактический результат — по SSE (`HEALTH_REPORTS` + сообщение об ошибке) | контрактная (1) |
| BR-286 | `karaoke-app/.../HealthReport.kt:2529-2535` (`symlinkFolderReports`) | ERR | Ошибка чтения каталога симлинков гасилась `println` + `return@forEach`: каталог выпадал из аудита, отчёт выглядел здоровым, `OK` был неотличим от «не проверили» | `WARNING`-запись без действия («Каталог ссылок не прочитан: …») + SLF4J `WARN` с throwable (`infra.health.report`) | контрактная (1) |
| BR-287 | `karaoke-app/.../HealthReport.kt:2677-2699` + `controllers/ApiController.kt:6052-6068` | ERR | `recalculatePlayerReadiness` считал `failed`, но возвращал `total` → SSE всегда показывал «Проверено песен: N», даже если сотни песен упали; причина — только `println` без стека | Возвращается `ReadinessRecalcResult(processed, failed)`; SSE: при `failed > 0` — `type=error` с обоими числами; ошибка логируется через SLF4J с throwable | контрактная (1) + поведенческая (1) |
| BR-288 | `karaoke-app/.../CacheEtagSizeBackfill.kt:169-192` | ERR | Ошибка выборки из БД (и `null`-соединение) превращалась в пустой список → оператор видел SSE `info` «Обработано 0 строк … завершено» вместо ошибки; «пропуски» в подсказке не появлялись, потому что `skipped == 0` | `loadRowsNeedingBackfill` **пробрасывает** сбой (`SQLException` при отсутствии соединения, исключение БД) — вызывающий шлёт SSE `error` и не печатает «завершено» | контрактная (1) |
| BR-289 | `karaoke-app/.../Extentions.kt:337-340` (`String.censored`) | ERR | Значение словаря «Censored» подставлялось в regex **как есть**: `х*й` превращалось в шаблон и маскировало «й» в любом слове (искажённый `song_name_censored` уходил в БД), а значение с незакрытой скобкой роняло `toRegex()` (`PatternSyntaxException`) → падало сохранение песни (`Song.saveToDb`) и массовое пересканирование | `Regex.escape` при сборке шаблона; пустые и неразбираемые значения отбрасываются с записью в лог; правила собираются один раз (`CensoredDictionaryRules.buildRules`) | контрактная (1) + поведенческая (3) |
| BR-290 | `karaoke-app/.../Functions.kt:243-246`, `:284`, `:316` | ERR | `maxDuration` считался по **всем** линиям, включая нулевые (`newLine`/`emptyLine` и комментарии дают `lineStartMs == lineEndMs`); голос без единой непустой по времени линии → `maxDuration == 0` → `ArithmeticException: / by zero` (создание проекта падало: 500 / `ERROR` в очереди) | Максимум считается только по линиям с **положительной** длительностью; деление выполняется только при `maxDuration > 0` (иначе пустые строки не вставляются) | контрактная (1) + поведенческая (1) |
| BR-291 | `karaoke-app/.../Crypto.kt:83-86`, `:97-99` | ERR | Обе операции при любом исключении делали `ex.printStackTrace()` в stdout и возвращали `null`: ни логгера, ни контекста, ни признака ошибки у вызывающего (`null` неотличим от «значения нет»); длина ключа/IV не проверялась → неверный `CRYPTO_AES_KEY` тихо превращал шифрование в `null`, а вызывающий отправлял в удалённую БД **пустую строку** | SLF4J `log.error("crypto: encrypt/decrypt failed op=…")` с throwable (без значений и ключей); `validateKeyMaterial` (ключ 16/24/32 байта, IV 16 байт) бросает `CryptoConfigurationException`; проверка конфигурации — при старте (`KaraokeAppService.init`, категория `infra.crypto.config`); операции при неверной конфигурации **отказываются** работать (fail-closed) | контрактная (1) + поведенческая (1) |
| BR-292 | `karaoke-app/.../Converter.kt:30-32`, `:41-42`, `:71-72`, `:171-173` | ERR | Парсер конфигурационной строки `voices` индексировал результат `split` без проверки размера (`partsVoiceFields[1]`, `parts[0]`/`parts[1]`) — `IndexOutOfBoundsException` **вне** `try`; `Color(...)`/`Font(...)` строились вне `try` — значение из БД вида `r=300` давало `IllegalArgumentException`. Настройка `voices` записывается через `POST /api/properties/setproperty` без валидации | После `split` — `getOrNull` (нет поля → дефолт + лог `infra.converter`); `Color`/`Font` строятся внутри `try` с fallback (`safeColor`) | контрактная (1) + поведенческая (2) |
| BR-293 | `karaoke-app/.../AlbumCoverFinder.kt:213` (`findYandexAlbumCovers`) | ERR | Яндекс-ветка не имела границы ошибок: `Playwright.create()` / `launchPersistentContext(USER_DATA_DIR)` / `page.navigate(...)` бросали, исключение проходило в контроллер → 500; фолбэк (SearXNG/fourget) выполнялся только для `NotFound`, т.е. при любом сбое поиск обложки был невозможен | Яндекс-ветка обёрнута в `try/catch`: сбой логируется (`infra.cover`) и даёт `AlbumCoverSearchOutcome.NotFound` — фолбэк работает при любом сбое | контрактная (1) |
| BR-294 | `karaoke-app/.../Extentions.kt:235-251`, `Utils.kt:586` (`rescanAllCensoredNames`) | PERF | `censored()` на каждый вызов заново читал весь словарь из БД (`SELECT` по `tbl_dictionaries`) и компилировал по два regex на каждое слово; в пакетном `rescanAllCensoredNames` вызов шёл в цикле по всем песням → N SELECT'ов и N×M компиляций (на 18k песен — десятки тысяч лишних запросов) | Словарь читается **один раз на пакет** (`CensoredDictionaryRules.load`), шаблоны компилируются один раз (`buildRules`), в цикле применяются готовые правила (`text.censored(rules)`) | контрактная (1) + поведенческая (1) |
| BR-526 | `karaoke-app/src/main/resources/test.xml:1-463` | SEC | В `src/main/resources` (боевой classpath) лежал образец запроса ЕГРН из чужого проекта с ПДн-подобными полями (ФИО, телефон, e-mail, СНИЛС, адрес с квартирой). Потребителя не было (0 вхождений «test.xml» в коде/конфигах/Dockerfile), но файл упаковывался в `BOOT-INF/classes/` каждого боевого jar и лежал в публичном репозитории; guard'а на такие фикстуры нет | Файл **удалён**; регрессионный тест ищет в `src/main/resources` любые XML/JSON с полями ПДн (`snils`, `phoneNumber`, `passport`, `EGRNRequest`) | поведенческая (1) |
| BR-531 | `karaoke-app/src/test/kotlin/Правильный.xml:1-1887` (+ зеркала `Неправильный.xml`, `1`, `2.xml`) | SEC | В каталоге тестовых исходников лежали реальные MLT-проекты с абсолютными путями `~/Documents/Караоке/…` (пути к stem-файлам, `done_projects`, название песни автора) — внутренние данные автора в публичном репозитории и в classpath тестов; потребителей нет | Файлы **удалены** (все четыре — один корень «личные пути в репозитории», лид аудита объединил локации в BR-531); регрессионный тест ищет в `src/test` любые фикстуры с `~/Documents/`, `/home/`, `C:\Users\` | поведенческая (1) |

