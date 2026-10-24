// ФИКСТУРА (tools/fixtures/marker-escape/) — замороженный снимок функции
// formatText для проверки «до/после» guard'а tools/check-marker-escape-guard.sh
// (аудит #336, BR-523, пакет PKG-S3-02, OpenProject #387).
//
// Снимок ИСПРАВЛЕННОГО кода: метка комментария и текст слога экранируются перед интерполяцией в HTML.
//
// Файл НЕ импортируется приложением: его читает только guard-скрипт, который
// прогоняет против него тест экранирования (karaoke-public/src/composables/
// __tests__/formatText-escape.test.js и webvue3/src/components/SongEditor/
// __tests__/useReviewModalFormat.test.js).
//
// Источник снимка: webvue3/src/components/SongEditor/useReviewModalFormat.js

function uppercaseFirstLetter(s) {
  return s && s.length ? s.charAt(0).toUpperCase() + s.slice(1) : s
}

// Каноническое экранирование — как в webvue3/src/utils/escapeHtml.js (спека #558).
function escapeHtml(text) {
  return String(text ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

export function formatText(markers, curMarkerIndex) {
  const CUR = '<span class="ke-fx-cur">'
  const GROUP_CLASS = {
    'GROUP|0': 'ke-fx-group0',
    'GROUP|1': 'ke-fx-group1',
    'GROUP|2': 'ke-fx-group2',
    'GROUP|3': 'ke-fx-group3',
  }
  let spanClass = 'ke-fx-group0'
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
          result += `<span class="ke-fx-comment">${escapeHtml(txt)}</span><br>`
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
        result += escapeHtml(txt)
        result += '</span>'
        break
      }
      default:
        break
    }
  }
  return result
}
