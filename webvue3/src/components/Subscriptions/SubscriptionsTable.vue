<template>
  <div class="subt-table">
    <SubscriptionsFilterModal v-if="isFilterVisible" @close="closeFilter" />

    <div class="subt-toolbar">
      <label class="subt-toolbar-item">
        БД:
        <select v-model="target" @change="onTargetChange">
          <option value="local">Локальная</option>
          <option value="remote">Сервер</option>
        </select>
      </label>
      <button class="subt-toolbar-item subt-btn" @click="isFilterVisible = true">Фильтр</button>
      <button class="subt-toolbar-item subt-btn" @click="reload">Обновить</button>
    </div>

    <div class="subt-table-header">
      <b-pagination
        v-model="currentPage"
        :total-rows="countRows"
        :per-page="perPage"
        :limit="30"
        size="sm"
        pills
      />
    </div>

    <div class="subt-table-body">
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
            :title="`user id: ${data.item.siteUserId}`"
            >{{ formatUser(data.item) }}</router-link
          >
        </template>
        <template #cell(scope)="data">
          <div style="text-align: center">
            {{ data.value === 'SONG' ? 'Песня' : data.value === 'SITE' ? 'Сайт' : data.value }}
          </div>
        </template>
        <template #cell(name)="data">
          <div class="fld-ellipsis" :title="formatTitle(data.item)">
            {{ formatTitle(data.item) }}
          </div>
        </template>
        <template #cell(finalPrice)="data">
          <div style="text-align: right">{{ formatMoney(data.value) }}</div>
        </template>
        <template #cell(discount)="data">
          <div
            style="text-align: right"
            :title="`Базовая: ${formatMoney(data.item.basePrice)}, Скидка: ${formatMoney(data.value)}`"
          >
            <span v-if="data.value > 0" style="color: darkgreen"
              >−{{ formatMoney(data.value) }}</span
            >
            <span v-else>—</span>
          </div>
        </template>
        <template #cell(status)="data">
          <div :style="{ color: statusColor(data.value), textAlign: 'center', fontWeight: 'bold' }">
            {{ statusLabel(data.value) }}
          </div>
        </template>
        <template #cell(autoRenew)="data">
          <div style="text-align: center">
            {{ data.value === true ? 'Да' : data.value === false ? 'Нет' : '—' }}
          </div>
        </template>
        <template #cell(createdAt)="data">
          <div class="fld-ellipsis" :title="data.value || ''">
            {{ formatTimestamp(data.value) }}
          </div>
        </template>
        <template #cell(paidAt)="data">
          <div class="fld-ellipsis">{{ formatTimestamp(data.value) }}</div>
        </template>
        <template #cell(orderId)="data">
          <div class="fld-ellipsis" :title="data.value || ''">
            <span v-if="data.value">{{ data.value.substring(0, 8) }}</span>
            <span v-else>—</span>
          </div>
        </template>
        <template #empty>
          <div style="text-align: center; padding: 10px">Подписок нет</div>
        </template>
      </b-table>
    </div>

    <div class="subt-table-footer">
      <span>Всего: {{ countRows }}</span>
    </div>
  </div>
</template>

<script>
import { BPagination, BSpinner, BTable } from 'bootstrap-vue-next'
import SubscriptionsFilterModal from './SubscriptionsFilterModal.vue'

