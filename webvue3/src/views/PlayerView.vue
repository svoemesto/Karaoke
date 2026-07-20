<template>
  <div
    ref="container"
    style="
      position: fixed;
      top: 0;
      left: 0;
      width: 100vw;
      height: 100vh;
      background: #000;
      overflow: hidden;
    "
  />
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
  // Headless mp4-рендер (см. DEVELOPMENT.md "Рендер видео MP4 из онлайн-плеера"): Kotlin/Playwright-
  // оркестратор открывает эту страницу с ?render=1 и управляет плеером извне через window.__kp
  // (page.evaluate), дёргая renderFrameAt(dt) покадрово — вместо обычного RAF-воспроизведения.
  if (route.query.render === '1') {
    player._offline = true
    window.__kp = player
  }
  player.init()
})

onBeforeUnmount(() => {
  if (window.__kp === player) delete window.__kp
  player?.destroy()
})
</script>
