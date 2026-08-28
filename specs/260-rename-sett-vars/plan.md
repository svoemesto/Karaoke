# Implementation Plan: Переименование `sett`/`settings` → `song` (260-rename-sett-vars)

**Branch**: `260-rename-sett-vars` | **Date**: 2026-08-28 | **Spec**: [`spec.md`](./spec.md)
**Input**: Feature specification from `/specs/260-rename-sett-vars/spec.md`
**Прецедент**: `specs/102-rename-song-settings-vars/` (полная форма `settings` → `song`, влита в master).

## Summary

Внутренний рефакторинг идентификаторов в Kotlin/HTML/Vue/SQL/KDoc кодовой базе, оставшихся от исторического переименования класса `Settings` в `Song` (2024–2025). Спека 102 покрыла полную форму `settings`, текущая — сокращённую форму `sett` + забытые `settings` в новом коде (`publishToVkNow`/`publishPremium*`) + ссылки `tbl_settings` в KDoc/JSDoc. Физическая схема БД не меняется, новые runtime-контракты не вводятся.

Технический подход: чисто переименовательный (sed/find-replace внутри Kotlin/HTTP/Vue/SQL, плюс ручная верификация IDE). Никаких новых структур данных, никаких миграций, никаких changes в API/JSON.

## Technical Context

**Language/Version**: Kotlin 1.x (JVM, JDK 17) — модули `karaoke-app`, `karaoke-web`. Vue 3 + Vite (Node 22) — модуль `karaoke-public`. Thymeleaf (legacy HTML, в `karaoke-app`/`karaoke-web`). PostgreSQL (inline SQL в `StatBySong.kt`).

**Primary Dependencies**:
- Backend: Spring Boot, Gradle multi-module, Ktor (если используется в `karaoke-web`).
- Frontend: Vue 3, Vuex, Bootstrap, Vite, ESLint, npm.
- Lint: ktlint (baseline в `config/ktlint/baseline-*.xml`), ESLint (baseline в `*.eslint-baseline.json`).

**Storage**: PostgreSQL (физическое имя `tbl_songs`, **не меняется**). MinIO (не затрагивается).

**Testing**: в CI нет автотестов; верификация — ручная + grep + линтеры (см. `quickstart.md`).

**Target Platform**: Linux server (prod) + admin-машина (local dev). Multi-module Spring Boot + SPA.

**Project Type**: multi-module Gradle backend (Kotlin) + 2 SPA (Vue).

**Performance Goals**: N/A (рефакторинг не меняет поведение; в худшем случае — без изменений).

**Constraints**: 
- Атомарный деплой backend+Thymeleaf (FR-013 спеки 260, аналог FR-016 спеки 102).
- `karaoke-public` деплоится отдельно (FR-014 спеки 260).
- `webvue3` вне scope целиком (Clarifications Q5 спеки 260).
- Никаких изменений physical schema БД (FR-005 спеки 260, прецедент спеки 102).
- Mass-rename коммит в `.git-blame-ignore-revs` (Constitution VII.2, спека 102 T046).

**Scale/Scope**: **≥546** совпадений `sett` в **30+** файлах (Kotlin/HTML/Vue/JS/SQL/KDoc). Модули: `karaoke-app`, `karaoke-web`, `karaoke-public`. **Не затрагивает**: `karaoke-db`, `webvue3`, `deploy/`, `node_modules/`, `build/`.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Принцип | Статус | Замечание |
|---|---|---|
| **I. Self-contained автопайплайн** | N/A | Рефакторинг не затрагивает ML/обработку медиа |
| **II. Сырой JDBC + дифф по хэшам** | ✅ | SQL-rename в `StatBySong.kt` — алиас таблицы, не statement; `recordhash`-триггеры не задеваются |
| **III. SyncRegistry** | ✅ | Никаких новых sync-targets. `SyncTarget.key = "settings"` (платформо-настройка) явно вне scope (FR-007) |
| **IV. Async-очередь** | N/A | `KaraokeProcess` не затрагивается |
| **V. Two-frontend** | ✅ | `webvue3` (admin SPA) и `karaoke-public` (public SPA) явно разделены (FR-014, Clarifications Q5) |
| **VI. Code Standards** | ✅ | KDoc/JSDoc обновляются (FR-008); ktlint/ESLint baselines не должны пополняться новыми строками (FR-010); per-feature документ будет создан в Phase 6 Polish |
| **VII. Cross-Machine** | ✅ | Коммит после мержа добавляется в `.git-blame-ignore-revs` (см. `quickstart.md` Done When); `git config blame.ignoreRevsFile` настраивается при клонировании |
| **VIII. Secrets** | N/A | Не трогает секрет-файлы; baseline-греп не задевает их |

