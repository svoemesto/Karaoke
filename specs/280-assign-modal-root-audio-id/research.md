# Research: 280 — AssignModal: фильтр по rootId и audioRootId

**Date**: 2026-08-31
**Spec**: [spec.md](spec.md)
**Branch**: `280-assign-modal-root-audio-id`

## Открытые вопросы на старте

| # | Вопрос | Решение | Источник |
|---|--------|---------|----------|
| Q1 | Что такое «audioRootId» в ТЗ? | Каноническое имя в проекте — `audioParentId`. Метка в UI — «A-root ID:» (UI-конвенция из `SongsFilterModal.vue`). | `SongDTOdigest.kt:96-97`, `ApiController.kt:2387,2464`, `SongsFilterModal.vue:100-113` |
| Q2 | Бэкенд уже принимает `filterRootId`/`filterAudioParentId`? | Да. `POST /api/songsdigests` маппит их в `filter_root_id`/`filter_audio_parent_id`, `Song.loadListFromDb` фильтрует по `=` в SQL. | `ApiController.kt:2386-2387,2463-2464`; `Song.kt:7577-7578,7844-7845` |
| Q3 | DTO содержит `rootId` и `audioParentId`? | Да. `SongDTOdigest` (поле `rootId: Long`, `audioParentId: Long`). | `SongDTOdigest.kt:96-97` |
| Q4 | UI-конвенция для числового фильтра в админке? | В `SongsFilterModal.vue` уже есть оба поля с метками «root ID:» и «A-root ID:» — повторяем стиль (label + input + clear-button в одной строке `.sfm-filter-row`). | `SongsFilterModal.vue:85-113` |
| Q5 | Нужны ли изменения в `webvue3/src/components/SongEditor/store.js`? | Да, минимальные: action `searchCandidateSongs` (строка 173) принимает `{ query, author, album, onlyStatus1 }` → расширяем до `{ query, author, album, onlyStatus1, rootId, audioRootId }` (имена полей payload — `rootId`/`audioRootId`, маппятся внутри в `filterRootId`/`filterAudioParentId`). | `store.js:173-185` |
| Q6 | Нужны ли npm-зависимости? | Нет. Используется только `v-model` (Vue 3 core) и существующий `promisedXMLHttpRequest`. | `AssignModal.vue:130-200` |
| Q7 | Сохранять ли фильтр в `localStorage`/store? | Нет, вне scope (Assumption A-5). Фильтр живёт пока открыта модалка. | Spec Assumption A-5 |
| Q8 | Триггерить ли поиск по Enter / debounce? | Нет. Только явная кнопка «Найти» (как сейчас). Поля фильтра совпадают с поведением `query`/`author`/`album`. | `AssignModal.vue:67` |

## Решения (Decision / Rationale / Alternatives)

### D-1: Имя поля в payload action — `rootId` / `audioRootId` (соответствует ТЗ)

- **Decision**: в `AssignModal.vue` `data()` — `rootIdQuery` / `audioRootIdQuery`; в payload `dispatch('searchCandidateSongs', { ..., rootId, audioRootId })`; внутри action — `params.filterRootId = rootId; params.filterAudioParentId = audioRootId`.
- **Rationale**: 1) payload action использует camelCase, бэкенд принимает `filterRootId`/`filterAudioParentId` — это конвенция `SongsFilterModal.vue` (строки 931-933); 2) имя `audioRootId` оставлено в payload, потому что оно совпадает с формулировкой ТЗ и недвусмысленно указывает на «A-root ID» из `SongsFilterModal.vue`; 3) внутри store происходит перевод в канонические `filterAudioParentId` — это единственная точка маппинга, дублирования логики нет.
- **Alternatives considered**:
  - передавать напрямую `filterRootId` / `filterAudioParentId` из модалки → отказываемся: нарушает инкапсуляцию (модалка знает имена query-параметров HTTP), плюс не совпадает с конвенцией `SongsFilterModal.vue`.
  - передавать `rootId` / `audioParentId` (как в DTO) → отказываемся: размывает различие между «значением DTO» и «параметром фильтра» (визуально и семантически); плюс требует переименования переменной в action.

### D-2: Метки в UI — «root ID:» / «A-root ID:» (как в SongsFilterModal.vue)

- **Decision**: метки `<label>` — «root ID:» и «A-root ID:»; плейсхолдеры — «root ID…», «A-root ID…».
- **Rationale**: визуальная и терминологическая консистентность между двумя фильтрами в админке (`SongsFilterModal.vue:85-113` и `AssignModal.vue`). Админу не нужно запоминать, что «audioRootId» из ТЗ = «A-root ID» в UI.
- **Alternatives considered**:
  - «root_id» / «audio_parent_id» (raw DB names) → отказываемся: админ-UI, не дев-инструмент; raw-имена нечитаемы.
  - «Корневая песня ID» / «Корневое аудио ID» → отказываемся: длинно; «A-root ID» уже устоялось в проекте.

### D-3: Порядок полей в строке фильтра — Автор, Альбом, Название, root ID, A-root ID

- **Decision**: новые поля добавляются справа от существующих трёх текстовых полей, в порядке «root ID» → «A-root ID».
- **Rationale**: 1) минимизация визуальной перестановки (FR-001 спеки); 2) `root_id` логически первичнее (`audio_parent_id` — ссылка на конкретную песню, у которой тоже есть `root_id`); 3) `SongsFilterModal.vue` использует тот же порядок: «root ID» перед «A-root ID».
- **Alternatives considered**:
  - Поменять местами (A-root первым) → отказываемся: нарушает конвенцию проекта.
  - Спрятать новые поля под отдельную кнопку «Расширенный фильтр» → отказываемся: превышает scope фичи; FR-001 явно говорит «в строке фильтра».

