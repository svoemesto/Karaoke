<template>
  <div ref="container" style="position:fixed;top:0;left:0;width:100vw;height:100vh;background:#000;overflow:hidden"></div>
</template>

<script setup>
import { onMounted, onBeforeUnmount, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import KaraokePlayer from '../player/KaraokePlayer.js'
import { useAuth } from '../composables/useAuth'

const route = useRoute()
const router = useRouter()
const container = ref(null)
let player = null
const { token: authToken } = useAuth()

onMounted(() => {
  const songId = route.params.id
  const token = sessionStorage.getItem(`kp_token_${songId}`)
  if (!token) {
    // No token in this browser session — behave like the route doesn't exist rather than
    // hinting that a hidden unlock mechanism exists.
    router.replace('/')
    return
  }
  // authToken (km_auth_token, site login) is sent along so the backend can resolve a live premium
  // status for playerdata's canExport — without it the "Экспорт аудио..." menu never appears, even
  // for a logged-in premium visitor, since the song-scoped player token alone carries no identity.
  player = new KaraokePlayer(container.value, songId, '/api/public/player', token, authToken.value)
  player.init()
})

onBeforeUnmount(() => {
  player?.destroy()
})
</script>
