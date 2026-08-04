# Implementation Plan: 131 — починка пайплайна после одобрения задания

**Branch**: `131-fix-approve-demo-render-telegram-sync` | **Date**: 2026-08-04 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/131-fix-approve-demo-render-telegram-sync/spec.md`

## Summary

Расширить существующий `SongEditorController.approve()` двумя блоками,
которые превращают текущий «approve-в-локал-sync» в полный конвейер
«approve → рендер DEMO → публикация в Telegram → sync сервер → новость»:

1. **Идемпотентно создать** процесс `RENDER_MP4_DEMO` в `tbl_processes`
   (после существующего `updateRemoteSongFromLocalDatabase`).
2. **Fire-and-forget запустить** sync связанных таблиц
   `tbl_pictures`/`tbl_authors`/`tbl_albums` через
   `updateRemoteDatabaseFromLocalDatabase(updateSongs=false, true, true)`.
3. В пост-хуке `KaraokeProcessThread.run()` **сразу после** `DONE` для
   `RENDER_MP4_DEMO` запустить `TelegramAutoPublishService.publishToTelegram`
   в отдельном `thread { ... }` с параметрами
   `allowPastDate=true, publicationType=AIR, persistMessageId=true`.

Существующая логика approve (markers / idStatus=6 / `updateRemoteSongFromLocalDatabase(song.id)` /
`aRead.save()`) **не меняется** — закреплена specs/094, 095, 096 (A-001).
Никаких изменений схемы БД, DTO, recordhash-триггеров (A-002).
Полное обоснование решений — в [research.md](./research.md), контракт
внутренних взаимодействий — в [contracts/pipeline.md](./contracts/pipeline.md),
роли сущностей — в [data-model.md](./data-model.md).

## Technical Context

**Language/Version**: Kotlin 1.9+, JDK 17 (для `karaoke-app` и `karaoke-web`).
Соответствует действующему Gradle-окружению, изменений версии языка/тулчейна не требуется.

**Primary Dependencies**:
- Spring Boot (`@RestController`, `@PostMapping`) — точка входа `approve()` уже подключена.
- JDBC (`Connection.local()`, `Connection.remote()`, `WORKING_DATABASE`) — для гарда по `tbl_processes` и для sync через `updateDatabases`.
- Текущие сервисы без новых зависимостей: `KaraokeProcess`, `KaraokeProcessWorker`,
  `TelegramAutoPublishService`, `TelegramAutoPublishState`, `PublicationType`,
  `SongReleaseAnnouncementService`, `Utils.updateRemoteDatabaseFromLocalDatabase`,
  `Utils.updateRemoteSongFromLocalDatabase`.
- Никаких новых сторонних библиотек.

**Storage**: PostgreSQL (`tbl_songs`, `tbl_processes`, `tbl_news`,
`tbl_pictures`, `tbl_authors`, `tbl_albums`, `tbl_settings`, `tbl_settings_sync`).
MinIO — для DEMO-MP4 (read-only, не меняется).

**Testing**: ручные сценарии (см. [quickstart.md](./quickstart.md));
существующие тесты в репозитории — интеграционные, `@Disabled`,
фича их не запускает. Автотесты не добавляем — соответствует
конституционному подходу (см. `AGENTS.md → Тесты`).

**Target Platform**: Linux-сервер под управлением JVM 17+ внутри
Docker-контейнера `karaoke-app` (публикуется на dev-машине; на проде
karaoke-web использует «тонкий» API). OS — Debian-база (eclipse-temurin
JRE).

**Project Type**: Web-service (Kotlin Spring Boot, существующая инфраструктура).

**Performance Goals**:

- SC-003: HTTP-ответ `approve` — в течение ≤5 с после клика
  (block синк-related и публикация Telegram — `thread { ... }`).
- SC-001: DEMO-MP4 готов ≤10 мин после approve (определяется
  воркером рендера; не ограничение этой фичи).
- SC-002: Telegram-пост ≤60 с после DEMO-MP4 (пост-хук
  `KaraokeProcessThread`).

**Constraints**:

- Никаких новых миграций БД (A-002, Принцип II конституции).
- Никаких изменений существующего DTO / JSON API.
- Approve должен пережить сбой каждого нового шага независимо (A-007,
  contract 5 в [contracts/pipeline.md](./contracts/pipeline.md)).

**Scale/Scope**:

- `tbl_processes`: ~тысячи строк, новый процесс создаётся редко (только
  approve).
- `tbl_songs`: ~18k записей; sync хэширует все — десятки секунд на
  редкий вызов. Это не inline.
- Telegram-канал: 1 пост при approve, типовой объём (≤50 МБ
  демо-MP4).

## Constitution Check

*GATE: must pass before Phase 0 research. Re-check after Phase 1 design.*

| # | Принцип | Применяется? | Соответствует | Обоснование |
|---|---|---|---|---|
| I | Plain words / никакой over-engineering | да | да | Минимум кода: 2 добавки в существующий метод + 1 пост-хук. Никаких новых сервисов. |
| II | Никаких новых полей, кроме согласованных миграций | да | да | Ни одной миграции. Работаем с `id_status`, `news_available_announced`, `id_telegram_demo`, `process_status` и т.д. в их текущем виде. |
| III | SyncRegistry — единственный путь LOCAL↔SERVER | да | да | Sync-related через `updateRemoteDatabaseFromLocalDatabase(keys=legacySyncKeys(...))`. Существующий `updateRemoteSongFromLocalDatabase(id)` без изменений. |
| IV | Безопасный рефакторинг | да | да | Каждый новый блок изолирован `try { ... } catch (_: Exception) { println(...) }`. Изменение поведения approve **аддитивное** — старые сценарии не задеты. |
| V | Никакого ad-hoc Json / приватных DTO | да | N/A | Фича не трогает JSON-обмен с фронтом. |
| VI | Web-endpoint должен быть быстрым | да | да | SC-003: ответ ≤5 с. Всё тяжёлое — `thread { ... }` или отдельный воркер. |
| VII | KDoc и комментарии — на русском | да | да | Все комментарии и KDoc — на русском (см. [research.md](./research.md), который ссылается на файлы и пишет комментарии в формате существующего стиля). |
| VIII | IP/секреты — из env, не из кода | да | N/A | Никаких секретов не добавляем. |
| IX | Async — никогда не блокировать HTTP | да | да | Шаг 2 — `thread { ... }`; Telegram-публикация — `thread { ... }` в пост-хуке (по аналогии с `TelegramAutoPublishScheduler` который сам неблокирующий). |
| X | Существующий код specs/094/095/096 — не трогаем | да | да | Только **аддитивные** вставки в конец approve-блока и в пост-хук. |

**Итог**: 7 принципов «применяются» — все «да». Никаких нарушений. Никаких
Justified-исключений в `## Complexity Tracking` ниже.

