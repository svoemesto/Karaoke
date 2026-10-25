# Component: Song (главная entity)

> **Домен**: [catalog](../domain.md)
> **Компонента**: детальное описание `Song` — главной entity проекта.


## Ответственность | Responsibility


детальное описание `Song` — главной entity проекта.

## Файл

`karaoke-app/.../model/Song.kt` (~1500 строк, включая companion).

## Назначение

**Главная сущность Karaoke** — песня (одна запись в `tbl_songs`).
Всё крутится вокруг Song: рендер видео, MLT-генерация, async-очередь,
two-DB sync, поиск lyrics, авто-публикация.

## Интерфейсы и Контракты | Interfaces and Contracts

### Реализует

- **`KaraokeDbTable`** — reflection-loader (`saveToDb` через diff,
  `loadListFromDb` / `loadFromDbById` через SELECT).
- **`KaraokeStorage`** — `storageBucketName = "karaoke"`,
  `storageFileName = "$author/$year - $album/$fileName"`,
  `storageFileNamePreview = ""`.
- **`Comparable<Song>`** — `compareTo` по `sortString`.
- **`Serializable`**.

### Хранит (контракт данных)

**Метаданные**:

- `id`, `name`, `author`, `year`, `album`.
- `songType` (song/instrumental/poetry).

**Аудио**:

- `sourceFileName`, `vocalsFileName`, `accompanimentFileName`, `mixFileName`.

**Контент**:

- `sourceText` (оригинальный текст).
- `translatedText` (перевод).
- `chordsText` (аккорды).

**Параметры рендера**: **~150 полей** из
[KaraokeProperties](../../processing/components/karaoke-properties.md)
(шрифты, цвета, отступы, тайминги, горизонт, watermark, и т.д.).

**Состояние пайплайна**:

