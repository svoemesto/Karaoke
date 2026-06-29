<template>
  <div ref="container" style="position:fixed;top:0;left:0;width:100vw;height:100vh;background:#000;overflow:hidden"></div>
</template>

<script setup>
import { onMounted, onBeforeUnmount, ref } from 'vue'
import { useRoute } from 'vue-router'
import KaraokePlayer from '../player/KaraokePlayer.js'

const route = useRoute()
const container = ref(null)
let player = null

onMounted(() => {
  player = new KaraokePlayer(container.value, route.params.id, '/api')
  player.init()
})

onBeforeUnmount(() => {
  player?.destroy()
})
</script>
