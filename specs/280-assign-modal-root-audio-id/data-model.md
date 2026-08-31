# Data Model: 280 — AssignModal: фильтр по rootId и audioRootId

**Date**: 2026-08-31
**Spec**: [spec.md](spec.md)

> Фича НЕ вводит новых сущностей в БД и НЕ меняет DTO. Этот документ фиксирует только изменение локального состояния Vue-компонента и форму payload существующего Vuex-action.

## 1. Изменение состояния компонента `AssignModal`

### 1.1. До фичи (`AssignModal.data()`)

| Поле | Тип | Default | Описание |
|------|-----|---------|----------|
| `assigneeId` | `Number` | `0` | ID выбранного пользователя-редактора |
| `busy` | `Boolean` | `false` | идёт запрос assign |
| `message` | `String` | `''` | текст сообщения об успехе/ошибке |
| `isError` | `Boolean` | `false` | флаг «сообщение — ошибка» |
| `searchQuery` | `String` | `''` | фильтр «Название песни» |
| `authorQuery` | `String` | `''` | фильтр «Автор» |
| `albumQuery` | `String` | `''` | фильтр «Альбом» |
| `dictAuthors` | `Array<String>` | `[]` | справочник авторов для `<datalist>` |
| `results` | `Array<SongDTOdigest>` | `[]` | результаты поиска |
| `searching` | `Boolean` | `false` | идёт HTTP-запрос |
| `searched` | `Boolean` | `false` | был ли выполнен хоть один поиск |
| `onlyStatus1` | `Boolean` | `true` | чекбокс «Только кандидаты на разметку» |
| `selectedSongs` | `Array<SongDTOdigest>` | `[]` | песни, выбранные для назначения |
| `hrSongId` | `Number\|null` | `null` | ID для открытия health-report |

### 1.2. После фичи — добавляются 2 поля

| Поле | Тип | Default | Описание |
|------|-----|---------|----------|
| `rootIdQuery` | `String` | `''` | фильтр «root ID» (значение, введённое админом; строка для совместимости с input) |
| `audioRootIdQuery` | `String` | `''` | фильтр «A-root ID» (значение, введённое админом; строка для совместимости с input) |

### 1.3. Правила валидации (новые поля)

| Правило | Условие | Поведение |
|---------|---------|-----------|
| `V1` пустое | `value.trim() === ''` | параметр НЕ передаётся в payload (как `query`/`author`/`album`) |
| `V2` только цифры | `/^\d+$/.test(value.trim())` | параметр передаётся в payload |
| `V3` нечисловое | `!/^\d+$/.test(value.trim())` | параметр НЕ передаётся (эквивалент «пустое»), сообщение об ошибке НЕ показывается |
| `V4` переполнение Long | значение > `Number.MAX_SAFE_INTEGER` (≈9.007e15) | браузер сам потеряет точность; бэкенд получит обрезанное число и SQL даст пустой результат — допустимо, в спеке Assumption A-2 |

### 1.4. Lifecycle

- `mounted()` — без изменений (новые поля уже инициализированы как `''` через `data()`).
- `beforeDestroy()` — без изменений (никаких подписок/таймеров/AbortController не вводится).
- Сохранение в localStorage / Vuex — НЕ выполняется (FR-012, Assumption A-5).

## 2. Существующая сущность `SongDTOdigest` (БЕЗ изменений)

### 2.1. Поля, относящиеся к фильтрации

| Поле DTO | Тип | Описание | Использование в AssignModal |
|----------|-----|----------|----------------------------|
| `id` | `Long` | первичный ключ песни | отображение `#{{ s.id }}`, ключ в `v-for` |
| `rootId` | `Long` | ID корневой песни семейства | **фильтрация по `rootIdQuery`** (НЕ отображается в строке результата — FR-008 спеки) |
| `audioParentId` | `Long` | ID родительской аудио-версии | **фильтрация по `audioRootIdQuery`** (НЕ отображается в строке результата — FR-008) |
| `author` | `String` | имя автора | отображение `{{ s.author }}` |
| `album` | `String` | название альбома | отображение `{{ s.album }}` |
| `songName` | `String` | название песни | отображение `{{ s.songName }}` |
| `year` | `Integer\|null` | год | отображение `({{ s.year }})` |
| `status` | `String` | статус песни (название, не id) | отображение `{{ s.status }}` |