/**
 * Таблица глобального списка подписок (`tbl_subscriptions`) в админ-SPA.
 *
 * Read-only просмотр с фильтрами (scope/status/userId/songId/createdFrom/createdTo),
 * target-aware (local/remote), пагинацией 25/стр, drill-down к `/siteusers?focus=ID`.
 * JOIN к tbl_site_users / tbl_songs / tbl_price_tariffs делается на бэкенде одним батчем
 * (см. `SubscriptionsController.kt`).
 *
 * Унифицированное поле `name` (отдаётся бэкендом): для `scope=SONG` — название песни,
 * для `scope=SITE` — название тарифа. Это позволяет клиентской сортировке работать
 * единообразно (один столбец — один ключ), а UI не делать условной логики.
 *
 * **Сортировка** (FR-026.1, паттерн из `SitePlaylistsTable` / `SiteUsersTable`):
 * - Клиентская через `v-model:sort-by` на `<b-table>` — мгновенно, без перезагрузки.
 * - `sortBy: []` в `data()` — пустой массив означает «использовать серверный порядок»
 *   (по умолчанию `created_at DESC` на бэкенде). При клике пользователя по заголовку —
 *   b-table сортирует текущую порцию на клиенте.
 *
 * **Структура таблицы** (см. CONTRIBUTING.md#vue-table-layout-fixed):
 * - `table-layout: fixed` + явная `width` на колонках.
 * - Без `display: flex` на `<td>` — только `text-align: center; vertical-align: middle`.
 *
 * **Pagination persistence** (см. AGENTS.md#персистентность-страницы-пагинации-в-webvue3):
 * - `currentPage` хранится в Vuex (`subscriptionsTableCurrentPage`) — переживает F5.
 * - `countRows` watcher сбрасывает `currentPage` только если страница вышла за пределы после
 *   обновления данных (паттерн из `SongsTable.vue`, ослабленный вариант «вернуться на ту же страницу»).
 *
 * @see AGENTS.md
 * @see specs/171-admin-subscriptions-history/spec.md (FR-001…FR-007, FR-026.1)
 */
