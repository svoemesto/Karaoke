<template>
  <div class="upm-overlay" @click.self="close">
    <SitePlaylistDetailModal v-if="isDetailVisible" @close="closeDetail" />
    <div class="upm-modal">
      <div class="upm-head">
        <div class="upm-title">Плейлисты пользователя {{ userLabel }}</div>
        <button class="upm-close" @click="close">×</button>
      </div>

      <div v-if="isLoading" class="upm-loading">Загрузка...</div>
      <template v-else>
        <table class="upm-table">
          <thead>
            <tr>
              <th>Плейлист</th>
              <th>Избр.</th>
              <th>Песен</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="pl in playlists" :key="pl.id" class="upm-row" @click="openDetail(pl.id)">
              <td class="upm-left">{{ pl.name }}</td>
              <td>{{ pl.favorites ? '★' : '' }}</td>
              <td>{{ pl.itemsCount }}</td>
            </tr>
          </tbody>
        </table>
        <div v-if="!playlists.length" class="upm-empty">Плейлистов нет</div>
      </template>
    </div>
  </div>
</template>

<script>
import SitePlaylistDetailModal from '../SitePlaylists/SitePlaylistDetailModal.vue'

/**
 * Модальное окно для playlists.
 *
 * @emits close
 *
 * @see AGENTS.md
 */

export default {
  name: 'UserPlaylistsModal',
  components: { SitePlaylistDetailModal },
  props: {
    siteUserId: { type: Number, required: true },
    userLabel: { type: String, default: '' },
    target: { type: String, default: 'local' },
  },
  emits: ['close'],
  data() {
    return { isDetailVisible: false }
  },
  computed: {
    playlists() {
      return this.$store.getters.getSitePlaylistsDigest
    },
    isLoading() {
      return this.$store.getters.getSitePlaylistsDigestIsLoading
    },
  },
  async mounted() {
    await this.$store.dispatch('setSitePlaylistsTarget', this.target)
    this.$store.dispatch('loadSitePlaylistsDigest', { filterOwnerId: this.siteUserId })
  },
  methods: {
    openDetail(id) {
      this.$store.dispatch('clearSitePlaylistDetail')
      this.$store.dispatch('loadSitePlaylistDetail', id)
      this.isDetailVisible = true
    },
    closeDetail() {
      this.isDetailVisible = false
    },
    close() {
      this.$emit('close')
    },
  },
}
</script>

<style scoped>
.upm-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1070;
}
.upm-modal {
  background: #fff;
  border-radius: 10px;
  padding: 16px;
  width: 560px;
  max-width: 95vw;
  max-height: 88vh;
  overflow-y: auto;
  font-size: small;
}
.upm-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 10px;
}
.upm-title {
  font-size: 1.05rem;
  font-weight: 700;
}
.upm-close {
  background: transparent;
  border: none;
  font-size: 1.5rem;
  cursor: pointer;
  line-height: 1;
}
.upm-loading,
.upm-empty {
  text-align: center;
  color: gray;
  padding: 20px;
}
.upm-table {
  width: 100%;
  border-collapse: collapse;
}
.upm-table th,
.upm-table td {
  border: 1px solid #ddd;
  padding: 4px 8px;
  text-align: center;
}
.upm-left {
  text-align: left;
}
.upm-row {
  cursor: pointer;
}
.upm-row:hover {
  background-color: #eef3f2;
}
</style>
