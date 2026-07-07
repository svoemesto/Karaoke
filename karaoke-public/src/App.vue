<template>
  <router-view v-if="isPlayerPage" />
  <div v-else-if="!isHomePage" class="modernScreen">
    <router-view />
  </div>
  <router-view v-else />
  <PremiumUpsellModal />
</template>

<script>
import PremiumUpsellModal from './components/PremiumUpsellModal.vue'

export default {
  name: 'App',
  components: { PremiumUpsellModal },
  computed: {
    isHomePage() {
      return this.$route.path === '/'
    },
    // The player is fullscreen/fixed-position and owns the whole viewport — it must not sit inside
    // the .modernScreen wrapper, same as webvue3's App.vue excludes /player/* from its sidebar layout.
    isPlayerPage() {
      return this.$route.name === 'player'
    }
  }
}
</script>

<style>
.modernScreen {
  min-height: 100vh;
  background: var(--km-bg, #0f0f1a);
}
</style>
