# Feature Specification: Не сохраняется цензурированное имя песни в SongEdit

**Feature Branch**: `302-fix-censored-name-loss`
**Created**: 2026-09-03
**Status**: Draft
**Input**: User description (OpenProject issue #52): "Админка, SongEdit.vue. Ручное изменение поля цензурированного имени не сохраняет значение в базу данных. С веба на бэк в пэйлоаде значение отправляется, на бэке теряется. Найти и исправить ошибку. Найти другие места (если есть) с аналогичной ошибкой. Возможно, "теряются" и другие поля."

## Clarifications

### Session 2026-09-03

- Q: Объём защитного чека — только FR-005/006 (SongEdit ↔ /song/update) или сразу FR-007/008 (общий аудит всех пар UI↔backend)? → A: **B (оба сразу)** — FR-005/006 + FR-007/008 в одной спеке, общий чек обязателен (не may-defer). US-3 остаётся в спеке как обязательный, не выносится.
- Q: Архитектурный подход — статический чек vs рефактор endpoint vs гибрид? → A: **C (гибрид)** — рефактор `songs2Update` на централизованный приём всех параметров (`@RequestParam Map<String, String> all` или `@ModelAttribute`) + статический чек как страховка от регрессий при будущих правках. Корневая причина бага устраняется рефактором, чек страхует.
- Q: OpenProject workflow для issue #52 — специфицировать явно как часть DoD? → A: **A (явно как DoD)** — `claim-issue 52` на старте (assignee=ai-agent, status=In progress), `add-comment` с отчётом о выполненной работе + `mark-review` при завершении PR, `close-issue` после одобрения пользователем. Это часть operational readiness для любой задачи из OpenProject (см. AGENTS.md Pass 295 + specs/295-jira-local-integration FR-007/008).
- Q: Whitelist по умолчанию для `tools/check-songedit-field-coverage.sh` — пустой или предзаполненный? → A: **B (предзаполненный)** — whitelist создаётся с нестандартными setter'ами, которые чек не может распознать автоматически (path-params, специальная обработка, не-String типы). Конкретный список фиксируется при реализации; ожидаемый объём ≤10 полей.
- Q: Тестирование SC-001 «10 тестовых правок» — где и как? → A: **B (ручная проверка на LOCAL-БД с откатом)** — 10 ручных правок через SongEdit в локальном окружении (LOCAL-БД, контейнеры Karaoke), после проверки выполнить `tools/cleanup-test-songs.sql` (или ручной UPDATE) для возврата 10 песен в исходное состояние. Автоматизация через Playwright — overengineering для smoke-проверки одной регрессии; ручная проверка достаточна для SC-001 + SC-002.

## Root cause (pre-implementation analysis)

При ручном анализе подтверждена причина бага в эндпоинте
`POST /api/song/update` (`karaoke-app/.../controllers/ApiController.kt`,
метод `songs2Update`, строки 2928–3180):

1. Фронт (`webvue3/src/components/Songs/edit/SongEdit.vue`, строка 139)
   использует `v-model="song.songNameCensored"` для поля «Censored».
2. Vuex-getter `getSongDiff` (`webvue3/src/components/Songs/store.js`,
   строка 388) включает изменённый ключ `songNameCensored` в `diff`.
3. `executeSave` (SongEdit.vue, строка 5641) кладёт его в `params`
   и отправляет `POST /api/song/update?songNameCensored=...`.
4. **На бэкенде метод `songs2Update` НЕ объявляет `@RequestParam songNameCensored`**
   — Spring Web молча отбрасывает неизвестный query-параметр (исключения
   не выкидывает). В результате `songValue.fields[SongField.NAME_CENSORED]`
   не обновляется, `getDiff()` не видит изменения, и `saveToDb()` формирует
   UPDATE SET без `song_name_censored`. Пользователь видит «сохранено», но
   значение в БД не меняется.

Причина появления бага: при реализации specs/277-song-name-censored
(2026-08-30, FR-005: «ручной ввод в SongEdit») на UI было добавлено поле
`v-model="song.songNameCensored"` и интегрировано в общий diff/payload
механизм, но **соответствующий `@RequestParam` + setter в `songs2Update`
не были добавлены**. Это типовой «разрыв» между двумя половинами
полного-stack фичи.

Аудит остальных полей SongEdit (95 `v-model` полей против списка
`@RequestParam` в `songs2Update`) показал, что **`songNameCensored` —
единственное потерянное поле** среди редактируемых во вкладке «Основное».
Остальные 94 поля покрыты корректно (см. SC-003).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Редактор правит цензурированное название в SongEdit и оно сохраняется (Priority: P1)

Редактор открывает карточку песни в админке (`SongEdit.vue`), находит
поле «Censored», вводит новое значение (например, чтобы сохранить
узнаваемость после реckana по словарю), ждёт автосохранения
(≤2 секунды) или нажимает кнопку «Сохранить». После сохранения
значение отображается при следующем открытии карточки, приходит в
публичный API (`SongDTO.songNameCensored`), и используется в шаблонах
VK/Telegram/News без обращения к `tbl_dictionaries` (см. политику
«доверие редактору» из specs/277 Clarification Q1/A).

**Why this priority**: Это полный обвал основного сценария specs/277
(US-2 «Редактор вручную правит цензурированное название»). Без
исправления ручной ввод цензурированного названия невозможен вовсе,
а поле в SongEdit — это «обманка» для редактора.

**Independent Test**: Открыть в SongEdit любую песню с непустым
`song_name_censored`, изменить поле «Censored» на «Тест 12345»,
дождаться тоста «Изменения сохранены», обновить страницу — значение
остаётся «Тест 12345». Проверить через БД:
`SELECT song_name_censored FROM tbl_songs WHERE id = <songId>` —
значение равно «Тест 12345». Проверить публичный API —
`songNameCensored` = «Тест 12345».

**Acceptance Scenarios**:

1. **Given** редактор открыл карточку песни с пустым `song_name_censored`,
   **When** он вводит «Кастомное Название» в поле «Censored» и ждёт
   автосохранения, **Then** в БД `song_name_censored = 'Кастомное Название'`,
   при перезагрузке карточки поле отображает «Кастомное Название»,
   публичный API возвращает `songNameCensored: 'Кастомное Название'`.
2. **Given** редактор ввёл значение в «Censored», **When** он нажимает
   кнопку «Сохранить», **Then** POST `/api/song/update` уходит с
   параметром `songNameCensored` в payload, бэкенд возвращает HTTP 200,
   бэкенд-логи `[karaoke-app] UPDATE tbl_songs SET song_name_censored = ? WHERE id = ?`
   содержит новое значение.
3. **Given** у песни уже было ручное значение «Кастом 1», **When**
   редактор правит его на «Кастом 2» и сохраняет, **Then** в БД лежит
   «Кастом 2» (а не «Кастом 1», не пустая строка, не NULL).
4. **Given** у песни есть ручное значение, **When** редактор нажимает
   кнопку Undo рядом с полем, **Then** значение возвращается к
   snapshotSong.songNameCensored и сохраняется.

---

### User Story 2 - Защита от повторения бага в будущих полях SongEdit (Priority: P2)

Разработчик, добавляющий новое редактируемое поле в SongEdit
(например, новое `song.fooBar` через `v-model`), получает автоматическое
предупреждение на CI / pre-commit, если в `songs2Update` нет
соответствующего `@RequestParam` и setter'а. Это предотвращает
повторение того же класса багов.

**Why this priority**: Без такой защиты этот же баг вернётся при
следующем добавлении поля. Цена исправления — однократная, цена
пропуска — повторный разрыв UI↔backend в любой будущей фиче.

**Independent Test**: Добавить в SongEdit новое поле `v-model="song.testProbeField"`,
запустить тест/чек → он должен упасть с указанием на отсутствующий
`@RequestParam testProbeField` в songs2Update. Удалить тестовое поле,
чек проходит зелёным.

**Acceptance Scenarios**:

1. **Given** в `webvue3/src/components/Songs/edit/SongEdit.vue`
   используется `v-model="song.<key>"`, **When** запускается
   новый чек `tools/check-songedit-field-coverage.sh`,
   **Then** для каждого такого ключа чек проверяет, что в
   `ApiController.songs2Update` есть `@RequestParam` с тем же camelCase-именем
   и `songValue.fields[...] = it` или `songValue.<key> = it` setter.
2. **Given** чек запущен, **When** все поля покрыты, **Then**
   exit code 0 и краткий отчёт «OK: 95/95 полей покрыты».
3. **Given** чек запущен, **When** хотя бы одно поле не покрыто,
   **Then** exit code 1 с указанием конкретного поля
   (`MISSING: songNameCensored`).
4. **Given** чек зелёный, **When** разработчик случайно удалил setter
   в `songs2Update` для существующего поля, **Then** чек падает
   на CI и PR блокируется до исправления.

---

### User Story 3 - Аудит других эндпоинтов на аналогичные потерянные поля (Priority: P3)

Провести аудит других эндпоинтов проекта (album/update, author/update,
dictionary/update, …) на предмет того же класса багов: UI-компонент
отправляет поле в payload, но backend молча его отбрасывает.

**Why this priority**: Этот класс багов (UI ↔ backend mismatch) — типовой
для проекта. Без аудита мы фиксируем только один инцидент, оставляя
другие потенциальные мины. Не критично для MVP (ручной аудит сейчас),
но обязательно для архитектурной гигиены.

**Independent Test**: Запустить `tools/check-endpoint-field-coverage.sh`
(новый чек) на всех известных парах «Vue-компонент редактирования +
backend-эндпоинт update». Все пары должны быть «зелёными» или явно
помечены как «известное исключение с обоснованием» в whitelist.

**Acceptance Scenarios**:

1. **Given** список пар UI↔backend известен (`tools/endpoint-pairs.yml`
   или авто-обнаружение по grep), **When** запускается аудит-чек,
   **Then** для каждой пары проверяется, что все `v-model` ключи
   из UI-компонента имеют соответствующий `@RequestParam` в эндпоинте.
2. **Given** в каком-то эндпоинте есть потерянное поле,
   **When** аудит-чек запущен, **Then** он падает с указанием пары
   «SongEdit.vue → /api/song/update» + «MISSING: songNameCensored»
   (этот баг, как уже исправленный, должен быть исключён через whitelist).
3. **Given** в whitelist добавлено известное исключение с обоснованием,
   **When** чек запущен, **Then** исключение выводится в отчёте как
   «SKIPPED (with reason)» и не считается ошибкой.

---

### Edge Cases

- **Кастомное значение содержит спец-символы (`<`, `>`, `&`, `'`, `"`)?**
  → Backend применяет значение как есть через `String?.let` setter;
  `saveToDb()` использует prepared statement (`?`), XSS-инъекция
  исключена. UI отображает корректно благодаря Vue v-model экранированию.
- **Кастомное значение превышает лимит колонки `tbl_songs.song_name_censored`?**
  → PostgreSQL уронит UPDATE с `value too long for type character varying(512)`;
  `saveToDb()` ловит exception → клиент получает HTTP 500 → тост
  «Ошибка автосохранения». Для защиты UI должен ограничивать длину
  на клиенте (maxlength="512"), но это улучшение, не блокер.
- **Пользователь ввёл только пробелы?** → Значение сохраняется как есть
  (trim — это политика, не входит в скоуп бага; если редактор хочет
  «стереть» строку, он стирает её руками). Backend не валидирует
  whitespace-only.
- **Одновременное редактирование одного `song_name_censored` двумя редакторами?**
  → Standard race: последний коммит выигрывает. L2-кеш
  `authorsTilesCache` не затрагивается (поле не влияет на счётчики).
  `recordhash` триггер обновляется → sync LOCAL↔SERVER подхватит.
- **Сохранение через старый API-клиент (например, скрипт без поля)?**
  → `?songNameCensored=` отсутствует в query → setter не вызывается →
  старое значение в БД сохраняется (нет wipe). Поведение корректное.
- **SongEdit открыт, sync с SERVER перезаписал локальное значение?**
  → Snapshot обновляется через WebSocket → UI перезагружается →
  ручное значение пользователя может быть затёрто серверным.
  Существующее поведение, не регрессия.
- **Поле добавлено в SongEdit, но не в SongDTO?**
  → Out of scope: проверка покрытия UI↔backend, не UI↔DTO.
  Покрытие DTO — отдельный чек (или unit-test).
- **Автосохранение запускается до завершения предыдущего?**
  → См. `isSaving` флаг в SongEdit.vue (строка 5633). Не наш случай.
- **После рефактора (FR-011) клиент шлёт параметр нестрокового типа
  с невалидным значением (например, `idStatus=abc`)?** → Маппер
  (FR-012) ДОЛЖЕН вернуть HTTP 400 с сообщением «Invalid value for
  param idStatus: 'abc' is not a number». Текущее поведение
  Spring `@RequestParam Long?` — тоже бросает 400, поведение должно
  остаться эквивалентным.
- **После рефактора (FR-011) клиент шлёт неизвестный параметр
  (не существующий в Song/SongField)?** → Маппер ДОЛЖЕН либо
  игнорировать его с WARN-логом (рекомендуется, не ломает старых
  клиентов), либо бросать HTTP 400 «Unknown param X» (более строго,
  ловит опечатки на фронте). Решение фиксируется при реализации.
- **После рефактора (FR-011) несколько значений для одного параметра
  (`?tag=a&tag=b`)?** → Song.tag — строка, не список. Поведение Spring
  по умолчанию для `Map<String, String>` — берёт первое значение.
  Поведение для `Map<String, String[]>` — собирает массив. Маппер
  ДОЛЖЕН явно зафиксировать, какой вариант используется (рекомендуется
  первый — соответствует текущему single-value поведению).
- **После рефактора (FR-011) существующий baseline-механизм в Song.kt:5364
  (`song_name_censored` автозаполняется через `songName.censored(database)`)
  продолжает срабатывать?** → Да, FR-014 требует сохранить baseline
  в новом маппере. Это не часть бага (baseline срабатывает только при
  пустом значении, после фикса фронт может явно перезаписать baseline
  пустой строкой).
- **При рефакторе (FR-011) используется B2 (`@ModelAttribute DTO`) —
  как поведёт себя фронт, который шлёт параметры в другом регистре
  (например, `songname` вместо `songName`)?** → `@ModelAttribute` чувствителен
  к регистру (Spring binding по имени). Текущее поведение
  `@RequestParam` тоже. Поведение должно остаться эквивалентным.
- **При рефакторе (FR-011) используется B1 (`Map<String, String>`) —
  параметры приходят как строки. Как передать null?** → null в query
  невозможен; отсутствие параметра = ключ отсутствует в Map. Маппер
  ДОЛЖЕН интерпретировать «ключ отсутствует» как «не менять поле»
  (текущее поведение `@RequestParam(required=false)`).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: В `ApiController.songs2Update` (`POST /api/song/update`)
  ДОЛЖЕН быть добавлен `@RequestParam(required = false) songNameCensored: String?`,
  и в теле метода (после загрузки Song из БД) — строка
  `songNameCensored?.let { songValue.fields[SongField.NAME_CENSORED] = it }`,
  по аналогии с уже существующими setter'ами для `songName`, `author`,
  `album` и т.п.
- **FR-001.alt (подход C, основной)**: **Альтернативная (предпочтительная)
  реализация** — рефактор `songs2Update` (см. FR-011) делает FR-001
  избыточным. Если FR-011 реализован, FR-001 считается покрытым
  автоматически (новый маппер принимает `songNameCensored` без явного
  `@RequestParam`).
- **FR-002**: Поведение setter'а: пустая строка (`""`) ДОЛЖНА
  интерпретироваться как «очистить поле» (записать `""` в БД), а не
  как «не менять». Это соответствует поведению других string-полей
  (`songName`, `author`). Backend НЕ ДОЛЖЕН применять baseline-автоцензурирование
  (`songName.censored(database)`), если пользователь явно задал значение
  (даже пустое) — политика «доверие редактору» (specs/277 Clarification
  Q1/A остаётся в силе).
- **FR-003**: После исправления ручное значение `song_name_censored` ДОЛЖНО
  сохраняться в БД через `saveToDb()` (UPDATE `tbl_songs SET
  song_name_censored = ? WHERE id = ?`) и попадать в `recordhash`-триггер
  для последующей sync LOCAL↔SERVER.
- **FR-004**: При пустом `songNameCensored` И пустом `songName`
  baseline-логика (`Song.kt:5364`) ДОЛЖНА срабатывать как раньше —
  это не наш случай, поведение существующее.
- **FR-005**: Новый инструмент `tools/check-songedit-field-coverage.sh`
  ДОЛЖЕН сравнивать список `v-model="song.<key>"` из
  `webvue3/src/components/Songs/edit/SongEdit.vue` со списком
  `@RequestParam` + setter'ов в `ApiController.songs2Update` и падать
  с exit code 1 при любом несоответствии. Скрипт ДОЛЖЕН поддерживать
  whitelist через `tools/check-songedit-field-coverage.whitelist.yml`
  (формат: `{ "fieldName": "reason" }`). Whitelist ДОЛЖЕН быть
  предзаполнен при создании (Session 2026-09-03 Q4→B) нестандартными
  setter'ами, которые чек не может распознать автоматически:
  - `id` — path-param (идентификатор песни, не редактируется через v-model).
  - `albumId` — обрабатывается через `albumId?.let { rawAlbumId -> ... }`
    со специальной логикой cross-author check.
  - `songType` — enum, setter через `SongType.entries.firstOrNull { ... }`.
  - `free`, `idStatus`, `rate`, `rootId`, `audioParentId`,
    `audioSimilarityPercent`, `audioDeltaMs`, `idTariff`, `diffBeats` —
    не-String типы (`Boolean?`, `Int?`, `Long?`), не подходят под
    общий шаблон `fields[SongField.X] = it`.
  - `fileName` — специальная обработка через sanitize+collision+
    active-process проверки.
  - `tags` — обрабатывается через прямой `songValue.tags = it`, не через `fields[...]`.
  - `rootFolder` — обрабатывается через прямой `songValue.rootFolder = it`.
  - `description`/`shortDescription`/`warning` — обрабатываются через
    прямые `songValue.description = it`, не через `fields[...]`.
  Ожидаемый объём whitelist ≤10 полей (Session 2026-09-03 Q4→B).
- **FR-006**: Чек `tools/check-songedit-field-coverage.sh` ДОЛЖЕН
  запускаться в pre-commit (`.pre-commit-config.yaml`) и в CI
  (`.github/workflows/*.yml`). При падении — коммит/PR блокируется.
- **FR-007**: Аудит-чек `tools/check-endpoint-field-coverage.sh` ДОЛЖЕН
  принимать список пар UI↔backend через `tools/endpoint-pairs.yml`
  и для каждой пары запускать ту же логику, что и `check-songedit-field-coverage.sh`.
  Если пара только одна (SongEdit ↔ /song/update) — чек выводит
  «INFO: только одна пара покрыта, остальные — out of scope MVP».
  **NB (Session 2026-09-03 Q1→B)**: FR-007 обязателен в этой спеке,
  не may-defer.
- **FR-008**: Чек `check-endpoint-field-coverage.sh` ДОЛЖЕН поддерживать
  глобальный whitelist `tools/check-endpoint-field-coverage.whitelist.yml`
  для полей, которые намеренно не редактируются (например, id,
  computed). Формат: `{ "ComponentName/endpointName/fieldName": "reason" }`.
- **FR-009**: Документация: создать/обновить `docs/features/song-edit-and-censored.md`
  (или новый per-feature документ, если такого ещё нет), описать:
  - Контракт UI↔backend для SongEdit: каждое `v-model="song.X"`
    ОБЯЗАНО иметь соответствующий `@RequestParam X` + setter в
    `songs2Update`. Изменения в одном без другого — это баг.
  - Ссылка на чек `tools/check-songedit-field-coverage.sh`.
  - Краткое описание фикса `songNameCensored` (FR-001).
- **FR-010**: Существующий per-feature документ specs/277-song-name-censored
  (`spec.md`, секция US-2 «Редактор вручную правит цензурированное
  название») ДОЛЖЕН получить обновлённый Acceptance Scenario, явно
  указывающий на требование FR-001 (backend должен принимать параметр)
  и ссылку на эту спеку.
- **FR-011** (основной подход C, рефактор endpoint): `ApiController.songs2Update`
  (`POST /api/song/update`) ДОЛЖЕН быть отрефакторен для приёма всех
  входящих параметров через единый централизованный механизм, чтобы
  баг «фронт шлёт X, бэкенд не принимает» стал невозможен в принципе.
  Конкретная реализация (на выбор исполнителя, см. `tools/spike-map-param-songs2Update.md`
  если будет spike-документ):
  - **Вариант B1**: `@RequestParam Map<String, String> all` +
    централизованный mapper (рефлексия по `Song.fields` Map или
    конвенция `camelCase param → SONG_FIELD_KEY`).
  - **Вариант B2**: `@ModelAttribute SongUpdateRequestDto dto` +
    DTO с полями для всех редактируемых полей + централизованный mapper
    DTO → Song.
  - **Любой другой вариант**, который (а) принимает ВСЕ присылаемые
    параметры без потерь, (б) корректно маппит их в `fields[SongField.X]`
    или прямые свойства `Song` (albumId, songType и т.п.), (в) сохраняет
    существующую семантику специальной обработки (например,
    `fileName` через sanitize+collision+active-process проверки).
- **FR-012** (рефактор, non-string типы): При использовании B1 (`Map<String, String>`)
  маппер ДОЛЖЕН корректно парсить нестроковые типы (`Int?`, `Long?`,
  `Boolean?`, enum-ы вроде `songType`) из строкового представления.
  Поведение при ошибке парсинга: HTTP 400 с понятным сообщением
  «Invalid value for param X: 'abc' is not a number» (НЕ silent ignore).
- **FR-013** (рефактор, обратная совместимость): Существующие клиенты
  (внешние скрипты, прямые вызовы `/api/song/update`) ДОЛЖНЫ продолжать
  работать без изменений — payload-формат остаётся тем же (query-параметры),
  response-формат (`SongUpdateResultDto`) остаётся тем же.
- **FR-014** (рефактор, специальная обработка): Специальные проверки,
  упомянутые в текущем `songs2Update` (sanitize для `fileName`,
  `KaraokeProcess.hasActiveProcess`, `Album.getAlbumById` для
  `albumId` cross-author check, baseline-автозаполнение
  `song_name_censored`), ДОЛЖНЫ быть сохранены в новом маппере —
  они не часть бага и должны работать как раньше.

### Non-Functional Requirements

- **NFR-001** (Performance): Добавление одного `@RequestParam` НЕ ДОЛЖНО
  заметно влиять на latency `/api/song/update` (Spring Web парсит
  query-параметры в map, O(N) по числу параметров, N≈95 — пренебрежимо).
- **NFR-002** (Security): Значение `songNameCensored` сохраняется
  через prepared statement (`?` placeholder в `saveToDb()`),
  SQL-инъекция исключена.
- **NFR-003** (Observability): Backend-логи при изменении
  `song_name_censored` ДОЛЖНЫ включать diff-запись
  `song_name_censored: old -> new` (через существующий
  `RecordChangeMessage`, см. specs/288-prod-diagnostics-logging).
- **NFR-004** (OpenProject DoD — старт): Перед началом работы
  над этим таском агент ДОЛЖЕН выполнить
  `tools/tracker.sh claim-issue 52` (HTTP `PATCH /api/v3/work_packages/52`
  с `assignee=ai-agent` + `status=In progress`). Без этого issue
  остаётся в статусе New и параллельная работа может быть потеряна.
- **NFR-005** (OpenProject DoD — отчёт при завершении): После прохождения
  всех проверок (FR-001..FR-014, SC-001..SC-010, AGENTS.md pre-commit
  8/8 + CI 7/7+) агент ДОЛЖЕН:
  1. Опубликовать комментарий-отчёт через
     `tools/tracker.sh add-comment 52 --file .report-tracker-52.md`
     (секции: «Что сделано», «Изменённые файлы», «Прогон проверок»,
     «Известные ограничения» — см. specs/295-jira-local-integration FR-008).
  2. Перевести статус в `In review` через
     `tools/tracker.sh mark-review 52`.
  3. НЕ закрывать issue самостоятельно (`close-issue`) — это делает
     пользователь после одобрения результата. В DoD этой спеки —
     только `mark-review`, `close` — вне нашего контроля.
- **NFR-006** (Cleanup test data — Session 2026-09-03 Q5→B): После
  ручной верификации SC-001 (10 тестовых правок на LOCAL-БД) агент
  ДОЛЖЕН выполнить `tools/cleanup-test-songs.sql` (или ручной
  `UPDATE tbl_songs SET song_name_censored = <original_value> WHERE
  id IN (<test_ids>)`) для возврата песен в исходное состояние. Исходные
  значения `song_name_censored` ДОЛЖНЫ быть сохранены в
  `.report-tracker-52.md` (секция «Cleanup») до выполнения теста,
  чтобы cleanup был воспроизводим.

### Key Entities

- **tbl_songs.song_name_censored** (TEXT, NOT NULL DEFAULT ''):
  цензурированное название песни. Хранится в `fields[SongField.NAME_CENSORED]`
  на стороне Kotlin. Используется в публичных шаблонах VK/Telegram/News
  и публичном API без re-censor.
- **SongDTO.songNameCensored** (String): JSON-поле в публичном API,
  читается из `tbl_songs.song_name_censored`.
- **SongEdit.vue.song.songNameCensored** (Vuex-state):
  клиентское представление, v-model на `<input>`.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% ручных правок `song_name_censored` через SongEdit
  сохраняются в БД (verification: выполнить 10 ручных правок на
  LOCAL-БД — Session 2026-09-03 Q5→B, все 10 видны в БД после reload
  и после очистки через `tools/cleanup-test-songs.sql`).
  До фикса: 0/10. После фикса: 10/10.
- **SC-002**: Время от ввода значения в UI до появления в БД —
  ≤2 секунды (autosave debounce 1 сек + Spring POST roundtrip).
  Verification: замерить вручную на LOCAL-БД при выполнении SC-001.
- **SC-003**: Аудит всех 95 `v-model` полей SongEdit против списка
  `@RequestParam` в `songs2Update` показывает 0 потерянных полей
  (за исключением whitelist). До фикса: 1 потерянное поле
  (`songNameCensored`).
- **SC-004**: Чек `tools/check-songedit-field-coverage.sh` запускается
  за ≤1 секунды на полном SongEdit.vue, exit code 0/1.
- **SC-005**: CI блокирует merge PR, который добавляет новое
  `v-model="song.X"` в SongEdit без соответствующего `@RequestParam X`
  в `ApiController.songs2Update`.
- **SC-006**: Аудит-чек для других эндпоинтов (`/albums/updatealbum`,
  `/authors/updateauthor`, …) выполняется за ≤5 секунд, exit code 0
  (включая whitelist).
- **SC-007**: Документация `docs/features/song-edit-and-censored.md`
  обновлена в том же PR (FR-009).
- **SC-008**: Никаких регрессий в существующих сценариях specs/277
  (US-1 CustomFunction реckan, baseline-автозаполнение новых песен,
  публичный API, шаблоны VK/Telegram/News).
- **SC-009** (рефактор, эквивалентность поведения): После FR-011
  существующие 95 полей `songs2Update` сохраняют поведение 1:1
  (тот же setter, та же специальная обработка fileName/albumId/...
  см. FR-014). Verification: integration-тест, который вызывает
  `/api/song/update` с полным набором параметров и сравнивает
  состояние Song до и после.
- **SC-010** (рефактор, обратная совместимость): Существующие клиенты
  (скрипты, прямые вызовы `/api/song/update`) продолжают работать
  без изменений payload (FR-013). Verification: набор golden-requests
  из существующих скриптов проекта (если есть) → все возвращают
  ожидаемый `SongUpdateResultDto`.
- **SC-011** (OpenProject DoD): В конце работы над этой спекой issue
  #52 в OpenProject ДОЛЖЕН иметь статус `In review` (через
  `mark-review`) и комментарий с отчётом по стандартной форме (NFR-005).
  Verification: `tools/tracker.sh get-issue 52` показывает status=In review
  и наличие последнего комментария от ai-agent.