**Вердикт**: 0 нарушений, complexity tracking не требуется.

## Project Structure

### Documentation (this feature)

```text
specs/260-rename-sett-vars/
├── plan.md              # Этот файл (output /speckit.plan)
├── spec.md              # Feature specification (/speckit.specify output)
├── research.md          # Phase 0 output (/speckit.plan)
├── data-model.md        # Phase 1 output (/speckit.plan)
├── quickstart.md        # Phase 1 output (/speckit.plan)
├── contracts/
│   └── contracts.md     # Phase 1 output (/speckit.plan) — нулевой контракт (только internal identifiers)
├── checklists/
│   └── requirements.md  # Spec quality checklist (output /speckit.specify)
└── tasks.md             # Phase 2 output (/speckit.tasks — NOT created by /speckit.plan)
```

### Source Code (repository root)

**Структура реального проекта** (соответствует репозиторию `/home/nsa/Karaoke/`):

```text
# Backend (Kotlin, Gradle multi-module)
karaoke-app/
├── build.gradle.kts
├── src/main/kotlin/com/svoemesto/karaokeapp/
│   ├── controllers/
│   │   ├── MainController.kt            # переименование `sett`/`settings` + `model.addAttribute("sett")`
│   │   └── ApiController.kt             # переименование `val settings = Song.loadFromDbById(...)` в 3 методах
│   ├── services/
│   │   └── TelegramUpdatesConsumer.kt   # переименование `val sett = Song.loadFromDbById(...)`
│   ├── model/
│   │   ├── Song.kt                      # не затрагивается (Class itself)
│   │   ├── StatBySong.kt                # SQL-rename алиаса `sett` → `song`
│   │   ├── CrossSong.kt                 # лямбда-параметры `sett`
│   │   └── Pictures.kt                  # лямбда-параметр `sett`
│   └── mlt/mko/                         # 13 файлов: конфликт имён, требует `targetSong`/`renderSong`
│       ├── MkoChords.kt
│       ├── MkoElement.kt
│       ├── MkoFill.kt
│       ├── MkoLines.kt
│       ├── MkoLineTrack.kt
│       ├── MkoMelodyNote.kt
│       ├── MkoMelodyTabs.kt
│       ├── MkoSepar.kt
│       ├── MkoString.kt
│       ├── MkoChordPictureElement.kt
│       ├── MkoChordPictureFader.kt
│       ├── MkoChordPictureImage.kt
│       └── MkoChordPictureLines.kt
├── src/main/resources/templates/         # Thymeleaf legacy
│   ├── area_left_column.html
│   ├── area_center_column.html
│   ├── songs.html
│   ├── filter.html
│   └── zakroma.html
└── build.gradle.kts

karaoke-web/
├── src/main/kotlin/com/svoemesto/karaokeweb/
│   ├── controllers/
│   │   ├── MainController.kt            # `val sett`/`model.addAttribute("sett")` + `val settings`
│   │   └── PublicApiController.kt       # `val sett = Song.loadFromDbById(...)`
│   ├── dto/
│   │   └── ZakromaPublicDto.kt          # KDoc: `tbl_settings` → `tbl_songs` + «settings public» → «songs public»
│   └── services/
│       └── ShareLinkSweeper.kt          # KDoc: `tbl_settings` → `tbl_songs`
├── src/main/resources/templates/         # Thymeleaf legacy
│   ├── filter.html
│   ├── zakroma.html
│   ├── song.html
│   └── testpage.html                    # baseline-проверка нужна: `sett`/`settings` здесь?
└── build.gradle.kts

# Public SPA (Vue 3 + Vite)
karaoke-public/
├── src/views/
│   ├── SearchView.vue                   # v-for="sett in searchResults" → "song"
│   ├── ZakromaView.vue                  # v-for="sett in item.alb.albumSettings"
│   └── AuthorPlaylistView.vue           # const setts → songs
├── src/composables/
│   └── useZakromaStreamProgress.js      # JSDoc: v-for="sett in alb.albumSettings"
└── src/player/KaraokePlayer.js          # ВНЕ SCOPE (LS_SETTINGS_KEY и пр. — настройки плеера)

# Admin SPA — ВНЕ SCOPE (Clarifications Q5, FR-014)
webvue3/
└── src/components/Songs/edit/SubsEdit.vue:183   # label «sett» — НЕ переменная, не переименовывается

# Public docs — НЕ ЗАТРАГИВАЕТСЯ (старые спек.md, plan.md, etc.)
specs/
└── 260-rename-sett-vars/  ← этот документ
```

