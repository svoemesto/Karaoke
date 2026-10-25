// Регрессионные тесты пакета PKG-S3-31 (аудит #336, OpenProject #403) — общие компоненты.
//
// Записи пакета в этом файле:
//   BR-369 — App.vue: отказ восстановления настроек обрывал `mounted` до таймеров.
//   BR-372 — CustomConfirm: interval обратного отсчёта не останавливался.
//   BR-378 — FileExplorerModal: листинг старого каталога подменял новый.
//   BR-381 — HealthReport: ошибка загрузки выглядела как «нарушений не найдено».
//   BR-385 — SmartCopyModal: модалка закрывалась до ответа, результат не показывался.
//   BR-386 — Monitor: недоступный мониторинг светился зелёным («всё хорошо»).
//   BR-387 — ProcessWorker: неудачная синхронизация состояния давала ложное «play».
//
// ДО ПРАВКИ (BR-369): `mounted()` ждал 13 ответов `getWebvueProp` подряд без try/catch.
// Любой отказ обрывал хук ДО создания трёх polling-таймеров — бейджи чата/заданий/
// новых альбомов не обновлялись до перезагрузки страницы.
//
// ДО ПРАВКИ (BR-372): `setInterval` без сохранённого id; `clearInterval(this.decreaseTimeToClose)`
// передавал ФУНКЦИЮ, а не id; `beforeUnmount` отсутствовал.
//
// ДО ПРАВКИ (BR-381): `catch` только логировал, а `HealthReportTableBody` показывал
// «Записей Health Report нет — нарушений не найдено» — ложное «проблем нет».
//
// ДО ПРАВКИ (BR-387): у начальной синхронизации не было `.catch` и защиты `JSON.parse`:
// показывалась иконка «play», а `POST /api/processes/workerstartstop` — ПЕРЕКЛЮЧАТЕЛЬ,
// то есть клик останавливал бы работающую очередь.
//
// Запуск: cd webvue3 && node --test src/components/Common/__tests__/pkg-s3-31-common.test.js

