<template>
  <router-view v-if="isPlayerPage" />
  <div v-else-if="!isHomePage" :class="isModern ? 'modernScreen' : 'nonHomeScreen'">
    <div :class="isModern ? '' : 'nonHomeBody'">
      <router-view />
    </div>
  </div>
  <router-view v-else />
  <PremiumUpsellModal />
</template>

<script>
import { useDesign } from './composables/useDesign'
import PremiumUpsellModal from './components/PremiumUpsellModal.vue'

export default {
  name: 'App',
  components: { PremiumUpsellModal },
  setup() {
    const { design } = useDesign()
    return { design }
  },
  computed: {
    isHomePage() {
      return this.$route.path === '/'
    },
    // The player is fullscreen/fixed-position and owns the whole viewport — it must not sit inside
    // the classic (.nonHomeScreen/.nonHomeBody, padded+centered) or modern (.modernScreen) wrapper,
    // same as webvue3's App.vue excludes /player/* from its sidebar layout.
    isPlayerPage() {
      return this.$route.name === 'player'
    },
    isModern() {
      return this.design === 'modern'
    }
  }
}
</script>

<style>
.nonHomeScreen {
  padding: 1em;
  display: flex;
  min-height: 100vh;
  flex-direction: column;
  margin: 0;
}
.nonHomeBody {
  align-self: center;
  padding: 5px;
  display: flex;
}
.modernScreen {
  min-height: 100vh;
  background: var(--km-bg, #0f0f1a);
}
</style>
