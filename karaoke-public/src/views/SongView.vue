<template>
  <SongModern v-if="design === 'modern'" />
  <SongClassic v-else />
</template>

<script>
import { useDesign } from '../composables/useDesign'
import SongClassic from './classic/SongClassic.vue'
import SongModern  from './modern/SongModern.vue'

export default {
  name: 'SongView',
  components: { SongClassic, SongModern },
  setup() { return useDesign() }
}
</script>
