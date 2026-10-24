// Общая оснастка e2e-набора админки webvue3.
//
// ЗАЧЕМ ФАЙЛ. До него каждый из двенадцати файлов набора повторял одну и ту же
// процедуру открытия редактора — с ЗАШИТЫМ id песни 11718, с фиксированным
// `waitForTimeout(7000…12000)` вместо ожидания готовности и со своей копией
// поиска инстанса SubsEdit в дереве компонентов. Последствия (аудит #336,
// порция P-68):
//
//   * BR-625 / P-68-12 — набор зависел от содержимого одной боевой песни:
//     на стенде без песни 11718 он был невоспроизводим;
//   * BR-624 / P-68-11 — фиксированные таймеры делали набор последовательным
//     (`workers: 1`) и зависящим от скорости машины.
//
// Здесь id песни берётся из переменной окружения E2E_SONG_ID, а готовность
// редактора определяется по состоянию компонента, а не по таймеру.
//
// ПЕРЕМЕННЫЕ ОКРУЖЕНИЯ:
//
//   E2E_BASE_URL  — адрес приложения (по умолчанию http://localhost:7906,
//                   см. playwright.config.js);
//   E2E_SONG_ID   — id песни для набора (по умолчанию 11718 — «Говорит
//                   Деметра» на локальном стенде);
//   E2E_WORKERS   — число воркеров Playwright (по умолчанию — своё Playwright).

/** id песни, на которой работает набор. Переопределяется переменной окружения. */
export const SONG_ID = Number(process.env.E2E_SONG_ID || 11718)

/**
 * Ищет инстанс компонента в дереве Vue и выполняет над его прокси функцию.
 *
 * Функция передаётся строкой: её тело уезжает в браузер, поэтому замыкания
 * на стороне Node недоступны — всё нужное передаётся аргументом `arg`.
 *
 * @param {import('@playwright/test').Page} page
 * @param {string} name - имя компонента (`name` или `__name`)
 * @param {Function} fn - функция `(proxy, arg) => any`
 * @param {*} [arg] - произвольный аргумент
 * @returns {Promise<any>} результат функции либо `{ error }`, если инстанс не найден
 */
export const withComponent = (page, name, fn, arg) =>
  page.evaluate(
    ([compName, body, a]) => {
      const app = document.querySelector('#app[data-v-app]').__vue_app__
      const seen = new Set()
      let inst = null
      const walk = (v) => {
        if (!v || typeof v !== 'object' || seen.has(v)) return
        seen.add(v)
        const t = v.component?.type
        if (v.component && t && (t.name || t.__name) === compName) inst = v.component
        if (Array.isArray(v.children)) v.children.forEach(walk)
        if (v.component?.subTree) walk(v.component.subTree)
      }
      walk(app._container._vnode)
      if (!inst) return { error: `${compName} не найден в дереве компонентов` }
      // eslint-disable-next-line no-new-func
      return new Function('inst', 'arg', `return (${body})(inst, arg)`)(inst.proxy, a)
    },
    [name, fn.toString(), arg === undefined ? null : arg],
  )

/**
 * Выполняет функцию над прокси инстанса SubsEdit (редактор субтитров).
 *
 * @param {import('@playwright/test').Page} page
 * @param {Function} fn - функция `(proxy, arg) => any`
 * @param {*} [arg]
 */
export const withSubsEdit = (page, fn, arg) => withComponent(page, 'SubsEdit', fn, arg)

/**
 * Ждёт готовности редактора: инстанс SubsEdit найден, разметка загружена,
 * аудио декодировано (длительность известна — только после этого создаются
 * регионы волны).
 *
 * Ожидание по состоянию вместо фиксированного таймера — набор перестал
 * зависеть от скорости машины и стал пригоден для параллельного прогона.
 *
 * @param {import('@playwright/test').Page} page
 * @param {number} [timeout] - сколько ждать, мс
 */
