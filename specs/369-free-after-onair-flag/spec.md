# Feature Specification: Флаг «не снимать с эфира» (free_after_on_air)

**Feature Branch**: `369-free-after-onair-flag`

**Created**: 2026-09-11

**Status**: Draft

**Input**: User description: "Работа над задачей #81 в трекере OpenProject"

## Clarifications

### Session 2026-09-11

- Q: Как должен вести себя флаг `freeAfterOnAir` если на той же песне уже включён `free=true` (всегда бесплатно)? → A: Независимы, флаг хранится. `free=true` уже делает песню всегда бесплатной; `freeAfterOnAir=true` после эфира тоже. В БД хранятся независимо. Если позже отключить `free=true` — `freeAfterOnAir` остаётся как «запасной» флаг.
- Q: Когда пользователь включает `freeAfterOnAir=true` на песне, у которой `dateTimePublish` ещё в будущем — должно ли поле действовать сразу или только после наступления эфира? → A: Только после эфира. До наступления `dateTimePublish` песня и так premium-only (по обычной логике). После эфира — `freeAfterOnAir=true` означает «не снимать».

## OpenProject Tracking *(MANDATORY — see AGENTS.md § Issue-tracker OpenProject)*

### Идентификация

- **Issue ID**: `#81`
- **Title**: Флаг "не снимать с эфира"
- **Created in OpenProject**: 2026-09-11

### Workflow (NON-NEGOTIABLE при наличии Issue ID)

| Шаг | Команда | Когда | Кто |
|---|---|---|---|
| 1. **Claim** | `source .env.local-tracker && bash tools/tracker.sh claim-issue 81` | ПЕРЕД первой строкой кода спеки (выполнено через `tracker-bootstrap.sh` hook на стадии `/speckit.specify`). | Agent |
| 2. **Pre-flight Knowledge** | `spec.md § Knowledge References` | Согласно Constitution Principle IX (см. ниже). | Agent |
| 3. **Work** | код, tests, knowledge updates | `/speckit.implement` | Agent |
| 4. **Add comment с отчётом** | `bash tools/tracker.sh add-comment 81 --file specs/369-free-after-onair-flag/report.md` | После merge. Файл `report.md` — REQUIRED. | Agent |
| 5. **Mark review** | `bash tools/tracker.sh mark-review 81` | После публикации комментария. | Agent |
| 6. **Close** | `bash tools/tracker.sh close-issue 81` | После ревью владельцем. | Agent или Owner |

## Knowledge References *(MANDATORY — see Constitution Principle IX)*

### Pre-flight log

- **Дата pre-flight**: 2026-09-11
- **Grep-запросы** (минимум 3, по релевантным ключевым словам задачи):
  1. `grep -rli "on[ _-]air\|onAir\|song.life\|free.*after\|free_after\|air.*window\|эфир" knowledge/` → `knowledge/domains/integration/components/song-public-dto.md`, `knowledge/domains/publishing/components/dictionaries.md`, `knowledge/domains/publishing/domain.md`, `knowledge/domains/stats/domain.md`, `knowledge/domains/catalog/components/entities-catalog.md`, `knowledge/domains/catalog/components/dictionaries.md`, `knowledge/domains/catalog/components/song-lifecycle.md`, `knowledge/domains/catalog/domain.md`, `knowledge/domains/editorial/components/dictionaries.md`, `knowledge/domains/editorial/components/assignment-lifecycle.md`, `knowledge/public/glossary.md`, `knowledge/public/onboarding.md`, `knowledge/system/frontend/composable-use-player-access.md`, `knowledge/guidelines/architecture-conventions.md`.
  2. `grep -lr "song-lifecycle\|Song.*Lifecycle\|song_status\|song.status\|song.state\|state_song" knowledge/` → подтвердил, что канонический документ по переходам — `knowledge/domains/catalog/components/song-lifecycle.md`.
  3. `grep -lr "publishDate\|onAir\|on_air\|publish.*window\|окно публикации" knowledge/` → `knowledge/domains/integration/components/song-public-dto.md`, `knowledge/domains/publishing/components/dictionaries.md`, `knowledge/domains/publishing/components/stats-cache.md`, `knowledge/domains/publishing/components/publishing-services.md`, `knowledge/domains/publishing/domain.md`, `knowledge/domains/catalog/components/entities-catalog.md`, `knowledge/domains/catalog/components/dictionaries.md`, `knowledge/domains/catalog/components/song-lifecycle.md`, `knowledge/domains/catalog/domain.md`, `knowledge/domains/editorial/components/dictionaries.md`.

