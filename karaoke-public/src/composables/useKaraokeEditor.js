// Чистая логика караоке-разметки, портированная из webvue3 SubsEdit.vue (минимальный набор:
// слоги + концы/новые строки + END). Никакой завязки на WaveSurfer — оперирует простыми объектами
// маркеров { uid, time, label, color, position, markertype }. Отрисовку регионов и транспорт держит
// компонент EditorWorkView.vue. Формат маркеров идентичен admin-редактору, поэтому разметка,
// одобренная админом, применяется в tbl_settings один-в-один.

const MARKER_COLOR_SYLLABLES = '#D2691E'
const MARKER_COLOR_FIRSTSYLLABLE = '#008000'
const MARKER_COLOR_ENDOFLINE = '#FF0000'
const MARKER_COLOR_NEWLINE = '#FF0000'
const MARKER_COLOR_END = '#000080'

let uidCounter = 1
export function nextUid() {
  return `m${uidCounter++}`
}

function uppercaseFirstLetter(s) {
  return s && s.length ? s.charAt(0).toUpperCase() + s.slice(1) : s
}

// Слогоделение (точная копия getSyllables из SubsEdit.vue): разбивает текст на слоги регэкспом по
// гласным (рус/лат), последний слог слова помечается суффиксом '_'. Слоги без гласной приклеиваются
// к соседям.
export function splitSyllables(sourceText) {
  const result = []
  const words = sourceText.match(/\S+/ig) || []
  for (let i = 0; i < words.length; i++) {
    const word = words[i]
    const syllables = word.replace(/[ЙЦКНГШЩЗХЪФВПРЛДЖЧСМТЬБQWRTYPSDFGHJKLZXCVBNM-]*[ЁУЕЫАОЭЯИЮEUIOAїієѣ][ЙЦКНГШЩЗХЪФВПРЛДЖЧСМТЬБQWRTYPSDFGHJKLZXCVBNM-]*?(?=[ЦКНГШЩЗХФВПРЛДЖЧСМТБQWRTYPSDFGHJKLZXCVBNM-]?[ЁУЕЫАОЭЯИЮEUIOAїієѣ]|[Й|Y][АИУЕОEUIOAїієѣ])/ig, '$& ').split(' ')
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
      if ('ЁУЕЫАОЭЯИЮёуеыаоэяиюEUIOAeuioaїієѣ'.includes(word[j])) { haveVowel = true; break }
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

// Стабильная сортировка по времени, затем по типу маркера (как sortSourceMarkers).
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

// Индекс текущего слогового маркера по времени воспроизведения (getCurrentSyllablesIndex).
export function currentSyllableIndex(markers, currentTime) {
  const syl = markers.filter(m => m.markertype === 'syllables')
  const diff = 0.02
  if (syl.length > 0 && currentTime < syl[0].time - diff) return -1
  for (let i = 0; i < syl.length - 1; i++) {
    if (currentTime >= syl[i].time - diff && currentTime < syl[i + 1].time - diff) return i
  }
  return syl.length - 1
}

// Индекс текущего маркера среди всех (getCurrentMarkersIndex) — для подсветки в тексте.
export function currentMarkerIndex(markers, currentTime) {
  const diff = 0.02
  if (markers.length > 0 && currentTime < markers[0].time - diff) return -1
  for (let i = 0; i < markers.length - 1; i++) {
    if (currentTime >= markers[i].time - diff && currentTime < markers[i + 1].time - diff) return i
  }
  return markers.length - 1
}

// Переназначает label/color слоговым маркерам по массиву слогов (updateMarkersBySyllables без
// region-специфики — регионы перерисовывает компонент). i-й syllables-маркер получает i-й слог;
// первый слог строки (после endofline) — зелёный, остальные — охра. Лишние маркеры зачищаются.
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

// Добавляет маркер в момент currentTime. Если он попадает почти точно на существующий маркер
// (diff < 0.002) — заменяет его (как в SubsEdit addMarker). Возвращает { markers, added }.
export function addMarker(markers, syllables, markerType, currentTime) {
  const cmi = currentMarkerIndex(markers, currentTime)
  const currentMarkerTime = cmi >= 0 ? markers[cmi].time : 0
  const diff = Math.abs(currentMarkerTime - currentTime)

  let color = MARKER_COLOR_SYLLABLES
  let position = 'bottom'
  if (markerType === 'endofline') { color = MARKER_COLOR_ENDOFLINE }
  else if (markerType === 'newline') { color = MARKER_COLOR_NEWLINE }

  const newMarker = { uid: nextUid(), time: currentTime, label: '', color, position, markertype: markerType }
  const indexToInsert = cmi + (diff < 0.002 ? 0 : 1)
  const countDeleted = diff < 0.002 ? 1 : 0
  markers.splice(indexToInsert, countDeleted, newMarker)
  relabelSyllables(markers, syllables)
  return markers
}

// Удаляет маркер, ближайший к currentTime (текущий по currentMarkerIndex). END не удаляем вручную.
export function deleteMarkerAtTime(markers, syllables, currentTime) {
  const cmi = currentMarkerIndex(markers, currentTime)
  if (cmi < 0) return markers
  const m = markers[cmi]
  if (m.markertype === 'setting' && m.label === 'END') return markers
  markers.splice(cmi, 1)
  relabelSyllables(markers, syllables)
  return markers
}

// Гарантирует END-маркер на длительности трека (addEndMarker). Вызывается перед submit.
export function ensureEndMarker(markers, duration) {
  if (markers.length === 0) return markers
  const end = markers.find(m => m.markertype === 'setting' && m.label === 'END')
  if (!end) {
    markers.push({ uid: nextUid(), time: duration, label: 'END', color: MARKER_COLOR_END, position: 'top', markertype: 'setting' })
    sortMarkers(markers)
  } else if (Math.abs(end.time - duration) > 0.05) {
    end.time = duration
  }
  return markers
}

// HTML отформатированного текста с подсветкой текущего слога (getFormattedText, минимальный набор:
// syllables + endofline/newline). Используется в превью.
export function formatText(markers, curMarkerIndex) {
  const CUR = '<span class="ke-fx-cur">'
  const NORM = '<span class="ke-fx-norm">'
  let wasBr = true
  let result = ''
  for (let i = 0; i < markers.length; i++) {
    const marker = markers[i]
    switch (marker.markertype) {
      case 'endofline':
      case 'newline':
        result += '<br>'
        wasBr = true
        break
      case 'syllables': {
        result += (i === curMarkerIndex) ? CUR : NORM
        let txt = marker.label ? marker.label.replaceAll('_', ' ') : ''
        if (wasBr) { txt = uppercaseFirstLetter(txt); wasBr = false }
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

// «Бегущая строка»: текущий слог крупно + следующий + контекст (getTail, минимальный набор).
export function buildTail(syllables, curSyllableIndex) {
  let textBegin = ''
  let textCurr = (curSyllableIndex === syllables.length - 1 && curSyllableIndex >= 0)
    ? syllables[syllables.length - 1].replaceAll('_', ' ') : ''
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

// Очищает маркеры для сохранения (как getMarkersToSave — без uid и служебных полей). Формат
// идентичен admin-редактору; note/chord/stringLad/locklad оставляем пустыми (минимальный редактор).
export function markersToSave(markers) {
  return markers.map(m => ({
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
  return (list || []).map(m => ({
    uid: nextUid(),
    time: m.time,
    label: m.label || '',
    color: m.color || '',
    position: m.position || 'bottom',
    markertype: m.markertype,
  }))
}
