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
  const assignmentId = route.query.assignmentId
  // target (local|remote) — только вместе с assignmentId: сообщает бэкенду, откуда реально читать
  // черновик задания (см. ApiController.getSongPlayerData / ReviewModal.playerSrc).
  const options = assignmentId
    ? { songId: route.params.id, assignmentId, target: route.query.target || null }
    : route.params.id
  player = new KaraokePlayer(container.value, options, '/api')
  player.init()
})

onBeforeUnmount(() => {
  player?.destroy()
})
</script>
