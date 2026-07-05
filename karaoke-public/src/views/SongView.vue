<template>
  <SongModern v-if="design === 'modern'" />
  <SongClassic v-else />
</template>

<script>
import { useRoute } from 'vue-router'
import { useDesign } from '../composables/useDesign'
import { useEngagementTracking } from '../composables/useEngagementTracking'
import SongClassic from './classic/SongClassic.vue'
import SongModern  from './modern/SongModern.vue'

export default {
  name: 'SongView',
  components: { SongClassic, SongModern },
  setup() {
    const route = useRoute()
    useEngagementTracking('song', () => route.query.id)
    return useDesign()
  }
}
</script>
