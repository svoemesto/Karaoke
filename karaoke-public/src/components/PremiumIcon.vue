<template>
  <!-- Монетка «премиум-контент» для НЕ-премиум посетителей. Готовность контента подгружается тем же
       асинхронным readiness-запросом, что и активность иконки плеера (см. usePlayerReadiness):
       пока не пришёл ответ — спиннер; затем золотая (контент готов — премиум смог бы открыть плеер
       прямо сейчас) или серебряная (ещё не готов — станет доступно позже) монетка. Золотая монетка
       дополнительно кликабельна ровно тогда, когда для песни разрешена отдельная подписка (clickable,
       тот же признак, что и у соседней иконки корзины) — мгновенное оформление подписки на одну
       песню, в обход демо-режима (см. PlayerIcon.vue — его золотая иконка теперь ведёт в демо-плеер,
       а не сразу сюда). -->
  <span v-if="state === 'loading'" class="premium-icon-spinner" title="Проверка доступности…" />
  <a
    v-else-if="state === 'ready' && clickable"
    href="#"
    class="premium-icon premium-icon-clickable"
    title="Оформить подписку на эту песню"
    @click.prevent="$emit('subscribe')"
  >
    <SvgIcon name="premium" :active="true" :size="18" />
  </a>
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

/**
 * Компонент «Premium Icon».
 *
 * @emits subscribe
 *
 * @see AGENTS.md
 */

export default {
  name: 'PremiumIcon',
  components: { SvgIcon },
  props: {
    // 'loading' | 'ready' | 'notready'
    state: { type: String, default: 'loading' },
    // Разрешена ли отдельная подписка на эту песню (id_tariff !== -1 у автора) И контент готов
    // И зритель ещё не смотрит бесплатно — та же формула, что и у CartIcon рядом.
    clickable: { type: Boolean, default: false },
  },
  emits: ['subscribe'],
}
</script>

<style scoped>
.premium-icon {
  display: inline-flex;
  vertical-align: middle;
}
.premium-icon-clickable {
  cursor: pointer;
  text-decoration: none;
}
.premium-icon-clickable:hover {
  filter: brightness(1.25);
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
  to {
    transform: rotate(360deg);
  }
}
</style>
