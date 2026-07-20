<template>
  <div class="promotionsview">
    <PromotionsTable />
  </div>
</template>

<script>
import PromotionsTable from '../components/Promotions/PromotionsTable.vue'
export default {
  name: 'PromotionsView',
  components: { PromotionsTable },
}
</script>
