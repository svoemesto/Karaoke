# Data Model: Удалить блок «Ссылки на просмотр» со страницы песни

> Phase 1 output для спеки `specs/142-remove-watch-links-block/`.
> Сгенерировано `/speckit.plan` 2026-08-04.

## Резюме

**Изменений в модели данных нет.** Это удаляющая UI-правка, не затрагивающая
ни PostgreSQL-схему, ни DTO, ни SyncRegistry, ни Model-классы Kotlin.

## Сущности (без изменений)

| Сущность | Где живёт | Изменение | Обоснование |
|----------|-----------|-----------|-------------|
| `tbl_settings` (Songs) | PostgreSQL | **N/A** | Поля `link*` (например, `link_sponsr_play`, `link_dzen_karaoke`, `link_vk_lyrics`, `link_max_tabs`, `link_tg_chords` и т.п.) **остаются** |
| `Song` (Kotlin) | `karaoke-app/.../model/Song.kt` | **N/A** | Геттеры/сеттеры `linkSponsrPlay`, `linkDzenKaraoke`, … остаются без изменений |
| `SettingsDTO` | `karaoke-app/.../dto/SettingsDTO.kt` | **N/A** | JSON-поля `link*` остаются в ответе `/api/public/song` |
| `SettingsDTOdigest` | `karaoke-app/.../dto/SettingsDTOdigest.kt` | **N/A** | Не затрагивается |
| `SyncRegistry.all` | `karaoke-app/.../sync/SyncTarget.kt` | **N/A** | Список sync-целей не меняется; `tbl_settings` уже синхронизируется LOCAL↔SERVER, и это остаётся |
| `recordhash`-триггер на `tbl_settings` | миграция `deploy/karaoke-db/` | **N/A** | Триггер продолжает работать; изменения полей нет → md5 не меняется |

## Контракт между backend и frontend (без изменений)

```
GET /api/public/song?id=<id>     // публичный API, отдаёт JSON
→ {
    ...,
    "linkSponsrPlay":   "...",
    "linkDzenKaraoke":  "...",
    "linkDzenLyrics":   "...",
    "linkDzenTabs":     "...",
    "linkDzenChords":   "...",
    "linkMaxKaraoke":   "...",
    ...
    "linkVkKaraoke":    "...",
    "linkTgLyrics":     "...",
    "linkTgTabs":       "...",
    "linkTgChords":     "...",
    ...
  }
```

Все `link*`-поля **продолжают возвращаться** как и раньше — фронтенд продолжит
принимать их в JSON; правка состоит в том, что `SongView.vue` просто
перестаёт их рендерить. Это упрощает возможный откат: вернуть DOM-блок
можно по `git revert`, без перегенерации API.

## Vue store / компоненты (без изменений схемы)

| Артефакт | Изменение |
|----------|-----------|
| `PlatformLink.vue` | **N/A** — сохраняется как есть |
| `SearchView.vue`, `ZakromaView.vue` | **N/A** — продолжают использовать `PlatformLink` |
| Vuex-модули `songs`, `stats`, и т.п. | **N/A** — никаких новых полей/геттеров |
| Vue Router-роуты | **N/A** — путь `/song?id=<id>` остаётся |

## Что УДАЛЯЕТСЯ (только presentation-слой)

В **только одном файле** `karaoke-public/src/views/SongView.vue`:

| Слой | Что удаляется |
|------|---------------|
| `<template>` | Блок `<div v-if="currentSong.onAir" class="km-links-card">` со всем содержимым (~133 строки: 5 групп × 4-5 `PlatformLink` ссылок + `km-links-title` + `km-links-grid`). |
| `<style scoped>` | CSS-правила `.km-links-card`, `.km-links-title`, `.km-links-grid`, `.km-link-group`, `.km-link-label`, `.km-link-icons` + адаптивное `.km-links-grid { gap: 0.5rem }` в `@media` (предварительно проверив, что ничего из этого вне удаляемого блока не используется — `grep -n "km-link" karaoke-public/src/views/SongView.vue`). |
| `<script>` | `import PlatformLink from '../components/PlatformLink.vue'` и регистрация в `components: { PlatformLink, ... }` (после удаления блока — других использований в файле нет). |

## Валидации (без изменений)

Поскольку форма с пользовательским вводом не затрагивается, валидации
(JSDoc-типы, PropTypes, Vue Prop validators) не нужны. Компонент
`PlatformLink.vue` сохраняет свои текущие prop-контракты.

## Edge-cases данных (без изменений)

| Сценарий | Поведение |
|----------|-----------|
| Песня с `tags = "SKIP"` | Блок и раньше скрывался через `v-if="currentSong.onAir"` (SKIP не влияет на `onAir` напрямую, но песня в SKIP обычно не в эфире). После правки — то же поведение: блока нет, рендерится стандартная страница. |
| Поле `linkSponsrPlay = null/""` | `PlatformLink.vue` уже умеет скрывать пустые ссылки внутри себя. После правки `PlatformLink` не вызывается из `SongView.vue` — нерелевантно. Поведение в `SearchView`/`ZakromaView` не меняется. |
| Песня с `onAir=false` | Блок и раньше не показывался. После правки — без изменений. |

## Out of data scope

- Изменения схемы `tbl_settings` (миграции) — **запрещены** (фича не про данные).
- Изменения `recordhash`-триггера — **запрещены** (нет изменений колонок → md5 стабилен).
- Изменения `SyncRegistry` — **запрещены** (`tbl_settings` уже синхронизируется).
- Удаление/чистка самих `link*`-полей в БД — **запрещено** (спека, Out of Scope).

## Итог

Data-model пуст в смысле «нет изменений». Этот документ существует для
явной фиксации того, **что НЕ меняется**, чтобы ревьюер/PR-check не искал
изменений в БД/DTO и не удивлялся их отсутствию.
