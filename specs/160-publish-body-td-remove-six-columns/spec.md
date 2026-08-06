# Feature Specification: Упрощение PublishTableBodyTd + полная чистка DTO от processColor*

**Feature Branch**: `160-publish-body-td-remove-six-columns`

**Created**: 2026-08-06

**Status**: Draft

**Input**:
- Исходный запрос: «Админка, `PublishTableBodyTd.vue`. Оставить только ячейку
  `class="publish-name"`, следующие за ней шесть `class="publish-column"`
  убрать, ширину `publish-name` увеличить на суммарную ширину убранных
  `publish-column`».
- Уточнение 1: «так как убираются ячейки, то отпадает необходимость в ДТО
  получать информацию о них (цвет). Из ДТО тоже надо убрать лишние теперь
  данные (если они не используются где-то в другом месте, это надо проверить)».
- Уточнение 2: «убрать `processColorMeltLyrics/Karaoke/Chords/Melody`,
  кнопки PLAY в SongEdit не раскрашивать».

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Читаемая ячейка с названием песни в таблице «Публикации» (Priority: P1)

Администратор (редактор каталога) открывает раздел «Публикации» в админке
(`webvue3`) и смотрит на сетку песен, готовящихся к публикации на разных
платформах. Каждая ячейка `PublishTableBodyTd` сейчас показывает название
песни (150 px) плюс шесть узких цветовых индикаторов состояния публикации
по 10 px (Melt, Sponsr, Dzen, VK, Pl, Telegram) — итого ячейка шириной
210 px, которая визуально ужимается в 200 px из-за родительского
контейнера. Цветовые «полосочки» перестали быть нужны редактору, и он
хочет видеть только название песни на всю ширину ячейки.

**Why this priority**: основная пользовательская история фичи — она
описывает ровно то, что меняется в UI. Без неё фичи нет.

**Independent Test**: открыть «Публикации» в `webvue3`, убедиться, что
каждая ячейка содержит только название песни, без шести цветовых
индикаторов; название занимает 210 px по ширине.

**Acceptance Scenarios**:

1. **Given** админ открыл раздел «Публикации» в админке,
   **When** он смотрит на любую строку таблицы с заполненной ячейкой,
   **Then** ячейка содержит только блок с названием песни и не содержит
   шести узких блоков-индикаторов публикации.
2. **Given** ячейка отображает только название песни,
   **When** измерять ширину блока `.publish-name` в DevTools,
   **Then** ширина равна 210 px (150 px старых + 60 px освободившихся
   от шести убранных колонок по 10 px).
3. **Given** ячейка отображает только название песни,
   **When** навести курсор на название,
   **Then** цвет названия меняется на красный и курсор становится
   «pointer» (поведение клика по `.publish-name:hover` сохранено).

---

### User Story 2 — Корректная отрисовка пустой ячейки (Priority: P2)

Если у ячейки нет связанной песни (`publish === null`), компонент
рендерит серый плейсхолдер `.empty`. После расширения имени ширина
ячейки увеличивается, и плейсхолдер должен соответствовать новой
ширине, чтобы строки таблицы не «прыгали» по высоте и не было
горизонтального сдвига.

**Why this priority**: визуальная консистентность пустых и заполненных
ячеек — без этого таблица выглядит «рваной».

**Independent Test**: в строке с пустой ячейкой убедиться, что серый
плейсхолдер имеет ту же ширину, что и заполненная ячейка (210 px).

**Acceptance Scenarios**:

1. **Given** строка таблицы с пустой ячейкой (нет связанной песни),
   **When** отображается раздел «Публикации»,
   **Then** серый плейсхолдер `.empty` имеет ширину 210 px и совпадает
   по горизонтальным границам с соседними заполненными ячейками.

---

### User Story 3 — Кнопки PLAY в SongEdit без раскраски (Priority: P1)

В `SongEdit.vue` есть 4 кнопки воспроизведения (`PLAY LYRICS`,
`PLAY KARAOKE`, `PLAY CHORDS`, `PLAY TABS`), у которых сейчас фон
задаётся через `:style="{ backgroundColor: song.processColorMelt* }"`.
Цветовая индикация этих кнопок не нужна редактору (состояние обработки
видно по другим признакам — `flag*`-колонкам в `SongsTable.vue`,
бейджу `flagPlayerDemo`, статусу задачи), и лишние `processColorMelt*`
поля в DTO тоже не нужны.

