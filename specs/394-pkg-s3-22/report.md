# Отчёт по OpenProject #394 (PKG-S3-22, волна E) — karaoke-app: COMPAT, DATA, ERR, PERF, RACE, SEC

- **Задача**: OpenProject #394 «[#336] S3: karaoke-app: COMPAT, DATA, ERR, PERF, RACE, SEC — 40 BR»
- **Записи пакета**: BR-034, BR-035, BR-036, BR-037, BR-051, BR-052, BR-053, BR-054,
  BR-055, BR-056, BR-057, BR-058, BR-059, BR-061, BR-065, BR-066, BR-067, BR-068,
  BR-069, BR-070, BR-072, BR-073, BR-074, BR-075, BR-076, BR-077, BR-078, BR-079,
  BR-080, BR-081, BR-082, BR-083, BR-084, BR-085, BR-086, BR-103, BR-105, BR-106,
  BR-107, BR-114 (реестр: `specs/336-code-audit/registry.csv`)
- **Карточки-источники**: `specs/336-code-audit/findings/P-04.md`, `P-08.md`,
  `P-09a.md`, `P-09b.md`, `P-18a.md`, `P-18b.md`
- **ТЗ**: `specs/336-code-audit/backlog.md`, блок `### PKG-S3-22`
- **Ветка**: `612-pkg-s3-22`, worktree `.worktrees/waveE-s3-22`, база — `origin/master` @ `512203aa`
- **PR / push / merge / деплой**: не выполнялись (по брифингу).
- **Per-feature документ (FR-009)**: `docs/features/pkg-s3-22-audit-fixes.md`
- **SSoT**: `knowledge/domains/processing/components/async-process-queue.md`,
  `knowledge/domains/identity/components/security-config.md`,
  `knowledge/system/utilities.md`,
  `knowledge/domains/catalog/components/song-entity.md`,
  `knowledge/domains/catalog/components/dictionaries.md`,
  `knowledge/domains/catalog/components/pictures.md`,
  `knowledge/domains/health/components/health-report.md`

Работа ведётся партиями по ~5–15 записей с коммитом после каждой партии
(пакет большой: 40 записей). **Состояние: закрыты все 40 записей** (партии 1–4) —
ниже отчёт по каждой партии.

---

## Партия 1 (P-04 + P-08): BR-034, BR-035, BR-036, BR-037, BR-114 — закрыто

### BR-034 (P-04-07): `filter_limit` валил запрос в HTTP 500

**Было** (`KaraokeProcess.loadList`): `args["filter_limit"]?.toInt()` — `NumberFormatException`
на нечисле (`?filterLimit=abc`), затем `for (i in 0 until limit) resultLimit.add(result[i])` —
`IndexOutOfBoundsException`, когда лимит больше числа строк (`?filterLimit=1000` при 50 заданиях).
Оба исключения не ловились (`catch` — только `SQLException`), наружу уходил 500.

**Стало**:
- `KaraokeProcess.applyFilterLimit(result, limitValue)` — срез через `take(limit)`;
  `null`/нечисло/отрицательное — `IllegalArgumentException` с текстом;
- `POST /api/processesdigests` (`ApiController.apisProcessesDigest`) валидирует
  `filterLimit` и отвечает **400** с причиной (`ResponseEntity`);
- HTML-страница `GET /processes` (`MainController`) некорректный необязательный
  фильтр игнорирует (не 400 — это не контракт страницы).

### BR-035 (P-04-08): `mediainfo` без таймаута вешал поток очереди

**Было** (`MediaInfo.kt:29–40`): `ProcessBuilder(...).start()`, `waitFor()` закомментирован,
stdout читался до EOF синхронно, код возврата не проверялся; сбой превращался в `null`
(`getInfoBySectionAndParameter`), `Song.durationInMilliseconds` подставлял `0.0` — и MLT
генерировался с `out=00:00:00.000`, оставаясь «успешным» заданием.

**Стало**:
- единая точка запуска `MediaInfo.runMediaInfo(param, timeoutSeconds)`: stdout читается
  **отдельным демон-потоком** (EOF наступает только после выхода процесса),
  завершение ждётся через общий `awaitProcessExit` (`waitFor(timeout)` +
  `destroyForcibly()`), ненулевой код → `MediaInfoException` с хвостом вывода;
