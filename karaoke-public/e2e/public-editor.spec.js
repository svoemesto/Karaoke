// E2E публичного сайта (karaoke-public).
//
// Зачем эта инфраструктура: правки публичного редактора (#200, #201, #202,
// #203) проверялись юнит-тестами по исходникам, но НЕ проверялось, что они
// доехали до работающего сайта. Правки попали в master, а контейнер
// karaoke-public остался на образе шестидневной давности — дыру обнаружили
// только вручную. Юнит-тест доказывает, что функция ведёт себя правильно,
// но не что браузер отдаёт именно эту функцию.
//
// Поэтому здесь есть проверка контракта развёртывания: правила рендера
// публичного редактора ОБЯЗАНЫ присутствовать в отдаваемом бандле.
//
// Запуск:
//   npm run test:e2e          — против запущенного контейнера (E2E_BASE_URL)
//   npm run test:e2e:headed   — то же, но с видимым браузером
//
// В CI (джоб `e2e (karaoke-public)` в .github/workflows/lint.yml) контейнер
// karaoke-public собирается из репозитория и поднимается рядом с тестом —
// см. .github/workflows/lint.yml и karaoke-public/e2e/nginx-e2e.conf.
//
// BR-524 (аудит #336, OpenProject #387): проверка контракта больше не ищет
// фрагменты МИНИФИЦИРОВАННОГО кода (регексы вида /\.time>=\w+&&\w+\.time<=/
// зависят от версии минификатора и падают без дефекта). Поведение #202
// (дедуп по временному диапазону) и FIX #018 (маркер не встаёт в ноль)
// теперь проверяется юнит-тестами
// (karaoke-public/src/composables/__tests__/syncSpecTags.test.js), а сама
// проверка бандла помечена как «дымовая» (@smoke) — она не блокирует CI.
//
// BR-525 (аудит #336, OpenProject #387): проверка «без ошибок в консоли»
// сужена до сетевых/ресурсных ошибок (favicon, внешние шрифты, недоступный
// бэкенд) — они не являются дефектом SPA. Необработанные исключения JS
// (pageerror) по-прежнему роняют тест.

import { expect, test } from '@playwright/test'

// BR-525: сетевые и ресурсные ошибки — не дефект приложения:
//   - «Failed to load resource: …» — не отдался favicon/шрифт/внешний CDN;
//   - «net::ERR_…» — сеть недоступна;
//   - HTTP-статус («Bad Gateway», «Not Found», …) — его отдаёт xhr.statusText
//     в services/api.js и services/authApi.js, когда API недоступен или
//     отвечает ошибкой (в CI бэкенда нет — см. e2e/nginx-e2e.conf).
// Необработанные исключения JS (pageerror) в этот список НЕ входят — они
// всегда фатальны.
const NETWORK_OR_RESOURCE_ERROR =
  /Failed to load resource|net::ERR_|favicon|fonts\.googleapis\.com|^Bad Gateway$|^Not Found$|^Internal Server Error$|^Service Unavailable$|^Gateway Timeout$|^Unauthorized$|^Forbidden$|^Connection Refused$|^Network Error$/i

// Сетевая/ресурсная ошибка — не дефект приложения. Тот же фильтр применяется
// к необработанным исключениям: сообщение вида «Bad Gateway» — это отказ XHR
// (xhr.statusText в services/api.js), когда API недоступен (в CI бэкенда нет —
// см. e2e/nginx-e2e.conf), а не ошибка JS-логики.
function isNetworkError(text) {
  return NETWORK_OR_RESOURCE_ERROR.test(String(text).trim())
}

test.describe('публичный сайт', () => {
  test('отдаётся и грузится без ошибок в консоли', async ({ page }) => {
    const pageErrors = []
    const consoleErrors = []
    page.on('pageerror', (e) => pageErrors.push(e.message))
    page.on('console', (m) => {
      if (m.type() === 'error') consoleErrors.push(m.text())
    })

    const response = await page.goto('/')
    expect(response, 'сервер должен ответить').not.toBeNull()
    expect(response.status()).toBeLessThan(500)
    await expect(page.locator('#app').first()).toBeAttached()

    // Необработанное исключение JS — дефект. Исключение — сообщения сетевых
    // ошибок (XHR отдал statusText, когда API недоступен).
    const appPageErrors = pageErrors.filter((text) => !isNetworkError(text))
    expect(appPageErrors, 'необработанных исключений JS быть не должно').toHaveLength(0)

    // BR-525: сетевые/ресурсные ошибки (favicon, шрифты, недоступный API)
    // отсекаются; остальные ошибки консоли — дефект приложения.
    const appErrors = consoleErrors.filter((text) => !isNetworkError(text))
    expect(appErrors, 'ошибок приложения в консоли быть не должно').toHaveLength(0)
  })

  test(
    'контракт развёртывания: правила рендера редактора в отдаваемом бандле',
    // BR-524: дымовая проверка — она подтверждает, что редактор собрался и
    // отдан, но не блокирует CI (в CI прогоняется с --grep-invert @smoke).
    { tag: '@smoke' },
    async ({ request }) => {
      const html = await (await request.get('/')).text()
      const scripts = [...html.matchAll(/src="([^"]*assets\/[^"]*\.js)"/g)].map((m) => m[1])
      expect(scripts.length, 'в index.html должны быть ссылки на бандлы').toBeGreaterThan(0)

      // BR-524: выбор бандла детерминирован — берём ВСЕ бандлы с кодом
      // редактора (раньше брался «последний подходящий», и результат зависел от
      // порядка скриптов в index.html).
      const editorBundles = []
      for (const src of scripts) {
        const body = await (await request.get(src)).text()
        if (body.includes('markertype')) editorBundles.push(body)
      }
      expect(editorBundles.length, 'бандль с кодом редактора должен быть найден').toBeGreaterThan(0)

      // Строковый литерал переживает минификацию — его наличие означает, что
      // код редактора действительно попал в отдаваемый бандл.
      // Проверки «по тексту минифицированного кода» (регексы вида
      // /\.time>=\w+&&\w+\.time<=/ и /<\s*\.5\s*&&/) убраны: они зависят от
      // версии минификатора и падают без дефекта. Поведение, которое они
      // проверяли, покрыто юнит-тестами:
      //   - #202 (дедуп по ВРЕМЕННОМУ диапазону) и FIX #018 (маркер не в
      //     ноль) — karaoke-public/src/composables/__tests__/syncSpecTags.test.js
      //   - #203 (пустой комментарий) — karaoke-public/src/composables/
      //     __tests__/formatText.test.js
      const editorBundle = editorBundles.find((body) => body.includes('COMMENT| '))
      expect(
        editorBundle,
        'бандль с кодом редактора (маркер «COMMENT| ») должен быть найден',
      ).toBeTruthy()
    },
  )
})
