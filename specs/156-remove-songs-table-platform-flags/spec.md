# Feature Specification: Удалить из таблицы «Песни» 18 столбцов-флагов публикации

**Feature Branch**: `156-remove-songs-table-platform-flags`
**Created**: 2026-08-06
**Status**: Draft
**Input**: User description: "Админка, компонент \"Песни\". Убрать из таблицы столбцы: SP, VG, ZL, ZK, ZC, ZM, VL, VK, VC, VM, TL, TK, TC, TM, ML, MK, MC, MM"

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Администратор открывает таблицу «Песни» и видит компактный список (Priority: P1)

Администратор заходит в раздел «Песни» админки `webvue3`, чтобы оценить состояние коллекции и найти нужную песню. Таблица должна показывать только значимые для ежедневной работы колонки, без шумовых флагов публикации по платформам.

**Why this priority**: Это основная ежедневная точка входа администратора. 18 узких столбцов-флагов (по 20px каждый ≈ 360px ширины) визуально перегружают таблицу и затрудняют сканирование списка, особенно при просмотре на ноутбуках и экранах с разрешением ≤1440px. Удаление — главное улучшение UX.

**Independent Test**: Открыть `http://localhost:5173/songs`, убедиться, что в шапке таблицы отсутствуют столбцы SP/VG/ZL/ZK/ZC/ZM/VL/VK/VC/VM/TL/TK/TC/TM/ML/MK/MC/MM; остальные столбцы отображаются без изменений.

**Acceptance Scenarios**:
1. **Given** администратор открыл страницу «Песни» админки, **When** таблица отрисовывается, **Then** в шапке видны колонки ID / Композиция / Исполнитель / Год / Альбом / № / Дата / Время / Tags / Status / V / BOO / Редактор / ▶ / ▶ / DE / TG / FR и столбцов SP/VG/ZL/ZK/ZC/ZM/VL/VK/VC/VM/TL/TK/TC/TM/ML/MK/MC/MM среди них нет.
2. **Given** администратор открыл страницу «Песни» админки, **When** таблица отрисовывается, **Then** ширина таблицы уменьшается на ≈ 360px по сравнению с текущим состоянием, остальные колонки занимают высвобожденное место пропорционально.
3. **Given** администратор кликает по строке песни, **When** строка раскрывается или открывается в редакторе, **Then** поля песни `flagSponsr`, `flagVk`, `flagDzen*`, `flagVkLyrics/Karaoke`, `flagTelegram*`, `flagMax*` сохраняются в БД как прежде (удаление только визуальное).
4. **Given** в админке есть отдельный раздел «Публикации» (`/publications`), **When** администратор открывает таблицу публикаций, **Then** там по-прежнему видны столбцы публикации (эта таблица не затрагивается).

### User Story 2 — Администратор сортирует/фильтрует таблицу «Песни» (Priority: P2)

Администратор использует фильтры в шапке таблицы для быстрого поиска песен. После удаления столбцов фильтрация по полям, которые были видны только в этих столбцах, должна продолжать работать через внутренние фильтры (если они там были), а в UI фильтра эти поля больше не показываются.

**Why this priority**: Фильтры в шапке таблицы — это второй по частоте сценарий (после скролла/скана). Если фильтр-инпут «уезжает» вместе со столбцом, это нормальное поведение (полей больше нет). Если фильтр живёт отдельно от шапки (например, в модальном окне `SongsFilterModal.vue`), то и там поле должно быть удалено.

**Independent Test**: Открыть модальное окно фильтров (если оно есть) и шапку таблицы, убедиться, что поля `flagSponsr`, `flagVk`, `flagDzenLyrics`, `flagDzenKaraoke`, `flagVkLyrics`, `flagVkKaraoke`, `flagTelegramLyrics`, `flagTelegramKaraoke`, `flagMaxLyrics`, `flagMaxKaraoke` отсутствуют в списке доступных полей для фильтрации.

