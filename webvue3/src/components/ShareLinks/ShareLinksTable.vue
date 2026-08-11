<template>
  <div class="slt-table">
    <ShareLinksFilterModal v-if="isFilterVisible" @close="closeFilter" />

    <div class="slt-toolbar">
      <label class="slt-toolbar-item">
        БД:
        <select v-model="target" @change="onTargetChange">
          <option value="local">Локальная</option>
          <option value="remote">Сервер</option>
        </select>
      </label>
      <button class="slt-toolbar-item slt-btn" @click="isFilterVisible = true">Фильтр</button>
      <button class="slt-toolbar-item slt-btn" @click="reload">Обновить</button>
    </div>

    <div class="slt-table-header">
      <b-pagination
        v-model="currentPage"
        :total-rows="countRows"
        :per-page="perPage"
        :limit="30"
        size="sm"
        pills
      />
    </div>

    <div class="slt-table-body">
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
        <template #cell(ownerEmail)="data">
          <router-link
            :to="`/siteusers?focus=${data.item.ownerSiteUserId}`"
            class="fld-link"
            :title="`user id: ${data.item.ownerSiteUserId}`"
            >{{ formatOwner(data.item) }}</router-link
          >
        </template>
        <template #cell(songName)="data">
          <router-link :to="`/songs?focus=${data.item.songId}`" class="fld-link">{{
            data.value || 'песня удалена'
          }}</router-link>
        </template>
        <template #cell(status)="data">
          <div :style="{ color: statusColor(data.item), textAlign: 'center', fontWeight: 'bold' }">
            {{ statusLabel(data.item) }}
          </div>
        </template>
        <template #cell(expiresAt)="data">
          <div :style="{ color: isExpired(data.value) ? 'darkred' : 'black' }" class="fld-ellipsis">
            {{ formatTimestamp(data.value) }}
          </div>
        </template>
        <template #cell(createdAt)="data">
          <div class="fld-ellipsis">{{ formatTimestamp(data.value) }}</div>
        </template>
        <template #cell(hasActiveSession)="data">
          <div style="text-align: center">
            <span v-if="data.value" style="color: darkgreen; font-weight: bold">● сессия</span>
            <span v-else style="color: lightgray">○</span>
          </div>
        </template>
        <template #cell(secret)="data">
          <div class="fld-ellipsis" :title="data.value || ''">
            {{ data.value ? data.value.substring(0, 8) + '…' : '—' }}
          </div>
        </template>
        <template #cell(revoke)="data">
          <div style="text-align: center">
            <button
              v-if="data.item.active"
              class="slt-btn-revoke"
              title="Отозвать ссылку"
              @click.left="confirmRevoke(data.item)"
            >
              Отозвать
            </button>
            <span v-else style="color: lightgray">—</span>
          </div>
        </template>
        <template #empty>
          <div style="text-align: center; padding: 10px">Временных ссылок нет</div>
        </template>
      </b-table>
    </div>

    <div class="slt-table-footer">
      <span>Всего: {{ countRows }}</span>
    </div>

    <div v-if="isRevokeModalVisible" class="slt-revoke-backdrop" @click.self="closeRevoke">
      <div class="slt-revoke-modal">
        <div class="slt-revoke-modal-header">Отзыв share-ссылки</div>
        <div class="slt-revoke-modal-body">
          <p>
            Отозвать ссылку <strong>#{{ revokeTarget?.id }}</strong
            >?
          </p>
          <p>
            Владелец: <strong>{{ revokeTarget?.ownerEmail }}</strong>
          </p>
          <p>
            Песня: <strong>{{ revokeTarget?.songName || '—' }}</strong>
          </p>
          <p>
            target: <strong>{{ target }}</strong>
          </p>
          <p style="color: darkred; font-weight: bold">
            ⚠ Активные playback-сессии будут завершены. Действие необратимо.
          </p>
        </div>
        <div class="slt-revoke-modal-footer">
          <button class="slt-btn-revoke-confirm" @click="doRevoke">Отозвать</button>
          <button class="slt-btn-revoke-cancel" @click="closeRevoke">Отмена</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { BPagination, BSpinner, BTable } from 'bootstrap-vue-next'
import ShareLinksFilterModal from './ShareLinksFilterModal.vue'

