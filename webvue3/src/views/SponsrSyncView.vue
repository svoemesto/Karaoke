<template>
  <div class="sponsrsyncview">
    <SponsrSyncPanel />
  </div>
</template>

<script>
import SponsrSyncPanel from '../components/SponsrSync/SponsrSyncPanel.vue'

/**
 * View-страница «Sponsr Sync» — основной layout и data-fetching.
 *
 * @see AGENTS.md
 */
export default {
  name: 'SponsrSyncView',
  components: { SponsrSyncPanel },
}
</script>