## Project Structure

### Documentation (this feature)

```text
specs/131-fix-approve-demo-render-telegram-sync/
├── plan.md              # Этот файл (вывод /speckit.plan)
├── research.md          # Phase 0 — обоснование технических решений
├── data-model.md        # Phase 1 — роли сущностей и state transitions
├── contracts/
│   └── pipeline.md      # Phase 1 — внутренний контракт оркестрации
├── quickstart.md        # Phase 1 — ручные сценарии проверки
├── spec.md              # Уже создано на /speckit.specify
├── checklists/
│   └── requirements.md  # Чек-лист качества спецификации (PASSED 16/16)
└── tasks.md             # Phase 2 — вывод /speckit.tasks (НЕ создаётся /speckit.plan)
```

### Source Code (repository root)

**Изменения** ограничены двумя файлами (никаких новых каталогов, никаких новых модулей):

```text
karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/
├── controllers/
│   └── SongEditorController.kt      # approve() — добавить 2 блока
└── KaraokeProcessWorker.kt          # KaraokeProcessThread.run() — добавить 1 ветку пост-хука

# Файлы, которые читаем, но НЕ правим:
#   karaoke-app/.../Utils.kt                  (updateRemoteSongFromLocalDatabase, updateRemoteDatabaseFromLocalDatabase)
#   karaoke-app/.../KaraokeProcess.kt         (createProcess)
#   karaoke-app/.../services/TelegramAutoPublishService.kt (publishToTelegram)
#   karaoke-web/.../controllers/MainController.kt           (doChangeRecords — серверная сторона)
#   karaoke-web/.../services/SongReleaseAnnouncementService.kt (detectAndAnnounceAvailability)
```

**Structure Decision**: фича НЕ создаёт новых сервисов / контроллеров /
DTO. Это **аддитивное** расширение двух существующих методов. Такой объём
не требует новых модулей — оставляем структуру как есть (Option 1 +
уже существующий layout `karaoke-app`).

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| (нет) | — | — |

Все принципы конституции соблюдены.

## Re-evaluation of Constitution Check (post-design)

После Phase 1 design пересматриваем конституционные гейты:

- **Принцип III (SyncRegistry)**: фаза 1 спроектировала **только** переиспользование
  существующего `updateRemoteDatabaseFromLocalDatabase` через
  `legacySyncKeys(updateSongs=false, true, true)`. Никаких параллельных путей.
  ✓
- **Принцип IV (безопасный рефакторинг)**: фаза 1 закрепила в
  [contracts/pipeline.md §5](./contracts/pipeline.md) матрицу изоляции сбоев.
  Каждый новый блок имеет свой `try { ... } catch (_: Exception) { println(...) }`.
  Существующие сценарии approve (094/095/096/101) не задеты. ✓
- **Принцип IX (async не блокирует HTTP)**: фаза 1 явно спроектировала
  Шаг 2 (related-sync) и публикацию Telegram (пост-хук) как `thread { ... }`.
  Это формализовано в [contracts/pipeline.md §2.2](./contracts/pipeline.md). ✓

Все гейты — passed. Фича может идти в фазу tasks/implementation.