### Knowledge files consulted

- [`knowledge/domains/catalog/domain.md`](../../knowledge/domains/catalog/domain.md)
  — зачем прочитан: чтобы понять, как `Song` вписывается в bounded context и какие у него поля и инварианты (важно для добавления нового boolean-флага).
- [`knowledge/domains/catalog/components/song-entity.md`](../../knowledge/domains/catalog/components/song-entity.md)
  — зачем прочитан: описание всех хранимых полей `Song` (включая `free`, `dateTimePublish`, `idStatus`, `*_ready`) и hot-path использования `/api/songs/list`, `/api/songs/save`.
- [`knowledge/domains/catalog/components/song-lifecycle.md`](../../knowledge/domains/catalog/components/song-lifecycle.md)
  — зачем прочитан: чтобы выровнять семантику «эфир / окно эфира» с уже описанными переходами `IdStatus` и контрактом `publishDate`.
- [`knowledge/domains/catalog/components/dictionaries.md`](../../knowledge/domains/catalog/components/dictionaries.md)
  — зачем прочитан: чтобы зафиксировать новое поле/флаг в централизованном словаре магических кодов (там же есть `isContentReady()` и тег `SKIP`).
- [`knowledge/domains/publishing/domain.md`](../../knowledge/domains/publishing/domain.md)
  — зачем прочитан: чтобы понять, что publishing — контекст эфира и подписки; новая семантика «оставить в эфире навсегда» относится именно к publishing-контексту.
- [`knowledge/domains/publishing/components/dictionaries.md`](../../knowledge/domains/publishing/components/dictionaries.md)
  — зачем прочитан: чтобы не путать `isExclusive` (premium-only, бизнес-решение) и `publishDate в будущем` (тайминг); наш флаг — третий, отдельный, «не снимать с эфира».

### ADR reviewed

- На данный момент нет `local-XXXX` ADR, прямо фиксирующего модель D / поведение эфира после окна; в knowledge/adr/local-* ничего не упоминает `free_after_on_air` или «вечный эфир». См. замечание в § Assumptions — возможно, потребуется оформить `local-0009-song-free-after-on-air.md` в этом PR.

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Редактор помечает песню как «не снимать с эфира» (Priority: P1)

**Сценарий**: редактор/админ открывает в админке (`webvue3`, `SongEdit.vue`) страницу
песни, которая сейчас находится в эфире (`dateTimePublish` уже прошёл) и попадает
в стандартное окно бесплатного доступа (1 календарный месяц, см.
`specs/143-song-free-access-window`). Через 1 месяц окно эфира закроется, и песня
станет «premium-only» (по `dateTimePublish` в прошлом, но эфирный период истёк).
Редактор хочет, чтобы конкретная песня **оставалась доступной публично навсегда**,
не уходя в premium-only по таймеру, без ручного продления `dateTimePublish`.

**Why this priority**: это основная user-история задачи #81 — без неё фичи нет.
Также совпадает с уже описанной в Issue 81 потребностью.

**Independent Test**: можно взять любую песню со статусом «в окне эфира», открыть
SongEdit.vue, переключить новый флаг «Не снимать с эфира» в ДА → сохранить →
проверить, что после симулированного прохода «окна эфира» (через `freeAccessWindowMonths=0`
или мокирование времени) песня остаётся публично доступной.

**Acceptance Scenarios**:

