<template>
  <router-view v-if="isPlayerPage" />
  <template v-else>
    <div v-if="!isHomePage" class="modernScreen">
      <router-view />
    </div>
    <router-view v-else />
    <footer class="km-global-footer">
      <RouterLink to="/oferta">Оферта</RouterLink>
    </footer>
  </template>
  <PremiumUpsellModal />
  <ChatUnreadBadge />
  <NewsBell />
</template>

<script>
import PremiumUpsellModal from './components/PremiumUpsellModal.vue'
import ChatUnreadBadge from './components/ChatUnreadBadge.vue'
import NewsBell from './components/NewsBell.vue'

/**
 * Корневой компонент приложения.
 *
 * @see AGENTS.md
 */

export default {
  name: 'App',
  components: { PremiumUpsellModal, ChatUnreadBadge, NewsBell },
  computed: {
    isHomePage() {
      return this.$route.path === '/'
    },
    // The player is fullscreen/fixed-position and owns the whole viewport — it must not sit inside
    // the .modernScreen wrapper, same as webvue3's App.vue excludes /player/* from its sidebar layout.
    isPlayerPage() {
      return this.$route.name === 'player'
    },
  },
}
</script>

<style>
.modernScreen {
  min-height: 100vh;
  background: var(--km-bg, #0f0f1a);
}
.km-global-footer {
  text-align: center;
  padding: 1rem;
  font-size: 0.8rem;
}
.km-global-footer a {
  color: var(--km-text2, #888);
  text-decoration: none;
}
.km-global-footer a:hover {
  text-decoration: underline;
}
</style>