## Assumptions

- **Vue-diff механизм корректен**: `getSongDiff` действительно включает
  `songNameCensored` в diff, `executeSave` отправляет его в payload —
  это подтверждено чтением кода. Альтернативный сценарий (фронт не
  отправляет поле) исключён.
- **Spring Web @RequestParam behavior**: неизвестные query-параметры
  молча отбрасываются без ошибки. Это дефолт Spring MVC поведение,
  актуальное для всех версий Spring Boot 2.x/3.x.
- **Существующий baseline-механизм** в `Song.kt:5364`
  (`song_name_censored` автозаполняется через `songName.censored(database)`,
  если поле пустое) НЕ нуждается в изменении — он срабатывает только
  при пустом поле, а FR-002 гарантирует, что пустое значение от UI
  перезаписывает baseline (т.е. baseline не перетирает ручное стирание).
- **Политика «доверие редактору»** из specs/277 Clarification Q1/A
  остаётся в силе: backend НЕ re-censor'ит значение на лету, не
  валидирует словарь, не предупреждает о потенциально нецензурном
  содержимом.
- **Whitelist `tools/check-songedit-field-coverage.whitelist.yml`** —
  это не «ослабление чека», а явный список исключений с обоснованием,
  видимый в diff каждого PR. Предзаполненный объём — ≤10 полей
  (Session 2026-09-03 Q4→B). При росте whitelist'а >15 — это симптом
  «чек слишком шумный, надо переделать» (например, перейти на
  AST-анализ вместо grep).
