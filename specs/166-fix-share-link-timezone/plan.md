# Implementation Plan: Единая трактовка дат share-ссылок (хранение в московском времени, отображение в местном)

**Branch**: `166-fix-share-link-timezone` | **Date**: 2026-08-11 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/home/nsa/Karaoke/specs/166-fix-share-link-timezone/spec.md`

## Summary

Устранить дефект «−3 часа» в датах share-ссылок и share-сессий.
Источник правды — naive timestamp в МСК в БД (`timestamp without time zone`).
Сервер возвращает единственное числовое поле даты (`expiresAt`, `createdAt`,
`revokedAt`, …) как реальный момент времени (epoch ms), без параллельных
`expiresAtMs`/`expiresAtLabel`. Интерфейсы форматируют дату в часовом поясе
устройства читателя. DDL не меняется; `recordhash` стабилен; синхронизация
двух БД не затрагивается.

Технический подход: явный `ZoneId.of("Europe/Moscow")` во всех серверных
операциях чтения/записи вместо `ZoneId.systemDefault()`; передача `Long`
без строковых меток; замена `setTimestamp(Timestamp(epochMs))` на
`setObject(..., LocalDateTime.ofInstant(...), Types.TIMESTAMP)` для устойчивости
к JVM TZ; удаление `formatMskLabel` и Field-ов `*Ms`/`*Label`; перевод UI на
`new Date(epochMs).toLocaleString('ru-RU', { … })` (пояс устройства).

## Technical Context

**Language/Version**: Kotlin 1.x (JDK 17), JavaScript ES2022 (Node 22).

**Primary Dependencies**: Spring Boot 2.x/3.x (back), Vue 3 + Vite (front),
JDBI-уровень сырого JDBC (никаких JPA/Hibernate — см. Principle II),
PostgreSQL 14+ (`EXTRACT(EPOCH FROM ts AT TIME ZONE 'Europe/Moscow')`).

**Storage**: PostgreSQL. Колонки дат остаются `timestamp without time zone`
(naive МСК). Никаких миграций схемы. Триггер `recordhash` остаётся как
есть (миграция 39).

**Testing**:
- Сервер (Kotlin): JUnit 5, `kotlin.test`, `org.junit.jupiter.api.Assertions`.
  Файл: `karaoke-web/src/test/kotlin/com/svoemesto/karaokeweb/services/SongShareLinkDateTimeTest.kt`.
- Frontend: `node --test` (встроенный test-runner Node 22). Без vitest/jest.
  Файлы: `karaoke-public/src/utils/__tests__/dateFormat.test.js`,
  `webvue3/src/utils/__tests__/dateFormat.test.js`. Покрывают
  форматирование в поясе устройства с `timeZone: 'Europe/Moscow'` /
  `'Asia/Vladivostok'` + проверка инвариантности от TZ машины
  (`process.env.TZ`).

**Target Platform**: Linux-сервер (`deploy/`), контейнер `karaoke-web` на
`eclipse-temurin:22-jre-jammy` с `ENV TZ="Europe/Moscow"`. Front — браузер
пользователя, любая TZ.

**Project Type**: Web-сервис (back) + SPA (front). Изменения затрагивают
3 модуля: `karaoke-web` (back + KDoc), `karaoke-public` (Vue 3 SPA),
`webvue3` (Vue 3 admin SPA).

**Performance Goals**: дефект — текстовый, без performance-критичных мест.
Изменения не добавляют SQL-запросов и не увеличивают payload (поля
`*Ms`/`*Label` удаляются — payload уменьшается).

**Constraints**:
- Не менять DDL и `recordhash` (Assumption #3, #4 спеки).
- Не трогать `SyncRegistry.all` (таблица не в sync по миграции 38).
- Не удалять `extract(epoch…AT TIME ZONE 'Europe/Moscow')` — это
  алгоритмический перевод, не зависит от TZ сессии Postgres.
- Логика сравнения `expires_at > now()` в SQL (resolveForGuest, findLinkIdBySecret,
  validateShareSession, heartbeat) не пересматривается.

**Scale/Scope**: 1 фича, 5 production-файлов + 3 теста. На проде задействованы
только эти 5 файлов. Никаких смежных фич.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

### Pre-Phase 0 (принципы из constitution.md v2.1.0)

| Принцип | Статус | Обоснование |
|---------|--------|-------------|
| **I. Self-contained автопайплайн** | ✅ PASS | Никаких внешних SaaS. Замена `setTimestamp` на `setObject` — локальный JDK. |
| **II. Сырой JDBC + дифф по хэшам** | ✅ PASS | Сырой JDBC остаётся. `recordhash` не трогается: текстовое представление в БД не меняется, поэтому md5 от конкатенации `column::TEXT` остаётся стабильным. O(n) сохраняется. |
| **III. Двух-БД синхронизация через SyncRegistry** | ✅ PASS | `tbl_song_share_links` и `tbl_song_share_sessions` **не** в `SyncRegistry.all` (см. комментарий в `38_song_share_links.sql`, строки 11-14). Задача не затрагивает sync. |
| **IV. Async-очередь задач** | ✅ PASS | Никаких `ProcessBuilder`. `ShareLinkSweeper` — отдельный сервис, не в этой задаче. |
| **V. Двух-фронтенд** | ✅ PASS | Изменения в обоих фронтендах (admin `webvue3`, public `karaoke-public`), разделены. Никакого смешения. |
| **VI. Code Standards** | ✅ PASS | KDoc обновляется (см. `task T8`), JSDoc на новых утилитах `dateFormat.js`. CI-линтеры не должны сломаться: новые тесты — отдельные файлы, `webvue3` и `karaoke-public` ESLint покроет новые `.js`-утилиты. |
| **VII. Cross-Machine Setup** | ✅ PASS | Никаких AI-конфигов. Конфигурационных файлов для ветки не создаётся. |
| **VIII. Секреты и git-гигиена** | ✅ PASS | Никаких секрет-файлов. `.gitignore` не меняется. |

### Post-Phase 1 (re-check)

| Принцип | Статус | Обоснование |
|---------|--------|-------------|
| **II. Сырой JDBC** | ✅ PASS | Замена `preparedStatement.setTimestamp(idx, Timestamp(epochMs))` → `setObject(idx, LocalDateTime, Types.TIMESTAMP)` — это всё ещё сырой JDBC; `Types.TIMESTAMP` — стандартный `java.sql.Types`. |
| **III. Двух-БД sync** | ✅ PASS | Подтверждено: `tbl_song_share_links` исключена из sync. См. SS-таблицу `SyncRegistry.all` отсутствие. |
| **VI. Code Standards** | ⚠ NEEDS CHECK | Новые тесты (`SongShareLinkDateTimeTest.kt`, `dateFormat.test.js`) не должны ломать CI. См. FR-002 в спецификации: «автоматические проверки». Линтеры ktlint и ESLint потенциально могут ругаться на `// noinspection` или другой шаблон — запланировать локальный прогон `./gradlew ktlintCheck` и `npm run lint:check` перед PR. |
| **VIII. Секреты** | ✅ PASS | Тесты не содержат секретов. |

