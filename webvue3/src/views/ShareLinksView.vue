<template>
  <div class="sharelinksview">
    <ShareLinksTable />
  </div>
</template>

<script>
import ShareLinksTable from '../components/ShareLinks/ShareLinksTable.vue'

/**
 * View-страница «Временные ссылки» — глобальный read-only список `tbl_song_share_links`
 * с JOIN к `tbl_songs`/`tbl_site_users`, фильтрами и действием «Отозвать»
 * через переиспользуемый `revokeSiteUserShareLink`.
 *
 * @see AGENTS.md
 * @see docs/features/guest-share-link.md
 * @see specs/171-admin-subscriptions-history/spec.md
 */
export default {
  name: 'ShareLinksView',
  components: { ShareLinksTable },
}
</script>

<style scoped>
.sharelinksview {
  display: flex;
  flex-direction: column;
  margin: 10px auto;
  justify-content: center;
  align-items: center;
}
</style>
