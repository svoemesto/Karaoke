// ФИКСТУРА (tools/fixtures/marker-escape/) — замороженный снимок функции
// formatText для проверки «до/после» guard'а tools/check-marker-escape-guard.sh
// (аудит #336, BR-523, пакет PKG-S3-02, OpenProject #387).
//
// Снимок УЯЗВИМОГО кода: метка комментария интерполируется в HTML без экранирования (дефект BR-312).
//
// Файл НЕ импортируется приложением: его читает только guard-скрипт, который
// прогоняет против него тест экранирования (karaoke-public/src/composables/
// __tests__/formatText-escape.test.js и webvue3/src/components/SongEditor/
// __tests__/useReviewModalFormat.test.js).
//
// Источник снимка: karaoke-public/src/composables/useKaraokeEditor.js

function uppercaseFirstLetter(s) {
  return s && s.length ? s.charAt(0).toUpperCase() + s.slice(1) : s
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
  // Предыдущая группа голоса: расхождение даёт перенос строки при смене голоса.
  let spanClassPrev = spanClass
  let wasBr = true
  let result = ''
  for (let i = 0; i < markers.length; i++) {
    const marker = markers[i]
    switch (marker.markertype) {
      case 'setting': {
        if (GROUP_CLASS[marker.label]) {
          spanClass = GROUP_CLASS[marker.label]
        } else if (marker.label === 'COMMENT| ') {
          // Пустой комментарий — только перенос строки (#203, канон владельца:
          // поведение админского SubsEdit, где это явный case в switch).
          // Здесь wasBr НЕ ставится: следующее слово остаётся строчным.
          // Раньше этот маркер проваливался в общую ветку startsWith('COMMENT|'),
          // которая рендерила пустой <span> и поднимала регистр следующего слова.
          result += '<br>'
        } else if (marker.label && marker.label.startsWith('COMMENT|')) {
          const txt = uppercaseFirstLetter((marker.label.split('|')[1] || '').replaceAll('_', ' '))
          result += `<span class="ke-fx-comment">${txt}</span><br>`
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
    // Перенос строки при смене группы голоса (#203, канон владельца: «<br> при
    // смене голоса — так надо, должно быть ВЕЗДЕ»). В админском SubsEdit это
    // `if (spanStyle !== spanStylePrev) result += '<br>'`; здесь аналог по
    // классу группы. Строка ставится ПОСЛЕ маркера — так же, как в админке.
    if (spanClass !== spanClassPrev) result += '<br>'
    spanClassPrev = spanClass
  }
  return result
}
