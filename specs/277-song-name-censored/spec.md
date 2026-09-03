# Feature Specification: Поле `song_name_censored` в `tbl_songs` (заполнение при миграции, CustomFunction-рескан, ручной ввод в SongEdit)

**Feature Branch**: `277-song-name-censored`

**Created**: 2026-08-30

**Status**: Draft

**Input**: User description: "Задача - добавить в таблицу tbl_songs поле song_name_censored чтобы его сразу заполнять и при выборках не делать запросов в таблицу словарей. При миграции локально и на сервере заполнить это поле значением поля song_name. В админке механизмом CustomFunction сделать полное сканирование всей базы и заполенение этого поля уже по правилам словаря. В SongEdit добавить это поле для возможности ручного ввода/изменения."

## Clarifications

### Session 2026-08-30

- Q: Политика ручной правки `song_name_censored` — что делать, если редактор вручную впишет нецензурное/раcцензурированное значение, и оно уйдёт в публичные шаблоны VK/Telegram/News и в публичный API? → A: **Доверие редактору (Option A)** — `song_name_censored` = то, что в БД, без re-censor на лету при формировании DTO/шаблонов. UI в SongEdit дополняется tooltip'ом с явным предупреждением, что ручное значение уйдёт в публикации без фильтрации. Re-censor при формировании DTO/шаблона — ЗАПРЕЩЁН (убивает основную мотивацию фичи — снова запрос к словарю на горячем пути).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Админ разово пересчитывает цензурированные названия по актуальному словарю (Priority: P1)

Администратор на главной странице webvue3 нажимает кнопку CustomFunction
«Пересканировать цензурированные названия песен». Backend запускает фоновое
сканирование всех строк `tbl_songs`: для каждой песни применяются текущие правила
словаря «Censored» (таблица `tbl_dictionaries`, `dict_name='Censored'`) и
полученное значение пишется в колонку `song_name_censored`. По завершении
администратор видит тост-сводку «Обработано N песен за M секунд».

**Why this priority**: Без возможности централизованного реckana правки словаря
«Censored» (добавление нового слова, опечатка, перенос из файла в БД) не будут
отражаться в публичных местах без перезапуска всего karaoke-web. Это блокер для
основной ценности фичи.

**Independent Test**: Запустить CustomFunction с известным количеством песен
(N≥100), дождаться SSE-уведомления, выполнить `SELECT COUNT(*) FROM tbl_songs
WHERE song_name_censored = ''` и убедиться, что счётчик равен 0 (или равен числу
песен с пустым `song_name`); сравнить несколько случайных `song_name_censored`
с результатом `song_name` через существующий `songName.censored(database)` —
значения должны совпасть с точностью до капитализации первой буквы.

**Acceptance Scenarios**:

1. **Given** в `tbl_dictionaries` есть слова «бдсм», «война», **When** админ
   нажимает кнопку CustomFunction, **Then** для всех песен, у которых
   `song_name` содержит эти слова, `song_name_censored` заменяет их на форму с
   символом `█` (как делает существующий `String.censored(database)`).
2. **Given** CustomFunction уже запущен, **When** админ повторно нажимает кнопку
   до завершения предыдущего запуска, **Then** запрос отклоняется с понятным
   сообщением (один процесс за раз) — повторных гонок не возникает.
3. **Given** CustomFunction завершился, **When** админ нажимает кнопку ещё раз,
   **Then** запускается новый проход — операция повторяемая идемпотентна.
4. **Given** в `tbl_songs` есть песни с `song_name=''` (безымянные/артефакты),
   **When** CustomFunction завершается, **Then** для таких строк
   `song_name_censored` остаётся пустой строкой (а не NULL и не
   `censored('')`-артефактом).

---

### User Story 2 - Редактор вручную правит цензурированное название в SongEdit (Priority: P1)

