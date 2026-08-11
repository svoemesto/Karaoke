<template>
  <div class="lht-table">
    <ListeningHistoryFilterModal v-if="isFilterVisible" @close="closeFilter" />

    <div class="lht-toolbar">
      <label class="lht-toolbar-item">
        БД:
        <select v-model="target" @change="onTargetChange">
          <option value="local">Локальная</option>
          <option value="remote">Сервер</option>
        </select>
      </label>
      <button class="lht-toolbar-item lht-btn" @click="isFilterVisible = true">Фильтр</button>
      <button class="lht-toolbar-item lht-btn" @click="reload">Обновить</button>
    </div>

    <div class="lht-table-header">
      <b-pagination
        v-model="currentPage"
        :total-rows="countRows"
        :per-page="perPage"
        :limit="30"
        size="sm"
        pills
      />
    </div>

    <div class="lht-table-body">
      <b-table
        v-model:sort-by="sortBy"
        :items="digest"
        :busy="isBusy"
        :fields="fields"
        :per-page="perPage"
        :current-page="currentPage"
        small
        bordered
        hover
      >
        <template #table-busy>
          <div class="text-center text-danger my-2">
            <b-spinner class="align-middle" />
            <strong>Loading...</strong>
          </div>
        </template>
        <template #table-colgroup="scope">
          <col v-for="field in scope.fields" :key="field.key" :style="field.style" />
        </template>
        <template #cell(userEmail)="data">
          <router-link
            :to="`/siteusers?focus=${data.item.siteUserId}`"
            class="fld-link"
            :title="data.item.userDisplayName || ''"
            >{{ data.value }}</router-link
          >
        </template>
        <template #cell(songName)="data">
          <router-link :to="`/songs?focus=${data.item.songId}`" class="fld-link">{{
            data.value || 'песня удалена'
          }}</router-link>
        </template>
        <template #cell(songAuthor)="data">
          <div class="fld-ellipsis">{{ data.value || '—' }}</div>
        </template>
        <template #cell(songAlbum)="data">
          <div class="fld-ellipsis">{{ data.value || '—' }}</div>
        </template>
        <template #cell(playCount)="data">
          <div style="text-align: center">{{ data.value }}</div>
        </template>
        <template #cell(lastPlayedAt)="data">
          <div class="fld-ellipsis" :title="data.value || ''">
            {{ formatTimestamp(data.value) }}
          </div>
        </template>
        <template #empty>
          <div style="text-align: center; padding: 10px">Истории прослушиваний нет</div>
        </template>
      </b-table>
    </div>

    <div class="lht-table-footer">
      <span v-if="countRows > perPage"
        >Показано {{ Math.min(countRows, currentPage * perPage) }} из {{ countRows }}</span
      >
      <span v-else>Всего: {{ countRows }}</span>
    </div>
  </div>
</template>

<script>
import { BPagination, BSpinner, BTable } from 'bootstrap-vue-next'
import ListeningHistoryFilterModal from './ListeningHistoryFilterModal.vue'

/**
 * Таблица глобального списка истории прослушиваний (`tbl_listening_history`) в админ-SPA.
 *
 * Read-only просмотр с фильтрами (userId/songId/lastPlayedFrom/lastPlayedTo), target-aware
 * (local/remote), пагинацией 500/стр, drill-down к `/siteusers?focus=ID` и `/songs?focus=ID`.
 *
 * ОБЯЗАТЕЛЬНО SKIP-фильтр на чтении — наследуется из публичного `ListeningHistory.getForUser`
 * (см. спек `ListeningHistoryController.kt`).
 *
 * **Структура таблицы** (см. CONTRIBUTING.md#vue-table-layout-fixed):
 * - `table-layout: fixed` + явная `width` на колонках.
 * - Без `display: flex` на `<td>` — только `text-align: center; vertical-align: middle`.
 *
 * **Pagination persistence** (см. AGENTS.md#персистентность-страницы-пагинации-в-webvue3):
 * - `currentPage` хранится в Vuex (`listeningHistoryTableCurrentPage`) — переживает F5.
 *
 * @see AGENTS.md
 * @see specs/171-admin-subscriptions-history/spec.md (FR-008…FR-014)
 */
