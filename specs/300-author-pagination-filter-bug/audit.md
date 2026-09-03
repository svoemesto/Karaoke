# Аудит таблиц admin SPA на воспроизведение бага #50

**Дата**: 2026-09-03
**Связанная задача**: OpenProject #50 — «Неверное поведение на страницах автора после фильтра»
**Связанная спека**: [spec.md](spec.md)
**Метод**: статический grep по 3 критериям + ручная проверка манифеста хранилища (`store.js`)

## Метод аудита

Для каждой таблицы из `webvue3/src/components/<Entity>/<Entity>Table.vue` проверяются 3 критерия:

1. **`countRows`** — есть ли computed `countRows` в Table.vue (используется как `:total-rows` для `<b-pagination>`).
2. **`watch.countRows`** — есть ли watcher на `countRows` с защитой от `currentPage > totalPages`.
3. **`watch.currentPage`** — есть ли watcher на `currentPage` (для сохранения в store).

**Определение «баг воспроизводится»**:

> `(1 == true) AND (2 == false) AND (currentPage хранится в store)`

Если `countRows` есть, watcher на countRows отсутствует, но `currentPage` сохраняется в store — значит после фильтра, сужающего выборку, `currentPage` останется прежним → пустая страница.

## Сводная таблица

| View / Table | File | `countRows` | `watch.countRows` | `watch.currentPage` | Баг? | Фикс |
|--------------|------|-------------|-------------------|---------------------|------|------|
| Authors | `webvue3/src/components/Authors/AuthorsTable.vue` | ✅ | ❌ | ✅ | ✅ **ОСН. OP#50** | T006 |
| Albums | `webvue3/src/components/Albums/AlbumsTable.vue` | ✅ | ❌ | ✅ | ✅ | T007 |
| Pictures | `webvue3/src/components/Pictures/PicturesTable.vue` | ✅ | ❌ | ✅ | ✅ | T008 |
| SiteUsers | `webvue3/src/components/SiteUsers/SiteUsersTable.vue` | ✅ | ❌ | ✅ | ✅ | T009 |
| Dictionaries | `webvue3/src/components/Dictionaries/DictionariesTable.vue` | ✅ | ❌ | ✅ | ✅ | T010a (если будет) |
| Processes | `webvue3/src/components/Processes/ProcessesTable.vue` | ✅ | ❌ | ✅ | ✅ | T010b (если будет) |
| Properties | `webvue3/src/components/Properties/PropertiesTable.vue` | ✅ | ❌ | ✅ | ✅ | T010c (если будет) |
| Songs | `webvue3/src/components/Songs/SongsTable.vue` | ✅ (29 refs) | ✅ (line 998-1009) | ✅ | ❌ **эталон** | — |
| ShareLinks | `webvue3/src/components/ShareLinks/ShareLinksTable.vue` | ✅ | ✅ (line 265) | ✅ | ❌ **уже есть watcher** | — |
| Subscriptions | `webvue3/src/components/Subscriptions/SubscriptionsTable.vue` | ✅ | ✅ (line 256) | ✅ | ❌ **уже есть watcher** | — |
| News | `webvue3/src/components/News/NewsTable.vue` | ❌ (использует `totalCount` из store) | ❌ | ❌ (другой паттерн: `setNewsTarget` сбрасывает currentPage на1) | ❌ **другой паттерн** | — |
| ListeningHistory | `webvue3/src/components/ListeningHistory/ListeningHistoryTable.vue` | ✅ | ❌ | ❌ (но использует currentPage из store) | ⚠️ **требует отдельной проверки** | T010d |
| SitePlaylists | `webvue3/src/components/SitePlaylists/SitePlaylistsTable.vue` | ✅ | ❌ | ❌ (но использует currentPage из store) | ⚠️ **требует отдельной проверки** | T010e |
| Stats/TopListenedSongs | `webvue3/src/components/Stats/TopListenedSongsTable.vue` | ❌ | ❌ | ❌ | ❌ **нет пагинации** | — |
| Stats/TopUsers | `webvue3/src/components/Stats/TopUsersTable.vue` | ❌ | ❌ | ❌ | ❌ **нет пагинации** | — |

## Результат аудита

### Категория A — точно требуют фикса (MVP + расширение)

7 таблиц: **Authors, Albums, Pictures, SiteUsers, Dictionaries, Processes, Properties**. Все имеют `countRows` (бэкенд возвращает список ≤ perPage, surrogate для total), watcher на `countRows` отсутствует, но `currentPage` сохраняется → баг воспроизводится.

**Решение**: применить паттерн из `SongsTable.vue:998-1009` ко всем 7 таблицам. Это покрывает User Story 2 (P1) целиком.

### Категория B — уже имеют правильный паттерн

3 таблицы: **Songs, ShareLinks, Subscriptions**. Уже имеют watcher на countRows. Не требуют фикса.

### Категория C — другой правильный паттерн

1 таблица: **News**. Использует `setNewsTarget` + `totalCount` в state. Не подвержена данному багу.

### Категория D — требуют отдельной ручной проверки

2 таблицы: **ListeningHistory, SitePlaylists**. Имеют `countRows` + `currentPage` в store, но watcher на countRows отсутствует. **Возможно, баг воспроизводится**, но нужно проверить руками (требуется поднятое окружение с наполненной БД, чтобы воспроизвести сценарий). В этой задаче — не блокер; если проверка выявит баг — отдельная задача.

### Категория N/A — нет пагинации

2 таблицы: **TopListenedSongs, TopUsers**. Не задействованы.

## Финальное решение по объёму фикса

**MVP (закрывает OP#50)**: фикс **Authors** (1 таблица). Минимальный скоуп для закрытия исходного бага.

**Полный объём (закрывает User Story 2)**: фикс **всех 7 таблиц категории A** (Authors + Albums + Pictures + SiteUsers + Dictionaries + Processes + Properties).

**Расширение (опционально, отдельная задача)**: ручная проверка ListeningHistory, SitePlaylists, и если баг воспроизводится — фикс по тому же паттерну.

## Связь с задачами в tasks.md

| Audit | Задача |
|-------|--------|
| Authors | T006 |
| Albums | T007 |
| Pictures | T008 |
| SiteUsers | T009 |
| Dictionaries / Processes / Properties | T010 (опциональные подзадачи T010a/T010b/T010c) |
| ListeningHistory / SitePlaylists | T010d/T010e (отдельная задача, не блокирует PR) |

## Связанные документы

- `specs/300-author-pagination-filter-bug/spec.md` — спецификация (FR-008 ссылается на этот audit)
- `specs/300-author-pagination-filter-bug/research.md` — корневая причина и decisions
- `docs/features/pagination-filter-admin-tables.md` — per-feature документ с описанием фикса (создаётся в T005)
- OpenProject #50 — исходный баг-репорт