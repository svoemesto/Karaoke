<template>
  <div class="songstable">
    <SongsTable />
  </div>
</template>

<script>
// @ is an alias to /src
import SongsTable from '../components/Songs/SongsTable.vue'

/**
 * View-страница «Songs» — список песен с фильтрами.
 *
 * Контейнер для `SongsTable` + `SongsFilterModal`. Использует Vuex-модуль
 * `songs` для хранения фильтров (выбранные словари, авторы, статусы,
 * publish-флаги). Применение фильтра → `SongsTable` перезагружает данные
 * через `loadList`.
 *
 * Содержит также кнопки bulk-операций (sync, push, pull, delete) —
 * см. `SongsTable.vue` для деталей.
 *
 * @see docs/features/dual-db-sync.md
 * @see docs/features/mlt-generator.md (откуда берутся параметры рендера)
 */
export default {
  name: 'SongView',
  components: {
    SongsTable,
  },
}
</script>

<style scoped>
.songstable {
  display: flex;
  flex-direction: column;
  margin: 10px auto;
  justify-content: center;
  align-items: center;
}
</style>