export default {
  name: 'ListeningHistoryTable',
  components: { ListeningHistoryFilterModal, BPagination, BSpinner, BTable },
  data() {
    return {
      perPage: 500,
      currentPage: this.$store.getters.getListeningHistoryTableCurrentPage || 1,
      sortBy: [],
      isBusy: false,
      isFilterVisible: false,
    }
  },
  computed: {
    digestIsLoading() {
      return this.$store.getters.getListeningHistoryDigestIsLoading
    },
    digest() {
      return this.$store.getters.getListeningHistoryDigest
    },
    countRows() {
      return this.$store.getters.getListeningHistoryDigestTotalCount
    },
    target: {
      get() {
        return this.$store.getters.getListeningHistoryTarget
      },
      set(value) {
        this.$store.dispatch('setListeningHistoryTarget', value)
      },
    },
    fields() {
      return [
        {
          key: 'lastPlayedAt',
          label: 'Когда',
          sortable: true,
          style: { minWidth: '140px', maxWidth: '140px', textAlign: 'left', fontSize: 'small' },
        },
        {
          key: 'userEmail',
          label: 'Email',
          sortable: true,
          style: { minWidth: '200px', maxWidth: '200px', textAlign: 'left', fontSize: 'small' },
        },
        {
          key: 'songName',
          label: 'Песня',
          sortable: true,
          style: { minWidth: '220px', maxWidth: '220px', textAlign: 'left', fontSize: 'small' },
        },
        {
          key: 'songAuthor',
          label: 'Исполнитель',
          sortable: true,
          style: { minWidth: '160px', maxWidth: '160px', textAlign: 'left', fontSize: 'small' },
        },
        {
          key: 'songAlbum',
          label: 'Альбом',
          sortable: true,
          style: { minWidth: '160px', maxWidth: '160px', textAlign: 'left', fontSize: 'small' },
        },
        {
          key: 'playCount',
          label: 'Счётчик',
          sortable: true,
          style: { minWidth: '80px', maxWidth: '80px', textAlign: 'center', fontSize: 'small' },
        },
      ]
    },
  },
  watch: {
    digestIsLoading() {
      this.isBusy = this.digestIsLoading
    },
    currentPage(newPage) {
      // Сохраняем страницу в store, чтобы она восстановилась после переключения на другой компонент.
      this.$store.commit('setListeningHistoryTableCurrentPage', newPage)
    },
  },
  mounted() {
    this.reload()
  },
  methods: {
    reload() {
      this.$store.dispatch('loadListeningHistoryDigest', {})
    },
    onTargetChange() {
      this.currentPage = 1
      this.reload()
    },
    closeFilter() {
      this.isFilterVisible = false
    },
    formatTimestamp(s) {
      if (!s) return '—'
      const m = s.match(/^(\d{4})-(\d{2})-(\d{2}) (\d{2}):(\d{2})(?::(\d{2}))?/)
      if (!m) return s
      const [, y, mo, d, h, mi] = m
      return `${d}.${mo}.${y} ${h}:${mi}`
    },
  },
}
</script>

<style scoped>
.lht-table {
  padding: 0;
  margin: 0;
  width: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  font-family: Avenir, Helvetica, Arial, sans-serif;
}
.lht-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
  font-size: small;
}
.lht-toolbar-item {
  font-size: small;
}
.lht-btn {
  border: solid 1px black;
  border-radius: 6px;
  padding: 4px 10px;
  background-color: antiquewhite;
  cursor: pointer;
}
.lht-btn:hover {
  background-color: lightpink;
}
.lht-table-header,
.lht-table-body {
  width: fit-content;
}
.lht-table-body :deep(th) {
  position: relative;
}
.lht-table-body :deep(th svg.bi) {
  position: absolute;
  right: 2px;
  top: 50%;
  transform: translateY(-50%);
  opacity: 0 !important;
  transition: opacity 0.15s ease;
  pointer-events: none;
}
.lht-table-body :deep(th:hover svg.bi) {
  opacity: 0.6 !important;
}
.lht-table-footer {
  margin-top: 6px;
  font-size: small;
  color: gray;
}
.fld-link {
  color: #0645ad;
  cursor: pointer;
  text-decoration: none;
}
.fld-link:hover {
  text-decoration: underline;
}
.fld-ellipsis {
  font-size: small;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>
