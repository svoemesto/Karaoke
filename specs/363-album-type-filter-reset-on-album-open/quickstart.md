# Quickstart: Сброс фильтра категории альбома при открытии песен конкретного альбома

**Feature**: [spec.md](./spec.md)
**Date**: 2026-09-10
**Branch**: `363-album-type-filter-reset-on-album-open`

> Руководство по проверке фичи end-to-end после её реализации.
> Сценарии воспроизводят баг (issue #78) и подтверждают фикс.

## Prerequisites

1. **Запущенные контейнеры** на этой машине (`nsa-i9`):
   ```bash
   docker ps --format '{{.Names}}\t{{.Status}}' | grep -E 'karaoke-public|nginx'
   # Должны быть: karaoke-public (Up), nginx (Up).
   ```
2. **Если правки ещё не задеплоены** — собрать образ `karaoke-public`:
   ```bash
   cd deploy && bash do.sh build_public
   ```
3. **Браузер** с DevTools (Chrome / Firefox).

## Сценарий 1 — Главный кейс (баг + фикс)

**Цель**: подтвердить, что открытие сингла с активным фильтром «скрыть синглы» показывает песни альбома.

### Шаги

1. Открыть `https://localhost/` (или соответствующий URL).
2. Перейти на любую страницу автора: `/zakroma/<id>` (например, через тайлы на главной).
3. **Дождаться загрузки** — увидеть список песен автора.
4. Открыть DevTools → `Application` → `Local Storage` → `https://localhost`.
5. Найти ключ `km-zakroma-hidden-album-types`. Если его нет — создать:
   ```javascript
   localStorage.setItem('km-zakroma-hidden-album-types', JSON.stringify(['single']))
   ```
   Или через UI: кликнуть кнопку «Синглы (N)» в шапке — она должна переключиться в «выключено» и ключ появится.
6. Перезагрузить страницу (`F5`) — список альбомов с типом `single` должен исчезнуть (фильтр активен).
7. Перейти на `/zakroma/<id>/albums` — кликнуть плашку «Альбомы автора» в шапке.
8. Найти в сетке альбом с типом `single` (например, по обложке/названию, если помните) — кликнуть на него.

### Ожидаемый результат ✅

- Открывается страница `/zakroma/<id>?albumId=<singleAlbumId>`.
- **Песни альбома отображаются** (не пустая страница).
- В шапке **нет панели `.km-album-controls-bar`** (ни переключателя «Сквозной/По типам», ни фильтра категорий) — скрыта через `v-if` (Clarifications Q2).
- В DevTools → Console — нет ошибок.

### Проверка после возврата

9. Кликнуть back-link «← К альбомам автора» → попадаем на `/zakroma/<id>/albums`.
10. Кликнуть «← К списку авторов» → попадаем на `/zakroma` (или на `/zakroma/<id>`, если прямая ссылка).
11. Открыть DevTools → `Application` → `Local Storage`:
    ```javascript
    localStorage.getItem('km-zakroma-hidden-album-types')
    // Должно вернуть: '["single"]' (НЕ изменилось).
    ```
12. Перейти на `/zakroma/<id>` — список песен автора должен быть **как был** (альбомы-синглы скрыты).

---

## Сценарий 2 — Контр-кейс (тип НЕ в скрытых)

**Цель**: подтвердить, что auto-reset не срабатывает «превентивно» — только когда тип реально скрыт.

### Шаги

1. Выполнить шаги 1–6 из Сценария 1 (скрыть `single`).
2. Перейти на `/zakroma/<id>/albums`.
3. Кликнуть на плашку **студийного** альбома (тип `studio`).

### Ожидаемый результат ✅

- Открывается `/zakroma/<id>?albumId=<studioAlbumId>`.
- Песни студийного альбома отображаются.
- В шапке **вся панель `.km-album-controls-bar` скрыта** (переключатель «Сквозной/По типам» + фильтр-бар) — на любом `?albumId=` (Clarifications Q2).
- `localStorage.km-zakroma-hidden-album-types === '["single"]'` (не изменился — `studio` и так не был скрыт).

---

## Сценарий 3 — Regression: ручная правка фильтра через UI

**Цель**: убедиться, что НЕ сломали существующую механику (Pass 012, FR-025/027).

### Шаги

1. Открыть `/zakroma/<id>` (без `?albumId=`).
2. В шапке кликнуть кнопку «Сборники (5)».
3. Подождать 1 сек. → в DevTools:
   ```javascript
   localStorage.getItem('km-zakroma-hidden-album-types')
   // Должно содержать 'compilation'.
   ```
4. Перезагрузить страницу (`F5`) → кнопка «Сборники» должна быть в состоянии «выключено» (выделение/нажатие как раньше).
5. Кликнуть ещё раз → `compilation` удаляется из `localStorage`.

### Ожидаемый результат ✅

- Все клики корректно отражаются в `localStorage`.
- После перезагрузки фильтр сохраняется.

---

## Сценарий 4 — Edge case: прямой URL на «несуществующий» альбомId

**Цель**: убедиться, что пустой/невалидный albumId не ломает фильтр.

### Шаги

1. Скрыть `single` в `localStorage`.
2. Перейти на `/zakroma/<id>?albumId=999999` (id несуществующего альбома).

### Ожидаемый результат ✅

- Бэк вернёт пустой `zakroma` (или ошибку 404).
- `effectiveHiddenAlbumTypes` ≡ `hiddenAlbumTypes` (нет совпадения по albumId).
- Вся панель `.km-album-controls-bar` **скрыта** (так как `selectedAlbumId != null` — Clarifications Q2).
- В DevTools нет uncaught-исключений.

---

## Сценарий 5 — Edge case: все типы скрыты

**Цель**: убедиться, что открытие единственного «видимого» альбома после auto-reset работает.

### Шаги

1. Скрыть ВСЕ типы в `localStorage`:
   ```javascript
   localStorage.setItem('km-zakroma-hidden-album-types', JSON.stringify(['studio','live','compilation','bootleg','single','archive','tribute']))
   ```
2. Перейти на `/zakroma/<id>/albums` — сетка пустая (всё скрыто).
3. Открыть напрямую `/zakroma/<id>?albumId=<someAlbumId>`.

### Ожидаемый результат ✅

- `effectiveHiddenAlbumTypes` = `{studio,live,compilation,bootleg,single,archive,tribute} \ {albumType открытого}` = 6 типов.
- Песни открытого альбома отображаются.
- При возврате на `/zakroma/{id}` (без `?albumId=`) — снова 7 скрытых типов.

---

## Чек-лист после реализации

- [ ] Все 5 сценариев пройдены.
- [ ] `localStorage.km-zakroma-hidden-album-types` НЕ изменился в сценариях 1, 2, 5 (только в сценарии 3 — от ручного клика).
- [ ] `console.error` в DevTools — пусто при прохождении сценариев.
- [ ] CI 7/7 PASS (см. AGENTS.md § CI 7/7 PASS — обязательно перед merge):
  - ktlintCheck
  - ESLint + Prettier в `karaoke-public`
  - Docs (structure + offline links) — автоматически
  - KDoc/JSDoc coverage ≥50%
  - Baseline stats — informational
- [ ] Нет прямых коммитов в `master` — только ветка `363-...` + PR + CI (см. AGENTS.md § Git — CI-gate для master).

## Changelog

- **2026-09-10**: Initial. Автор: agent (Karaoke).