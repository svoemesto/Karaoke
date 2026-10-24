# PKG-S2-10 — отчёт по пакету аудита #336 (OpenProject #373)

> **Ветка**: `593-pkg-s2-10` (worktree `waveD-s2-10`, от `origin/master` `f5da6c18`)
> **Дата**: 2026-10-24
> **Пакет**: `PKG-S2-10` — karaoke-app: DATA, ERR, PERF, RACE, SEC — 15 BR
> **Источник правды**: карточки `specs/336-code-audit/findings/` (P-04, P-09a, P-09b, P-18a, P-18b), реестр `specs/336-code-audit/registry.csv`
> **Трекер**: OpenProject #373 (claim: `ai-agent`)

## Сводка

Исправлены все 15 записей пакета. Изменены 7 production-файлов и добавлен
один файл политик (`SafetyPolicies.kt`); добавлены 2 регрессионных тест-класса
(21 тест), обновлены 2 документа `knowledge/` и создан per-feature документ
`docs/features/pkg-s2-10-data-integrity.md`.

Коммиты:

| Коммит | Что |
|---|---|
| `5a7be278` | BR-030/031/032/033 — очередь: прогресс, claim, цепочка, args |
| `18a178c7` | BR-048/049/050/063/064 — ApiController: whitelist, SSRF, rootFolder, флаги публикации, пауза |
| HEAD ветки | BR-071/101/102/104/108/109 — Song/Utils + тесты + docs/report |

## Таблица BR

| BR | Было | Стало |
|---|---|---|
| BR-030 | прогресс писался полным `save()` из цикла `doStart` — возвращал строку в `WORKING` после `DONE` | `saveProgress()`: узкий `UPDATE` percentage-колонок с `WHERE process_status='WORKING'`; SSE сохранён |
| BR-031 | `createProcess` удалял всё, кроме `WORKING`; поток писал `WORKING` безусловно | атомарный `claimForWorking()` (`WHERE id=? AND process_status='WAITING'`, ровно 1 строка); active = single-flight |
| BR-032 | шаги цепочки независимы, `DONE` первого ставил песне `DONE` | `process_chain_id` + `process_order=0..N-1`; шаг N+1 после N; `ERROR` останавливает цепочку; статус песни — по завершении |
| BR-033 | `args[0]` на `[]` ронял очередь, `args=[[]]` вечно блокировал лейн | `ProcessArgsPolicy`: «непустой список непустых списков»; отклонение на create, `ERROR` с причиной в воркере |
| BR-048 | `fields.split(";")` → любой `SongField`, включая `ID` → UPDATE чужой песни | `SongCopyFieldsPolicy`: whitelist контентных полей, `400` на всё остальное, `id==idAnother` — `400` |
| BR-049 | прокси тянул произвольный URL (SSRF) и любой объём | `AlbumCoverUrlPolicy`: только http(s), порты 80/443, публичные адреса, лимит 10 МиБ, проверка сигнатуры |
| BR-050 | `rootFolder` из формы писался как есть → запись/чтение в произвольном каталоге | `LibraryRootPolicy`: read-only, только текущее значение под корнем библиотеки, realpath/симлинки; иначе `400` |
| BR-063 | после `sendVideo` полный `saveToDb()` устаревшего объекта затирал `id_telegram_demo`/`id_vk` | `SongPublicationFlagsPolicy`: свежая строка + перенос только флагов, затем `saveToDbLocked()` |
| BR-064 | `Thread.sleep` на каждой итерации, включая пропущенные песни → 504 на десятках песен | `LyricsSearchPacing`: пауза только перед фактическим `getLyricsSearch`, от последнего запроса |
| BR-071 | `musicNote!!`/`musicChord!!` → NPE на `N.C.`, `C/H`, опечатке; `getTransposingChord` — `note!!` | неизвестный аккорд рендерится как есть; `getTransposingChord` возвращает исходную запись |
| BR-101 | `loadListFromDb` глотал `SQLException` → `null` = «песни нет», правка молча терялась | `throwOnError`; `loadFromDbById` пробрасывает; в `saveToDb` пропавшая строка — WARN + `false` |
| BR-102 | `saveToDb` глотал ошибку `executeUpdate`; `saveToDbLocked` коммитил и возвращал `true` | `saveToDb(): Boolean`, ошибка биндинга/UPDATE отменяет запись; locked коммитит только при `true`, иначе rollback |
| BR-104 | `toDTO()` делал `SELECT` по `tbl_albums` на каждую песню (N+1) | `toDTO()` без БД; `toDTOsWithAlbumNames()` — один пакетный запрос на список |
| BR-108 | INSERT без `album_id`, `root_id`, `free`, `free_after_on_air`, `audio_*`, `player_readiness_flags` | перечисленные колонки добавлены; `exclusive` — за владельцем (нет поля в сущности) |
| BR-109 | `scPath`/`scRenameTemplate` из запроса → `createDirectories` + `chmod 777/666` + `cp` куда угодно | `SmartCopyPathPolicy`: абсолютный нормализованный путь, системные/исходные корни запрещены, шаблон санитайзится, права `755/644` |