**Why this priority**: прямая просьба пользователя, расширение US3
(полная чистка DTO от всех `processColorMelt*`).

**Independent Test**: открыть `SongEdit.vue` для любой песни, убедиться,
что 4 кнопки «PLAY …» имеют одинаковый фон (по умолчанию из CSS
`.group-button`) и ни одна из них не использует inline-стиль
`backgroundColor`.

**Acceptance Scenarios**:

1. **Given** редактор открыл `SongEdit.vue` для любой песни,
   **When** посмотреть на кнопки «PLAY LYRICS / KARAOKE / CHORDS /
   TABS»,
   **Then** ни одна из 4 кнопок не имеет атрибута `:style` с
   `backgroundColor`; фон у всех одинаковый (определяется CSS-классом
   `.group-button`).
2. **Given** в `SongEdit.vue` свойство `song.processColorMeltLyrics`
   больше не читается,
   **When** фронт получит обновлённый DTO без `processColorMelt*`
   полей,
   **Then** ни один Vue-биндинг не выбросит ошибку (поля просто
   отсутствуют — раньше их никто, кроме этих 4 кнопок, не использовал).

---

### User Story 4 — Чистый DTO без полей, которые никто не читает (Priority: P1)

После удаления шести цветовых ячеек (`PublishTableBodyTd`) и
раскраски кнопок PLAY (`SongEdit.vue`) фронт перестаёт быть
потребителем 27 из 28 полей `processColor*` в DTO. Остаётся
живым только `processColorPlayerDemo` (бейдж `DE` в `SongsTable.vue`,
строка 329).

Эти 27 полей продолжают вычисляться в backend-модели `Song.kt` (нужны
для diff-логики LOCAL↔SERVER sync) и попадают в JSON-ответы
`/api/songs`, `/api/songsdigests`, `/api/songshistory`,
`/api/publications`, `/api/unpublications` — а их никто не читает.

Проверка по всей кодовой базе (`grep -rEn "processColor[A-Za-z]+" webvue3/src
karaoke-public/src karaoke-web/src` + `karaoke-app/src/main` с фильтром на
DTO и шаблоны) подтвердила, что живыми потребителями остаются ровно
**одно** поле: `processColorPlayerDemo` (бейдж `DE` в
`SongsTable.vue`).

**Why this priority**: уменьшение payload и устранение мёртвого кода —
прямая просьба пользователя, логическое продолжение US1–US3.

**Independent Test**: открыть DevTools → Network → `/api/songsdigests` и
убедиться, что в JSON-ответе присутствует ровно **одно** поле
`processColor*` — `processColorPlayerDemo`. Остальных 27 нет.

**Acceptance Scenarios**:

1. **Given** бэкенд собирает ответ `/api/songsdigests`,
   **When** посмотреть JSON одной песни,
   **Then** в нём присутствует ровно одно поле с префиксом
   `processColor*` — `processColorPlayerDemo`. Остальные 27 (`Melt*`,
   `Sponsr`, `Vk`, `Boosty`, `Dzen*`, `Vk*`, `Telegram*`, `Pl*`,
   `Max*`) отсутствуют.
2. **Given** бэкенд собирает ответ `/api/publications`,
   **When** в каждом из `publish10..publish23` посмотреть набор полей,
   **Then** присутствует только `processColorPlayerDemo`; остальные
   `processColor*` отсутствуют.
3. **Given** фича смержена и перезапущен backend,
   **When** редактор открывает `SongsTable.vue`,
   **Then** бейдж `DE` в колонке `flagPlayerDemo` показывает цвет из
   `processColorPlayerDemo` (бейдж продолжает работать).

---

### Edge Cases

- **Шапка таблицы (`PublishTableHead.vue`) остаётся прежней**: ячейки
  `.td-text` сохраняют ширину 200 px (фиксированный `min-width/max-width`),
  а тело ячейки становится 210 px. В результате подписи колонок будут
  на 10 px уже тела ячейки — лёгкое визуальное рассогласование по правому
  краю. По явному указанию пользователя в скоупе только
  `PublishTableBodyTd.vue`, шапка в этом PR не меняется; если потребуется
  полное выравнивание — это отдельная задача.
