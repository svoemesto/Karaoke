<template>
  <a
    href="#"
    class="cart-icon"
    :class="{ 'cart-icon-on': inCart }"
    :title="inCart ? 'Убрать из корзины' : 'Добавить в корзину'"
    @click.prevent="onClick"
    >🛒</a
  >
</template>

<script>
import { computed, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuth } from '../composables/useAuth'
import { useCart } from '../composables/useCart'

export default {
  name: 'CartIcon',
  props: {
    songId: { type: [Number, String], required: true },
  },
  setup(props) {
    const router = useRouter()
    const route = useRoute()
    const { token } = useAuth()
    const { isInCart, toggle } = useCart()
    const busy = ref(false)

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
        await toggle(props.songId)
      } finally {
        busy.value = false
      }
    }

    return { inCart, onClick }
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
