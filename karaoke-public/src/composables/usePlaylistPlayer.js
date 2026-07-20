import { ref } from 'vue'
import { fetchPlayerToken } from '../services/playerLauncher'

// Мост к встроенному плееру /player/:id?pl=1 (iframe): очередь song_id, токены «точно в срок»
// (need-token), управление prev/next/toggle/режимы, текущий трек и состояние play, «широкий» режим.
// UI-независим — используется страницами плейлистов (плейлист автора и т.п.).
export function usePlaylistPlayer(iframeRef) {
  const started = ref(false)
  const firstSongId = ref(null)
  const isPlaying = ref(false)
  const currentSongId = ref(null)
  const playerWide = ref(false)

  function send(type, extra) {
    const win = iframeRef.value && iframeRef.value.contentWindow
    if (win) win.postMessage(Object.assign({ source: 'kp-playlist', type }, extra), '*')
  }

  async function onMessage(e) {
    const win = iframeRef.value && iframeRef.value.contentWindow
    if (e.source === win && e.data && e.data.source === 'kp-playlist-player') {
      const d = e.data
      if (d.type === 'need-token') {
        const { token } = await fetchPlayerToken(d.songId)
        if (token) sessionStorage.setItem(`kp_token_${d.songId}`, token)
        send('token', { songId: d.songId, token })
      } else if (d.type === 'track') {
        currentSongId.value = d.songId
      } else if (d.type === 'state') {
        isPlaying.value = !!d.playing
      }
      return
    }
    if (
      e.source === win &&
      e.data &&
      e.data.source === 'karaoke-player' &&
      e.data.type === 'display-mode'
    ) {
      playerWide.value = e.data.mode === 'page'
    }
  }

  // ids — упорядоченный список воспроизводимых song_id; modes — { continuous, repeatMode };
  // startId (опц.) — с какой песни начать (должна быть в ids), иначе с первой.
  async function start(ids, modes, startId) {
    if (!ids.length) return false
    const first = startId != null && ids.includes(startId) ? startId : ids[0]
    const { canWatch, token } = await fetchPlayerToken(first)
    if (!canWatch || !token) return false
    sessionStorage.setItem(`kp_token_${first}`, token)
    sessionStorage.setItem(
      'kp_pl_queue',
      JSON.stringify({
        ids,
        continuous: modes.continuous,
        repeatMode: modes.repeatMode,
      }),
    )
    firstSongId.value = first
    currentSongId.value = first
    started.value = true
    return true
  }

  function pushQueue(ids) {
    if (started.value) send('setqueue', { ids })
  }
  function pushModes(modes) {
    send('setmodes', { continuous: modes.continuous, repeatMode: modes.repeatMode })
  }
  function prev() {
    if (started.value) send('prev')
  }
  function next() {
    if (started.value) send('next')
  }
  function toggle() {
    send('toggle')
  }

  // Начать/переключить воспроизведение с конкретной песни (клик по строке списка).
  async function playFrom(songId, ids, modes) {
    if (started.value) send('playid', { songId })
    else await start(ids, modes, songId)
  }

  function mount() {
    window.addEventListener('message', onMessage)
  }
  function unmount() {
    window.removeEventListener('message', onMessage)
    sessionStorage.removeItem('kp_pl_queue')
  }

  return {
    started,
    firstSongId,
    isPlaying,
    currentSongId,
    playerWide,
    start,
    playFrom,
    pushQueue,
    pushModes,
    prev,
    next,
    toggle,
    mount,
    unmount,
  }
}
