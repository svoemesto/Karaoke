/**
 * Подсветка слов, требующих внимания в блоке исходного текста редактора.
 *
 * ЗАЧЕМ ОТДЕЛЬНЫЙ ФАЙЛ. В textarea нельзя положить HTML, поэтому подсветка
 * рисуется в зеркальном слое `<div>` ПОД textarea (см. SubsEdit.vue). Слой
 * берёт HTML отсюда. Функция чистая и детерминированная — её можно тестировать
 * без DOM, что дешевле и надёжнее, чем кликать по редактору.
 *
 * ПРАВИЛО СОВПАДЕНИЯ. Для текущего набора («ё») словарь хранит по одной записи на лексему (вариант с «ё»
 * либо нормативный — см. deploy/karaoke-db/51_ambiguous_yo_dictionary.sql).
 * При сопоставлении «е» и «ё» взаимозаменяемы ВНУТРИ слова-образца, поэтому
 * запись «всё» находит и «все», и «всё», а дублировать формы в БД не нужно.
 *
 * ГРАНИЦЫ СЛОВА. Используются lookaround, а не \b: \b в JavaScript не работает
 * с кириллицей (она не попадает в \w). В класс границы включена латиница —
 * в данных этого проекта встречаются слова с латинскими буквами внутри
 * кириллицы (гомоглифы, см. словарь «Слова с Ё»), и без латиницы подсветка
 * резала бы слова посередине.
 *
 * Экранирование обязательно: текст песни — это пользовательский ввод, который
 * уходит в innerHTML. Без escapeHtml слово «<b>» из текста стало бы тегом.
 *
 * ПОЧЕМУ <span>, А НЕ <mark>. Разметку приходится вставлять через innerHTML, а
 * элементы, вставленные мимо шаблона, не получают атрибут скоупа Vue — значит
 * правила вида `.компонент mark {...}` к ним не применяются, и побеждают чужие.
 * На <mark> это вылилось в два бага: bootstrap-reboot красил его в бледный
 * #fff3cd вместо жёлтого, и — что хуже — он вставал блочным элементом, так что
 * подсвеченное слово УЕЗЖАЛО на отдельную строку, ломая вёрстку текста песни.
 * Обычный <span> с классом не несёт чужих правил.
 */

/** Символы, которые регулярное выражение воспринимает как спецсимволы. */
const RE_SPECIAL = /[.*+?^${}()|[\]\\]/g

/** Граница слова: кириллица + латиница, чтобы не резать слова посередине. */
const WORD_CHAR = 'А-Яа-яЁёA-Za-z0-9\\-'

/**
 * Экранирование текста для безопасной вставки в innerHTML.
 *
 * @param {string} text - исходный текст
 * @returns {string} текст, в котором & < > " ' экранированы
 */
export function escapeHtml(text) {
  return String(text ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

/**
 * Превращает слово-образец в кусок регулярки, где «е» и «ё» взаимозаменяемы.
 *
 * @param {string} word - слово из словаря
 * @returns {string} фрагмент регулярки
 */
function flexibleWordPattern(word) {
  let out = ''
  for (const ch of word) {
    const lower = ch.toLowerCase()
    if (lower === 'е' || lower === 'ё') {
      // Класс с «ё» и без диакритики: часть шрифтов/движков не имеет «ё»,
      // и без неё слово просто не нашлось бы.
      out += '[еёЁ]'
    } else {
      out += ch.replace(RE_SPECIAL, '\\$&')
    }
  }
  return out
}

/**
 * Строит одно объединённое регулярное выражение по всему словарю.
 *
 * @param {string[]} words - словарь
 * @returns {RegExp|null} регулярка или null, если словарь пуст
 */
export function buildHighlightRegex(words) {
  const list = (words || []).map((w) => String(w || '').trim()).filter((w) => w !== '')
  if (list.length === 0) return null
  // Длинные слова первыми: при одинаковом префиксе («всё»/«всё-таки») движок
  // обязан попробовать более длинный вариант раньше короткого.
  const body = list
    .slice()
    .sort((a, b) => b.length - a.length)
    .map(flexibleWordPattern)
    .join('|')
  return new RegExp(`(?<![${WORD_CHAR}])(?:${body})(?![${WORD_CHAR}])`, 'giu')
}

/**
 * Подсвечивает слова словаря в тексте, возвращая безопасный HTML.
 *
 * @param {string} text - исходный текст песни
 * @param {string[]} words - словарь
 * @param {object} [opts]
 * @param {string} [opts.className] - CSS-класс обёртки совпадения
 * @returns {string} HTML для зеркального слоя
 */
export function highlightWords(text, words, opts = {}) {
  const className = opts.className || 'se-hl-attention'
  const source = String(text ?? '')
  const re = buildHighlightRegex(words)
  // Без словаря возвращаем просто экранированный текст: слой всё равно нужен,
  // он держит те же метрики шрифта, что и textarea.
  if (!re) return escapeHtml(source)
  let out = ''
  let last = 0
  for (const m of source.matchAll(re)) {
    out += escapeHtml(source.slice(last, m.index))
    out += `<span class="${className}">${escapeHtml(m[0])}</span>`
    last = m.index + m[0].length
  }
  out += escapeHtml(source.slice(last))
  return out
}
