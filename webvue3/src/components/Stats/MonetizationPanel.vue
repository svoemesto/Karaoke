<template>
  <div class="chart-card mb-3">
    <div class="chart-head">
      <h6 class="chart-title">Монетизация (подписки)</h6>
    </div>
    <div v-if="isLoading && !summary" class="text-center py-3"><BSpinner small /></div>
    <template v-else-if="summary">
      <div class="mp-kpi-row">
        <div class="mp-kpi-card" style="--mp-accent: #59a14f">
          <div class="mp-kpi-icon">💰</div>
          <div class="mp-kpi-body">
            <div class="mp-kpi-value">{{ formatMoney(summary.revenueTotal) }} ₽</div>
            <div class="mp-kpi-label">Выручка всего</div>
            <div class="mp-kpi-sub">
              песни: {{ formatMoney(summary.revenueSong) }} ₽ · сайт:
              {{ formatMoney(summary.revenueSite) }} ₽
            </div>
          </div>
        </div>
        <div class="mp-kpi-card" style="--mp-accent: #4e79a7">
          <div class="mp-kpi-icon">🎵</div>
          <div class="mp-kpi-body">
            <div class="mp-kpi-value">{{ summary.paidSongSubscriptions }}</div>
            <div class="mp-kpi-label">Подписок на песню (оплачено)</div>
          </div>
        </div>
        <div class="mp-kpi-card" style="--mp-accent: #f28e2b">
          <div class="mp-kpi-icon">🌐</div>
          <div class="mp-kpi-body">
            <div class="mp-kpi-value">{{ summary.paidSiteSubscriptions }}</div>
            <div class="mp-kpi-label">Подписок на сайт (оплачено)</div>
          </div>
        </div>
        <div class="mp-kpi-card" style="--mp-accent: #e15759">
          <div class="mp-kpi-icon">⏳</div>
          <div class="mp-kpi-body">
            <div class="mp-kpi-value">{{ summary.pendingSubscriptions }}</div>
            <div class="mp-kpi-label">В ожидании оплаты</div>
            <div class="mp-kpi-sub">неудачных: {{ summary.failedSubscriptions }}</div>
          </div>
        </div>
        <div class="mp-kpi-card" style="--mp-accent: #af7aa1">
          <div class="mp-kpi-icon">🪙</div>
          <div class="mp-kpi-body">
            <div class="mp-kpi-value">{{ activePremiumTotal }}</div>
            <div class="mp-kpi-label">Активный премиум (из {{ summary.totalSiteUsers }})</div>
            <div class="mp-kpi-sub">
              ручной: {{ summary.activeManualPremium }} · Sponsr:
              {{ summary.activeSponsrPremium }} · сайт: {{ summary.activeSitePremium }}
            </div>
          </div>
        </div>
      </div>
    </template>

    <div class="mp-top-songs">
      <h6 class="mp-subtitle">Топ песен по подпискам</h6>
      <div v-if="topSongsIsLoading" class="text-center py-2"><BSpinner small /></div>
      <div v-else-if="topSongs.length === 0" class="mp-empty">
        Пока нет оплаченных подписок на песни.
      </div>
      <table v-else class="table table-sm table-hover table-bordered stats-table">
        <thead class="table-dark">
          <tr>
            <th>ID</th>
            <th>Песня</th>
            <th>Автор</th>
            <th>Подписок</th>
            <th>Выручка, ₽</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in topSongs" :key="row.songId">
            <td>{{ row.songId }}</td>
            <td class="text-start">{{ row.songName }}</td>
            <td class="text-start">{{ row.author }}</td>
            <td>{{ row.subscriptionsCount }}</td>
            <td>{{ formatMoney(row.revenue) }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<script>
import { BSpinner } from 'bootstrap-vue-next'

/**
 * Компонент «Monetization Panel».
 *
 * @see AGENTS.md
 */

export default {
  name: 'MonetizationPanel',
  components: { BSpinner },
  props: {
    summary: { type: Object, default: null },
    isLoading: { type: Boolean, default: false },
    topSongs: { type: Array, default: () => [] },
    topSongsIsLoading: { type: Boolean, default: false },
  },
  computed: {
    activePremiumTotal() {
      const s = this.summary
      if (!s) return 0
      // Пересечение источников не убираем (юзер может числиться в нескольких сразу) — это верхняя
      // оценка, не точное число уникальных премиум-пользователей.
      return Math.max(s.activeManualPremium, s.activeSponsrPremium, s.activeSitePremium)
    },
  },
  methods: {
    formatMoney(n) {
      return (n || 0).toLocaleString('ru-RU', { maximumFractionDigits: 2 })
    },
  },
}
</script>

<style scoped>
.chart-card {
  background: #fff;
  border: 1px solid #e6e6e6;
  border-radius: 8px;
  padding: 12px 16px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
}
.chart-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}
.chart-title {
  margin: 0;
  font-size: 0.95rem;
  font-weight: 600;
}
.mp-kpi-row {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}
.mp-kpi-card {
  display: flex;
  align-items: center;
  gap: 12px;
  background: #fff;
  border: 1px solid #e6e6e6;
  border-left: 4px solid var(--mp-accent, #4e79a7);
  border-radius: 8px;
  padding: 12px 14px;
}
.mp-kpi-icon {
  font-size: 1.6rem;
  line-height: 1;
}
.mp-kpi-body {
  min-width: 0;
}
.mp-kpi-value {
  font-size: 1.3rem;
  font-weight: 700;
  color: #222;
  line-height: 1.1;
}
.mp-kpi-label {
  font-size: 0.76rem;
  color: #555;
}
.mp-kpi-sub {
  font-size: 0.68rem;
  color: #999;
}
.mp-subtitle {
  font-size: 0.85rem;
  font-weight: 600;
  margin: 8px 0;
}
.mp-empty {
  font-size: 0.85rem;
  color: #999;
}
.stats-table {
  font-size: 0.8rem;
}
</style>
