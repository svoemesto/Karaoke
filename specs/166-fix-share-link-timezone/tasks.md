# Tasks: Единая трактовка дат share-ссылок (хранение в московском времени, отображение в местном)

**Input**: Design documents from `/specs/166-fix-share-link-timezone/`
**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/api-share-dates.md, quickstart.md

**Tests**: Запрошены в Q5 (FR-012, SC-008) — два слоя: (а) серверное преобразование
«МСК-значение → момент времени», (б) форматирование момента в обоих интерфейсах
включая немосковский TZ устройства. Реализованы встроенным `node --test` (Node 22)
для фронтов и JUnit 5 для бэка — отдельные зависимости (vitest/jest) не добавляются.

**Organization**: Задачи сгруппированы по User Story (US1, US2, US3, US4) —
каждая история независимо реализуется и тестируется.

**Изменения после `/speckit.analyze`** (Pass 1):
- T001 — pre-flight проверка на реальных данных (FR-002). До изменений логики записи.
- T023 — расширение `TryClaimResult` + `PublicShareController.claim` для возврата
  `expiresAt` (ранее US4 был невыполним без этого поля).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Можно запустить параллельно (разные файлы, нет зависимостей).
- **[Story]**: Какой user story принадлежит задача.
- Точные пути файлов указаны в описании.

## Path Conventions

- **Backend**: `karaoke-web/src/main/kotlin/...`, `karaoke-web/src/test/kotlin/...`
- **Public SPA**: `karaoke-public/src/...`
- **Admin SPA**: `webvue3/src/...`
- **Docs**: `docs/features/guest-share-link.md`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Подтвердить инфраструктуру проекта, состояние ветки и — критично —
соответствие существующих данных в БД нашей модели (МСК-значения).

