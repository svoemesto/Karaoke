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
аккордов, нормализация устаревшего снимка, удаление файлов до строки БД,
атомарность флагов готовности, типизация разбора Sheetsage, счётчик голосов по
заглушке, проглатывание ошибок `INSERT`).

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
- **BR-073** — `Song.rootFolder` — чистое чтение: геттер не подбирает «похожий»
  каталог и не пишет `UPDATE tbl_songs SET root_folder`. Починка — явная операция
  `Song.repairRootFolder()`: кандидат принимается, только если он **единственный**
  и в нём лежит файл этой песни (иначе — fail-closed, WARN). Вызывается из
  `HealthReport.getHealthReportList`.
- **BR-074** — `Song.pictureAuthor` / `pictureAlbum` — чистое чтение `tbl_pictures`.
  Создание картинки (INSERT + заливка в MinIO) — `Song.ensurePictureAuthor()` /
  `ensurePictureAlbum()` (write-путь: импорт, загрузка обложки, health-repair).
  Флаг готовности выставляется только после проверки объекта в хранилище;
  `ImageIO.read == null` — WARN и `null`, а не NPE.
- **BR-075** — readiness-флаги (`player_readiness_flags`) пишутся **точечным
  compare-and-swap** (`UPDATE … WHERE id = ? AND player_readiness_flags = ?`),
  а не записью всего JSON: параллельные потоки больше не затирают ключи друг друга.
  Дифф-обновление песни это поле не пишет. Формат хранения прежний — компактный
  JSON без пробелов.
- **BR-078** — разбор файлов Sheetsage устойчив: `trim`/`removePrefix` вместо
  `substring(1, length-2)`, `toDoubleOrNull`/`toLongOrNull`, `getOrNull` по индексам.
  Нештатный файл — `WARN` с `songId`/путём и пустой результат, а не 500.
- **BR-079** — `Song.sheetsageInfo` возвращает типизированный `SheetsageInfo`
  (`key`, `tempo`, `chords`, `beattimes`) вместо `Map<String, Any>`; потребители
  (`ApiController`, `Utils`) читают поля напрямую. JSON-эндпоинт
  `/api/song/sheetsageinfo` отдаёт `SheetsageInfo.toMap()` — формат прежний.
- **BR-084** — разбор `player_readiness_flags` кэшируется в объекте (ключ кэша —
  сама строка JSON), сброс — при записи поля: вместо ~20 разборов на песню
  (на каждый флаг) — один.
- **BR-103** — слияние голосов при сохранении: `saveToDb()` сливает «устаревший
  снимок фонового объекта» со «свежей строкой из БД» ДО вычисления diff
  (трёхсторонний diff по снимку загрузки). Голос, добавленный редактором, больше
  не исчезает при фоновом сохранении; нормализация применяется к результату слияния.
- **BR-105** — фильтр списка «Количество голосов» считается по реальным маркерам:
  `source_markers` грузится принудительно, сравнение идёт с `Song.countVoicesLoaded`
  (`null` — маркеры не загружены, а не «1»). `SongDTO.countVoices` стал `Int?`.
- **BR-106** — `Song.deleteFromDb()`: сначала `DELETE` строки (в `use {}`, ошибка →
  WARN, файлы **не** трогаются), потом файлы — сразу (`File.delete()`), без
  `deleteOnExit()`. Ошибка удаления файла пишется в лог.
- **BR-107** — `Song.createDbInstance()` ловит только `SQLException` и «лечит» только
  рассинхрон сиквенса; остальные ошибки INSERT пробрасываются наружу, `null`
  возвращается только когда строка действительно не вставлена. `saveToDb()` реагирует
  на неудачный INSERT (`false` + `ERROR`), а не возвращает «успех».

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
- Чтение `Song.rootFolder`, `Song.pictureAuthor`, `Song.pictureAlbum` не меняет БД,
  хранилище и флаги готовности.
- `root_folder` меняется только через `Song.repairRootFolder()` и только при
  единственном кандидате, содержащем файл песни.
- Readiness-флаг пишется точечно (CAS); `player_readiness_flags` не попадает в
  дифф-обновление песни — формат хранения (`{"a":true}`, без пробелов) неизменен.
- Число голосов в DTO — `Int?`: для песен без загруженных маркеров — `null`, не «1».
- Удаление песни: при ошибке `DELETE` файлы остаются на диске; `deleteOnExit` не
  используется.
- Ошибка `INSERT` песни не превращается в «успех»: `saveToDb()` возвращает `false`,
  `song.id` остаётся `0`, вызывающий не ставит задания для несохранённой песни.

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
- [SSoT: song-entity](../../knowledge/domains/catalog/components/song-entity.md)
- [SSoT: dictionaries](../../knowledge/domains/catalog/components/dictionaries.md)
- [SSoT: pictures](../../knowledge/domains/catalog/components/pictures.md)
- [SSoT: health-report](../../knowledge/domains/health/components/health-report.md)

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
- **`player_readiness_flags` — одно JSON-поле на все флаги.** Поэтому запись флага
  обязана быть **точечной** (CAS по текущему значению колонки). Не пишите это поле
  общим `UPDATE ... SET player_readiness_flags = ?` из diff-обновления песни: вы
  затрёте ключи, записанные параллельным потоком. И не меняйте формат хранения на
  `jsonb`-нормализацию — от компактного JSON без пробелов зависят проверки
  в `karaoke-web` (`"pictureAlbumReady":true`).
- **Заглушка `"[[]]"` — это «маркеры не загружены», а не «один голос».** Для списков
  и фильтров маркеры не грузятся, поэтому `countVoices` опирается на заглушку.
  Считать число голосов нужно через `countVoicesLoaded` (`null` — неизвестно), а
  фильтр «Количество голосов» обязан грузить `source_markers` принудительно.
- **Починка `root_folder` неоднозначна по построению.** Один и тот же хвост пути
  («Автор/Год - Альбом») существует под несколькими корнями (`PROJECT_ROOT_FOLDERS`).
  Поэтому кандидат принимается только при единственном совпадении И наличии файла
  песни в каталоге; иначе песня остаётся с прежним корнем, а в лог уходит
  `song.root_folder.repair_skipped`.
- **Геттер картинки песни больше не создаёт запись `tbl_pictures`.** Если нужен
  create-путь (импорт, загрузка обложки, health-repair) — вызывайте
  `Song.ensurePictureAuthor()` / `ensurePictureAlbum()`. Иначе картинка не появится
  в каталоге: GET-запросы только читают.
- **Флаг готовности картинки ≠ наличие локального файла.** `pictureAuthorReady` /
  `pictureAlbumReady` выставляются только после `storageService.fileExists` — то
  есть когда объект реально доступен плееру.
- **`saveToDb()` сливает голоса с БД, а не затирает их.** Фоновый объект песни,
  загруженный до правки редактора, не должен записать свой устаревший набор
  голосов. Если вы пишете голоса целиком (например, копируете из другой песни) —
  это считается явной заменой, и слияние отключается.
- **Ошибка `INSERT` песни — это исключение, а не «пустой результат».**
  `createDbInstance` бросает `SQLException` наружу (кроме рассинхрона сиквенса),
  `saveToDb` ловит его, пишет `ERROR` и возвращает `false`. Проверяйте результат
  `saveToDb()`, если песня должна попасть в БД.