**Structure Decision**: Используем существующую multi-module Gradle структуру. Никаких новых каталогов не создаётся. Все правки — in-place в существующих файлах. Никаких новых тестов.

## Complexity Tracking

> **Не требуется** — Constitution Check не выявил нарушений. Все принципы соблюдены. Пустая.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|----------|------------|-------------------------------------|
| (нет) | — | — |

## Phase 0 — Research (✓ completed)

Файлы артефактов:
- [`research.md`](./research.md) — baseline-таблица очагов legacy, 5 решений с альтернативами, технические находки по конфликтам имён и SQL inline, исключения (deferrals), Constitution Check, риски.
- `data-model.md` — см. ниже.

**Ключевые открытия Phase 0**:
1. `sett` ≥546 совпадений в 30+ файлах (исходники, без `build/`).
2. Реальное число `val settings = Song.loadFromDbById(...)` в `ApiController.kt` — **минимум 6** (6851, 6900, 6990), не 5 как было в baseline спеки. Фиксируется в `tasks.md` T002.
3. Все 13 файлов `mlt/mko/*.kt` имеют `Song` параметр + `val sett = song` → нужен `targetSong`/`renderSong` при коллизии.
4. `karaoke-public/src/player/KaraokePlayer.js` и `webvue3/src/player/KaraokePlayer.js` — НЕ Song, исключения (прецедент спеки 102 FR-014).
5. `webvue3/SubsEdit.vue:183` "sett" — label UI-кнопки, не Song, исключение (Q5).

## Phase 1 — Design & Contracts (✓ completed)

Файлы артефактов:
- [`data-model.md`](./data-model.md) — формально «zero changes»; фиксирует, что сущности, атрибуты, отношения, валидации, лайфциклы — без изменений; только имена идентификаторов.
- [`contracts/contracts.md`](./contracts/contracts.md) — перечень 4 internal-контрактов (Thymeleaf attribute, Vue v-for iterator, SQL alias, KDoc/JSDoc); явно отмечено, что wire-level контракты (HTTP/JSON/SSE) — вне scope (покрыты спекой 102).
- [`quickstart.md`](./quickstart.md) — 9 проверочных сценариев (8 grep/CLI-сценариев + 2 ручных UI-проверки legacy и public).

**Принцип дизайна**: zero new runtime contracts. Все артефакты максимально короткие, потому что задача чисто переименовательная.

## Phase 2 (НЕ выполняется здесь — следующая команда `/speckit.tasks`)

> Следующий шаг — `/speckit.tasks`, который сгенерирует `tasks.md` с пошаговой разбивкой по 6 фазам (по прецеденту спеки 102: Setup / Foundational / US1 (Kotlin) / US2 (HTML+Vue+SQL+DTO-Comments) / US3 (exclusion verification) / Polish).
> На основе `spec.md` (5 Clarifications разрешены), `research.md` (baseline + решения), `data-model.md` (zero changes), `contracts/contracts.md` (4 internal), `quickstart.md` (9 сценариев) — `tasks.md` будет формализован как чек-лист из ~25-30 задач, готовых к механическому переименованию.

## Done When

- [x] `plan.md` заполнен и валидирован (Constitution Check passed, no violations).
- [x] `research.md` (Phase 0) — baseline + 5 решений + risks.
- [x] `data-model.md` (Phase 1) — zero changes, with rationale.
- [x] `contracts/contracts.md` (Phase 1) — internal contracts только.
- [x] `quickstart.md` (Phase 1) — 9 validation scenarios.
- [x] После Phase 2 (`/speckit.tasks`) — `tasks.md` готов к механическому выполнению.

**Следующая команда**: `/speckit.tasks` (Phase 2 — генерирует `tasks.md`).
