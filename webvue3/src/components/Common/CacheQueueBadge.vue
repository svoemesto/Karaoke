<template>
  <!--
    @component CacheQueueBadge
    @description Бейдж счётчика количества задач в пуле проверки кеша обращения
    к хранилищу (`cacheFillerExecutor` на backend). Pass 92 — отображение количества
    заданий в пуле проверки кеша и их последовательность.

    Расположение: правый ВЕРХНИЙ угол кнопки Старт/Стоп (mirror существующего
    .text-count-waiting в ProcessWorker.vue, который в правом НИЖНЕМ углу).

    В Pass 95 / OP #95 расширен `/api/health/cacheStats` полем
    `cacheFiller: CacheFillerMetrics {pendingTotal, activeCount, queueSize, ...}`.
    В Pass 96 (этот тикет) — прототип бейджа со stub-данными.

    Цвет: #007bff (стандартный Bootstrap primary, как у progress-bar).
    При 0 — скрывается через v-show.

    @see Pass 95 — Backend queue size endpoint (CLOSED).
    @see Pass 97 — Семантика приоритезации hrQueue (CLOSED).
    @see specs/_wayfinder-92-hrqueue-priority/_charter.md
  -->
  <div v-show="count > 0" class="cache-queue-badge" :title="title">
    <span class="cache-queue-badge-value">{{ count }}</span>
  </div>
</template>

<script>
/**
 * Бейдж счётчика количества задач в пуле проверки кеша.
 *
 * В Pass 96 (прототип) данные берутся из локального prop (stub).
 * В implementation-фазе (Pass 95 + этот бейдж) подключить к Vuex store
 * через расширенный `/api/health/cacheStats` response (поле `cacheFiller.pendingTotal`).
 *
 * @prop {Number} count - количество задач в пуле (передаётся из родителя)
 */
export default {
  name: 'CacheQueueBadge',
  props: {
    count: {
      type: Number,
      required: true,
      default: 0,
    },
  },
  computed: {
    title() {
      return `В пуле проверки кеша: ${this.count} задач`
    },
  },
}
</script>

<style scoped>
.cache-queue-badge {
  /* Позиционирование — внутри родительского .button-with-text-count-waiting (ProcessWorker.vue).
     Position relative родителя + absolute бейджа = правый верхний угол кнопки. */
  position: absolute;
  top: 0;
  right: 0;
  transform: translate(-25%, -25%);
  background-color: #007bff;
  color: white;
  border-radius: 8px;
  padding: 1px 5px;
  font-size: x-small;
  font-weight: bold;
  pointer-events: none;
  /* Тень для контраста с любой подложкой */
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.3);
}

.cache-queue-badge-value {
  display: inline-block;
  line-height: 1.2;
}
</style>
