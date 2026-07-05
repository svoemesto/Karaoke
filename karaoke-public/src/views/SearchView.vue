<template>
  <SearchModern v-if="design === 'modern'" />
  <SearchClassic v-else />
</template>

<script>
import { useDesign } from '../composables/useDesign'
import { useEngagementTracking } from '../composables/useEngagementTracking'
import SearchClassic from './classic/SearchClassic.vue'
import SearchModern  from './modern/SearchModern.vue'

export default {
  name: 'SearchView',
  components: { SearchClassic, SearchModern },
  setup() { useEngagementTracking('search'); return useDesign() }
}
</script>