- таймаут `mediaInfoTimeoutSeconds()` — `KARAOKE_MEDIAINFO_TIMEOUT_SECONDS`, по умолчанию 30 с;
- `getInfoBySectionAndParameter` логирует `WARN` с путём файла и причиной (не молчит),
  касты `as?` вместо `as`;
- `Song.durationInMilliseconds` — `toDoubleOrNull() ?: 0.0` (нечисловой ответ больше не
  бросает `NumberFormatException`);
- `executeMediaInfo` больше не игнорирует переданные аргументы (была жёстко зашитая команда
  с тестовым файлом) и идёт через тот же `runMediaInfo`.

### BR-036 (P-04-09): восстановление при старте глушило сбой, причина `ERROR` терялась

**Было**: `deleteDone` / `setWorkingToWaiting` ловили `Exception` и печатали `e.message`
(без стека, без признака «не удалось»), после чего `start()` продолжал запуск. Если
`setWorkingToWaiting` не сработал, `WORKING`-строки оставались, воркер их не выбирает
(`getProcessesToStart` берёт только `WAITING`) — лейн стоит, а очередь выглядит работающей.
При `ERROR` в БД писался только статус, причина уходила в stdout контейнера.

**Стало**:
- `deleteDone`/`setWorkingToWaiting` возвращают `Boolean`, исключение логируется
  `ERROR` со стеком (SLF4J);
- `KaraokeProcessWorker.start` при `false` отправляет SSE-ошибку
  (`SseNotification.error`) с перечнем неудавшихся шагов;
- причина сбоя задания дописывается в `process_description`
  (`KaraokeProcess.appendErrorReason`: маркер ` | ERROR: `, тип + сообщение,
  одна строка, до 500 символов, идемпотентно) — оператор видит её в админке.

### BR-037 (P-04-10): soft-deleted задания возвращались и «вечно» блокировали песню

**Было**: `loadList` не фильтровал `process_deleted_at`, поэтому soft-deleted задание
(`KaraokeProcessAdminService.deleteProcess` ставит метку, но НЕ меняет статус) оставалось
видимым в `/api/processesdigests`, а `hasActiveProcess` навсегда видел `WAITING` —
переименование файла песни блокировалось «выполняется фоновая обработка», sync-audio
пропускал ребёнка. `KaraokeProcessDTO` признака удаления не имел.

**Стало**:
- `loadList` добавляет `process_deleted_at IS NULL` по умолчанию; escape hatch —
  явный `include_deleted` (для инструментов разбора/восстановления);
- `KaraokeProcessDTO.processDeletedAt` отдаёт признак наружу;
- `hasActiveProcess` идёт через `loadList` и получает фильтр автоматически.

### BR-114 (P-08-05): admin-API доступен анонимно, механизма входа нет

**Было**: единственное правило `authenticated()` для `/api/private/…` (контроллеров с таким
путём в репозитории нет), всё остальное `permitAll()`; `formLogin`/`httpBasic`/
`UserDetailsService` не настроены вовсе. Все admin-эндпоинты, включая меняющие состояние,
доступны анонимно; `X-Admin-Username` подделывается тривиально.

**Стало (максимум безопасного без решения владельца)**:
- `config/AdminApiTokenFilter.kt` — при заданном `karaoke.security.admin-token`
  (env `KARAOKE_ADMIN_TOKEN`) запросы к admin-префиксам (`/api/admin/`, `/api/health/`,
  `/api/monitor/`, `/api/cleanup/`, `/api/dictionaries/`, `/api/news/`,
  `/api/promorules/`, `/api/publicsettings/`, `/api/sharelinks/`, `/api/stemjobs/`)
  обязаны нести `X-Admin-Token`, иначе **401**; сравнение — `MessageDigest.isEqual`;
- без токена поведение прежнее (нельзя включить `authenticated()`: приложение не выпускает
  аутентифицированного субъекта, админка получила бы 401 на каждый запрос), но при первом
  admin-запросе в лог уходит `WARN` «Admin-API открыт»;
- в `SecurityConfig` состояние периметра зафиксировано комментарием и `WARN` при старте.

**За владельцем**: выбор механизма аутентификации (form-login / basic-auth на nginx /
mTLS / SSO) и включение `karaoke.security.admin-token` в деплое. `X-Admin-Username`
по-прежнему не является идентичностью — это отдельное решение вместе с механизмом.

