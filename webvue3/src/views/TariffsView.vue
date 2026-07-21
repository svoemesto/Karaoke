<template>
  <div class="tariffsview">
    <TariffsTable />
  </div>
</template>

<script>
import TariffsTable from '../components/Tariffs/TariffsTable.vue'

/**
 * View-страница «Tariffs» — основной layout и data-fetching.
 *
 * @see AGENTS.md
 */
export default {
  name: 'TariffsView',
  components: { TariffsTable },
}
</script>
