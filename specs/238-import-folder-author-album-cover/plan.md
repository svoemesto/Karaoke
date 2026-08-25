# Implementation Plan: 238 — Импорт из папки: родители только у того же автора + автообложка альбома

**Branch**: `238-import-folder-author-album-cover` | **Date**: 2026-08-25 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/238-import-folder-author-album-cover/spec.md`

## Summary

Две точечные правки в общей логике импорта из папки (`Song.createFromPath`):

1. **`findDuplicateOriginal`** в `Utils.kt` — убрать fallback на поиск «родителя» среди песен **других авторов**; оставить только поиск у того же автора. Устраняет ложные привязки текста/маркеров от чужого автора.
2. **Автообложка нового альбома** — при создании **нового** альбома через импорт из папки искать в `rootFolder` каждой песни ровно один графический файл (`jpg|jpeg|png|webp|bmp|tiff`, не скрытый), кадрировать его по короткой стороне до 1:1, масштабировать до 400×400 и сохранять как `LogoAlbum.png` + превью; переиспользуется существующая логика `cropCenterSquareAndResize` + `song.pictureAlbum` (путь через `Pictures` → MinIO).

UI/UX **не меняется** — фича прозрачна для оператора.

Технически подход подтверждён в [research.md](research.md) R1-R6. Никаких новых сервисов, миграций БД или технологий — только переиспользование существующих утилит.

## Technical Context

**Language/Version**: Kotlin 1.x (JDK 17), Spring Boot 2.x/3.x (см. Constitution § «Технологический стек»). Файлы правятся в `karaoke-app/src/main/kotlin/`.

**Primary Dependencies**: существующие модули проекта — `KaraokeConnection` (сырой JDBC, Constitution § II), `KaraokeStorageService`, `StorageApiClient`, `Pictures`, `ImageIO` (стандартная библиотека Java для кадрирования/масштабирования PNG). Никаких **новых** зависимостей.

**Storage**: PostgreSQL через сырой JDBC (`Connection.local()/remote()/virtual()`) — никаких миграций схемы, никаких новых таблиц или колонок. Локальная файловая система для `LogoAlbum.png`/`LogoAlbum.preview.png`. MinIO (через `StorageApiClient`) для удалённого хранения обложек — переиспользуется существующая логика `song.pictureAlbum`.

**Testing**: ручная проверка по [quickstart.md](quickstart.md) (11 сценариев). Существующие юнит-тесты в `karaoke-app/src/test` — `@Disabled` (Constitution § «Тесты»); полагаться на них не нужно.

**Target Platform**: Linux-сервер (admin-машина, где развёрнут `karaoke-app` через Docker). Endpoints: `/api/utils/createfromfolder` (admin UI) и `/utils/createfromfolder` (legacy шаблон `main.html`). Оба эндпоинта автоматически покрываются через общую функцию `Song.createFromPath`.

**Project Type**: backend-only фича внутри существующего Spring Boot multi-module Gradle проекта. Никаких новых модулей, никаких изменений в `pom.xml`/`build.gradle.kts`/Dockerfile.

**Performance Goals**: не регрессировать. Импорт 10k файлов из папки (`specs/082-fix-import-folder-oom`) остаётся в тех же пределах. Новая логика автообложки добавляет **один** `File.listFiles` + **одно** чтение PNG + **одну** запись `LogoAlbum.png` + **одну** запись в `Pictures` **только при создании нового альбома** (не для каждой песни). Ожидаемое замедление: единицы миллисекунд на альбом — незаметно.

**Constraints**:
- Constitution § II: только сырой JDBC, никаких JPA/Hibernate — уже соблюдается.
- Constitution § V: `webvue3` — admin SPA; изменения только в бэкенде, никаких правок UI.
- Constitution § VI (FR-006/FR-007/FR-009): все новые публичные функции (`findOrCreateForSongImportWithAutoCover`, `applyAutoAlbumCoverFromFolder`) ДОЛЖНЫ иметь KDoc с `@see`-ссылкой на эту спеку/план; per-feature документ создаётся при миграции в `livedocs/features/` (post-merge).
- Constitution § VIII: никаких секретов в коде — фича не работает с секретами.

**Scale/Scope**:
- 1 файл правки в `Utils.kt` (≤5 строк).
- 1-2 новых companion-метода в `Album.kt` (≤80 строк суммарно).
- 1 строка правки в `Song.kt:8064` (замена одного вызова на новый).
- 0 изменений в `webvue3/`, `karaoke-public/`, `karaoke-web/`.
- 0 изменений в БД (миграций нет).
- 0 новых эндпоинтов.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Принцип | Статус | Комментарий |
|---------|--------|-------------|
| **I. Self-contained автопайплайн** | ✅ PASS | Никаких внешних SaaS — только локальные утилиты (ImageIO, JDK Files API, существующая MinIO-логика). |
| **II. Сырой JDBC + дифф по хэшам** | ✅ PASS | Никаких изменений в SQL или сущностях БД — фича не трогает JDBC-слой, только Kotlin-логику над ним. |
| **III. Двух-БД синхронизация** | ✅ PASS | Никаких изменений в `SyncRegistry`, никаких новых sync-флагов. Не затрагивает recordhash-триггеры (структура таблиц не меняется). |
| **IV. Async-очередь** | ✅ PASS | Никаких новых OS-подпроцессов. `cropCenterSquareAndResize` синхронный, выполняется inline в `createFromPath`. Дополнительных лейнов/thread'ов не требуется. |
| **V. Двух-фронтенд** | ✅ PASS | Изменения только в `karaoke-app` (бэкенд). UI `webvue3` и `karaoke-public` **не** затрагиваются. |
| **VI. Code Standards** | ⚠️ CHECK | FR-006: новые public companion-методы ДОЛЖНЫ иметь KDoc с `@see`-ссылкой на спеку (см. plan). FR-007: ktlint MUST пройти (pre-commit hook). FR-009: per-feature документ создаётся в `livedocs/features/238-import-folder-author-album-cover.md` при миграции post-merge. |
| **VII. Cross-Machine Setup** | ✅ PASS | Никаких изменений в AGENTS.md, .gitattributes, .git-blame-ignore-revs. Локальные файлы исключений не нужны. |
| **VIII. Секреты и git-гигиена** | ✅ PASS | Фича не работает с секретами. Pre-commit `git ls-files | grep -iE '\.env$\|do\.env$\|...'` пусто. |

**Решение по FR-009**: per-feature документ создаётся в `livedocs/features/238-import-folder-author-album-cover.md` **после** успешного merge в master (это Pass-style миграция, см. LiveDocs README). В этом PR — обязательно обновить архитектурный changelog (`livedocs/architecture-notes.md`), если будут значимые архитектурные решения (например, введение нового helper'а — это архитектурно значимо).

**Решение по FR-006**: новые companion-методы `findOrCreateForSongImportWithAutoCover` и `applyAutoAlbumCoverFromFolder` ДОЛЖНЫ иметь KDoc с `@see`-ссылкой на `specs/238-import-folder-author-album-cover/spec.md`. Это блокирующий пункт для merge.

## Project Structure

### Documentation (this feature)

```text
specs/238-import-folder-author-album-cover/
├── plan.md              # Этот файл (/speckit.plan command output)
├── research.md          # Phase 0 output — технические решения и обоснования
├── data-model.md        # Phase 1 output — затронутые сущности и новые компоненты
├── quickstart.md        # Phase 1 output — 11 ручных сценариев проверки
├── contracts/           # Phase 1 output — внутренние контракты
│   ├── apply-auto-album-cover.md   # контракт Album.findOrCreateForSongImportWithAutoCover
│   └── find-parent-same-author.md  # контракт изменения Utils.findDuplicateOriginal
└── tasks.md             # Phase 2 output — будет создан /speckit.tasks (НЕ этим скриптом)
```

### Source Code (repository root)

Изменения только в `karaoke-app/` (бэкенд). Никаких новых директорий.

```text
karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/
├── Utils.kt                                  # ПРАВКА: findDuplicateOriginal — убрать fallback (~5 строк)
├── controllers/
│   ├── ApiController.kt                      # БЕЗ ИЗМЕНЕНИЙ (использует обновлённый Song.createFromPath)
│   └── MainController.kt                     # БЕЗ ИЗМЕНЕНИЙ (legacy, использует тот же Song.createFromPath)
└── model/
    ├── Album.kt                              # ПРАВКА: новые companion-методы findOrCreateForSongImportWithAutoCover, applyAutoAlbumCoverFromFolder, findOrCreateForSongImportRaw (или модификация существующего)
    └── Song.kt                               # ПРАВКА: createFromPath:8064 — заменить вызов findOrCreateForSongImport на findOrCreateForSongImportWithAutoCover