1. **Given** песня в эфире (`idStatus ≥ 6`, `dateTimePublish ≤ now()`, окно эфира не истекло), **When** редактор в `SongEdit.vue` ставит новый флаг «Не снимать с эфира» в ДА и сохраняет, **Then** в БД сохраняется `free_after_on_air = true`; для этой песни `isFreelyAvailableNow` продолжает возвращать `true` после окончания окна эфира; `isPubliclyWatchable` остаётся `true`.
2. **Given** песня в эфире, **When** редактор выключает флаг, **Then** стандартная логика окна эфира (`freeAccessWindowMonths`) восстанавливается; после окончания окна песня перестаёт быть «freely available» (становится premium-only по обычным правилам).
3. **Given** песня ещё не вышла в эфир (`dateTimePublish` в будущем), **When** редактор сохраняет флаг «Не снимать с эфира», **Then** флаг сохраняется, но фактическая видимость до наступления эфира не меняется (песня по-прежнему premium-only до эфира). После эфира — включается семантика флага (clarification 2026-09-11: «только после эфира»).

---

### User Story 2 — Запрос публичного сайта учитывает новый флаг (Priority: P1)

**Сценарий**: посетитель сайта открывает страницу песни (или список эфирных песен)
после того, как стандартное окно эфира истекло. Если у песни установлен флаг
«Не снимать с эфира», она должна выглядеть и вести себя так же, как эфирная
(`AccessMode.open`), а не уходить в premium-only.

**Why this priority**: без корректной интеграции в публичный путь фича бесполезна —
флажок есть, а пользователи всё равно видят «premium-only».

**Independent Test**: взять песню, у которой `dateTimePublish < now()`, окно эфира
истекло, и `free_after_on_air = true`; через публичный API получить `access`
(`/api/songs/{id}/access`) — `AccessMode.open`. Без флага — `premium-only`.

**Acceptance Scenarios**:

1. **Given** песня прошла `dateTimePublish`, окно эфира истекло, `free_after_on_air = true`, **When** анонимный пользователь запрашивает `/api/songs/{id}/access`, **Then** возвращается `AccessMode.open`.
2. **Given** песня прошла `dateTimePublish`, окно эфира истекло, `free_after_on_air = false`, **When** анонимный пользователь запрашивает `/api/songs/{id}/access`, **Then** возвращается `AccessMode.premium-only` (текущее поведение).

---

### User Story 3 — Внутренние сервисы (StatBySong, авто-новости) корректно учитывают флаг (Priority: P2)

**Сценарий**: сервисы авто-публикации (`VkAutoPublishService`, `TelegramAutoPublishService`,
`SongReleaseAnnouncementService`) и счётчики `StatBySong` (главная страница) должны
отдавать песню как «эфирную / бесплатно доступную» без необходимости
трогать `publishDate`.

**Why this priority**: если флаг не «протекает» в эти сервисы, главная страница
будет показывать песню как premium-only — а это противоречит самой цели флага.

**Independent Test**: для песни с флагом `free_after_on_air = true` и истёкшим окном
эфира проверить, что `StatBySong`/главная страница относит её к категории
«В открытом доступе», а авто-сервисы считают её эфирной.

**Acceptance Scenarios**:

1. **Given** песня с флагом и истёкшим окном, **When** `Stat.kt` считает категории главной страницы, **Then** песня попадает в «В открытом доступе» (как если бы окно не истекло).
2. **Given** песня только что вышла в эфир и `free_after_on_air = true`, **When** запускается `SongReleaseAnnouncementService`, **Then** авто-новость публикуется один раз (как и сейчас, без дублирования).

### Edge Cases