## Подробно по BR

### BR-030 (P-04-03, RACE) — `KaraokeProcessWorker.kt:1140–1143`

- **Что не так**: один объект `KaraokeProcess` сохранялся из двух потоков: цикл
  `doStart` каждые ~10 мс писал прогресс, поток задания — терминальный статус.
  `save()` — полный `UPDATE` без compare-and-set, поэтому запоздавший
  `UPDATE` из `doStart` возвращал строку в `WORKING` после `DONE`.
- **Как исправлено**: `KaraokeProcess.saveProgress()` обновляет только
  `process_percentage*`, `process_time_*` и только при
  `process_status='WORKING'`; в цикле `doStart` все прогресс-записи заменены
  на него. SSE-уведомление о прогрессе сохранено (внутри `saveProgress`).
- **Тесты**: `PkgS210SqlShapeTest.BR-030 …` (SQL не содержит
  `process_status`/`process_end` в SET, есть гард `WORKING`, 0 строк вне
  `WORKING`).

### BR-031 (P-04-04, RACE) — `KaraokeProcess.kt:1027–1037`

- **Что не так**: «SELECT → DELETE всех не-`WORKING` → INSERT» без транзакции:
  второй вызов удалял только что выбранную, но ещё не помеченную `WORKING`
  строку; у цепочки удалялись `WAITING`-шаги 2..N.
- **Как исправлено**: поток задания стартует с `claimForWorking()` —
  `UPDATE … WHERE id=? AND process_status='WAITING'` с проверкой «1 строка»;
  без claim работа не начинается. `createProcess()` считает активным
  `WAITING`/`WORKING`/`CREATING` (single-flight, `return 0`) и удаляет только
  терминальные.
- **Тесты**: `PkgS210SqlShapeTest.BR-031 …` (второй claim → 0 строк/false;
  SQL содержит оба условия WHERE).

### BR-032 (P-04-05, DATA) — `KaraokeProcess.kt:1941–1971`

- **Что не так**: `separate()` не проставлял `processChainId`, INSERT не
  писал `process_chain_id`, шаги жили независимо; `DONE` первого шага сразу
  выставлял песне `DONE`; после `ERROR` шаг N+1 всё равно запускался.
- **Как исправлено**: `separate()` нумерует шаги (`process_order`) и копирует
  chain id; головной шаг вставляется первым, его id становится
  `process_chain_id` остальных; воркер выбирает `WAITING` по
  `(priority, order, id)`, поэтому порядок соблюдается. При `ERROR` шага
  `stopChainSiblingsOnError()` переводит оставшиеся `WAITING`/`CREATING` в
  `ERROR`; агрегированный статус песни обновляется только когда незавершённых
  шагов нет (нетерминальные статусы видны сразу).
- **Тесты**: `PkgS210SqlShapeTest.BR-032 separate …` (order 0,1,2; общий
  chain id; шаги с `tail`), `BR-032 INSERT …` (SQL содержит
  `process_chain_id` и значение).
- **Ограничение**: шаги, созданные до релиза, остаются без chain id (видны
  как top-level в админке). Backfill не делался — см. «За владельцем».

### BR-033 (P-04-06, ERR) — `KaraokeProcessWorker.kt:1104–1125`

- **Что не так**: `karaokeProcess.args[0]` без проверки внешнего списка:
  `[]` → `IndexOutOfBoundsException` в главном цикле (5 retry →
  `isWork=false`, вставала вся очередь); `[[]]` → задание навсегда `WAITING`
  и блокировало лейн.
