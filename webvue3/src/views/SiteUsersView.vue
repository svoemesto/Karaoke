<template>
  <div class="siteuserstable">
    <SiteUsersTable />
  </div>
</template>

<script>
import SiteUsersTable from '../components/SiteUsers/SiteUsersTable.vue'

/**
 * View-страница «Site Users» — основной layout и data-fetching.
 *
 * @see AGENTS.md
 */
export default {
  name: 'SiteUsersView',
  components: {
    SiteUsersTable,
  },
}
</script>

<style scoped>
.siteuserstable {
  display: flex;
  flex-direction: column;
  margin: 10px auto;
  justify-content: center;
  align-items: center;
}
</style>
