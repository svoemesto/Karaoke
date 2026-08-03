---

description: "Task list for feature 128-news-publish-templates"
---

# Tasks: Шаблоны публикаций авто-новостей сайта

**Input**: Design documents from `/specs/128-news-publish-templates/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/api.md, quickstart.md

**Tests**: Тесты в CI не используются (см. AGENTS.md «Тесты»). Задач
тест-генерации нет; валидация — через `quickstart.md` сценарии в
Phase 6 (Polish).

**Organization**: Tasks grouped by user story (US1=air, US2=premium,
US3=preview, US4=defaults). Foundational phase создаёт сервис,
контроллер (все 4 endpoints), и UI-каркас вкладки — все stories
зависят от него, но stories между собой независимы.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: User story label (US1, US2, US3, US4) — only for story-phase tasks
- Include exact file paths in descriptions

## Path Conventions

Web app (backend + admin frontend):
- Backend: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/`
- Backend (web): `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/`
- Admin frontend: `webvue3/src/`
- Per-feature docs: `docs/features/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Фича-ветка уже создана (`128-news-publish-templates`),
конституция проверена в `plan.md`. Setup минимальный — без новой
инфраструктуры (БД, миграции, фреймворк — существующие).

- [ ] T001 [P] Зарезервировать номер ветки через `./tools/reserve-branch-number.sh` (если ветка уже создана — пропустить) и убедиться что `git branch --show-current` = `128-news-publish-templates`
- [ ] T002 [P] Заготовить per-feature документ `docs/features/news-templates.md` со структурой «Что делает / Зачем / Как работает / Инварианты / Известные ловушки / Ссылки» (FR-009 AGENTS.md) — наполнить в Phase 6 (Polish)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Backend-сервис `NewsTemplateService`, контроллер
`NewsTemplateController` со всеми 4 endpoints (по образцу
`/api/vk/templates/*`), и UI-каркас вкладки «Новости сайта» в
`PublishTemplatesView.vue`. Всё, что нужно всем 4 stories.

**⚠️ CRITICAL**: Ни одна user story не может начать UI/backend-правки
до завершения этой фазы.

- [ ] T003 Создать `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/NewsTemplateService.kt` — object с `DEFAULT_AIR_TITLE`, `DEFAULT_AIR_BODY`, `DEFAULT_PREMIUM_TITLE`, `DEFAULT_PREMIUM_BODY` (byte-идентичны хардкоду `SongReleaseAnnouncementService`), `NEWS_TITLE_MAX_LENGTH=500`, `PLACEHOLDERS` (14 плейсхолдеров из contracts/api.md §1), `template(key, database)` (JDBC-чтение `tbl_public_settings` с fail-open к DEFAULT_*), `render(template, song, news)` (замена плейсхолдеров, неизвестные — literal, усечение title до 500 с `…`), `placeholders()` (для UI). Перенести хелперы `albumYearSuffix(song)` и `bodyDetails(song)` из `SongReleaseAnnouncementService` (byte-идентично). См. contracts/api.md §«Внутренний контракт»
- [ ] T004 Создать `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/NewsTemplateController.kt` — `@RestController @RequestMapping("/api/news/templates")`, 4 endpoints: `GET /` (список 4 ключей + placeholders, query `target=local|remote`), `POST /` (UPSERT с `key`+`value`+`target`, валидация `key in allowedNewsTemplateKeys`, `INSERT ... ON CONFLICT (key) DO UPDATE`), `POST /preview` (render `titleTemplate`+`bodyTemplate` на song по `id`, возврат `title`/`body`/`titleLength`/`titleTruncated`/`titleMaxLength`/`bodyLength`/`bodyTruncated`), `GET /defaults` (4 заводских значения из `NewsTemplateService.DEFAULT_*`). Использовать `resolveDb(target)` хелпер по образцу `PublicSettingsController.withDb`. Контракт: `specs/128-news-publish-templates/contracts/api.md`
- [ ] T005 [P] Зарегистрировать `NewsTemplateController` как Spring-бин (если `@ComponentScan` не покрывает пакет) — проверить `@SpringBootApplication` scan path в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeAppApplication.kt`; если контроллер не подхватывается — добавить `@Component` или явный `@Import`. Проверить `GET /api/news/templates/defaults` через `curl` после локального старта (на `dev-pc` под `dev` — без согласия)
- [ ] T006 Создать `webvue3/src/components/NewsTemplates/NewsTemplatesEditor.vue` — каркас компонента: `nav nav-pills` переключатель «В эфире (air)» / «В коллекции (premium)» (по образцу `VkTemplatesEditor.vue`), `<template #placeholders>` список плейсхолдеров (получает через `GET /api/news/templates`), состояние `currentType: 'air'|'premium'`, `target: 'local'|'remote'` select (по образцу `PublicSettingsTable.vue`), пустые `v-model`-поля `titleTemplate`/`bodyTemplate` для текущего типа. Без логики сохранения/превью/сброса (эти — в stories)
- [ ] T007 [P] В `webvue3/src/views/PublishTemplatesView.vue` добавить третью вкладку «Новости сайта» в существующий `nav nav-tabs` (после «ВКонтакте» и «Telegram»), импортировать `NewsTemplatesEditor` и рендерить его при выборе. Не ломать существующие вкладки (FR-001)

**Checkpoint**: Backend-сервис и контроллер работают (`curl
/api/news/templates/defaults` возвращает 4 ключа), UI-вкладка
открывается с пустыми полями и переключателем air/premium. Stories
можно реализовывать.

---

## Phase 3: User Story 1 - Администратор редактирует авто-новость «в эфире» (Priority: P1) 🎯 MVP

**Goal**: Администратор правит `title`/`body` для типа «В эфире (air)»,
сохраняет (UPSERT в `tbl_public_settings`), и следующая auto-новость
категории `air` формируется по новому шаблону — без перезапуска.

**Independent Test** (quickstart.md Сценарий 2): открыть вкладку «Новости
сайта» → тип «air» → добавить эмодзи 🎵 в `body` → сохранить с
`target=remote` → смоделировать выход песни в эфир → `SELECT body FROM
tbl_news WHERE category='air' ORDER BY id DESC LIMIT 1` содержит эмодзи.

### Implementation for User Story 1

- [ ] T008 [US1] В `webvue3/src/components/NewsTemplates/NewsTemplatesEditor.vue` для типа `air` — заполнить `titleTemplate`/`bodyTemplate` из ответа `GET /api/news/templates` (ключи `newsTemplateAirTitle`/`newsTemplateAirBody`, пустое `value` → показать `default` как placeholder), добавить кнопку «Сохранить» → `POST /api/news/templates` с `key`+`value`+`target`. Подсветка плейсхолдеров + предупреждение о несбалансированных скобках (по образцу `VkTemplatesEditor.vue`). FR-003, FR-004, FR-005
- [ ] T009 [US1] В `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/SongReleaseAnnouncementService.kt` в методе `checkOnAirWindow` (категория `air`) заменить хардкод `title`/`body` на `NewsTemplateService.render(NewsTemplateService.template("newsTemplateAirTitle", database), song)` и `NewsTemplateService.render(NewsTemplateService.template("newsTemplateAirBody", database), song, news)`. Перед правкой — найти точные строки через `codegraph_explore "SongReleaseAnnouncementService checkOnAirWindow"` (номера строк хрупки и не зафиксированы в этом файле). Параметр `news` — связанная новость (если есть) для плейсхолдера `{newsBody}` (FR-006). FR-006, FR-007, FR-010 (byte-идентичность дефолтов гарантируется `DEFAULT_*` + `albumYearSuffix`/`bodyDetails`); усечение `title` до 500 с `…` — в `NewsTemplateService.render` (FR-010a)
- [ ] T010 [US1] Проверить, что `News.createAutoAnnouncement` (`karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/News.kt:337`) принимает отрендеренные `title`/`body` без правок сигнатуры. Удостовериться что `isNewsAutoPublishKillSwitchActive` (kill-switch `newsAutoPublishKillSwitch`) сохраняет приоритет — шаблон не рендерится если kill-switch `"true"`. FR (см. plan.md «Non-Goals»)

**Checkpoint**: US1 функционален и тестируем независимо. `tbl_news`
категории `air` создаётся по сохранённому шаблону, дефолт byte-идентичен
хардкоду.

---

## Phase 4: User Story 2 - Администратор редактирует авто-новость «в коллекции» (Priority: P1)

**Goal**: Администратор правит `title`/`body` для типа «В коллекции
(premium)», сохраняет, и следующая auto-новость категории `premium`
(создаваемая в `detectAndAnnounceAvailability` при sync) — по новому
шаблону.

**Independent Test** (quickstart.md Сценарий 3): изменить `title`
premium на `{author} — {songName} (премиум)`, сохранить с `target=remote`,
смоделировать `newsAvailableAnnounced` false→true через sync → новая
`tbl_news` `category='premium'` содержит новый `title`.

### Implementation for User Story 2

- [ ] T011 [P] [US2] В `webvue3/src/components/NewsTemplates/NewsTemplatesEditor.vue` для типа `premium` — аналогично T008, но ключи `newsTemplatePremiumTitle`/`newsTemplatePremiumBody`. Если T008 уже сделал общую логику для обоих типов (рекомендуется) — этот таск сводится к проверке что переключение `currentType='premium'` корректно подгружает premium-значения и сохраняет в правильный key. FR-002, FR-003
- [ ] T012 [US2] В `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/SongReleaseAnnouncementService.kt` в методе `detectAndAnnounceAvailability` (категория `premium`) заменить хардкод `title`/`body` на рендер через `NewsTemplateService.template("newsTemplatePremiumTitle"/"newsTemplatePremiumBody", database)` + `NewsTemplateService.render(..., news = null)` (для premium `news` нет). Перед правкой — найти точные строки через `codegraph_explore "SongReleaseAnnouncementService detectAndAnnounceAvailability"` (номера строк хрупки). FR-006, FR-007, FR-010, FR-010a (усечение title)
- [ ] T013 [US2] Проверить на проде (через `karaoke-web` / `MainController.doChangeRecords`) что sync-триггер `Song.newsAvailableAnnounced=false→true` создаёт auto-новость `category='premium'` с новым шаблоном. Удостовериться что `News.existsAnnouncement` (идемпотентность) сохраняет поведение — повторный sync не создаёт дубликат

**Checkpoint**: US1 и US2 оба функциональны и тестируемы независимо.

---

## Phase 5: User Story 3 - Превью шаблона на тестовой песне (Priority: P2)

**Goal**: Превью возвращает отрендеренные `title` и `body` раздельно
на тестовой песне, без записи в `tbl_news`. Неизвестные плейсхолдеры
остаются literal.

**Independent Test** (quickstart.md Сценарий 4): ввести id песни →
«Превью» → показаны `title` и `body` с подставленными значениями. В
шаблон добавить `{nonexistent}` → превью содержит literal
`{nonexistent}`.

### Implementation for User Story 3

- [ ] T014 [US3] В `webvue3/src/components/NewsTemplates/NewsTemplatesEditor.vue` добавить блок превью: поле `songId` (input number), кнопка «Превью» → `POST /api/news/templates/preview` с `titleTemplate`+`bodyTemplate` (текущие значения полей, не сохранённые) + `id` + `target`. Отобразить `title`/`body` отдельно, с бейджами `titleLength`/`titleMaxLength`/`titleTruncated` (если `titleTruncated=true` — красный бейдж «усечён»). FR-009, FR-014
- [ ] T015 [US3] Проверить через `curl POST /api/news/templates/preview` что endpoint возвращает `success:false` для несуществующего `id` (без 500), и что для шаблона с `{nonexistent}` literal-текст сохраняется в обоих полях. Удостовериться что `SELECT count(*) FROM tbl_news` не увеличился после preview. FR-005 (Edge Case). **SLA SC-003**: измерить время ответа через `curl -w '%{time_total}\n' -o /dev/null -s -X POST ...` на типичной песне — ДОЛЖНО быть ≤ 3 сек; если больше — профилировать `NewsTemplateService.render` (возможно `Song.loadFromDbById` — узкое место, но оно вне hot-path рендера и кешируется Spring). Зафиксировать измеренное значение в PR-описании

**Checkpoint**: US3 функционален и тестируем независимо.

---

## Phase 6: User Story 4 - Сброс шаблона к дефолту (Priority: P3)

**Goal**: Кнопка «Сбросить к дефолту» заполняет поле заводским
значением (без автосохранения — нужно нажать «Сохранить» отдельно).

**Independent Test** (quickstart.md Сценарий 5): изменить `body` типа
`air`, нажать «Сбросить к дефолту» → поле вернулось к `Песня
«{songName}» ({bodyDetails}) вышла в эфир.`. `SELECT value FROM
tbl_public_settings WHERE key='newsTemplateAirBody'` — не изменился.

### Implementation for User Story 4

- [ ] T016 [US4] В `webvue3/src/components/NewsTemplates/NewsTemplatesEditor.vue` добавить кнопку «Сбросить к дефолту» для каждого поля (`title` и `body` независимо — по образцу ВК/Telegram, FR-003). Клик → `GET /api/news/templates/defaults` (один раз при первом открытии вкладки, кешировать в `data()`), заполнить `titleTemplate`/`bodyTemplate` соответствующим значением из `defaults` (по `currentType`). НЕ вызывать `POST` — только UI-состояние. FR-013

**Checkpoint**: US4 функционален и тестируем независимо.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Per-feature документация (FR-009 AGENTS.md), lint-гейты
CI 7/7 (NON-NEGOTIABLE для merge в master), обновление `docs/features/README.md`,
`architecture-notes.md`.

- [ ] T017 [P] Наполнить `docs/features/news-templates.md` (заготовлен в T002) содержимым по структуре `check-feature-doc.sh`: «Что делает» (4 шаблона в `tbl_public_settings`), «Зачем» (снять зависимость от разработчика), «Как работает» (`NewsTemplateService.render` + точки применения), «Инварианты» (kill-switch приоритет, byte-идентичность дефолтов, UPSERT без миграций), «Известные ловушки» (kill-switch, `target=remote` обязателен для прода, `tbl_public_settings` вне SyncRegistry), «Ссылки» (spec/contracts/research/quickstart)
- [ ] T018 [P] Добавить запись в `docs/features/README.md` — строка в таблицу: `news-templates.md` | Шаблоны авто-новостей сайта (air+premium) | FR-001…FR-016 spec 128
- [ ] T019 [P] Добавить `@see docs/features/news-templates.md` во **все** публичные API фичи (constitution FR-006 — без этого `check-kdoc-coverage.sh` и `check-jsdoc-coverage.sh` упадут, T020 не пройдёт): (1) KDoc в класс `NewsTemplateService` (`karaoke-app/.../services/NewsTemplateService.kt`); (2) KDoc в класс `NewsTemplateController` (`karaoke-app/.../controllers/NewsTemplateController.kt`); (3) JSDoc `@see docs/features/news-templates.md` в `<script setup>` блоке `webvue3/src/components/NewsTemplates/NewsTemplatesEditor.vue`; (4) JSDoc `@see` в правке `webvue3/src/views/PublishTemplatesView.vue` (если новый import добавлен в `<script setup>`). После правок — прогнать `bash tools/check-kdoc-coverage.sh` и `bash tools/check-jsdoc-coverage.sh webvue3`, оба должны быть 100%
- [ ] T020 Запустить локально проверку CI 7/7: `./gradlew ktlintCheck`, `cd webvue3 && npm run lint:check`, `cd karaoke-public && npm run lint:check`, `bash tools/check-kdoc-coverage.sh`, `bash tools/check-jsdoc-coverage.sh webvue3`, `pre-commit run --all-files`, `./gradlew :karaoke-app:compileKotlin`. Все зелёные, baseline не растёт. См. `docs/features/ci-lint-enforcement.md`
- [ ] T021 [P] Добавить запись в `docs/architecture-notes.md` (отдельный коммит в master после merge PR — см. AGENTS.md CI-gate «Исключения для документации-only»): Pass N, PR #NNN, фича 128 — что сделано, какие паттерны применены, ловушки
- [ ] T022 Прогнать все 8 сценариев `specs/128-news-publish-templates/quickstart.md` вручную (где возможно — на `dev-pc` под `dev`; где нужен прод — согласовать с пользователем). Зафиксировать результаты в PR-описании

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: Нет зависимостей — T001, T002 параллельны
- **Foundational (Phase 2)**: T003 → T004 → T005 (последовательно: сервис → контроллер → регистрация). T006 → T007 (UI: компонент → интеграция в view). T003/T004 и T006/T007 можно вести параллельно (разные файлы, backend vs frontend)
- **User Stories (Phases 3-6)**: Все зависят от Foundational:
  - US1 (Phase 3): T008 (UI) + T009 (backend hook) + T010 (verify) — последовательно в рамках story
  - US2 (Phase 4): T011 [P] (UI, можно параллельно с T008 если общий компонент) + T012 (backend hook) + T013 (verify)
  - US3 (Phase 5): T014 (UI) + T015 (verify) — зависит только от Foundational, независим от US1/US2
  - US4 (Phase 6): T016 (UI only) — зависит только от Foundational, независим от US1/US2/US3
- **Polish (Phase 7)**: T017, T018, T019, T021 параллельны; T020 после всех stories; T022 после T020

### User Story Dependencies

- **US1 (P1)**: После Foundational. Backend-хук в `checkOnAirWindow` (T009) и UI форма air (T008) — независимы друг от друга, можно параллельно
- **US2 (P1)**: После Foundational. Аналогично US1, но для `detectAndAnnounceAvailability`. Независим от US1 (разные методы, разные ключи)
- **US3 (P2)**: После Foundational. Зависит только от `NewsTemplateController.preview` (T004). Не трогает `SongReleaseAnnouncementService`
- **US4 (P3)**: После Foundational. Зависит только от `NewsTemplateController.defaults` (T004) и UI-каркаса (T006). Не трогает backend-рендеринг

### Parallel Opportunities

- T001, T002 (Setup) — параллельны
- T003+T004 (backend service+controller, последовательны) vs T006+T007 (frontend, последовательны) — две дорожки параллельны
- T008 (US1 UI air) и T011 (US2 UI premium) — [P], если общий компонент `NewsTemplatesEditor` уже делает оба типа в T006, то эти таски сводятся к проверке
- T017, T018, T019, T021 (Polish docs) — все [P], разные файлы
- US3 (T014, T015) и US4 (T016) — полностью независимы от US1/US2 backend-хуков, можно вести параллельно с ними

---

## Parallel Example: Foundational Phase

```bash
# Backend track (один разработчик):
Task: T003 "Создать NewsTemplateService.kt"
Task: T004 "Создать NewsTemplateController.kt"
Task: T005 "Зарегистрировать бин, проверить curl"

# Frontend track (параллельно, другой разработчик):
Task: T006 "Создать NewsTemplatesEditor.vue каркас"
Task: T007 "Добавить вкладку в PublishTemplatesView.vue"
```

## Parallel Example: User Stories 1+3+4

```bash
# После Foundational — три независимых потока:
Task: T008+T009+T010 (US1: air UI + backend hook + verify)
Task: T014+T015 (US3: preview UI + verify) — не трогает SongReleaseAnnouncementService
Task: T016 (US4: defaults button UI) — только UI
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Phase 1 (Setup) — skip, ветка уже создана; T002 заготовка doc
2. Phase 2 (Foundational) — критично, блокирует всё
3. Phase 3 (US1: air) — минимальная ценность: админ правит формулировку
4. **STOP and VALIDATE**: quickstart.md Сценарии 1+2 — byte-идентичность
   дефолтов + правка без перезапуска
5. Demo пользователю; если OK — продолжить

### Incremental Delivery

1. Foundational → Foundation ready (curl `/defaults` работает, UI
   вкладка открывается)
2. +US1 → MVP (air формулировки редактируемы) → Demo
3. +US2 → premium формулировки редактируемы → Demo
4. +US3 → превью снижает риск опечаток → Demo
5. +US4 → сброс к дефолту (страховка) → Demo
6. Polish → per-feature doc + CI 7/7 → PR → merge

### Parallel Team Strategy

С 2 разработчиками:
- Backend: T003 → T004 → T005 → T009 → T012 → T020
- Frontend: T006 → T007 → T008 → T011 → T014 → T016 → T017 → T018 → T019

---

## Notes

- All file paths absolute-from-repo-root или относительно `karaoke-app/`/`karaoke-web/`/`webvue3/` (см. plan.md §Project Structure)
- [Story] label: US1=air, US2=premium, US3=preview, US4=defaults — только в story-фазах
- Foundational T003+T004 — последовательны (контроллер использует сервис)
- T009/T012 — правки одного файла (`SongReleaseAnnouncementService.kt`), но разных методов — не конфликтуют
- Lint-гейты CI 7/7 — NON-NEGOTIABLE для merge в master (AGENTS.md CI-gate)
- Без миграций БД: UPSERT в `tbl_public_settings` создаёт 4 ключа при первом сохранении (research.md R2)
- Kill-switch `newsAutoPublishKillSwitch` сохраняет приоритет — фича НЕ обходит (T010 verify)
- `News.createAutoAnnouncement` сигнатура НЕ меняется (T010 verify)