- **Как исправлено**: `ProcessArgsPolicy.validationError()` — «непустой
  список непустых списков». `createProcess()` отклоняет невалидное задание
  до вставки; `doStart` переводит уже существующую невалидную строку в
  `ERROR` с причиной и продолжает цикл; `save()` больше не разыменовывает
  `args[0][0]` без проверки.
- **Тесты**: `PkgS210PoliciesTest.BR-033 …` (пустой список/пустой шаг
  отклоняются, валидный принимается; реплика старого `args[0]`).
- **Ограничение**: полный путь `doStart` не покрыт юнит-тестом (нужен живой
  цикл воркера/БД); покрыта политика, которую он вызывает.

### BR-048 (P-09-01, DATA) — `ApiController.kt:612`

- **Что не так**: `fields.split(";").map { SongField.valueOf(it) }` принимал
  любой `SongField`, включая `ID`; `copyFieldsFromAnother` записывал чужой id
  в `fields[ID]`, и следующий `saveToDbLocked()` делал
  `UPDATE tbl_songs … WHERE id=<idAnother>` — затирал другую песню.
- **Как исправлено**: `SongCopyFieldsPolicy` — allowlist контентных полей
  (`SOURCE_TEXT`, `RESULT_TEXT`, `SOURCE_MARKERS`, `FORMATTED_TEXT_*`,
  `DIFFBEATS`, `DESCRIPTION`, `SHORT_DESCRIPTION`, `WARNING`). Неизвестные и
  неразрешённые имена → `400`; `id == idAnother` → `400`.
- **Тесты**: `PkgS210PoliciesTest.BR-048 …`.

### BR-049 (P-09-02, SEC) — `ApiController.kt:3173`

- **Что не так**: `/api/song/albumcoverproxy` делал серверный GET по
  произвольному `url` и возвращал тело (SSRF + неограниченная память).
- **Как исправлено**: `AlbumCoverUrlPolicy` — только `http`/`https`, порт
  80/443, запрет userinfo, `localhost`/`.local`/`.internal`, IP-литералы и
  резолв проверяются на loopback/private/link-local/multicast/CGNAT;
  `downloadImageBytes` читает поток с лимитом 10 МиБ; эндпоинт отдаёт только
  распознанную сигнатуру (JPEG/PNG/GIF/WebP), иначе 415.
- **Тесты**: `PkgS210PoliciesTest.BR-049 SSRF …` (9 отказов, 2 разрешения,
  DNS-rebinding), `BR-049 сигнатура …`.
- **Ограничение**: доменный allowlist (Яндекс.Музыка / SearXNG) не введён —
  SearXNG отдаёт произвольные внешние хосты, жёсткий список сломал бы фичу.
  См. «За владельцем».

### BR-050 (P-09-03, SEC) — `ApiController.kt:2952`

- **Что не так**: `rootFolder` из `@RequestParam all: Map<String,String>`
  применялся как direct setter без проверки; далее
  `saveAlbumCover` писал `${rootFolder}/LogoAlbum.png` + `chmod 666`, а
  `file*` читали `<rootFolder>/<fileName>.*` — произвольный каталог ФС.
- **Как исправлено**: `LibraryRootPolicy` — путь обязан быть абсолютным,
  нормализованным, без `..`, под одним из `PROJECT_ROOT_FOLDERS`, существующим
  каталогом, realpath под realpath корня и не симлинком; принимается только
  неизменённое текущее значение. Иначе `SongUpdateMapper` бросает `400`.
- **Тесты**: `PkgS210PoliciesTest.BR-050 …`.
- **Ограничение**: проверка выполняется на входе (`SongUpdateMapper`), не в
  каждом файловом sink. Legacy-значения `rootFolder` вне `PROJECT_ROOT_FOLDERS`
  теперь получат `400` при сохранении — см. «За владельцем».

### BR-063 (P-09-16, DATA) — `ApiController.kt:7042–7108`

- **Что не так**: премиум-эндпоинты сохраняли песню полным `saveToDb()` после
  сетевого вызова (10–30 с); `getDiff` устаревшего объекта включал чужие
  изменения (например, `id_telegram_demo` от air-публикации) и затирал их.
- **Как исправлено**: `savePublicationFlagsAfterNetwork()` перечитывает
  строку, `SongPublicationFlagsPolicy.copyFlags()` переносит только флаги
  публикации, запись — `saveToDbLocked()`. Оба эндпоинта (Telegram и VK),
  включая pre-flight save, переведены на locked-сохранение.