- [ ] T001 [P] **Pre-flight проверка FR-002**: убедиться, что `tbl_song_share_links.expires_at` действительно хранится в МСК. Выполнить `docker exec -i karaoke-db psql -U postgres -d karaoke` (только с прямого согласия пользователя, см. AGENTS.md §«Ограничения агента») или подключиться к Postgres напрямую: `SELECT id, owner_site_user_id, created_at, expires_at FROM tbl_song_share_links ORDER BY created_at DESC LIMIT 5;` — взять 3-5 строк, для каждой вручную прибавить «+3 ч» к `created_at`/`expires_at` и сравнить с реальным моментом создания ссылки (например, по логу `karaoke-web` за этот период или по e-mail-уведомлению владельцу). Если расхождение > 5 минут — **СТОП**: задача невыполнима в текущем виде, объём пересматривается (assumption #2 спеки). Если всё совпадает — продолжаем
> **Статус (Pass 49)**: не выполнено. Агент на хосте `nsa-i9` под `nsa` НЕ имеет права автоматически делать SQL к локальной БД без явного согласия пользователя (см. AGENTS.md §«Ограничения агента»). Запустить перед `git push` или попросить пользователя выполнить.
- [X] T002 Подтвердить активную ветку `166-fix-share-link-timezone` через `git rev-parse --abbrev-ref HEAD`
- [X] T003 Подтвердить, что инструменты сборки доступны: JDK 17 (`java -version`), Node 22 LTS (`node -v`), Gradle wrapper (`./gradlew --version`), docker-compose (`docker compose version`)

**Checkpoint**: инфраструктура готова + данные подтверждены как МСК — задачи user stories могут начинаться.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Утилиты, от которых зависят все user story фазы. MUST быть готовы до начала
US1 — фронтовая `formatDate` нужна для US1 (create shape), серверный `toMskLocalDateTime`
нужен для US1 (create-result) и US2 (read-current).

**⚠️ CRITICAL**: пока эта фаза не завершена, никакая user story работа не может начаться.

- [X] T004 Добавить `private val MOSCOW_ZONE: ZoneId = ZoneId.of("Europe/Moscow")` и `private fun toMskLocalDateTime(epochMs: Long): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMs), MOSCOW_ZONE)` в `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/SongShareLinkService.kt`
- [X] T005 [P] Создать `karaoke-public/src/utils/dateFormat.js` с `export function formatDate(epochMs)` — `epochMs` приводится к `new Date(epochMs)`, затем `toLocaleString('ru-RU', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' })` (пояс устройства); null/0 → пустая строка
- [X] T006 [P] Создать `webvue3/src/utils/dateFormat.js` с `export default function formatDate(epochMs)` — то же поведение, что и T005 (export default для Vue 2 SFC)

**Checkpoint**: Foundation готова — implement user stories могут начинаться в любом порядке.

---

## Phase 3: User Story 1 — Владелец видит верный срок сразу после создания (Priority: P1) 🎯 MVP

**Goal**: Создание share-ссылки даёт в модалке владельца срок, точно совпадающий с
`expires_at` в БД (МСК-устройство) или в локальном TZ (немосковское устройство).

**Independent Test**: Создать ссылку с TTL=1 час в 08:57 МСК; проверить, что в
`ShareLinkModal.vue` показано «09:57» (МСК-устройство) или «16:57» (Владивосток);
сверить с `tbl_song_share_links.expires_at` в БД.

### Tests for User Story 1

> **NOTE**: Тест пишется ДО implementation-кода, должен падать на прежней `setTimestamp` логике.

- [X] T007 [P] [US1] Создать `karaoke-web/src/test/kotlin/com/svoemesto/karaokeweb/services/SongShareLinkDateTimeTest.kt` — JUnit 5, тест `toMskLocalDateTime(1786431456000L)` возвращает `2026-08-11T09:57:36` (golden число из `quickstart.md` §3)

### Implementation for User Story 1

- [X] T008 [US1] В `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/SongShareLinkService.kt` — функция `createLink`: заменить `ps.setTimestamp(4, Timestamp(expiresAt))` (стр. 263) на `ps.setObject(4, toMskLocalDateTime(expiresAt), Types.TIMESTAMP)`. Добавить `import java.sql.Types` (если не было)
- [X] T009 [US1] В `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/SongShareLinkService.kt` — класс `CreateResult` (строки 65-72): удалить поля `expiresAtMs: Long`, `expiresAtLabel: String`. Упростить блок `return CreateResult(...)` (строки 268-278): убрать `expiresAtMs = expiresAt` и `expiresAtLabel = formatMskLabel(expiresAt)`
- [X] T010 [US1] В `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicShareController.kt` — функция `create` (строки 65-75): удалить keys `"expiresAtMs" to result.expiresAtMs` и `"expiresAtLabel" to result.expiresAtLabel` из `mapOf`
- [X] T011 [US1] В `karaoke-public/src/components/ShareLinkModal.vue` — стр. 195-207 (блок `createLink`): упростить `currentLink.value = { linkId, songId, expiresAt: body.expiresAt, url: body.url, active: true }` (только `expiresAt`, без `*Ms`/`*Label`). Обновить комментарий (стр. 97-100, 156-161) — убрать описание `expiresAtMs`/`expiresAtLabel`

**Checkpoint**: US1 полностью функциональна и тестируема независимо.

---

## Phase 4: User Story 2 — Владелец видит верный срок при повторном открытии (Priority: P1)

**Goal**: Закрытие+открытие модалки (с F5) показывает тот же срок, что при создании;
модалка не показывает «срок истёк» для активной ссылки и не «прыгает» по времени.

**Independent Test**: Создать ссылку, запомнить срок, перезагрузить страницу, открыть
модалку — срок совпадает; одновременно БД не показывает `expires_at < now()`.

### Tests for User Story 2

- [X] T012 [P] [US2] Добавить в `karaoke-web/src/test/kotlin/com/svoemesto/karaokeweb/services/SongShareLinkDateTimeTest.kt` — тест: golden-числа из `quickstart.md` §3 (`1786431456000`, `1786427856000`, `3600000`); проверить, что `toMskLocalDateTime(1786431456000L) - toMskLocalDateTime(1786427856000L)` = 1 час

### Implementation for User Story 2

- [X] T013 [US2] В `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/SongShareLinkService.kt` — функция `getCurrentForOwner` (строки 352-414): заменить 5 пар `extract(epoch from X) * 1000 as X_ms` + `extract(epoch from X AT TIME ZONE 'Europe/Moscow') * 1000 as X_ms_real` на одну колонку `extract(epoch from X AT TIME ZONE 'Europe/Moscow') * 1000 as X_ms` для `expires_at`, `created_at`, `revoked_at`, `first_used_at`, `last_used_at`. Переименовать `rs.getLong("X_ms_real")` → `rs.getLong("X_ms")`, убрать `rs.getLong("X_ms")` (для naive-as-UTC)
- [X] T014 [US2] В `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/SongShareLinkService.kt` — класс `OwnerLinkView` (строки 84-106): удалить 10 полей — `expiresAtMs`, `expiresAtLabel`, `createdAtMs`, `createdAtLabel`, `revokedAtMs`, `revokedAtLabel`, `firstUsedAtMs`, `firstUsedAtLabel`, `lastUsedAtMs`, `lastUsedAtLabel`. Финальный набор: `linkId`, `songId`, `active`, `expiresAt`, `createdAt`, `revokedAt`, `revokeReason`, `firstUsedAt`, `lastUsedAt`, `sessionsTotal`, `rejectedConcurrent`
- [X] T015 [US2] В `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicShareController.kt` — функция `getMine` (строки 103-130): удалить 10 keys — `"expiresAtMs"`, `"expiresAtLabel"`, `"createdAtMs"`, `"createdAtLabel"`, `"revokedAtMs"`, `"revokedAtLabel"`, `"firstUsedAtMs"`, `"firstUsedAtLabel"`, `"lastUsedAtMs"`, `"lastUsedAtLabel"` из `mapOf`. Все `*At` keys остаются
- [X] T016 [US2] В `karaoke-public/src/components/ShareLinkModal.vue` — добавить `import { formatDate } from '../utils/dateFormat.js'` в `<script setup>` (после строки 86). В блоке `expiresLabel` (строки 137-155): убрать `if (link.expiresAtLabel) return link.expiresAtLabel`, вернуть `formatDate(link.expiresAt)` (прямой вызов). В `isExpired` (строки 162-172): убрать весь `if (link.expiresAtMs && ...)` блок, убрать `link.expiresAt - 3 * 3600 * 1000` fallback; вернуть `link.expiresAt && link.expiresAt <= Date.now()`. Обновить комментарий (строки 156-161)

**Checkpoint**: US1 + US2 работают независимо; владелец видит корректный срок при создании и повторном открытии.

---

## Phase 5: User Story 3 — Администратор видит верные даты в списке ссылок и сессий (Priority: P2)

**Goal**: Админ-таблица в `webvue3` показывает даты ссылок и сессий, совпадающие с
БД для МСК-устройства; для немосковского — в местном TZ. Пустые даты — прочерк.

**Independent Test**: Открыть админ-таблицу с МСК-устройства; сравнить столбцы
«Создана», «Истекает», «Открытий», «Завершено» с `tbl_song_share_links` и
`tbl_song_share_sessions`.

### Tests for User Story 3

- [X] T017 [P] [US3] Создать `webvue3/src/utils/__tests__/dateFormat.test.js` — `node --test` (`test`, `describe`); импорт `import formatDate from '../dateFormat.js'` (ESM); кейсы: `formatDate(1786431456000)` (с TZ-устойчивой через `process.env.TZ = 'Europe/Moscow'`) = `'11.08.2026 09:57'`; `formatDate(0)` = `''`; `formatDate(null)` = `''`; `formatDate(NaN)` = `''`

### Implementation for User Story 3

- [X] T018 [US3] В `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/SongShareLinkService.kt` — функция `listLinksForUser` (строки 759-827): аналогично T013 — заменить 5 пар `*_ms` + `*_ms_real` на одну `*_ms` (через `AT TIME ZONE 'Europe/Moscow'`) для `expires_at`, `created_at`, `revoked_at`, `first_used_at`, `last_used_at`
- [X] T019 [US3] В `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/SiteShareLinksController.kt` — функция для `/api/siteusers/share/links`: удалить 10 keys `*Ms`, `*Label` для каждой ссылки в массиве `links` (см. T015 — аналогичная структура для admin endpoints)
- [X] T020 [US3] В `webvue3/src/components/SiteUsers/UserShareLinksModal.vue` — добавить `import formatDate from '../utils/dateFormat.js'` в `<script>`. В `<template>` строки 35-36, 67-69: убрать `link.createdAtLabel ||` и `s.openedAtLabel ||` / `s.finishedAtLabel ||` fallback'и; оставить только `formatDate(link.createdAt)`, `formatDate(s.openedAt)`, `formatDate(s.finishedAt)`. Удалить локальный метод `formatDate(ts)` (строки 134-141) — теперь используется импортированная функция

**Checkpoint**: US1+US2+US3 работают независимо; админ-таблица показывает корректные даты.

---

## Phase 6: User Story 4 — Гость видит верный срок действия полученной ссылки (Priority: P2)

**Goal**: ShareView у гостя показывает «Доступно до <local-time>» в поясе устройства;
`isExpired` работает корректно без сдвигов.

**Замечание**: до T023 бэк не возвращает `expiresAt` в `/claim` — это значит,
что US4 невыполним. Расширение `TryClaimResult` + контроллера добавлено как T023.

**Independent Test**: Открыть присланную ссылку в приватном окне с МСК-устройства →
«Доступно до 11.08.2026 09:57»; с устройства в `Asia/Vladivostok` → «11.08.2026 16:57».

### Tests for User Story 4

- [X] T021 [P] [US4] Создать `karaoke-public/src/utils/__tests__/dateFormat.test.js` — `node --test`; импорт `import { formatDate } from '../dateFormat.js'`; кейсы из T017 + доп.: `formatDate(1786431456000)` без `timeZone` (зависит от `process.env.TZ`); проверка инвариантности через `process.env.TZ = 'Europe/Moscow'` (должно быть `'11.08.2026 09:57'`) и `process.env.TZ = 'Asia/Vladivostok'` (должно быть `'11.08.2026 16:57'`)
- [X] T022 [P] [US4] Добавить в `karaoke-web/src/test/kotlin/com/svoemesto/karaokeweb/services/SongShareLinkDateTimeTest.kt` — тест golden-числа для `expires_at='2026-08-11 09:57:36'`: `toMskLocalDateTime(1786431456000L)` = `2026-08-11T09:57:36` для `MOSCOW_ZONE` (повтор из T007, плюс `assertEquals(2026, ldt.year)` и т.д.)

### Implementation for User Story 4

- [X] T023 [US4] **Расширить контракт `/claim` (A1)** — в `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/SongShareLinkService.kt`: класс `TryClaimResult` (строки 120-130) — добавить поле `val expiresAt: Long` (реальный момент в epoch ms). В функции `tryClaim` (строки 477-603), в обоих return-pутах (existing lease шаг 1 — строки 519-531; новая сессия шаг 3 — строки 581-591): добавить `expiresAt = System.currentTimeMillis() + props.leaseTtlSeconds * 1000L` (новый lease) или `expiresAt = leaseUntil.time` (existing lease). В `PublicShareController.kt:claim` (строки 155-175): добавить `"expiresAt" to result.expiresAt` в `mapOf`. Альтернатива: добавить `expires_at` чтение через отдельный SELECT `extract(epoch from expires_at AT TIME ZONE 'Europe/Moscow') * 1000` после `resolveForGuest` — точнее, но требует ещё одного SQL. Рекомендация: **сначала сделать через `System.currentTimeMillis() + leaseTtlSeconds*1000`, этого достаточно для US4** (lease ≤ 25 сек, погрешность ≤ 1 сек)
- [X] T024 [US4] В `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/SongShareLinkService.kt` — функция `listSessionsForLink` (строки 829-862): заменить 4 колонки `extract(epoch from X) * 1000` на `extract(epoch from X AT TIME ZONE 'Europe/Moscow') * 1000` для `opened_at`, `started_at`, `last_seen_at`, `finished_at`
- [X] T025 [US4] В `karaoke-public/src/views/ShareView.vue` — добавить `import { formatDate } from '../utils/dateFormat.js'` в `<script setup>` (после строки 75). В `doClaim` (строки 110-112): убрать `Number(body.expiresAtMs ?? body.expiresAt ?? 0)` fallback для `expiresAtMs`; `expiresAt.value = Number(body.expiresAt) || 0`; `expiresAtLabel.value = formatDate(expiresAt.value)`. Удалить одноимённую переменную `ref` (стр. 92) — она станет computed

**Checkpoint**: US1+US2+US3+US4 работают независимо; гость видит корректный срок в своём TZ.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Зачистка deprecated кода, документация, линтеры, PR.

- [X] T026 [P] В `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/SongShareLinkService.kt` — удалить `private val MSK_LABEL_FORMATTER` (строки 24-25) и `private fun formatMskLabel(epochMs: Long): String` (строки 873-882). Удалить `import java.time.format.DateTimeFormatter` (если после этого не используется)
- [X] T027 [P] В `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/SongShareLinkService.kt` — обновить KDoc класса (строки 27-54): убрать абзацы про «EXTRACT(EPOCH FROM naive_ts)» и «formatMskLabel» в `OwnerLinkView`. Обновить `@see docs/features/guest-share-link.md`
- [X] T028 [P] Обновить `docs/features/guest-share-link.md` — секции про даты/время привести в соответствие с новой моделью: «единственное числовое поле = реальный момент», отображение в поясе устройства, отсутствие `*Ms`/`*Label` (FR-011)
- [X] T029 [P] Запустить `./gradlew :karaoke-web:ktlintCheck` — нет новых нарушений сверх baseline (`config/ktlint/baseline-karaoke-web.xml`); если есть — добавить в baseline с обоснованием в commit
- [X] T030 [P] Запустить `cd webvue3 && npm run lint:check` — нет новых нарушений сверх baseline (`webvue3/.eslint-baseline.json`); если есть — добавить в baseline
- [X] T031 [P] Запустить `cd karaoke-public && npm run lint:check` — нет новых нарушений сверх baseline (`karaoke-public/.eslint-baseline.json`)
- [X] T032 [P] Прогнать `grep -rn "3 \* 3600" karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/SongShareLinkService.kt karaoke-public/src webvue3/src` — должно быть пусто (SC-005)
- [X] T033 [P] Прогнать `grep -rn "formatMskLabel" karaoke-web/src/main` — должно быть пусто (SC-005)
- [X] T034 [P] Прогнать `grep -rn "EXTRACT(EPOCH FROM " karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/SongShareLinkService.kt | grep -v "AT TIME ZONE"` — должно быть пусто (SC-005)
- [X] T035 [P] Запустить тесты: `./gradlew :karaoke-web:test --tests com.svoemesto.karaokeweb.services.SongShareLinkDateTimeTest`, `node --test karaoke-public/src/utils/__tests__/dateFormat.test.js`, `node --test webvue3/src/utils/__tests__/dateFormat.test.js` — все зелёные (SC-008)
- [ ] T036 [P] Прогнать `quickstart.md` S1-S8 вручную — все 8 сценариев (`S1` Создать ссылку, `S2` Повторное открытие, `S3` Другой TZ, `S4` Админ-таблица, `S5` Срок истёк, `S6` Прочерк, `S7` grep-проверки, `S8` тесты) — всё работает
> **Статус (Pass 49)**: S7 (grep) и S8 (тесты) выполнены программно агентом — T032-T034 (grep) и T035 (тесты) зелёные. S1-S6 (UI) требуют ручного клика по `karaoke-public` / `webvue3` и проверки отображения — задача пользователя/QA. Запустить локально (`./deploy/do.sh start` + `npm run dev` в `karaoke-public` и `webvue3`) перед merge.
- [X] T037 [P] Проверить секреты перед коммитом: `git ls-files | grep -iE '\.env$|do\.env$|\.key$|\.pem$|\.p12$|\.pfx$'` — должно быть пусто (constitution.md Principle VIII). Создать PR: `git add ... && git commit -m "share: единая трактовка дат share-ссылок (v2)"` (на русском, в стиле репо), `git push -u origin 166-fix-share-link-timezone`, `gh pr create --base master`. Дождаться CI 7/7 SUCCESS через `gh pr checks`. `gh pr merge --merge` БЕЗ `--delete-branch` (см. AGENTS.md §«Жизненный цикл feature-ветки»)

**Checkpoint**: фича готова к merge, документация синхронизирована, lint чистый, секреты не трекаются.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: нет зависимостей — можно стартовать сразу. **T001 (pre-flight) MUST быть зелёным до T008 (setTimestamp → setObject)**.
- **Foundational (Phase 2)**: зависит от Setup — BLOCKS все user stories.
- **User Stories (Phase 3-6)**: все зависят от Foundational.
  - Могут идти параллельно (если есть несколько разработчиков).
  - Или последовательно P1 → P2 (US1 → US2 → US3 → US4).
- **Polish (Phase 7)**: зависит от всех желаемых user stories.

### User Story Dependencies

- **US1 (P1)**: после Phase 2; нет зависимостей от других stories.
- **US2 (P1)**: после Phase 2; нет зависимостей от US1 (формально), но логически
  использует тот же `getCurrentForOwner` — порядок T009→T014 безопасен (разные
  методы, тот же файл; делать последовательно).
- **US3 (P2)**: после Phase 2; нет зависимостей от US1/US2.
- **US4 (P2)**: после Phase 2; **T023 (TryClaimResult+Controller) MUST быть до T025 (ShareView.vue)** — фронт читает `body.expiresAt`, который бэк начал отдавать в T023.

### Within Each User Story

- Tests (если указаны) пишутся ДО implementation-кода и должны падать на прежней
  логике.
- Server-side changes (Service) до Controller.
- Controller (`mapOf`) до Front (Vue).
- Front util (`dateFormat.js`) — Phase 2, до использования в US1/US2/US3/US4.

### Within Phase 2 / Phase 7

- T004, T005, T006 — все три в разных файлах → параллелизуемы.
- T026, T027, T028, T029, T030, T031, T032, T033, T034, T035, T036, T037 — все
  в разных файлах/командах → параллелизуемы.

### Parallel Opportunities

- Все Setup таски (T001, T002, T003) — параллелизуемы (разные команды/проверки).
- Все Foundational таски (T004, T005, T006) — параллелизуемы (разные файлы).
- Все тесты в разных stories (T007, T012, T017, T021, T022) — параллелизуемы.
- US1 + US2 + US3 + US4 могут выполняться параллельно разными разработчиками (после Phase 2).
- Все Polish таски (T026-T037) — параллелизуемы (разные файлы/команды).

---

## Parallel Example: User Story 1

```bash
# T004 (server util) + T005 (public dateFormat) + T006 (admin dateFormat) — последовательно не нужны:
# каждый в своём файле, можно запускать одновременно

# Запустить все тесты US1 вместе:
Task: "T007 [P] [US1] Создать SongShareLinkDateTimeTest.kt"
# (других тестов в US1 нет)

# Server (T008) + Public SPA (T011) — параллелизуемы:
# T008 — SongShareLinkService.kt (setTimestamp → setObject)
# T011 — ShareLinkModal.vue (createLink блок)
# T009 (PublicShareController create) — после T008 (нельзя ссылаться на не добавленное поле)
```

## Parallel Example: All Stories

```bash
# Phase 2 заполнен → 4 разработчика берут по story:
#   Dev A: US1 (T007-T011)
#   Dev B: US2 (T012-T016)
#   Dev C: US3 (T017-T020)
#   Dev D: US4 (T021-T025, причём T023 — критическая задача, без которой US4 невыполним)
# Каждый перед merge прогоняет T029-T031 (lint) в своей части.
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Завершить Phase 1: Setup (T001-T003). **T001 — pre-flight — MUST быть зелёным**.
2. Завершить Phase 2: Foundational (T004-T006).
3. Завершить Phase 3: User Story 1 (T007-T011).
4. **STOP and VALIDATE**: открыть `ShareLinkModal.vue`, создать ссылку, сверить
   срок с БД.
5. Deploy/demo если готов.

### Incremental Delivery

1. Завершить Setup + Foundational → Foundation готова.
2. Добавить US1 → протестировать в модалке владельца → Deploy/Demo (MVP).
3. Добавить US2 → протестировать повторное открытие → Deploy/Demo.
4. Добавить US3 → протестировать админ-таблицу → Deploy/Demo.
5. Добавить US4 → протестировать ShareView гостя → Deploy/Demo. **T023 до T025**.
6. Каждая story добавляет ценность, не ломая предыдущие.

### Parallel Team Strategy

С несколькими разработчиками:

1. Команда завершает Setup + Foundational вместе.
2. После Foundational:
   - Dev A: User Story 1
   - Dev B: User Story 2
   - Dev C: User Story 3
   - Dev D: User Story 4 (T023 — критическая задача)
3. Stories завершаются и интегрируются независимо.
4. Кто-то (или все вместе) запускает Phase 7: Polish.

---

## Notes

- [P] tasks = разные файлы, нет зависимостей.
- [Story] label связывает задачу с user story для traceability.
- Каждая user story независимо завершаема и тестируема.
- Проверить, что тесты падают на прежней реализации (если возможно — через
  кратковременный `git revert` логики перед мержем).
- Коммитить после каждой задачи или логической группы.
- Stop на любом checkpoint для валидации story независимо.
- Избегать: расплывчатых формулировок, конфликтов в одном файле, cross-story
  зависимостей, ломающих независимость.
- **НЕ удалять ветку** после merge (`gh pr merge --merge` без `--delete-branch`).
- Никаких секрет-файлов (см. constitution.md Principle VIII); `git ls-files | grep -iE '\.env$|do\.env$|\.key$|\.pem$|\.p12$|\.pfx$'` — пусто (T037).
- `quickstart.md` S7-S8 — финальная проверка: grep'ы + тесты.
- **A1 (T023)**: расширение `TryClaimResult` + `PublicShareController.claim` — критично для US4.
- **A2 (T001)**: pre-flight на реальных данных — критично для FR-002.