export default {
  name: 'SubscriptionsTable',
  components: { SubscriptionsFilterModal, BPagination, BSpinner, BTable },
  data() {
    return {
      perPage: 25,
      currentPage: this.$store.getters.getSubscriptionsTableCurrentPage || 1,
      sortBy: [],
      isBusy: false,
      isFilterVisible: false,
    }
  },
  computed: {
    digestIsLoading() {
      return this.$store.getters.getSubscriptionsDigestIsLoading
    },
    digest() {
      return this.$store.getters.getSubscriptionsDigest
    },
    countRows() {
      return this.$store.getters.getSubscriptionsDigestTotalCount
    },
    target: {
      get() {
        return this.$store.getters.getSubscriptionsTarget
      },
      set(value) {
        this.$store.dispatch('setSubscriptionsTarget', value)
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
          key: 'userEmail',
          label: 'Пользователь',
          sortable: true,
          style: { minWidth: '240px', maxWidth: '240px', textAlign: 'left', fontSize: 'small' },
        },
        {
          key: 'scope',
          label: 'Тип',
          sortable: true,
          style: { minWidth: '70px', maxWidth: '70px', textAlign: 'center', fontSize: 'small' },
        },
        {
          key: 'name',
          label: 'Название',
          sortable: true,
          style: { minWidth: '420px', maxWidth: '420px', textAlign: 'left', fontSize: 'small' },
        },
        {
          key: 'finalPrice',
          label: 'Сумма',
          sortable: true,
          style: { minWidth: '90px', maxWidth: '90px', textAlign: 'right', fontSize: 'small' },
        },
        {
          key: 'discount',
          label: 'Скидка',
          sortable: true,
          style: { minWidth: '80px', maxWidth: '80px', textAlign: 'right', fontSize: 'small' },
        },
        {
          key: 'status',
          label: 'Статус',
          sortable: true,
          style: { minWidth: '100px', maxWidth: '100px', textAlign: 'center', fontSize: 'small' },
        },
        {
          key: 'autoRenew',
          label: 'Авто',
          sortable: true,
          style: { minWidth: '60px', maxWidth: '60px', textAlign: 'center', fontSize: 'small' },
        },
        {
          key: 'paidAt',
          label: 'Оплачена',
          sortable: true,
          style: { minWidth: '140px', maxWidth: '140px', textAlign: 'left', fontSize: 'small' },
        },
        {
          key: 'orderId',
          label: 'Order',
          sortable: true,
          style: { minWidth: '80px', maxWidth: '80px', textAlign: 'left', fontSize: 'small' },
        },
      ]
    },
  },
  watch: {
    digestIsLoading() {
      this.isBusy = this.digestIsLoading
    },
    countRows(newCount) {
      // Сбрасываем страницу только если она вышла за пределы после обновления данных
      // (паттерн из SongsTable.vue — ослабленная версия, чтобы при переключении target Local↔Remote
      // не сбрасывать позицию, если данные всё ещё влезают на текущую страницу).
      const totalPages = Math.max(1, Math.ceil(newCount / this.perPage))
      if (this.currentPage > totalPages) {
        this.currentPage = 1
      }
    },
    currentPage(newPage) {
      // Сохраняем страницу в store, чтобы она восстановилась после переключения на другой компонент.
      this.$store.commit('setSubscriptionsTableCurrentPage', newPage)
    },
  },
  mounted() {
    this.reload()
  },
  methods: {
    reload() {
      this.$store.dispatch('loadSubscriptionsDigest', {})
    },
    onTargetChange() {
      this.currentPage = 1
      this.reload()
    },
    closeFilter() {
      this.isFilterVisible = false
    },
    formatMoney(value) {
      if (value == null || value === '') return '—'
      return Number(value).toFixed(2) + ' ₽'
    },
    statusColor(status) {
      switch (status) {
        case 'PAID':
          return 'darkgreen'
        case 'PENDING':
          return 'darkorange'
        case 'CREATED':
          return 'gray'
        case 'FAILED':
          return 'darkred'
        case 'REFUNDED':
          return 'darkblue'
        case 'CANCELED':
          return 'dimgray'
        default:
          return 'black'
      }
    },
    statusLabel(status) {
      switch (status) {
        case 'PAID':
          return 'Оплачена'
        case 'PENDING':
          return 'В ожидании'
        case 'CREATED':
          return 'Создана'
        case 'FAILED':
          return 'Ошибка'
        case 'REFUNDED':
          return 'Возврат'
        case 'CANCELED':
          return 'Отменена'
        default:
          return status || '—'
      }
    },
    formatTimestamp(s) {
      if (!s) return '—'
      // Backend отдаёт java.sql.Timestamp.toString() — "yyyy-MM-dd HH:mm:ss[.f...]" в TZ JVM (МСК).
      // Парсим как naive UTC+0 (для админ-таблицы точность до минут достаточна).
      const m = s.match(/^(\d{4})-(\d{2})-(\d{2}) (\d{2}):(\d{2})(?::(\d{2}))?/)
      if (!m) return s
      const [, y, mo, d, h, mi] = m
      return `${d}.${mo}.${y} ${h}:${mi}`
    },
    formatUser(item) {
      // «Имя (email)» — если displayName пуст, только email.
      const name = (item.userDisplayName || '').trim()
      if (name && name !== item.userEmail) {
        return `${name} (${item.userEmail})`
      }
      return item.userEmail || ''
    },
    formatTitle(item) {
      // Унифицированное название: для scope=SONG — «Автор (Альбом, год) - Название»,
      // для scope=SITE — tariffName.
      if (item.scope === 'SONG') {
        if (!item.songName) return `#${item.idSong || ''} (удалена)`
        if (!item.songAuthor) return item.songName
        const albumMeta = []
        if (item.songAlbum) albumMeta.push(item.songAlbum)
        if (item.songYear && item.songYear > 0) albumMeta.push(String(item.songYear))
        const meta = albumMeta.length > 0 ? ` (${albumMeta.join(', ')})` : ''
        return `${item.songAuthor}${meta} - ${item.songName}`
      }
      return item.tariffName || '—'
    },
  },
}
</script>

<style scoped>
.subt-table {
  padding: 0;
  margin: 0;
  width: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  font-family: Avenir, Helvetica, Arial, sans-serif;
}
.subt-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
  font-size: small;
}
.subt-toolbar-item {
  font-size: small;
}
.subt-btn {
  border: solid 1px black;
  border-radius: 6px;
  padding: 4px 10px;
  background-color: antiquewhite;
  cursor: pointer;
}
.subt-btn:hover {
  background-color: lightpink;
}
.subt-table-header,
.subt-table-body {
  width: fit-content;
}
.subt-table-body :deep(th) {
  position: relative;
}
.subt-table-body :deep(th:nth-child(3)),
.subt-table-body :deep(td:nth-child(3)),
.subt-table-body :deep(th:nth-child(5)),
.subt-table-body :deep(td:nth-child(5)) {
  text-align: left !important;
}
.subt-table-body :deep(th svg.bi) {
  position: absolute;
  right: 2px;
  top: 50%;
  transform: translateY(-50%);
  opacity: 0 !important;
  transition: opacity 0.15s ease;
  pointer-events: none;
}
.subt-table-body :deep(th:hover svg.bi) {
  opacity: 0.6 !important;
}
.subt-table-footer {
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
