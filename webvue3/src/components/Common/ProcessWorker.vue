<template>
  <div class="process_worker">
    <div class="wrapper">
      <div class="process-text" v-text="processName" />
      <div class="wrapper-bar">
        <div
          class="process-progress-bar"
          role="progressbar"
          :style="styleProgressBar"
          v-text="processPercentage"
        />
      </div>
    </div>
    <div v-show="!hideButton" class="button-with-text-count-waiting" @dblclick="forceStopClick">
      <button
        class="btn-round-double"
        :disabled="disabled"
        @click.left="clickStartStopWorkerButton"
      >
        <img v-if="!isWork" alt="start" class="icon-40" src="../../assets/svg/icon_play.svg" />
        <img v-else alt="stop" class="icon-40" src="../../assets/svg/icon_stop.svg" />
      </button>
      <div class="text-count-waiting" v-text="countWaiting" />
      <!-- specs/118 #397: бейдж размера cache-очереди StorageMetadataCache (правый верхний угол).
           Показывается всегда (даже если queueSize=0), чтобы пользователь видел индикатор активности. -->
      <div class="text-cache-pool-size" v-text="cacheQueueSize" />
    </div>
    <custom-confirm
      v-if="isConfirmVisible"
      :params="confirmParams"
      @close="isConfirmVisible = false"
    />
  </div>
</template>

<script>
import CustomConfirm from './CustomConfirm.vue'

/**
 * UI-компонент для отображения статуса KaraokeProcess-воркера.
 *
 * Используется на странице `ProcessesView` и в `HealthReport` для показа
 * текущего состояния очереди задач:
 * - `WAITING` (запланировано), `WORKING` (выполняется), `DONE`, `ERROR`.
 * - Прогресс-бар (парсится из `KaraokeProcess.percentage`).
 * - Кнопки `Force stop` (вызывает `forceStop()` через `/api/process/forceStop`).
 *
 * Подписывается на SSE `PROCESS_WORKER_STATE` и `PROCESS_COUNT_WAITING`
 * для live-обновления.
 *
 * @see archive/docs/features/async-process-queue.md
 */