- **Тесты**: `PkgS210PoliciesTest.BR-063 …` (реплика старого diff с
  `id_telegram_demo`; новый diff содержит только флаги).
- **Ограничение**: endpoint-путь не покрыт интеграционным тестом (нужен
  Spring/БД); покрыта политика, которую он использует.

### BR-064 (P-09-17, PERF) — `ApiController.kt:4810–4899`

- **Что не так**: `Thread.sleep(timeout * 1000L)` стоял вне ветки фактического
  поиска и выполнялся на каждой итерации, включая песни с текстом; 100 песен
  ≈ 16 минут синхронного HTTP → 504 nginx, поток занят часами.
- **Как исправлено**: `LyricsSearchPacing.shouldRequest()` + пауза
  `delayBeforeRequest()` от времени последнего фактического запроса; sleep
  выполняется только перед `getLyricsSearch`. Замер `minIntervalMs` и SSE-итог
  сохранены.
- **Тесты**: `PkgS210PoliciesTest.BR-064 …` (реплика старого sleep на
  пропущенной итерации; новая политика — 0 паузы).

### BR-071 (P-18-01, ERR) — `Song.kt:4255–4260`

- **Что не так**: `getFormattedChords()` принудительно разыменовывал
  `musicNote!!`/`musicChord!!`; любой аккорд вне `MusicChord` (`N.C.`, `C/H`,
  `Cadd11`, опечатка) давал NPE → 500 при сохранении голоса/апруве.
  `getTransposingChord` так же падал на `note!!`.
- **Как исправлено**: неизвестный аккорд рендерится исходной строкой без
  транспозиции; `getTransposingChord` при неразбираемой ноте/аккорде
  возвращает исходную запись.
- **Тесты**: `PkgS210PoliciesTest.BR-071 …` (NPE-реплика, `N.C.`/`C/H`,
  `getFormattedChords` с маркерами CHORD+ENDOFLINE содержит `N.C.`).

### BR-101 (P-18-18, ERR) — `Song.kt:5410–5411`

- **Что не так**: `loadListFromDb` ловил `SQLException` и возвращал пустой
  список, поэтому `loadFromDbById` возвращал `null` и при сбое чтения; `saveToDb`
  выходил по пустому diff — правка не сохранялась, эндпоинт отвечал успехом.
- **Как исправлено**: `loadListFromDb(..., throwOnError)`; `loadFromDbById` и
  `loadFromDbByIdForUpdate` передают `true`. В `saveToDb` `savedSong == null`
  → WARN `song.save_skipped` и `false`.
- **Тесты**: `PkgS210SqlShapeTest.BR-101 …` (SQLException пробрасывается, а не
  превращается в `null`).

### BR-102 (P-18-19, DATA) — `Song.kt:5652–5658`

- **Что не так**: `saveToDb` глотал ошибку `executeUpdate` (и ошибки биндинга
  по каждому параметру), продолжал рассылку SSE/хуки; `saveToDbLocked`
  безусловно коммитил и возвращал `true`.
- **Как исправлено**: `saveToDb(): Boolean`; ошибка биндинга отменяет UPDATE
  целиком, `executeUpdate` с 0 строк → `false`; `saveToDbLocked` коммитит
  только при `true`, иначе `rollback()` и `false`.
- **Тесты**: `PkgS210SqlShapeTest.BR-102 …` (пропавшая строка → `false`;
  locked → rollback + `false`).

### BR-104 (P-18-21, PERF) — `Song.kt:8974–8977`

- **Что не так**: `toDTO()` на каждую песню с `albumId` делал отдельный
  `SELECT` по `tbl_albums`; списки грузят все строки → N+1 на таблице песен.
- **Как исправлено**: `toDTO()` не обращается к БД (`albumName = ""`);
  `Song.toDTOsWithAlbumNames()` одним запросом `Album.getAlbumsByIds` заполняет
  имена. Эндпоинты `/api/songs` и `/api/songsdigests` переведены на него.
- **Тесты**: `PkgS210SqlShapeTest.BR-104 …` (`getConnectionCalls == 0` для
  `toDTO`; ровно один `createStatement` на список).

### BR-108 (P-18-25, DATA) — `Song.kt:6345–6449`

