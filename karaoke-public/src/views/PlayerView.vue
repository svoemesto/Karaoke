<template>
  <div ref="container" style="position:fixed;top:0;left:0;width:100vw;height:100vh;background:#000;overflow:hidden"></div>
</template>

<script setup>
import { onMounted, onBeforeUnmount, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import KaraokePlayer from '../player/KaraokePlayer.js'

const route = useRoute()
const router = useRouter()
const container = ref(null)
let player = null

onMounted(() => {
  const songId = route.params.id
  const token = sessionStorage.getItem(`kp_token_${songId}`)
  if (!token) {
    // No token in this browser session — behave like the route doesn't exist rather than
    // hinting that a hidden unlock mechanism exists.
    router.replace('/')
    return
  }
  player = new KaraokePlayer(container.value, songId, '/api/public/player', token)
  player.init()
})

onBeforeUnmount(() => {
  player?.destroy()
})
</script>