export const waitForEditorReady = async (page, timeout = 90000) => {
  await page.locator('#waveform').waitFor({ state: 'attached', timeout })
  try {
    await page.waitForFunction(
      () => {
        const root = document.querySelector('#app[data-v-app]')
        const app = root && root.__vue_app__
        if (!app) return false
        const seen = new Set()
        let inst = null
        const walk = (v) => {
          if (!v || typeof v !== 'object' || seen.has(v)) return
          seen.add(v)
          const t = v.component?.type
          if (v.component && t && (t.name || t.__name) === 'SubsEdit') inst = v.component
          if (Array.isArray(v.children)) v.children.forEach(walk)
          if (v.component?.subTree) walk(v.component.subTree)
        }
        walk(app._container._vnode)
        if (!inst) return false
        const p = inst.proxy
        return p.sourceMarkers.length > 0 && p.duration > 0
      },
      null,
      { timeout },
    )
  } catch (e) {
    const state = await withSubsEdit(page, (p) => ({
      markers: p.sourceMarkers.length,
      duration: p.duration,
    })).catch(() => null)
    throw new Error(
      'редактор не дождался готовности за ' +
        timeout +
        ' мс: разметка=' +
        JSON.stringify(state) +
        ' (проверьте E2E_SONG_ID и наличие аудио у песни)',
    )
  }
}

/**
 * Открывает редактор субтитров песни и дожидается его готовности.
 *
 * @param {import('@playwright/test').Page} page
 * @param {{songId?: number, timeout?: number}} [opts]
 */
export const openEditor = async (page, opts = {}) => {
  const songId = opts.songId === undefined ? SONG_ID : opts.songId
  const timeout = opts.timeout || 90000

  // Список песен тянет POST /api/songs (~81 МБ) — обрываем, он не нужен.
  // ВАЖНО: сравнение по pathname, а не glob: `**/api/songs**` матчит ещё и
  // `/api/songsdigests` (song + s + digests), из-за чего обрывался нужный запрос.
  await page.route(
    (url) => url.pathname === '/api/songs',
    (r) => r.abort(),
  )
  await page.goto('/songs', { waitUntil: 'domcontentloaded' })

  // Песни приходят только с фильтром. Именно loadSongsDigestsPromise — обычный
  // loadSongsDigests промис не возвращает, и await после него не ждёт ничего.
  await page.evaluate(async (id) => {
    const store =
      document.querySelector('#app[data-v-app]').__vue_app__.config.globalProperties.$store
    const raw = await store.dispatch('loadSongsDigestsPromise', { filterId: String(id) })
    store.commit('updateSongsDigests', JSON.parse(raw))
  }, songId)

  await page.locator('.fld-song-name').first().click()
  await page.locator('button[title="Редактировать субтитры"]').first().click()
  await waitForEditorReady(page, timeout)
}

/**
 * Подставляет словарь «Слова для внимания» прямо в компонент.
 *
 * Тест не зависит от данных владельца: словарь наполняется вручную, поэтому
 * подстановка делается на месте, а не читается из базы.
 *
 * @param {import('@playwright/test').Page} page
 * @param {string[]} words
 */
export const setDictionary = (page, words) =>
  withSubsEdit(page, (p, ws) => {
    p.attentionWords = ws
    p.dictVersion++
    p.syncSourceTextDom()
  }, words)

/**
 * Состояние блока исходного текста: текст в модели и смещение каретки.
 *
 * Смещение каретки измеряется в координатах МОДЕЛИ, а не в координатах
 * текстового узла: узлов в блоке много (подсветка режет текст на span'ы), и
 * узловое смещение ничего не говорит о позиции во вводе. Способ — тот же, что
 * в enter-caret.spec.js: содержимое до каретки клонируется в отрисованный
 * (не visibility:hidden!) зонд, и его длина — искомое смещение.
 *
 * @param {import('@playwright/test').Page} page
 */