### Тесты партии 1

`karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/audit/PkgS322ProcessTest.kt` (13 тестов) и
`PkgS322SecurityTest.kt` (7 тестов). Каждый — «до/после»: рядом лежит реплика старого
поведения (`assertThrows<IndexOutOfBoundsException>`, `assertThrows<NumberFormatException>`,
проверка «в SQL не было `process_deleted_at`»), затем проверка нового шва.

Поведенческие (не статические) тесты:
- BR-035 — реальный «зависший» shell-скрипт (`sleep 30`): `runMediaInfo(timeout = 1)`
  возвращает `MediaInfoException` быстрее 10 с; скрипт с `exit 2` даёт ошибку с кодом и
  хвостом вывода;
- BR-034 — срез 1000 при трёх строках, `take` вместо индексации; нечисло/отрицательное — исключение;
- BR-036 — `deleteDone`/`setWorkingToWaiting` при `SQLException` возвращают `false`;
  `appendErrorReason` идемпотентен и ограничен по длине;
- BR-037 — SQL `loadList` содержит `process_deleted_at IS NULL`, с `include_deleted` — нет;
  DTO отдаёт `processDeletedAt`;
- BR-114 — фильтр: 401 без токена, пропуск с верным токеном, публичные пути не трогаются.

### Проверки партии 1

```
./gradlew :karaoke-app:test :karaoke-web:test   → BUILD SUCCESSFUL (602 теста)
:karaoke-app:ktlintCheck :karaoke-web:ktlintCheck → BUILD SUCCESSFUL
bash tools/check-knowledge-structure.sh          → OK (9/9)
python3 tools/lint-knowledge.py --baseline …     → OK
python3 tools/check-doc-references.py            → OK
python3 tools/check-recordhash-consistency.py    → OK
bash tools/check-no-mp4-mentions.sh              → OK (139 baseline)
bash tools/check-feature-doc.sh docs/features/*.md → OK
bash tools/check-subagent-isolation.sh           → см. «Оговорки»
```

Два ранее существовавших теста пришлось обновить (они сверяли точный текст SQL
`loadList`): `SqlInjectionBeforeAfterTest` и `SqlInjectionRegressionTest` (BR-028) —
к ожидаемой строке добавлен постоянный предикат `process_deleted_at IS NULL`
(сама проверка инъекции не изменилась).

---

## Партия 2 (P-09a + P-09b): BR-051…BR-070 — закрыто (16 записей)

