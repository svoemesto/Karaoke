# Research: Упрощение PublishTableBodyTd + полная чистка DTO от processColor*

**Phase**: 0 (Outline & Research)
**Branch**: `160-publish-body-td-remove-six-columns`
**Spec**: [`./spec.md`](./spec.md)
**Date**: 2026-08-06

Цель: подтвердить технический контекст, зафиксировать конкретные позиции кода для surgical edit и закрыть все `NEEDS CLARIFICATION` ещё до Phase 1.

---

## 1. Технологический контекст (подтверждённый)

| Слой | Решение | Источник в репо |
|---|---|---|
| Backend язык | Kotlin 1.x, JDK 17 | `karaoke-app/build.gradle.kts` |
| Backend фреймворк | Spring Boot 2.x/3.x (DTO → JSON через Jackson) | constitution §Технологический стек |
| БД | PostgreSQL через сырой JDBC (`KaraokeConnection`) | constitution Principle II NON-NEGOTIABLE |
| Diff LOCAL↔SERVER | md5 от recordhash + reflection-diff в `KaraokeDbTable.save()` | `KaraokeDbTable.kt`, `Song.kt` геттеры ~6816–6966 |
| Frontend (admin) | Vue 3 + Vite + Vuex + Bootstrap-vue-next | `webvue3/` |
| Frontend (public) | Vue 3 + Vite + Bootstrap 5 | `karaoke-public/` (0 ссылок на `processColor*`) |
| Backend шаблоны | Thymeleaf (`karaoke-app/src/main/resources/templates/*.html`) | используют `${song.processColorX}` |
| Storage | MinIO (S3-compatible), объектное хранилище | constitution §Технологический стек |
| Build | Gradle multi-module + npm | `AGENTS.md` → «Сборка и запуск» |
| Tests | Нет CI-тестов; существующие в `karaoke-app/src/test` — `@Disabled` интеграционные | `AGENTS.md` → «Тесты» |

---

## 2. Карта изменяемых файлов (точные позиции)

### 2.1. Backend (Kotlin)

| Файл | Диапазон строк | Действие |
|---|---|---|
| `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/SongDTO.kt` | **68–95** (data class fields) | **Удалить 27 полей `processColor*`**, оставить ровно `processColorPlayerDemo` |
| `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/SongDTO.kt` | **353–380** (`toDtoDigest()` assignments) | **Удалить 27 строк присваивания**, оставить `processColorPlayerDemo = processColorPlayerDemo,` |
| `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/SongDTOdigest.kt` | **61–88** (data class fields) | **Удалить 27 полей `processColor*`**, оставить ровно `processColorPlayerDemo` |
| `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt` | **8232–8259** (`toDTO()` assignments) | **Удалить 27 строк присваивания**, оставить `processColorPlayerDemo = processColorPlayerDemo,` |

**Не трогать:**
- `Song.kt` геттеры `processColor*` (~2454–2538) — нужны для `diff` и `Publication`.
- `Song.kt` diff-логика LOCAL↔SERVER (~6816–6966) — Principle III NON-NEGOTIABLE.
- `Publication.kt` строки 249+ — использует `publishX!!.processColorMeltLyrics` (геттеры `Song`).
- `SyncTarget.kt:215` (виртуальные diff-поля `processColorXxx`) — ссылка в комментарии.

### 2.2. Frontend (`webvue3`)

| Файл | Диапазон строк | Действие |
|---|---|---|
| `webvue3/src/components/Publish/components/PublishTableBodyTd.vue` | **17–48** (template: 6 `<div class="publish-column">`) | Удалить 6 блоков-колонок |
| `webvue3/src/components/Publish/components/PublishTableBodyTd.vue` | **97–153** (`computed.processColor*`) | Удалить 20 computed-геттеров |
| `webvue3/src/components/Publish/components/PublishTableBodyTd.vue` | **163–171** (`methods.dblClick*`) | Удалить 3 метода `dblClickKaraoke/Lyrics/Chords` |
| `webvue3/src/components/Publish/components/PublishTableBodyTd.vue` | **183–184** (CSS `.publish` min/max-width) | Изменить `200px` → `210px` |
| `webvue3/src/components/Publish/components/PublishTableBodyTd.vue` | **190–192** (CSS `.publish-name` width) | Изменить `150px` → `210px` |
| `webvue3/src/components/Publish/components/PublishTableBodyTd.vue` | **207–232** (CSS `.publish-column*`) | Удалить 3 правила |
| `webvue3/src/components/Publish/components/PublishTableBodyTd.vue` | **233–235** (CSS `.empty` width) | Изменить `200px` → `210px` |
| `webvue3/src/components/Songs/edit/SongEdit.vue` | **2297–2328** (4 PLAY-кнопки) | Удалить `:style="{ backgroundColor: song.processColorMelt* }"` (4 строки) |

