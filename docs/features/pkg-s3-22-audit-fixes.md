# karaoke-app: записи аудита пакета `PKG-S3-22` (BR-034…BR-114)

> **Status**: active
> **Feature Key**: pkg-s3-22-audit-fixes
> **Last Updated**: 2026-10-25
> **Spec**: [specs/394-pkg-s3-22/report.md](../../specs/394-pkg-s3-22/report.md)
> **Issue**: OpenProject #394 (пакет `PKG-S3-22`, волна E)
> **Branch**: `612-pkg-s3-22`
> Per-feature документ (Constitution § VI FR-009).

## Что делает

Закрывает 40 дефектов S3 в `karaoke-app`, найденных аудитом #336 (порции
P-04, P-08, P-09, P-18): очередь заданий и `mediainfo` (потеря причины
`ERROR`, soft-deleted задания как «вечно активные», `filter_limit`,
валящий запрос в 500, сбой восстановления при старте), `ApiController`
(непроверенные касты, проглоченные `SQLException`, ZIP в heap, отсутствие
таймаута у `ffmpeg`, подмена пути на `/`, контракт 404) и `Song.kt`
(геттеры, которые пишут в БД, unchecked-индексы и касты, XSS в HTML
аккордов, нормализация устаревшего снимка, удаление файлов до строки БД).

## Зачем

Это слой, на котором «всё выглядит работающим, а данные уже неверны»:
задание падает без причины (её нет в БД), удалённое задание навсегда
блокирует переименование песни, `mediainfo` вешает поток очереди, геттер
модели правит строку в БД во время чтения, а HTML для аккордов собирается
интерполяцией пользовательских значений. Ни один из этих дефектов не
виден в UI как ошибка — их видно только по последствиям.

## Как работает

- **BR-034** — `KaraokeProcess.applyFilterLimit`: `take(limit)` вместо
  индексации; нечисловой/отрицательный `filter_limit` — `IllegalArgumentException`,
  `POST /api/processesdigests` отвечает 400, HTML-страница `/processes`
  игнорирует некорректный необязательный фильтр.
- **BR-035** — `MediaInfo.runMediaInfo`: чтение stdout отдельным демон-потоком,
  `awaitProcessExit` с таймаутом (`KARAOKE_MEDIAINFO_TIMEOUT_SECONDS`, 30 с),
  `destroyForcibly()`, проверка кода возврата, `MediaInfoException`, `WARN`
  в лог; `Song.durationInMilliseconds` — `toDoubleOrNull() ?: 0.0`.
- **BR-036** — `deleteDone`/`setWorkingToWaiting` возвращают `Boolean`,
  логируют стек; `KaraokeProcessWorker.start` при сбое отправляет SSE-ошибку;
  причина `ERROR` дописывается в `process_description`
  (`KaraokeProcess.appendErrorReason`).
- **BR-037** — `loadList` добавляет `process_deleted_at IS NULL` по умолчанию
  (escape hatch — `include_deleted`); `KaraokeProcessDTO.processDeletedAt`
  отдаёт признак наружу.
- **BR-051** — `ApiController.sheetsageString/StringList/DoubleList`: безопасное чтение
  `Song.sheetsageInfo` (пустая map → пустые значения, а не NPE → 500).
- **BR-052** — `/songs/changed`, `/processes/changed`: `ERROR` в лог + 5xx вместо
  `printStackTrace` + пустого списка.
- **BR-053/055/059** — batch-эндпоинты: `loadSongsBatch`, `distinct()`, «успех = есть
  поставленные процессы», порог пропуска из `FORCED_ALIGN_MAX_ID_STATUS`.
- **BR-054** — семь chords-эндпоинтов формируют текст версии `SongVersion.CHORDS`.
- **BR-056** — `TextFileHistory`: `ReentrantLock` на путь, атомарная запись, копия
  повреждённого файла вместо «пустой истории».
- **BR-057** — `/publications/date`: `Song.loadPublicationDateCandidates` (4 колонки)
  вместо полной загрузки таблицы.
- **BR-058** — `notifyStatsDirty`: проверка `200..299`, `WARN` с хостом и кодом.
- **BR-061** — `runBackgroundReported`: фоновые потоки ловят `Throwable`, пишут стек и
  отправляют SSE-ошибку.
- **BR-065** — `smartcopyperodbyday`: 400 на невалидную дату/`start > end`/период > 31 дня.
- **BR-066** — `playerfile`: ZIP стримится в ответ, `finish()`/`close()` в `finally`.
- **BR-067** — `convertFlacToMp3`: вывод ffmpeg в файл, таймаут + `destroyForcibly`,
  лок по пути файла.
- **BR-068** — массовый поиск текста: причина неудачи в лог и в SSE-сводку
  (`failedCount`, `failedReason`).
- **BR-069** — `extractlyricsbysearchresultid`: `ResponseEntity` + явный 404.
- **BR-070** — `/api/files`: 404 «путь не найден» / 400 «не каталог» вместо подмены на `/`.
- **BR-072/080/083** — геттеры `Song` больше не пишут в БД и не запускают процессы:
  `sourceMarkersList` (чистый разбор + явная `migrateSourceMarkersFormat`),
  `songLengthMs`/`getMltProp` (пустые маркеры — не ошибка), `ms` (чистое чтение +
  `refreshDurationMsFromAudioFile`).
