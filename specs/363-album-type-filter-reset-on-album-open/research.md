# Research: Сброс фильтра категории альбома при открытии песен конкретного альбома

**Feature**: [spec.md](./spec.md)
**Date**: 2026-09-10
**Branch**: `363-album-type-filter-reset-on-album-open`

## Контекст

Issue #78: при открытии конкретного альбома автора через `/zakroma/{id}?albumId=Y`, если тип альбома (например, `single`) скрыт в фильтре категорий (set `hiddenAlbumTypes` в `localStorage`), `visibleAlbums(zak)` исключает этот альбом → страница показывает пустоту. Пользователь не понимает, что произошло, и не может увидеть песни выбранного альбома.

## Решение (high-level)

В `karaoke-public/src/views/ZakromaView.vue`:

1. Ввести computed `effectiveHiddenAlbumTypes`, который возвращает `hiddenAlbumTypes` **минус** тип открытого альбома (если он скрыт).
2. `visibleAlbums(zak)` использовать `effectiveHiddenAlbumTypes` вместо `hiddenAlbumTypes`.
3. Блок `.km-album-type-filters` обернуть в `v-if="!selectedAlbumId"` (из `spec.md` Clarifications Q1).
4. `localStorage` остаётся источником правды; `hiddenAlbumTypes` (Set в `data()`) не меняется.

## Resolved unknowns (Phase 0)

В спеке нет [NEEDS CLARIFICATION], кроме UX-вопроса, закрытого в Stage 2:
- ✅ **Q1 (UI фильтра при auto-reset)** → «Скрыть весь фильтр-бар» через `v-if="!selectedAlbumId"` (см. `spec.md` → Clarifications).

Остальные решения (Decision / Rationale / Alternatives) ниже.

---

### Decision 1: Авто-сброс через computed, а не через watcher + мутацию `hiddenAlbumTypes`

**Decision**: Использовать `computed: effectiveHiddenAlbumTypes`, который читает `hiddenAlbumTypes` + `selectedAlbumId` + `zakroma` (для `albumType` нужного альбома).

**Rationale**:
- Computed реактивен — Vue сам пересчитает, когда меняется любой из 3-х входов (`hiddenAlbumTypes`, `selectedAlbumId`, `zakroma`).
- Не нужно хранить «снапшот оригинального состояния» — мы никогда не мутируем `hiddenAlbumTypes` в Set-е, не нужно откатывать.
- `localStorage` остаётся единственным источником правды для персистентного состояния.

**Alternatives considered**:
- **Watcher + мутация Set**: при появлении `?albumId=Y` — `hiddenAlbumTypes.delete(albumType)`; при уходе — `add`. Отвергнуто: нужно сохранять «оригинал» до мутации, добавляет state, риск рассинхронизации при race conditions.
- **Запись в `localStorage`**: испортит оригинальное значение, нарушит FR-003.
- **Изменение только `visibleAlbums()` через if-else**: менее явно, размазывает логику; хуже читается.

---

### Decision 2: Читать `albumType` открытого альбома из `zakroma` store, а не дёргать бэк за альбомом

**Decision**: Использовать `zakroma` из Vuex-стора (`mapGetters('zakroma', ['zakroma'])`), найти в нём нужный `zak.albums[].albumId == selectedAlbumId`, взять его `albumType`.

**Rationale**:
- `zakroma` уже загружен на странице `/zakroma/{id}` (Pass 357: `loadZakromaStream` грузит ВСЕ альбомы автора с `albumType` в payload).
- Бэк уже всё отдал — нет смысла делать ещё один HTTP-запрос.
- Если `zakroma` ещё не загружен (direct URL на `/zakroma/{id}?albumId=Y`), computed вернёт `null` → `effectiveHiddenAlbumTypes` ≡ `hiddenAlbumTypes` → фильтр работает «как раньше». Как только `zakroma` приходит — фильтр пересчитывается.

**Alternatives considered**:
- **Отдельный запрос `/api/public/albums/{id}`**: лишний round-trip; дублирует данные; race conditions с приходом `zakroma`.
- **Передавать `albumType` через query `?albumType=`**: anti-pattern — два источника правды.

---

### Decision 3: Не учитывать «другие альбомы того же типа, но скрытые»

