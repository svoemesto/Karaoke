---
status: Active
slug: 169-share-link-in-premium-compare
related:
  - ../domain/publishing.md
  - ../domain/identity.md
  - ../architecture/L3-components.md
  - ../../specs/169-share-link-in-premium-compare/spec.md
---

# 169 — Строка «Временная ссылка» в таблице FREE vs PREMIUM (LiveDoc)

> Drill-down — [specs/169-share-link-in-premium-compare/spec.md](../../specs/169-share-link-in-premium-compare/spec.md).

## Что делает

Добавление **12-й строки** «Временная ссылка» в таблицу сравнения `FREE vs
PREMIUM` на `/premium` (`PremiumView.vue:204-220`, блок «Что вы получите за подписку»).

**Гипотеза**: free-пользователь, **знающий** о возможности поделиться песней
с друзьями без регистрации, охотнее платит. Сейчас эта фича нигде не упоминается
в сравнении — часть потенциальных покупателей её просто не видит. Это пробел
в воронке `registration → premium` (см. `docs/strategy/growth.md` § 5 Top-3).

**Строка таблицы**: `Временная ссылка на песню | ❌ | ✅`.

Функционал share-link уже работает в проде с 2026-08-10 (см. фичи 163, 164 —
для премиум-пользователей).

## User Stories (краткий список)

- **US1** (P1): Free-пользователь видит 12-ю строку «Временная ссылка на песню» в таблице `/premium`.
- **US2** (P2): Существующие 11 строк таблицы остаются без изменений (никаких regression'ов).

## Functional Requirements (указатель)

- **FR-001**: 12-я строка «Временная ссылка на песню | ❌ | ✅» в `PremiumView.vue`.
- **FR-002**: Строка ссылается на `ShareLinkModal` или `/account/share-links` для контекста.
- **FR-003**: Стилистика совпадает с 11 существующими строками (тот же CheckIcon / CrossIcon).

## Acceptance Criteria

- [ ] **AC1**: Открыть `/premium` → таблица содержит 12 строк, последняя — «Временная ссылка на песню».
- [ ] **AC2**: FREE = ❌, PREMIUM = ✅ (как и планировалось).
- [ ] **AC3**: Click на строку открывает tooltip с пояснением.
- [ ] **AC4**: 11 существующих строк НЕ изменились (визуальное сравнение).

## Связанные LiveDocs

- Domain: [publishing.md](../publishing.md) (share-link как премиум-фича), [identity.md](../identity.md) (SiteUser — владелец ссылки)
- Specs: `specs/005-free-vs-premium` (исходная таблица FREE vs PREMIUM)
- Docs: `docs/strategy/growth.md` § Top-3 (гипотеза), `docs/features/guest-share-link.md`

## Код

- Frontend: `karaoke-public/src/views/PremiumView.vue` — `COMPARISON_ROWS` (добавить 12-й элемент)
- DTO: `src/store/modules/premium.js` — если нужно добавить share-link в данные

## История

- Создан: 2026-08-14
- Последнее обновление: 2026-08-14