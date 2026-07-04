<template>
  <div class="stats-view">

    <div class="stats-toolbar">
      <label>
        БД:
        <select class="form-select form-select-sm" v-model="target" @change="onTargetChange">
          <option value="local">Локальная</option>
          <option value="remote">Сервер</option>
        </select>
      </label>
    </div>

    <div class="stats-section">
      <div class="stats-header">
        <h5>Статистика по песням</h5>
        <div class="d-flex gap-2 align-items-center">
          <select class="form-select form-select-sm limit-select" v-model="statsBySongPageSize" @change="onStatsBySongPageSizeChange">
            <option :value="20">20</option>
            <option :value="50">50</option>
            <option :value="100">100</option>
          </select>
          <button class="btn btn-sm btn-outline-secondary" @click="reloadStatsBySong">Обновить</button>
        </div>
      </div>
      <div v-if="statsBySongIsLoading" class="text-center py-2">
        <BSpinner small />
      </div>
      <template v-else>
        <div class="table-responsive">
          <table class="table table-sm table-hover table-bordered stats-table">
            <thead class="table-dark">
              <tr>
                <th>ID</th>
                <th>Описание</th>
                <th title="Всего">Всего</th>
                <th title="Открытий страницы песни на сайте">Сайт</th>
                <th title="Boosty">Boosty</th>
                <th title="VK Karaoke">VK кар.</th>
                <th title="VK Lyrics">VK тек.</th>
                <th title="Dzen Karaoke">Dzen кар.</th>
                <th title="Dzen Lyrics">Dzen тек.</th>
                <th title="Telegram Karaoke">TG кар.</th>
                <th title="Telegram Lyrics">TG тек.</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in statsBySong" :key="row.songId">
                <td>{{ row.songId }}</td>
                <td class="text-start">{{ row.description }}</td>
                <td class="fw-bold">{{ row.cntTotal }}</td>
                <td>{{ row.cntSm }}</td>
                <td>{{ row.cntBoosty }}</td>
                <td>{{ row.cntVkKaraoke }}</td>
                <td>{{ row.cntVkLyrics }}</td>
                <td>{{ row.cntDzenKaraoke }}</td>
                <td>{{ row.cntDzenLyrics }}</td>
                <td>{{ row.cntTgKaraoke }}</td>
                <td>{{ row.cntTgLyrics }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <div class="stats-pagination">
          <b-pagination
              v-model="statsBySongPageModel"
              :total-rows="statsBySongTotalCount"
              :per-page="statsBySongPageSize"
              :limit="20"
              size="sm"
              pills
          ></b-pagination>
          <span class="stats-count">Всего: {{ statsBySongTotalCount }}</span>
        </div>
      </template>
    </div>

    <div class="stats-section mt-4">
      <div class="stats-header">
        <h5>Последние события</h5>
        <div class="d-flex gap-2 align-items-center">
          <select class="form-select form-select-sm limit-select" v-model="webEventsPageSize" @change="onWebEventsPageSizeChange">
            <option :value="20">20</option>
            <option :value="50">50</option>
            <option :value="100">100</option>
          </select>
          <button class="btn btn-sm btn-outline-secondary" @click="reloadWebEvents">Обновить</button>
        </div>
      </div>
      <div v-if="webEventsIsLoading" class="text-center py-2">
        <BSpinner small />
      </div>
      <template v-else>
        <div class="table-responsive">
          <table class="table table-sm table-hover table-bordered stats-table">
            <thead class="table-dark">
              <tr>
                <th>Дата</th>
                <th>Тип</th>
                <th>Описание</th>
                <th>Referer</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(evt, idx) in webEvents" :key="idx">
                <td class="text-nowrap">{{ formatDate(evt.eventDate) }}</td>
                <td class="text-nowrap">{{ evt.eventType }}</td>
                <td class="text-start">{{ evt.eventDescription }}</td>
                <td class="text-start referer-cell">{{ evt.eventReferer }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <div class="stats-pagination">
          <b-pagination
              v-model="webEventsPageModel"
              :total-rows="webEventsTotalCount"
              :per-page="webEventsPageSize"
              :limit="20"
              size="sm"
              pills
          ></b-pagination>
          <span class="stats-count">Всего: {{ webEventsTotalCount }}</span>
        </div>
      </template>
    </div>

  </div>
</template>

<script>
import { BSpinner, BPagination } from 'bootstrap-vue-next'

export default {
  name: 'StatsView',
  components: { BSpinner, BPagination },
  data() {
    return {
      statsBySongPage: 1,
      statsBySongPageSize: 50,
      webEventsPage: 1,
      webEventsPageSize: 50,
    }
  },
  computed: {
    statsBySong() { return this.$store.getters.getStatsBySong },
    statsBySongIsLoading() { return this.$store.getters.getStatsBySongIsLoading },
    statsBySongTotalCount() { return this.$store.getters.getStatsBySongTotalCount },
    webEvents() { return this.$store.getters.getWebEvents },
    webEventsIsLoading() { return this.$store.getters.getWebEventsIsLoading },
    webEventsTotalCount() { return this.$store.getters.getWebEventsTotalCount },
    target: {
      get() { return this.$store.getters.getStatsTarget },
      set(value) { this.$store.dispatch('setStatsTarget', value) }
    },
    // Обёртки над локальным номером страницы: сеттер — единственное место, дёргающее
    // перезагрузку с сервера при клике по пагинации. Программные сбросы страницы (смена
    // БД/размера страницы) пишут в this.statsBySongPage/webEventsPage напрямую, в обход
    // сеттера, и сами вызывают reload — иначе был бы двойной запрос.
    statsBySongPageModel: {
      get() { return this.statsBySongPage },
      set(value) { this.statsBySongPage = value; this.reloadStatsBySong() }
    },
    webEventsPageModel: {
      get() { return this.webEventsPage },
      set(value) { this.webEventsPage = value; this.reloadWebEvents() }
    },
  },
  methods: {
    reloadStatsBySong() {
      this.$store.dispatch('loadStatsBySong', { page: this.statsBySongPage, pageSize: this.statsBySongPageSize })
    },
    reloadWebEvents() {
      this.$store.dispatch('loadWebEvents', { page: this.webEventsPage, pageSize: this.webEventsPageSize })
    },
    onStatsBySongPageSizeChange() {
      this.statsBySongPage = 1
      this.reloadStatsBySong()
    },
    onWebEventsPageSizeChange() {
      this.webEventsPage = 1
      this.reloadWebEvents()
    },
    onTargetChange() {
      this.statsBySongPage = 1
      this.webEventsPage = 1
      this.reloadStatsBySong()
      this.reloadWebEvents()
    },
    formatDate(ts) {
      if (!ts) return ''
      const d = new Date(ts)
      return d.toLocaleString('ru-RU', { timeZone: 'Europe/Moscow' })
    }
  },
  mounted() {
    this.reloadStatsBySong()
    this.reloadWebEvents()
  }
}
</script>

<style scoped>
.stats-view {
  padding: 10px;
  text-align: left;
}
.stats-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
  font-size: small;
}
.stats-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 6px;
}
.stats-table {
  font-size: 0.8rem;
}
.stats-table th, .stats-table td {
  white-space: nowrap;
  vertical-align: middle;
}
.referer-cell {
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.limit-select {
  width: 80px;
}
.stats-pagination {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 6px;
}
.stats-count {
  font-size: small;
  color: gray;
}
</style>