Все правки — в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt`,
кроме BR-056 (`textfilehistory/TextFileHistory.kt`) и BR-057 (`model/Song.kt` + `ApiController`).

| BR | Было | Стало |
|---|---|---|
| BR-051 | `song.sheetsageInfo["tempo"] as String` → NPE → 500 на песне без Sheetsage | `sheetsageString`/`sheetsageStringList`/`sheetsageDoubleList`: пустая строка/список, чужая структура не валит запрос |
| BR-052 | `/songs/changed`, `/processes/changed`: `printStackTrace` + `return emptyList()` → 200 «изменений нет» | `log.error` с контекстом + `ResponseStatusException(500)`; закрытие JDBC — `log.warn` вместо `printStackTrace` |
| BR-053 | `result = true` вне `song?.let` — «прошло успешно» при пустой очереди | счётчик `queued`/`created`, успех = `> 0`, в сообщении — сколько поставлено/пропущено |
| BR-054 | семь chords-эндпоинтов формировали текст версии **LYRICS** | `SongVersion.CHORDS` в семи местах; лимит `textplchordsheader` выровнен (140 → 100) |
| BR-055 | 4 batch-эндпоинта грузили песни по одной (`loadFromDbById` в цикле) | `loadSongsBatch` (`WHERE id IN`, пачки по 25) + `distinct()` |
| BR-056 | история фильтров: read-modify-write без лока, `writeText`, повреждённый файл → «пустая история» | `ReentrantLock` на путь, `load-modify-save` под локом, атомарная запись (`ATOMIC_MOVE`), копия `*.corrupt-*` + `WARN` |
| BR-057 | `/publications/date` грузил всю таблицу песен (полная материализация) | `Song.loadPublicationDateCandidates` — проекция 4 колонок + тот же `SongStateResolver` |
| BR-058 | `notifyStatsDirty`: код ответа не проверялся, любое исключение → `println` | проверка `responseCode in 200..299`, `WARN` с хостом/кодом; исключение — `WARN` с причиной |
| BR-059 | текст «Пропущено (статус >= 3)» при условии `< 4` | константа `FORCED_ALIGN_MAX_ID_STATUS` + `forcedAlignSummary` — текст и условие из одного источника |
| BR-061 | тела части фоновых потоков без `catch`, часть ловила только `Exception` | `runBackgroundReported` (`Throwable` + `log.error` + SSE), upgrade `catch (Exception)` → `catch (Throwable)` в трёх backfill-потоках |
| BR-065 | `smartcopyperodbyday`: цикл по дням без предела и без `start <= end`, невалидная дата → 500 | невалидный формат/`start > end`/период > `SMART_COPY_MAX_PERIOD_DAYS` (31) → 400 с текстом |
| BR-066 | `playerfile`: ZIP в `ByteArrayOutputStream` + `toByteArray()` (2× архив в heap), `zip` не в `finally` | ZIP пишется сразу в `response.outputStream`, заголовки до тела, `zip.finish()`/`close()` в `finally`, сбой логируется |
| BR-067 | `convertFlacToMp3`: `waitFor()` без таймаута и без чтения stdout (pipe-буфер), два ffmpeg в один файл | вывод в временный файл (`redirectOutput`), `awaitProcessExit` (таймаут + `destroyForcibly`), лок по пути файла, `-loglevel error` |
| BR-068 | путь A массового поиска текста: **пустой** `catch` | `log.warn` с songId/движком + причины в SSE-сводку (`failedCount`, `failedReason`); подсчёт `minIntervalMs` не изменён |
| BR-069 | `extractlyricsbysearchresultid`: nullable → 200 с пустым телом (клиент падал на `JSON.parse('')`) | `ResponseEntity<SearchResultDTO>` + явный 404 |
| BR-070 | `/api/files`: несуществующий путь молча подменялся на `/` | 404 «путь не найден» / 400 «не каталог»; листинг корня — только при пустом `path` |

Тесты — `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/audit/PkgS322ApiControllerTest.kt` (20 тестов).
Поведенческие: BR-051 (пустая/чужая map, старое поведение — `assertThrows<NullPointerException>`),
BR-056 (5 потоков × 5 записей, все 25 строк после гонки; повреждённый файл → копия),
BR-057 (SQL-форма проекции + совпадение `stateAt` с `SongStateResolver`), BR-059 (текст из константы),
BR-065 (`parsePeriodDate` → `null`, старый `LocalDate.parse` → `DateTimeParseException`).
Статические guard'ы (у эндпоинтов нет тестового шва: глобальный `WORKING_DATABASE`, очередь
процессов, `HttpServletResponse`): BR-052, BR-053/055, BR-054, BR-066, BR-067, BR-068, BR-069, BR-070.
Для S3-мелочей это допустимо; каждая проверка падает на старом коде (в отчёте — пара «было/стало»).

Отдельно: первый вариант BR-067 использовал поток-читатель (`Thread { … }`), но это нарушает
`OneShotThreadWiringTest` (сырые одноразовые потоки запрещены — утечка thread-local соединения);
итог — вывод ffmpeg в временный файл, без потока.

## Партия 3 (P-18a): BR-072, BR-076, BR-077, BR-080, BR-081, BR-082, BR-083, BR-085, BR-086 — закрыто (9 записей)

Все правки — в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt`.

