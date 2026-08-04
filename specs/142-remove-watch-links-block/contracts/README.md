# Contracts: Удалить блок «Ссылки на просмотр» со страницы песни

> Phase 1 output для спеки `specs/142-remove-watch-links-block/`.
> Сгенерировано `/speckit.plan` 2026-08-04.

## Резюме

**Контракты интерфейсов НЕ меняются.** Эта правка чисто-UI: один Vue-файл,
никаких изменений API, никаких изменений публичных типов/сигнатур.

Этот документ существует для фиксации того, что публичные контракты проекта
остаются неизменными, и для будущих ревьюеров/PR-check'ов — чтобы не
тратить время на поиск diff'ов в API/SQL/Store.

## Что НЕ меняется

### Backend → Frontend API (REST JSON)

| Endpoint | Изменение | Файл |
|----------|-----------|------|
| `POST /api/public/song` (song view payload) | **N/A** — JSON-поля `linkSponsrPlay`, `linkDzenKaraoke`, …, `linkTgChords` остаются в ответе | `karaoke-web/.../controllers/PublicApiController.kt` |
| `POST /api/song/update` | **N/A** — параметры не меняются (поведение сохранения полей такое же) | `karaoke-app/.../controllers/ApiController.kt` |
| `POST /api/songsdigests` | **N/A** — поля ссылок в digest-DTO сохраняются | `karaoke-app/.../controllers/ApiController.kt` |
| `GET /api/song/{id}/shortinfo` | **N/A** | `karaoke-app/.../controllers/ApiController.kt` |

Тип DTO `SettingsDTO` (Kotlin data class) и его JSON-сериализация
**сохраняются**: поля `linkSponsrPlay`, `linkDzenKaraoke`, …, `linkTgChords`
остаются `val`/`var` со своими типами (как правило, `String?`).

### Vue-prop контракты

| Компонент | Изменение | Файл |
|-----------|-----------|------|
| `PlatformLink` | **N/A** — `props: { linkName, linkValue, songId, songVersion }` остаются | `karaoke-public/src/components/PlatformLink.vue` |
| `SongView` (page) | **N/A** — других public prop'ов у view нет | `karaoke-public/src/views/SongView.vue` |
| `SearchView`, `ZakromaView` | **N/A** | — |

### Vuex-контракты

| Store-модуль | Изменение |
|--------------|-----------|
| `songs`, `stats`, `platforms`, и т.д. | **N/A** — никаких новых state/getters/mutations/actions |

### URL/роуты

| Route | Изменение |
|-------|-----------|
| `/song?id=<id>` (и `/song/<slug>` если включён) | **N/A** — публичный URL страницы песни остаётся прежним; меняется только содержимое (нет блока ссылок) |
| `/search`, `/zakroma` | **N/A** — ссылки на платформы продолжают рендериться `PlatformLink`-компонентом |

### DB / SQL контракты

| Артефакт | Изменение |
|----------|-----------|
| `tbl_settings` схема | **N/A** — нет DDL |
| `tbl_settings_sync` (LOCAL↔SERVER) | **N/A** — нет DDL, recordhash-триггер стабилен |
| `recordhash`-триггер на `tbl_settings` | **N/A** — md5 от канонизированной строки таблицы не меняется, регенерация не требуется |
| `SyncRegistry.all` | **N/A** — `tbl_settings` уже синхронизируется, ничего не добавляем/убираем |

### KDoc / JSDoc / typedoc контракты

| Артефакт | Изменение |
|----------|-----------|
| `docs/api/dokka/` (Kotlin) | **N/A** — публичный Kotlin-API не меняется |
| `docs/api/typedoc-karaoke-public/` | **N/A** — `PlatformLink` (компонент) сохраняет prop-описания; `SongView` — page-view, типизированных export'ов не имеет |
| `docs/api/typedoc-webvue3/` | **N/A** — `webvue3` не затрагивается |

### CI / baseline контракты

| Артефакт | Изменение |
|----------|-----------|
| `karaoke-public/.eslint-baseline.json` | **N/A — может только СОКРАЩАТЬСЯ** (мы удаляем код, а не добавляем). Если новых нарушений нет — baseline остаётся прежним или уменьшается. Проверить: `cd karaoke-public && npm run lint:check` должен пройти в рамках текущего baseline. |
| `webvue3/.eslint-baseline.json` | **N/A** |
| `config/ktlint/baseline-*.xml` | **N/A** (Kotlin не трогаем) |

## Что формально является контрактом, но в этой фиче не задействовано

Для справки — типичные контракты web-проекта, которые НЕ применяются:

- **CLI-команды** — проект не экспортирует CLI-инструментов.
- **Библиотечные API** — `karaoke-app` используется как Spring-boot-приложение,
  не как библиотека; нет стабильного внешнего API.
- **Грамматики/парсеры** — N/A.
- **OpenAPI/Proto-файлы** — схема API задаётся in-source через контроллеры.

## Итог

Контракты проекта стабильны. Эта папка создана для явной фиксации того,
**что НЕ меняется**, и чтобы в PR не было вопросов «а почему нет обновлённого
API-контракта?». Содержательного diff'а в этой папке нет.