**Acceptance Scenarios**:
1. **Given** администратор нажал кнопку «Фильтры» в шапке таблицы, **When** открылось модальное окно фильтров, **Then** в списке колонок фильтра отсутствуют удалённые поля.
2. **Given** администратор обновил страницу после удаления, **When** Vuex-state `Songs/fieldSongParams` загружается, **Then** массив содержит только оставшиеся поля.

### User Story 3 — Разработчик/AI-агент собирает webvue3 без ошибок (Priority: P3)

Разработчик после внесения правок запускает `npm run build` в `webvue3/`. Сборка должна проходить без ошибок и предупреждений ESLint о неиспользуемых импортах/переменных (если правка выполнена аккуратно — вместе со столбцами удаляются и привязанные к ним CSS-классы и методы воспроизведения).

**Why this priority**: Техническое здоровье кодовой базы. Без этой проверки можно оставить «мёртвый код» (стили, методы), который через год придётся вычищать отдельно.

**Independent Test**: Выполнить `cd webvue3 && npm run build && npm run lint:check` — обе команды завершаются с кодом 0, новых предупреждений ESLint не появляется.

**Acceptance Scenarios**:
1. **Given** правки внесены, **When** запускается `npm run build`, **Then** сборка успешна (exit 0), размер бандла `webvue3` уменьшается на ≥ 1KB (удалены 18 строк шаблонов и стилей).
2. **Given** правки внесены, **When** запускается `npm run lint:check`, **Then** нет новых ошибок (`no-unused-vars`, `no-unreachable` и т.п.).

### Edge Cases

