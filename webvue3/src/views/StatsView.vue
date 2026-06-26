<template>
  <div class="stats-view">

    <div class="stats-section">
      <div class="stats-header">
        <h5>Статистика по песням</h5>
        <button class="btn btn-sm btn-outline-secondary" @click="reloadStatsBySong">Обновить</button>
      </div>
      <div v-if="statsBySongIsLoading" class="text-center py-2">
        <BSpinner small />
      </div>
      <div v-else class="table-responsive">
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
    </div>

    <div class="stats-section mt-4">
      <div class="stats-header">
        <h5>Последние события ({{ webEventsLimit }})</h5>
        <div class="d-flex gap-2 align-items-center">
          <select class="form-select form-select-sm limit-select" v-model="webEventsLimit" @change="reloadWebEvents">
            <option :value="100">100</option>
            <option :value="500">500</option>
            <option :value="1000">1000</option>
          </select>
          <button class="btn btn-sm btn-outline-secondary" @click="reloadWebEvents">Обновить</button>
        </div>
      </div>
      <div v-if="webEventsIsLoading" class="text-center py-2">
        <BSpinner small />
      </div>
      <div v-else class="table-responsive">
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
    </div>

  </div>
</template>

<script>
import { BSpinner } from 'bootstrap-vue-next'

export default {
  name: 'StatsView',
  components: { BSpinner },
  data() {
    return {
      webEventsLimit: 500
    }
  },
  computed: {
    statsBySong() { return this.$store.getters.getStatsBySong },
    statsBySongIsLoading() { return this.$store.getters.getStatsBySongIsLoading },
    webEvents() { return this.$store.getters.getWebEvents },
    webEventsIsLoading() { return this.$store.getters.getWebEventsIsLoading },
  },
  methods: {
    reloadStatsBySong() {
      this.$store.dispatch('loadStatsBySong')
    },
    reloadWebEvents() {
      this.$store.dispatch('loadWebEvents', this.webEventsLimit)
    },
    formatDate(ts) {
      if (!ts) return ''
      const d = new Date(ts)
      return d.toLocaleString('ru-RU', { timeZone: 'Europe/Moscow' })
    }
  },
  mounted() {
    this.$store.dispatch('loadStatsBySong')
    this.$store.dispatch('loadWebEvents', this.webEventsLimit)
  }
}
</script>

<style scoped>
.stats-view {
  padding: 10px;
  text-align: left;
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
</style>