- Что если `free_after_on_air = true`, но `free = true` уже? Оба флага независимы и хранятся в БД раздельно (clarification 2026-09-11): `free=true` уже делает песню «всегда бесплатной» (см. `Song.isFreelyAvailableNow`). Новый флаг должен **не ломать** существующую логику: при `free=true` песня и так всегда бесплатна — `free_after_on_air` фактически не имеет эффекта, но сохраняется без ошибок. Если позже отключить `free=true`, `free_after_on_air` остаётся как «запасной» флаг.
- Что если `isExclusive = true`? `isExclusive` имеет приоритет (см. `accessModeFor`) и возвращает `premium-only` независимо ни от чего. `free_after_on_air` в этом случае сохраняется, но не меняет публичный доступ.
- Что если `idStatus < 6` (песня в работе)? Флаг сохраняется, но публичный смысл не имеет (песня и так не публикуется до `idStatus=6`).
- Что если флаг включают после того, как авто-новость уже ушла? Ничего — флаг не должен ретриггерить `SongReleaseAnnouncementService` (он про «эфир», а флаг — про «оставаться в эфире после окна»).
- Что если `free_after_on_air` уже хранится в БД у части песен (для которых флаг был установлен ранее)? Должно продолжать работать без миграционных скриптов (только default).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST добавить к `Song` новое boolean-поле `freeAfterOnAir` (имя в коде и БД может быть `free_after_on_air` / `freeAfterOnAir` — должно быть согласовано между Kotlin и миграцией). По умолчанию `false`.
- **FR-002**: System MUST отображать флаг в `SongEdit.vue` в виде пары кнопок ДА/НЕТ рядом (или ниже) с уже существующим блоком «Всегда бесплатно (вечный эфир)», по тому же UX-паттерну, что и `setFree(true|false)`.
- **FR-003**: System MUST включать новое поле в `SongDTO`, чтобы оно было доступно в API `/api/songs/getById` и `/api/songs/save` без необходимости кастом-сериализации.
- **FR-004**: System MUST учитывать `freeAfterOnAir` в логике публичного доступа: песня с `freeAfterOnAir = true`, `dateTimePublish ≤ now()` (то есть эфир наступил) и истёкшим стандартным окном бесплатного доступа — должна оставаться публично доступной (`AccessMode.open`) без необходимости продлевать `dateTimePublish` или включать `isExclusive=false`.
- **FR-005**: System MUST учитывать `freeAfterOnAir` в `SongStateResolver.resolve()` (или эквивалентном месте) так, чтобы:
  - `idStatus < 6` → `SongState.IN_WORK` (как и сейчас);
  - `free = true` → `SongState.ON_AIR` (как и сейчас, приоритет);
  - **`freeAfterOnAir = true` И `dateTimePublish ≤ now()`** → `SongState.ON_AIR` (новое правило — приоритет над `TODAY`/`DONE`/`EXCLUSIVE`, но не над `free=true`; флаги независимы и хранятся в БД раздельно);
  - иначе — старая логика (EXCLUSIVE / TODAY / DONE).
- **FR-006**: System MUST учитывать `freeAfterOnAir` в `Song.isFreelyAvailableNow` так, чтобы после окончания стандартного окна песня продолжала считаться «freely available» при установленном флаге.
- **FR-007**: System MUST учитывать `freeAfterOnAir` в публичном API `/api/songs/{id}/access` (через `accessModeFor` или эквивалентный путь): при `freeAfterOnAir=true` и истёкшем окне — возвращать `AccessMode.open`.
- **FR-008**: System MUST НЕ менять поведение авто-новостей (`SongReleaseAnnouncementService`): флаг не должен ретриггерить публикацию новости «вышла в эфир» после её первого срабатывания (т.к. этот сервис логически привязан к первому переходу `isPubliclyWatchable`).
- **FR-009**: System MUST сохранить обратную совместимость: `free=true` остаётся отдельным флагом с прежней семантикой; `freeAfterOnAir` — отдельный, не должен затирать `free`.
- **FR-010**: System MUST НЕ упоминать флаг `freeAfterOnAir` в публичных маркетинговых материалах (см. `docs/strategy/growth.md`, принцип «сайт-центричная модель»). Только внутренний UI редактора и публичный движок доступа.
- **FR-011**: System MUST добавить запись про новый флаг в `knowledge/domains/catalog/components/dictionaries.md` (централизованный словарь) и в `knowledge/domains/catalog/components/song-entity.md` (список хранимых полей).
- **FR-012**: System MUST добавить запись про новый флаг в per-feature документ `docs/features/song-air-access.md` (или создать его, если отсутствует — см. `docs/features/`).

