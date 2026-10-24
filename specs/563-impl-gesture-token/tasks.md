---

description: "Задачи реализации фичи 563 — закрытие обхода оплаты по скрытому жесту (спека 557, OpenProject #358, аудит BR-018)"
---

# Tasks: 563 — Закрыть обход оплаты по «скрытому жесту»

**Input**: Design documents from `/specs/563-impl-gesture-token/`

**Prerequisites**: plan.md, research.md, quickstart.md; контракт — `../557-close-gesture-token-paywall/spec.md`

**Tests**: включены — спека FR-011 требует регрессионный тест «до/после».

**Organization**: задачи сгруппированы по пользовательским сценариям; каждый сценарий —
самостоятельный проверяемый инкремент.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: можно выполнять параллельно (разные файлы, нет зависимостей)
- **[Story]**: сценарий из spec.md 557 (US1 — P1, US2 — P2, US3 — P3)

## Path Conventions

Монорепозиторий: `karaoke-web/`, `karaoke-app/`, `karaoke-public/`, `docs/features/`, `knowledge/`.

---

## Phase 1: Setup

**Purpose**: зафиксировать контракт решения о доступе до правки поведения.

- [x] T001 Создать каталог артефактов `specs/563-impl-gesture-token/` (research.md, plan.md, tasks.md, quickstart.md) — по образцу `specs/554-close-public-sql-endpoint/`
- [x] T002 Зафиксировать в `research.md` решения D1…D10 (жест не даёт доступа; живая проверка права в момент отдачи байт; id пользователя в токене; демо-понижение вместо ошибки; fail-closed обрезка; категория журнала; живой премиум для выгрузки; что не меняем; тесты; минимум правок во фронте)
- [x] T003 [P] Обновить `specs/336-code-audit/registry.csv`: BR-018 → статус «исправлено» (work_package 358 уже проставлен)

**Checkpoint**: контракт решения зафиксирован до изменения кода; поведение ещё не менялось.

---

## Phase 2: Foundational — правило доступа

**Purpose**: вынести «право» в одно место, проверяемое в момент запроса; от которого зависят все сценарии.

- [x] T004 [P] В `karaoke-web/.../services/PlayerGestureUnlockService.kt`: удалить механику жеста (`registerClick`, `ClickBucket`, `clickBuckets`, `TARGET_FIELD`, `REQUIRED_CLICKS`, `CLICK_WINDOW_MS`), добавить в `TokenInfo` поле `userId: Long?`, заменить `validateToken`/`demoRangeForToken`/`assignmentIdForToken` на единый `describeToken(token, songId): TokenFacts?` (вид токена: FULL/DEMO/ASSIGNMENT + userId + assignmentId + demoRange). TTL остаётся 30 минут (FR-013)
- [x] T005 [P] Создать `karaoke-web/.../services/PlayerRightsProvider.kt` — живой источник прав из БД: `isPremium(siteUserId)` (загрузка `SiteUser` → `isEffectivePremium`), `isSubscribedToSong(siteUserId, songId)` (`Subscription.isSubscribedToSong`), `isAssignmentOwner(assignmentId, siteUserId, songId)` (`SongAssignment.getById` → `assigneeId`/`songId`)
- [x] T005a [P] Создать `karaoke-web/.../services/PlayerSongLoader.kt` — вынести статический `Song.loadFromDbById` из контроллера в инъецируемый сервис, чтобы путь «песня → право → байты» тестировался без БД (research D10)
- [x] T006 Создать `karaoke-web/.../services/PlayerAccessService.kt` — правило решения: `evaluate(song, token, session): AccessDecision` с полями `full`, `demoRange`, `shareGuest`, `reason`. Порядок оснований (research D2): публичность песни → живой премиум → живая подписка → гостевая share-сессия → владение заданием редактора → демо-токен → понижение «полного» токена без права до демо-фрагмента песни → нет доступа (`None`)
- [x] T007 Зарегистрировать категорию журнала `infra.player.access` в `knowledge/domains/monitoring/components/log-categories.md` (`player:access:denied` — WARN, `player:access:demo` — INFO; поля `songId=`, `reason=`; токен и аудио не пишутся)

**Checkpoint**: правило доступа существует в одном месте, тестируется без БД; поведение эндпоинтов ещё не менялось.

---

## Phase 3: User Story 1 — Анонимный «жест» не открывает полный доступ (P1)

**Story goal**: анонимный посетитель не получает ни значения доступа из публичного эндпоинта событий, ни полных байт/разметки платной песни.

**Independent test**: три «жеста» через `POST /api/public/events` → в ответе нет значения доступа; запрос полного стема отклоняется (quickstart, проверка 1).

- [x] T008 [US1] В `PublicApiController.events()`: убрать ветку жеста (вызов `gestureUnlockService.registerClick`) и выдачу значения в `meta` — поле остаётся, но всегда `null`; убрать ненужную зависимость `gestureUnlockService` из конструктора
- [x] T009 [US1] В `PublicPlayerController`: заменить `authorized()`/`isShareGuest()` на живое решение `PlayerAccessService.evaluate(...)`; в `fileAccompaniment`/`fileVocals`/`fileBass`/`fileDrums`/`playerData`/`playerFile` — применять решение: `full` → полные байты/разметка, `demoRange` → обрезка байт (`Mp3Trimmer`) и маркеров, `None` → 404
- [x] T010 [US1] В `PublicApiController`/`PublicSongEditorController`: выдавать токен с id пользователя (`siteUserResolver.resolve(request)?.id`), чтобы право можно было перепроверить в момент отдачи байт
- [x] T011 [US1] Написать регрессионный тест «до/после» в `karaoke-web/src/test/kotlin/com/svoemesto/karaokeweb/controllers/PublicPlayerControllerAccessTest.kt`: (а) жест из трёх кликов → `meta` не содержит значения доступа [ДО правки — падает: `meta` содержит токен]; (б) анонимный запрос полного стема платной песни → отказ [ДО правки — падает: отдаются полные байты]
- [x] T012 [US1] Написать тест «токен без действующего права не открывает полный доступ» в том же файле: токен, выданный как «прямой» (структурно неотличим от жестового), на платной песне у анонима → сервер отдаёт демо-фрагмент, а не полный файл

