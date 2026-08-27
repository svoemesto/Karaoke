# Quickstart: 254 — header-back-link «К списку авторов»

**Branch**: `254-fix-zakroma-header-back-link` | **Date**: 2026-08-27
**Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md)

## Назначение

Пошаговая валидация после правки `karaoke-public/src/views/ZakromaView.vue`:
1. AppHeader `:back` стал динамическим (computed `zakromaHeaderBack`).
2. Удалены два in-page `<button class="km-back-btn">`.
3. Удалены scoped-CSS `.km-back-btn`.

Чисто клиентский фикс, 1 файл. Полный приёмочный чек-лист — в [spec.md § Success Criteria](spec.md#success-criteria-mandatory).

## Prerequisites

1. Сборка фронта: `cd karaoke-public && npm run build`.
2. Браузер с DevTools.
3. Ветка `254-fix-zakroma-header-back-link`.

## Setup

```bash
git checkout 254-fix-zakroma-header-back-link
cd karaoke-public
npm install   # если ещё не
npm run dev   # или deploy/do.sh build_start_public
```

## Сценарии валидации

### V-1: `/zakroma` без автора — back-link скрыт

1. Открыть `http://localhost:5173/zakroma` (без query).
2. В DevTools-Console:
   ```js
   const links = document.querySelectorAll('.km-header-left .km-back')
   console.log({ count: links.length, texts: Array.from(links).map(a => a.textContent.trim()) })
   ```
3. **Ожидание**: `count === 0` — слева в шапке НЕТ back-link. Логотип справа работает (ведёт на `/`).
4. Визуально: слева в шапке пусто (только правый слот с auth-widget / theme-toggle / логотипом).

### V-2: `/zakroma?author=X` — back-link «К списку авторов»

1. Открыть `http://localhost:5173/zakroma?author=Машина Времени`.
2. В DevTools-Console:
   ```js
   const links = document.querySelectorAll('.km-header-left .km-back')
   console.log({
     count: links.length,
     text: links[0] ? links[0].textContent.trim() : null,
     href: links[0] ? links[0].getAttribute('href') : null,
     // RouterLink может рендериться как <a>, для проверки link-attributes:
     target: links[0] ? links[0].outerHTML : null
   })
   ```
3. **Ожидание**:
   - `count === 1`,
   - `text === '← К списку авторов'`,
   - `href` начинается с `/zakroma` (без `?author=`).
4. В теле страницы **под** AppHeader:
   ```js
   document.querySelectorAll('.km-back-btn').length
   ```
   **Ожидание**: `0` — НЕТ in-page кнопок «← К списку авторов».

### V-3: Клик на back-link — корректный переход

1. На странице `/zakroma?author=Машина Времени` кликнуть на header-back-link «← К списку авторов».
2. **Ожидание**:
   - URL меняется на `http://localhost:5173/zakroma` (без query),
   - `document.location.pathname === '/zakroma'`,
   - `document.location.search === ''` (нет `?author=`),
   - Сетка тайлов авторов (`.at-tile`) отображается (DOM содержит `.km-page` → `.km-content` → `.at-grid`),
   - В шапке back-link исчезает (см. V-1).

### V-4: Спец-корзина (?specialBucket=true)

1. Открыть `http://localhost:5173/zakroma?specialBucket=true`.
2. **Ожидание**: back-link виден (т.к. `specialBucketShown === true`), label «← К списку авторов», target `/zakroma`.
3. Клик → URL `localhost:5173/zakroma`, сетка тайлов авторов.

### V-5: Регрессия — sticky/sticky-wrapper сохранилась

1. На `/zakroma?author=Машина Времени` проскроллить к таблице песен.
2. **Ожидание**: AppHeader + `.km-author-header-sticky` обёртка прилипают к верху (спеки 250 + 252 + 253 не сломаны).
3. Header-back-link виден внутри sticky AppHeader.

### V-6: Регрессия — другие view

1. Открыть `/author-playlist?author=Машина Времени`, `/song?id=…`, `/account`, `/search`.
2. **Ожидание**: их `AppHeader :back` props работают независимо (некоторые передают свои, некоторые используют `<slot name="left">`). Наша правка только в `ZakromaView.vue` — другие view не задеты.

## Проверка линтеров и сборки

```bash
cd /home/nsa/Karaoke/karaoke-public
npm run lint                                       # 0 warnings
bash tools/check-eslint-baseline.sh karaoke-public  # 0 violations
npm run build                                      # PASS
```

## Бэкенд-проверка

```bash
cd /home/nsa/Karaoke
./gradlew :karaoke-app:bootJar :karaoke-web:bootJar --parallel
# Ожидание: :karaoke-web:bootJar UP-TO-DATE
```

## Edge-кейсы (после фикса)

| Кейс | Ожидание |
|------|----------|
| `/zakroma?author=` (пустой query) | Header-back-link скрыт (т.к. `!!'' === false` в `authorChosen = !!this.$route.query.author`). |
| `/zakroma?specialBucket=true&author=X` (невалидная комбинация) | Header-back-link виден, клик → `/zakroma` без query, оба state сбрасываются. |
| AppHeader в slot-режиме (`<slot name="left">`) в каком-то view | Наш фикс НЕ затрагивает slot-API. Другие view не задеты. |
| `backToAuthors()` метод больше не вызывается | Метод остаётся в `methods` (для будущего использования, например, программная навигация). Header-back-link работает через vue-router переход на `/zakroma`. |

## Куда смотреть, если что-то не так

| Симптом | Возможная причина | Действие |
|---------|------------------|----------|
| Back-link не появляется при `authorChosen=true` | Computed `zakromaHeaderBack` не определён / не возвращает объект | Проверить, что в `computed:` секции `ZakromaView.vue` есть `zakromaHeaderBack() { ... }`. Hard-reload. |
| Back-link ведёт на `/` вместо `/zakroma` | Где-то остался статический `:back` | Проверить `grep ':back="{ to' ZakromaView.vue` — должно быть только `:back="zakromaHeaderBack"`. |
| In-page кнопки `.km-back-btn` всё ещё видны | Удаление не сработало | Проверить `grep '.km-back-btn' ZakromaView.vue` — должно быть пусто. |
| Hard-reload не помог | Браузерный кэш | Ctrl+Shift+R (или ⌘+Shift+R), либо DevTools → Network → Disable cache. |

## После успешной валидации

1. `git status` показывает только `karaoke-public/src/views/ZakromaView.vue` + LiveDocs + spec-артефакты.
2. `git diff --stat` — без посторонних изменений.
3. Pre-commit хуки (FR-007 Конституции): ktlint/eslint + секрет-чек.
4. PR в master через `gh pr create --base master` (требует согласия пользователя).
5. После merge — деплой `karaoke-public` через `deploy/do.sh build_start_public` (требует согласия).

## Документация

- `livedocs/features/254-fix-zakroma-header-back-link.md` — новый LiveDoc.
- `livedocs/features/250-unify-site-header.md` — опционально добавить cross-ref на спек 254 (показывает явное использование `back: null`).
- `livedocs/architecture-notes.md` — запись «Pass 254» (по образцу Pass 251 / 252 / 253).