- **Сохранение состояния в localStorage / Vuex**: если ширина столбцов или состояние фильтров персистится (per AGENTS.md «Персистентность страницы пагинации в webvue3»), необходимо убедиться, что старые сохранённые значения для удалённых полей не вызывают ошибок при загрузке.
- **Ширина таблицы на узких экранах**: если все удалённые столбцы суммарно занимают 360px, оставшиеся колонки на 1280px экране помещаются без горизонтального скролла (проверить на типичных разрешениях 1280×800 / 1366×768 / 1920×1080).
- **Горизонтальный скролл**: текущая таблица имеет много колонок, и горизонтальный скролл уже может присутствовать — нужно убедиться, что после удаления 18 столбцов прокрутка по-прежнему работает корректно для оставшихся.
- **Зависимость от store**: `fieldSongParams[]` в Vuex-store содержит определения 10 из 18 удаляемых полей — если они не используются другими компонентами, их тоже надо удалить, чтобы не плодить «мёртвые» декларации.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: В таблице `SongsTable.vue` (`webvue3/src/components/Songs/SongsTable.vue`) MUST быть удалены 18 определений столбцов из массива `fields[]` со следующими ключами: `flagSponsr`, `flagVk`, `flagDzenLyrics`, `flagDzenKaraoke`, `flagDzenChords`, `flagDzenMelody`, `flagVkLyrics`, `flagVkKaraoke`, `flagVkChords`, `flagVkMelody`, `flagTelegramLyrics`, `flagTelegramKaraoke`, `flagTelegramChords`, `flagTelegramMelody`, `flagMaxLyrics`, `flagMaxKaraoke`, `flagMaxChords`, `flagMaxMelody`.
- **FR-002**: В шаблоне `SongsTable.vue` MUST быть удалены 18 ячеек-шаблонов `<template #cell(flagX)="data">` для тех же ключей, что и в FR-001.
- **FR-003**: В файле `SongsTable.vue` SHOULD быть удалены CSS-стили для классов `.fld-flag-sponsr`, `.fld-flag-vk`, `.fld-flag-dzen-lyrics`, `.fld-flag-dzen-karaoke`, `.fld-flag-dzen-chords`, `.fld-flag-dzen-melody`, `.fld-flag-vk-lyrics`, `.fld-flag-vk-karaoke`, `.fld-flag-vk-chords`, `.fld-flag-vk-melody`, `.fld-flag-tg-lyrics`, `.fld-flag-tg-karaoke`, `.fld-flag-tg-chords`, `.fld-flag-tg-melody`, `.fld-flag-max-lyrics`, `.fld-flag-max-karaoke`, `.fld-flag-max-chords`, `.fld-flag-max-melody` (если эти классы не используются где-то ещё).
- **FR-004**: В Vuex-store `webvue3/src/components/Songs/store.js` в массиве `state.fieldSongParams[]` SHOULD быть удалены 10 определений со следующими именами: `flagSponsr`, `flagVk`, `flagDzenLyrics`, `flagDzenKaraoke`, `flagVkLyrics`, `flagVkKaraoke`, `flagTelegramLyrics`, `flagTelegramKaraoke`, `flagMaxLyrics`, `flagMaxKaraoke`. Определения для `flagDzenChords`, `flagDzenMelody`, `flagVkChords`, `flagVkMelody`, `flagTelegramChords`, `flagTelegramMelody`, `flagMaxChords`, `flagMaxMelody` в `fieldSongParams[]` уже отсутствуют — удалять нечего.
- **FR-005**: В файле `SongsTable.vue` SHOULD быть удалены методы воспроизведения `playLyrics(id)`, `playKaraoke(id)`, `playChords(id)`, `playTabs(id)`, если они не вызываются где-то ещё в этом компоненте. Метод `playDemo(id)` MUST остаться (используется ячейкой `flagPlayerDemo`, которая не удаляется).
- **FR-006**: Бэкенд-логика, поля БД (`flag_sponsr`, `flag_vk`, `flag_dzen_*`, `flag_vk_*`, `flag_telegram_*`, `flag_max_*`), DTO и `processColor*` свойства MUST NOT быть изменены — данные продолжают вычисляться и сохраняться как прежде. Удаление чисто визуальное.
- **FR-007**: Компонент `Publish/components/PublishTableBodyTd.vue` (таблица публикаций), `Songs/edit/SongEdit.vue` (редактор песни, кнопки PLAY LYRICS / PLAY KARAOKE / PLAY CHORDS / PLAY TABS / PLAY DEMO), Vuex-геттеры `playLyrics`, `playKaraoke`, `playChords`, `playTabs`, `playDemo` MUST NOT быть затронуты этой фичей — они используются в других местах и не должны быть удалены.
- **FR-008**: Per-feature документ `docs/features/songs-table.md` MUST быть обновлён в том же PR — удалить из него описание/перечисление 18 удаляемых столбцов (или явно отметить их как удалённые) согласно FR-009 Конституции.
- **FR-009**: Сборка `webvue3` MUST проходить успешно: `npm run build` (exit 0), `npm run lint:check` (exit 0), без новых ESLint-ошибок и без новых предупреждений baseline.
- **FR-010**: Шапка таблицы MUST сохранить оставшиеся 18 столбцов в порядке: ID, Композиция, Исполнитель, Год, Альбом, №, Дата, Время, Tags, Status, V, BOO, Редактор, ▶ (player), ▶ (playerDemo), DE (flagPlayerDemo), TG (telegramPublish), FR (flagFree).

### Key Entities *(include if feature involves data)*

