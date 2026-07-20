// Чистая логика караоке-разметки, портированная из karaoke-public useKaraokeEditor.js (идентичный
// набор: слоги + концы/новые строки + END + комментарии и группы голоса). Никакой завязки на
// WaveSurfer — оперирует простыми объектами маркеров { uid, time, label, color, position,
// markertype }. Формат маркеров совместим с публичным редактором karaoke-public и с admin-редактором.

// Цвета слоговых маркеров и служебных типов (1:1 с karaoke-public). Используются только при
// создании нового маркера через addMarker; если маркеры пришли с сервера с уже заданным цветом —
// этот цвет сохраняется (см. relabelSyllables).
const MARKER_COLOR_SYLLABLES = '#D2691E'
const MARKER_COLOR_FIRSTSYLLABLE = '#008000'
const MARKER_COLOR_ENDOFLINE = '#FF0000'
const MARKER_COLOR_NEWLINE = '#FF0000'
const MARKER_COLOR_ENDOFSYLLABLE = '#99004C'
const MARKER_COLOR_END = '#000080'
const MARKER_COLOR_SETTING = '#000080'

// ─── Локальная персистентность настроек редактора ─────────────────────────────────────────────
//
// Зеркало _loadPersistedSettings/_savePersistedSettings из KaraokePlayer.js — чтобы редактор
// запоминал размеры шрифтов, громкость, скорость, масштаб, активный стем, видимость клавиатуры
// между сессиями (per-user, per-browser, через localStorage). Ключ ОТДЕЛЬНЫЙ от плеерского
// (`karaoke-player-settings`), чтобы настройки редактора и плеера не пересекались.
//
// localStorage может быть недоступен (приватный режим / квота) — обе функции тихо ничего не
// делают и возвращают дефолты (load) либо частичное сохранение (save).

const EDITOR_SETTINGS_LS_KEY = 'karaoke-editor-settings'

export const EDITOR_DEFAULTS = Object.freeze({
  textFontSize: 16,
  previewFontSize: 18,
  volume: 1,
  playbackRate: 0.75,
  zoom: 100,
  activeSound: 'voice',
  showKeyboard: false,
})

export function loadEditorSettings() {
  try {
    const raw = localStorage.getItem(EDITOR_SETTINGS_LS_KEY)
    if (!raw) return { ...EDITOR_DEFAULTS }
    const s = JSON.parse(raw) || {}
    const clamp = (v, lo, hi) => (Number.isFinite(v) ? Math.min(hi, Math.max(lo, v)) : null)
    return {
      textFontSize: clamp(s.textFontSize, 6, 36) ?? EDITOR_DEFAULTS.textFontSize,
      previewFontSize: clamp(s.previewFontSize, 6, 36) ?? EDITOR_DEFAULTS.previewFontSize,
      volume: clamp(s.volume, 0, 1) ?? EDITOR_DEFAULTS.volume,
      playbackRate: clamp(s.playbackRate, 0.3, 1) ?? EDITOR_DEFAULTS.playbackRate,
      zoom: clamp(s.zoom, 20, 400) ?? EDITOR_DEFAULTS.zoom,
      activeSound: s.activeSound === 'music' ? 'music' : EDITOR_DEFAULTS.activeSound,
      showKeyboard:
        typeof s.showKeyboard === 'boolean' ? s.showKeyboard : EDITOR_DEFAULTS.showKeyboard,
    }
  } catch (e) {
    return { ...EDITOR_DEFAULTS }
  }
}

export function saveEditorSettings(partial) {
  try {
    const current = loadEditorSettings()
    localStorage.setItem(EDITOR_SETTINGS_LS_KEY, JSON.stringify({ ...current, ...partial }))
  } catch (e) {
    /* no-op */
  }
}

let uidCounter = 1
export function nextUid() {
  return `m${uidCounter++}`
}

function uppercaseFirstLetter(s) {
  return s && s.length ? s.charAt(0).toUpperCase() + s.slice(1) : s
}

