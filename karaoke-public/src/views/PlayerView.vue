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

// --- Режим плейлиста ---------------------------------------------------------------------------
// Когда /player/:id открыт с ?pl=1 (в iframe на странице редактора плейлиста), плеер сам ведёт
// очередь: по окончании трека грузит следующий (playSong в том же аудио-контексте — воспроизведение
// не блокируется, iframe имеет allow="autoplay"). Токен на каждую песню запрашивается у родителя
// «точно в срок» (need-token) — не выдаём 500 токенов заранее и не засоряем статистику. prev/next/
// режимы/пауза приходят от родителя через postMessage. Плеер выглядит и управляется как на странице песни.
const isPlaylist = route.query.pl === '1'
let queue = []               // songId[]
let pos = 0
let modes = { continuous: true, repeatMode: 'none' }
const tokenCache = {}        // songId -> token
const pendingToken = {}      // songId -> [resolve...]

function postToParent(msg) {
  try { window.parent.postMessage(Object.assign({ source: 'kp-playlist-player' }, msg), '*') } catch (e) { /* ignore */ }
}

function requestToken(songId) {
  if (tokenCache[songId]) return Promise.resolve(tokenCache[songId])
  return new Promise(resolve => {
    ;(pendingToken[songId] = pendingToken[songId] || []).push(resolve)
    postToParent({ type: 'need-token', songId })
    setTimeout(() => {
      const arr = pendingToken[songId]
      if (arr) { delete pendingToken[songId]; arr.forEach(r => r(null)) }
    }, 8000)
  })
}

async function playPos(p) {
  if (p < 0 || p >= queue.length) return
  pos = p
  const songId = queue[pos]
  const token = await requestToken(songId)
  if (!token) { advanceAfterEnd(); return }   // недоступна (истёк токен и т.п.) — дальше
  await player.playSong(songId, token, authToken.value, true)
  postToParent({ type: 'track', songId })
  postToParent({ type: 'state', playing: true })
}

function advanceAfterEnd() {
  if (modes.repeatMode === 'one') { playPos(pos); return }
  const nextP = pos + 1
  if (nextP < queue.length) {
    if (modes.continuous) playPos(nextP)
    else postToParent({ type: 'state', playing: false })
  } else if (modes.repeatMode === 'all') {
    if (queue.length) playPos(0)
  } else {
    postToParent({ type: 'state', playing: false })
  }
}

function onParentMessage(e) {
  const d = e.data
  if (!d || d.source !== 'kp-playlist') return
  if (d.type === 'token') {
    if (d.token) tokenCache[d.songId] = d.token
    const arr = pendingToken[d.songId]
    if (arr) { delete pendingToken[d.songId]; arr.forEach(r => r(d.token || null)) }
  } else if (d.type === 'playid') { const p = queue.findIndex(sid => String(sid) === String(d.songId)); if (p >= 0) playPos(p) }
  else if (d.type === 'next') { let n = pos + 1; if (n >= queue.length) n = 0; playPos(n) }
  else if (d.type === 'prev') { let n = pos - 1; if (n < 0) n = queue.length - 1; playPos(n) }
  else if (d.type === 'toggle') { player.togglePlay(); postToParent({ type: 'state', playing: !!player.isPlaying }) }
  else if (d.type === 'setmodes') { modes.continuous = d.continuous; modes.repeatMode = d.repeatMode }
  else if (d.type === 'setqueue') {
    queue = d.ids || []
    const cur = queue.findIndex(sid => String(sid) === String(queue[pos]))
    if (cur >= 0) pos = cur
    else pos = Math.min(pos, Math.max(0, queue.length - 1))
  }
}

onMounted(() => {
  const songId = route.params.id
  const token = sessionStorage.getItem(`kp_token_${songId}`)
  if (!token) {
    // Нет токена в этой сессии — ведём себя как несуществующий роут (не палим скрытый механизм).
    router.replace('/')
    return
  }

  if (isPlaylist) {
    try {
      const raw = sessionStorage.getItem('kp_pl_queue')
      const data = raw ? JSON.parse(raw) : null
      if (data && Array.isArray(data.ids)) {
        queue = data.ids
        modes.continuous = data.continuous !== false
        modes.repeatMode = data.repeatMode || 'none'
      }
    } catch (e) { /* ignore */ }
    if (!queue.length) queue = [Number(songId) || songId]
    pos = queue.findIndex(sid => String(sid) === String(songId))
    if (pos < 0) pos = 0
    tokenCache[queue[pos]] = token
    window.addEventListener('message', onParentMessage)

    player = new KaraokePlayer(container.value, queue[pos], '/api/public/player', token, authToken.value)
    player.onTrackEnded = advanceAfterEnd
    player.init().then(() => {
      player.play()                                  // iframe allow="autoplay" → первый трек стартует
      postToParent({ type: 'track', songId: queue[pos] })
      postToParent({ type: 'state', playing: true })
    })
    return
  }

  // authToken (km_auth_token) шлётся, чтобы бэкенд определил живой премиум-статус для canExport
  // в playerdata — иначе пункт «Экспорт аудио...» не появится даже у залогиненного премиума.
  player = new KaraokePlayer(container.value, songId, '/api/public/player', token, authToken.value)
  player.init()
})

onBeforeUnmount(() => {
  if (isPlaylist) window.removeEventListener('message', onParentMessage)
  player?.destroy()
})
</script>