- **Аудит других эндпоинтов** может показать, что других
  потерянных полей нет — это валидный результат, не «нет работы».
  Если чек находит баги — они оформляются отдельными спеками.
- **Pre-commit уже содержит 7 проверок** (см. AGENTS.md / CLAUDE.md).
  Добавление FR-006 увеличит счётчик до 8. CI workflow тоже расширяется.
- **Текущая среда**: `webvue3` (Vue 3 + Vite + Vuex), `karaoke-app`
  (Kotlin/Spring Boot 3, JDK 17). `karaoke-web` (тонкий слой) эту
  логику не содержит — проксирует `/api/song/*` в karaoke-app.

## Out of Scope (MVP этой спеки)

- Trim / валидация длины / запрет whitespace-only значения
  `song_name_censored` — политики, не баг.
- Frontend-ограничения (maxlength="512") — UX-улучшение, не блокер.
- Изменение public API контракта `SongDTO.songNameCensored` — поле
  уже есть, контракт стабилен.
- Миграция существующих строк `tbl_songs` — `song_name_censored`
  уже заполнен через specs/277 (baseline + CustomFunction реckan).
- Расширение чек-листа на другие UI-компоненты (PictureEdit, SubsEdit,
  SiteUserEdit) — out of scope этой спеки.

## Open Questions

Нет критических неопределённостей. Все предположения зафиксированы
в секции Assumptions. Если в ходе реализации обнаружится, что
фронт НЕ отправляет `songNameCensored` в payload (другой root cause),
это будет исправлено в той же PR.