- `idStatus` (0..7) — см. [dictionaries.md#idstatus](dictionaries.md).
- `lastUpdate` — последнее обновление.
- Флаги ремонта: `isAudioAnalizeNeed`, `isMelodyNeed`, и т.п.
- `free` — «всегда бесплатно», см. [dictionaries.md](dictionaries.md).
- `freeAfterOnAir` — «не снимать с эфира» после стандартного окна доступа
  (Pass 369, OpenProject #81). См.
  [dictionaries.md#флаг-freeafteronair--не-снимать-с-эфира-pass-369-openproject-81](dictionaries.md).
- `isExclusive` — «premium-only по бизнес-решению».

**Внешние ссылки**:

- Telegram: `id_telegram_*`.
- VK: `id_vk_*`.
- SponsorBoard: `*`.

**Player readiness** (Pass 341 P0): `stemAccompanimentReady`,
`stemVocalReady`, `pictureAlbumReady`, `pictureAuthorReady` — 4 флага
от `reconcilePlayerReadinessFlags` (см.
[health-report.md](../../health/components/health-report.md#reconcileplayerreadinessflags)).

**Прочее**: `tags: String`, `fields: MutableMap<SongField, String>`
(вычисляемые/производные поля по `SongField`), `firstSongInAlbum: Boolean`,
`sortString` = `"author - year - album - %3d(track)"`.

### JSON-сериализация (Jackson)

`@JsonIgnoreProperties(value = ["database", "storageService",
"pictureAuthor", "pictureAlbum"])` — при сериализации эти поля исключаются
(transient/runtime). **`Kotlin val isX: Boolean` сериализуется как `"x"`**
(без `is`) — см. DEVELOPMENT.md и CLAUDE.md.

### Методы

**Инстанс-методы (создание процессов)**:

- `createProcessDemux2(threadId, prior)` / `createProcessDemux5(...)` —
  `KaraokeProcess.createProcess(song = this, action =
  KaraokeProcessTypes.DEMUCS2 / DEMUCS5, doWait = true)`.
- `createProcessVideo720(songVersion, threadId, prior)` —
  `KaraokeProcessTypes.FF_720_KAR` / `FF_720_LYR`.
- `save()` — алиас `saveToDb()`.
- `saveToDb()`, `saveToDbLocked(): Boolean` — см. «Логика».
- `deleteFromDb(...)`.

Остальные типы процессов (`KEY_BPM_FROM_FILE`, `SYMLINK`, `SMARTCOPY`,
`UPLOAD_TO_LOCAL_STORE`, `UPLOAD_TO_REMOTE_STORE`,
`FF_MP3_ACCOMPANIMENT` / `FF_MP3_VOCAL`, ...) не обёрнуты отдельными
методами — они создаются внутри `Song.createFromPath()` и шагов пайплайна
(см. [async-process-queue.md](../../processing/components/async-process-queue.md)).

**Статические методы (`companion object`)**:

- `createFromPath(...)`, `loadListFromDb(...)`, `loadFromDbById(...)`,
  `loadFromDbByIdForUpdate(...)`, `loadListFromDbByIds(...)`,
  `loadListIds(...)`, `loadListAuthors(...)`, `loadListAlbums(...)`,
  `deleteFromDb(...)`, `listHashes(...)`, `getWhereList(...)` — возвращает `SqlWhere`
  (фрагменты с `?` + параметры), см. [persistence](../../persistence/domain.md#параметризованный-where-sqlwhere).

## Логика и Алгоритмы | Logic and Algorithms

### `saveToDb()` vs `saveToDbLocked()` (Pass 357)

- **`saveToDb()`** — обычный путь сохранения через reflection-diff. При
  `readonly` — выход; при `id == 0L` — INSERT-ветка (там же baseline
  `song_name_censored`, specs/277). Подходит для коротких эндпоинтов
  (объект `song` живёт < 100 мс).
- **`saveToDbLocked()`** — атомарная защита от race condition через
  `SELECT ... FOR NO KEY UPDATE` + UPDATE в одной транзакции. Используется
  для долгих процессов (KEY_BPM_FROM_FILE, DEMUCS2, Sheetsage, поиск
  текстов, импорт файлов из папки), где параллельная ручная правка через
  `SongEdit.vue` может перезатереть данные. Добавлен в
  [Pass 299](../../../../specs/299-song-fields-overwrite-race-condition/spec.md),
  расширен в [Pass 357](../../../../specs/357-folder-import-overwrite/spec.md) —
  30+ мест переведены. Внутри пишет WARN `song.locked_save_diff_overlap` в
  `infra.prod.ping` лог при обнаружении расхождения между in-memory и БД
  (операционная диагностика). При `readonly`, `id == 0L` или отсутствии
  соединения — fallback на `saveToDb()`.

### Хук аудио-потомков (#141, specs/413)

Обе ветки сохранения вызывают `SyncAudioDescendants.onSongSaved(...)` после
успешного UPDATE (`Song.kt:5521`, `5715`). `saveToDbLocked()` подавляет
pre-commit вызов через `suppressTriggers` и повторяет хук после `commit()`,
чтобы воркер синхронизации читал уже зафиксированный статус родителя. См.
[audio-descendant-sync](audio-descendant-sync.md).

### Автопостановка премиум-публикации в очередь

`saveToDb()` при `newsPremiumPublishPending == false` и
`premiumAutoPublishState` пустом или `"RUNNING"` ставит публикацию в очередь,
если одновременно: `idTelegramDemo.isEmpty()`, `idVk.isEmpty()`,
`idStatus == 6L`, `stemAccompanimentReady`, `stemVocalReady`,
`pictureAlbumReady`, `pictureAuthorReady`, `sourceMarkersList.isNotEmpty()` —
тогда `newsPremiumPublishPending = true`, `premiumAutoPublishState =
"RUNNING"`, `premiumAttemptCount = 0`.
Уже завершённая (`COMPLETE`) или окончательно проваленная (`FAILED`)
публикация повторно не переустанавливается.

### Нормализация голосов при сохранении (specs/547)

Голоса песни — два синхронных списка: `source_text` (тексты) и `source_markers`
(маркеры по голосам). Пустой голос (текст пуст И маркеров нет) не является
реальным голосом: он завышает `countVoices` (карточка, фильтр «Количество
голосов», `SongDTO`), добавляет лишний перевод строки в `resultText`, а при
позиционном обходе в рендере «съедает» реальный голос, идущий после него.

Правило (`saveToDb()`, specs/547):

1. **До** вычисления diff вызывается `normalizeVoicesBeforeSave()`: пустые
   голоса с индексом > 0 удаляются, остальные сдвигаются без дыр, первый голос
   сохраняется всегда.
2. Работает на **любом** сохранении песни (голос, карточка, апрув черновика,
   фоновые задания) — это одна общая точка.
3. Парсинг — чистыми функциями из
   `model/VoiceNormalization.kt` (`parseVoiceTexts`, `parseVoiceMarkers`,
   `normalizeVoices`, `canNormalize`, `nonEmptyVoiceIndices`), без геттера
   `sourceMarkersList` (тот при ошибке разбора сам пишет поле и вызывает
   сохранение → рекурсия).
4. Гард: если в `fields` нет `source_text`/`source_markers` (песня загружена с
   `withoutMarkersAndText = true` — списки, фильтры), нормализация не
   выполняется, чтобы не записать пустой текст.
5. При фактическом удалении пересчитываются `result_text`,
   `formatted_text_song/tabs/chords`; факт пишется WARN
   `song.voices.normalized` в лог `infra.song.voices`; нечитаемые данные —
   WARN `song.voices.normalize_skipped`, нормализация пропускается (fail-safe).
6. Результат последнего сохранения доступен в памяти
   (`lastVoiceNormalization`), чтобы API сохранения голоса вернул новый индекс
   голоса после сдвига (FR-010).

Текст и маркеры одного голоса пишутся **одним** сохранением
(`setSourceTextAndMarkers`): иначе после удаления пустого голоса второй вызов
`setSourceText` вернул бы его обратно через ветку «дописать в конец». Апрув
черновика применяет весь набор сразу (`setAllVoices`), чтобы нормализация не
сдвигала голоса внутри цикла.

См. [dictionaries](dictionaries.md#пустой-голос-и-его-удаление-при-сохранении-specs547)
и [specs/547](../../../../specs/547-drop-empty-song-voices/spec.md).

### Лёгкая проекция для состояния/даты публикации (BR-057, PKG-S3-22)

`/api/publications/date` нужна одна дата, а не песня целиком. Для этого в `Song.kt` есть
`Song.loadPublicationDateCandidates(database, inWork)` → `List<PublicationDateCandidate>`
(4 колонки: `id_status`, `free`, `publish_date`, `publish_time`).

- `PublicationDateCandidate.stateAt(now)` считает состояние тем же
  `SongStateResolver.resolve`, что и `Song.state` — контракт один, расхождение
  «дата в UI не совпадает с фильтром» невозможно.
- Предикат `id_status < 6` / `>= 6` — это первый шаг `SongStateResolver.resolve`
  (`idStatus < 6 → IN_WORK`), поэтому отсечение в SQL не меняет результат.
- `Song.dateTimePublish` и проекция используют один разбор
  `parsePublishDateTime(date, time)` (таймзона MSK явно): иначе дата, разобранная
  в UTC, даёт сдвиг на 3 часа при сравнении с «сейчас по Москве».
- **Не заменяйте проекцию на `Song.loadListFromDb(emptyMap())`** ради «единообразия»:
  это полная материализация ~100 полей на строку ради одной даты (было ровно так).

### Геттеры `Song` не пишут и не запускают процессы (BR-072/073/074/080/083, PKG-S3-22)

Правило после аудита #336: **чтение свойства не меняет БД и не запускает внешний CLI**.

- `sourceMarkersList` — чистый разбор (`parseSourceMarkersOrEmpty`): неразбираемое значение
  даёт пустой список и `ERROR` с `songId`/исходной строкой, но НЕ перезаписывает
  `source_markers`. Явная миграция «одиночный формат → multi-voice» — только
  `migrateSourceMarkersFormat()` (вызывается осознанно: health-action/импорт).
  Раньше геттер при неудаче разбора писал `"[]"` и вызывал `saveToDb()` — обычное чтение
  уничтожало разметку песни.
- `ms` — чистое чтение сохранённого `song_ms` (0, если пусто). Заполнение — только явной
  операцией `refreshDurationMsFromAudioFile()` (`ffprobe` + `UPDATE`), а не геттером: раньше
  каждое чтение (включая DTO списка) порождало subprocess, а при недоступном файле —
  subprocess на каждое обращение.
- `songLengthMs`, `getMltProp`, `timesForFlashList[0]`, `getStartSilentOffsetMs` — устойчивы к
  пустым маркерам: `maxOfOrNull ?: 0`, `firstOrNull()`, минимум по НЕПУСТЫМ голосам.
  Пустой список — штатное состояние (`source_markers = "[]"`), а не ошибка.
- `getStringsForAllNotesInSong` — проверяет `voice in sourceMarkersList.indices` и пустые
  варианты аппликатур; `parseLockedStringLad` валидирует `stringLad` (0..5, лад ≥ 0) с
  фолбэком на автоподбор строя; `clampTabsVariantIndex` не выпускает индекс за границу.
- HTML аккордов/табов собирается конкатенацией: **все** интерполируемые значения
  (`originalKey`, `newKey`, `capo`, `noteOctave`) обязаны проходить `escapeHtml()` —
  иначе stored XSS в `v-html` публичной страницы.
- `rootFolder` (BR-073) — **чистое чтение**: геттер не подбирает «похожий» каталог и не
  пишет `UPDATE tbl_songs SET root_folder = ?`. Починка — явная операция
  `Song.repairRootFolder()`: кандидат принимается, только если он **единственный**
  и в нём лежит файл этой песни; иначе — fail-closed (WARN, корень не меняем).
  Вызывается из `HealthReport.getHealthReportList` (health-проверка песни).
- `pictureAuthor` / `pictureAlbum` (BR-074) — **чистое чтение** записи `tbl_pictures`.
  Создание картинки (INSERT + заливка в MinIO) перенесено в явные операции
  `Song.ensurePictureAuthor()` / `Song.ensurePictureAlbum()` (write-путь: импорт,
  загрузка обложки, health-repair). Флаг готовности `pictureAuthorReady` /
  `pictureAlbumReady` выставляется **только после проверки, что объект реально лежит
  в хранилище** (`storageService.fileExists`), а не по факту наличия локального файла.
  `ImageIO.read` может вернуть `null` (файл не картинка) — это `WARN` и `null`, не NPE.

### Удаление песни: сначала строка, потом файлы (BR-106, PKG-S3-22)

`Song.deleteFromDb(withFiles = true)`:

1. `DELETE FROM tbl_songs WHERE id = ?` — **первым**, в `use {}`, ошибка → `WARN`
   `song.delete.failed`, файлы **не трогаются** (fail-closed: «строка есть, звука нет»
   хуже, чем «файл есть, строка есть»).
2. Файлы (`fileAbsolutePath`, вокал, аккомпанемент) — best-effort, **сразу**
   (`File.delete()`), результат пишется в лог. `deleteOnExit()` запрещён: в
   долгоживущем контейнере файлы оставались на диске до перезапуска JVM, а список
   путей рос в памяти.
3. `SSE recordDelete` — только после успешного DELETE.

### Разбор файлов Sheetsage: типизированный и устойчивый (BR-078/BR-079, PKG-S3-22)

- `Song.sheetsageInfo` возвращает **типизированный** `SheetsageInfo`
  (`key`, `tempo`, `chords`, `beattimes`), а не `Map<String, Any>`. Потребители
  (`ApiController`, `Utils.updateBpmAndKeyLV`) читают поля напрямую — `as String`
  на отсутствующем ключе давал NPE (`?: ""` применяется к результату приведения,
  а не к значению в карте) → HTTP 500 вместо пустой строки.
  JSON-эндпоинт `/api/song/sheetsageinfo` отдаёт `SheetsageInfo.toMap()` — формат
  ответа прежний (те же имена ключей, пустые значения не попадают в карту).
- Разбор устойчив: `substring(1, length - 2)` → `trim` + `removePrefix("[")` /
  `removeSuffix("]")`; `toDouble()`/`toLong()` → `toDoubleOrNull()`/`toLongOrNull()`;
  `beattimes[indexBeat]` → `getOrNull`; destructuring `split` → `getOrNull`.
  Нештатный файл — `WARN` (`song.sheetsage.*`) с `songId`/путём и пустой результат,
  а не исключение наружу.

### Флаги готовности пишутся атомарно (BR-075, PKG-S3-22)

Все readiness-флаги (`stemAccompanimentReady`, `pictureAlbumReady`,
`newsAvailableAnnounced`, `premiumAutoPublishState`, `telegramAutoPublishState`,
…) лежат в одном JSON-поле `tbl_songs.player_readiness_flags` (тип `text`).

**Правило записи.** Флаг пишется **точечно и атомарно**:
`UPDATE tbl_songs SET player_readiness_flags = ? WHERE id = ? AND COALESCE(player_readiness_flags, '{}') = ?`
(compare-and-swap по текущему значению колонки, до 3 попыток — `Song.READINESS_FLAG_CAS_ATTEMPTS`).
Значение-ожидание читается из БД непосредственно перед записью; при расхождении
(колонку изменил параллельный поток) читаем заново и повторяем.

**Чего больше нет.** Дифф-обновление (`saveToDb`) поле `player_readiness_flags`
**не пишет** (`getDiff` отдаёт его как диагностическое поле, `recordDiffRealField = false`).
Иначе оно снова записало бы весь устаревший JSON и затерело ключи параллельного потока.

**Формат хранения не меняется** — компактный JSON без пробелов (`{"a":true}`).
От него зависят проверки в `karaoke-web` (`flags.contains("\"pictureAlbumReady\":true")`),
поэтому `jsonb`-нормализация не применяется: слияние выполняется в Kotlin, а в БД
уходит результат с тем же форматом.

**Кэш разбора** (BR-084): `player_readiness_flags` разбирается один раз на объект
(`readinessFlagsCacheOf`, ключ кэша — сама строка JSON), сброс — при записи поля.
Раньше JSON парсился заново на каждое чтение каждого из ~20 флагов.

### Число голосов: «неизвестно» вместо «1» (BR-105, PKG-S3-22)

- `Song.countVoicesLoaded: Int?` — число голосов по **реально загруженным** маркерам;
  `null`, если `source_markers` не загружен (песня из списка с `withoutMarkersAndText = true`).
- `Song.countVoices: Int` — исторический счётчик (используется в рендере, где маркеры
  всегда загружены). Для незагруженных маркеров он опирается на заглушку `"[[]]"` и даёт `1`.
- Фильтр списка «Количество голосов» (`filter_count_voices`) грузит `source_markers`
  принудительно и сравнивает с `countVoicesLoaded` — раньше он считал заглушку, то есть
  показывал ВСЕ песни при значении `1` и пусто при `2+`.
- `SongDTO.countVoices` / `SongDTOdigest.countVoices` стали `Int?` — «неизвестно»
  отдаётся как `null`, а не как выдуманное «1».

### Слияние голосов при сохранении (BR-103, PKG-S3-22)

Песня, загруженная **до** правки редактора, несёт устаревший снимок голосов.
Раньше `saveToDb()` нормализовал этот снимок, а diff (против свежей строки) записывал
его — голос, добавленный редактором, исчезал.

**Правило (трёхстороннее слияние).** В `saveToDb()` голоса сливаются ДО вычисления diff:

| Поле | Смысл |
| --- | --- |
| `base` | снимок голосов на момент загрузки песни (`loadedVoicesSnapshot`) |
| `mine` | текущие значения объекта (до нормализации) |
| `theirs` | свежая строка из БД (`savedSong`) |

- объект голоса **не менял** (`mine == base`) → берётся `theirs` (голос редактора сохранён);
- объект голоса **менял** → слияние по индексам: не тронутые этим объектом голоса
  берутся из `theirs`, изменённые — из `mine`; удалённый объектом голос не восстанавливается.

Нормализация применяется к **результату слияния**, а не к устаревшему снимку.
Явная замена набора голосов (`copyFieldsFromAnother` с `SOURCE_TEXT`/`SOURCE_MARKERS`)
слияние отключает — решение вызывающего приоритетно.

### Цензурирование названий: правила собираются один раз (BR-289, BR-294, #398)

`String.censored(database)` читает словарь «Censored» (`tbl_dictionaries`) и компилирует
шаблоны заново на **каждый** вызов. Пакетная операция `rescanAllCensoredNames`
(`Utils.kt`) вызывала её в цикле по всем песням — N SELECT'ов словаря и N×M компиляций
regex (BR-294). Кроме того, значение словаря подставлялось в шаблон **как есть**:
`х*й` превращалось в regex-шаблон и маскировало «й» в любом слове, а значение с
незакрытой скобкой роняло `toRegex()` (`PatternSyntaxException`) — то есть падало
сохранение песни (BR-289).

Правила:

- значение словаря экранируется `Regex.escape` перед подстановкой в шаблон;
- пустые и заведомо неразбираемые значения отбрасываются с записью в лог
  (категория `infra.song.voices`);
- словарь читается **один раз на пакет**: `CensoredDictionaryRules.load(database)` →
  `buildRules(values)` (шаблоны компилируются один раз) → `text.censored(rules)`;
- одиночная запись песни по-прежнему может звать `text.censored(database)` —
  это один SELECT, а не N.

## Hot paths

- **`POST /api/songs`** — на каждое изменение фильтра/страницы.
- **`POST /api/song`** (`id`) — на каждое открытие карточки.
- **`POST /api/song/update`** — при редактировании метаданных.
- **HealthReport** — 4 `*_ready` флага обновляются
  `reconcilePlayerReadinessFlags`.

## Зависимости | Dependencies

- **HealthReport** ([health-report.md](../../health/components/health-report.md)) —
  `Song.save()` для смены `idStatus`.
- **Async Process Queue** ([async-process-queue.md](../../processing/components/async-process-queue.md)) —
  `KaraokeProcess.createProcess(song=this, ...)`.
- **Two-DB sync** ([two-db-sync.md](../../processing/components/two-db-sync.md)) —
  Song — основная syncable-сущность.
- **MLT** ([mlt-generator.md](../../rendering/components/mlt-generator.md)) —
  входные данные для MLT.
- **KaraokeProperties** ([karaoke-properties.md](../../processing/components/karaoke-properties.md)) —
  150 параметров.

## Известные TODO

- [ ] **Каждое из ~150 полей** — детальное описание (Pass 343+).
- [ ] **Nullable-колонки** в БД vs Kotlin-поля — соответствие.
- [ ] **`fields: Map<SongField, String>`** — все поля где используются.

## Changelog

- **2026-10-25, аудит #336 (OpenProject #394, PKG-S3-22, BR-072/076/077/080/081/082/083/085/086)**:
  зафиксировано правило «геттеры не пишут и не запускают процессы», миграция формата
  `source_markers` вынесена в явную операцию, пустые маркеры больше не роняют длительность/
  рендер, добавлена валидация `stringLad`/индекса строя и экранирование HTML. См.
  `specs/394-pkg-s3-22/report.md`.
- **2026-10-25, аудит #336 (OpenProject #394, PKG-S3-22, BR-057)**: добавлена
  лёгкая проекция `Song.loadPublicationDateCandidates` / `PublicationDateCandidate`
  и общий разбор `parsePublishDateTime` для `Song.dateTimePublish`. См.
  `specs/394-pkg-s3-22/report.md`.
- **Pass 547** (2026-10-20, spec `547-drop-empty-song-voices`): добавлен раздел «Нормализация голосов при сохранении» — пустые голоса (кроме первого) удаляются в `saveToDb()`, новые методы `setSourceTextAndMarkers`/`setAllVoices`, чистая нормализация в `VoiceNormalization.kt`. Автор: agent (Karaoke).
- **Pass 483** (2026-09-27, spec `483-knowledge-domain-catalog`): секции приведены к шаблону компонента. Автор: agent (Karaoke).
- **Pass 384** (2026-09-09): Initial. Автор: agent (Karaoke).
