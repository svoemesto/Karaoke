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
      <b-form-input
        :id="`rows-per-page-listening_history`"
        type="number"
        min="1"
        max="1000"
        size="sm"
        style="width: 65px"
        :model-value="perPage"
        :disabled="isSavingRowsPerPage"
        @change="onPerPageChange($event)"
      />
      <b-spinner v-if="isSavingRowsPerPage" small />
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
        <template #cell(songTitle)="data">
          <router-link :to="`/songs?focus=${data.item.songId}`" class="fld-link">{{
            formatSongTitle(data.item)
          }}</router-link>
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
        >Показано {{ itemsShownOnCurrentPage }} из {{ countRows }}</span
      >
      <span v-else>Всего: {{ countRows }}</span>
    </div>
  </div>
</template>

<script>
import { BPagination, BSpinner, BTable, BFormInput } from 'bootstrap-vue-next'
import ListeningHistoryFilterModal from './ListeningHistoryFilterModal.vue'

/**
 * Таблица глобального списка истории прослушиваний (`tbl_listening_history`) в админ-SPA.
 *
 * Read-only просмотр с фильтрами (userId/songId/lastPlayedFrom/lastPlayedTo),
 * target-aware (local/remote), пагинацией 500/стр, drill-down к `/siteusers?focus=ID` и `/songs?focus=ID`.
 *
 * ОБЯЗАТЕЛЬНО SKIP-фильтр на чтении — наследуется из публичного `ListeningHistory.getForUser`
 * (см. спек `ListeningHistoryController.kt`).
 *
 * **Колонки** (паттерн из `SiteUsers` / `Subscriptions` / `ShareLinks`):
 * - «Пользователь» — формат «Имя (email)» (если displayName пуст — только email), drill-down к `/siteusers`.
 * - «Песня» — формат «Название — Исполнитель (Альбом, год)» (drill-down к `/songs`). Поля
 *   `Исполнитель` и `Альбом» объединены в одну колонку — стандартный шаблон для админ-таблиц.
 *
 * **Сортировка** (паттерн из `SitePlaylistsTable` / `SiteUsersTable`):
 * - Клиентская через `v-model:sort-by` на `<b-table>` — мгновенно, без перезагрузки.
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
  components: { ListeningHistoryFilterModal, BPagination, BSpinner, BTable, BFormInput },
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
    // specs/358-rows-per-page: состояние saving для индикатора loading.
    isSavingRowsPerPage() {
      return this.$store.getters.isSavingRowsPerPage('listening_history')
    },
    digestIsLoading() {
      return this.$store.getters.getListeningHistoryDigestIsLoading
    },
    digest() {
      return this.$store.getters.getListeningHistoryDigest
    },
    countRows() {
      return this.$store.getters.getListeningHistoryDigestTotalCount
    },
    itemsShownOnCurrentPage() {
      // Размер текущей серверной страницы: для последней страницы меньше perPage, для остальных = perPage.
      // Формула: items between (currentPage-1)*perPage и min(countRows, currentPage*perPage), exclusive end.
      // Упрощённо: min(perPage, countRows - (currentPage-1)*perPage), но если currentPage уже за пределами
      // существующих страниц — возвращаем 0.
      if (this.countRows === 0) return 0
      const from = (this.currentPage - 1) * this.perPage
      if (from >= this.countRows) return 0
      return Math.min(this.perPage, this.countRows - from)
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
          label: 'Пользователь',
          sortable: true,
          style: { minWidth: '240px', maxWidth: '240px', textAlign: 'left', fontSize: 'small' },
        },
        {
          key: 'songTitle',
          label: 'Песня',
          sortable: true,
          style: { minWidth: '420px', maxWidth: '420px', textAlign: 'left', fontSize: 'small' },
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
    currentPage(newPage, oldPage) {
      // Сохраняем страницу в store, чтобы она восстановилась после переключения на другой компонент.
      this.$store.commit('setListeningHistoryTableCurrentPage', newPage)
      // Триггер загрузки данных при смене страницы пагинатора. Защита `newPage !== oldPage`
      // отсекает первый вызов watcher после mount, когда currentPage уже равно значению
      // из Vuex — там первичную загрузку делает mounted() { this.reload() }.
      if (newPage !== oldPage) {
        this.$store.dispatch('loadListeningHistoryDigest', {
          page: newPage,
          pageSize: this.perPage,
        })
      }
    },
  },
  async mounted() {
    // specs/358-rows-per-page: загрузить настройки таблиц (один раз при старте SPA,
    // защищён флагом `loaded` в store `tableSettings`). Важно: ждём завершения
    // ДО первой загрузки данных, чтобы pageSize корректно передавался в backend.
    await this.$store.dispatch('loadTableSettings')
    this.perPage = this.$store.getters.getRowsPerPage('listening_history')
    this.reload()
  },
  methods: {
    /**
     * specs/358-rows-per-page: обработчик изменения поля «Строк на странице».
     * Парсит значение, валидирует диапазон, отправляет в backend, обновляет UI
     * только после успешного ответа (без оптимистичного обновления — см.
     * Clarifications Q3 spec.md).
     *
     * @param {string|number} newValue
     */
    async onPerPageChange(e) {
      // Bootstrap-vue-next `<b-form-input>` в нативном режиме передаёт в @change Event,
      // а не значение. Извлекаем value из target.
      const rawValue = e && e.target ? e.target.value : e
      const parsed = parseInt(rawValue, 10)
      if (isNaN(parsed) || parsed < 1 || parsed > 1000) {
        // eslint-disable-next-line no-console
        console.warn('[ListeningHistory.onPerPageChange] invalid value', rawValue)
        return
      }
      if (parsed === this.perPage) return
      this.currentPage = 1 // ADR-0004: page reset
      if (this.$store.getters.getListeningHistoryTableCurrentPage !== undefined) {
        this.$store.commit('setListeningHistoryTableCurrentPage', 1)
      }
      const ok = await this.$store.dispatch('setRowsPerPage', {
        tableKey: 'listening_history',
        value: parsed,
      })
      if (ok) {
        this.perPage = this.$store.getters.getRowsPerPage('listening_history')
        // specs/358-rows-per-page: backend `/api/listeninghistory/digest` принимает
        // `pageSize` (= perPage) и `page`. Передаём pageSize.
        await this.$store.dispatch('loadListeningHistoryDigest', {
          page: this.currentPage,
          pageSize: this.perPage,
        })
      }
    },
    reload() {
      this.$store.dispatch('loadListeningHistoryDigest', {
        page: this.currentPage,
        pageSize: this.perPage,
      })
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
    formatUser(item) {
      // «Имя (email)» — если displayName пуст, только email.
      const name = (item.userDisplayName || '').trim()
      if (name && name !== item.userEmail) {
        return `${name} (${item.userEmail})`
      }
      return item.userEmail || ''
    },
    formatSongTitle(item) {
      // «Автор (Альбом, год) - Название» — единый шаблон для всех админ-таблиц с полем «Песня».
      // Если song удалена (songName='') — показываем «id удалён» (drill-down всё равно работает
      // по songId, может открыть карточку если она ещё доступна).
      if (!item.songName) return `#${item.songId} (удалена)`
      if (!item.songAuthor) return item.songName
      const albumMeta = []
      if (item.songYear && item.songYear > 0) albumMeta.push(String(item.songYear))
      if (item.songAlbum) albumMeta.push(item.songAlbum)
      const meta = albumMeta.length > 0 ? ` (${albumMeta.join(', ')})` : ''
      return `${item.songAuthor}${meta} - ${item.songName}`
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
  display: flex;
  align-items: center;
  gap: 10px;
}
.lht-table-body :deep(th) {
  position: relative;
}
.lht-table-body :deep(th:nth-child(2)),
.lht-table-body :deep(td:nth-child(2)),
.lht-table-body :deep(th:nth-child(3)),
.lht-table-body :deep(td:nth-child(3)) {
  text-align: left !important;
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

/* specs/358-rows-per-page: поле ввода «Строк на странице» — выровнено по центру
   с кнопками пагинации. Bootstrap .form-control имеет min-height через padding
   + font-size, что смещает baseline относительно .btn-sm кнопок. */
#rows-per-page-listening_history {
  padding: 0.25rem 0.5rem !important;
  line-height: 1.5 !important;
  height: 31px !important;
  font-size: 0.875rem !important;
  text-align: center;
  align-self: center;
}

/* specs/358-rows-per-page: убрать дефолтный margin-bottom у <b-pagination>
   внутри header-div, чтобы pagination был выровнен по центральной оси
   с input (без смещения baseline вниз). */
.lht-table-header .pagination,
.lht-table-header ul.pagination {
  margin-bottom: 0 !important;
}
</style>
