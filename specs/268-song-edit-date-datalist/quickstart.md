# Quickstart: проверка возврата datalist в поле «Дата» (`SongEdit.vue`)

Ручная проверка (в CI автотестов для webvue3-UI нет, см. `plan.md` →
Technical Context → Testing). Выполняется на dev-машине с локально
поднятым стеком (`karaoke-app` + `webvue3` + локальная БД).

> **Перед проверкой** убедитесь, что вы на feature-ветке
> `268-song-edit-date-datalist`, а **не** на `master`:
>
> ```bash
> git branch --show-current
> # → 268-song-edit-date-datalist
> ```

## Предпосылки

- Локальный стек поднят (`karaoke-app` + `webvue3` + локальная PostgreSQL)
  — см. `DEVELOPMENT.md` / `deploy/do.sh`.
- Сборка фронта актуальна:
  ```bash
  cd webvue3 && npm run build && npm run lint && npx prettier --check \
    "src/components/Songs/edit/SongEdit.vue"
  ```
  Все три шага MUST проходить без новых нарушений.
- В БД `tbl_songs` есть хотя бы несколько записей с заполненными
  `publish_date`/`publish_time` за последние N дней (иначе `freeTimeSlots`
  вернёт только сегодняшние/завтрашние даты — поведение корректное, но
  визуально бедное для демонстрации).
- Тестовый браузер — Chrome 120+, Edge 120+, Firefox 120+ или Safari 17+
  (целевые платформы, см. SC-001).

## Сценарий 1 — поле «Дата» показывает datalist при фокусе (User Story 1, FR-001, FR-003, FR-004, SC-001)

1. Открыть админку (`webvue3`), перейти в карточку любой песни (`SongEdit.vue`).
2. Очистить поле «Дата» (если оно заполнено — `Ctrl+A`, `Delete`).
3. Кликнуть в поле «Дата» (поставить фокус).
4. **Ожидаемо**: браузер показывает выпадающий список **только** из вариантов
   datalist (`dd.MM.yy HH:mm` на каждый час с 10:00 до 22:00 включительно —
   до 13 элементов), **без** собственного списка «Предлагать заполнение поля».
5. **Ожидаемо**: ни один из вариантов не находится в прошлом.

### Контрольный признак успеха

В DevTools (F12) → Elements → найти `<input ... list="list_free_time_slots" ...>`.
**Должны быть** атрибуты:

```html
<input
  ...
  list="list_free_time_slots"
  name="song_date_field"
  autocomplete="off"
/>
```

## Сценарий 2 — фильтрация при наборе (User Story 1, Acceptance Scenario 2)

1. Не убирая фокус с поля «Дата», начать вводить символы, например `30`.
2. **Ожидаемо**: выпадающий список фильтруется, остаются только варианты
   datalist, начинающиеся с `30` (например, `30.08.26 14:00`, `30.08.26 15:00`).
   Браузерный автокомплит не подмешивается.

## Сценарий 3 — поле «Дата» непустое, фокус (User Story 1, Acceptance Scenario 3, 4)

1. В поле «Дата» ввести значение `12.08.26 14:00` (или взять существующее).
2. Кликнуть в поле (поставить фокус).
3. **Ожидаемо**: datalist доступен (варианты можно выбрать стрелками или
   увидеть при клике); история браузера не вытесняет datalist.
4. Ввести произвольный «мусор» в поле (например, `qwe`), поставить фокус.
5. **Ожидаемо**: datalist показывается первым; браузерный список «Предлагать
   заполнение поля» **не** появляется (благодаря `autocomplete="off"`).

## Сценарий 4 — поле «Время» (User Story 2, FR-005, SC-002)

1. Очистить поле «Время», кликнуть в него (поставить фокус).
2. **Ожидаемо**: выпадающий список содержит **ровно 6 вариантов** —
   `11:00`, `12:00`, `13:00`, `14:00`, `15:00`, `16:00`. Без браузерного
   автокомплита.

### Контрольный признак успеха

В DevTools → найти `<input ... list="list_hours" ...>`:

```html
<input
  ...
  list="list_hours"
  name="song_time_field"
  autocomplete="off"
/>
```

## Сценарий 5 — кросс-браузерная проверка (SC-001)

Повторить Сценарий 1 в **трёх** браузерах из списка:

- [ ] Chrome 120+ (или актуальная стабильная)
- [ ] Firefox 120+ (или актуальная стабильная)
- [ ] Safari 17+ (если доступен на dev-машине)
- [ ] Edge 120+ (если есть на Windows-машине)

Для каждого:

- [ ] Datalist показывается **вместо** браузерного автокомплита.
- [ ] Все варианты — в будущем.
- [ ] При наборе текста фильтрация работает.

## Сценарий 6 — проверка CI webvue3 (NFR-002, NFR-003, SC-003)

```bash
cd webvue3

# 1. Линтер
npm run lint
# Ожидаемо: 0 новых warnings/errors (baseline может содержать legacy, но
# diff не должен добавлять новых).

# 2. Prettier
npx prettier --check "src/components/Songs/edit/SongEdit.vue"
# Ожидаемо: "All matched files use Prettier code style!"

# 3. Vite build
npm run build
# Ожидаемо: успешный build (✓ built in N.NNs), без новых warnings
# от нашего diff.
```

## Сценарий 7 — прямой API-тест (опционально, sanity-check, что бэк не сломан)

```bash
curl -s -X POST http://localhost:<PORT>/api/getfreetimeslots | jq
```

**Ожидаемо**: JSON-массив из 13 строк формата `dd.MM.yy HH:mm`,
часы 10:00..22:00, все даты строго в будущем (контракт не меняется,
см. [`contracts/getfreetimeslots.md`](./contracts/getfreetimeslots.md)).
Если массив пуст — возможно, локальная БД пуста; это не регрессия
нашего фикса.

## Definition of Done (recap)

- [ ] Все 6 сценариев пройдены в Chrome (минимум; +Firefox/Safari/Edge
      если доступны).
- [ ] `npm run lint`, `prettier --check`, `npm run build` — без новых нарушений.
- [ ] Дифф только в `webvue3/src/components/Songs/edit/SongEdit.vue`,
      ≤25 добавленных строк.
- [ ] `livedocs/features/156-publish-slots-range.md` обновлён (секция
      «История» + ссылка на спку 268).
- [ ] CI 7/7 PASS для ветки `268-song-edit-date-datalist`.
- [ ] PR смержен в `master` через `gh pr merge --merge` (без `--delete-branch`).