| BR | Было | Стало |
|---|---|---|
| BR-072 | `sourceMarkersList` при неразбираемом JSON перезаписывал `source_markers` и вызывал `saveToDb()` — чтение уничтожало разметку | чистый разбор `parseSourceMarkersOrEmpty`: пустой список + `ERROR` с `songId`/значением; миграция формата — только явная `migrateSourceMarkersFormat()` |
| BR-076 | `sourceMarkersList[voice]` без проверки размера; `variants.minOf` на пустом списке | проверка `voice in indices` и пустых `variants` → пустой результат + `WARN` |
| BR-077 | `size.coerceAtMost(index)` давал `size` (на 1 больше последнего индекса) → `IndexOutOfBoundsException` | `clampTabsVariantIndex` → `coerceIn(0, size-1)`, пустой список → 0 |
| BR-080 | `maxOf`/`[0]` на пустых маркерах → `NoSuchElementException`/`IndexOutOfBounds` | `maxOfOrNull ?: 0`, `firstOrNull() ?: emptyList()`, `firstOrNull() ?: 0L` |
| BR-081 | `stringLad.split("|")`/`toInt()`/`stringNote[string]` без валидации — падение рендера табов | `parseLockedStringLad` (0..5, лад ≥ 0) + фолбэк на автоподбор строя + `WARN` |
| BR-082 | минимум по ВСЕМ голосам: один голос без SYLLABLES/NOTE → `NoSuchElementException` → тихие 5000 мс (сдвиг разметки до 5 с) | минимум по непустым голосам (`minOfOrNull … minOrNull`), пусто — 0, сбой — `WARN` с id |
| BR-083 | геттер `ms` запускал `ffprobe` и писал `UPDATE`; при недоступном файле — subprocess на КАЖДОЕ чтение | геттер — чистое чтение `song_ms`; заполнение — явная `refreshDurationMsFromAudioFile()` |
| BR-085 | `getKeyBpmFromFile`: `Json.decodeFromString` без `try/catch` — битый файл ронял шаг цепочки | через `parseKeyBpmFileOrNull`: `null` → `WARN` + `Pair("", 0)`, шаг деградирует |
| BR-086 | `originalKey`/`newKey`/`capo`/`noteOctave` вставлялись в HTML без экранирования | все интерполируемые значения — `escapeHtml()` (defense-in-depth до «мягкой» починки парсера нот) |

Тесты — `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/model/PkgS322SongTest.kt` (13 тестов).
Поведенческие: BR-072 (неразбираемое значение не переписывается и не открывает соединение —
`FakeKaraokeConnection.getConnectionCalls == 0`), BR-076/080/082 (старая реплика падает,
новая возвращает пустой/нулевой результат), BR-077 (`coerceAtMost` vs `clampTabsVariantIndex`),
BR-081 (`parseLockedStringLad` на невалидных значениях), BR-083 (чтение не ходит в БД),
BR-085 (битый JSON → `null`). Статический guard: BR-086 (все четыре значения проходят
`escapeHtml()`, в HTML не осталось неэкранированного `$originalKey`) — сам HTML-путь
аккордов сейчас недостижим без падения парсера нот (`P-18-01`, S2, вне этого пакета).

## Оговорки

- **`check-subagent-isolation.sh` падает на общих предках веток.** Скрипт считает
  «race condition» любой коммит, который виден более чем в одной PR-ветке, а в этом
  окружении все ветки пакетов созданы от одного `master` (`512203aa`, `b49041ec`,
  `5666129b`, `4a584e87` — общая история). Это не следствие работы в этом worktree:
  главную копию и соседние worktree не трогал, ветка `612-pkg-s3-22` создана от
  `origin/master`. Требует решения владельца/инфраструктуры (проверять только
  коммиты «своей» ветки, а не общую историю).

## Партия 4 (P-18a + P-18b): BR-073, BR-074, BR-075, BR-078, BR-079, BR-084, BR-103, BR-105, BR-106, BR-107 — закрыто (10 записей)

