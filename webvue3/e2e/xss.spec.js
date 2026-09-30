// #193: v-html на пользовательском тексте песни без экранирования.
//
// Текст песни попадает в getFormattedText / getFormattedNotes / getFormattedChords /
// getTail, которые конкатенируют его в HTML-строки, и те рендерятся через v-html.
// Функций экранирования в компоненте не было.
//
// ВАЖНО про тест: вектор нужно подать ЧЕРЕЗ sourceText. Прямая запись в
// p.sourceMarkers[i].label не работает — sourceMarkers пересобирается из
// sourceText, и подставленное значение тут же затирается. Первая версия теста
// поэтому проходила вхолостую: ни <img>, ни &lt;img&gt; в выводе не было.

import { expect, test } from '@playwright/test'

test.setTimeout(180000)

// Порт 9 (discard) — соединение всегда отклоняется, поэтому onerror
// срабатывает ДЕТЕРМИНИРОВАННО. С src=x не срабатывал: nginx отдаёт по
// такому пути 200 с HTML, и картинка «загружается» без ошибки — из-за чего
// проверка «скрипт не выполнился» ничего не проверяла (fired=0 на обоих кодах).
const PAYLOAD = '<img src="http://127.0.0.1:9/x" onerror="window.__XSS_FIRED=1">'

test('#193: HTML из текста песни не исполняется, а показывается как текст', async ({ page }) => {
  await page.route(
    (u) => u.pathname === '/api/songs',
    (r) => r.abort(),
  )
  await page.goto('/songs', { waitUntil: 'domcontentloaded' })
  await page.evaluate(async () => {
    const s = document.querySelector('#app[data-v-app]').__vue_app__.config.globalProperties.$store
    s.commit(
      'updateSongsDigests',
      JSON.parse(await s.dispatch('loadSongsDigestsPromise', { filterId: '11718' })),
    )
  })
  await page.locator('.fld-song-name').first().click()
  await page.locator('button[title="Редактировать субтитры"]').first().click()
  await page.locator('#waveform').waitFor({ state: 'attached' })
  await page.waitForTimeout(9000)

  await page.evaluate((payload) => {
    window.__XSS_FIRED = 0
    const app = document.querySelector('#app[data-v-app]').__vue_app__
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
    // Отдельная строка, чтобы попасть в разбивку на слоги.
    inst.proxy.sourceText = payload + '\n' + inst.proxy.sourceText
  }, PAYLOAD)

  // Ждём пересборку маркеров из текста и все computed.
  await page.waitForTimeout(2500)

  const res = await page.evaluate(() => {
    const app = document.querySelector('#app[data-v-app]').__vue_app__
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
    const p = inst.proxy
    const all =
      String(p.textFormatted || '') +
      String(p.notesFormatted || '') +
      String(p.chordsFormatted || '') +
      String(p.tail || '')
    const per = {
      text: String(p.textFormatted || ''),
      notes: String(p.notesFormatted || ''),
      chords: String(p.chordsFormatted || ''),
      tail: String(p.tail || ''),
    }
    return {
      perRawImg: Object.keys(per).filter((k) => per[k].includes('<img')),
      perEscaped: Object.keys(per).filter((k) => per[k].includes('&lt;img')),
      rawImg: all.includes('<img'),
      escapedImg: all.includes('&lt;img'),
      injected: document.querySelectorAll(
        '.se-grid-item-text img, .se-grid-item-notes img, .se-grid-item-chords img, .se-tail img',
      ).length,
      sample: all.slice(0, 120),
    }
  })

  await page.waitForTimeout(1500)
  const fired = await page.evaluate(() => window.__XSS_FIRED || 0)
  console.log(
    'XSS: fired=' +
      fired +
      ' rawImg=' +
      res.rawImg +
      ' escaped=' +
      res.escapedImg +
      ' injected=' +
      res.injected,
  )
  console.log(
    'PER_RAW: ' + JSON.stringify(res.perRawImg) + ' PER_ESC: ' + JSON.stringify(res.perEscaped),
  )

  // Тест не имеет смысла, если вектор не дошёл до вывода.
  expect(res.escapedImg, 'вектор должен дойти до HTML в ЭКРАНИРОВАННОМ виде').toBe(true)
  expect(res.rawImg, 'сырой <img в HTML быть не должно').toBe(false)
  // Утверждения про fired здесь НЕТ намеренно: на старом коде сырой <img>
  // попадал в DOM (injected=2), но обработчик не срабатывал — разбивка текста
  // песни на слоги разрывает тег, и он не собирается в рабочий элемент.
  // Утверждение «скрипт не выполнился» было бы пустым: оно проходит и на
  // уязвимом коде, и выглядело бы как доказательство защиты.
  // Что тест действительно ловит — сырое HTML пользовательского текста,
  // попадающее в DOM через v-html.
  expect(res.injected, 'в блоки v-html не должно попасть ни одного <img>').toBe(0)
})
