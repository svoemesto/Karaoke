# Implementation Plan: Backfill флагов публикаций готовых песен без создания новостей

**Branch**: `124-news-flags-backfill` | **Date**: 2026-08-03 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/124-news-flags-backfill/spec.md`

## Summary

Разовая операция приведения флагов публикаций (news/премиум) в «закрытое» состояние для ~15000 готовых песен (`id_status=6`), запускаемая кнопкой из webvue3 через HTTP-endpoint в `ApiController` (по образцу `doBackfillNewsAvailable`). Изменения выполняются только на LOCAL через `Song.saveToDb()` (порождает SSE + пересчитывает recordhash). Распространение на PROD — штатный sync LOCAL→PROD. Для блокировки лавины ~15000 ложных auto-новостей «появилась в коллекции» в точке применения sync-изменений (`MainController.doChangeRecords` → `SongReleaseAnnouncementService.detectAndAnnouncementService.detectAndAnnounceAvailability`) вводится временный kill-switch в `KaraokeProperties` (`newsAutoPublishKillSwitch`), включаемый администратором перед sync и снимаемый после. Постоянный guard НЕ строится — идемпотентность `markNewsAvailableIfReady` (монотонное `newsAvailableAnnounced` false→true) + блокировка `markNewsAvailableIfReady` при `premiumAutoPublishState=COMPLETE` сами по себе предотвращают рецидивы при последующих точечных save() готовых песен.

## Technical Context

**Language/Version**: Kotlin 17 (JDK 17, проект на JDK 17; в контейнере `eclipse-temurin:22-jre-jammy`, см. AGENTS.md). Frontend: Vue 3 + Vite + Vuex (webvue3), JS без TypeScript.

**Primary Dependencies**: Spring Boot 6.2 (`@PostMapping`, `@Scheduled`, `SseEmitter`), kotlinx.serialization (JsonObject/JsonPrimitive для `player_readiness_flags`), сырой JDBC (`KaraokeConnection`), KaraokeProperties (base64-настройки через `/api/properties/setproperty`). Без новых внешних библиотек.

**Storage**: PostgreSQL (LOCAL и PROD). Ключевая колонка — `tbl_songs.player_readiness_flags` (TEXT, JSON). Миграция схемы НЕ требуется (ключи хранятся в JSON, не в отдельных колонках — см. `deploy/karaoke-db/26_player_readiness_flags.sql`). Recordhash-триггер на `tbl_songs` пересоздавать НЕ нужно (колонка `player_readiness_flags` уже в формуле md5, изменение её содержимого меняет hash — sync это увидит).

**Testing**: Тестов в CI нет (AGENTS.md «Тесты»). Проверка — ручная: dry-run backfill, реальный backfill на LOCAL, sync с kill-switch на PROD, проверка `tbl_news` на PROD = 0 новых, проверка save() готовой песни = 0 новостей. CI-gate: ktlint, ESLint webvue3, KDoc/JSDoc coverage (см. AGENTS.md «CI-gate для master»).

**Target Platform**: admin-машина (LOCAL, Karaoke-app контейнер) — backfill; prod-сервер (PROD, Karaoke-web контейнер) — sync-применение + kill-switch. На PROD `karaoke-app` НЕ развёрнут (AGENTS.md «Q: Чем отличается karaoke-app от karaoke-web?») — kill-switch и `detectAndAnnouncementService.detectAndAnnounceAvailability` живут в `karaoke-web` (`MainController` + `SongReleaseAnnouncementService` через dependency).

**Project Type**: web-service (Kotlin/Spring Boot backend + Vue 3 admin SPA).

**Performance Goals**: backfill на ~15000 готовых песен на LOCAL — ≤ 15 минут (SC-007). Sync разносит на PROD в штатном темпе. Окно kill-switch = время sync + небольшой буфер. SSE-тосты прогресса — каждые ~500 обработанных песен (FR-015).

**Constraints**: 
- Constitution Principle II: сравнение рекордов LOCAL↔PROD — через `associateBy { it.id }` (O(n)), не O(n²). Backfill не сравнивает — он грузит чанками `WHERE id_status = 6` и обновляет каждую через `saveToDb()`.
- Constitution Principle IV: `ProcessBuilder.redirectErrorStream(true)` — НЕ применяется (backfill не запускает подпроцессы, только JDBC + JSON).
- AGENTS.md «Запрещено»: пересобирать/перезапускать `karaoke-app` на LOCAL — только пользователь. Бэк заполняется через endpoint, который требует запущенного `karaoke-app`; если контейнер не запущен — пользователь запускает его сам.
- AGENTS.md «Деплой на сервер» — только пользователь. Kill-switch на PROD включается/снимается через `/api/properties/setproperty` (без деплоя, без рестарта контейнера).

**Scale/Scope**: ~15000 готовых песен в `tbl_songs` (статус 6 + непустые `source_markers`). Изменения: 1 новый метод в `SongReleaseAnnouncementService` (backfill), 1 новый endpoint в `ApiController`, 1 новый метод-обёртка kill-switch в `SongReleaseAnnouncementService.detectAndAnnouncementService.detectAndAnnounceAvailability` (или `News.createAutoAnnouncement`), 1 кнопка в webvue3 (UI запуска), 1 новое property в `KaraokeProperties`. Без миграций БД, без новых сущностей.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Загружена `.specify/memory/constitution.md` (Core Principles I–VIII). Проверка по принципам:

| Principle | Статус | Обоснование |
|---|---|---|
| **I. Self-contained автопайплайн** | ✅ PASS | Backfill не вводит внешних SaaS-зависимостей в горячем пути. Только локальная БД + локальный SSE. |
| **II. Сырой JDBC + дифф по хэшам** | ✅ PASS | Backfill использует `Song.saveToDb()` (существующий raw-JDBC + reflection-diff). Сравнение LOCAL↔PROD — через recordhash (sync-движок). O(n²)-паттернов нет: backfill грузит чанками по `CHUNK_SIZE=25`, не делает вложенных `.any`/`.none`. |
| **III. Двух-БД синхронизация через SyncRegistry** | ✅ PASS | Новых сущностей в sync не добавляется. `tbl_songs` уже в SyncRegistry. Backfill меняет существующую колонку `player_readiness_flags` — sync увидит изменение recordhash и разнесёт. `tbl_news` исключена из auto-sync через `source='auto'` (см. `News.listHashes`). |
| **IV. Async-очередь задач с парсингом stdout** | ✅ N/A | Backfill не запускает подпроцессы (ffmpeg/Demucs/MLT). Только JDBC + JSON. Принцип не применяется. |
| **V. Категорически запрещено** | ✅ PASS | Не коммитим секреты, не редактируем сервер напрямую (kill-switch — через `/api/properties/setproperty`, без rsync/ssh), не перезаписываем `do.env`. |
| **VI. Tabулатура (ASCII-only)** | ✅ N/A | Не генерируем табулатуру/MLT. |
| **VII. Git-гигиена** | ✅ PASS | Feature-ветка `124-news-flags-backfill` зарезервирована через `tools/reserve-branch-number.sh`. PR + CI 7/7 перед merge в master (AGENTS.md «CI-gate для master»). `CLAUDE.md` в `.git/info/exclude`. |
| **VIII. Секреты и git-гигиена** | ✅ PASS | Kill-switch — не секрет (булев флаг в `KaraokeProperties`). `do.env`/`.env` не трогаем. |

**GATE PASSED.** Вариаций без нарушений — нет записей в Complexity Tracking.

## Project Structure

### Documentation (this feature)

```text
specs/124-news-flags-backfill/
├── plan.md              # Этот файл
├── spec.md              # Спецификация (из /speckit.specify)
├── research.md          # Phase 0: исследование решений
├── data-model.md        # Phase 1: модель данных (JSON-ключи player_readiness_flags)
├── quickstart.md        # Phase 1: гайд валидации end-to-end
├── contracts/
│   └── api.md           # Phase 1: контракт HTTP-endpoint'а backfill + kill-switch property
└── tasks.md             # Phase 2 (НЕ создаётся /speckit.plan — после /speckit.tasks)
```

### Source Code (repository root)

```text
karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/
├── controllers/
│   └── ApiController.kt                         # +endpoint POST /api/utils/backfillpublishflags
├── services/
│   └── SongReleaseAnnouncementService.kt        # +backfillPublishFlags(), +killSwitch check
└── model/
    └── Song.kt                                  # существующие getters/setters для news-флагов (не меняются)

karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/
└── controllers/
    └── MainController.kt                        # doChangeRecords — точка detectAndAnnounceAvailability (не меняется, kill-switch внутри detect)

karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/
└── KaraokeProperties.kt                         # +newsAutoPublishKillSwitch (default=false)

webvue3/src/
├── components/
│   └── Dashboard/                                # место кнопки backfill (по образцу кнопки RecalcPlayerReadiness)
└── lib/
    └── utils.js                                 # promisedXMLHttpRequest (используется)
```

**Structure Decision**: Используется существующая структура проекта (Kotlin/Spring Boot backend + Vue 3 SPA). Новых модулей/пакетов не создаётся. Изменения точечные: 1 endpoint, 1 метод-обёртка kill-switch, 1 property, 1 кнопка UI. Шаблон «backend + frontend» (Option 2 из шаблона) — но без папок `backend/frontend` (проект использует `karaoke-app`/`karaoke-web`/`webvue3`, см. AGENTS.md «Модули»).

## Complexity Tracking

Нет нарушений Constitution Check. Таблица пуста.

---

## Phase 0: Outline & Research

См. `research.md` (создаётся ниже). NEEDS CLARIFICATION в Technical Context отсутствуют — все решения приняты на этапе `/speckit.clarify` (см. `spec.md` → `## Clarifications`).

## Phase 1: Design & Contracts

См. `data-model.md`, `contracts/api.md`, `quickstart.md` (создаются ниже).