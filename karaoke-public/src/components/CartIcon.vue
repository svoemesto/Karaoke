<template>
  <a
    v-if="!hidden"
    href="#"
    class="cart-icon"
    :class="{ 'cart-icon-on': inCart }"
    :title="inCart ? 'Убрать из корзины' : 'Добавить в корзину'"
    @click.prevent="onClick"
    >🛒</a
  >
</template>

<script>
import { computed, getCurrentInstance, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuth } from '../composables/useAuth'
import { useCart } from '../composables/useCart'
import { CART_TOGGLE_RESULT, classifyCartToggle } from '../utils/cartToggle.js'
import { notify } from '../utils/notify.js'

/**
 * Компонент «Cart Icon».
 *
 * @see AGENTS.md
 */

export default {
  name: 'CartIcon',
  props: {
    songId: { type: [Number, String], required: true },
  },
  setup(props) {
    const router = useRouter()
    const route = useRoute()
    const instance = getCurrentInstance()
    const { token } = useAuth()
    const { isInCart, toggle } = useCart()
    const busy = ref(false)
    // BR-297: подписка уже оформлена (409 already_subscribed) — иконку корзины убираем,
    // иначе пользователь снова кликает по ней и снова получает отказ.
    const hidden = ref(false)

    const inCart = computed(() => isInCart(props.songId))

    async function onClick() {
      // Аноним — предлагаем войти (после входа вернём на текущую страницу), как FavoriteIcon.
      if (!token.value) {
        router.push({ path: '/login', query: { redirect: route.fullPath } })
        return
      }
      if (busy.value) return
      busy.value = true
      try {
        // BR-297 (аудит #336, PKG-S3-27): результат toggle РАЗБИРАЕТСЯ. Раньше
        // `await toggle(...)` шёл в try/finally без catch и без ветки на status — 409
        // already_subscribed / 400 song_not_for_subscription / 401 / 5xx оставляли иконку
        // в прежнем состоянии, и кнопка молча «не работала».
        const { status, body } = await toggle(props.songId)
        const outcome = classifyCartToggle(status, body)
        if (outcome.result !== CART_TOGGLE_RESULT.OK) {
          if (outcome.hideIcon) hidden.value = true
          notify(instance && instance.proxy, outcome.message, 'warning')
        }
      } catch (e) {
        notify(
          instance && instance.proxy,
          'Нет связи с сервером — корзина не изменена. Попробуйте ещё раз.',
          'error',
        )
      } finally {
        busy.value = false
      }
    }

    return { inCart, onClick, hidden }
  },
}
</script>

<style scoped>
.cart-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  line-height: 0;
  font-size: 15px;
  text-decoration: none;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  opacity: 0.35;
  filter: grayscale(1);
  transition:
    opacity 0.15s,
    filter 0.15s,
    transform 0.15s,
    background-color 0.15s,
    box-shadow 0.15s;
}
.cart-icon:hover {
  transform: scale(1.15);
  opacity: 0.6;
}
.cart-icon-on {
  opacity: 1;
  filter: none;
  background-color: rgba(124, 58, 237, 0.2);
  box-shadow: 0 0 0 1.5px #7c3aed inset;
}
.cart-icon-on:hover {
  opacity: 1;
  transform: scale(1.15);
}
</style>
