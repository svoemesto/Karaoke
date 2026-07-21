<template>
  <button class="btn-round-double" :title="tooltipText" @click.left="toggleModal">
    <span
      class="monitor-light-dot"
      :class="{ 'monitor-light-blink': isCritical }"
      :style="{ backgroundColor: dotColor }"
    />
  </button>
  <MonitorModal v-if="isModalVisible" @close="isModalVisible = false" />
</template>

<script>
import MonitorModal from './MonitorModal.vue'

// Держим в синхроне с backend MonitorSeverity.color (karaoke-app/.../monitor/MonitorSeverity.kt).
const SEVERITY_COLORS = {
  '-1': '#4CAF50',
  0: '#4CAF50',
  1: '#FFC107',
  2: '#F44336',
  3: '#D50000',
}

/**
 * Компонент «Monitor Light».
 *
 * @see AGENTS.md
 */

export default {
  name: 'MonitorLight',
  components: { MonitorModal },
  data() {
    return {
      isModalVisible: false,
    }
  },
  computed: {
    topSeverity() {
      return this.$store.getters.monitorTopSeverity
    },
    dotColor() {
      return SEVERITY_COLORS[this.topSeverity] ?? SEVERITY_COLORS['-1']
    },
    isCritical() {
      return this.topSeverity === 3
    },
    tooltipText() {
      if (this.topSeverity < 0) return 'Мониторинг: сообщений нет'
      if (this.topSeverity === 0) return 'Мониторинг: информационные сообщения'
      if (this.topSeverity === 1) return 'Мониторинг: есть предупреждения'
      if (this.topSeverity === 2) return 'Мониторинг: есть ошибки'
      return 'Мониторинг: критическая проблема!'
    },
  },
  mounted() {
    this.$store.dispatch('loadMonitorAlerts')
  },
  methods: {
    toggleModal() {
      this.isModalVisible = !this.isModalVisible
    },
  },
}
</script>

<style scoped>
/* Тот же визуальный стиль круглой кнопки хедера, что и у ResourceLimitToggle/ProcessWorker
   (btn-round-double scoped per-component - Vue не расшаривает scoped-стили между компонентами). */
.btn-round-double {
  border: solid 1px black;
  border-radius: 6px;
  width: 50px;
  height: 50px;
  margin: 0;
  background-color: antiquewhite;
  display: flex;
  align-items: center;
  justify-content: center;
}
.btn-round-double:hover {
  background-color: lightpink;
}
.btn-round-double:focus {
  background-color: darksalmon;
}

.monitor-light-dot {
  display: inline-block;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  border: 2px solid rgba(0, 0, 0, 0.4);
}

@keyframes monitor-light-blink {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0.25;
  }
}
.monitor-light-blink {
  animation: monitor-light-blink 1s step-start infinite;
}
</style>
