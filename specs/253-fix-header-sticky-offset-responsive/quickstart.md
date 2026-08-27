# Quickstart: 253 — sticky-блок приклеивается к AppHeader на узких экранах

**Branch**: `253-fix-header-sticky-offset-responsive` | **Date**: 2026-08-27
**Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md)

## Назначение

Пошаговая валидация фикса после правки:
- `karaoke-public/src/style.css` — добавление `--km-header-height` + media queries.
- `karaoke-public/src/views/ZakromaView.vue` — `top: var(--km-header-height, 53px)`.

Это **чисто клиентский CSS-фикс**. Полный приёмочный чек-лист — в
[spec.md § Success Criteria](spec.md#success-criteria-mandatory).

## Prerequisites

1. Сборка фронта: `cd karaoke-public && npm run build` (или `deploy/do.sh build_start_public`).
2. Браузер с DevTools (Chromium / Firefox).
3. Feature-ветка `253-fix-header-sticky-offset-responsive`.

## Setup

```bash
git checkout 253-fix-header-sticky-offset-responsive

# Локальный запуск фронта (если не поднят)
cd karaoke-public
npm install
npm run dev

# Альтернативно — полный docker-compose
cd ../deploy && bash do.sh build_start_public
```

## Сценарии валидации

### V-1: визуальная проверка на разных viewport'ах

1. Открыть `/zakroma?author=Машина Времени` (крупный автор — 6 типов альбомов, ~2500 песен).
2. Chrome DevTools → Toggle device toolbar → **Responsive**:
   - **1280 × 800** (desktop) → прокрутить до `scrollY = 800` → визуально: 0 зазора между шапкой и sticky-wrapper.
   - **700 × 800** (узкий десктоп) → прокрутить → 0 зазора.
   - **500 × 800** (мобильный) → прокрутить → 0 зазора.
   - **375 × 667** (iPhone SE) → прокрутить → 0 зазора; 2-строчный блок типов не отрезается.

**Ожидание**: на всех 4 viewport'ах `scrollY > 0` → нет визуального пустого пространства между нижней границей AppHeader и верхней границей `.km-author-header-sticky`.

### V-2: DevTools-замер gap'а

На каждом viewport'е из V-1:

1. Скроллить до `scrollY = 200` (чтобы sticky «прилип»).
2. Открыть Console, выполнить:

```js
const h = document.querySelector('.km-header').getBoundingClientRect()
const s = document.querySelector('.km-author-header-sticky').getBoundingClientRect()
const gap = s.top - h.bottom
console.log({ headerBottom: h.bottom, stickyTop: s.top, gap })
```

**Ожидание** (`gap ∈ [-1, 1] px`):
| Viewport | headerBottom | stickyTop | gap |
|----------|--------------|-----------|-----|
| 1280×800 | 53 | 53 | ~0 |
| 700×800 | 49 | 49 | ~0 |
| 500×800 | 46 | 46 | ~0 |
| 375×667 | 46 | 46 | ~0 |

Если `gap > 1` px — фикс не сработал: проверить, что CSS-переменная определена в `style.css` и что `ZakromaView.vue` использует `var(--km-header-height, 53px)`.

### V-3: проверка media queries

В Chrome DevTools → Elements → выбрать `<html>` → Styles panel:
1. Найти `:root { --km-header-height: 53px; }` в `style.css`.
2. Переключить viewport между breakpoints (700 / 500) — значение должно переключаться: 53 → 49 → 46 px.
3. В Console: `getComputedStyle(document.documentElement).getPropertyValue('--km-header-height')` должно возвращать текущее значение (например, `49px` на 700 px viewport).

### V-4: проверка регрессии (спека 252)

Повторить quickstart.md спек 252:
- US1 на desktop 1280×800 (нет overlap'а между фильтром и блоком типов).
- US2 на mobile 375×667 (2-строчный блок типов не отрезается).

**Ожидание**: поведение из спек 252 сохранено — фикс не регрессирует sticky-wrap.

### V-5: проверка других view

Открыть страницы, использующие AppHeader:
- `/` (главная)
- `/search`
- `/account` (если залогинен)
- `/author-playlist?author=Машина Времени`

Визуально: шапка приклеена к верху viewport, нормально отображается на всех breakpoint'ах. Стик-блоки этих view (если есть у них свои) — без изменений.

## Проверка линтеров и сборки

```bash
cd karaoke-public
npm run lint                                  # 0 warnings
bash ../tools/check-eslint-baseline.sh karaoke-public   # 0 violations
npm run build                                 # PASS
```

## Бэкенд-проверка

```bash
cd /home/nsa/Karaoke
./gradlew :karaoke-app:bootJar :karaoke-web:bootJar --parallel
```

**Ожидание**: `:karaoke-web:bootJar UP-TO-DATE` (бэкенд не задет; `karaoke-app:bootJar` артефакт без изменений).

## Edge-кейсы (после фикса)

| Кейс | Ожидание |
|------|----------|
| Viewport 320×568 (iPhone 5) | AppHeader на 500-px стеке (46 px); gap ≤ 1 px. |
| Resize 1280 → 400 плавно | sticky обёртка пересчитывается по media queries без перезагрузки. |
| Pixel 6 (412×915) | 700-px стек (49 px); шапка 1-строчная (типично) → gap 0. |
| 700 px + очень длинный `back` text в AppHeader + все 3 виджета | Шапка может перенестись на 2 строки (assumption (b)) → gap увеличится. **Out of scope этой спеки.** См. spec.md § Edge Cases. |

## Куда смотреть, если что-то не так

| Симптом | Возможная причина | Действие |
|---------|------------------|----------|
| Gap на 700 px всё ещё 4 px | CSS-переменная не подгрузилась (нет импорта `style.css`) | Проверить `main.js` — должен импортировать `./style.css`. |
| Gap 0 на desktop, но не на mobile | Media queries в `style.css` не на тех breakpoints | Сравнить breakpoints с `AppHeader.vue:211, 232` — должно быть совпадение. |
| `var(--km-header-height)` возвращает пусто | Переменная не определена на `:root` (опечатка, не там объявили) | Проверить `style.css` — должно быть `:root { --km-header-height: 53px; }` (НЕ `body` или `.km-page`). |
| На 700 px логотип 32 px, но шапка всё равно 53 px | AppHeader.vue был отредактирован отдельно — sync нарушен | Обновить `:root --km-header-height` в `style.css` под новые значения. |
| `Fallback: 53px` сработал на desktop, но обёртка «прыгает» между 49 и 53 при resize на 700 px | Опечатка в media query | Проверить `@media (max-width: 700px) { --km-header-height: 49px; }`. |

## После успешной валидации

1. `git status` показывает только ожидаемые 2 файла + артефакты спеки 253.
2. `git diff --stat` — без посторонних изменений.
3. Pre-commit хуки (FR-007 Конституции): ktlint/eslint + секрет-чек.
4. PR в master через `gh pr create --base master` (требует согласия пользователя).
5. После merge — деплой через `cd deploy && bash do.sh build_start_public` (требует согласия).

## Документация

- LiveDoc 253 создаётся в Phase 6 (Polish) — `livedocs/features/253-fix-header-sticky-offset-responsive.md` (по образцу 252).
- `livedocs/features/252-fix-author-album-types-hide.md` обновляется: добавляется секция «См. также: bug-fix 253» (выполняет FR-009 Конституции Principle VI).
- `livedocs/architecture-notes.md` — запись «Pass 253» (по образцу Pass 251 / 252).
