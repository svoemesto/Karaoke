// BR-434 (аудит #336, пакет PKG-S3-32, OpenProject #404).
//
// Word-level diff для AI-редактора текста песни.
//
// ДЕФЕКТ. LCS-матрица строилась целиком: `(n+1) × (m+1)` вложенных массивов на каждое
// нажатие «Исправить…». Память и время росли квадратично от длины текста, а ограничения
// на длину входа в модалке не было — при длинном тексте вкладка зависала или падала
// по памяти.
//
// РЕШЕНИЕ (две части).
// 1. Общий префикс и общий суффикс вырезаются ДО построения матрицы. Для типичной
//    правки (LLM меняет несколько слов в середине) differing-часть крошечная, и
//    матрица строится по ней, а не по всему тексту.
// 2. Если differing-часть всё равно больше предела (`DIFF_MAX_TOKENS`), подсветка НЕ
//    вычисляется: показывается результат целиком, без подсветки, и вызывающий
//    показывает пользователю сообщение об этом. Честная деградация вместо зависания.
//
// Семантика сохранена: токены исходного текста, отсутствующие в результате, просто
// пропускаются (не подсвечиваются) — правая панель показывает ИТОГОВЫЙ текст, где
// отличия от исходного помечены цветом.

// Токенизация текста для word-level diff: слова (кириллица/латиница/цифры), одиночный перевод
// строки, пробельные "не-\n" последовательности и всё остальное (пунктуация) — отдельными
// токенами. Такой гранулярности достаточно и для орфографии (меняются словесные токены), и для
// пунктуации (меняются токены-символы), без разных путей кода под каждый режим.
const TOKEN_RE = /[0-9A-Za-zА-Яа-яЁё]+|\n|[^\S\n]+|[^\s0-9A-Za-zА-Яа-яЁё]+/g

/**
 * Предел суммарного числа токенов в differing-части, для которого ещё строится
 * LCS-матрица. 2000 × 2000 — это 4 млн ячеек: заметно, но безопасно для вкладки.
 *
 * @type {number}
 */
export const DIFF_MAX_TOKENS = 2000

/**
 * Разбивает текст на токены.
 *
 * @param {string} text — исходный текст.
 * @returns {string[]} токены.
 */
export function tokenize(text) {
  return String(text || '').match(TOKEN_RE) || []
}

/**
 * Экранирует текст перед вставкой в HTML.
 *
 * @param {string} text — текст.
 * @returns {string} экранированный текст.
 */
export function escapeHtml(text) {
  return String(text)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

/**
 * Обычный LCS-diff по токенам (с вырезанием общего префикса/суффикса).
 *
 * @param {string[]} a — токены исходного текста.
 * @param {string[]} b — токены результата.
 * @param {number} [maxTokens] — предел числа токенов differing-части.
 * @returns {{tokens: Array<{type: string, text: string}>, truncated: boolean}}
 *   `truncated === true` — подсветка не вычислялась (превышен предел), результат
 *   показан целиком.
 */
export function diffTokens(a, b, maxTokens = DIFF_MAX_TOKENS) {
  const n = a.length
  const m = b.length

  // BR-434: общий префикс и суффикс не участвуют в матрице.
  let prefix = 0
  while (prefix < n && prefix < m && a[prefix] === b[prefix]) prefix++
  let suffix = 0
  while (suffix < n - prefix && suffix < m - prefix && a[n - 1 - suffix] === b[m - 1 - suffix]) {
    suffix++
  }

  const tokens = []
  for (let i = 0; i < prefix; i++) tokens.push({ type: 'equal', text: a[i] })

  const midA = a.slice(prefix, n - suffix)
  const midB = b.slice(prefix, m - suffix)

  // BR-434: превышен предел — результат показывается целиком, без подсветки.
  if (midA.length + midB.length > maxTokens) {
    for (const text of midB) tokens.push({ type: 'equal', text })
    for (let i = 0; i < suffix; i++) tokens.push({ type: 'equal', text: a[n - 1 - i] })
    return { tokens, truncated: true }
  }

  const p = midA.length
  const q = midB.length
  const dp = new Array(p + 1)
  for (let i = 0; i <= p; i++) dp[i] = new Array(q + 1).fill(0)
  for (let i = p - 1; i >= 0; i--) {
    for (let j = q - 1; j >= 0; j--) {
      dp[i][j] = midA[i] === midB[j] ? dp[i + 1][j + 1] + 1 : Math.max(dp[i + 1][j], dp[i][j + 1])
    }
  }

  let i = 0
  let j = 0
  while (i < p && j < q) {
    if (midA[i] === midB[j]) {
      tokens.push({ type: 'equal', text: midB[j] })
      i++
      j++
    } else if (dp[i + 1][j] >= dp[i][j + 1]) {
      i++
    } else {
      tokens.push({ type: 'added', text: midB[j] })
      j++
    }
  }
  while (j < q) {
    tokens.push({ type: 'added', text: midB[j] })
    j++
  }
  for (let k = 0; k < suffix; k++) tokens.push({ type: 'equal', text: a[n - 1 - k] })

  return { tokens, truncated: false }
}

/**
 * Строит HTML результата корректора с подсветкой добавленного.
 *
 * @param {string} oldText — исходный текст.
 * @param {string} newText — результат корректора.
 * @param {number} [maxTokens] — предел числа токенов differing-части.
 * @returns {{html: string, truncated: boolean}} HTML и признак «подсветка пропущена».
 */
export function buildDiffHtml(oldText, newText, maxTokens = DIFF_MAX_TOKENS) {
  const { tokens, truncated } = diffTokens(tokenize(oldText), tokenize(newText), maxTokens)
  const html = tokens
    .map((t) => {
      const escaped = escapeHtml(t.text)
      return t.type === 'added' ? `<span class="ae-mark ae-added">${escaped}</span>` : escaped
    })
    .join('')
  return { html, truncated }
}