Редактор открывает карточку песни в админке (`SongEdit.vue`), находит новое
поле «Композиция (цензурированная)», вводит/правит значение
(например, чтобы сохранить узнаваемость для конкретной песни после реckana),
нажимает «Сохранить». После сохранения значение отображается при следующем
открытии карточки и приходит в публичный API в `SongDTO.songNameCensored` без
обращения к `tbl_dictionaries`.

**Why this priority**: Редактор должен иметь возможность ручной корректировки —
словарь «Censored» массовый и грубый, для отдельных песен может потребоваться
исключение (например, название содержит нейтральное слово, попавшее в словарь по
другому значению).

**Independent Test**: Открыть в SongEdit любую песню, ввести в поле «Композиция
(цензурированная)» строку «Кастомное Название», сохранить, перезагрузить
карточку, убедиться, что значение сохранилось; запросить публичный API этой
песни — `songNameCensored` совпадает с введённым.

**Acceptance Scenarios**:

1. **Given** в SongEdit у песни отображается поле «Композиция
   (цензурированная)» с авто-заполненным значением из БД, **When** редактор
   вводит свой текст и сохраняет, **Then** в `tbl_songs.song_name_censored`
   записывается новое значение (а не пересчитанное из словаря).
2. **Given** редактор вручную очистил поле (пустая строка) и сохранил, **When**
   карточка открывается повторно, **Then** поле остаётся пустым — НЕ
   авто-заполняется заново из словаря при следующем открытии.
3. **Given** редактор меняет основное название (`Композиция`) и сохраняет,
   **When** карточка переоткрывается, **Then** поле «Композиция
   (цензурированная)» сохраняет прежнее ручное значение — переименование
   основного названия не перетирает ручную правку.