- **BR-076/077/081** — валидация аппликатур и индексов: `voice in indices`,
  `clampTabsVariantIndex`, `parseLockedStringLad` с фолбэком на автоподбор строя.
- **BR-082** — тишина перед первым слогом считается по непустым голосам.
- **BR-085** — `getKeyBpmFromFile` через `parseKeyBpmFileOrNull` (битый файл — `WARN` + пустой
  результат, шаг цепочки не падает).
- **BR-086** — все интерполируемые значения в HTML аккордов/табов проходят `escapeHtml()`.
- **BR-114** — `AdminApiTokenFilter`: при заданном `karaoke.security.admin-token`
  admin-префиксы требуют заголовок `X-Admin-Token` (иначе 401); без токена
  поведение прежнее, но в лог уходит `WARN`. Механизм аутентификации —
  решение владельца.

## Инварианты

- `KaraokeProcess.loadList` без `include_deleted` не возвращает строки с
  заполненным `process_deleted_at`.
- Невалидный `filter_limit` не приводит к HTTP 500: JSON-эндпоинт — 400,
  HTML-страница — параметр игнорируется.
- `mediainfo` не может висеть дольше `KARAOKE_MEDIAINFO_TIMEOUT_SECONDS`
  (по умолчанию 30 с): по истечении процесс убивается, вызывающий получает
  `MediaInfoException`.
- Сбой `deleteDone`/`setWorkingToWaiting` при старте воркера виден вызывающему
  (`false`) и в SSE; причина `ERROR` сохраняется в `process_description`.
- `AdminApiTokenFilter` при заданном `karaoke.security.admin-token` отвечает 401
  на admin-префиксы без заголовка `X-Admin-Token` и не трогает публичные пути.
- Polling `/api/songs/changed` и `/api/processes/changed` при сбое БД отвечают 5xx,
  а не 200 с пустым списком.
- Batch-эндпоинты постановки в очередь грузят песни пакетно и рапортуют успех
  только при реально поставленных процессах.
- Файл истории фильтров пишется атомарно и только под общим локом; повреждённый
  файл сохраняется копией.
- `/api/song/{id}/playerfile` не собирает архив в памяти (стрим в ответ), а
  ffmpeg-конвертация стема не может висеть бесконечно.
- Геттеры `Song` (`sourceMarkersList`, `ms`, `songLengthMs`, `getMltProp`) не меняют БД
  и не запускают внешние процессы; миграция формата разметки и заполнение `song_ms` —
  только явными операциями.
- Пустые маркеры (`source_markers = "[]"`) — штатное состояние: длительность 0,
  рендер не падает, аппликатуры/индекс строя валидируются.

## Ссылки

- [Карточка аудита P-04](../../specs/336-code-audit/findings/P-04.md)
- [Карточка аудита P-08](../../specs/336-code-audit/findings/P-08.md)
- [Карточка аудита P-09a](../../specs/336-code-audit/findings/P-09a.md)
- [Карточка аудита P-09b](../../specs/336-code-audit/findings/P-09b.md)
- [Карточка аудита P-18a](../../specs/336-code-audit/findings/P-18a.md)
- [Карточка аудита P-18b](../../specs/336-code-audit/findings/P-18b.md)
- [Реестр аудита #336](../../specs/336-code-audit/registry.csv)
- [Отчёт пакета PKG-S3-22](../../specs/394-pkg-s3-22/report.md)
- [SSoT: async-process-queue](../../knowledge/domains/processing/components/async-process-queue.md)
- [SSoT: security-config](../../knowledge/domains/identity/components/security-config.md)

## Известные ловушки

- **`process_deleted_at` не меняет `process_status`.** Админский soft-delete
  ставит только метку времени. Любой код, который считает «активные» задания
  (`hasActiveProcess`, `createProcess`, `HealthReport`), обязан фильтровать по
  метке — иначе удалённое `WAITING`-задание навсегда блокирует переименование
  песни и пересоздание задания. Для инструментов разбора — `include_deleted`.
- **`filter_limit` — необязательный фильтр, а не часть контракта страницы.**
  На JSON-эндпоинте невалидное значение — 400 (ошибка клиента), в HTML-админке —
  «просто не применяем». Не поднимайте `IllegalArgumentException` до 500.
- **`mediainfo` может зависнуть на битом/сетевом файле.** Не возвращайте
  синхронное чтение `inputStream` до EOF: EOF наступает только после выхода
  процесса. Таймаут и `destroyForcibly()` — в `runMediaInfo`, не в вызывающем.
- **Причина `ERROR` живёт в `process_description`, а не в отдельной колонке.**
  Хвост ` | ERROR: …` дописывается идемпотентно; при правке формата описания
  (UI, sync) не потеряйте маркер — по нему оператор и поиск отличают причину
  от исходного текста шага.
- **`AdminApiTokenFilter` не аутентифицирует субъекта.** `X-Admin-Username`
  по-прежнему приходит от клиента; токен — только «свой/чужой» на периметре.
  Включение `authenticated()` без механизма входа оставит админку без доступа.
