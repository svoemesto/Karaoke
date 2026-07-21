<template>
  <a v-if="linkValue" href="#" class="platform-icon" :title="linkName" @click.prevent="open">
    <SvgIcon :name="iconName" :active="true" :size="20" />
  </a>
  <span v-else class="platform-icon disabled" :title="linkName">
    <SvgIcon :name="iconName" :active="false" :size="20" />
  </span>
</template>

<script>
import { trackLinkToSong } from '../services/tracking'
import SvgIcon from './SvgIcon.vue'

const iconMap = {
  sponsr: 'sponsr',
  dzen: 'dzen',
  vk: 'vk',
  tg: 'tg',
  max: 'max',
  vkgroup: 'vkgroup',
  boosty: 'boosty',
  pl: 'pl',
}

/**
 * Компонент «Platform Link».
 *
 * @see AGENTS.md
 */

export default {
  name: 'PlatformLink',
  components: { SvgIcon },
  props: {
    linkName: { type: String, required: true },
    linkValue: { type: String, default: '' },
    songId: { type: [Number, String], required: true },
    songVersion: { type: String, required: true },
  },
  computed: {
    iconName() {
      return iconMap[this.linkName] || this.linkName
    },
  },
  methods: {
    open() {
      trackLinkToSong(this.linkName, this.songId, this.songVersion)
      window.open(this.linkValue, '_blank')
    },
  },
}
</script>