**Никаких нарушений, требующих сложности** (Complexity Tracking пуст).

## Project Structure

### Documentation (this feature)

```text
specs/166-fix-share-link-timezone/
├── plan.md              # This file (/speckit.plan command output)
├── spec.md              # User scenarios, FR, SC
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/
│   └── api-share-dates.md  # Phase 1 output
├── checklists/
│   └── requirements.md  # Quality checklist (16/16 PASS)
└── tasks.md             # Phase 2 output (NOT created by /speckit.plan)
```

### Source Code (repository root)

Структура — multi-module Gradle (Kotlin) + два SPA (Vue 3). Затрагиваемые
и добавляемые файлы:

```text
karaoke-web/
├── src/main/kotlin/com/svoemesto/karaokeweb/
│   ├── services/
│   │   └── SongShareLinkService.kt        # EDIT: данные, KDoc, формат
│   └── controllers/
│       └── PublicShareController.kt        # EDIT: удаление лишних полей
└── src/test/kotlin/com/svoemesto/karaokeweb/
    └── services/
        └── SongShareLinkDateTimeTest.kt    # NEW: серверный тест дат

karaoke-public/
├── src/
│   ├── views/
│   │   └── ShareView.vue                   # EDIT: импорт dateFormat, expiresAt
│   ├── components/
│   │   └── ShareLinkModal.vue              # EDIT: убрать -3ч сдвиг, расширить expiresLabel
│   └── utils/
│       ├── dateFormat.js                   # NEW: утилита форматирования
│       └── __tests__/
│           └── dateFormat.test.js          # NEW: node --test
└── package.json                            # без изменений

webvue3/
├── src/
│   └── components/SiteUsers/
│       └── UserShareLinksModal.vue         # EDIT: использовать dateFormat
└── src/utils/
    ├── dateFormat.js                       # NEW: утилита форматирования
    └── __tests__/
        └── dateFormat.test.js              # NEW: node --test

deploy/
└── (нет изменений — DDL остаётся, обновляются только файлы-приложения)
```

**Structure Decision**: существующая multi-module структура. Никаких
новых модулей. Изменения локализованы в одном сервисе на бэке и в трёх
VUE-файлах на фронте, плюс две новые утилиты `dateFormat.js` (копии в
обоих SPA — у них разные `package.json` и lock-файлы, общего пакета
сейчас нет). Три новых тестовых файла.

## Complexity Tracking

> *Fill ONLY if Constitution Check has violations that must be justified*

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| — | — | — |

**Нарушений нет.** Все восемь принципов соблюдены, вторичные риски (тесты
могут зацепить CI — линтеры) вынесены в Post-Phase-1 секцию как
`NEEDS CHECK` и будут проверены в `tasks.md` (T8).

## Делегирование в tasks.md

**Это `/speckit.plan` — задачи не создаём.** Следующая команда:
`/speckit.tasks`. Артефакты, которые будут использоваться:

- `research.md` — обоснование решений, golden-числа, риски.
- `data-model.md` — точная структура изменений DTO и SQL.
- `contracts/api-share-dates.md` — формат JSON до/после.
- `quickstart.md` — 8 сценариев ручной проверки.

Предполагаемые блоки задач (для preview):

1. **Server DTO + SQL** — замена `extract(epoch…)` и `setTimestamp` (T1-T3).
2. **Server контракт + контроллер** — удаление `*Ms`/`*Label` (T4-T5).
3. **Server утилита преобразования** — `toMskLocalDateTime` (T6).
4. **Public SPA** — `dateFormat.js`, замена `ShareLinkModal.vue`, `ShareView.vue` (T7-T9).
5. **Admin SPA** — `dateFormat.js`, `UserShareLinksModal.vue` (T10-T11).
6. **Тесты** — три файла (T12-T14).
7. **Линтеры / прогон** — ktlint, ESLint (T15).
8. **Документация** — `docs/features/guest-share-link.md` (FR-011) (T16).
9. **PR + CI** — feature-ветка, PR, ждать CI 7/7 (T17).
