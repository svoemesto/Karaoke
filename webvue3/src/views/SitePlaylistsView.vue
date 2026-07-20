<template>
  <div class="siteplayliststable">
    <SitePlaylistsTable />
  </div>
</template>

<script>
import SitePlaylistsTable from '../components/SitePlaylists/SitePlaylistsTable.vue'
export default {
  name: 'SitePlaylistsView',
  components: { SitePlaylistsTable },
}
</script>

<style scoped>
.siteplayliststable {
  display: flex;
  flex-direction: column;
  margin: 10px auto;
  justify-content: center;
  align-items: center;
}
</style>