- **Серверные шаблоны `publications.html` / `unpublications.html`**
  (Thymeleaf в `karaoke-app/src/main/resources/templates/`) обращаются к
  `/song/{id}` через `MainController.getSong`, который возвращает сырой
  `Song` (а не `SongDTO`). `Song` продолжает вычислять все `processColor*`
  через геттеры — JS в шаблонах продолжает работать без изменений.
- **LOCAL↔SERVER sync не затронут**: diff-логика в `Song.kt` (строки
  ~6816+) сравнивает `processColor*` геттеры. Поскольку `Song.kt`
  остаётся нетронутым (геттеры + diff сохраняются), sync продолжает
  работать. Удаление полей только из `SongDTO`/`SongDTOdigest` НЕ
  затрагивает recordhash (он считается на полях `tbl_settings`, не на
  DTO).
- **`Publication.kt` использует `Song.processColor*` геттеры** напрямую
  (например, `publish10!!.processColorMeltLyrics` в строках ~249+).
  Поскольку эти геттеры остаются в `Song.kt`, `Publication.kt` и
  `PublicationDTO` (включая `/api/publications`) работают без изменений.
- **`SongsTable.vue` содержит закомментированные блоки
  `<template #cell(flagPlLyrics)="data">` и аналогичные** (строки
  ~358–385), ссылающиеся на `data.item.processColorPlLyrics`,
  `processColorPlKaraoke`, `processColorPlChords`, `processColorPlMelody`.
  Эти ссылки станут «битыми» (полей больше нет в DTO), но блоки
  закомментированы и не выполняются. В этом PR они НЕ чистятся (выходит
  за рамки; может быть отдельной задачей).
- **JSON-кеши и фронтенд-стейт** (Vuex `songsDigest`,
  `publications`, `unpublications`): поскольку фронт уже не использует
  эти 27 полей (после FR-001 и US3), их отсутствие в ответе API не
  вызывает runtime-ошибок. Поля просто не появятся в `state.songsDigest[i]`
  — JS-биндинг молча вернёт `undefined`, и никаких `TypeError` не будет
  (поля не запрашиваются в шаблонах/сторе).

## Requirements *(mandatory)*

### Functional Requirements

**Frontend — `PublishTableBodyTd.vue`:**

- **FR-001**: В файле
  `webvue3/src/components/Publish/components/PublishTableBodyTd.vue`
  разметка ячейки `publish` MUST содержать только один блок с классом
  `publish-name`. Шесть блоков с классом `publish-column` MUST быть
  удалены вместе со всем их содержимым (`.publish-column-cell-top`,
  `.publish-column-cell-bottom`).
- **FR-002**: CSS-правило `.publish-name` MUST иметь `width: 210px`
  (текущие 150 px + 60 px суммарной ширины удалённых шести
  `publish-column` по 10 px).
- **FR-003**: CSS-правила `.publish-column`, `.publish-column-cell-top`,
  `.publish-column-cell-bottom` MUST быть удалены из блока `<style scoped>`
  компонента как ставшие неиспользуемыми.
- **FR-004**: CSS-правило `.publish` MUST иметь `min-width: 210px` и
  `max-width: 210px` (было 200 px) — иначе `publish-name` 210 px будет
  обрезаться или вылезать за контейнер.
- **FR-005**: CSS-правило `.empty` MUST иметь `width: 210px` для
  визуального соответствия ширине заполненной ячейки.
- **FR-006**: Все computed-свойства `processColor*` в блоке `computed:`
  компонента MUST быть удалены (после удаления шаблона они перестают
  иметь потребителей). В частности: `processColorBoosty`,
  `processColorSponsr`, `processColorVk`, `processColorMeltLyrics`,
  `processColorMeltKaraoke`, `processColorMeltChords`,
  `processColorMeltMelody`, `processColorPlayerDemo`,
  `processColorDzenLyrics`, `processColorDzenKaraoke`,
  `processColorDzenChords`, `processColorVkLyrics`,
  `processColorVkKaraoke`, `processColorVkChords`, `processColorPlLyrics`,
  `processColorPlKaraoke`, `processColorPlChords`, `processColorTelegramLyrics`,
  `processColorTelegramKaraoke`, `processColorTelegramChords`.
- **FR-007**: Методы `dblClickKaraoke`, `dblClickLyrics`, `dblClickChords`
  MUST быть удалены из блока `methods:` компонента, так как они были
  привязаны только к удаляемым `publish-column-cell-top` /
  `publish-column-cell-bottom`.