// Слогоделение (точная копия getSyllables из SubsEdit.vue и karaoke-public): разбивает текст на
// слоги регэкспом по гласным (рус/лат), последний слог слова помечается суффиксом '_'. Слоги без
// гласной приклеиваются к соседям.
export function splitSyllables(sourceText) {
  const result = []
  const words = sourceText.match(/\S+/gi) || []
  for (let i = 0; i < words.length; i++) {
    const word = words[i]
    const syllables = word
      .replace(
        /[ЙЦКНГШЩЗХЪФВПРЛДЖЧСМТЬБQWRTYPSDFGHJKLZXCVBNM-]*[ЁУЕЫАОЭЯИЮEUIOAїієѣ][ЙЦКНГШЩЗХЪФВПРЛДЖЧСМТЬБQWRTYPSDFGHJKLZXCVBNM-]*?(?=[ЦКНГШЩЗХФВПРЛДЖЧСМТБQWRTYPSDFGHJKLZXCVBNM-]?[ЁУЕЫАОЭЯИЮEUIOAїієѣ]|[Й|Y][АИУЕОEUIOAїієѣ])/gi,
        '$& ',
      )
      .split(' ')
    if (syllables.length === 0) {
      result.push(word + '_')
    } else {
      for (let j = 0; j < syllables.length; j++) {
        result.push(syllables[j] + (j === syllables.length - 1 ? '_' : ''))
      }
    }
  }
  for (let i = 0; i < result.length; i++) {
    const word = result[i]
    let haveVowel = false
    for (let j = 0; j < word.length; j++) {
      if ('ЁУЕЫАОЭЯИЮёуеыаоэяиюEUIOAeuioaїієѣ'.includes(word[j])) {
        haveVowel = true
        break
      }
    }
    if (!haveVowel) {
      if (i === result.length - 1 || (word === '-_' && i !== 0)) {
        result[i - 1] = result[i - 1] + word
        result.splice(i, 1)
        i--
      } else if (i < result.length - 2) {
        result[i + 1] = word + result[i + 1]
        result.splice(i, 1)
        i--
      }
    }
  }
  return result
}

// Стабильная сортировка маркеров по времени, затем по типу — как в admin-редакторе и karaoke-public.
export function sortMarkers(markers) {
  markers.sort((a, b) => {
    if (a.time > b.time) return 1
    if (a.time < b.time) return -1
    if (a.markertype > b.markertype) return 1
    if (a.markertype < b.markertype) return -1
    return 0
  })
  return markers
}

// Индекс текущего слогового маркера по времени — для подсветки текущего слога.
export function currentSyllableIndex(markers, currentTime) {
  const syl = markers.filter((m) => m.markertype === 'syllables')
  const diff = 0.02
  if (syl.length > 0 && currentTime < syl[0].time - diff) return -1
  for (let i = 0; i < syl.length - 1; i++) {
    if (currentTime >= syl[i].time - diff && currentTime < syl[i + 1].time - diff) return i
  }
  return syl.length - 1
}

// Индекс текущего маркера среди всех — для подсветки в тексте разметки.
export function currentMarkerIndex(markers, currentTime) {
  const diff = 0.02
  if (markers.length > 0 && currentTime < markers[0].time - diff) return -1
  for (let i = 0; i < markers.length - 1; i++) {
    if (currentTime >= markers[i].time - diff && currentTime < markers[i + 1].time - diff) return i
  }
  return markers.length - 1
}

// Переназначает label/color слоговым маркерам по массиву слогов. i-й syllables-маркер получает
// i-й слог; первый слог строки (после endofline) — зелёный, остальные — охра. Лишние маркеры
// зачищаются.
export function relabelSyllables(markers, syllables) {
  sortMarkers(markers)
  let index = 0
  let color = MARKER_COLOR_FIRSTSYLLABLE
  for (let i = 0; i < markers.length; i++) {
    const marker = markers[i]
    if (marker.markertype === 'syllables') {
      if (index >= syllables.length) {
        marker.label = ''
      } else {
        marker.label = syllables[index]
        marker.color = color
      }
      index++
      color = MARKER_COLOR_SYLLABLES
    } else if (marker.markertype === 'endofline') {
      color = MARKER_COLOR_FIRSTSYLLABLE
    }
  }
  sortMarkers(markers)
  return markers
}

