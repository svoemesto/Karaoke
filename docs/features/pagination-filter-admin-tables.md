# Feature: Корректная пагинация таблиц admin SPA после применения фильтра

**Spec**: [`specs/300-author-pagination-filter-bug/spec.md`](../../specs/300-author-pagination-filter-bug/spec.md)
**Status**: In progress (Pass 300, реализация в feature-ветке `300-author-pagination-filter-bug`)
**Created**: 2026-09-03
**Last updated**: 2026-09-03
**Source**: OpenProject #50 — «Неверное поведение на страницах автора после фильтра»

## Назначение

В admin SPA `webvue3` после применения фильтра к таблице с пагинацией (Authors, Albums, Pictures, SiteUsers, Dictionaries, Processes, Properties и др.) на странице N>1, если фильтр сужает выборку до меньшего числа страниц, чем текущая, таблица показывает **пустую страницу** вместо корректного перехода на последнюю доступную страницу с записями.

## Bug description

**Симптом** (из OpenProject #50):

> «Админка, компонент «Авторы». Получаем всех авторов. Переходим на любую страницу, кроме первой. Применяем фильтр, который возвращает одну страницу (например список авторов с новыми альбомами). В этом случае на экране показывается как будто первая и единственная страница выборки, но она пустая.»

**Сценарий воспроизведения**:

1. Открыть `Authors` (или любую таблицу из списка аудита).
2. Перейти на страницу N>1 (например, 3).
3. Применить фильтр, сужающий выборку до ≤ 1 страницы.
4. Ожидается: видны записи результата фильтра, текущая страница = 1.
5. **Наблюдается до фикса**: «Page 1 of 1», но таблица пустая (currentPage остался 3, b-table пытается показать страницу 3 несуществующего контента).

## Корневая причина

В `webvue3/src/components/<Entity>/<Entity>Table.vue`:

```vue
<b-pagination
  v-model="currentPage"
  :total-rows="countRows"   <!-- ← countRows = digests.length, длина ТЕКУЩЕЙ страницы -->
  :per-page="perPage"
/>
```

```javascript
countRows() {
  return this.<digests> ? this.<digests>.length : 0  // surrogate для total
}
```

В `<Entity>Table.vue` есть **watcher только на `currentPage`** (для сохранения в стор):

```javascript
watch: {
  currentPage: {
    handler(newPage) {
      this.$store.commit('set<Entity>TableCurrentPage', newPage)
    },
  },
},
```

…но **нет watcher на `countRows`**, который бы пересчитал `totalPages = ceil(countRows / perPage)` и сбросил `currentPage`, если он вышел за пределы.

**Цепочка бага**:

1. `currentPage = 3` (админ на стр. 3).
2. Применяется фильтр → `load<Entity>Digests` action → backend возвращает массив длиной ≤ `perPage`.
3. `commit('set<Entity>Digests', result)` → `digests` обновляется → `countRows` computed → `<b-pagination :total-rows>` видит уменьшенное значение → визуально показывает «Page 1 of 1».
4. **Но `currentPage` остаётся 3** (нет watcher).
5. `<b-table :current-page="3">` пытается показать страницу 3, которой нет в массиве → пустая таблица.

## Pattern (эталон) — `Songs/SongsTable.vue:998-1009`

`SongsTable.vue` уже имеет правильный watcher, который и нужно скопировать:

```javascript
watch: {
  countRows: {
    handler(newCount) {
      // Сбрасываем на 1, если текущая страница вышла за пределы после загрузки/фильтрации.
      // Иначе (при первом монтировании компонента) сохраняем страницу, на которой был пользователь.
      // @see docs/features/pagination-filter-admin-tables.md (FR-006, эталон — Songs/SongsTable.vue:998-1009)
      const totalPages = Math.max(1, Math.ceil(newCount / this.perPage))
      if (this.currentPage > totalPages) {
        this.currentPage = 1
      }
    },
  },
},
```

**Почему это работает** (и почему безопаснее, чем «всегда сбрасывать на 1»):

- При **первом монтировании** компонента `countRows` инициализируется из уже сохранённого `digests` (если есть в store). Watcher срабатывает, но `currentPage` уже подгружен из `get<Entity>TableCurrentPage` через `data()`. Если страница валидна (`currentPage <= totalPages`) — сброса не происходит. Если невалидна — сбрасывается на 1.
- При **сужении фильтра** (`countRows` уменьшается) → `totalPages` уменьшается → если `currentPage > totalPages`, сброс на 1.
- При **расширении фильтра** (`countRows` увеличивается) → `totalPages` увеличивается → если `currentPage <= totalPages`, сброса нет, остаёмся на текущей странице (плавный UX).
- При **сбросе фильтра** → аналогично сужению/расширению, в зависимости от объёма.

**Политика FR-006** («всегда страница 1 после сброса фильтра») — согласована в Clarifications 2026-09-03. Реализуется **через watcher**: если `currentPage > totalPages` после новой выборки, сбрасываем на 1. Это покрывает и сброс фильтра, и сужение фильтра в один проход — без необходимости знать «был ли сброс».

## Affected tables (из `audit.md`)

| Таблица | Файл | Статус |
|---------|------|--------|
| Authors | `webvue3/src/components/Authors/AuthorsTable.vue` | Требует фикса (MVP, OP#50) |
| Albums | `webvue3/src/components/Albums/AlbumsTable.vue` | Требует фикса |
| Pictures | `webvue3/src/components/Pictures/PicturesTable.vue` | Требует фикса |
| SiteUsers | `webvue3/src/components/SiteUsers/SiteUsersTable.vue` | Требует фикса |
| Dictionaries | `webvue3/src/components/Dictionaries/DictionariesTable.vue` | Требует фикса |
| Processes | `webvue3/src/components/Processes/ProcessesTable.vue` | Требует фикса |
| Properties | `webvue3/src/components/Properties/PropertiesTable.vue` | Требует фикса |

**Уже имеют правильный паттерн** (не требуют фикса):

- `Songs/SongsTable.vue` — эталон (watcher на countRows)
- `ShareLinks/ShareLinksTable.vue` — уже есть watcher (line 265)
- `Subscriptions/SubscriptionsTable.vue` — уже есть watcher (line 256)
- `News/NewsTable.vue` + `News/store.js` — другой правильный паттерн (`setNewsTarget` сбрасывает `newsCurrentPage(1)` + `totalCount` в state)

## Почему News и Songs работают по-разному

### News: target-based паттерн

`webvue3/src/components/News/store.js:85-92`:

```javascript
setNewsTarget(ctx, target) {
  ctx.commit('setNewsTarget', target)
  ctx.commit('setNewsCurrentPage', 1)  // ← явный сброс на 1
}
```

News сбрасывает страницу в **action** при смене target (а не в watcher на countRows), потому что у News в state есть **реальный `newsTotalCount`** (backend возвращает `{news: [...], total: N}`). Это позволяет точно знать `totalPages` в любой момент. При смене фильтра или target — явный сброс на 1.

### Songs: watcher-based паттерн (эталон для текущего фикса)

Songs не имеет `total` в backend-ответе, поэтому использует `countRows = songsDigest.length` как surrogate. Watcher на countRows реагирует на изменение длины массива и пересчитывает `totalPages` каждый раз. Это полностью client-side решение, не требует backend-изменений.

## Future work (вне этой задачи)

Добавление **реального `total` в backend-ответы** для Authors/Albums/Pictures/SiteUsers/Dictionaries/Processes/Properties позволит:

1. Заменить `countRows = digests.length` на `countRows = get<Entity>TotalCount` (точное число страниц).
2. Корректно отображать «Page X of Y» (Y = реальное число страниц, а не 1).
3. Упростить watcher (всегда доверять `total`, а не `digests.length`).

Это потребует:

- Изменения `*Controller.kt` в `karaoke-app` (добавить `total` в `mapOf(...)`).
- Изменения соответствующего `*/store.js` (добавить `set<Entity>TotalCount` mutation).
- Изменения `<Entity>Table.vue` (использовать `get<Entity>TotalCount` в `countRows` computed).

Watcher на countRows **продолжит работать** без изменений (он реагирует на computed, не на state напрямую).

**Это отдельная задача** — выходит за рамки текущего баг-фикса.

## Как применить фикс в новой таблице

При добавлении **новой** таблицы в admin SPA с фильтром + пагинацией:

1. Создать `store.js` с `state.<entity>Digest = []`, `state.<entity>TableCurrentPage = 1`, `set<Entity>Digests`, `set<Entity>TableCurrentPage`, `load<Entity>Digests`.
2. В `<Entity>Table.vue`:
   - `data()`: `currentPage: this.$store.getters.get<Entity>TableCurrentPage || 1`, `perPage: 30`.
   - `computed`: `countRows() { return this.<digests> ? this.<digests>.length : 0 }`, `<digests>() { return this.$store.getters.get<Entity>Digest }`.
   - `watch`: **обязательно** добавить `countRows` watcher по этому шаблону.
3. Проверить по сценарию 1 из `quickstart.md` (страница N>1 → фильтр → корректная страница).

## Связанные документы

- [`specs/300-author-pagination-filter-bug/spec.md`](../../specs/300-author-pagination-filter-bug/spec.md) — спецификация (FR-001…FR-011, User Stories 1-3, Clarifications)
- [`specs/300-author-pagination-filter-bug/plan.md`](../../specs/300-author-pagination-filter-bug/plan.md) — implementation plan
- [`specs/300-author-pagination-filter-bug/research.md`](../../specs/300-author-pagination-filter-bug/research.md) — корневая причина, decisions, файл-список
- [`specs/300-author-pagination-filter-bug/data-model.md`](../../specs/300-author-pagination-filter-bug/data-model.md) — client state + backend response shapes
- [`specs/300-author-pagination-filter-bug/audit.md`](../../specs/300-author-pagination-filter-bug/audit.md) — аудит таблиц (FR-008)
- [`specs/300-author-pagination-filter-bug/quickstart.md`](../../specs/300-author-pagination-filter-bug/quickstart.md) — 9 ручных validation scenarios
- [`specs/300-author-pagination-filter-bug/tasks.md`](../../specs/300-author-pagination-filter-bug/tasks.md) — задачи
- OpenProject #50 — исходный баг-репорт
- `webvue3/src/components/Songs/SongsTable.vue:998-1009` — эталон правильного watcher
- `webvue3/src/components/News/store.js:85-92` — пример target-based сброса (News)

## Версионирование

- **v300.1.0** (эта итерация): добавлен watcher в Authors, Albums, Pictures, SiteUsers (минимум для OP#50 + US2). Dictionaries/Processes/Properties — в той же ветке или отдельной, по результатам скоупа.
- **v300.2.0** (future): добавить `total` в backend-ответы для всех таблиц admin SPA, переключить `countRows` на `get<Entity>TotalCount` (см. «Future work»).