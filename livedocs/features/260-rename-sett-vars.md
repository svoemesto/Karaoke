---
status: Active
slug: 260-rename-sett-vars
related:
  - ../domain/catalog.md
  - ../architecture/L3-components.md
  - ../../specs/260-rename-sett-vars/spec.md
  - 102-rename-song-settings-vars.md
---

# 260 — Переименование `sett`/`settings` → `song` (остаточный охват) (LiveDoc)

> Drill-down — [specs/260-rename-sett-vars/spec.md](../../specs/260-rename-sett-vars/spec.md).

## Что делает

Остаточный рефакторинг после [спеки 102](102-rename-song-settings-vars.md) (полная форма `settings → song`).
Спека 102 покрыла **полную форму** `settings`/`settingsId`/`settings_*` (включая DTO, SSE,
`@RequestParam settings_xxx`, webvue3-контракт), но не покрыла **сокращённую форму
`singT` `sett`** и забытые `settings` в новом коде после спеки 102.

Эта фича закрывает:

- **Сокращённая форма `sett`** в Kotlin/HTML/Vue/JS (546+ вхождений).
- **Полная форма `settings:`** в коде, написанном после спеки 102 (`publishToVkNow`,
  `publishPremiumTelegram`, `publishPremiumVk` — 3 метода в `ApiController.kt`).
- **KDoc-ссылки `tbl_settings`** (2 ссылки устарели — таблица давно `tbl_songs`).

## User Stories (краткий список)

- **US1** — Читаемые Kotlin-методы без сокращений: параметры/локалы с типом `Song`
  переименованы в `song` (или `songValue`/`targetSong`/`songItem` при коллизии).
- **US2** — Шаблоны, фронтенд и SQL без `sett`: Thymeleaf-атрибут `model.addAttribute("sett")` →
  `"song"` синхронно с шаблонами; Vue-итератор `v-for="sett in"` → `"song"`; SQL-алиас
  `tbl_songs sett` → `tbl_songs song`; KDoc `tbl_settings` → `tbl_songs`.
- **US3** — Не задеты понятия «настроек», не связанные с `Song` (исключения): проверка
  сохранности физической БД, конфигурации платформ, настроек плеера, endpoint плейлиста,
  `tbl_public_settings`, `webvue3` целиком (вне scope).

## Functional Requirements (указатель)

- **FR-001**: Kotlin-параметры/локалы `sett` → `song` (или производное).
- **FR-002**: Kotlin-оставшиеся `settings:` → `song`.
- **FR-003**: Thymeleaf атрибут `"sett"` → `"song"` синхронно с `${sett.*}` в шаблонах.
- **FR-004**: Vue/JS в `karaoke-public` — `v-for="sett"` → `"song"`, `setts` → `albumSongs`.
- **FR-005**: SQL-алиас `tbl_songs sett` → `tbl_songs song`.
- **FR-006**: DTO-поле `albumSettings` НЕ переименовывается (отдельная задача).
- **FR-007**: Исключения (физическая БД, конфигурация платформ, настройки плеера, webvue3,
  `tbl_public_settings`, `SyncTarget.key="settings"`, `@KaraokeDbTableField("settings_id")`).
- **FR-008**: KDoc/JSDoc `tbl_settings` → `tbl_songs`.
- **FR-013**: Атомарный деплой backend+Thymeleaf одним коммитом/PR.
- **FR-014**: `karaoke-public` — отдельный деплой (но тот же PR); `webvue3` — вне scope.

## Acceptance Criteria

- [ ] AC1 (FR-001/002): `grep -rn '\bsett\b' karaoke-app/src/main karaoke-web/src/main
      karaoke-public/src` возвращает **0 совпадений** (вне exceptions FR-007). Baseline
      при первичном сканировании — ≥474.
- [ ] AC2 (FR-001/002): `grep -rn "^[[:space:]]*\(val\|var\) settings =" karaoke-app/src/main/kotlin
      karaoke-web/src/main/kotlin` возвращает **0 совпадений** (вне exceptions).
- [ ] AC3 (FR-009/010): `./gradlew :karaoke-app:compileKotlin :karaoke-web:compileKotlin`
      и `:karaoke-app:ktlintCheck :karaoke-web:ktlintCheck` проходят без новых ошибок;
      `config/ktlint/baseline-*.xml` не изменён.
- [ ] AC4 (FR-013/014): один PR на всю ветку `260-rename-sett-vars`; backend+Thymeleaf
      коммитятся атомарно; `karaoke-public` коммитится в том же PR, деплоится отдельно.
- [ ] AC5 (FR-008): `grep -rn 'tbl_settings' karaoke-app/src/main/kotlin karaoke-web/src/main/kotlin
      karaoke-public/src` — остались только физические SQL (`docs/Статискика.sql` + JS в
      `songs.html:1727`), эти **намеренно** оставлены (миграция БД не входит).

## Связанные LiveDocs

- Domain: [catalog.md](../domain/catalog.md) (нет изменений).
- Architecture: [L3-components.md](../architecture/L3-components.md) (нет изменений).
- Прецедент: [102-rename-song-settings-vars.md](102-rename-song-settings-vars.md) —
  покрыл полную форму; эта фича — остаточный охват.

## Код

- Модуль: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/{controllers/ApiController.kt,
  controllers/MainController.kt, services/TelegramUpdatesConsumer.kt, mlt/mko/*.kt (13 файлов),
  model/{CrossSong, Pictures, StatBySong, Song}.kt}`.
- Модуль: `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/{controllers/MainController.kt,
  controllers/PublicApiController.kt, dto/ZakromaPublicDto.kt, services/ShareLinkSweeper.kt}`.
- Модуль: `karaoke-public/src/{views/{SearchView, ZakromaView, AuthorPlaylistView}.vue,
  composables/useZakromaStreamProgress.js}`.
- Frontend: `webvue3/` — **вне scope** (Clarifications Q5: только `SubsEdit.vue:183` label
  остаётся, это не Song).

## История

- Создан: 2026-08-28
- Последнее обновление: 2026-08-28
