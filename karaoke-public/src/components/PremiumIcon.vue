<template>
  <!-- Монетка «премиум-контент» для НЕ-премиум посетителей. Готовность контента подгружается тем же
       асинхронным readiness-запросом, что и активность иконки плеера (см. usePlayerReadiness):
       пока не пришёл ответ — спиннер; затем золотая (контент готов — премиум смог бы открыть плеер
       прямо сейчас) или серебряная (ещё не готов — станет доступно позже) монетка. -->
  <span v-if="state === 'loading'" class="premium-icon-spinner" title="Проверка доступности…" />
  <span
    v-else-if="state === 'ready'"
    class="premium-icon"
    title="Доступно для премиум-пользователей"
  >
    <SvgIcon name="premium" :active="true" :size="18" />
  </span>
  <span
    v-else
    class="premium-icon"
    title="Будет доступно для премиум-пользователей в ближайшее время"
  >
    <SvgIcon name="premium" :active="false" :size="18" />
  </span>
</template>

<script>
import SvgIcon from './SvgIcon.vue'

export default {
  name: 'PremiumIcon',
  components: { SvgIcon },
  props: {
    // 'loading' | 'ready' | 'notready'
    state: { type: String, default: 'loading' }
  }
}
</script>

<style scoped>
.premium-icon {
  display: inline-flex;
  vertical-align: middle;
}
.premium-icon-spinner {
  display: inline-block;
  width: 13px;
  height: 13px;
  border: 2px solid #e0cfa0;
  border-top-color: #d99413;
  border-radius: 50%;
  vertical-align: middle;
  animation: premium-icon-spin 0.8s linear infinite;
}
@keyframes premium-icon-spin {
  to { transform: rotate(360deg); }
}
</style>