- **FR-008**: Существующее поведение клика по `.publish-name`
  (открытие `SongEditModal` через `editSong`), hover-стили
  (`.publish-name:hover`), всплывающая подсказка `title` и фон по
  `publish.color` MUST быть сохранены без изменений.
- **FR-009**: Компонент `PublishTableBodyTd.vue` MUST пройти
  `npm run lint:check` в `webvue3` без новых нарушений baseline
  (через `./tools/check-eslint-baseline.sh`).

**Frontend — `SongEdit.vue` (раскраска PLAY-кнопок):**

- **FR-016**: В файле `webvue3/src/components/Songs/edit/SongEdit.vue`
  у четырёх `<button>`-элементов «PLAY KARAOKE» (~стр. 2297–2304),
  «PLAY LYRICS» (~2305–2312), «PLAY CHORDS» (~2313–2320) и «PLAY TABS»
  (~2321–2328) MUST быть удалён атрибут `:style="{ backgroundColor:
  song.processColorMelt* }"`. Кнопки сохраняют обработчики
  `@click="playKaraoke"` / `playLyrics` / `playChords` / `playTabs`,
  CSS-класс `.group-button` и подсказки `title` без изменений; фон
  становится одинаковым у всех четырёх (определяется CSS-классом).
- **FR-017**: В файле `webvue3/src/components/Songs/edit/SongEdit.vue`
  MUST быть удалены все обращения к `song.processColorMeltLyrics`,
  `song.processColorMeltKaraoke`, `song.processColorMeltChords`,
  `song.processColorMeltMelody` (после FR-016 других потребителей этих
  полей в файле не остаётся — проверяется grep'ом).

**Backend — DTO и его потребители:**

- **FR-010**: В файле
  `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/SongDTO.kt`
  MUST быть удалены 27 полей с префиксом `processColor*`, не имеющие
  живых потребителей во фронте:
  - `processColorBoosty`, `processColorSponsr`, `processColorVk`,
  - `processColorMeltLyrics`, `processColorMeltKaraoke`,
    `processColorMeltChords`, `processColorMeltMelody`,
  - `processColorDzenLyrics`, `processColorDzenKaraoke`,
    `processColorDzenChords`, `processColorDzenMelody`,
  - `processColorVkLyrics`, `processColorVkKaraoke`,
    `processColorVkChords`, `processColorVkMelody`,
  - `processColorTelegramLyrics`, `processColorTelegramKaraoke`,
    `processColorTelegramChords`, `processColorTelegramMelody`,
  - `processColorPlLyrics`, `processColorPlKaraoke`,
    `processColorPlChords`, `processColorPlMelody`,
  - `processColorMaxLyrics`, `processColorMaxKaraoke`,
    `processColorMaxChords`, `processColorMaxMelody`.
  Должно остаться ровно одно поле — `processColorPlayerDemo`
  (потребитель: бейдж `DE` в `SongsTable.vue`).
- **FR-011**: В файле
  `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/SongDTOdigest.kt`
  MUST быть удалены те же 27 полей `processColor*`, что в FR-010
  (порядок полей в `SongDTOdigest` повторяет порядок в `SongDTO`).
- **FR-012**: В файле
  `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt`,
  в методе `toDTO()` (строки ~8177+) MUST быть удалены соответствующие
  27 строк присваивания (`processColorX = processColorX,`), чтобы
  компиляция прошла успешно после FR-010. Должна остаться ровно одна
  строка `processColorPlayerDemo = processColorPlayerDemo,`.
- **FR-013**: В файле
  `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/SongDTO.kt`,
  в методе `toDtoDigest()` (строки ~304+) MUST быть удалены
  соответствующие 27 строк присваивания, чтобы компиляция прошла
  успешно после FR-011. Должна остаться ровно одна строка
  `processColorPlayerDemo = processColorPlayerDemo,`.
- **FR-014**: Геттеры `processColor*` в `Song.kt` (строки ~2454–2538)
  MUST быть сохранены без изменений — они используются diff-логикой
  LOCAL↔SERVER sync (`Song.kt` ~6816–6966) и классом `Publication`
  (`Song.kt` ~249+) для собственных геттеров `publishXcolorMeltY` и
  т.п. Это вне скоупа чистки.
- **FR-015**: Diff-логика LOCAL↔SERVER в `Song.kt` MUST быть сохранена
  без изменений: поля `processColor*` продолжают участвовать в сравнении
  `settA.processColorX vs settB.processColorX`. Удаление полей только из
  DTO не затрагивает recordhash-триггеры `tbl_settings` (см. конституцию,
  Принцип III).

### Key Entities

- **`Song` (Kotlin, `karaoke-app/.../Song.kt`)** — доменная модель песни.
  Геттеры `processColor*` остаются (нужны для diff + Publication). Никаких
  изменений в этом PR.
- **`SongDTO` (Kotlin, `karaoke-app/.../SongDTO.kt`)** — DTO для
  админских ответов API. 27 полей `processColor*` удаляются, остаётся
  ровно `processColorPlayerDemo` (FR-010, FR-013).
- **`SongDTOdigest` (Kotlin, `karaoke-app/.../SongDTOdigest.kt`)** —
  «лёгкий» DTO для пагинированных списков. Те же 27 полей удаляются
  (FR-011).
- **`Publication` (Kotlin, `karaoke-app/.../Publication.kt`)** —
  модель публикации. Использует `Song.processColor*` геттеры для своих
  геттеров `publishXcolorMeltY`. Без изменений (Song.kt не тронут).
- **`PublishTableBodyTd` (Vue, `webvue3/.../PublishTableBodyTd.vue`)** —
  ячейка таблицы «Публикации». После PR содержит только `publish-name`
  шириной 210 px.
- **`SongEdit` (Vue, `webvue3/.../Songs/edit/SongEdit.vue`)** — редактор
  песни. После PR кнопки «PLAY LYRICS / KARAOKE / CHORDS / TABS» теряют
  `:style="{ backgroundColor: song.processColorMelt* }"` и получают
  единый фон из CSS-класса `.group-button`.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: В DOM каждой строки раздела «Публикации» для каждой
  непустой ячейки присутствует ровно один элемент с классом
  `publish-name` и ноль элементов с классом `publish-column`
  (проверяется через DevTools или unit-тест на snapshot).
- **SC-002**: Вычисленная ширина элемента `.publish-name` равна
  ровно 210 px в браузере (DevTools → Computed → width).
- **SC-003**: Контейнер `.publish` имеет вычисленную ширину 210 px,
  а плейсхолдер `.empty` — 210 px; никакого горизонтального
  скролла или обрезания имени внутри ячейки не наблюдается
  на стандартном наборе тестовых данных.
- **SC-004**: В JSON-ответе `/api/songsdigests` (и `/api/songs`,
  `/api/songshistory`, `/api/publications`, `/api/unpublications`)
  каждая песня содержит ровно **одно** поле с префиксом
  `processColor*` — `processColorPlayerDemo`. Проверяется через
  DevTools → Network или
  `jq '.[0] | keys | map(select(startswith("processColor")))'`
  (ожидается массив из одного элемента).
- **SC-005**: В DOM `SongEdit.vue` ни одна из 4 кнопок «PLAY LYRICS /
  KARAOKE / CHORDS / TABS» не имеет атрибута `style` с
  `background-color` (проверяется через DevTools → Elements →
  вычисленный стиль).
- **SC-006**: `npm run lint:check` в `webvue3`, `ktlintCheck` в
  `karaoke-app` и `pre-commit run --all-files` зелёные; новых нарушений
  ESLint/ktlint-baseline не появляется; компоненты проходят
  `./tools/check-jsdoc-coverage.sh webvue3` и
  `./tools/check-kdoc-coverage.sh` (JSDoc/KDoc на `export default` /
  `class` сохранены).
- **SC-007**: CI на PR зелёный (7/7 SUCCESS: ktlint, ESLint webvue3,
  ESLint karaoke-public, Docs, Baseline, KDoc, JSDoc) — иначе
  PR не мёрджится в master (см. `AGENTS.md` → «CI-gate для master»).
- **SC-008**: Визуальная проверка после деплоя:
  - В `SongsTable.vue` бейдж `DE` (`flagPlayerDemo`) показывает цвет
    из `processColorPlayerDemo` (единственное оставшееся
    `processColor*`-поле).
  - В `SongEdit.vue` кнопки «PLAY LYRICS / KARAOKE / CHORDS / TABS»
    имеют одинаковый фон (CSS-класс `.group-button`); цвета больше
    не зависят от `processColorMelt*` (полей нет в DTO).
  - В `publications.html` / `unpublications.html` цвета «полосок»
    публикации обновляются через SSE — данные берутся из `/song/{id}`
    через сырой `Song`, не из DTO; продолжает работать без изменений.

## Assumptions

- **Скоуп строго ограничен** `PublishTableBodyTd.vue` + `SongEdit.vue` (4
  кнопки) + двумя DTO + сопутствующими присваиваниями в `toDTO()` /
  `toDtoDigest()`. Вне скоупа:
  - Шапка `PublishTableHead.vue` (намеренное рассогласование ширины
    210 vs 200 px).
  - Геттеры `processColor*` в `Song.kt` — нужны diff'у и
    `Publication.kt`.
  - Diff-логика LOCAL↔SERVER в `Song.kt` — конституционный Принцип III
    NON-NEGOTIABLE.
  - Закомментированные блоки `<template #cell(flagPlLyrics)>` и
    аналогичные в `SongsTable.vue` — отдельная задача.
  - Геттер `processColorBoostyFiles` в `Song.kt:2483` (не в DTO,
    не во фронте, не в diff — мёртвый код) — отдельная задача.
- **Backend API остаётся совместимым по направлению «не добавлено»**:
  удаляются только поля, не имеющие потребителей; добавлений и
  переименований нет. Это формально breaking change для клиентов,
  которым эти поля нужны (если такие есть вне `webvue3` /
  `karaoke-public` / `karaoke-web`) — проверка `grep` не нашла таких
  клиентов. На фронте (`webvue3`, `karaoke-public`, `karaoke-web`)
  единственный потребитель `processColor*` — `processColorPlayerDemo`
  в `SongsTable.vue` (SC-008).
- **`Song.kt` геттеры `processColor*` остаются**, потому что они
  используются:
  - В diff-логике LOCAL↔SERVER sync (`Song.kt` ~6816–6966) — это
    NON-NEGOTIABLE по конституции Принцип III.
  - В `Publication.kt` для построения собственных геттеров
    `publishXcolorMeltY` (`Song.kt` ~249+) — нужно для шаблонов
    `publications.html` / `unpublications.html` (которые работают через
    `/song/{id}` → сырой `Song`, не через DTO).
- **Ширина суммируется строго как есть**: 6 колонок × 10 px = 60 px,
  прибавляется к текущим 150 px имени, получается 210 px. Никаких
  дополнительных отступов, padding или margin не добавляется —
  пользователь сказал «на суммарную ширину убранных».
- **Тесты в этом PR не требуются.** Существующие тесты в `karaoke-app`
  интеграционные и не покрывают Vue-компоненты админки
  (см. `AGENTS.md` → «Тесты»). Изменение проверяется визуально
  в dev-режиме `npm run dev` в `webvue3` и через DevTools → Network
  на dev-сервере `karaoke-app`.
- **Per-feature документы** (`docs/features/<slug>.md`) для этой фичи
  обновляются в этом же PR (см. FR-009 конституции):
  - `docs/features/songs-table.md` — добавить запись о #160: «после
    визуального удаления в #156 DTO тоже почищен от неиспользуемых
    `processColor*` полей (SongDTO + SongDTOdigest); осталось ровно
    одно поле — `processColorPlayerDemo` (бейдж `DE`); раскраска PLAY
    в `SongEdit.vue` тоже снята (FR-016); геттеры в Song.kt и
    diff-логика сохранены».
  - `docs/features/song-state-colors.md` — не требует правки (фича
    касается цвета ячейки целиком через `publish.color`, что
    сохраняется; цвета PLAY-кнопок были производными от
    `processColorMelt*`, не от `SongState`).
- **CHANGELOG / `docs/architecture-notes.md`** дополняется записью
  о PR в том же merge-коммите (см. `AGENTS.md` → «CI-gate для master» →
  исключение для документации-only коммитов).
- **JSON-совместимость API**: 27 полей `processColor*` удаляются из
  ответов `/api/songs`, `/api/songsdigests`, `/api/songshistory`,
  `/api/publications`, `/api/unpublications`. Поскольку фронт уже
  перестал их использовать (после FR-001 + US3), это безопасно. Размер
  ответа `/api/songsdigests` уменьшится на ~5 МБ (18 858 песен ×
  27 полей × ~10 байт).
- **Существующий JSON-парсинг на фронте** (Vuex `songsDigest`,
  `publications`, `unpublications`) не сломается: обращения к
  несуществующим полям вернут `undefined`, но в живом коде таких
  обращений нет (FR-001 + FR-016 + grep-аудит подтверждают).