/**
 * Таблица глобального списка временных share-ссылок (`tbl_song_share_links`) в админ-SPA.
 *
 * Read-only просмотр с фильтрами (activeOnly/ownerId/songId/createdFrom/createdTo),
 * target-aware (local/remote), пагинацией 25/стр, drill-down к `/siteusers?focus=ID` и
 * `/songs?focus=ID`. Действие «Отозвать» диспатчит существующий `revokeSiteUserShareLink`
 * (см. `webvue3/src/components/SiteUsers/shareLinkStore.js:64`) — НЕ дублирует логику.
 *
 * После успешного revoke — обновляем строку in-place через `updateShareLinksDigestItem`,
 * без полной перезагрузки таблицы (FR-022, UX быстрее).
 *
 * **Структура таблицы** (см. CONTRIBUTING.md#vue-table-layout-fixed):
 * - `table-layout: fixed` + явная `width` на колонках.
 * - Без `display: flex` на `<td>` — только `text-align: center; vertical-align: middle`.
 *
 * **Pagination persistence** (см. AGENTS.md#персистентность-страницы-пагинации-в-webvue3):
 * - `currentPage` хранится в Vuex (`shareLinksTableCurrentPage`) — переживает F5.
 *
 * @see AGENTS.md
 * @see docs/features/guest-share-link.md
 * @see specs/171-admin-subscriptions-history/spec.md (FR-015…FR-022)
 */
export default {
  name: 'ShareLinksTable',
  components: { ShareLinksFilterModal, BPagination, BSpinner, BTable },
  data() {
    return {
      perPage: 25,
      currentPage: this.$store.getters.getShareLinksTableCurrentPage || 1,
      sortBy: [],
      isBusy: false,
      isFilterVisible: false,
      isRevokeModalVisible: false,
      revokeTarget: null,
    }
  },
  computed: {
    digestIsLoading() {
      return this.$store.getters.getShareLinksDigestIsLoading
    },
    digest() {
      return this.$store.getters.getShareLinksDigest
    },
    countRows() {
      return this.$store.getters.getShareLinksDigestTotalCount
    },
    target: {
      get() {
        return this.$store.getters.getShareLinksTarget
      },
      set(value) {
        this.$store.dispatch('setShareLinksTarget', value)
      },
    },
    fields() {
      return [
        {
          key: 'id',
          label: 'ID',
          sortable: true,
          style: { minWidth: '60px', maxWidth: '60px', textAlign: 'center', fontSize: 'small' },
        },
        {
          key: 'createdAt',
          label: 'Создана',
          sortable: true,
          style: { minWidth: '140px', maxWidth: '140px', textAlign: 'left', fontSize: 'small' },
        },
        {
          key: 'ownerEmail',
          label: 'Владелец',
          sortable: true,
          style: { minWidth: '240px', maxWidth: '240px', textAlign: 'left', fontSize: 'small' },
        },
        {
          key: 'songName',
          label: 'Песня',
          sortable: true,
          style: { minWidth: '220px', maxWidth: '220px', textAlign: 'left', fontSize: 'small' },
        },
        {
          key: 'expiresAt',
          label: 'Истекает',
          sortable: true,
          style: { minWidth: '140px', maxWidth: '140px', textAlign: 'left', fontSize: 'small' },
        },
        {
          key: 'status',
          label: 'Статус',
          sortable: true,
          style: { minWidth: '110px', maxWidth: '110px', textAlign: 'center', fontSize: 'small' },
        },
        {
          key: 'revokeReason',
          label: 'Причина',
          sortable: true,
          style: { minWidth: '110px', maxWidth: '110px', textAlign: 'left', fontSize: 'small' },
        },
        {
          key: 'hasActiveSession',
          label: 'Сессия',
          sortable: true,
          style: { minWidth: '70px', maxWidth: '70px', textAlign: 'center', fontSize: 'small' },
        },
        {
          key: 'secret',
          label: 'Secret',
          sortable: true,
          style: { minWidth: '90px', maxWidth: '90px', textAlign: 'left', fontSize: 'small' },
        },
        {
          key: 'revoke',
          label: 'Действие',
          style: { minWidth: '100px', maxWidth: '100px', textAlign: 'center', fontSize: 'small' },
        },
      ]
    },
  },
  watch: {
    digestIsLoading() {
      this.isBusy = this.digestIsLoading
    },
    countRows(newCount) {
      const totalPages = Math.max(1, Math.ceil(newCount / this.perPage))
      if (this.currentPage > totalPages) {
        this.currentPage = 1
      }
    },
    currentPage(newPage) {
      this.$store.commit('setShareLinksTableCurrentPage', newPage)
    },
  },
  mounted() {
    this.reload()
  },
  methods: {
    reload() {
      this.$store.dispatch('loadShareLinksDigest', {})
    },
    onTargetChange() {
      this.currentPage = 1
      this.reload()
    },
    closeFilter() {
      this.isFilterVisible = false
    },
    confirmRevoke(item) {
      this.revokeTarget = item
      this.isRevokeModalVisible = true
    },
    closeRevoke() {
      this.isRevokeModalVisible = false
      this.revokeTarget = null
    },
    doRevoke() {
      const target = this.revokeTarget
      if (!target) return
      this.$store
        .dispatch('revokeShareLink', { shareLinkId: target.id, reason: 'admin' })
        .then(() => {
          // In-place обновление строки (FR-022): заменяем объект в массиве, без F5.
          const updated = Object.assign({}, target, {
            active: false,
            revokedAt: new Date().toISOString(),
            revokeReason: 'admin',
            hasActiveSession: false,
          })
          this.$store.commit('updateShareLinksDigestItem', updated)
          this.closeRevoke()
        })
        .catch((error) => {
          console.log(error)
          this.closeRevoke()
        })
    },
    isExpired(expiresAt) {
      if (!expiresAt) return false
      // expiresAt от бэкенда — Timestamp.toString(), парсим как naive UTC+0 для админки.
      const ts = Date.parse(expiresAt.replace(' ', 'T') + 'Z')
      return !Number.isNaN(ts) && ts < Date.now()
    },
    statusColor(item) {
      if (!item.active) return 'dimgray'
      if (this.isExpired(item.expiresAt)) return 'darkorange'
      return 'darkgreen'
    },
    statusLabel(item) {
      if (!item.active) {
        if (item.revokeReason === 'admin') return 'Отозвана админом'
        if (item.revokeReason === 'expired') return 'Истекла'
        if (item.revokeReason === 'premium_lost') return 'Потеря премиума'
        if (item.revokeReason === 'song_unavailable') return 'Песня недоступна'
        if (item.revokeReason === 'manual') return 'Отозвана владельцем'
        if (item.revokeReason === 'replaced') return 'Заменена'
        return 'Отозвана'
      }
      if (this.isExpired(item.expiresAt)) return 'Истекла (sweep)'
      return 'Активна'
    },
    formatTimestamp(s) {
      if (!s) return '—'
      const m = s.match(/^(\d{4})-(\d{2})-(\d{2}) (\d{2}):(\d{2})(?::(\d{2}))?/)
      if (!m) return s
      const [, y, mo, d, h, mi] = m
      return `${d}.${mo}.${y} ${h}:${mi}`
    },
    formatOwner(item) {
      // «Имя (email)» — стандартный шаблон для админ-таблиц (паттерн из Subscriptions/ShareLinks).
      // Если displayName пуст — только email.
      const name = (item.ownerDisplayName || '').trim()
      if (name && name !== item.ownerEmail) {
        return `${name} (${item.ownerEmail})`
      }
      return item.ownerEmail || ''
    },
  },
}
</script>