Основные правки — в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt`.
Попутно изменены потребители (этого требовала смена контракта):
`controllers/ApiController.kt` (sheetsage + картинки), `model/Album.kt` (создание
обложки), `Utils.kt` (массовый проход по sheetsage), `HealthReport.kt` (вызов
явной починки корня), `model/SongDTO.kt` / `model/SongDTOdigest.kt`
(`countVoices: Int?`).

| BR | Было | Стало |
|---|---|---|
| BR-073 | геттер `rootFolder` при отсутствующем каталоге перебирал `PROJECT_ROOT_FOLDERS`, подставлял хвост пути и при **первом** совпадении менял `_rootFolder` + делал `UPDATE tbl_songs SET root_folder = ?`; совпадение не проверялось на однозначность (чожой каталог того же автора) | геттер — **чистое чтение** (ни `UPDATE`, ни `getConnection`); починка — явная операция `Song.repairRootFolder(roots)`: кандидат принимается, только если он **единственный** и в нём лежит файл этой песни, иначе fail-closed (`song.root_folder.repair_skipped`); вызывается из `HealthReport.getHealthReportList` |
| BR-074 | геттеры `pictureAuthor`/`pictureAlbum` — read-путь, который писал: INSERT `tbl_pictures` + заливка в MinIO + `saveToDb()`; флаг `picture*Ready` ставился по наличию **локального** файла; `ImageIO.read` без проверки на `null` (NPE) | геттеры — **чистое чтение**; создание — `Song.ensurePictureAuthor()` / `ensurePictureAlbum()` (write-путь: импорт, загрузка обложки, health-repair); флаг готовности выставляется **только после `storageService.fileExists`** (объект реально в хранилище); `ImageIO.read == null` → `WARN` + `null` |
| BR-075 | `player_readiness_flags`: read-modify-write всего JSON из in-memory `fields` без блокировки/версии — два потока (заливка стемов / планировщик автопубликации) затирали ключи друг друга | запись флага — **точечный compare-and-swap**: `UPDATE tbl_songs SET player_readiness_flags = ? WHERE id = ? AND COALESCE(player_readiness_flags,'{}') = ?` (ожидание читается из БД перед записью, до 3 попыток, `Song.READINESS_FLAG_CAS_ATTEMPTS`); diff-обновление песни поле **не пишет** (`getDiff` → `recordDiffRealField = false`), формат хранения прежний (компактный JSON без пробелов — от него зависят проверки `karaoke-web`) |
| BR-078 | парсер `sheetsageInfo`: `substring(1, length-2)`, destructuring `split`, индексы `beattimes[...]`, `toDouble()`/`toLong()` — исключения наружу (500 на диагностическом эндпоинте, обрыв массового прохода) | разбор устойчив: `trim` + `removePrefix("[")`/`removeSuffix("]")`, `toDoubleOrNull`/`toLongOrNull`, `getOrNull` по индексам, `diffBeats` через `coerceIn`; нештатный файл → `WARN` (`song.sheetsage.*`) с `songId`/путём и пустой результат |
| BR-079 | `sheetsageInfo` возвращал `Map<String, Any>`, ключи появлялись только при совпадении регулярки; потребители читали `as String`/`as List<String>` → `null as String` = NPE (элвис `?: ""` применяется к результату приведения) | типизированный `data class SheetsageInfo(key, tempo, chords, beattimes)`; потребители (`ApiController`, `Utils.updateBpmAndKeyLV`) читают поля напрямую; JSON-эндпоинт отдаёт `SheetsageInfo.toMap()` — формат ответа прежний (те же ключи, пустые не попадают) |
| BR-084 | `player_readiness_flags` парсился заново на каждое чтение каждого из ~20 флагов (десятки тысяч лишних разборов на 18k песен) | кэш разбора в объекте (`readinessFlagsCacheOf`), ключ кэша — сама строка JSON, сброс — при записи поля; boolean- и string-флаги читаются из одного кэша |
| BR-103 | `normalizeVoicesBeforeSave()` нормализовала **устаревший** снимок голосов долгоживущего объекта, а diff (против свежей строки) записывал его — голос, добавленный редактором параллельно, исчезал | трёхстороннее слияние **до** вычисления diff: `base` (снимок при загрузке) + `mine` (объект) + `theirs` (свежая строка из БД). Объект не менял голоса → берётся `theirs`; менял → слияние по индексам (изменённые — приоритет, удалённые — не восстанавливаются). Нормализация применяется к **результату слияния**; явная замена (`copyFieldsFromAnother`) слияние отключает |
| BR-105 | фильтр `filter_count_voices` применялся в памяти и опирался на заглушку `"[[]]"` (при `withoutMarkersAndText = true`) → `countVoices == 1` у всех песен: фильтр показывал всё при «1» и пусто при «2+» | `source_markers` грузится принудительно при наличии фильтра; сравнение — с `Song.countVoicesLoaded` (`null` — маркеры не загружены, а не «1»); `SongDTO.countVoices` / `SongDTOdigest.countVoices` стали `Int?` |
| BR-106 | `deleteFromDb(withFiles = true)`: файлы удалялись **до** строки, вокал/аккомпанемент — `deleteOnExit()` (в контейнере файлы жили до перезапуска JVM, список путей рос в памяти); `ps.executeUpdate()` без `use/try` → при `SQLException` строка оставалась, а аудио уже исчезало | сначала `DELETE` (в `use {}`), ошибка → `WARN song.delete.failed` и файлы **не трогаются** (fail-closed); файлы — сразу (`File.delete()`), best-effort с логом; `deleteOnExit` запрещён; `DELETE` без строк → тоже без удаления файлов |
| BR-107 | `createDbInstance` ловил **любую** ошибку INSERT, «лечил» сиквенс и возвращал `null`; `saveToDb` игнорировал `null` → песня с `id = 0` уходила в процессы | ловится только `SQLException`; «починка» сиквенса — только для рассинхрона (`23505` + `last_value` сиквенса позади `max(id)`); остальные ошибки INSERT **пробрасываются**; `null` — только когда строка реально не вставлена (пустые `generatedKeys`); `saveToDb` ловит `SQLException`, пишет `ERROR song.insert.failed` и возвращает `false` |

### Тесты партии 4

`karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/model/PkgS322SongDataTest.kt` (24 теста).

Поведенческие (падали на старом коде):

- **BR-073** — чтение `rootFolder` не открывает соединение (`FakeKaraokeConnection`,
  `name = "LOCAL"`); `repairRootFolder` принимает единственного кандидата (mock JDBC,
  `verify(ps).executeUpdate()`), отклоняет неоднозначного (2 кандидата) и кандидата без
  файла песни.
- **BR-074** — `ImageIO.read` не-картинки → `null` (реплика старого NPE) и
  `ensurePictureAlbum()` возвращает `null` без исключения, флаг готовности не выставлен.
- **BR-075** — `mergeReadinessFlag` сохраняет чужие ключи JSON и формат
  `{"a":true}` (без пробелов); флаг несохранённой песни остаётся в памяти и не ходит в БД.
- **BR-078** — `parseSheetsageBeattimes("")` → пустой список (реплика старого
  `StringIndexOutOfBoundsException`), `"[0.0, мусор, 1.0]"` → числовая часть разобрана;
  полный `sheetsageInfo` на временных файлах `.ly`/`[beattimes].txt` → темп/тональность/
  аккорды/beat-times; битый `.ly` → пустой результат без исключения.
- **BR-079** — `SheetsageInfo` типизирован, `toMap()` пуст для пустого разбора; реплика
  старого `null as String` → NPE; в `ApiController`/`Utils` нет unchecked-кастов.
- **BR-084** — кэш сбрасывается при записи поля (старое значение не «залипает»);
  string-флаги читаются из того же кэша.
- **BR-103** — `mergeVoiceFields`: голос редактора сохраняется; изменённый объектом
  голос приоритетнее версии из БД; удалённый голос не восстанавливается.
- **BR-105** — `countVoicesLoaded`: `null` для незагруженных маркеров (реплика старого
  `countVoices == 1`) и реальное число для загруженных.
- **BR-106** — при `SQLException` на `DELETE` файл песни **остаётся** на диске (раньше
  удалялся первым).
- **BR-107** — `createDbInstance` пробрасывает `SQLException` с исходным текстом
  («not-null»), а не возвращает `null`.

Статические guard'ы (шва нет — нужен живой БД/MinIO, либо проверяется порядок
операций в исходнике):

- **BR-073** — в теле геттера `rootFolder` нет `UPDATE tbl_songs` и `getConnection`.
- **BR-074** — в геттерах `pictureAuthor`/`pictureAlbum` нет `createNewPicture`,
  `saveToDb()` и выставления флага готовности.
- **BR-075** — в исходнике есть CAS-предикат
  `AND COALESCE(player_readiness_flags, '{}') = ?` и
  `RecordDiff("player_readiness_flags", …, false)`.
- **BR-103** — `applyVoiceMerge(savedSong, voiceFieldsBeforeSave)` вызывается **до**
  `getDiff(this, savedSong)` (индексы в исходнике).
- **BR-105** — фильтр использует `song.countVoicesLoaded?.toString()` и
  `args.containsKey("filter_count_voices")` (принудительная загрузка маркеров).
- **BR-106** — в `deleteFromDb` нет `deleteOnExit`, а `DELETE FROM tbl_songs` стоит
  раньше `file.delete()`.
- **BR-107** — `saveToDb` содержит `song.insert.failed` и `val created =` (результат
  INSERT не игнорируется).

Обоснование guard'ов: для BR-074/BR-075/BR-106/BR-107 требуется либо живая БД
(INSERT/DELETE), либо MinIO — в юнит-тестах `karaoke-app` нет живой БД, а поддельное
JDBC-соединение не позволяет отличить SELECT от INSERT по SQL-строке. Поэтому
проверяется либо поведение на mock-JDBC (BR-073/BR-106/BR-107 — где достаточно
одного статмента), либо порядок/состав операций в исходнике.

Два ранее существовавших теста обновлены (поведение изменилось осознанно):

- `SongDtoPremiumPublishStateTest` (BR-087, пакет PKG-S2-17) — после BR-075
  **установка** readiness-флага пишет его в БД атомарно, то есть настройка теста
  обращается к БД. Проверка «`toDTO()` не ходит в БД» сохранена, но теперь измеряет
  только вызов `toDTO()` (счётчик снимается перед ним) — иначе тест ловил бы
  изменения эталона, а не N+1 в `toDTO`.
- `PkgS322ApiControllerTest` (BR-051) — helpers `sheetsageString`/`sheetsageStringList`/
  `sheetsageDoubleList` переведены на типизированный `SheetsageInfo` (BR-079);
  реплика старого поведения (`null as String` → NPE) в тесте сохранена.

---

## Статус пакета

- **Закрыто 40 из 40 записей** (партии 1–4), четыре коммита:
  `ec236744` (BR-034/035/036/037/114), `fcd5d297` (BR-051…BR-070, 16 записей),
  `8281a84c` (BR-072/076/077/080/081/082/083/085/086, 9 записей),
  `462a1235` (BR-073/074/075/078/079/084/103/105/106/107, 10 записей — партия 4).
  История перезаписана 2026-10-25 до push: первый вариант коммитов (`git add -A`) захватил
  осколки shell-редиректов в корне worktree (`argparse`, `collections`, `os`, `re`,
  `subprocess`, `sys` — по ~23 МБ); они удалены из ветки, имена внесены в `.gitignore`.
- **Проверки** (последний прогон, все четыре коммита вместе):
  `:karaoke-app:test :karaoke-web:test` + `ktlintCheck` — BUILD SUCCESSFUL;
  `check-knowledge-structure.sh` — OK (9/9); `lint-knowledge.py --baseline` — OK;
  `check-doc-references.py` — OK; `check-recordhash-consistency.py` — OK;
  `check-no-mp4-mentions.sh` — OK (139 baseline); `check-feature-doc.sh` — все документы
  валидны; `check-subagent-isolation.sh` — OK; `check-ssot-impact.py` — OK.
- **За владельцем**:
  - **BR-114** — выбор механизма аутентификации admin-API и включение
    `karaoke.security.admin-token` в деплое (`X-Admin-Username` не является идентичностью);
  - **BR-073** — починка `root_folder` теперь fail-closed: при неоднозначности (одинаковый
    хвост пути под двумя корнями) каталог **не** меняется. Если в проде такие случаи
    массовые — нужен выбор владельца (например, приоритет корня `done2` > `done1` > `work`);
  - **BR-074** — перенос создания картинки в write-путь означает, что `GET`-запросы больше
    не создают `tbl_pictures`. Для сценариев, где картинка создавалась «сама» при первом
    обращении, нужен явный вызов `ensurePicture*` (импорт/health-repair уже вызывают);
  - **BR-075** — readiness-флаги остаются в одном JSON-поле; атомарность достигнута
    CAS-записью. Разделение флагов по колонкам (идеал карточки аудита) — отдельная
    миграция схемы, решение владельца;
  - **BR-103** — слияние голосов сделано **по индексам**: если объект и редактор добавили
    голос в одну и ту же позицию, приоритет у сохраняющего объекта, и голос редактора
    может быть потерян. Полный merge по идентичности голосов — за владельцем;
  - **BR-107** — `createDbInstance` теперь пробрасывает `SQLException` наружу; вызывающие,
    которые полагались на «исключение проглотится», обязаны обработать ошибку
    (внутри пакета обработан `saveToDb` → `false` + `ERROR`).
- **PR/push/merge/деплой** не выполнялись.