### Key Entities *(include if feature involves data)*

- **Song.freeAfterOnAir**: новое boolean-поле сущности `Song` (домен `catalog`). Семантика: «после того как песня вышла в эфир (`dateTimePublish ≤ now()`), не снимать её с эфира по таймеру стандартного окна бесплатного доступа». Не путать с:
  - `Song.free` — «всегда бесплатно, независимо от эфира».
  - `Song.isExclusive` — «premium-only по бизнес-решению».
  - `Song.dateTimePublish` — «момент выхода в эфир».

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: В `SongEdit.vue` для любой песни редактор может включить/выключить флаг «Не снимать с эфира» одним кликом, без перезагрузки страницы, без ошибок валидации; после сохранения поле отображается корректно при повторном открытии.
- **SC-002**: При `freeAfterOnAir = true` и `dateTimePublish ≤ now()` песня возвращает `AccessMode.open` через `/api/songs/{id}/access`, даже если стандартное окно бесплатного доступа истекло. Проверяется unit-тестом `SongStateResolver` / property-based тестом на доступе.
- **SC-003**: Существующие песни (без миграционных скриптов) видят новый флаг как `false` (default), поведение публичного доступа не изменилось ни для одной из них.
- **SC-004**: Все эфирные/бесплатные счётчики главной страницы (`Stat.kt`, `StatsCacheScheduler`) корректно учитывают новый флаг — песня с флагом продолжает попадать в категорию «В открытом доступе» после истечения окна. Подтверждается e2e-проверкой сценария «песня с истёкшим окном + флаг = ON_AIR».
- **SC-005**: Все добавленные/изменённые unit-тесты (`SongStateResolverTest`, `SongAccessTest`, тесты `SongEdit.vue` или эквивалентные) проходят, без flake; покрытие `SongStateResolver` остаётся ≥80%.

## Assumptions

- Предполагается, что новое поле будет добавлено через миграцию в
  `deploy/karaoke-db/<NNN>_tbl_songs_free_after_on_air.sql`
  (`ALTER TABLE ... ADD COLUMN ... DEFAULT false NOT NULL` — стандартный
  идемпотентный паттерн Karaoke).
- Предполагается, что `Song.state` поля `freeAfterOnAir` будет
  сохраняться через тот же `KaraokeDbTable` reflection-diff, что и
  остальные поля (никакого ручного SQL UPDATE).
- Предполагается, что в `webvue3` поле будет доступно через
  `song.freeAfterOnAir` (JS-сторона) и `store/song-store`/`store/song-editor`
  без необходимости создавать отдельный store.
- Предполагается, что для публичного API `AccessMode` флаг
  не выделяется в отдельный enum-кейс, а влияет на расчёт `accessModeFor()`
  (сохраняем существующие `OPEN` / `PREMIUM_ONLY`).
- Предполагается, что **никакого нового `local-XXXX` ADR** не требуется
  для самого факта добавления флага, но если в ходе `/speckit.plan` будет
  принято более широкое решение (например, общая модель «вечного эфира»),
  — оформить `local-0009-song-free-after-on-air.md`.
- **Принцип «не упоминать в публичных материалах»** (FR-010): модель D
  (гибрид) уже описана в `docs/strategy/growth.md`; конкретно новый
  внутренний флаг не должен появляться в рекламных текстах и
  комментариях для конечных пользователей.

## Out of Scope

- Миграция существующих песен с «истёкшим окном» на новый флаг (решается
  вручную через SongEdit.vue при необходимости).
- Полная переработка модели эфира (Модель D) — в этом PR только флаг.
- Поддержка эфира «по событию» (например, эфир «на 7 дней после даты
  регистрации пользователя») — не относится к этой задаче.
- Возможность задать флаг «через N дней после эфира снова premium-only»
  (т.е. автоматический эфир-N-дней снимается полностью). В этом PR только
  бинарный «снимать / не снимать».