> **Важно:** поля `rootId` / `audioParentId` уже есть в DTO (`SongDTOdigest.kt:96-97`), фронт их получает, но в текущем `AssignModal.vue` НЕ использует ни для отображения, ни для фильтрации. После фичи — начинают использоваться для фильтрации (входной параметр HTTP-запроса), но **не** для отображения.

## 3. Маппинг payload (AssignModal → action → HTTP)

```text
AssignModal.data()               Vuex action payload          HTTP query param
─────────────────────────────────────────────────────────────────────────────────
searchQuery          ─────────►  payload.query        ──────►  filterSongName
authorQuery          ─────────►  payload.author       ──────►  filterAuthor
albumQuery           ─────────►  payload.album        ──────►  filterAlbum
onlyStatus1          ─────────►  payload.onlyStatus1  ──────►  filterStatus
rootIdQuery     NEW  ─────────►  payload.rootId       ──────►  filterRootId
audioRootIdQuery NEW ─────────►  payload.audioRootId  ──────►  filterAudioParentId
                                                                (rename inside action)
```

**Контракт маппинга (action `searchCandidateSongs`):**
- `payload.rootId` (String|undefined) → `params.filterRootId = rootId` (только если валидно по V1/V2)
- `payload.audioRootId` (String|undefined) → `params.filterAudioParentId = audioRootId` (только если валидно по V1/V2)

## 4. Серверная модель (БЕЗ изменений)

### 4.1. Таблица `song` (или эквивалент)

Колонки, участвующие в фильтрации:

| Колонка | Тип | Использование |
|---------|-----|---------------|
| `root_id` | `BIGINT` | фильтр `filter_root_id` (точное совпадение `=`) |
| `audio_parent_id` | `BIGINT` | фильтр `filter_audio_parent_id` (точное совпадение `=`) |
| `id_status` | `BIGINT` | фильтр `filterStatus` (=`1` если `onlyStatus1=true`) |
| `song_name` | `TEXT` | LIKE-фильтр `filterSongName` (`song_name`) |
| `song_author` | `TEXT` | LIKE-фильтр `filterAuthor` |
| `song_album` | `TEXT` | LIKE-фильтр `filterAlbum` |

### 4.2. Триггеры, индексы

- **Индекс по `root_id`**: должен существовать (используется в `SongsFilterModal.vue` и `apisSongsDigests` уже давно). Если нет — это out of scope данной фичи (FR-010 спеки: «бэкенд не меняется»).
- **Индекс по `audio_parent_id`**: аналогично.
- **recordhash-триггер на `song`**: не затрагивается (схема не меняется).

## 5. Состояния / переходы

Фича не вводит новых state-machines. Существующий жизненный цикл модалки:

```text
closed ──(открытие из SongEditorTable)──► mounted ──(doSearch)──► searching ──(response)──► displayed
                                                                                              │
                                                                                              (toggleSelect)
                                                                                              ▼
                                                                                       selecting songs
                                                                                              │
                                                                                              (doAssign)
                                                                                              ▼
                                                                                       assigning
                                                                                              │
                                                                                              (succeed|fail)
                                                                                              ▼
                                                                                       results(updated)
displayed ──(close emit)──► beforeDestroy ──► closed
```

Новые поля `rootIdQuery` / `audioRootIdQuery` живут во всех состояниях с момента `mounted` до `beforeDestroy`. Сброс — только явный (через кнопку «X» или при повторном открытии модалки — FR-011 спеки: «инициализируются пустой строкой при каждом открытии»).

## 6. ER-диаграмма (контекст)

```text
┌─────────────────────┐
│  song (table)       │
├─────────────────────┤
│ id (PK)             │
│ root_id (BIGINT)    │◄──── self-reference (parent in family)
│ audio_parent_id     │◄──── self-reference (parent in audio family)
│ id_status           │
│ song_name           │
│ song_author         │
│ song_album          │
│ ...                 │
└─────────────────────┘
       │
       │  1:N
       ▼
┌─────────────────────┐
│  song_assignment    │  (назначение на разметку — out of scope)
│  ...                │
└─────────────────────┘
```

Изменений в схеме нет.
