<template>
  <!-- Готовность плеера подгружается асинхронно после отрисовки таблицы: пока не пришёл ответ —
       спиннер. Дальше три исхода: зелёная (можно смотреть прямо сейчас) — открывает плеер; золотая
       (контент готов, но зрителю сейчас недоступен, а подписка на песню разрешена) — кликабельна,
       предлагает оформить подписку (или войти/зарегистрироваться, если анонимный); серая — либо
       подписка на эту песню запрещена автором, либо контент ещё не готов (title меняется). -->
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
    v-else-if="showSubscribeCta"
    href="#"
    class="platform-icon"
    title="Оформить подписку на эту песню"
    @click.prevent="onSubscribeClick"
  >
    <SvgIcon name="player" variant="gold" :size="20" />
  </a>
  <span v-else class="platform-icon disabled" :title="disabledTitle">
    <SvgIcon name="player" :active="false" :size="20" />
  </span>
</template>

<script>
import { useRouter, useRoute } from 'vue-router'
import SvgIcon from './SvgIcon.vue'
import { openPlayer } from '../services/playerLauncher'
import { useAuth } from '../composables/useAuth'

export default {
  name: 'PlayerIcon',
  components: { SvgIcon },
  props: {
    songId: { type: [Number, String], required: true },
    // 'loading' | 'active' | 'disabled' — может ли ТЕКУЩИЙ посетитель открыть плеер прямо сейчас.
    watchState: { type: String, default: 'loading' },
    // 'loading' | 'ready' | 'notready' — готовность контента независимо от прав зрителя.
    contentReadyState: { type: String, default: 'loading' },
    // Разрешена ли отдельная подписка на эту песню (id_tariff !== -1 у автора в карточке песни).
    subscribable: { type: Boolean, default: false }
  },
  emits: ['subscribe'],
  setup() {
    const router = useRouter()
    const route = useRoute()
    const { token } = useAuth()
    return { router, route, token }
  },
  computed: {
    showSpinner() {
      return this.watchState === 'loading' || this.contentReadyState === 'loading'
    },
    showSubscribeCta() {
      return this.subscribable && this.contentReadyState === 'ready'
    },
    disabledTitle() {
      if (this.subscribable && this.contentReadyState === 'notready') {
        return 'На эту песню можно будет оформить подписку, когда она будет готова'
      }
      return 'Плеер недоступен'
    }
  },
  methods: {
    onOpen() {
      openPlayer(this.songId)
    },
    onSubscribeClick() {
      // Аноним — предлагаем войти/зарегистрироваться (после входа вернём на текущую страницу).
      if (!this.token) {
        this.router.push({ path: '/login', query: { redirect: this.route.fullPath } })
        return
      }
      this.$emit('subscribe', this.songId)
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