<style scoped>
.slt-table {
  padding: 0;
  margin: 0;
  width: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  font-family: Avenir, Helvetica, Arial, sans-serif;
}
.slt-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
  font-size: small;
}
.slt-toolbar-item {
  font-size: small;
}
.slt-btn {
  border: solid 1px black;
  border-radius: 6px;
  padding: 4px 10px;
  background-color: antiquewhite;
  cursor: pointer;
}
.slt-btn:hover {
  background-color: lightpink;
}
.slt-btn-revoke {
  border: solid 1px darkred;
  border-radius: 4px;
  padding: 2px 8px;
  background-color: lightcoral;
  color: darkred;
  font-size: small;
  cursor: pointer;
}
.slt-btn-revoke:hover {
  background-color: darkred;
  color: white;
}
.slt-table-header,
.slt-table-body {
  width: fit-content;
}
.slt-table-body :deep(th) {
  position: relative;
}
.slt-table-body :deep(th svg.bi) {
  position: absolute;
  right: 2px;
  top: 50%;
  transform: translateY(-50%);
  opacity: 0 !important;
  transition: opacity 0.15s ease;
  pointer-events: none;
}
.slt-table-body :deep(th:hover svg.bi) {
  opacity: 0.6 !important;
}
.slt-table-footer {
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
.slt-revoke-backdrop {
  position: fixed;
  top: 0;
  bottom: 0;
  left: 0;
  right: 0;
  background-color: rgba(0, 0, 0, 0.5);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 1060;
}
.slt-revoke-modal {
  background: #ffffff;
  box-shadow: 2px 2px 20px 1px;
  width: 420px;
  max-width: calc(100vw - 20px);
  display: flex;
  flex-direction: column;
}
.slt-revoke-modal-header {
  background-color: darkred;
  padding: 10px;
  color: white;
  font-size: larger;
  font-weight: 300;
}
.slt-revoke-modal-body {
  padding: 15px;
  background-color: white;
  font-size: small;
}
.slt-revoke-modal-footer {
  padding: 10px;
  background-color: darkslategray;
  display: flex;
  justify-content: center;
  gap: 10px;
}
.slt-btn-revoke-confirm {
  border: 1px solid white;
  border-radius: 8px;
  cursor: pointer;
  font-weight: bold;
  color: white;
  background-color: darkred;
  padding: 6px 16px;
}
.slt-btn-revoke-cancel {
  border: 1px solid white;
  border-radius: 8px;
  cursor: pointer;
  font-weight: bold;
  color: white;
  background: transparent;
  padding: 6px 16px;
}
</style>
