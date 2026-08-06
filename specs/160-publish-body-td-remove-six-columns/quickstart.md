# Quickstart: Валидация фичи #160 за 5 шагов

**Phase**: 1 (Design & Contracts)
**Branch**: `160-publish-body-td-remove-six-columns`
**Spec**: [`./spec.md`](./spec.md)
**Data Model**: [`./data-model.md`](./data-model.md)
**API Contract**: [`./contracts/api-songsdigests.md`](./contracts/api-songsdigests.md)
**Date**: 2026-08-06

Этот документ — runnable validation guide: 5 шагов, которые проверяют, что фича работает end-to-end. Не включает код реализации — только команды и ожидаемые результаты.

---

## Предусловия

| Требование | Команда проверки |
|---|---|
| Java/JDK 17 | `java -version` |
| Node 22 (LTS) | `node -v` |
| Backend собирается | `cd /home/nsa/Karaoke && ./gradlew :karaoke-app:bootJar -x test` |
| Frontend зависимости | `cd /home/nsa/Karaoke/webvue3 && npm install` |
| Backend запущен локально (admin-машина) | dev-окружение с `WORKING_DATABASE` |
| Frontend dev-сервер запущен | `cd /home/nsa/Karaoke/webvue3 && npm run dev` |

---

## Шаг 1: Визуальная проверка таблицы «Публикации»

**Цель**: проверить US1 + US2 (только `publish-name` 210 px, без шести цветовых колонок).

**Действия**:
1. Открыть `http://localhost:5173/publications` (или адрес dev-сервера `webvue3`).
2. Авторизоваться (если требуется).
3. Перейти в раздел «Публикации».

**Ожидаемый результат**:
- Каждая ячейка `PublishTableBodyTd` содержит **только** название песни.
- Шести цветовых блоков-индикаторов больше нет.
- Названия длинных песен визуально влезают в ячейку 210 px.

**Измерение** (DevTools → Elements):
1. Выделить ячейку `.publish-name` (любую заполненную).
2. Вкладка **Computed** → `width`: `210px`.
3. Контейнер `.publish`: `min-width: 210px`, `max-width: 210px`.
4. Элементов с классом `publish-column` в DOM — **0**.

**Команда-эквивалент** (консоль браузера):
```javascript
const name = document.querySelector('.publish-name')
const width = getComputedStyle(name).width
const columns = document.querySelectorAll('.publish-column').length
console.log({ width, columns })
// Ожидается: { width: "210px", columns: 0 }
```

**SC**: SC-001, SC-002, SC-003.

---

## Шаг 2: Визуальная проверка кнопок PLAY в SongEdit

**Цель**: проверить US3 (4 PLAY-кнопки без раскраски).

**Действия**:
1. В таблице «Публикации» кликнуть на любую песню → открывается `SongEdit`.
2. Прокрутить до группы кнопок воспроизведения.

**Ожидаемый результат**:
- 4 кнопки «PLAY KARAOKE / LYRICS / CHORDS / TABS» имеют **одинаковый фон** (CSS-класс `.group-button`).
- Ни одна не окрашена в красный/зелёный/серый (фон по умолчанию).

**Измерение** (DevTools → Elements):
1. Выделить любую из 4 кнопок.
2. Вкладка **Styles** → нет правила `background-color: ...` из атрибута `style="..."` (inline).
3. Inline-стиль `style="background-color: rgb(...)"` — **отсутствует**.

**Команда-эквивалент** (консоль браузера):
```javascript
const buttons = document.querySelectorAll('.group-button[title^="PLAY"]')
const inlineBgs = [...buttons].map(b => b.style.backgroundColor)
console.log({ count: buttons.length, inlineBgs })
// Ожидается: { count: 4, inlineBgs: ["", "", "", ""] } (пустые inline-стили)
```

**SC**: SC-005.

---

## Шаг 3: Проверка JSON-ответа `/api/songsdigests`

**Цель**: проверить US4 (DTO содержит ровно 1 поле `processColor*`).

**Действия**:
1. Открыть DevTools → Network.
2. Перезагрузить таблицу «Публикации».
3. Найти запрос `GET /api/songsdigests` → Response.

**Ожидаемый результат** (jq-эквивалент):
```bash
curl -s -b "JSESSIONID=..." "http://localhost:8080/api/songsdigests" \
  | jq '.[0] | keys | map(select(startswith("processColor")))'
# Ожидается: ["processColorPlayerDemo"]
```

**Ручная проверка** (DevTools → Network → Response):
- В JSON одной песни ищем `processColor*` — ровно одно поле: `processColorPlayerDemo`.
- Полей `processColorMeltLyrics`, `processColorMeltKaraoke`, `processColorBoosty`, `processColorVkLyrics` и т.п. — нет.

**SC**: SC-004.

---

## Шаг 4: Визуальная проверка бейджа DE в SongsTable

**Цель**: убедиться, что единственный оставшийся живой потребитель `processColorPlayerDemo` (бейдж `DE` в `SongsTable.vue`) продолжает работать.

**Действия**:
1. Открыть раздел «Песни» (`SongsTable.vue`).
2. Найти колонку `flagPlayerDemo` (бейдж `DE`).

**Ожидаемый результат**:
- Бейдж `DE` показывает цвет из `processColorPlayerDemo` (зелёный `#00FF00` если демо готово, серый `#A9A9A9` если нет).

**Команда-эквивалент** (консоль браузера):
```javascript
// Через Vue DevTools выбрать строку с flagPlayerDemo
const row = /* выбрать строку */
const demoColor = row.processColorPlayerDemo
console.log({ demoColor, flagPlayerDemo: row.flagPlayerDemo })
// Ожидается: соответствие цвета и значения бейджа
```

**SC**: SC-008 (часть бейджа DE).

---

## Шаг 5: Запуск линтеров и проверок качества

**Цель**: убедиться, что PR не сломал baseline.

**Действия** (выполняются на машине разработчика, на CI запускаются автоматически):

```bash
# Backend Kotlin lint
cd /home/nsa/Karaoke && ./gradlew ktlintCheck

# Frontend ESLint + baseline check
cd /home/nsa/Karaoke/webvue3 && npm run lint:check
cd /home/nsa/Karaoke && bash tools/check-eslint-baseline.sh webvue3

# KDoc coverage
cd /home/nsa/Karaoke && bash tools/check-kdoc-coverage.sh

# JSDoc coverage
cd /home/nsa/Karaoke && bash tools/check-jsdoc-coverage.sh webvue3

# Pre-commit (все 7 проверок)
cd /home/nsa/Karaoke && pre-commit run --all-files
```

**Ожидаемый результат**:
- Все линтеры — SUCCESS.
- Baseline не вырос (новых нарушений нет).
- KDoc/JSDoc coverage = 100%.
- Pre-commit — все 7 проверок зелёные.

**SC**: SC-006, SC-007 (CI gate).

---

## Сводка шагов ↔ Success Criteria

| Шаг | SC | Описание |
|---|---|---|
| 1 | SC-001, SC-002, SC-003 | Только `publish-name` 210 px |
| 2 | SC-005 | PLAY-кнопки без inline `background-color` |
| 3 | SC-004 | JSON содержит ровно 1 поле `processColorPlayerDemo` |
| 4 | SC-008 (часть) | Бейдж `DE` показывает цвет |
| 5 | SC-006, SC-007 | Линтеры и CI зелёные |

**SC-008** (полный) проверяется визуально после деплоя на проде:
- В `publications.html`/`unpublications.html` цвета «полосок» обновляются через SSE → данные берутся из `/song/{id}` (raw `Song`), не из DTO. Продолжает работать.
