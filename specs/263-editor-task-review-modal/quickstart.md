# Quickstart: 263 — Улучшение модалки проверки задания

**Date**: 2026-08-30
**Spec**: [spec.md](./spec.md)
**Plan**: [plan.md](./plan.md)

## Цель

Этот документ — практический runbook для проверки фичи end-to-end после реализации. Не содержит implementation-деталей (они в [data-model.md](./data-model.md) и будут в `tasks.md`).

## Prerequisites

- Локальная копия Karaoke на машине разработчика (см. [docs/onboarding.md](../../docs/onboarding.md) и [livedocs/onboarding.md](../../livedocs/onboarding.md)).
- Запущенные контейнеры:
  - `karaoke-app` (admin-движок, не пересобирается агентом — см. AGENTS.md § «Категорически запрещено»).
  - `karaoke-web` (API).
  - `webvue3` (admin SPA, порт 7906).
- Браузер: Chrome/Firefox/Safari последних 2 мажорных версий (для devtools).
- Доступ к admin-лк (admin-учётка создаётся в `webvue3`).
- Задание в статусе `submitted` (на проверке) с НЕпустой разметкой (`parsedMarkers.length > 0`) — для проверки всех 4 US.

## Setup / build / smoke-checks

### Перед началом — sanity baseline

```bash
cd /home/nsa/Karaoke

# 1. ESLint baseline ДОЛЖЕН быть пустым (0 нарушений). Если есть — стоп.
cat webvue3/.eslint-baseline.json
# Ожидаемый вывод: []

# 2. Vite-сборка ДОЛЖНА проходить без warning'ов о cross-package imports.
cd webvue3 && npm run build
# Ожидаемый вывод: ✓ built in ... без warning "Rollup failed to resolve import"

# 3. После правок ReviewModal.vue — те же проверки.
cd /home/nsa/Karaoke/webvue3
npm run lint                # никаких НОВЫХ нарушений (baseline OK)
npm run build               # успешная сборка
npm run format:check        # prettier OK (Pass 244 fix)
```

Если `npm run build` падает с `Rollup failed to resolve import` — включить fallback (см. [contracts/README.md](./contracts/README.md)).

### Frontend dev-режим

```bash
cd /home/nsa/Karaoke/webvue3
npm run dev
# Открыть http://localhost:7906/ (или порт из .env)
```

## Validation scenarios

Сценарии привязаны к US из спеки. Каждый сценарий — ручная проверка в браузере + (опционально) DOM-замер через devtools console.

### Scenario 1: Текст пользователя выровнен по левому краю (US1)

**Setup**:
1. Открыть http://localhost:7906/.
2. Авторизоваться как admin.
3. Перейти в меню «Задания редактора» → выбрать задание в статусе `На проверке` с непустым текстом → клик «Открыть» (или эквивалент, открывающий `ReviewModal`).

**Validation**:
1. Открыть DevTools → Console, выполнить:
   ```js
   getComputedStyle(document.querySelector('.se-text')).textAlign
   ```
   Ожидаемый результат: `'left'` (или `'start'` в RTL — но проект LTR, должно быть `'left'`).

3. Изменить ширину окна (ресайз DevTools) — проверить, что выравнивание не меняется (всё ещё `'left'`).

**Pass criteria**: `getComputedStyle().textAlign === 'left'` при любой ширине окна ≥768px.

---

### Scenario 2: Блок «Разметка» отображается с правильной палитрой (US2)

**Setup**: то же, что в Scenario 1. Задание с НЕпустыми `parsedMarkers` (т.е. редактор наразметил хотя бы несколько слогов).

**Validation**:
1. В модалке видны ТРИ блока в строке (на десктопе ≥1024px): «Текст пользователя», «Разметка», «Маркеры: N».
2. Блок «Разметка» имеет **чёрный фон**:
   ```js
   getComputedStyle(document.querySelector('.se-markup')).backgroundColor
   ```
   Ожидаемый результат: `'rgb(0, 0, 0)'` (или `'rgba(0, 0, 0, 1)'`).
3. Внутри блока «Разметка» есть `<span class="ke-fx-groupN">` для каждого слога:
   ```js
   document.querySelectorAll('.se-markup .ke-fx-group0, .se-markup .ke-fx-group1, .se-markup .ke-fx-group2, .se-markup .ke-fx-group3').length
   ```
   Ожидаемый результат: `> 0` (хотя бы один слог).
4. Переводы строк через `<br>`:
   ```js
   document.querySelectorAll('.se-markup br').length
   ```
   Ожидаемый результат: совпадает с количеством `endofline + newline` маркеров в задании (плюс один завершающий, если есть `COMMENT|…`).
5. (Если есть `COMMENT|…` маркеры) комментарии курсивом и охрой:
   ```js
   getComputedStyle(document.querySelector('.se-markup .ke-fx-comment')).fontStyle
   ```
   Ожидаемый результат: `'italic'`.

**Pass criteria**: визуальная идентичность с правой колонкой karaoke-public `EditorWorkView.vue` при открытии того же задания в `/account/editor/...` (для сверки).

---

### Scenario 3: Размер шрифта соответствует настройкам редактора (US3)

**Setup**: то же, что в Scenario 1.

**Sub-scenario 3a: дефолтные значения (16/18)**.

**Setup**: убедиться, что в localStorage нет кастомных настроек:
```js
localStorage.removeItem('karaoke-editor-settings')
location.reload()
```

**Validation**:
```js
getComputedStyle(document.querySelector('.se-text')).fontSize
// Ожидаемый результат: '16px'

getComputedStyle(document.querySelector('.se-markup')).fontSize
// Ожидаемый результат: '18px'
```

**Sub-scenario 3b: кастомные значения (24/30)**.

