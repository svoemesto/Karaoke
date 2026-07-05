<template>
  <ZakromaModern v-if="design === 'modern'" />
  <ZakromaClassic v-else />
</template>

<script>
import { useDesign } from '../composables/useDesign'
import { useEngagementTracking } from '../composables/useEngagementTracking'
import ZakromaClassic from './classic/ZakromaClassic.vue'
import ZakromaModern  from './modern/ZakromaModern.vue'

export default {
  name: 'ZakromaView',
  components: { ZakromaClassic, ZakromaModern },
  setup() { useEngagementTracking('zakroma'); return useDesign() }
}
</script>
