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
  `knowledge/system/utilities.md`

Работа ведётся партиями по ~5–15 записей с коммитом после каждой партии
(пакет большой: 40 записей). Ниже — состояние на текущий момент.

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

## Оговорки

- **`check-subagent-isolation.sh` падает на общих предках веток.** Скрипт считает
  «race condition» любой коммит, который виден более чем в одной PR-ветке, а в этом
  окружении все ветки пакетов созданы от одного `master` (`512203aa`, `b49041ec`,
  `5666129b`, `4a584e87` — общая история). Это не следствие работы в этом worktree:
  главную копию и соседние worktree не трогал, ветка `612-pkg-s3-22` создана от
  `origin/master`. Требует решения владельца/инфраструктуры (проверять только
  коммиты «своей» ветки, а не общую историю).

## Остаток

Партии 3–4 (Song.kt: BR-072…BR-086, BR-103, BR-105, BR-106, BR-107) — в работе.
