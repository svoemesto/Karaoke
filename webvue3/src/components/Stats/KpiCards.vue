<template>
  <div class="kpi-row">
    <div v-for="c in cards" :key="c.label" class="kpi-card" :style="{ '--kpi-accent': c.color }">
      <div class="kpi-icon">{{ c.icon }}</div>
      <div class="kpi-body">
        <div class="kpi-value">{{ formatNum(c.value) }}</div>
        <div class="kpi-label">{{ c.label }}</div>
        <div v-if="c.sub" class="kpi-sub">{{ c.sub }}</div>
      </div>
    </div>
  </div>
</template>

<script>
/**
 * Компонент «Kpi Cards».
 *
 * @see AGENTS.md
 */
export default {
  name: 'KpiCards',
  props: { summary: { type: Object, default: null } },
  computed: {
    cards() {
      const s = this.summary || {}
      return [
        { label: 'Всего событий', value: s.totalEvents || 0, icon: '📊', color: '#4e79a7' },
        {
          label: 'Уник. посетителей',
          value: s.uniqueVisitors || 0,
          icon: '👥',
          color: '#59a14f',
          sub: 'по anon_id',
        },
        {
          label: 'Зарегистр. события',
          value: s.registeredEvents || 0,
          icon: '🔑',
          color: '#f28e2b',
          sub: `${s.uniqueRegisteredUsers || 0} польз.`,
        },
        { label: 'За сегодня', value: s.eventsToday || 0, icon: '📅', color: '#e15759' },
        { label: 'За 7 дней', value: s.events7d || 0, icon: '🗓️', color: '#76b7b2' },
        { label: 'За 30 дней', value: s.events30d || 0, icon: '📈', color: '#af7aa1' },
        {
          label: 'Топ-канал',
          value: s.topChannelCount || 0,
          icon: '🏆',
          color: '#ff9da7',
          sub: s.topChannel || '-',
        },
      ]
    },
  },
  methods: {
    formatNum(n) {
      return (n || 0).toLocaleString('ru-RU')
    },
  },
}
</script>

<style scoped>
.kpi-row {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}
.kpi-card {
  display: flex;
  align-items: center;
  gap: 12px;
  background: #fff;
  border: 1px solid #e6e6e6;
  border-left: 4px solid var(--kpi-accent, #4e79a7);
  border-radius: 8px;
  padding: 12px 14px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
}
.kpi-icon {
  font-size: 1.6rem;
  line-height: 1;
}
.kpi-body {
  min-width: 0;
}
.kpi-value {
  font-size: 1.4rem;
  font-weight: 700;
  color: #222;
  line-height: 1.1;
}
.kpi-label {
  font-size: 0.78rem;
  color: #555;
}
.kpi-sub {
  font-size: 0.7rem;
  color: #999;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 140px;
}
</style>
