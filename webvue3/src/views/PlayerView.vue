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
/**
 * Полноэкранный плеер караоке-видео в admin-SPA (Composition API, `<script setup>`).
 *
 * Инициализирует `KaraokePlayer` (см. `../player/KaraokePlayer.js`),
 * который загружает FLAC-стемы (vocals/accompaniment) и MP4-видео,
 * синхронизирует с текстом песни и аккордами, поддерживает режимы
 * KARAOKE (только acc) / LYRICS (acc+voc) / DEMO (с watermark).
 *
 * Использует route-параметры:
 * - `id` — ID песни (`Settings.id`).
 * - `assignmentId` (query, опционально) — ID черновика задания для ревью.
 * - `target` (query, вместе с assignmentId) — `local`/`remote`.
 * - `render=1` (query) — headless-режим для MP4-рендера через Playwright
 *   (см. `mp4-render.md`).
 *
 * @see docs/features/mp4-render.md
 * @see docs/features/premium-stems.md (доступ по подписке)
 */
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
