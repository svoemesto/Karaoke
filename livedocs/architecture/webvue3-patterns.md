---
status: Active
slug: webvue3-patterns
type: topic
related:
  - ../architecture/L2-containers.md
---

# webvue3 — паттерны

> Drill-down из `AGENTS.md` секции «Важные паттерны».
> Этот LiveDoc — полная версия. В AGENTS.md осталась только короткая ссылка.

## Персистентность страницы пагинации

**Проблема**: `currentPage` живёт в `data()` компонента; при уходе с роута
Vue выгружает компонент и значение сбрасывается. Чтобы при уходе
«Песни → Публикации → Песни» открывалась та же страница — храним номер в Vuex.

**Решение** (3 вещи в сторе + 2 вещи в компоненте):

### В сторе (модуль с таблицей)

```js
// components/Songs/store.js — пример
export default {
  state: {
    songsTableCurrentPage: 1,
    // ...
  },
  getters: {
    getSongsTableCurrentPage: (state) => state.songsTableCurrentPage,
  },
  mutations: {
    setSongsTableCurrentPage(state, page) {
      state.songsTableCurrentPage = page;
    },
  },
};
```

### В компоненте таблицы

```vue
<script>
export default {
  data() {
    return {
      currentPage: this.$store.getters.getSongsTableCurrentPage || 1,
      // ...
    };
  },
  watch: {
    currentPage(newPage) {
      this.$store.commit('setSongsTableCurrentPage', newPage);
    },
    countRows(newCount) {
      // Ослабленная логика: сбрасывать currentPage только если вышла за пределы
      const totalPages = Math.ceil(newCount / this.perPage);
      if (this.currentPage > totalPages) {
        this.currentPage = 1;
      }
    },
  },
};
</script>
```

### Применено в

`SongsTable` (с расширенной логикой `countRows`), `ProcessesTable`,
`AuthorsTable`, `PicturesTable`, `SiteUsersTable`, `SitePlaylistsTable`,
`PropertiesTable`, `DictionariesTable`. В `StatsView` — две отдельные пагинации
(`statsBySongPage` / `webEventsPage`) в сторе `stats`.

### Правило для новых таблиц

Применять тот же шаблон к **любой новой таблице в `webvue3`** сразу, иначе
пользователь потеряет позицию при переключении пунктов меню.

## Таблицы `karaoke-public` — `table-layout: fixed` + явная `width`

`<table class="table" style="table-layout: fixed;">` требует явной `width: Npx`
для каждой колонки. Без этого ширины «прыгают» при скролле.

**Колонки платформ**: 16 иконок × 22px = 352px (точная ширина).

**Ловушка**: `display: flex` на `<td>` ломает высоту строки — использовать
`text-align: center; vertical-align: middle`.

## Bootstrap 5 в `karaoke-public`

`<select>` → класс `form-select` (НЕ `form-control`). Это частая ошибка при
миграции с Bootstrap 4 на 5.

## Картинки — только MinIO, НЕ БД

Поле `picture_full` в БД всегда `""`. Картинки только в MinIO. `PicturesDTO`
содержит `previewUrl`/`fullUrl`.

При загрузке картинки **всегда** использовать `ignoreUseInList = false`
(см. оптимизацию в фиче 186).

## Тег SKIP — заглушка

Если в `tags` есть `SKIP`, показывается заглушка «удалено по требованию
правообладателя». Теги наружу не утекают (фильтруются на сервере).

## Табулатура (ASCII-only)

При отображении ASCII-табулатур:
- `-` вместо `⎼` (U+23BC).
- `||` вместо `‖` (U+2016).

Unicode ломает выравнивание через font fallback. ASCII-only даёт стабильный
рендер во всех браузерах.

## HealthReport — видео проверяются только при idStatus >= 6

Не трогать логику для видео при статусе < 6 (видео ещё нет). Это правило
предотвращает ложные «видео отсутствует» в HealthReport.

## Связанные LiveDocs

- Architecture: [L2-containers.md](../architecture/L2-containers.md) (Vue 3 SPA)

## Код

- `webvue3/src/components/Songs/SongsTable.vue` — образец паттерна пагинации
- `webvue3/src/store/modules/Songs/store.js` — образец стора
- `karaoke-public/src/components/Songs/SongsTable.vue` — public-аналог

## История

- Создан: 2026-08-14 (мигрировано из `AGENTS.md` v1.7.1)
- Последнее обновление: 2026-08-14