- **Что не так**: фиксированный список колонок `getSqlToInsert` отстал от
  схемы: при INSERT терялись `root_id`, `free`, `free_after_on_air`,
  `audio_parent_id`, `audio_similarity_percent`, `audio_delta_ms`,
  `audio_compare_history`, `album_id`, `player_readiness_flags`; `recordhash`
  вставленной строки не сходился с исходной.
- **Как исправлено**: перечисленные колонки добавлены; `album_id` пишется
  только при непустом значении (чтобы `null` не стал строкой `"null"`).
- **Тесты**: `PkgS210PoliciesTest.BR-108 …` (все колонки и значения в SQL).
- **За владельцем**: `exclusive` не переносится — в сущности `Song` нет поля
  (колонка читается только сырым SQL в фильтрах).

### BR-109 (P-18-26, SEC) — `Song.kt:5966–6005`

- **Что не так**: `doSmartCopy` принимал `scPath`/`scRenameTemplate` из запроса
  и делал `Files.createDirectories`, `chmod 777`, `cp`, `chmod 666` без
  проверки пути/имени — запись в произвольный каталог, `..` в шаблоне уводил
  файл за пределы каталога.
- **Как исправлено**: `SmartCopyPathPolicy.validateExportRoot()` (абсолютный
  нормализованный путь, запрет системных каталогов и `PROJECT_ROOT_FOLDERS`,
  опциональный allowlist через `-Dkaraoke.smartcopy.allowed-roots` /
  `SMARTCOPY_ALLOWED_ROOTS`), `sanitizeDestinationTemplate/FileName` (без
  `..` и разделителей), права `755` и `644`. Эндпоинты возвращают `400` до
  постановки задач; `doSmartCopy` повторяет проверку (defense in depth).
- **Тесты**: `PkgS210PoliciesTest.BR-109 …` (запреты пути/шаблона, allowlist,
  валидный шаблон UI).

## Тесты «до/после»

### До (старый код)

Так как исправления вводят новые production-швы (`saveProgress`,
`claimForWorking`, `toDTOsWithAlbumNames`, …), на старом коде регрессионные
тесты не компилируются. Зафиксировано прогоном на ревертнутых production-файлах:

```text
$ git stash push -- <7 production-файлов>
$ GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew ... :karaoke-app:compileTestKotlin
exception: .../PkgS210SqlShapeTest.kt:74:33: error: unresolved reference 'saveProgress'.
exception: .../PkgS210SqlShapeTest.kt:90:33: error: unresolved reference 'saveProgress'.
exception: .../PkgS210SqlShapeTest.kt:107:28: error: unresolved reference 'claimForWorking'.
exception: .../PkgS210SqlShapeTest.kt:115:28: error: unresolved reference 'claimForWorking'.
exception: .../PkgS210SqlShapeTest.kt:198:9: error: none of the following candidates is applicable:
exception: .../PkgS210SqlShapeTest.kt:245:14: error: unresolved reference 'toDTOsWithAlbumNames'.
BUILD FAILED
```

Дополнительно в каждом тесте есть **реплика старого поведения**, которая
показывает дефект на данных/строке SQL:

- BR-030: старый SQL пишет `process_status`/`process_end` в SET прогресса.
- BR-031: старый `WHERE id = ?` не содержит `process_status` → оба «получают»
  строку.
- BR-032: старые шаги имеют одинаковый `order = -1`.
- BR-033: `emptyList[0]` → `IndexOutOfBoundsException`.
- BR-048: `SongField.valueOf("ID")` возвращает `ID`.
- BR-049: `URI.create("http://127.0.0.1:8899/...")` валиден без политики.
- BR-050: сеттер `rootFolder` принимает `/etc`.
- BR-063: diff устаревшего объекта содержит `id_telegram_demo`.
- BR-064: старый sleep выполняется на пропущенной итерации (10 000 мс).
- BR-071: `note!!` на `null` → NPE.
- BR-101: старый catch превращал `SQLException` в `null` (в новом коде —
  `assertThrows<SQLException>`).
- BR-102: старый fallback `saveToDb(); true` возвращал успех (новый —
  `false` + rollback).
- BR-104: старый `toDTO()` ходил в БД (`getConnectionCalls`).
- BR-108: старый INSERT не содержал перечисленных колонок.
- BR-109: сырой `Path(export, "../etc").normalize()` выходит за `export`.

### После (новый код)