4. **Given** фронт отправляет `POST /api/song/update` с параметром
   `songNameCensored=<X>`, **When** бэкенд обрабатывает запрос, **Then**
   параметр принимается (через `@RequestParam Map<String, String> all`
   и `SongUpdateMapper.fieldLookup`, см. [specs/302-fix-censored-name-loss
   FR-011](../302-fix-censored-name-loss/spec.md#fr-011-основной-подход-c-рефактор-endpoint))
   ИЛИ явный `@RequestParam songNameCensored`. Если ни то, ни другое — баг
   (теряется значение, см. OpenProject issue #52). Защитный чек
   `tools/check-songedit-field-coverage.sh` ловит такое автоматически.

---

### User Story 3 - Чтение цензурированного названия не обращается к `tbl_dictionaries` (Priority: P1)

При выборке песен для публичного API, админских таблиц, шаблонов VK/News/
Telegram значение `songNameCensored` берётся из уже загруженной колонки
`tbl_songs.song_name_censored`, без дополнительного запроса в
`tbl_dictionaries` для построения маски.

**Why this priority**: Это исходная мотивация задачи — убрать N словарных
запросов на каждое чтение песни. Без выполнения этого сценария фича не имеет
смысла.

**Independent Test**: Включить логирование SQL-запросов (или поставить точку
останова в `CensoredWordsDictionary.dict`), выполнить `GET /api/public/songs`
для списка из ≥100 песен и убедиться, что:
  - ровно 1 запрос `SELECT … FROM tbl_dictionaries WHERE dict_name='Censored'`
    на ВЕСЬ список (или 0, если значения закэшированы на стороне JVM);
  - в логах karaoke-web не появляется вызовов `String.censored(database)` при
    формировании DTO списка.

**Acceptance Scenarios**:

1. **Given** список из 100 песен с `song_name_censored` уже заполненным,
   **When** выполняется `GET /api/public/songs`, **Then** публичный ответ
   содержит `songNameCensored` для каждой песни без обращения к
   `tbl_dictionaries` на этапе сборки DTO.
2. **Given** шаблон VK-публикации содержит плейсхолдер `{songNameCensored}`,
   **When** формируется пост, **Then** значение берётся из
   `song.songNameCensored` (поле), а НЕ из `song.songName.censored(database)`
   (метод с запросом в БД).

---

### User Story 4 - Миграция безопасно заполняет колонку без потери данных (Priority: P1)

Применение миграции `42_song_name_censored.sql` на локальной БД и на проде
добавляет колонку `song_name_censored VARCHAR NOT NULL DEFAULT ''` и
заполняет её текущим значением `song_name` для всех существующих строк. После
применения миграции `recordhash` пересчитан для всех строк (LOCAL↔SERVER sync
не ломается).

**Why this priority**: Миграция затрагивает продовую таблицу с 18k+ записей —
любая ошибка в DDL/DML бьёт по публичному сайту и синхронизации. Без
гарантии «применить → ничего не сломалось» фича не выкатывается.

**Independent Test**: На стенде LOCAL: применить миграцию, выполнить `SELECT
COUNT(*) FROM tbl_songs WHERE song_name_censored = '' OR song_name_censored
IS NULL` → должно совпадать с `SELECT COUNT(*) FROM tbl_songs WHERE song_name =
'' OR song_name IS NULL`; выполнить sync LOCAL↔SERVER — должно пройти без
ошибок recordhash.

**Acceptance Scenarios**:

1. **Given** в `tbl_songs` есть строки с `song_name = 'Песня о войне'`, **When**
   применяется миграция, **Then** для этих строк `song_name_censored = 'Песня о
   войне'` (копия без цензурирования на этом шаге — цензурирование делает
   CustomFunction уже после миграции).
2. **Given** миграция применена, **When** сравниваются `recordhash` LOCAL и
   SERVER, **Then** записи с одинаковым `id` имеют одинаковый `recordhash`
   (новая колонка учтена в md5).
3. **Given** прод ещё на старом коде (без `Song.songNameCensored`), **When**
   применяется миграция, **Then** старый код продолжает работать — колонка
   `song_name_censored` не ломает существующие SELECT/INSERT/UPDATE (DEFAULT
   '' держит NOT NULL-инвариант).

---

### Edge Cases

- Что происходит, если `song_name` пустой (`""`)? `song_name_censored` тоже
  должен оставаться пустым (не NULL, не `censored('')`-артефакт с заглавной
  буквой). Гарантируется DEFAULT '' + NOT NULL + проверкой в Save/Scan.
- Что происходит, если `song_name_censored` приходит из БД как `""` или NULL
  при чтении через ResultSet? Поле маппится в `SongField.SONG_NAME_CENSORED`
  как пустая строка — никаких `null` в логике.
- Что происходит, если CustomFunction запущен и процесс падает на середине
  (OOM/сетевая ошибка)? Флаг «уже запущено» сбрасывается в `finally`, чтобы
  повторный запуск был возможен; частично обновлённые строки остаются в
  согласованном состоянии (каждая UPDATE — отдельная транзакция).
- Что происходит, если в `tbl_dictionaries` нет ни одной записи
  `dict_name='Censored'`? CustomFunction трактует словарь как пустой и пишет
  `song_name_censored = song_name` (нет слов — нет цензурирования).
- Что происходит при ручном сохранении пустого `songNameCensored`? Поле
  остаётся пустым (как и просили в US2-AC2), НЕ заполняется автоматически
  из словаря при следующем сохранении.
- Что происходит, если `song_name` изменён через SongEdit (US2-AC3), а потом
  кто-то запускает CustomFunction? По дизайну — CustomFunction ПЕРЕЗАПИСЫВАЕТ
  ВСЕ строки (включая ручные правки), это явное действие админа. Документируем
  как ограничение; если потребуется «мягкий» режим (не трогать строки, где
  `song_name_censored != censored(song_name)`) — это отдельная фича.
- Что происходит, если редактор вручную вписывает нецензурное или
  раcцензурированное слово в `song_name_censored` через SongEdit? По
  политике «доверие редактору» (Clarifications Q1/A) — значение
  записывается как есть и без какой-либо дополнительной фильтрации
  используется в шаблонах публикаций (VK/Telegram/News) и в публичном
  API. Никакого re-censor на лету не происходит. Редактор уведомлён о
  риске через tooltip поля в SongEdit (см. FR-008).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST хранить в `tbl_songs` колонку `song_name_censored`
  типа `VARCHAR` (длина достаточна для названий песен — не менее 255 символов)
  с ограничениями `NOT NULL DEFAULT ''`. Колонка участвует в
  `recordhash`-триггере `update_tbl_songs_recordhash` (md5 включает её
  содержимое).
- **FR-002**: System MUST при применении миграции `42_song_name_censored.sql`
  выполнить `UPDATE public.tbl_songs SET song_name_censored = song_name WHERE
  id > 0` (бэкфилл существующих строк) и затем пересчитать `recordhash` для
  всех строк `tbl_songs` (см. шаблон в `31_entity_description_fields.sql`).
- **FR-003**: System MUST при создании новой записи (`createDbInstance`) и при
  `saveToDb()` гарантировать, что `song_name_censored` либо содержит значение
  из БД/поля, либо авто-заполнено `songName.censored(database)` ТОЛЬКО если
  текущее значение пустое — то есть ручная правка пользователя НЕ
  перезатирается автоматически при сохранении.
- **FR-004**: System MUST при чтении записи (`loadListFromDb` /
  `loadFromDbById`) читать колонку `song_name_censored` из ResultSet в
  `SongField.SONG_NAME_CENSORED`. Если значение в ResultSet равно `null`,
  поле получает пустую строку.
- **FR-005**: System MUST при `getSqlToInsert` (sync=false и sync=true)
  включать пару `("song_name_censored", song.fields[SongField.SONG_NAME_CENSORED]
  ?: "")` в `fieldsValues` — чтобы новая запись сразу попадала в БД с
  заполненной колонкой.
- **FR-005a (добавлено после применения на проде, 2026-08-30)**:
  `Song.getDiff(settA, settB)` MUST явно сравнивать `songNameCensored` и
  добавлять `RecordDiff("song_name_censored", …)` при отличии — иначе
  sync-механизм не включает колонку в `UPDATE` SET, и изменения
  `song_name_censored` (от `rescanAllCensoredNames` или из SongEdit) не
  доходят до REMOTE, несмотря на то что LOCAL/REMOTE `recordhash`
  расходятся. См. bug-fix в `data-model.md` (таблица «Точки правки в
  Song.kt», строка #6).
- **FR-006**: Backend MUST предоставить endpoint `POST
  /api/utils/rescanallcensorednames` (и зеркальный `GET` в `MainController`,
  как у `customfunction`), который:
    1. отклоняет повторный запуск, если уже идёт (флаг в
       `KaraokeProperties`/объекте-синглтоне);
    2. запускает фоновый поток (`thread { … }` по образцу
       `Utils.customFunction`);
    3. в фоне читает ВСЕ `id` из `tbl_songs` через
       `SELECT id FROM tbl_songs` (пакетно, не по одной записи;
       ожидаемый объём — 18k+);
    4. для каждого `id` загружает `Song.loadFromDbById`, вычисляет
       `songName.censored(database)` и сравнивает с текущим
       `songNameCensored` из БД; если отличается — вызывает
       `UPDATE tbl_songs SET song_name_censored = ? WHERE id = ?` через
       `PreparedStatement`;
    5. по завершении отправляет SSE-уведомление (тост) с числом обработанных
       строк и длительностью.
- **FR-007**: Admin UI MUST вызывать `POST /api/utils/rescanallcensorednames`
  через новую кнопку в `HomeView.vue` (по соседству с существующим
  CustomFunction). Кнопка сопровождается подтверждением
  (`CustomConfirm`) с явным описанием «Операция перезапишет ВСЕ
  цензурированные названия, включая ручные правки в SongEdit».
- **FR-008**: `SongEdit.vue` MUST отображать поле «Композиция
  (цензурированная)» под полем «Композиция» с тем же набором кнопок
  (undo/copy/paste) и тем же поведением сохранения через `saveSong` →
  `POST /api/song/update`. Поле сопровождается tooltip'ом
  `title="Ручное значение используется в публикациях (VK/Telegram/News)
  и публичном API БЕЗ повторной фильтрации. Редактируйте на свой страх и
  риск."` (см. Clarifications Session 2026-08-30 Q1/A: политика
  «доверие редактору»).
- **FR-009**: System MUST заменить обращения `song.songName.censored(database)`
  в путях формирования публичных DTO и шаблонов публикаций на
  `song.songNameCensored` (значение из БД). Никаких re-censor на лету
  при формировании DTO/шаблона — это ЗАПРЕЩЕНО (см. Clarifications Q1/A).
  Минимальный набор точек замены:
  `VkTemplateService`, `TelegramTemplateService`, `NewsTemplateService`,
  `UtilsPictures`, `Song.getVKGroupDescription` и связанные методы
  формирования описаний для площадок. Допускается оставить `String.censored`
  как low-level утилиту — она остаётся в коде, просто перестаёт вызываться на
  горячем пути.
- **FR-010**: System MUST регистрировать новый `SongField.SONG_NAME_CENSORED`
  в перечислении `SongField` (KDoc со ссылкой на этот spec) и в
  `fieldSongParams` webvue3-store (если поле должно отображаться в
  дополнительных местах — НЕ обязательно для основной задачи, но регистрация
  делает поле доступным для `getSongFieldParams` в UI).

### Key Entities

- **Колонка `tbl_songs.song_name_censored`**: VARCHAR (длина ≥ 255),
  NOT NULL, DEFAULT ''. Содержит предвычисленное цензурированное название
  песни (по правилам словаря «Censored» из `tbl_dictionaries` либо
  отредактированное вручную). Участвует в recordhash, синхронизируется
  LOCAL↔SERVER (через существующий `tbl_songs_sync` и `update_tbl_songs_*`
  триггер).
- **Запись `SongField.SONG_NAME_CENSORED`**: новое значение enum для хранения
  в `Song.fields` (in-memory маппинг колонка ↔ поле).
- **Endpoint `POST /api/utils/rescanallcensorednames`** + одноимённая
  background-функция в `karaoke-app` (по образцу `Utils.customFunction`):
  идемпотентный повторный запуск, защита от гонок, отчёт в конце.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: После применения миграции `42_song_name_censored.sql` на LOCAL и
  на проде, `SELECT COUNT(*) FROM tbl_songs WHERE song_name_censored = '' OR
  song_name_censored IS NULL` совпадает с `SELECT COUNT(*) FROM tbl_songs
  WHERE song_name = '' OR song_name IS NULL` (для каждой БД отдельно).
  Проверяется одним SQL-запросом после миграции.
- **SC-002**: После выполнения CustomFunction «Пересканировать цензурированные
  названия песен» (US1) на БД с 18k+ строк, число строк, где
  `song_name_censored != song_name` после применения словаря «Censored»,
  равно нулю. Проверяется запросом с подзапросом/regex через текущий
  словарь.
- **SC-003**: При выполнении `GET /api/public/songs` для списка из 100 песен
  в логах backend отсутствуют вызовы `CensoredWordsDictionary.dict` (словарь
  не загружается на этапе построения DTO-списка). До фичи — 1 вызов на
  песню (то есть 100 запросов в `tbl_dictionaries` за одну выборку).
- **SC-004**: Администратор может через SongEdit изменить `song_name_censored`
  произвольной песни; значение сохраняется в БД и приходит в публичный API
  (`GET /api/song/<id>`) без изменений при следующих 10 перезагрузках
  карточки. Проверяется ручным сценарием за <1 минуты.
- **SC-005**: После фичи переименование `song_name` (в SongEdit или через
  saveToDb) НЕ перезатирает ручную правку `song_name_censored` (US2-AC3).
  Проверяется тестом: `assert song.fields[SongField.SONG_NAME_CENSORED]
  == original_value` после `saveToDb()` с изменённым `songName`.

## Assumptions

- Имена/длина колонки: `song_name_censored VARCHAR(255) NOT NULL DEFAULT ''`
  — выбрана та же длина, что и `song_name` (VARCHAR в текущей схеме).
  Если текущая длина `song_name` другая — берём `MAX(LEN(song_name))` по
  прод-данным, но не меньше 255.
- При миграции на шаге бэкфилла `song_name_censored = song_name` (без
  цензурирования). Цензурирование применяется отдельным шагом —
  CustomFunction после деплоя. Это безопаснее, чем пытаться применить
  словарь в момент миграции: миграция остаётся чисто DML/DDL, без
  зависимости от состояния `tbl_dictionaries` на момент применения.
- CustomFunction OVERWRITES все строки, включая ручные правки (см. Edge
  Cases). Если в будущем потребуется «мягкий» режим (не трогать
  отредактированные вручную строки) — это отдельная фича с маркером
  `is_manually_edited` или сравнением текущего значения с
  `censored(song_name)`.
- В `HomeView.vue` кнопка CustomFunction-реckana располагается рядом с
  существующей кнопкой CustomFunction (поиск родителей) — отдельным
  блоком с собственным подтверждением, без изменения поведения
  существующей кнопки.
- В `SongEdit.vue` новое поле «Композиция (цензурированная)» появляется
  СРАЗУ под полем «Композиция» в первом столбце тела, ширина та же, что у
  поля «Композиция» (250).
- `fieldSongParams` в webvue3-store: НЕ добавляем новое поле в массив
  (оно не должно отображаться как отдельная колонка в `SongsTable` — это
  внутреннее/служебное поле, показываемое только в SongEdit). Если
  потребуется отображение в таблице — отдельная задача.
- В `SongDTO` поле `songNameCensored` уже есть (см. `SongDTO.kt:28`). Никаких
  изменений в DTO не требуется — достаточно сделать так, чтобы значение в
  `SongDTO.songNameCensored` бралось из `fields[SongField.SONG_NAME_CENSORED]`,
  а не из `songName.censored(database)` (через
  `Song.songNameCensored` getter). **Политика «доверие редактору»
  (Clarifications Q1/A)**: никакого `song_name_censored.censored(database)`
  на этапе сборки DTO/шаблона публикации.
- SyncRegistry: `tbl_songs` уже зарегистрирован в синхронизации; новая колонка
  включается в `recordhash`-триггер (см. FR-001), отдельных sync-флагов не
  требуется — поведение push/pull/update остаётся прежним, разница лишь в
  составе md5.
- Производительность CustomFunction: для 18k строк ожидаемая длительность —
  единицы минут (логарифм зависит от длины названий и числа слов в словаре).
  Пользователь уведомляется SSE-тостом по завершении (тот же паттерн, что у
  существующего `customFunction`).

## Out of Scope (явно не входит в данный feature)

- Изменение словаря «Censored» (содержимое `tbl_dictionaries`) — это отдельная
  задача редактирования словаря (уже есть UI в `DictionariesView.vue`).
- Отображение `song_name_censored` как отдельной колонки в `SongsTable`
  админки (на текущем этапе — только в SongEdit).
- Публикация `song_name_censored` в публичном API для анонимного
  пользователя (там сейчас используется `songName`/`songNameCensored` уже
  на уровне DTO; решение о видимости — на стороне
  `PublicApiController.toDTO`, не меняется в данной фиче).
- Мягкий режим CustomFunction (не трогать ручные правки) — отдельная фича.
- Перенос содержимого файловых словарей в `tbl_dictionaries` для других
  словарей (только «Censored» уже перенесён в `tbl_dictionaries` ранее,
  см. `17_dictionaries.sql`).