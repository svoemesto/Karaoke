<template>
  <div class="promotionsview">
    <PromotionsTable />
  </div>
</template>

<script>
import PromotionsTable from '../components/Promotions/PromotionsTable.vue'

/**
 * View-страница «Promotions» — основной layout и data-fetching.
 *
 * @see AGENTS.md
 */
export default {
  name: 'PromotionsView',
  components: { PromotionsTable },
}
</script>
