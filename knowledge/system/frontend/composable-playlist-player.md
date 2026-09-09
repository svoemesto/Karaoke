# Composable: usePlaylistPlayer

> **Домен**: system (frontend)
> **Компонента**: `karaoke-public/src/composables/usePlaylistPlayer.js`
> — мост к встроенному плееру через iframe.

## Файл

`karaoke-public/src/composables/usePlaylistPlayer.js` (112 строк)

## Назначение

**Мост к встроенному плееру** `/player/:id?pl=1` (iframe): очередь
`song_id`, токены «точно в срок» (`need-token`), управление
prev/next/toggle/режимы, текущий трек и состояние play, «широкий»
режим.

**UI-независимый** — используется страницами плейлистов (плейлист
автора и т.п.).

## State

```javascript
const started = ref(false)         // плеер инициализирован
const firstSongId = ref(null)      // первый ID в очереди
const isPlaying = ref(false)       // play/pause
const currentSongId = ref(null)    // текущий трек
const playerWide = ref(false)      // широкий режим
```

## Логика (postMessage)

```javascript
function send(type, extra) {
  const win = iframeRef.value && iframeRef.value.contentWindow
  if (win) win.postMessage({ source: 'kp-playlist', type, ...extra }, '*')
}

async function onMessage(e) {
  // Только сообщения от нашего iframe
  if (e.source === win && e.data?.source === 'kp-playlist-player') {
    const d = e.data
    if (d.type === 'need-token') {
      // Плеер запросил токен для текущей песни
      const { token } = await fetchPlayerToken(d.songId)
      if (token) sessionStorage.setItem(`kp_token_${d.songId}`, token)
      send('token', { songId: d.songId, token })
    } else if (d.type === 'track') {
      currentSongId.value = d.songId
    } else if (d.type === 'state') {
      isPlaying.value = !!d.playing
    }
  }
}
```

## Hot paths

- **`/api/public/player/token?{id}`** — `fetchPlayerToken(songId)`.
- `postMessage` между parent и iframe (см. [usePlayerAccess](composable-use-player-access.md)).

## Связь

- **PlayerAccess** ([composable-use-player-access.md](composable-use-player-access.md)) —
  похожий flow для одиночной песни.
- **KaraokePlayer.js** — обработчик сообщений от плейлиста.

## Changelog

- **Pass 402** (2026-09-09): Initial. Автор: agent (Karaoke).