**Decision**: Auto-reset открывает ровно ОДИН тип — тот, что у открытого альбома. Если у автора есть ещё 5 синглов и все скрыты — после открытия одного сингла на странице песен автора остальные 4 по-прежнему скрыты.

**Rationale**:
- Юзер явно кликнул на ОДИН альбом — мы уважаем этот выбор и временно делаем доступным именно его.
- Если открыть ВСЕ альбомы того же типа — мы выходим за scope «посмотреть песни конкретного альбома», лезем в UX фильтра.
- Минимальный риск сюрприза: юзер ожидает, что ОДИН альбом будет виден, остальные скрыты — это консистентно.

**Alternatives considered**:
- **Auto-reset всех альбомов того же типа**: шире, чем просит issue #78; может раздражать («я хотел скрыть синглы, а они снова видны»).
- **Показывать только альбом, на который кликнули, не учитывая `zakroma.albums`**: требует отдельной ветки рендера; сильно ломает Pass 357.

---

### Decision 4: `v-if="!selectedAlbumId"` для фильтр-бара

**Decision**: Полностью скрыть `.km-album-type-filters` (Pass 012, FR-025/027), когда `selectedAlbumId != null`.

**Rationale**:
- Закрывает Clarifications Q1: «Скрыть весь фильтр-бар».
- Невозможно случайно править фильтр во время просмотра конкретного альбома.
- Минимальный CSS-импакт — фильтр-бар уже обёрнут в `v-if` (см. `ZakromaView.vue` — `zakromaAlbumTypeCounts` возвращает `[]` когда `displayedZakroma.length !== 1`, и шаблон на 60-70 строке проверяет `v-if`).

**Alternatives considered**:
- **Disabled buttons**: оставляет шум в UI.
- **Подсветка активного чипа**: требует тултип-объяснения — anti-pattern.
- **`v-show`** вместо `v-if`: скрывает визуально, но держит в DOM; зачем.

---

### Decision 5: Тесты — только ручные

**Decision**: Никаких unit/integration-тестов; проверка — DevTools reproducer.

**Rationale**:
- Проект `karaoke-public` не покрыт тестами (см. `AGENTS.md` → Тесты: в CI нет; `karaoke-app/src/test — @Disabled`).
- Фига чисто-фронтовая, state — локальный Set + computed; юнит-тест на Vue computed — overkill.
- Pass 359 (zakroma-albums-by-author) принят без тестов по той же причине.

**Alternatives considered**:
- **Cypress/E2E**: проект не использует Cypress. Не вводить новый стек ради 1 фичи.
- **Vitest с @vue/test-utils**: полезно в будущем, но это отдельный эпик (Pass 343+ backlog).

---

## Технические паттерны, использованные в проекте

Сверялся с существующими паттернами:

| Паттерн | Где | Что используем |
|---|---|---|
| Filter persistence через `localStorage` | `ZakromaView.vue:451–459` (Pass 012, FR-025) | Шаблон инициализации + watcher на изменение. |
| `Set<string>` для мульти-селекта | `hiddenAlbumTypes: new Set(...)` | Тот же контейнер, без изменений. |
| Computed `displayedZakroma` / `filteredZakroma` | `ZakromaView.vue:495–525` | Шаблон для нового computed. |
| `selectedAlbumId` через query | `ZakromaView.vue:575–578` | Прямо используем — он уже реактивный. |
| `mapGetters('zakroma', ['zakroma'])` | `ZakromaView.vue:463–470` | Доступ к Vuex-сторе. |
| v-if на расчётных блоках | `ZakromaView.vue:531–533` (`zakromaAlbumTypeCounts`) | Шаблон для скрытия фильтр-бара. |

Никаких новых паттернов изобретать не нужно.

## Knowledge references

Уже выполнено в `spec.md` → Knowledge References. Дополнительно использовано:
- `knowledge/system/frontend/filter-stores.md` — подтвердил, что в админке фильтры живут в Vuex (server-side key/value), а в публичном модуле — в `localStorage` (per-visitor). Контраст полезен для понимания, почему мы НЕ унифицируем.
- `karaoke-public/src/views/ZakromaView.vue:451–733` — прямой код паттерна, который правим.
- `specs/356-zakroma-albums-by-author/spec.md` — донорский паттерн URL-фильтрации.

## Changelog

- **2026-09-10**: Initial. Автор: agent (Karaoke).