# БЕЗ ИЗМЕНЕНИЙ:
karaoke-web/                                  # публичный API (только чтение)
webvue3/                                      # admin SPA (только чтение)
karaoke-public/                               # публичный SPA (только чтение)
karaoke-db/                                   # миграции БД (не нужны — нет изменений схемы)
```

**Structure Decision**: фича модифицирует **2** Kotlin-файла в существующем `karaoke-app` модуле (`Utils.kt`, `Album.kt`, `Song.kt` — 3 файла, но 2 из них — это `Album.kt`/`Song.kt`, `Utils.kt` — точечная правка). Никаких новых модулей или директорий.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

Нет violations — таблица пуста.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| — | — | — |

## Готово к Phase 2 (`/speckit.tasks`)

Все артефакты Phase 0 и Phase 1 созданы:
- [research.md](research.md) — технический подход, обоснования, риски.
- [data-model.md](data-model.md) — затронутые сущности + новые компоненты.
- [contracts/apply-auto-album-cover.md](contracts/apply-auto-album-cover.md) — контракт нового helper'а.
- [contracts/find-parent-same-author.md](contracts/find-parent-same-author.md) — контракт изменения `findDuplicateOriginal`.
- [quickstart.md](quickstart.md) — 11 ручных сценариев проверки.

Constitution Check пройден, блокирующих нарушений нет. FR-006/FR-009 зафиксированы как обязательные пункты для `/speckit.tasks` → `tasks.md`.