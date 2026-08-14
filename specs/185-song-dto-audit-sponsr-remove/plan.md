# Implementation Plan: Аудит публичного DTO песни и удаление ссылки на Sponsr

**Branch**: `185-song-dto-audit-sponsr-remove` | **Date**: 2026-08-14 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/185-song-dto-audit-sponsr-remove/spec.md`

## Summary

Аддитивно-обратный рефакторинг публичного DTO песни (`SongPublicDto`, `ZakromaAlbumSongPublicDto`) — удаляем ~25-50 полей, которые не используются фронтом `karaoke-public` (Vue 3 SPA), но засоряют JSON-ответ на каждом `/api/public/*` запросе. Главное пользовательское требование: убрать иконку-платформу `Sponsr` из таблиц Закромов и поиска.

Технический подход (см. [research.md](./research.md), решения D-1..D-6):
1. Удаляем 21 поле ссылок на соцсети + 4 служебных из `SongPublicDto` и `ZakromaAlbumSongPublicDto`.
2. Удаляем те же 21 поле из `ZakromaAlbumSong` (внутренняя модель, используется ТОЛЬКО для построения DTO).
3. Удаляем колонку `PlatformLink[link-name="sponsr"]` из таблиц в `ZakromaView.vue` и `SearchView.vue`.
4. Удаляем блоки ссылок на соцсети из Thymeleaf-шаблонов (`filter.html`, `zakroma.html`, `testpage.html`).
5. НЕ трогаем `Song.kt` (модель) — нужна админке `webvue3` и публикационным ботам.
6. НЕ трогаем БД (`tbl_songs` / `tbl_songs_sync`).
7. НЕ делаем миграций.

## Technical Context

**Language/Version**:
- **Backend**: Kotlin 1.x (проект на JDK 17, Gradle multi-module).
- **Frontend**: Vue 3 + Vite, Node 22 (LTS).

**Primary Dependencies**:
- Spring Boot 2.x/3.x (для `PublicApiController`).
- Jackson (для JSON-сериализации DTO).
- Bootstrap 5 / Bootstrap-vue-next (для UI).
- Никаких НОВЫХ зависимостей.

**Storage**: N/A в этой спеке. Поля читаются из `Song.kt` (модель поверх `tbl_songs` в PostgreSQL), но схема БД не меняется.

**Testing**: Ручное тестирование по [quickstart.md](./quickstart.md). В проекте нет CI-тестов для этого уровня (см. constitution, раздел «Рабочий процесс» / «Тесты»).

**Target Platform**:
- Backend: Linux server, Docker (`eclipse-temurin:22-jre-jammy` для karaoke-web).
- Frontend: статический `dist/`, отдаётся через nginx на проде.

**Project Type**: web-service (multi-module Spring Boot + 2 Vue SPA).

**Performance Goals**:
- SC-001: payload `/api/public/zakroma?author=КИНО` сокращается на ≥80% (с ~48 KB до ~8 KB).
- Latency не ухудшается (фактически улучшается за счёт меньшего JSON-сериализации).

**Constraints**:
- Полная обратная совместимость с потребителями (единственный — `karaoke-public` Vue SPA).
- Никаких БД-миграций.
- Никаких изменений в `Song.kt` (нужна админке и ботам).
- `idStatus` и `haveVkGroupLink` ОСТАЮТСЯ в `Song.kt` и в legacy `testpage.html` (используются для логики отображения картинки).

**Scale/Scope**:
- ~18 600 записей в `tbl_songs` на проде.
- 21+1 DTO-поля для удаления.
- 3 файла Vue-шаблонов (Vue 3 SPA: `ZakromaView.vue`, `SearchView.vue`).
- 3 файла Thymeleaf-шаблонов (legacy: `filter.html`, `zakroma.html`, `testpage.html`).
- 4 файла Kotlin (`SongPublicDto.kt`, `ZakromaPublicDto.kt`, `PublicApiController.kt`, `Zakroma.kt`).
- **0 БД-миграций**.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

### Principle I — Self-contained автопайплайн
**Применимо**: нет. Эта фича не затрагивает медиа-пайплайн (ffmpeg / Demucs / MLT). Только DTO-сериализация.

**Статус**: ✅ N/A.

### Principle II — Сырой JDBC + дифф по хэшам
**Применимо**: косвенно. `Song.kt` читает из `tbl_songs` через сырой JDBC (уже соответствует). Рефакторинг НЕ затрагивает JDBC-слой.

**Статус**: ✅ N/A.

### Principle III — Двух-БД синхронизация через SyncRegistry
**Применимо**: нет. Эта фича НЕ добавляет/удаляет сущности из `SyncRegistry`. `tbl_songs` уже синхронизируется (не меняется). Удаляемые поля — это только проекции (DTO), не БД-колонки.

**Статус**: ✅ N/A.

### Principle IV — Async-очередь задач
**Применимо**: нет. Никаких `KaraokeProcess*` не задействовано.

**Статус**: ✅ N/A.

### Principle V — Двух-фронтенд (админка и публичный сайт — разные приложения)
**Применимо**: ДА. Граница между `webvue3` (admin) и `karaoke-public` (public) сохраняется:
- Удаляем поля из публичных DTO (отдаются в `/api/public/*` для `karaoke-public`).
- НЕ трогаем `webvue3` (админка читает из `Song.kt` напрямую через `ApiController.kt`, не через публичные DTO).

**Статус**: ✅ Соответствует.

### Principle VI — Code Standards (KDoc/JSDoc, линтеры, per-feature docs)
**Применимо**: ДА.

- **FR-006 (KDoc/JSDoc)**: После рефакторинга все 3 data class'a (`SongPublicDto`, `ZakromaAlbumSongPublicDto`, `ZakromaAlbumSong`) сохраняют свои KDoc с `@see AGENTS.md`. Удаляемые поля НЕ требуют KDoc (поля data class'a документируются через описание класса).
- **FR-007 (линтеры)**: после рефакторинга должны запускаться `./gradlew ktlintCheck`, `cd karaoke-public && npm run lint:check`, `./tools/check-eslint-baseline.sh`. Baseline не должен вырасти.
- **FR-009 (per-feature docs)**: рефакторинг НЕ затрагивает ни одну из 9 ключевых подсистем (`docs/features/`). Изменения — это DTO-чистка, не новая фича.

**Статус**: ✅ Соответствует. Требуется ручная проверка линтеров после реализации.

### Principle VII — Cross-Machine Setup
**Применимо**: ДА.
- Эта фича работает в feature-ветке `185-song-dto-audit-sponsr-remove` (NON-NEGOTIABLE, см. AGENTS.md «CI-gate для master»).
- Прямой push в master ЗАПРЕЩЁН.
- Коммит-стиль: `185-song-dto-audit-sponsr-remove: краткое описание` (на русском).

**Статус**: ✅ Соответствует.

### Principle VIII — Секреты и git-гигиена
**Применимо**: ДА.
- Эта фича НЕ добавляет секрет-файлов.
- Pre-commit проверка: `git ls-files | grep -iE '\.env$|do\.env$|\.key$|\.pem$'` MUST возвращать пусто.
- Все изменения — в публичных DTO и шаблонах. Никаких `.env`, никаких credentials.

**Статус**: ✅ Соответствует.

### Governance — приоритет документов
**Применимо**: ДА. Эта фича ссылается на:
- `AGENTS.md` (приоритет 2) — для CI-gate, шаблонов таблиц, Jackson-инварианта.
- `.specify/memory/constitution.md` (приоритет 1) — для Core Principles.
- `docs/features/` — НЕ затрагивается (DTO-чистка не per-feature фича).

**Статус**: ✅ Соответствует.

### Итог Constitution Check

**Все Gates PASS.** Никаких нарушений. Никаких обоснований в Complexity Tracking не требуется.

## Project Structure

### Documentation (this feature)

```text
specs/185-song-dto-audit-sponsr-remove/
├── plan.md              # Этот файл (/speckit.plan output)
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/
│   └── api-public-song.md  # JSON-контракты /api/public/zakroma, /songs, /song/{id}, /zakroma/stream
├── checklists/
│   └── requirements.md  # Quality checklist
├── spec.md              # Feature spec
└── tasks.md             # Phase 2 output (создаётся /speckit.tasks, не /speckit.plan)
```

### Source Code (repository root)

Эта фича затрагивает несколько файлов в существующих модулях. Структура НЕ создаётся — только РЕДАКТИРУЕТСЯ.

**Backend (Kotlin)**:
- `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/dto/SongPublicDto.kt` — удаление ~50 полей + правка `fromSong`.
- `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/dto/ZakromaPublicDto.kt` — удаление 21 поля + правка `fromZakroma`.
- `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicApiController.kt` — удаление 21 параметра из inline-конструктора `zakromaStream` (строки 344-389).
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Zakroma.kt` — удаление 21 `var`-поля из `ZakromaAlbumSong` (строки 265-285) + 21 строка сборщика (строки 207-225).

**Frontend Vue (karaoke-public)**:
- `karaoke-public/src/views/SearchView.vue` — удаление `<td>` (строки 140-147) и `<div>` в карточке (строки 183-189), правка `<col style="width">`.
- `karaoke-public/src/views/ZakromaView.vue` — удаление `<td>` (строки 319-326) и `<div>` в карточке (строки 354-360), правка `<col style="width">`.

**Legacy Thymeleaf**:
- `karaoke-web/src/main/resources/templates/filter.html` — удаление блоков ссылок на соцсети + `<col>`/`<th>` + правка `width`.
- `karaoke-web/src/main/resources/templates/zakroma.html` — то же.
- `karaoke-web/src/main/resources/templates/testpage.html` — то же + ОСТАВИТЬ `${sett.idStatus}` и `${sett.haveVkGroupLink}` на строке 300.

**Структура НЕ создаётся** — это правки в существующих файлах. **Structure Decision**: применяется Option 2 из шаблона плана (web application с backend + frontend).

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

Нет нарушений. Таблица пуста.

## Implementation Order

Рекомендуемый порядок (фазы 2 — для `/speckit.tasks`):

1. **Backend DTO** (`SongPublicDto.kt`, `ZakromaPublicDto.kt`): удалить поля и правки конвертеров.
2. **Backend Controller** (`PublicApiController.kt`): удалить параметры из inline-конструктора.
3. **Backend Model** (`Zakroma.kt`): удалить поля из `ZakromaAlbumSong` и сборщик.
4. **Local verify**: `./gradlew clean :karaoke-app:compileKotlin :karaoke-web:compileKotlin`.
5. **Frontend Vue** (`SearchView.vue`, `ZakromaView.vue`): удалить `PlatformLink` колонки + правка `width`.
6. **Frontend build**: `cd karaoke-public && npm run build` (или `do.sh build_start_public`).
7. **Legacy Thymeleaf** (`filter.html`, `zakroma.html`, `testpage.html`): удалить блоки ссылок + правка `width`.
8. **Manual test** по [quickstart.md](./quickstart.md) — все SC-001..SC-007 должны пройти.
9. **Lint**: ktlint, ESLint, baseline — все зелёные.
10. **Commit + PR + CI 7/7 + merge** (см. AGENTS.md «CI-gate для master»).

## References

- Spec: [spec.md](./spec.md)
- Research: [research.md](./research.md)
- Data model: [data-model.md](./data-model.md)
- Contracts: [contracts/api-public-song.md](./contracts/api-public-song.md)
- Quickstart: [quickstart.md](./quickstart.md)
- Constitution: [../../.specify/memory/constitution.md](../../.specify/memory/constitution.md)
- AGENTS.md (русский, приоритет 2): [../../AGENTS.md](../../AGENTS.md)
- Karaoke Constitution § Self-contained автопайплайн (Principle I)
- Karaoke Constitution § Двух-фронтенд (Principle V)
- Karaoke Constitution § Code Standards (Principle VI)
- AGENTS.md Q&A «Jackson отбрасывает is в boolean-полях Kotlin DTO»
- AGENTS.md Q&A «Главный фокус стратегии роста — visitor→registration» (контекст: чистый публичный сайт = лучше для конверсии)