- **`SongsTable.vue`**: Vue Single-File Component, отображающий таблицу песен в админке `webvue3`. Содержит определения столбцов (`fields[]`), шаблоны ячеек (`<template #cell(flagX)>`), CSS-стили (`.fld-flag-*`), методы воспроизведения (`playLyrics`/`playKaraoke`/`playChords`/`playTabs`/`playDemo`). Изменяется этой фичей.
- **`store.js` (Songs)**: Vuex-модуль для раздела «Песни», содержит `state.fieldSongParams[]` — массив описаний колонок для фильтра/UI. Изменяется этой фичей.
- **`Songs/filter/SongsFilterModal.vue`**: компонент модального окна фильтров. Должен продолжать работать после удаления полей из `fieldSongParams[]` (не затрагивается напрямую).
- **`Publish/components/PublishTableBodyTd.vue`**: компонент ячейки таблицы публикаций. Использует те же `processColorSponsr`/`processColorVk` и методы `playKaraoke`/`playLyrics`/`playChords`. **Не** затрагивается фичей.
- **`Songs/edit/SongEdit.vue`**: редактор песни, кнопки PLAY. **Не** затрагивается фичей.
- **`docs/features/songs-table.md`**: per-feature документация подсистемы «Таблица песен». Обновляется в том же PR (FR-009).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: В шапке таблицы «Песни» после сборки и запуска отсутствуют 18 перечисленных столбцов — это проверяется визуально или через инспекцию DOM (`.b-table` / `thead th`): общее число ячеек шапки ≤ 18 (точное число зависит от того, какие ещё колонки будут сохранены).
- **SC-002**: Ширина таблицы уменьшается на ≈ 360px (18 × 20px), что подтверждается измерением DOM `getBoundingClientRect()` шапки до и после.
- **SC-003**: Сборка `npm run build` в `webvue3` завершается с exit-кодом 0; размер итогового JS-чанка уменьшается на ≥ 1KB (удалено ~250 строк шаблонов и стилей).
- **SC-004**: `npm run lint:check` в `webvue3` завершается с exit-кодом 0; количество новых предупреждений ESLint = 0; baseline (`webvue3/.eslint-baseline.json`) не увеличивается.
- **SC-005**: Тестовый ручной сценарий (см. `Acceptance Scenarios` User Story 1) проходит: администратор открывает `/songs`, видит таблицу без удалённых столбцов, ширина меньше прежней, остальные данные отображаются корректно.
- **SC-006**: Раздел «Публикации» (`/publications`) и редактор песни (`/songs/:id/edit`) работают без регрессий — `flagSponsr`/`flagVk`/etc. продолжают отображаться там, где они нужны, и методы `playLyrics`/`playKaraoke`/`playChords`/`playTabs`/`playDemo` продолжают работать.
- **SC-007**: Документ `docs/features/songs-table.md` обновлён в том же PR: описание 18 удаляемых столбцов удалено или явно помечено как удалённые, добавлена запись в changelog/architecture-notes.md о PR с датой.

## Assumptions

- **Объём правки только UI**: пользователь сказал «Убрать из таблицы столбцы» — это трактуется как визуальное скрытие в таблице песен админки, без удаления полей из БД, DTO или бэкенд-логики. Альтернативная интерпретация («очистить БД от этих полей») отвергнута как слишком деструктивная — данные о публикациях могут быть востребованы в других разделах (`/publications`, отчёты).
- **Не трогаем `Publish`**: компонент таблицы публикаций `Publish/components/PublishTableBodyTd.vue` использует те же `processColor*` свойства и методы `play*`. Эта таблица показывает публикации (где они были сделаны), а таблица песен — флаги публикации как атрибут самой песни. Концептуально это разные представления, поэтому удаление в одном не подразумевает удаление в другом.
- **Порядок оставшихся столбцов**: определяется существующим порядком в `fields[]` — порядок не меняется, только удаляются 18 позиций из середины массива.
- **Ширины не пересчитываются**: остальные колонки остаются с теми же `minWidth`/`maxWidth`, что и сейчас (фиксированная ширина через `table-layout: fixed` per AGENTS.md). Горизонтальная прокрутка продолжает работать как прежде.
- **Vuex-state `fieldSongParams[]` не имеет внешних потребителей**: было обнаружено, что геттер `getFieldSongParams` существует, но в текущем коде `webvue3` не используется другими компонентами напрямую (проверено через `grep -rn`). Если в будущем кто-то начнёт его использовать — нужно будет вернуть удалённые определения.
- **Метод `playDemo` остаётся**: ячейка `flagPlayerDemo` (DE) сохраняется и продолжает обрабатывать `@dblclick.left="playDemo(data.item.id)"`. Остальные методы `playLyrics`/`playKaraoke`/`playChords`/`playTabs` после удаления ячеек станут «мёртвым кодом» в этом компоненте, и SHOULD быть удалены.
- **Не нужно мигрировать локальные данные фильтров**: если в localStorage/Vuex у пользователя сохранены значения фильтров по удалённым полям — UI должен тихо их игнорировать (фильтр перестаёт находить такие записи, но ошибок не возникает).
