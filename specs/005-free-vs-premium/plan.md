# Implementation Plan: Таблица «FREE vs PREMIUM» на /premium (QW-1)

**Branch**: `005-free-vs-premium` | **Date**: 2026-07-25 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/005-free-vs-premium/spec.md`

## Summary

Таблица сравнения FREE/PREMIUM из 9 строк на `PremiumView.vue` (`karaoke-public`),
над существующим блоком выбора тарифа. Чистый фронтенд: без новых таблиц БД,
без нового backend-кода, переиспользует `useAuth().user.effectivePremium` и
`trackUi()`.

**Список фичей построчно верифицирован с кодом и согласован с пользователем
2026-07-25** (см. `spec.md` User Story 1 «Верификация построчно» и
`research.md` Decision 1) — из исходных 12 предложенных строк 9 подтверждены
кодом, 3 убраны (несуществующие или запрещённые constitution.md фичи).

## Technical Context

**Language/Version**: JavaScript (ES2020+), Vue 3.4+ (Options API —
`PremiumView.vue` целиком в Options API).

**Primary Dependencies**: `composables/useAuth.js` (`user.effectivePremium` —
определить FREE/PREMIUM, поле подтверждено грепом по 8 местам использования
в `karaoke-public`, включая `SearchView.vue`, `ZakromaView.vue`,
`PlaylistsView.vue`), `services/tracking.js` (`trackUi` — клик по CTA).
Никаких новых зависимостей.

**Storage**: N/A — новых таблиц не требуется (spec.md Assumptions, FR-011:
список фичей хранится в исходниках). Клики трекаются через существующий
`POST /api/public/events`.

**Testing**: ручное тестирование в браузере (в `karaoke-public` автотестов
нет). Сценарии — в `quickstart.md`.

**Target Platform**: браузер (мобильный + десктоп), `karaoke-public` SPA.

**Project Type**: web — фронтенд-дополнение к существующему `PremiumView.vue`.
Backend не меняется.

**Performance Goals**: NFR-001 — таблица не замедляет загрузку `/premium`.
Контент полностью статический, ноль дополнительных HTTP-запросов.

**Constraints**:
- Только CSS-переменные `--km-*` (FR-009).
- **Та же поправка к FR-010, что и в `003`/`004`**: в `karaoke-public` нет
  разделения `views/classic/`/`views/modern/` — таблица реализуется один раз
  в едином `PremiumView.vue` (см. `004-reasons-to-register/research.md`
  Decision 1, тот же факт, проверять повторно не нужно).
- **Ничего не упоминаем про MP4/скачивание** (constitution.md, «только
  онлайн») и **ничего про сторонние площадки** (VK и т.п. — сайт-центричная
  модель) — оба ограничения уже учтены в финальном списке из 9 строк
  (`spec.md` FR-003).
- Числа в таблице (100/500/50) — захардкожены в соответствии с реальными
  константами бэкенда (`PublicPlaylistController.kt`:
  `FREE_FAVORITES_LIMIT=100`, `PREMIUM_ITEMS_LIMIT=500`,
  `PREMIUM_PLAYLIST_LIMIT=50`). Если бэкенд-константы когда-нибудь изменятся,
  таблицу нужно обновить вручную (см. `research.md` Decision 2 — почему не
  тянем эти числа через API).

**Scale/Scope**: 1 существующий файл на правку (`PremiumView.vue`: +1 секция
в template между `.km-delivery-note` и первой картой тарифа, +1 data-массив
из 9 строк, +1 метод трекинга клика по CTA).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| # | Принцип | Соответствие | Обоснование |
|---|---------|--------------|-------------|
| I | Self-contained автопайплайн | N/A | Не медиа-пайплайн. |
| II | Сырой JDBC + дифф по хэшам | N/A | Нет БД-изменений. |
| III | Двух-БД синхронизация | N/A | Не затрагивается. |
| IV | Async-очередь задач | N/A | Нет долгих операций. |
| V | Двух-фронтенд: admin и public | ✅ PASS | Только `karaoke-public`. |
| VI | Code Standards | ✅ PASS | Правка существующего файла, JSDoc уже покрыт. |

**Дополнительная проверка (специфично для этой фичи)**: «только онлайн»/
«без скачивания» и «площадки — не реклама» — оба ограничения из
`AGENTS.md`/`CONTRIBUTING.md` «TOP-10 ловушек» пунктов 10 явно учтены при
построчной верификации списка фичей (см. Summary выше). Это не отдельный
Constitution-принцип с номером, но такой же обязательный gate — фича не
проходит review без этого.

**Итог**: без нарушений. Complexity Tracking не нужен.

## Project Structure

### Documentation (this feature)

```text
specs/005-free-vs-premium/
├── plan.md              # Этот файл
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output (новых сущностей нет)
├── quickstart.md        # Phase 1 output
└── tasks.md             # Phase 2 output (/speckit.tasks)
```

`contracts/` не создаётся — новых REST-эндпоинтов нет.

### Source Code (repository root)

```text
karaoke-public/
└── src/
    └── views/
        └── PremiumView.vue   # УЖЕ ЕСТЬ, правка: +таблица сравнения между
                                #   .km-delivery-note и первой km-card
                                #   (см. research.md Decision 3)
```

**Structure Decision**: точечная правка одного существующего файла, без новых
компонентов — таблица используется в одном месте, контент статический
(тот же паттерн решения, что и в `004-reasons-to-register`).

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| *(нет)* | — | — |

Constitution Check прошёл без нарушений.