**Не трогать:**
- `webvue3/src/components/Songs/SongsTable.vue:329` — живой потребитель `processColorPlayerDemo`.
- `webvue3/src/components/Songs/SongsTable.vue:362, 369, 376, 383` — **закомментированные** блоки `<!-- :style="...data.item.processColorPlLyrics..." -->`. После PR поля в DTO не будет, но блок закомментирован — не выполнится; out of scope.
- `karaoke-public/` — 0 ссылок на `processColor*`.
- `karaoke-web/` — 0 ссылок на `processColor*`.

### 2.3. Backend шаблоны (Thymeleaf + JS)

Используют `processColor*` через `${song.processColorX}` (Thymeleaf) и `data.processColorX` (JS после `fetch('/song/{id}')`):

| Файл | Строки | Действие |
|---|---|---|
| `karaoke-app/src/main/resources/templates/publications.html` | 737, 744, 762, 768, 774, 780, 786, 792, 798, 804, 874–883 | **Не трогать** — привязаны к `/song/{id}` (raw `Song`) |
| `karaoke-app/src/main/resources/templates/unpublications.html` | 220, 226, 232, 314–323 | **Не трогать** — аналогично |
| `karaoke-app/src/main/resources/templates/songs.html` | 1894, 1898, 1902, 1906, 1910, 1914, 1918, 1922, 2066, 2067, 2313, 2314, 2315 | **Не трогать** — legacy Thymeleaf, raw `Song` |
| `karaoke-app/src/main/resources/templates/songs2.html` | 2268, 2272, 2276, 2280, 2284, 2288, 2292, 2296 | **Не трогать** — legacy Thymeleaf, raw `Song` |
| `karaoke-app/src/main/resources/templates/area_left_column.html` | 323, 330, 337, 345, 353, 361, 368, 382, 389 | **Не трогать** — raw `Song` через Thymeleaf `${song.processColorX}` |

Все шаблоны получают данные через `MainController` (`/song/{id}` → raw `Song`) или напрямую через Thymeleaf `${song.X}` (где `song` — атрибут модели `Song`). Поскольку `Song.kt` геттеры сохраняются (FR-014), эти шаблоны продолжают работать без изменений.

### 2.4. Controller / API endpoints

`karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt` использует `toDTO()` / `toDtoDigest()` в строках 2132, 2142, 2168, 2186, 2203, 2499, 2651, 2819, 2868, 3175, 6073, 6291, 6482, 6501, 7561, 7627, 7644. После нашего PR компиляция сохранится (именованные параметры `SongDTO`/`SongDTOdigest` уменьшаются вместе с полями). Поведение `/api/songs`, `/api/songsdigests`, `/api/songshistory`, `/api/publications`, `/api/unpublications` идентично — меняется только объём JSON.

---

## 3. Зависимости и best practices (Phase 0)

### 3.1. Kotlin data class: порядок полей = порядок параметров

`SongDTO` и `SongDTOdigest` — `data class` с именованными параметрами. Удаление поля и соответствующего присваивания в `toDTO()`/`toDtoDigest()` идёт парно; порядок остальных полей менять нельзя (для читаемости и бинарной совместимости с возможной сериализацией через Java Serializable).

**Decision**: оставить порядок оставшихся полей как есть (не пересортировывать), только удалить строки.

**Rationale**: минимальный diff = меньше риск регрессии; читаемость diff сохраняется.

**Alternatives**: пересортировать поля (например, перенести `processColorPlayerDemo` из строки 75 в конец processColor*-блока) — отклонено, косметика, не оправдана.

### 3.2. Vue computed properties и `:style` после удаления

В `PublishTableBodyTd.vue` удаление computed `processColor*` не вызывает TypeScript/Vue-ошибок, потому что `:style="processColorXxx"` тоже удаляется в той же операции (template lines 17–48). Линтер поймает «unused computed» если бы они остались, но они удаляются полностью.

**Decision**: удалить computed-блоки целиком (97–153).

**Rationale**: 20 computed не имеют потребителей после удаления template — Vue runtime warning.

### 3.3. CSS `width: Npx` для таблиц Bootstrap-vue-next