export const readCaretState = (page) =>
  page.evaluate(() => {
    const el = document.querySelector('#editor')
    if (!el) return { error: 'блок #editor не найден' }
    const sel = window.getSelection()
    const model = el.innerText.endsWith('\n') ? el.innerText.slice(0, -1) : el.innerText
    let caretOffset = 0
    if (sel && sel.rangeCount) {
      const r = sel.getRangeAt(0)
      const range = document.createRange()
      range.selectNodeContents(el)
      range.setEnd(r.startContainer, r.startOffset)
      const probe = document.createElement('div')
      probe.style.cssText = 'position:absolute;left:-9999px;top:0;white-space:pre-wrap'
      probe.appendChild(range.cloneContents())
      document.body.appendChild(probe)
      const t = probe.innerText
      caretOffset = t.endsWith('\n') ? Math.max(0, t.length - 1) : t.length
      probe.remove()
    }
    return { model, caretOffset }
  })

/**
 * Подсвеченные слова из блока исходного текста (в порядке появления).
 *
 * @param {import('@playwright/test').Page} page
 * @returns {Promise<string[]>}
 */
export const markedWords = (page) =>
  page.evaluate(() =>
    [...document.querySelectorAll('#editor span.se-hl-attention')].map((m) => m.textContent),
  )

/**
 * Ждёт, пока в блоке исходного текста окажется ровно `count` подсвеченных слов.
 *
 * Ожидание по состоянию, а не по таймеру: перерисовка подсветки отложена
 * (debounce 350 мс) и на загруженной машине (параллельный прогон набора)
 * отрабатывает через несколько секунд — фиксированная пауза в 1–1.5 секунды
 * давала «мигающие» падения.
 *
 * @param {import('@playwright/test').Page} page
 * @param {number} count - ожидаемое число подсвеченных слов
 * @param {number} [timeout]
 */
export const waitForMarkedWords = (page, count, timeout = 30000) =>
  page.waitForFunction(
    (expected) => document.querySelectorAll('#editor span.se-hl-attention').length === expected,
    count,
    { timeout },
  )

/**
 * Ждёт, пока подсвеченных слов станет не меньше `count`.
 *
 * @param {import('@playwright/test').Page} page
 * @param {number} count
 * @param {number} [timeout]
 */
export const waitForMarkedWordsAtLeast = (page, count, timeout = 30000) =>
  page.waitForFunction(
    (expected) => document.querySelectorAll('#editor span.se-hl-attention').length >= expected,
    count,
    { timeout },
  )

/**
 * Ждёт, пока блок исходного текста будет содержать указанную строку.
 *
 * @param {import('@playwright/test').Page} page
 * @param {string} text
 * @param {number} [timeout]
 */
export const waitForEditorText = (page, text, timeout = 30000) =>
  page.waitForFunction(
    (expected) => {
      const el = document.querySelector('#editor')
      return Boolean(el) && el.innerText.includes(expected)
    },
    text,
    { timeout },
  )

/**
 * Выбирает слово из ТЕКСТА ПЕСНИ для использования в качестве словаря.
 *
 * Тест не должен зависеть от содержимого конкретной песни (аудит #336,
 * BR-623): слово берётся из того текста, который открыт в редакторе, причём
 * выбирается максимальный по длине «токен» — так подсветка гарантированно
 * находится целиком (регулярка подсветки требует, чтобы слово не было
 * частью более длинного, а дефис входит в класс границы слова).
 *
 * @param {import('@playwright/test').Page} page
 * @returns {Promise<string>} слово либо пустая строка
 */
export const pickWordFromSongText = async (page) => {
  const text = await withSubsEdit(page, (p) => String(p.sourceText || ''))
  const source = typeof text === 'string' ? text : ''
  const tokens = source.match(/[\p{L}\p{N}]+(?:-[\p{L}\p{N}]+)*/gu) || []
  const counts = new Map()
  for (const token of tokens) {
    if (token.length < 4) continue
    counts.set(token, (counts.get(token) || 0) + 1)
  }
  let best = ''
  let bestCount = 0
  for (const [token, count] of counts) {
    if (count > bestCount || (count === bestCount && token.length > best.length)) {
      best = token
      bestCount = count
    }
  }
  return best
}

