<template>
  <div class="social-links">
    <a v-for="link in links" :key="link.name" href="#" class="social-link" @click.prevent="open(link)">
      <SvgIcon :name="link.icon" :active="true" :size="80" />
    </a>
  </div>
</template>

<script>
import { trackLinkToSocialNetwork } from '../services/tracking'
import SvgIcon from './SvgIcon.vue'

const links = [
  { name: 'vkgroup',  icon: 'vkgroup', url: 'https://vk.com/svoemestokaraoke' },
  { name: 'sponsr',   icon: 'sponsr',  url: 'https://sponsr.ru/smkaraoke' },
  { name: 'dzen',     icon: 'dzen',    url: 'https://dzen.ru/svoemesto' },
  { name: 'vkvideo',  icon: 'vk',      url: 'https://vkvideo.ru/video/@nsasvoemesto' },
  { name: 'tg',       icon: 'tg',      url: 'https://t.me/svoemestokaraoke' },
  { name: 'max',      icon: 'max',     url: 'https://max.ru/join/hYGH-mbcExUtzP5o4zq38uwb0xL9iwL80uSeEBO7Bu0' }
]

export default {
  name: 'SocialLinks',
  components: { SvgIcon },
  data() {
    return { links }
  },
  methods: {
    open(link) {
      trackLinkToSocialNetwork(link.name)
      window.open(link.url, '_blank')
    }
  }
}
</script>

<style scoped>
.social-links {
  display: flex;
  gap: 0;
  justify-content: center;
  flex-wrap: wrap;
}
.social-link {
  display: inline-block;
  width: 80px;
  height: 80px;
  cursor: pointer;
}
</style>
