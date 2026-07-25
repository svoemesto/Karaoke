# Implementation Plan: «5 причин зарегистрироваться» на главной (QW-2)

**Branch**: `004-reasons-to-register` | **Date**: 2026-07-25 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/004-reasons-to-register/spec.md`

## Summary

Блок из 5 карточек-причин зарегистрироваться на главной странице `karaoke-public`
(`HomeView.vue`), виден только анонимам, с CTA «Зарегистрироваться» и трекингом
клика в `tbl_events`. Чистый фронтенд: без новых таблиц, без нового backend-кода,
переиспользует существующие `useAuth()` и `trackUi()`.

**Отклонение от spec.md, согласовано с пользователем (2026-07-25)**: пункт 5
«Скидка -10% на первую подписку» заменён — в БД нет гарантированно активного
`NEW_USER_PERCENT`-правила (`tbl_promo_rules` настраивается вручную админом,
публичного API проверить активность нет), рекламировать скидку, которой может
не быть, нельзя (см. `research.md` Decision 2). Пункт 5 заменён на
«🆓 Бесплатно — без карты» (регистрация не требует оплаты — факт, который всегда
верен, ничего дополнительно проверять не нужно).

## Technical Context

**Language/Version**: JavaScript (ES2020+), Vue 3.4+ (Options API — `HomeView.vue`
целиком в Options API, новый код следует тому же стилю файла).

**Primary Dependencies**: `composables/useAuth.js` (`isLoggedIn` — определить
аноним/зарег.), `services/tracking.js` (`trackUi` — клик по CTA), Vue Router
(переход на `/register`). Никаких новых зависимостей.

**Storage**: N/A — новых таблиц не требуется (spec.md Assumptions). Клики
трекаются через существующий `POST /api/public/events` (тот же путь, что и
`trackUi`/`trackLinkToSocialNetwork` в других местах сайта).

**Testing**: ручное тестирование в браузере (в `karaoke-public` автотестов нет).
Сценарии — в `quickstart.md`.

**Target Platform**: браузер (мобильный + десктоп), `karaoke-public` SPA.

**Project Type**: web — фронтенд-дополнение к существующему `HomeView.vue`.
Backend не меняется.

**Performance Goals**: NFR-001 — блок не должен замедлять загрузку главной.
Достигается тривиально: контент полностью статический (hardcoded массив из
5 объектов), никаких дополнительных HTTP-запросов.

**Constraints**:
- Только CSS-переменные `--km-*` (FR-008).
- **Важная поправка к FR-006/FR-009 spec.md**: в текущем `karaoke-public`
  **нет** разделения `views/classic/` / `views/modern/` — это устаревшая
  формулировка, унаследованная из старого JSDoc-комментария в самом
  `HomeView.vue` (строки 122-134), который описывает архитектуру, никогда не
  реализованную (`localStorage.km-design` нигде не читается, директорий
  `views/classic/`/`views/modern/` не существует — есть только единый
  `HomeView.vue` с переключателем light/dark-темы через `useDesign()`).
  См. `research.md` Decision 1. Блок реализуется **один раз**, в едином
  `HomeView.vue`, адаптивным через CSS grid (часть FR-006 про
  desktop/tablet/mobile-раскладку остаётся в силе и достигается обычным
  `grid-template-columns` + media query, как остальные блоки этой страницы).
- Регистрация всегда бесплатна (пункт 5) — факт, не требующий проверки БД.

**Scale/Scope**: 1 существующий файл на правку (`HomeView.vue`: +1 секция в
template, +1 data-массив, +1 computed на `isLoggedIn`, +1 метод для трекинга
клика).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| # | Принцип | Соответствие | Обоснование |
|---|---------|--------------|-------------|
| I | Self-contained автопайплайн | N/A | Не медиа-пайплайн. |
| II | Сырой JDBC + дифф по хэшам | N/A | Нет БД-изменений. |
| III | Двух-БД синхронизация | N/A | Не затрагивается. |
| IV | Async-очередь задач | N/A | Нет долгих операций. |
| V | Двух-фронтенд: admin и public | ✅ PASS | Только `karaoke-public`, `webvue3` не трогается. |
| VI | Code Standards | ✅ PASS | Правка существующего файла с JSDoc уже покрыта; новых экспортируемых символов, требующих отдельного JSDoc-блока, не создаётся (правки внутри существующего `export default`). |

**Итог**: без нарушений. Complexity Tracking не нужен.

## Project Structure

### Documentation (this feature)

```text
specs/004-reasons-to-register/
├── plan.md              # Этот файл
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output (новых сущностей нет)
├── quickstart.md        # Phase 1 output
└── tasks.md             # Phase 2 output (/speckit.tasks)
```

`contracts/` не создаётся — новых REST-эндпоинтов нет, трекинг идёт через уже
существующий `POST /api/public/events`.

### Source Code (repository root)

```text
karaoke-public/
└── src/
    └── views/
        └── HomeView.vue    # УЖЕ ЕСТЬ, правка: +секция «Почему стоит зарегистрироваться»
                             #   между .km-desc и .km-nav-cards (см. research.md Decision 3)
```

**Structure Decision**: точечная правка одного существующего файла, без новых
компонентов (в отличие от `003-about-page`, где потребовался новый
`ShareButton.vue` — здесь контент проще: 5 статических карточек + 1 CTA,
не оправдывает вынесение в отдельный компонент).

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| *(нет)* | — | — |

Constitution Check прошёл без нарушений.
