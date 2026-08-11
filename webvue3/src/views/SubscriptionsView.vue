<template>
  <div class="subscriptionsview">
    <SubscriptionsTable />
  </div>
</template>

<script>
import SubscriptionsTable from '../components/Subscriptions/SubscriptionsTable.vue'

/**
 * View-страница «Подписки» — глобальный read-only список `tbl_subscriptions` с фильтрами,
 * пагинацией и target-aware переключателем (local/remote).
 *
 * @see AGENTS.md
 * @see specs/171-admin-subscriptions-history/spec.md
 */
export default {
  name: 'SubscriptionsView',
  components: { SubscriptionsTable },
}
</script>

<style scoped>
.subscriptionsview {
  display: flex;
  flex-direction: column;
  margin: 10px auto;
  justify-content: center;
  align-items: center;
}
</style>
