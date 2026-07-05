<template>
  <HomeModern v-if="design === 'modern'" />
  <HomeClassic v-else />
</template>

<script>
import { useDesign } from '../composables/useDesign'
import { useEngagementTracking } from '../composables/useEngagementTracking'
import HomeClassic from './classic/HomeClassic.vue'
import HomeModern  from './modern/HomeModern.vue'

export default {
  name: 'HomeView',
  components: { HomeClassic, HomeModern },
  setup() { useEngagementTracking('home'); return useDesign() }
}
</script>