Согласно `AGENTS.md` Q&A «Таблицы `karaoke-public`: `table-layout: fixed` требует явной `width: Npx`». Для `webvue3` (Bootstrap-vue-next) правило то же — `width` обязательно, иначе flex-схлопывание.

**Decision**: явно `width: 210px` для `.publish-name`; явно `min-width/max-width: 210px` для `.publish`; явно `width: 210px` для `.empty`.

**Rationale**: единый паттерн с `karaoke-public`, предотвращает collapse-эффект при длинных именах.

### 3.4. Шапка `PublishTableHead.vue` — намеренное рассогласование

Шапка остаётся 200 px, тело становится 210 px. Рассогласование на 10 px по правому краю подписей колонок — явное решение пользователя (FR-008 / US1 / Assumptions «out of scope»).

**Decision**: НЕ править `PublishTableHead.vue`.

**Rationale**: явное решение пользователя, локализация визуальной проблемы в этом PR.

### 3.5. SSE diff события для `processColorMelt*`

`karaoke-app/src/main/kotlin/.../sync/SyncTarget.kt:262` ссылается на `processColorXxx` в комментарии — не код. JS в `publications.html:737, 744` обрабатывает `case 'processColorMeltLyrics':` — событие приходит из SSE-канала при изменении песни. Поскольку `Song.kt:diff` использует геттеры (не DTO-поля), событие по-прежнему публикуется с правильным именем.

**Decision**: НЕ трогать SSE-логику и JS в шаблонах.

**Rationale**: данные берутся из `/song/{id}` (raw `Song`), не из DTO.

### 3.6. JSON payload — расчёт экономии

`/api/songsdigests` отдаёт массив `SongDTOdigest`. С `n = 18858` песен:
- 27 полей × ~10 байт/JSON-имя-строка × 18858 ≈ **~5 МБ** на полный ответ.
- С 27 полей остаётся 1 (`processColorPlayerDemo`) → экономия ~95%.
- Время сериализации и парсинга JSON сократится пропорционально.

**Decision**: не вводить `JsonInclude.NON_NULL` или другие оптимизации — естественное удаление полей само по себе даёт нужный эффект.

**Rationale**: явное удаление = читаемая спецификация контракта; `JsonInclude` — неявное поведение, хуже для документирования.

---

## 4. NEEDS CLARIFICATION

**Все 5 кандидатов из clarify-скана закрыты дефолтами в спеке**, поэтому **0 `[NEEDS CLARIFICATION]`** в `research.md`:

| Кандидат | Решение |
|---|---|
| Позиция `processColorPlayerDemo` в `SongDTO` | Оставить как есть (минимальный diff) |
| Чистить ли закомментированные `<template #cell(flagPlLyrics)>` | НЕТ, out of scope |
| Удалять ли мёртвый геттер `processColorBoostyFiles` | НЕТ, out of scope |
| Координация с внешними потребителями API | grep подтвердил отсутствие; пользователь принял |
| Wording SC-005 (inline vs computed style) | Inline атрибут `:style` — DevTools → Elements → визуально |

---

## 5. Открытые риски (для отслеживания в tasks.md)

1. **Lint baseline**: удаление 27 строк из Vue-компонента может изменить ESLint-baseline (`webvue3/.eslint-baseline.json`). После PR прогнать `./tools/check-eslint-baseline.sh` и `npm run lint:check`. Если новых нарушений нет — baseline не меняется; если есть — добавить в baseline в том же PR (FR-007 constitution).
2. **KDoc/JSDoc coverage**: компонент `PublishTableBodyTd.vue` имеет JSDoc-блок (строки 57–61), проверяется `./tools/check-jsdoc-coverage.sh webvue3`. Удаление computed-блоков не затронет JSDoc (он на `export default`, не на каждом computed).
3. **Визуальная регрессия**: рассогласование 210 vs 200 px в шапке — проверить SC-008 на dev-стенде.

---

## 6. Сводка для Phase 1

Phase 1 (`data-model.md`, `contracts/`, `quickstart.md`) уже может стартовать — все технические решения приняты, NEEDS CLARIFICATION = 0, файлы и позиции зафиксированы.

**Constitution Check** будет оценён в `plan.md` (Phase 0 gate): все принципы NON-NEGOTIABLE проходят (II — сырой JDBC не затрагивается, III — diff не затрагивается, VI — линтеры/KDoc/JSDoc в FR-006/FR-007/FR-009 покрыты, VIII — секреты не затрагиваются).