// Добавляет маркер в момент currentTime. Если попадает почти точно на существующий маркер (diff <
// 0.002) — заменяет (как в SubsEdit.vue), КРОМЕ notDelete=true (для комбо-хоткеев Digit3/Digit5,
// которые добавляют несколько маркеров подряд в ОДНУ currentTime). label — только для
// 'setting' (GROUP|0..4, COMMENT|текст).
export function addMarker(
  markers,
  syllables,
  markerType,
  currentTime,
  notDelete = false,
  label = '',
) {
  const cmi = currentMarkerIndex(markers, currentTime)
  const currentMarkerTime = cmi >= 0 ? markers[cmi].time : 0
  const diff = Math.abs(currentMarkerTime - currentTime)

  let color = MARKER_COLOR_SYLLABLES
  let position = 'bottom'
  if (markerType === 'endofline') {
    color = MARKER_COLOR_ENDOFLINE
  } else if (markerType === 'newline') {
    color = MARKER_COLOR_NEWLINE
  } else if (markerType === 'endofsyllable') {
    color = MARKER_COLOR_ENDOFSYLLABLE
  } else if (markerType === 'setting') {
    color = MARKER_COLOR_SETTING
    position = 'top'
  }

  const newMarker = {
    uid: nextUid(),
    time: currentTime,
    label: markerType === 'setting' ? label : '',
    color,
    position,
    markertype: markerType,
  }
  const shouldReplace = diff < 0.002 && !notDelete
  const indexToInsert = cmi + (shouldReplace ? 0 : 1)
  const countDeleted = shouldReplace ? 1 : 0
  markers.splice(indexToInsert, countDeleted, newMarker)
  relabelSyllables(markers, syllables)
  return markers
}

// Ближайший маркер к currentTime в направлении (-1/+1), опционально отфильтрованный по типу
// (для хоткеев навигации [ ] — все, , . — только слоги).
export function adjacentMarkerTime(markers, currentTime, direction, filterType) {
  const cmi = currentMarkerIndex(markers, currentTime)
  const step = direction < 0 ? -1 : 1
  for (let i = cmi + step; i >= 0 && i < markers.length; i += step) {
    if (!filterType || markers[i].markertype === filterType) return markers[i].time
  }
  return null
}

// Удаляет маркер, ближайший к currentTime (текущий по currentMarkerIndex). END не удаляем.
// Возвращает индекс удалённого маркера — вызывающая сторона ставит воспроизведение на «съехавший
// на этот индекс» маркер (см. EditorWorkView.removeMarker).
export function deleteMarkerAtTime(markers, syllables, currentTime) {
  const cmi = currentMarkerIndex(markers, currentTime)
  if (cmi < 0) return -1
  const m = markers[cmi]
  if (m.markertype === 'setting' && m.label === 'END') return -1
  markers.splice(cmi, 1)
  relabelSyllables(markers, syllables)
  return cmi
}

// Гарантирует END-маркер на длительности трека (вызывается перед submit и при включении preview).
export function ensureEndMarker(markers, duration) {
  if (markers.length === 0) return markers
  const end = markers.find((m) => m.markertype === 'setting' && m.label === 'END')
  if (!end) {
    markers.push({
      uid: nextUid(),
      time: duration,
      label: 'END',
      color: MARKER_COLOR_END,
      position: 'top',
      markertype: 'setting',
    })
    sortMarkers(markers)
  } else if (Math.abs(end.time - duration) > 0.05) {
    end.time = duration
  }
  return markers
}

// HTML отформатированного текста с подсветкой текущего слога, группами голоса (0..3) и
// комментариями. Идентично формату karaoke-public getFormattedText.
export function formatText(markers, curMarkerIndex) {
  const CUR = '<span class="ske-fx-cur">'
  const GROUP_CLASS = {
    'GROUP|0': 'ske-fx-group0',
    'GROUP|1': 'ske-fx-group1',
    'GROUP|2': 'ske-fx-group2',
    'GROUP|3': 'ske-fx-group3',
  }
  let spanClass = 'ske-fx-group0'
  let wasBr = true
  let result = ''
  for (let i = 0; i < markers.length; i++) {
    const marker = markers[i]
    switch (marker.markertype) {
      case 'setting': {
        if (GROUP_CLASS[marker.label]) {
          spanClass = GROUP_CLASS[marker.label]
        } else if (marker.label && marker.label.startsWith('COMMENT|')) {
          const txt = uppercaseFirstLetter((marker.label.split('|')[1] || '').replaceAll('_', ' '))
          result += `<span class="ske-fx-comment">${txt}</span><br>`
          wasBr = true
        }
        break
      }
      case 'endofline':
      case 'newline':
        result += '<br>'
        wasBr = true
        break
      case 'syllables': {
        result += i === curMarkerIndex ? CUR : `<span class="${spanClass}">`
        let txt = marker.label ? marker.label.replaceAll('_', ' ') : ''
        if (wasBr) {
          txt = uppercaseFirstLetter(txt)
          wasBr = false
        }
        result += txt
        result += '</span>'
        break
      }
      default:
        break
    }
  }
  return result
}