export default {
  name: 'ProcessWorker',
  components: {
    CustomConfirm,
  },
  props: {
    // Если hideButton = true то не показывать кнопку старт/стоп
    hideButton: {
      type: Boolean,
      required: false,
      default: false,
    },
    // Показывать потоки с id, перечисленными в includedThreadId. Если пусто - показывать все (в зависимости от excludedThreadId)
    includedThreadId: {
      type: Array,
      required: false,
      default: () => [],
    },
    // Не показывать потоки с id из списка
    excludedThreadId: {
      type: Array,
      required: false,
      default: () => [],
    },
  },
  data() {
    return {
      isConfirmVisible: false,
      confirmParams: undefined,
      // specs/118 #397: interval для periodic poll размера cache-очереди.
      cacheQueueSizeInterval: null,
    }
  },
  computed: {
    process() {
      return this.$store.getters.getWorkingProcessForThreads(
        this.includedThreadId,
        this.excludedThreadId,
      )
    },
    isWork() {
      return this.$store.getters.getProcessIsWorking
    },
    stopAfterThreadIsDone() {
      return this.$store.getters.getProcessWillStopAfterThreadIsDone
    },
    countWaiting() {
      return this.$store.getters.getCountWaiting
    },
    // specs/118 #397: размер cache-очереди StorageMetadataCache.
    cacheQueueSize() {
      return this.$store.getters.getCacheQueueSize
    },
    disabled() {
      return this.isWork && this.stopAfterThreadIsDone
    },
    processName() {
      const maxSymbols = 75
      let name = this.process
        ? `${this.process.name} /${this.process.type}/ [${this.process.timeLeftStr}]`
        : ''
      return this.truncateString(name, maxSymbols)
    },
    processPercentage() {
      return this.process ? `${this.process.percentage}%` : ''
    },
    styleProgressBar() {
      return {
        fontSize: 'small',
        width: this.processPercentage,
        backgroundImage:
          'linear-gradient(45deg, hsla(0, 0%, 100%, .15) 25%, transparent 0, transparent 50%, hsla(0, 0%, 100%, .15) 0, hsla(0, 0%, 100%, .15) 75%, transparent 0, transparent)',
        backgroundSize: '1rem 1rem',
        display: 'flex',
        flexDirection: 'column',
        overflow: 'hidden',
        color: '#fff',
        justifyContent: 'center',
        textAlign: 'center',
        whiteSpace: 'nowrap',
        backgroundColor: '#007bff',
        transition: 'width .6s ease',
        animation: '1s linear infinite progress-bar-stripes',
      }
    },
  },
  mounted() {
    this.checkUpdateProcessesWorker()
    this.checkCountWaiting()
    // specs/118 #397: initial poll + periodic poll каждые 5с для размера cache-очереди.
    this.checkCacheQueueSize()
    this.cacheQueueSizeInterval = setInterval(() => this.checkCacheQueueSize(), 5000)
  },
  beforeUnmount() {
    // specs/118 #397: очистить interval при unmount.
    if (this.cacheQueueSizeInterval) {
      clearInterval(this.cacheQueueSizeInterval)
      this.cacheQueueSizeInterval = null
    }
  },
  methods: {
    clickStartStopWorkerButton() {
      this.$store.dispatch('startStopProcessWorker')
    },
    // Двойной клик по задизейбленной кнопке (во время мягкого ожидания остановки) — принудительная
    // остановка очереди после подтверждения: убить docker-контейнеры и вернуть задания в WAITING.
    forceStopClick() {
      if (!this.disabled) return // доступно только во время мягкого ожидания остановки
      this.confirmParams = {
        header: 'Принудительная остановка',
        body: 'Немедленно остановить очередь? Текущее задание вернётся в очередь (WAITING), а его docker-контейнер будет убит.',
        alertType: 'error',
        timeout: 10,
        callback: this.doForceStop,
      }
      this.isConfirmVisible = true
    },
    doForceStop() {
      this.$store.dispatch('forceStopProcessWorker')
    },
    checkUpdateProcessesWorker() {
      this.$store.dispatch('getProcessesWorkerStatusPromise').then((data) => {
        let status = JSON.parse(data)
        let isWork = status.isWork
        let stopAfterThreadIsDone = status.stopAfterThreadIsDone
        this.$store.dispatch('setProcessIsWorking', isWork)
        this.$store.dispatch('setProcessWillStopAfterThreadIsDone', stopAfterThreadIsDone)
      })
    },
    checkCountWaiting() {
      this.$store.dispatch('getProcessesCountWaitingPromise').then((raw) => {
        // promisedXMLHttpRequest возвращает xhr.responseText (string). Backend
        // возвращает просто Long, не объект. Парсим как JSON (для простого числа
        // JSON.parse("12345") === 12345 — Number).
        let count = 0
        try {
          count = typeof raw === 'number' ? raw : Number(JSON.parse(raw))
        } catch (e) {
          console.warn('[ProcessWorker.checkCountWaiting] parse failed:', e)
        }
        this.$store.dispatch('setCountWaiting', { countWaiting: count })
      })
    },
    // specs/118 #397: загрузить размер cache-очереди.
    checkCacheQueueSize() {
      this.$store.dispatch('getCacheQueueSizePromise').then((raw) => {
        // promisedXMLHttpRequest возвращает xhr.responseText (string), не parsed object.
        // Парсим JSON: {"queueSize": N}.
        let size = 0
        try {
          const parsed = typeof raw === 'string' ? JSON.parse(raw) : raw
          size = parsed?.queueSize ?? 0
        } catch (e) {
          console.warn('[ProcessWorker.checkCacheQueueSize] JSON parse failed:', e)
        }
        this.$store.dispatch('setCacheQueueSize', size)
      })
    },
    truncateString(name, maxSymbols) {
      if (name.length <= maxSymbols) {
        return name
      }

      if (maxSymbols <= 3) {
        return '...'
      }

      const charsToShow = maxSymbols - 3 // 3 символа для троеточия
      const frontChars = Math.ceil(charsToShow / 2)
      const backChars = Math.floor(charsToShow / 2)

      const front = name.substring(0, frontChars)
      const back = name.substring(name.length - backChars)

      return front + '...' + back
    },
  },
}
</script>

<style scoped>
.process_worker {
  display: flex;
  margin: 0 10px;
}
.worker-start-stop-button {
}
.icon-start {
}

.btn-round-double {
  border: solid 1px black;
  border-radius: 6px;
  width: 50px;
  height: 50px;
  margin-left: 2px;
  background-color: antiquewhite;
}
.btn-round-double:hover {
  background-color: lightpink;
}
.btn-round-double:focus {
  background-color: darksalmon;
}
.btn-round-double[disabled] {
  background-color: lightgray;
  /* Пропускаем события мыши на родительский div, чтобы сработал @dblclick (форс-стоп) */
  pointer-events: none;
}
.icon-40 {
  width: 40px;
  height: 40px;
  margin-left: -1px;
}

.wrapper {
  display: flex;
  flex-direction: column;
  margin-left: 10px;
}
.process-text {
  font-size: small;
}
.button-with-text-count-waiting {
  position: relative;
}
.text-count-waiting {
  font-size: x-small;
  color: white;
  position: absolute;
  pointer-events: none;
  top: 100%;
  left: 100%;
  transform: translate(-50%, -50%);
  padding: 0 4px;
  border-radius: 5px;
  background-color: gray;
}
.wrapper-bar {
  display: flex;
  height: 1rem;
  overflow: hidden;
  line-height: 0;
  font-size: 0.75rem;
  background-color: #e9ecef;
  border-radius: 0.25rem;
}
/* specs/118 #397: синий бейдж размера cache-очереди — правый верхний угол кнопки. */
.text-cache-pool-size {
  font-size: x-small;
  color: white;
  position: absolute;
  pointer-events: none;
  top: 0;
  right: 0;
  transform: translate(50%, -50%);
  padding: 0 4px;
  border-radius: 5px;
  background-color: var(--bs-primary, #0d6efd);
}
</style>
