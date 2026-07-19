<template>
  <div class="spt-table">
    <SitePlaylistDetailModal v-if="isDetailVisible" @close="closeDetail" />

    <div class="spt-toolbar">
      <label class="spt-toolbar-item">
        БД:
        <select v-model="target" @change="onTargetChange">
          <option value="local">Локальная</option>
          <option value="remote">Сервер</option>
        </select>
      </label>
      <input class="spt-toolbar-item" v-model.number="filterOwnerId" placeholder="Фильтр по owner id" @keyup.enter="reload">
      <button class="spt-toolbar-item spt-btn" @click="reload">Обновить</button>
    </div>

    <div class="spt-table-header">
      <b-pagination v-model="currentPage" :total-rows="countRows" :per-page="perPage" :limit="40" size="sm" pills />
    </div>

    <div class="spt-table-body">
      <b-table
          :items="digest"
          :busy="isBusy"
          :fields="fields"
          :per-page="perPage"
          :current-page="currentPage"
          v-model:sort-by="sortBy"
          small bordered hover
          @row-clicked="onRowClicked"
      >
        <template #table-busy>
          <div class="text-center text-danger my-2"><b-spinner class="align-middle" /><strong>Loading...</strong></div>
        </template>
        <template #table-colgroup="scope">
          <col v-for="field in scope.fields" :key="field.key" :style="field.style">
        </template>
        <template #cell(name)="data">
          <div class="fld-name" @click.left="openDetail(data.item.id)">
            <span v-if="data.item.favorites" title="Избранное">★ </span>{{ data.value }}
          </div>
        </template>
        <template #cell(favorites)="data">
          <div style="text-align: center">{{ data.value ? '★' : '' }}</div>
        </template>
      </b-table>
    </div>

    <div class="spt-table-footer"><span>Всего: {{ countRows }}</span></div>
  </div>
</template>

<script>
import { BPagination, BSpinner, BTable } from 'bootstrap-vue-next'
import SitePlaylistDetailModal from "./SitePlaylistDetailModal.vue";

export default {
  name: "SitePlaylistsTable",
  components: { SitePlaylistDetailModal, BPagination, BSpinner, BTable },
  data() {
    return {
      perPage: 45,
      // Восстанавливаем последнюю страницу из store, чтобы при уходе с компонента и возврате таблица
      // открывалась на той же странице.
      currentPage: this.$store.getters.getSitePlaylistsTableCurrentPage || 1,
      sortBy: [],
      isBusy: false,
      isDetailVisible: false,
      filterOwnerId: null
    }
  },
  watch: {
    digestIsLoading() { this.isBusy = this.digestIsLoading },
    currentPage(newPage) {
      // Сохраняем страницу в store, чтобы она восстановилась после переключения на другой компонент.
      this.$store.commit('setSitePlaylistsTableCurrentPage', newPage);
    }
  },
  computed: {
    digestIsLoading() { return this.$store.getters.getSitePlaylistsDigestIsLoading },
    digest() { return this.$store.getters.getSitePlaylistsDigest },
    countRows() { return this.digest ? this.digest.length : 0 },
    target: {
      get() { return this.$store.getters.getSitePlaylistsTarget },
      set(value) { this.$store.dispatch('setSitePlaylistsTarget', value) }
    },
    fields() {
      return [
        { key: 'id', sortable: true, label: 'ID', style: { minWidth: '50px', maxWidth: '50px', textAlign: 'center', fontSize: 'small' } },
        { key: 'name', sortable: true, label: 'Плейлист', style: { minWidth: '220px', maxWidth: '220px', textAlign: 'left', fontSize: 'small' } },
        { key: 'favorites', sortable: true, label: 'Избр.', style: { minWidth: '50px', maxWidth: '50px', textAlign: 'center', fontSize: 'small' } },
        { key: 'itemsCount', sortable: true, label: 'Песен', style: { minWidth: '70px', maxWidth: '70px', textAlign: 'center', fontSize: 'small' } },
        { key: 'ownerEmail', sortable: true, label: 'Владелец (email)', style: { minWidth: '240px', maxWidth: '240px', textAlign: 'left', fontSize: 'small' } },
        { key: 'ownerName', sortable: true, label: 'Имя владельца', style: { minWidth: '160px', maxWidth: '160px', textAlign: 'left', fontSize: 'small' } },
        { key: 'ownerId', sortable: true, label: 'owner id', style: { minWidth: '70px', maxWidth: '70px', textAlign: 'center', fontSize: 'small' } },
      ]
    }
  },
  mounted() { this.reload() },
  methods: {
    reload() {
      const params = {};
      if (this.filterOwnerId) params.filterOwnerId = this.filterOwnerId;
      this.$store.dispatch('loadSitePlaylistsDigest', params);
    },
    onTargetChange() { this.currentPage = 1; this.reload() },
    openDetail(id) {
      this.$store.dispatch('clearSitePlaylistDetail');
      this.$store.dispatch('loadSitePlaylistDetail', id);
      this.isDetailVisible = true;
    },
    closeDetail() { this.isDetailVisible = false },
    onRowClicked(item) { this.openDetail(item.id) }
  }
}
</script>

<style scoped>
.spt-table { padding: 0; margin: 0; width: 100%; display: flex; flex-direction: column; align-items: center; font-family: Avenir, Helvetica, Arial, sans-serif; }
.spt-toolbar { display: flex; align-items: center; gap: 10px; margin-bottom: 8px; font-size: small; }
.spt-toolbar-item { font-size: small; }
.spt-btn { border: solid 1px black; border-radius: 6px; padding: 4px 10px; background-color: antiquewhite; cursor: pointer; }
.spt-btn:hover { background-color: lightpink; }
.spt-table-header, .spt-table-body { width: fit-content; }
.spt-table-body :deep(th) { position: relative; }
.spt-table-body :deep(th svg.bi) {
  position: absolute;
  right: 2px;
  top: 50%;
  transform: translateY(-50%);
  opacity: 0 !important;
  transition: opacity 0.15s ease;
  pointer-events: none;
}
.spt-table-body :deep(th:hover svg.bi) { opacity: 0.6 !important; }
.spt-table-footer { margin-top: 6px; font-size: small; color: gray; }
.fld-name { font-size: small; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.fld-name:hover { text-decoration: underline; cursor: pointer; }
</style>