### D-4: Кнопки очистки полей — локальные «X» справа от каждого input

- **Decision**: каждое новое числовое поле получает свою кнопку очистки по правому краю строки (`.se-btn-clear` или inline-styled button), обнуляющую только это поле.
- **Rationale**: соответствует US2 спеки и конвенции `SongsFilterModal.vue:92-96` (`.sfm-button-clear-field`). Альтернативы — очистка всех полей разом — нет (только общая кнопка «Очистить всё» уже есть для `selectedSongs`, не для фильтра).
- **Alternatives considered**:
  - Делать очистку через двойной клик / длительное нажатие → отказываемся: нестандартно, недоступно (a11y).
  - Сброс через нажатие Escape → отказываемся: недокументированное поведение.

### D-5: Валидация на клиенте — только цифры через `inputmode="numeric"` + JS-проверка

- **Decision**: `<input type="text" inputmode="numeric" pattern="[0-9]*">`. Перед отправкой payload — `if (rootIdQuery.trim() && /^\d+$/.test(rootIdQuery.trim()))` — иначе параметр опускается.
- **Rationale**: HTML5 `inputmode="numeric"` даёт цифровую клавиатуру на мобильных; `pattern` — встроенный hint для браузера; JS-валидация — единственная надёжная защита (HTML-валидацию можно обойти через DevTools).
- **Alternatives considered**:
  - `<input type="number" min="0">` → отказываемся: type=number имеет проблемы на iOS Safari (спиннеры, локаль, дробные числа); для текстового ввода с клиентской валидацией `text + pattern` надёжнее.
  - Без валидации (пусть бэкенд решает) → отказываемся: пользователь увидит HTTP 400 без понятного сообщения (бэкенд сейчас просто игнорирует пустые параметры, но нечисловые могут пройти как есть и дать пустой результат без объяснения).

### D-6: Обратная совместимость (пустые поля) — НЕ передавать параметр в payload

- **Decision**: `if (rootIdQuery.trim() !== '') params.rootId = rootIdQuery.trim()`. Аналогично `audioRootIdQuery`.
- **Rationale**: полностью совпадает с существующей логикой для `query`/`author`/`album` (`store.js:174-178`). Нулевая стоимость для бэкенда (без фильтрации по отсутствующему полю).
- **Alternatives considered**:
  - Всегда передавать `rootIdQuery=''` → отказываемся: зашумляет payload, нет функциональной разницы.

## Аналоги в проекте (паттерн уже использован)

| Файл | Поля | Поведение |
|------|------|-----------|
| `webvue3/src/components/Songs/filter/SongsFilterModal.vue:85-113` | `songsFilterRootId`, `songsFilterAudioParentId` | `<input>` + кнопка «X» очистки; метки «root ID:», «A-root ID:»; передаются в `params.filterRootId`/`filterAudioParentId` (строки 931-933). |
| `webvue3/src/components/SongEditor/AssignModal.vue:44-67` | `authorQuery`, `albumQuery`, `searchQuery` | `<input>` + Enter → поиск; метка-плейсхолдер. Передаются в `params.filterAuthor`/`filterAlbum`/`filterSongName`. |
| `karaoke-app/src/main/kotlin/.../controllers/ApiController.kt:2386-2387,2463-2464` | `filterRootId`, `filterAudioParentId` | `@RequestParam(required=false)`, мапятся в `filter_root_id`/`filter_audio_parent_id`. |

**Вывод**: паттерн «числовой фильтр с локальной очисткой» уже отработан в `SongsFilterModal.vue`. Адаптация к `AssignModal.vue` — копирование подхода с поправкой на другой action (`searchCandidateSongs` вместо `loadSongsDigests`) и другой store (`SongEditor/store.js` вместо `Songs/filter/store.js`).

## Риски

| Риск | Митигация |
|------|-----------|
| ESLint-предупреждения на новой разметке | после правки — `cd webvue3 && npm run lint` + `./tools/check-eslint-baseline.sh webvue3`; baseline не должен расти. |
| Docker-сборка webvue3 (Pass 245 инцидент: кросс-импорты ломают multi-stage) | фича не вводит новых кросс-импортов; проверить `bash do.sh build_webvue3` после правки. |
| Нечисловой ввод ломает SQL | клиентская валидация + параметр опускается при невалидном значении (D-5, D-6). |
| Путаница в терминологии `audioRootId` vs `audioParentId` | явно зафиксировано в спеке (Assumption A-1); в коде и UI используется `audioParentId` / «A-root ID», в payload action — `audioRootId` (как в ТЗ) с маппингом в action. |
| Админ вводит ID в `rootId`, но его песни используют `audio_parent_id` для группировки | это намеренное разделение (root_id — семейство по песне, audio_parent_id — семейство по аудио); документировано в FR-005 спеки и Assumption A-1. |

## Готовность к Phase 1

- [x] Все NEEDS CLARIFICATION из Technical Context разрешены
- [x] Подтверждено: бэкенд без изменений
- [x] Подтверждено: DTO без изменений
- [x] Подтверждено: npm-зависимости не нужны
- [x] Подтверждено: ESLint baseline не должен расти
- [x] Подтверждено: Docker-сборка не требует новых COPY-контекстов
