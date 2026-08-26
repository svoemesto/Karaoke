<template>
  <!-- Pass 239 (specs/239-zakroma-author-songs-batch-render): иконка плеера без per-row readiness.
       Все данные (premium/inAir/flagFree/hasSubscription/contentReady) приходят с бэка в стриме
       или из module-level store'ов, никаких асинхронных спиннеров. Три финальных состояния:
       зелёная (полный доступ), золотая (демо), серая disabled (контент не готов). -->
  <a
    v-if="isActive"
    href="#"
    class="platform-icon"
    :aria-label="'Открыть онлайн-плеер'"
    title="Открыть онлайн-плеер"
    @click.prevent="onOpen"
  >
    <SvgIcon name="player" :active="true" :size="20" />
  </a>
  <a
    v-else-if="isDemo"
    href="#"
    class="platform-icon"
    :aria-label="'Прослушать демо-фрагмент'"
    title="Прослушать демо-фрагмент (полная версия — по подписке)"
    @click.prevent="onOpen"
  >
    <SvgIcon name="player" variant="gold" :size="20" />
  </a>
  <span
    v-else
    class="platform-icon disabled"
    :aria-label="'Плеер недоступен'"
    title="Плеер недоступен"
  >
    <SvgIcon name="player" :active="false" :size="20" />
  </span>
</template>

<script>
import SvgIcon from './SvgIcon.vue'
import { openPlayer } from '../services/playerLauncher'

/**
 * Компонент «Player Icon».
 *
 * @see AGENTS.md
 *
 * Pass 239 (specs/239-zakroma-author-songs-batch-render): три новых props добавлены — `premium`,
 * `inAir`, `flagFree`, `hasSubscription`. Prop `watchState` сохранён для backward-compat
 * (старые call-site'ы), но больше НЕ используется в Zakroma/Search/AuthorPlaylist (FR-004).
 *
 * @prop {boolean} premium — пользователь премиум? (из useAuth())
 * @prop {boolean} inAir — песня в эфире (`song.isFreelyAvailableNow` либо `song.onAir` из стрима).
 * @prop {boolean} flagFree — песня «всегда бесплатно» (song.free) — отдельный prop для ясности,
 *   дублирует `inAir` для случая `alwaysFree && !onAir`.
 * @prop {boolean} hasSubscription — есть ли у пользователя активная подписка на эту песню
 *   (из `useSongSubscriptions().subscriptionIds`).
 *
 * Логика:
 *   - contentReady=false → серая disabled (контент не готов).
 *   - contentReady=true AND (inAir OR flagFree OR premium OR hasSubscription) → зелёная.
 *   - contentReady=true AND NOT any-of-above → золотая (демо-фрагмент).
 *
 * Старые props `watchState`/'contentReadyState' === 'loading' трактуются как 'notready'
 * (FR-017 — defensive default для частично переведённых call-site'ов).
 */
export default {
  name: 'PlayerIcon',
  components: { SvgIcon },
  props: {
    songId: { type: [Number, String], required: true },
    // DEPRECATED (Pass 239): оставлен для backward-compat. На страницах списка песен НЕ передаётся.
    // 'loading' трактуется как 'notready' (FR-017).
    watchState: { type: String, default: 'notready' },
    // 'ready' | 'notready' (Pass 239: 'loading' тоже допустим для backward-compat → 'notready').
    contentReadyState: { type: String, default: 'notready' },
    // Pass 239: новые props для иконки без per-row readiness.
    premium: { type: Boolean, default: false },
    inAir: { type: Boolean, default: false },
    flagFree: { type: Boolean, default: false },
    hasSubscription: { type: Boolean, default: false },
  },
  computed: {
    contentReady() {
      // Defensive default: 'loading' → false (FR-017).
      return this.contentReadyState === 'ready'
    },
    isActive() {
      return (
        this.contentReady && (this.inAir || this.flagFree || this.premium || this.hasSubscription)
      )
    },
    isDemo() {
      return this.contentReady && !this.isActive
    },
    isDisabled() {
      return !this.contentReady
    },
  },
  methods: {
    onOpen() {
      openPlayer(this.songId)
    },
  },
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
  to {
    transform: rotate(360deg);
  }
}
</style>
