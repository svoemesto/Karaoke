<template>
  <div class="stats-view">

    <div class="stats-toolbar">
      <label class="tb-item">
        БД:
        <select class="form-select form-select-sm" v-model="target" @change="onTargetChange">
          <option value="local">Локальная</option>
          <option value="remote">Сервер</option>
        </select>
      </label>
      <label class="tb-item">
        Период:
        <select class="form-select form-select-sm" v-model.number="days" @change="onDaysChange">
          <option :value="7">7 дней</option>
          <option :value="30">30 дней</option>
          <option :value="90">90 дней</option>
          <option :value="365">Год</option>
        </select>
      </label>
      <button class="btn btn-sm btn-outline-secondary" @click="reloadAll">Обновить всё</button>
    </div>

    <BTabs v-model="activeTab" nav-class="stats-nav" pills card>
      <!-- 1. KPI -->
      <BTab title="KPI">
        <div v-if="summaryIsLoading && !summary" class="text-center py-3"><BSpinner small /></div>
        <KpiCards :summary="summary" />
      </BTab>

      <!-- 2. Монетизация -->
      <BTab title="Монетизация">
        <MonetizationPanel
            :summary="monetizationSummary"
            :is-loading="monetizationSummaryIsLoading"
            :top-songs="monetizationTopSongs"
            :top-songs-is-loading="monetizationTopSongsIsLoading" />
      </BTab>

      <!-- 3. Динамика -->
      <BTab title="Динамика">
        <TimeSeriesChart
            class="mb-3"
            :items="timeSeries"
            :mode="timeSeriesMode"
            :is-loading="timeSeriesIsLoading"
            @change-mode="onChangeTimeSeriesMode" />
      </BTab>

      <!-- 4. Разбивки -->
      <BTab title="Разбивки">
        <TypeChannelBreakdown
            :by-type="byType"
            :channels="channels"
            :is-loading="breakdownIsLoading"
            @select-type="onSelectDetailType" />
        <DetailBreakdown
            :items="detailed"
            :selected-type="selectedDetailType"
            :is-loading="breakdownIsLoading"
            @clear-type="selectedDetailType = ''" />
      </BTab>

      <!-- 5. География -->
      <BTab title="География">
        <GeoReferrers
            :countries="countries"
            :referrers="referrers"
            :is-loading="geoIsLoading" />
      </BTab>

      <!-- 6. Пользователи -->
      <BTab title="Пользователи">
        <TopUsersTable
            :users="topUsers"
            :total-count="topUsersTotalCount"
            :is-loading="topUsersIsLoading"
            :page="topUsersPage"
            :page-size="topUsersPageSize"
            @select-user="openUser"
            @page="onTopUsersPage"
            @page-size="onTopUsersPageSize" />
      </BTab>

      <!-- 7. Слушают — песни, дослушанные ≥75% в онлайн-плеере -->
      <BTab title="Слушают">
        <TopListenedSongsTable
            :items="topListened"
            :total-count="topListenedTotalCount"
            :is-loading="topListenedIsLoading"
            :page="topListenedPage"
            :page-size="topListenedPageSize"
            @select-song="openSong"
            @page="onTopListenedPage"
            @page-size="onTopListenedPageSize" />
      </BTab>

      <!-- 8. События -->
      <BTab title="События">
        <!-- Топ песен -->
        <div class="chart-card mb-3">
          <div class="chart-head">
            <h6 class="chart-title">Топ песен по событиям</h6>
            <select class="form-select form-select-sm w-auto" v-model.number="statsBySongPageSize" @change="onStatsBySongPageSizeChange">
              <option :value="20">20</option>
              <option :value="50">50</option>
              <option :value="100">100</option>
            </select>
          </div>
          <div v-if="statsBySongIsLoading" class="text-center py-3"><BSpinner small /></div>
          <template v-else>
            <div class="table-responsive">
              <table class="table table-sm table-hover table-bordered stats-table">
                <thead class="table-dark">
                  <tr>
                    <th>ID</th><th>Описание</th><th title="Все события песни">Всего</th>
                    <th title="Просмотры страницы песни">Сайт</th>
                    <th title="События онлайн-плеера всего">Плеер∑</th>
                    <th title="Плеер: показан на странице">Показ</th>
                    <th title="Плеер: открыт из списка (Закрома/Поиск)">Открыт</th>
                    <th title="Плеер: старт">Старт</th>
                    <th title="Плеер: пауза">Пауза</th>
                    <th title="Плеер: перемотка">Перем.</th>
                    <th title="Плеер: экспорт стема">Эксп.</th>
                    <th title="Плеер: прогресс прослушивания">Прогр.</th>
                    <th title="Плеер: завершение трека">Заверш.</th>
                    <th>VK кар.</th><th>VK тек.</th><th>Dzen кар.</th><th>Dzen тек.</th><th>TG кар.</th><th>TG тек.</th>
                    <th title="Клики по ссылке MAX">MAX</th><th title="Клики по ссылке Sponsr">Sponsr</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="row in statsBySong" :key="row.songId" class="clickable-row" @click="openSong(row)" title="Подробная статистика по песне">
                    <td>{{ row.songId }}</td>
                    <td class="text-start">{{ row.description }}</td>
                    <td class="fw-bold">{{ row.cntTotal }}</td>
                    <td>{{ row.cntSm }}</td><td>{{ row.cntPlayer }}</td>
                    <td>{{ row.cntPlayerShown }}</td><td>{{ row.cntPlayerOpened }}</td><td>{{ row.cntPlayerPlay }}</td><td>{{ row.cntPlayerPause }}</td>
                    <td>{{ row.cntPlayerSeek }}</td><td>{{ row.cntPlayerExport }}</td><td>{{ row.cntPlayerProgress }}</td><td>{{ row.cntPlayerEnded }}</td>
                    <td>{{ row.cntVkKaraoke }}</td><td>{{ row.cntVkLyrics }}</td>
                    <td>{{ row.cntDzenKaraoke }}</td><td>{{ row.cntDzenLyrics }}</td>
                    <td>{{ row.cntTgKaraoke }}</td><td>{{ row.cntTgLyrics }}</td>
                    <td>{{ row.cntMax }}</td><td>{{ row.cntSponsr }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
            <div class="d-flex align-items-center gap-2">
              <b-pagination v-model="statsBySongPageModel" :total-rows="statsBySongTotalCount" :per-page="statsBySongPageSize" :limit="40" size="sm" pills />
              <span class="text-muted small">Всего: {{ statsBySongTotalCount }}</span>
            </div>
          </template>
        </div>

        <!-- Лог событий -->
        <div class="chart-card">
          <div class="chart-head">
            <h6 class="chart-title">Последние события</h6>
            <div class="d-flex gap-2 align-items-center">
              <select class="form-select form-select-sm w-auto" v-model="webEventsType" @change="onWebEventsFilterChange">
                <option value="">Все типы</option>
                <option value="callRest">Просмотры</option>
                <option value="clickToLink">Клики</option>
                <option value="play">Видео</option>
                <option value="player">Плеер</option>
                <option value="engagement">Время</option>
                <option value="ui">UI</option>
              </select>
              <select class="form-select form-select-sm w-auto" v-model.number="webEventsPageSize" @change="onWebEventsPageSizeChange">
                <option :value="20">20</option>
                <option :value="50">50</option>
                <option :value="100">100</option>
              </select>
            </div>
          </div>
          <div v-if="webEventsIsLoading" class="text-center py-3"><BSpinner small /></div>
          <template v-else>
            <div class="table-responsive">
              <table class="table table-sm table-hover table-bordered stats-table events-table">
                <thead class="table-dark">
                  <tr>
                    <th>ID</th>
                    <th>Дата (last_update)</th>
                    <th title="event_type">Тип</th>
                    <th title="event_type (сырое)">event_type</th>
                    <th title="Человекочитаемое описание">Описание</th>
                    <th>rest_name</th>
                    <th>rest_parameters</th>
                    <th>link_type</th>
                    <th>link_name</th>
                    <th>song_id</th>
                    <th>song_version</th>
                    <th title="Внешний источник перехода (document.referrer)">Источник</th>
                    <th>client_ip</th>
                    <th title="Страна по client_ip">Страна</th>
                    <th>anon_id</th>
                    <th>site_user_id</th>
                    <th>user_agent</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="(evt, idx) in webEvents" :key="idx">
                    <td>{{ evt.id }}</td>
                    <td class="text-nowrap">{{ formatDate(evt.eventDate) }}</td>
                    <td class="text-nowrap">{{ evt.eventType }}</td>
                    <td class="text-nowrap">{{ evt.eventTypeRaw || '-' }}</td>
                    <td class="text-start">{{ evt.eventDescription || '-' }}</td>
                    <td>{{ evt.restName || '-' }}</td>
                    <td class="text-start cell-clip" :title="evt.restParameters">{{ evt.restParameters || '-' }}</td>
                    <td>{{ evt.linkType || '-' }}</td>
                    <td>{{ evt.linkName || '-' }}</td>
                    <td>{{ evt.songId || '-' }}</td>
                    <td>{{ evt.songVersion || '-' }}</td>
                    <td class="text-start cell-clip" :title="evt.referer">
                      <a v-if="isHttp(evt.referer)" :href="evt.referer" target="_blank" rel="noopener noreferrer">{{ evt.referer }}</a>
                      <span v-else>{{ evt.referer || '-' }}</span>
                    </td>
                    <td class="text-nowrap">{{ evt.clientIp || '-' }}</td>
                    <td class="text-nowrap">{{ evt.country || '-' }}</td>
                    <td class="text-nowrap cell-clip" :title="evt.anonId">{{ evt.anonId || '-' }}</td>
                    <td>{{ evt.siteUserId || 0 }}</td>
                    <td class="text-start cell-clip" :title="evt.userAgent">{{ evt.userAgent || '-' }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
            <div class="d-flex align-items-center gap-2">
              <b-pagination v-model="webEventsPageModel" :total-rows="webEventsTotalCount" :per-page="webEventsPageSize" :limit="40" size="sm" pills />
              <span class="text-muted small">Всего: {{ webEventsTotalCount }}</span>
            </div>
          </template>
        </div>
      </BTab>
    </BTabs>

    <UserEventsModal
        v-if="selectedUser"
        :user="selectedUser"
        :events="userEvents"
        :total-count="userEventsTotalCount"
        :is-loading="userEventsIsLoading"
        :cap="userEventsPageSize"
        @close="selectedUser = null" />

    <SongEventsModal
        v-if="selectedSong"
        :song="selectedSong"
        :events="songEvents"
        :total-count="songEventsTotalCount"
        :is-loading="songEventsIsLoading"
        :cap="songEventsPageSize"
        @close="selectedSong = null" />

  </div>
</template>

<script>
import { BSpinner, BPagination, BTabs, BTab } from 'bootstrap-vue-next'
import KpiCards from '../components/Stats/KpiCards.vue'
import MonetizationPanel from '../components/Stats/MonetizationPanel.vue'
import TimeSeriesChart from '../components/Stats/TimeSeriesChart.vue'
import TypeChannelBreakdown from '../components/Stats/TypeChannelBreakdown.vue'
import DetailBreakdown from '../components/Stats/DetailBreakdown.vue'
import GeoReferrers from '../components/Stats/GeoReferrers.vue'
import TopUsersTable from '../components/Stats/TopUsersTable.vue'
import TopListenedSongsTable from '../components/Stats/TopListenedSongsTable.vue'
import UserEventsModal from '../components/Stats/UserEventsModal.vue'
import SongEventsModal from '../components/Stats/SongEventsModal.vue'

export default {
  name: 'StatsView',
  components: { BSpinner, BPagination, BTabs, BTab, KpiCards, MonetizationPanel, TimeSeriesChart, TypeChannelBreakdown, DetailBreakdown, GeoReferrers, TopUsersTable, TopListenedSongsTable, UserEventsModal, SongEventsModal },
  data() {
    return {
      activeTab: 0,
      // Восстанавливаем последние страницы из store, чтобы при уходе с компонента и возврате таблицы
      // открывались на той же странице.
      statsBySongPage: this.$store.getters.getStatsBySongPage || 1,
      statsBySongPageSize: 50,
      webEventsPage: this.$store.getters.getWebEventsPage || 1,
      webEventsPageSize: 50,
      webEventsType: '',
      topUsersPage: 1,
      topUsersPageSize: 50,
      // Топ песен, которые реально слушают (≥75%). Страница восстанавливается из store
      // (см. AGENTS.md: «вернуться на ту же страницу»).
      topListenedPage: this.$store.getters.getTopListenedPage || 1,
      topListenedPageSize: 50,
      selectedDetailType: '',
      selectedUser: null,
      // Drill-down грузим одним запросом (для дерева нужны все события пользователя); 2000 — потолок.
      userEventsPageSize: 2000,
      selectedSong: null,
      // Drill-down по песне тоже одним запросом (дереву нужны все события песни).
      songEventsPageSize: 2000,
    }
  },
  computed: {
    summary() { return this.$store.getters.getStatsSummary },
    summaryIsLoading() { return this.$store.getters.getStatsSummaryIsLoading },
    timeSeries() { return this.$store.getters.getStatsTimeSeries },
    timeSeriesMode() { return this.$store.getters.getStatsTimeSeriesMode },
    timeSeriesIsLoading() { return this.$store.getters.getStatsTimeSeriesIsLoading },
    byType() { return this.$store.getters.getStatsByType },
    channels() { return this.$store.getters.getStatsChannels },
    detailed() { return this.$store.getters.getStatsDetailed },
    breakdownIsLoading() { return this.$store.getters.getStatsBreakdownIsLoading },
    countries() { return this.$store.getters.getStatsCountries },
    referrers() { return this.$store.getters.getStatsReferrers },
    geoIsLoading() { return this.$store.getters.getStatsGeoIsLoading },
    topUsers() { return this.$store.getters.getStatsTopUsers },
    topUsersTotalCount() { return this.$store.getters.getStatsTopUsersTotalCount },
    topUsersIsLoading() { return this.$store.getters.getStatsTopUsersIsLoading },
    userEvents() { return this.$store.getters.getStatsUserEvents },
    userEventsTotalCount() { return this.$store.getters.getStatsUserEventsTotalCount },
    userEventsIsLoading() { return this.$store.getters.getStatsUserEventsIsLoading },
    statsBySong() { return this.$store.getters.getStatsBySong },
    statsBySongIsLoading() { return this.$store.getters.getStatsBySongIsLoading },
    statsBySongTotalCount() { return this.$store.getters.getStatsBySongTotalCount },
    songEvents() { return this.$store.getters.getStatsSongEvents },
    songEventsTotalCount() { return this.$store.getters.getStatsSongEventsTotalCount },
    songEventsIsLoading() { return this.$store.getters.getStatsSongEventsIsLoading },
    webEvents() { return this.$store.getters.getWebEvents },
    webEventsIsLoading() { return this.$store.getters.getWebEventsIsLoading },
    webEventsTotalCount() { return this.$store.getters.getWebEventsTotalCount },
    monetizationSummary() { return this.$store.getters.getMonetizationSummary },
    monetizationSummaryIsLoading() { return this.$store.getters.getMonetizationSummaryIsLoading },
    monetizationTopSongs() { return this.$store.getters.getMonetizationTopSongs },
    monetizationTopSongsIsLoading() { return this.$store.getters.getMonetizationTopSongsIsLoading },
    topListened() { return this.$store.getters.getTopListened },
    topListenedTotalCount() { return this.$store.getters.getTopListenedTotalCount },
    topListenedIsLoading() { return this.$store.getters.getTopListenedIsLoading },
    target: {
      get() { return this.$store.getters.getStatsTarget },
      set(v) { this.$store.dispatch('setStatsTarget', v) }
    },
    days: {
      get() { return this.$store.getters.getStatsDays },
      set(v) { this.$store.dispatch('setStatsDays', v) }
    },
    statsBySongPageModel: {
      get() { return this.statsBySongPage },
      set(v) { this.statsBySongPage = v; this.reloadStatsBySong() }
    },
    webEventsPageModel: {
      get() { return this.webEventsPage },
      set(v) { this.webEventsPage = v; this.reloadWebEvents() }
    },
    topListenedPageModel: {
      get() { return this.topListenedPage },
      set(v) { this.topListenedPage = v; this.reloadTopListened() }
    },
  },
  methods: {
    reloadStatsBySong() {
      this.$store.dispatch('loadStatsBySong', { page: this.statsBySongPage, pageSize: this.statsBySongPageSize })
    },
    reloadWebEvents() {
      this.$store.dispatch('loadWebEvents', { page: this.webEventsPage, pageSize: this.webEventsPageSize, eventType: this.webEventsType })
    },
    reloadTopUsers() {
      this.$store.dispatch('loadStatsTopUsers', { page: this.topUsersPage, pageSize: this.topUsersPageSize })
    },
    reloadTopListened() {
      this.$store.dispatch('loadTopListened', { page: this.topListenedPage, pageSize: this.topListenedPageSize })
    },
    reloadAll() {
      this.$store.dispatch('loadStatsSummary')
      this.$store.dispatch('loadStatsTimeSeries')
      this.$store.dispatch('loadStatsBreakdown')
      this.$store.dispatch('loadStatsGeo')
      this.reloadTopUsers()
      this.reloadStatsBySong()
      this.reloadWebEvents()
      this.reloadTopListened()
      this.$store.dispatch('loadMonetizationSummary')
      this.$store.dispatch('loadMonetizationTopSongs')
    },
    isHttp(url) { return typeof url === 'string' && /^https?:\/\//i.test(url) },
    onTargetChange() {
      this.statsBySongPage = 1; this.webEventsPage = 1; this.topUsersPage = 1; this.topListenedPage = 1
      this.reloadAll()
    },
    onDaysChange() {
      this.$store.dispatch('loadStatsSummary')
      this.$store.dispatch('loadStatsTimeSeries')
      this.$store.dispatch('loadStatsBreakdown')
    },
    onChangeTimeSeriesMode(mode) {
      this.$store.dispatch('loadStatsTimeSeries', { mode })
    },
    onSelectDetailType(eventTypeRaw) {
      this.selectedDetailType = eventTypeRaw
    },
    onStatsBySongPageSizeChange() { this.statsBySongPage = 1; this.reloadStatsBySong() },
    onWebEventsPageSizeChange() { this.webEventsPage = 1; this.reloadWebEvents() },
    onWebEventsFilterChange() { this.webEventsPage = 1; this.reloadWebEvents() },
    onTopUsersPage(p) { this.topUsersPage = p; this.reloadTopUsers() },
    onTopUsersPageSize(sz) { this.topUsersPageSize = sz; this.topUsersPage = 1; this.reloadTopUsers() },
    onTopListenedPage(p) { this.topListenedPage = p; this.reloadTopListened() },
    onTopListenedPageSize(sz) { this.topListenedPageSize = sz; this.topListenedPage = 1; this.reloadTopListened() },
    openUser(u) {
      this.selectedUser = u
      this.loadUserEvents()
    },
    loadUserEvents() {
      const u = this.selectedUser
      this.$store.dispatch('loadStatsUserEvents', {
        siteUserId: u.siteUserId || 0,
        anonId: (u.siteUserId > 0 ? '' : (u.anonId || '')),
        page: 1,
        pageSize: this.userEventsPageSize,
      })
    },
    openSong(row) {
      this.selectedSong = row
      this.loadSongEvents()
    },
    loadSongEvents() {
      const s = this.selectedSong
      this.$store.dispatch('loadStatsSongEvents', {
        songId: s.songId,
        page: 1,
        pageSize: this.songEventsPageSize,
      })
    },
    formatDate(ts) {
      if (!ts) return ''
      return new Date(ts).toLocaleString('ru-RU', { timeZone: 'Europe/Moscow' })
    },
    userLabel(evt) {
      if (evt.siteUserId > 0) return `#${evt.siteUserId}`
      if (evt.anonId) return evt.anonId.slice(0, 8)
      return '-'
    }
  },
  mounted() {
    this.reloadAll()
  },
  watch: {
    // Сохраняем номера страниц в store, чтобы они восстановились после возврата на вкладку «Статистика».
    statsBySongPage(newVal) { this.$store.commit('setStatsBySongPage', newVal) },
    webEventsPage(newVal) { this.$store.commit('setWebEventsPage', newVal) },
    topListenedPage(newVal) { this.$store.commit('setTopListenedPage', newVal) },
  }
}
</script>

<style scoped>
.stats-view { padding: 12px; text-align: left; }
.stats-toolbar {
  display: flex; align-items: center; gap: 14px; margin-bottom: 14px; flex-wrap: wrap;
}
.tb-item { display: flex; align-items: center; gap: 6px; font-size: 0.85rem; }
.tb-item .form-select { width: auto; }
.stats-nav { margin-bottom: 12px; }
.stats-nav :deep(.nav-link) { font-size: 0.85rem; padding: 6px 14px; }
.chart-card {
  background: #fff; border: 1px solid #e6e6e6; border-radius: 8px;
  padding: 12px 16px; box-shadow: 0 1px 3px rgba(0,0,0,0.06);
}
.chart-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px; }
.chart-title { margin: 0; font-size: 0.95rem; font-weight: 600; }
.stats-table { font-size: 0.8rem; }
.stats-table th, .stats-table td { white-space: nowrap; vertical-align: middle; }
.events-table { font-size: 0.75rem; }
.cell-clip { max-width: 220px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.clickable-row { cursor: pointer; }
.clickable-row:hover { background-color: rgba(0, 0, 0, 0.05); }
</style>