// «Бегущая строка»: текущий слог крупно + следующий + контекст (минимальный набор как в karaoke-public).
export function buildTail(syllables, curSyllableIndex) {
  let textBegin = ''
  let textCurr =
    curSyllableIndex === syllables.length - 1 && curSyllableIndex >= 0
      ? syllables[syllables.length - 1].replaceAll('_', ' ')
      : ''
  let textNext = ''
  let textEnd = ''

  for (let i = curSyllableIndex + 1; i < syllables.length; i++) {
    if (i === curSyllableIndex + 1) {
      textNext += syllables[i].replaceAll('_', ' ')
      if (i > 1) textCurr = syllables[i - 1].replaceAll('_', ' ')
    } else {
      textEnd += syllables[i]
    }
    if (textEnd.length > 40) break
  }
  textEnd = textEnd.substring(0, 40)
  for (let i = curSyllableIndex; i > 0; i--) {
    textBegin = syllables[i - 1] + textBegin
    if (textBegin.length > 40) break
  }
  textBegin = textBegin.split('').reverse().join('').substring(0, 40).split('').reverse().join('')

  if (textNext === '') textNext = '~'
  if (textCurr === '') textCurr = '~'

  return {
    begin: textBegin.replaceAll('_', ' ').trim(),
    curr: textCurr,
    next: textNext,
    end: textEnd.replaceAll('_', ' ').trim(),
  }
}

// Очищает маркеры для сохранения (без uid и служебных полей). Формат 1:1 с admin и public.
export function markersToSave(markers) {
  return markers.map((m) => ({
    time: m.time,
    label: m.label || '',
    note: '',
    chord: '',
    stringLad: '',
    locklad: '',
    color: m.color || '',
    position: m.position || 'bottom',
    markertype: m.markertype,
  }))
}

// Загружает маркеры из ответа сервера, добавляя uid для связи с регионами WaveSurfer.
export function markersFromServer(list) {
  return (list || []).map((m) => ({
    uid: nextUid(),
    time: m.time,
    label: m.label || '',
    color: m.color || '',
    position: m.position || 'bottom',
    markertype: m.markertype,
  }))
}

// Собирает inline-данные для KaraokePlayer из черновика редактора (для preview без fetch).
// Преобразует массив маркеров по голосам в формат `{markers: [...]}` (как в /api/song/{id}/playerdata),
// потому что KaraokePlayer ожидает именно поле `markers` (см. _parseMarkers).
//
// Формат 1:1 с ответом /api/song/{id}/playerdata (см. ApiController.kt) — все поля, что плеер
// читает из data (см. KaraokePlayer.js), должны быть тут. Если key/album/year/track пустые у
// конкретной песни в БД — плеер корректно скрывает тональность/альбом в UI (см. _renderFrame:2309
// и metaRows:2939).
export function buildInlinePlayerData({
  songId,
  songName,
  author,
  album,
  year,
  track,
  key,
  bpm,
  sourceTexts,
  markersPerVoice,
  audioVocalsUrl,
  audioAccompanimentUrl,
  audioBassUrl,
  audioDrumsUrl,
  albumImageUrl,
  artistImageUrl,
  exportBaseName,
}) {
  return {
    id: songId,
    songName,
    author,
    album: album || '',
    year: year != null ? year : null,
    track: track != null ? track : null,
    key: key || null,
    bpm: bpm != null ? bpm : null,
    markers: markersPerVoice || [],
    audioAccompanimentUrl,
    audioVocalsUrl,
    audioBassUrl: audioBassUrl || null,
    audioDrumsUrl: audioDrumsUrl || null,
    albumImageUrl: albumImageUrl || null,
    artistImageUrl: artistImageUrl || null,
    exportBaseName: exportBaseName || '',
  }
}
