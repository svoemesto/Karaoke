<template>
  <!-- Готовность плеера подгружается асинхронно после отрисовки таблицы: пока не пришёл ответ —
       спиннер; затем активная (зелёная, кликабельная) или недоступная (серая) иконка плеера. -->
  <span v-if="state === 'loading'" class="player-icon-spinner" title="Проверка доступности плеера…" />
  <a
    v-else-if="state === 'active'"
    href="#"
    class="platform-icon"
    title="Открыть онлайн-плеер"
    @click.prevent="onOpen"
  >
    <SvgIcon name="player" :active="true" :size="20" />
  </a>
  <span v-else class="platform-icon disabled" title="Плеер недоступен">
    <SvgIcon name="player" :active="false" :size="20" />
  </span>
</template>

<script>
import SvgIcon from './SvgIcon.vue'
import { openPlayer } from '../services/playerLauncher'

export default {
  name: 'PlayerIcon',
  components: { SvgIcon },
  props: {
    songId: { type: [Number, String], required: true },
    // 'loading' | 'active' | 'disabled'
    state: { type: String, default: 'loading' }
  },
  methods: {
    onOpen() {
      openPlayer(this.songId)
    }
  }
}
</script>

<style scoped>
.player-icon-spinner {
  display: inline-block;
  width: 14px;
  height: 14px;
  border: 2px solid #b9c9e0;
  border-top-color: #22a447;
  border-radius: 50%;
  vertical-align: middle;
  animation: player-icon-spin 0.8s linear infinite;
}
@keyframes player-icon-spin {
  to { transform: rotate(360deg); }
}
</style>