```text
$ JAVA_TOOL_OPTIONS=-Djava.awt.headless=true GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle \
  ./gradlew --max-workers=2 -Dorg.gradle.jvmargs="-Xmx1536m -XX:MaxMetaspaceSize=512m" \
  :karaoke-app:test --tests 'com.svoemesto.karaokeapp.audit.*'
BUILD SUCCESSFUL
```

21 тест: `PkgS210PoliciesTest` (16) и `PkgS210SqlShapeTest` (5).

## Проверки (CI-гейты)

| Проверка | Результат |
|---|---|
| `:karaoke-app:test` (полный прогон) | BUILD SUCCESSFUL (511 тестов; запускать с `JAVA_TOOL_OPTIONS=-Djava.awt.headless=true` — см. примечание) |
| `:karaoke-web:test` | BUILD SUCCESSFUL |
| `ktlintCheck` | BUILD SUCCESSFUL |
| `bash tools/check-knowledge-structure.sh` | OK: 9/9 checks passed |
| `python3 tools/lint-knowledge.py --baseline config/knowledge/baseline-knowledge-lint.txt` | All documentation passes |
| `python3 tools/check-doc-references.py` | OK после создания этого отчёта |
| `bash tools/check-no-mp4-mentions.sh` | OK: all mentions in baseline |
| `bash tools/check-feature-doc.sh docs/features/*.md` | Все документы валидны |
| `bash tools/check-subagent-isolation.sh` | Все commits уникальны |

**Примечание по окружению**: `VkPreviewWarmupClientTest` использует AWT
(`BufferedImage.createGraphics`) и падает с `AWTError: Can't connect to X11`
без `JAVA_TOOL_OPTIONS=-Djava.awt.headless=true`; это существующая
особенность машины, не связанная с пакетом (на ревертнутом коде поведение то
же). Полный прогон выполнен с этим флагом.

## За владельцем

1. **BR-049 — строгий доменный allowlist прокси обложек.** Сейчас разрешены
   любые публичные http(s)-адреса (SSRF закрыт), потому что SearXNG отдаёт
   произвольные внешние хосты и жёсткий список сломал бы поиск обложек.
   Вариант: проксировать картинки через SearXNG (`/image_proxy`) и оставить
   allowlist «Яндекс.Музыка + SearXNG». Нужно решение владельца о смене
   контракта.
2. **BR-108 — колонка `exclusive`.** В сущности `Song` нет поля, поэтому
   `getSqlToInsert()` её не переносит (значение уходит в `DEFAULT false`).
   Чтобы переносить, нужно добавить поле в `Song` и элемент `SongField`
   (это меняет модель/сериализацию — решение владельца).
3. **BR-032 — backfill `process_chain_id` для существующих цепочек.** Новые
   цепочки получают id, старые шаги остаются top-level в админке. Нужен
   одноразовый backfill или решение, что это допустимо (операция с БД —
   только по слову владельца).
4. **BR-050 — `PROJECT_ROOT_FOLDERS` и legacy `rootFolder`.** Если в проде
   есть песни с `root_folder` вне `/sm-karaoke/{work,done1,done2}`, их
   сохранение теперь вернёт `400` (fail-closed). Нужно подтвердить список
   корней или расширить его конфигом.
5. **BR-031 — семантика «повторный createProcess не пересоздаёт WAITING».**
   Это осознанное изменение прежнего поведения (раньше удаляло и создавало
   заново). Если какой-то сценарий на это опирался — нужно решение владельца.

## Риски и миграции

- Схема БД не менялась: используются существующие `process_chain_id`,
  `process_order`, `process_deleted_at`. Миграций нет.
- `process_chain_id` пишется отдельным `UPDATE` (`saveChainId`), потому что
  колонка намеренно не входит в полный `save()` (FR-019).
- `saveToDb(): Boolean` — source-compatible для существующих вызовов
  (результат можно игнорировать), но новые hot-path обязаны проверять `false`.
- Прогресс в UI продолжает обновляться через SSE из `saveProgress`.
- BR-049 резолвит DNS имени в потоке запроса (как и раньше сетевой вызов);
  для прокси это допустимо, но при медленном DNS добавляется задержка.

## Хэши

- База: `origin/master` `f5da6c18`.
- `5a7be278` — process (BR-030..033).
- `18a178c7` — api (BR-048/049/050/063/064).
- HEAD ветки (`git log --oneline -1`) — song/docs (BR-071/101/102/104/108/109 + тесты + knowledge + docs + этот отчёт).