**Checkpoint**: публичный эндпоинт событий не выдаёт доступа; байты платной песни анониму не отдаются; попытка видна в журнале.

---

## Phase 4: User Story 2 — Законные пользователи не теряют доступ (P2)

**Story goal**: премиум, подписчик песни, слушатель эфира, гость по share-ссылке и редактор с заданием получают ровно то же, что и до правки; посетитель без полного доступа по-прежнему слышит демо-фрагмент.

**Independent test**: матрица решений — все пять оснований и все отказы (quickstart, проверка 2).

- [x] T013 [US2] Написать матрицу решений в `karaoke-web/src/test/kotlin/com/svoemesto/karaokeweb/services/PlayerAccessServiceTest.kt`: публичная песня → full; премиум → full; подписка на песню → full; подписка на ДРУГУЮ песню → demo; гостевая share-сессия → full; задание редактора → full; потерянное задание → отказ; демо-токен → demo; «полный» токен без права → demo; токен от другой песни → отказ; без токена и без сессии → отказ
- [x] T014 [US2] В `PublicPlayerController.playerFile`: оставить выгрузку пакета только при живом премиуме; источник премиума — заголовок `Authorization` ИЛИ id пользователя из токена (research D7); гость по share-ссылке — 403
- [x] T015 [US2] Проверить, что демо-режим сохранён: `playerdata` при решении `demo` отдаёт обрезанные и перебазированные маркеры, `isDemo=true`, `demoFadeInSeconds`; байты стема обрезаются на сервере
- [x] T016 [US2] В `Mp3Trimmer.trimToRange`: заменить фолбэк «вернуть исходные байты» на пустой массив — полный файл не должен покидать сервер даже при неразбираемом потоке (FR-005, research D5)

**Checkpoint**: все легитимные пути работают; демо-режим не изменился; полный файл не уходит ни при каких условиях.

---

## Phase 5: User Story 3 — Отказы видны, правило одно (P3)

**Story goal**: администратор видит отказы в журнале; правило доступа одно; доступ к одной песне не открывает доступ к другой.

**Independent test**: запрос аудио платной песни без права → демо-фрагмент и запись в журнале; тот же токен на другой песне → отказ (quickstart, проверка 3).

- [x] T017 [US3] В `PlayerAccessService`: писать в журнал `infra.player.access` при каждом отказе в выдаче полного аудио/разметки — `player:access:denied` (WARN: нет доступа вовсе или «полный» токен без действующего права) и `player:access:demo` (INFO: штатный демо-режим); без токена и без аудио в записи
- [x] T018 [US3] Проверить FR-006 (доступ к одной песне не открывает доступ к другой): `describeToken` сверяет `songId`; живая проверка подписки — по конкретной песне
- [x] T019 [US3] Фронтенд: `karaoke-public/src/services/tracking.js` — `trackMetaClick` становится fire-and-forget (ответ больше не несёт ничего полезного); `karaoke-public/src/views/SongView.vue` — `onMetaClick` больше не сохраняет токен и не открывает плеер; обычный клик-трекинг по метаданным песни продолжает уходить в `POST /api/public/events` без изменений (FR-007)

**Checkpoint**: отказы наблюдаемы; правило доступа одно; публичный сайт не ломается (тихий отказ).

---

## Phase 6: Polish & Cross-Cutting

**Purpose**: документация, SSoT, проверки, отчёт.

- [x] T020 [P] Написать per-feature документ `docs/features/close-gesture-token-paywall.md` (6 обязательных секций, Status, Feature Key) — FR-009
- [x] T021 [P] Привести `docs/features/song-air-access.md` в соответствие с фактическим поведением после правки (основания доступа; жест не входит в перечень)
- [x] T022 [P] Обновить SSoT: `knowledge/domains/karaoke-web/components/public-controllers-4.md` (контракт плеера: живая проверка права, понижение до демо), `public-controllers-3.md` (описание `POST /events`), `public-controllers.md` (формула доступа), `services-overview.md` (каталог сервисов), `knowledge/domains/karaoke-web/domain.md` (структура)
- [x] T023 Прогнать проверки: `cd karaoke-public && npm run lint && npm run build && npm run format:check`; `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-web:compileKotlin :karaoke-app:compileKotlin --parallel` и `:karaoke-web:test :karaoke-app:test`; `bash tools/check-knowledge-structure.sh`; `python3 tools/check-doc-references.py`; `bash tools/check-feature-doc.sh docs/features/*.md`; `bash tools/check-subagent-isolation.sh`
- [x] T024 Написать `specs/563-impl-gesture-token/report.md` — отчёт для трекера (что изменено, проверки, что осталось за владельцем)
- [x] T025 Закоммитить с маркером `[tracker-claim-358]` (шаги 4–6 workflow — за владельцем: `add-comment`/`mark-review` после merge)

**Checkpoint**: документация и SSoT отражают факт; проверки зелёные; изменения в ветке `563-impl-gesture-token`.