import { test, describe } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const stripComments = (source) =>
  source.replace(/\/\*[\s\S]*?\*\//g, '').replace(/(^|[^:])\/\/[^\n]*/g, '$1')

const APP_VUE = stripComments(readFileSync(new URL('../../../App.vue', import.meta.url), 'utf-8'))
const CUSTOM_CONFIRM = stripComments(
  readFileSync(new URL('../CustomConfirm.vue', import.meta.url), 'utf-8'),
)
const FILE_EXPLORER = stripComments(
  readFileSync(new URL('../FileExplorer/FileExplorerModal.vue', import.meta.url), 'utf-8'),
)
const HEALTH_REPORT_STORE = stripComments(
  readFileSync(new URL('../HealthReport/store.js', import.meta.url), 'utf-8'),
)
const HEALTH_REPORT_BODY = stripComments(
  readFileSync(
    new URL('../HealthReport/components/HealthReportTableBody.vue', import.meta.url),
    'utf-8',
  ),
)
const SMART_COPY_MODAL = stripComments(
  readFileSync(new URL('../SmartCopy/SmartCopyModal.vue', import.meta.url), 'utf-8'),
)
const MONITOR_STORE = stripComments(
  readFileSync(new URL('../Monitor/store.js', import.meta.url), 'utf-8'),
)
const MONITOR_LIGHT = stripComments(
  readFileSync(new URL('../Monitor/MonitorLight.vue', import.meta.url), 'utf-8'),
)
const PROCESS_WORKER = stripComments(
  readFileSync(new URL('../ProcessWorker.vue', import.meta.url), 'utf-8'),
)

/**
 * Достаёт тело метода по имени.
 *
 * @param {string} source — исходник.
 * @param {string} name — имя метода.
 * @returns {string|null} тело метода либо null.
 */
const methodBody = (source, name) => {
  const definition = new RegExp('\\n\\s*(?:async\\s+)?' + name + '\\s*\\([^)]*\\)\\s*\\{').exec(
    source,
  )
  if (!definition) return null
  const open = source.indexOf('{', definition.index + definition[0].length - 1)
  let depth = 0
  for (let i = open; i < source.length; i++) {
    if (source[i] === '{') depth++
    else if (source[i] === '}') {
      depth--
      if (depth === 0) return source.slice(open + 1, i)
    }
  }
  return null
}

describe('PKG-S3-31 / Общие компоненты', () => {
  describe('BR-369 — App.vue: отказ восстановления настроек не роняет бейджи', () => {
    test('polling-таймеры создаются ДО восстановления настроек', () => {
      const mounted = methodBody(APP_VUE, 'mounted')
      assert.ok(mounted, 'BR-369: не найден хук mounted')
      const timersIndex = mounted.indexOf('chatUnreadPollTimer = setInterval')
      const restoreIndex = mounted.indexOf('restoreLastSettings')
      assert.ok(timersIndex > 0, 'BR-369: не найден таймер бейджа чата')
      assert.ok(restoreIndex > 0, 'BR-369: не найдено восстановление настроек')
      assert.ok(
        timersIndex < restoreIndex,
        'BR-369: отказ восстановления не должен обрывать создание таймеров',
      )
    })

    test('восстановление не бросает наружу и читает значения параллельно', () => {
      const body = methodBody(APP_VUE, 'restoreLastSettings')
      assert.ok(body, 'BR-369: не найден метод restoreLastSettings')
      assert.match(body, /Promise\.allSettled/, 'BR-369: значения читаются параллельно')
      assert.match(body, /catch/, 'BR-369: отказ не должен обрывать приложение')
    })
  })

  describe('BR-372 — CustomConfirm: таймер обратного отсчёта останавливается', () => {
    test('id таймера сохраняется', () => {
      assert.match(CUSTOM_CONFIRM, /this\.timer = setInterval/, 'BR-372: id таймера не сохраняется')
    })

    test('таймер очищается в beforeUnmount, ok и close', () => {
      assert.match(CUSTOM_CONFIRM, /beforeUnmount\(\)\s*\{[\s\S]*?clearTimer\(\)/)
      const ok = methodBody(CUSTOM_CONFIRM, 'ok')
      assert.ok(ok, 'BR-372: не найден метод ok')
      assert.match(ok, /clearTimer\(\)/, 'BR-372: ok обязан останавливать таймер')
      const close = methodBody(CUSTOM_CONFIRM, 'close')
      assert.ok(close, 'BR-372: не найден метод close')
      assert.match(close, /clearTimer\(\)/, 'BR-372: close обязан останавливать таймер')
    })

    test('clearInterval получает id, а не функцию', () => {
      assert.match(CUSTOM_CONFIRM, /clearInterval\(this\.timer\)/)
      assert.doesNotMatch(
        CUSTOM_CONFIRM,
        /clearInterval\(this\.decreaseTimeToClose\)/,
        'BR-372: clearInterval от функции не останавливает таймер',
      )
    })
  })

  describe('BR-378 — FileExplorerModal: листинг применяется только к своему каталогу', () => {
    test('у запроса есть поколение, ответ сверяется с ним', () => {
      const body = methodBody(FILE_EXPLORER, 'getFiles')
      assert.ok(body, 'BR-378: не найден метод getFiles')
      assert.match(body, /listingGeneration/, 'BR-378: нужен счётчик поколения запроса')
      assert.match(
        body,
        /ticket !== this\.listingGeneration/,
        'BR-378: устаревший ответ не должен применяться',
      )
    })
  })

  describe('BR-381 — HealthReport: ошибка отличается от «нарушений не найдено»', () => {
    test('в сторе есть состояние ошибки, которое заполняется в catch', () => {
      assert.match(HEALTH_REPORT_STORE, /healthReportListError: ''/)
      assert.match(HEALTH_REPORT_STORE, /getHealthReportListError\(state\)/)
      assert.match(
        HEALTH_REPORT_STORE,
        /setHealthReportListError/,
        'BR-381: ошибка обязана попадать в state',
      )
    })

    test('модалка показывает ошибку вместо «нарушений не найдено»', () => {
      assert.match(
        HEALTH_REPORT_BODY,
        /v-else-if="error"/,
        'BR-381: текст «нарушений не найдено» не должен показываться при ошибке',
      )
      assert.match(HEALTH_REPORT_BODY, /Не удалось загрузить Health Report/)
    })
  })

  describe('BR-385 — SmartCopyModal: результат показывается, модалка не закрывается заранее', () => {
    test('закрытие модалки происходит после ответа, а не сразу', () => {
      const body = methodBody(SMART_COPY_MODAL, 'doSmartCopy')
      assert.ok(body, 'BR-385: не найден метод doSmartCopy')
      assert.match(body, /createSmartCopyForAllPromise/, 'BR-385: не найден вызов создания')
      assert.doesNotMatch(
        body,
        /\$emit\('close'\)/,
        'BR-385: модалка закрывалась сразу после отправки запроса — результат терялся',
      )
      assert.match(body, /\.catch\(/, 'BR-385: отказ обязан обрабатываться')
      assert.match(body, /isCreating/, 'BR-385: нужен признак незавершённого запроса')
      assert.match(
        body,
        /isCustomConfirmVisible = true/,
        'BR-385: результат должен показываться в модалке',
      )
    })

    test('модалка закрывается после показа результата', () => {
      const body = methodBody(SMART_COPY_MODAL, 'closeCustomConfirm')
      assert.ok(body, 'BR-385: не найден метод closeCustomConfirm')
      assert.match(
        body,
        /\$emit\('close'\)/,
        'BR-385: закрытие диалога результата закрывает и модалку',
      )
    })
  })

  describe('BR-386 — Monitor: недоступный мониторинг не выглядит «зелёным»', () => {
    test('в сторе есть состояние ошибки загрузки', () => {
      assert.match(MONITOR_STORE, /monitorAlertsError: ''/)
      assert.match(MONITOR_STORE, /monitorAlertsError\(state\)/)
      assert.match(MONITOR_STORE, /setMonitorAlertsError/)
    })

    test('светофор показывает серый при ошибке', () => {
      assert.match(
        MONITOR_LIGHT,
        /if \(this\.error\) return '#9E9E9E'/,
        'BR-386: при ошибке светофор не должен быть зелёным',
      )
      assert.match(MONITOR_LIGHT, /нет связи с сервисом мониторинга/)
    })
  })

  describe('BR-387 — ProcessWorker: неудачная синхронизация блокирует переключение очереди', () => {
    test('у начальной синхронизации есть catch и защита JSON.parse', () => {
      const body = methodBody(PROCESS_WORKER, 'checkUpdateProcessesWorker')
      assert.ok(body, 'BR-387: не найден метод checkUpdateProcessesWorker')
      assert.match(body, /\.catch\(/, 'BR-387: отказ обязан обрабатываться')
      assert.match(body, /try \{[\s\S]*?JSON\.parse/, 'BR-387: JSON.parse должен быть защищён')
      assert.match(body, /workerStateUnknown = true/, 'BR-387: состояние помечается неизвестным')
    })

    test('кнопка старт/стоп блокируется, пока состояние неизвестно', () => {
      const body = methodBody(PROCESS_WORKER, 'disabled')
      assert.ok(body, 'BR-387: не найден computed disabled')
      assert.match(
        body,
        /if \(this\.workerStateUnknown\) return true/,
        'BR-387: переключать очередь без синхронизации нельзя',
      )
    })
  })
})
