<template>
  <div class="listeninghistoryview">
    <ListeningHistoryTable />
  </div>
</template>

<script>
import ListeningHistoryTable from '../components/ListeningHistory/ListeningHistoryTable.vue'

/**
 * View-страница «История прослушиваний» — глобальный read-only список `tbl_listening_history`
 * с JOIN к `tbl_songs`, SKIP-фильтром на чтении, фильтрами, пагинацией 500/стр.
 *
 * @see AGENTS.md
 * @see specs/171-admin-subscriptions-history/spec.md
 */
export default {
  name: 'ListeningHistoryView',
  components: { ListeningHistoryTable },
}
</script>

<style scoped>
.listeninghistoryview {
  display: flex;
  flex-direction: column;
  margin: 10px auto;
  justify-content: center;
  align-items: center;
}
</style>
