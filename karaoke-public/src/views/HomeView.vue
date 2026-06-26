<template>
  <div id="areaScreen">
    <div id="areaBody">
      <table style="width: 400px; display: block">
        <tr>
          <td colspan="6" style="width: 400px">
            <img src="/KARAOKE_LOGO.png" style="width: 400px; display: block; margin: auto" alt="Karaoke logo" />
          </td>
        </tr>

        <tr v-if="!isLoading">
          <td colspan="6" style="width: 400px">
            <div style="text-align: center">
              <span style="color: white; font-size: x-large">Песен в коллекции: </span>
              <span style="color: #EC8888; font-size: x-large; font-weight: bolder">{{ onSponsr }}</span>
            </div>
          </td>
        </tr>
        <tr v-if="!isLoading">
          <td colspan="6" style="width: 400px">
            <div style="text-align: center">
              <span style="color: white; font-size: x-large">Песен в открытом доступе: </span>
              <span style="color: #EC8888; font-size: x-large; font-weight: bolder">{{ onAir }}</span>
            </div>
          </td>
        </tr>
        <tr v-if="!isLoading">
          <td colspan="6" style="width: 400px">
            <div style="text-align: center">
              <span style="color: white; font-size: x-large">Эксклюзивно на SPONSR: </span>
              <span style="color: #EC8888; font-size: x-large; font-weight: bolder">{{ exclusive }}</span>
            </div>
          </td>
        </tr>

        <tr>
          <td colspan="6" style="width: 400px">
            <div style="text-align: center">
              <span style="color: white; font-size: medium">
                Каждый день в открытый доступ (в «эфир») выходит до 10 песен на 5-х площадках:
                Sponsr, Dzen, VK, Max и Telegram.
              </span>
            </div>
          </td>
        </tr>
        <tr>
          <td colspan="6" style="width: 400px">
            <div style="text-align: center">
              <span style="color: white; font-size: medium">
                Вся коллекция доступна по подпискам на Sponsr.
              </span>
            </div>
          </td>
        </tr>
        <tr>
          <td colspan="6" style="width: 400px">
            <div style="text-align: center">
              <span style="color: #9fcdff; font-size: medium">
                Каталог песен по исполнителям «Закрома»:
              </span>
            </div>
          </td>
        </tr>
        <tr>
          <td colspan="6" style="width: 400px">
            <RouterLink to="/zakroma" style="width: 400px; display: block; margin: auto; text-align: center; font-size: 48pt">Закрома</RouterLink>
          </td>
        </tr>
        <tr>
          <td colspan="5" style="width: 400px">
            <div style="text-align: center">
              <span style="color: #9fcdff; font-size: medium">
                Поиск по исполнителю, названию и тексту песни:
              </span>
            </div>
          </td>
        </tr>
        <tr>
          <td colspan="6" style="width: 400px">
            <RouterLink to="/filter" style="width: 400px; display: block; margin: auto; text-align: center; font-size: 48pt">Поиск песен</RouterLink>
          </td>
        </tr>
        <tr>
          <td colspan="6" style="width: 400px">
            <div style="text-align: center">
              <span style="color: #9fcdff; font-size: medium">Соцсети:</span>
            </div>
          </td>
        </tr>
        <tr>
          <td v-for="link in socialLinks" :key="link.name" style="width: 80px; height: 80px">
            <div style="cursor: pointer" @click="openLink(link)">
              <SvgIcon :name="link.icon" :active="true" :size="80" style="height: 150px" />
            </div>
          </td>
        </tr>
      </table>
    </div>
  </div>
</template>

<script>
import { mapGetters, mapActions } from 'vuex'
import SvgIcon from '../components/SvgIcon.vue'
import { trackLinkToSocialNetwork } from '../services/tracking'

const socialLinks = [
  { name: 'vkgroup',  icon: 'vkgroup', url: 'https://vk.com/svoemestokaraoke' },
  { name: 'sponsr',   icon: 'sponsr',  url: 'https://sponsr.ru/smkaraoke' },
  { name: 'dzen',     icon: 'dzen',    url: 'https://dzen.ru/svoemesto' },
  { name: 'vkvideo',  icon: 'vk',      url: 'https://vkvideo.ru/video/@nsasvoemesto' },
  { name: 'tg',       icon: 'tg',      url: 'https://t.me/svoemestokaraoke' },
  { name: 'max',      icon: 'max',     url: 'https://max.ru/join/hYGH-mbcExUtzP5o4zq38uwb0xL9iwL80uSeEBO7Bu0' },
]

export default {
  name: 'HomeView',
  components: { SvgIcon },
  data() {
    return { socialLinks }
  },
  computed: {
    ...mapGetters('stats', ['onSponsr', 'onAir', 'exclusive', 'isLoading'])
  },
  mounted() {
    this.loadStats()
    document.title = 'Каraoke на «Своём Месте»'
  },
  methods: {
    ...mapActions('stats', ['loadStats']),
    openLink(link) {
      trackLinkToSocialNetwork(link.name)
      window.open(link.url, '_blank')
    }
  }
}
</script>

<style scoped>
#areaScreen {
  width: 100%;
  height: 100%;
  position: fixed;
  top: 0;
  left: 0;
  display: flex;
  align-items: center;
  align-content: center;
  justify-content: center;
  overflow: auto;
  background-color: black;
}

#areaBody {
  display: block;
  border: none;
}
</style>
