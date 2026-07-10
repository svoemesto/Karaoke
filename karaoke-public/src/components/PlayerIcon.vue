<template>
  <!-- Готовность плеера подгружается асинхронно после отрисовки таблицы: пока не пришёл ответ —
       спиннер. Дальше три исхода: зелёная (полный доступ прямо сейчас — премиум/в эфире/подписан)
       — открывает плеер; золотая (контент готов, но полного доступа нет) — тоже открывает плеер,
       но он сам решит (см. PublicPlayerController.access) отдать демо-фрагмент вместо paywall —
       мотивация подписаться идёт уже внутри самого плеера (водяной знак + оверлей по окончании
       фрагмента), а не мгновенным отказом по клику; серая — контент ещё не готов (title меняется).
       -->
  <span v-if="showSpinner" class="player-icon-spinner" title="Проверка доступности плеера…" />
  <a
    v-else-if="watchState === 'active'"
    href="#"
    class="platform-icon"
    title="Открыть онлайн-плеер"
    @click.prevent="onOpen"
  >
    <SvgIcon name="player" :active="true" :size="20" />
  </a>
  <a
    v-else-if="showDemoCta"
    href="#"
    class="platform-icon"
    title="Прослушать демо-фрагмент (полная версия — по подписке)"
    @click.prevent="onOpen"
  >
    <SvgIcon name="player" variant="gold" :size="20" />
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
    // 'loading' | 'active' | 'disabled' — может ли ТЕКУЩИЙ посетитель открыть плеер прямо сейчас.
    watchState: { type: String, default: 'loading' },
    // 'loading' | 'ready' | 'notready' — готовность контента независимо от прав зрителя.
    contentReadyState: { type: String, default: 'loading' }
  },
  computed: {
    showSpinner() {
      return this.watchState === 'loading' || this.contentReadyState === 'loading'
    },
    // Демо доступно любому посетителю (даже анониму), как только контент готов — независимо от
    // того, разрешена ли для этой песни отдельная подписка (subscribable): в отличие от старого
    // прямого перехода к paywall, демо не требует входа на сайт.
    showDemoCta() {
      return this.contentReadyState === 'ready'
    }
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
