<template>
  <div v-if="!isHomePage" :class="isModern ? 'modernScreen' : 'nonHomeScreen'">
    <div :class="isModern ? '' : 'nonHomeBody'">
      <router-view />
    </div>
  </div>
  <router-view v-else />
</template>

<script>
import { useDesign } from './composables/useDesign'

export default {
  name: 'App',
  setup() {
    const { design } = useDesign()
    return { design }
  },
  computed: {
    isHomePage() {
      return this.$route.path === '/'
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