**Setup**:
1. Открыть тот же `ReviewModal` → клик «Открыть в редакторе» → откроется `SongKaraokeEditorModal` (`SongKaraokeEditorView.vue`).
2. В редакторе — найти ползунки «Шрифт 16px» (textarea) и «Шрифт 18px» (preview) — сдвинуть на 24 и 30 соответственно.
3. Закрыть редактор (без обязательного сохранения — настройки шрифта сохраняются в localStorage автоматически).
4. Открыть `ReviewModal` заново (из таблицы заданий).

**Validation**:
```js
getComputedStyle(document.querySelector('.se-text')).fontSize
// Ожидаемый результат: '24px'

getComputedStyle(document.querySelector('.se-markup')).fontSize
// Ожидаемый результат: '30px'
```

**Sub-scenario 3c: clamp при невалидных значениях**.

**Setup**: через DevTools console:
```js
localStorage.setItem('karaoke-editor-settings', JSON.stringify({ textFontSize: 100, previewFontSize: 0 }))
location.reload()
```

**Validation**:
```js
getComputedStyle(document.querySelector('.se-text')).fontSize
// Ожидаемый результат: '36px' (clamp вверх)

getComputedStyle(document.querySelector('.se-markup')).fontSize
// Ожидаемый результат: '6px' (clamp вниз)
```

**Pass criteria**: все три sub-scenario проходят, шрифт НИКОГДА не выходит за [6, 36]px.

---

### Scenario 4: Блок «Маркеры» в одну строку (US4)

**Setup**: то же, что в Scenario 1. Задание с `markerCount > 0` (хотя бы несколько маркеров).

**Validation**:
1. Визуально: счётчики «Слоги · Концы строк · Новые строки · END: есть/нет» идут **в одну строку** (на ширине ≥768px).
2. DOM-замер:
   ```js
   const summary = document.querySelector('.se-marker-summary')
   const h = summary.getBoundingClientRect().height
   const lineHeight = parseFloat(getComputedStyle(summary).fontSize) * 1.5
   console.log('Height:', h, '1.5×lineHeight:', lineHeight)
   // Ожидаемый результат: h < lineHeight × 1.5 (одна строка, не 4)
   ```
3. Сузить окно до <500px — счётчики переносятся (`flex-wrap: wrap`), но заголовок «Маркеры: N» остаётся на своей строке.

**Pass criteria**: одна строка на десктопе, допустимый перенос на мобиле.

---

### Scenario 5: Переключение голоса (US2.4)

**Setup**: задание с `voiceCount > 1` (многоголосная песня, например 2 голоса).

**Validation**:
1. Над блоком «Разметка» (и над «Текст пользователя») видны табы голосов «Голос 1», «Голос 2».
2. Кликнуть «Голос 2» → блоки «Текст пользователя» и «Разметка» обновляются на содержимое 2-го голоса за <50мс (один tick реактивности Vue).
3. Количество `<br>` в `.se-markup` может измениться (если у голосов разное количество `endofline`/`newline`).

**Pass criteria**: переключение мгновенное, без задержки, без ошибок в console.

---

### Scenario 6: Защита от XSS / стабильность (Edge Cases)

**Validation**:
1. Задание с `parsedMarkers` содержащим `COMMENT|потенциально_опасный_текст<script>alert(1)</script>` — в блоке «Разметка» ничего не должно произойти (alert не сработает; текст будет виден как обычный).
   - Если сработает — сервер не валидирует label, нужно эскалировать как баг upstream (фича 263 НЕ чинит эту проблему; см. notes в спеке).
2. Открыть DevTools → Network → открыть модалку → убедиться, что НЕТ новых HTTP-запросов (всё работает на клиентских данных `a.draftMarkersPerVoice`).

**Pass criteria**: console чистая, никаких лишних запросов.

---

### Scenario 7: Все 6 точек входа `ReviewModal` (Scope check)

`ReviewModal` рендерится в 6 местах (см. [livedocs/features/154-editor-tasks-manage.md](../../livedocs/features/154-editor-tasks-manage.md) и codegraph `ReviewModal.vue`):
1. `SongEditorTable` (раздел «Задания редактора»).
2. `SongsTable` (таблица песен).
3. `SongEdit` (карточка песни).
5–6. Прочие ссылки в `SongsTable`/`SongEdit`.

**Validation**:
- Открыть модалку из КАЖДОЙ точки входа — все 4 US работают одинаково (одна реализация компонента).

**Pass criteria**: визуально идентично во всех точках входа.

---

## Production-like smoke checks (опционально)

После merge в master и прохождения CI `lint.yml`:

1. `deploy/do.sh build_webvue3` — успешная пересборка контейнера `webvue3` (если у вас есть доступ к deploy-машине).
2. Через `http://<admin-host>:7906/` пройти все 7 scenarios.
3. Проверить логи nginx на 4xx/5xx после открытия модалки.

## Out of scope (НЕ проверять)

- Слайдер шрифта в самой модалке — НЕ реализован (см. Clarifications спеки).
- Изменение backend — НЕ должно ничего меняться (никаких deploy на `karaoke-web`/`karaoke-app` для этой фичи).
- Синхронизация/одобрение задания — НЕ меняется (только визуальная часть модалки).
- Поддержка `curMarkerIndex !== -1` (динамическая подсветка текущего слога в реальном времени) — НЕ реализовано в модалке (нет плеера); только в онлайн-редакторе.

## Когда считать фичу готовой

- ✅ Все 7 scenarios проходят.
- ✅ `npm run lint` / `npm run build` / `npm run format:check` без ошибок.
- ✅ LiveDoc `livedocs/features/263-editor-task-review-modal.md` создан в том же PR.
- ✅ CI `lint.yml` на PR в master — PASS.