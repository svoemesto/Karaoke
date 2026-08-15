---
status: Active
slug: 166-fix-share-link-timezone
related:
  - ../domain/publishing.md
  - ../domain/identity.md
  - ../../specs/166-fix-share-link-timezone/spec.md
---

# 166 — Единая трактовка дат share-ссылок (московское хранение, локальный показ) (LiveDoc)

> Drill-down — [specs/166-fix-share-link-timezone/spec.md](../../specs/166-fix-share-link-timezone/spec.md).

## What it does

В таблице `tbl_song_share_links` все временные поля (`expires_at`,
`created_at`) хранятся как `timestamp without time zone` в **московском
времени** (naive-МСК) — это единственный источник правды.

Сейчас в коде перепутаны **три представления** одной и той же даты:

1. «naive как UTC» — число, если наивно трактовать московское значение как UTC
   (на 3 часа больше реального момента).
2. Реальный момент времени (правильный).
3. Готовая текстовая метка вида `ДД.ММ.ГГГГ ЧЧ:ММ`.

**Наблюдаемый дефект (прод, 2026-08-11)**: для ссылки с
`expires_at = '2026-08-11 09:57:36'` пользователю показывается
«Ссылка активна до 11.08.2026 06:57» — минус 3 часа.

**Фикс**:
- Зона «Европа/Москва» задаётся **явно в коде** на всех операциях чтения,
  записи и форматирования (БД и сессия НЕ влияют на результат).
- Сервер передаёт **одно поле** `expiresAt` (реальный момент времени);
  поля `*Ms` (naive-МСК) удалены из ответов.
- Фронт **пересчитывает** реальный момент в локальное время читателя (JS
  `toLocaleString()` или `Intl.DateTimeFormat`).
- Серверные текстовые метки «ДД.ММ.ГГГГ ЧЧ:ММ в Москве» упраздняются.

## User Stories (краткий список)

- **US1** (P1): Владелец видит верный срок истечения сразу после создания ссылки (без ±3 часов).
- **US2** (P1): Гость по share-ссылке видит дату истечения в **своём** часовом поясе.
- **US3** (P2): Тесты на конверсию «МСК из БД → момент времени» + форматирование в обоих интерфейсах.

## Functional Requirements (указатель)

- **FR-001**: `expiresAt`, `createdAt` и др. возвращаются как **реальный момент** (ISO 8601 / epoch-millis).
- **FR-002**: Поля `*Ms` (naive-МСК) удалены из DTO и фронта.
- **FR-003**: Зона `Europe/Moscow` зашита в код (БД-сессия, контейнера — игнор).
- **FR-004**: Фронт `Intl.DateTimeFormat` с локалью пользователя (автоопределение).
- **FR-005**: Серверные текстовые метки в МСК — упразднены (всё на клиенте).

## Acceptance Criteria

- [ ] **AC1**: Создать ссылку в 09:57:36 МСК → владелец видит «09:57:36 МСК» (без ±3 часов).
- [ ] **AC2**: Тот же пользователь видит то же время в `ShareLinkModal`.
- [ ] **AC3**: Гость по ссылке (в другом TZ, напр. UTC+8) видит «14:57:36» (свой TZ).
- [ ] **AC4**: Тесты `Europe/Moscow → moment + format` PASS.

## Related LiveDocs

- Domain: [publishing.md](../domain/publishing.md) (ShareLink lifecycle), [identity.md](../domain/identity.md) (владелец ссылки)
- Specs: `specs/167-fix-share-claim-500` (смежная — там share-таблицы могут отсутствовать)
- Docs: AGENTS.md Q&A (миграция share-таблиц)

## Code

- Backend: `karaoke-web/.../dto/SongShareLinkDto.kt` — оставить `expiresAt: Long` (epoch-millis)
- Backend: `karaoke-web/.../services/SongShareLinkService.kt` — все операции с датами через `ZoneId.of("Europe/Moscow")`
- Backend: `karaoke-web/.../model/SongShareLink.kt` — getter'ы возвращают `Instant`, не `LocalDateTime`
- Frontend: `karaoke-public/src/components/ShareLinkModal.vue` — `Intl.DateTimeFormat` с deviceTZ
- Frontend: `karaoke-public/src/views/ShareView.vue` — то же форматирование

## History

- Created: 2026-08-14
- Last updated: 2026-08-14