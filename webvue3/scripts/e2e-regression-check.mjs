// Проверка «до/после» для e2e-набора админки webvue3.
//
// ЗАЧЕМ ЭТОТ СКРИПТ. Аудит #336 (порция P-68) нашёл в наборе проверки, которые
// НЕ МОГЛИ упасть: гонку сохранений (#189) и кламп индекса варианта строя
// (#194). Прежние тесты на этих дефектах проходили и на сломанном коде.
//
// Скрипт делает ровно то, что нужно, чтобы это проверить: поднимает админку ИЗ
// ИСХОДНИКОВ (vite dev server + прокси к готовому бэкенду), ломает код
// точечной мутацией, прогоняет соответствующий тест и требует, чтобы тест
// УПАЛ. Затем мутация отменяется, и тот же тест обязан пройти.
//
// Ничего не деплоится и не перезапускается: контейнеры, БД и прод не трогаются,
// работают только локальные процессы.
//
// Запуск:
//   cd webvue3 && npm run check:e2e:regressions
//   npm run check:e2e:regressions -- save-queue   # только одна мутация
//
// Требуется: запущенный стенд админки (по умолчанию http://localhost:7906) —
// он используется как БЭКЕНД (данные песен), а фронт собирается из исходников.

import { spawn } from 'node:child_process'
import { readFileSync, writeFileSync } from 'node:fs'
import http from 'node:http'

const SUBS_EDIT = new URL('../src/components/Songs/edit/SubsEdit.vue', import.meta.url)
const BACKEND = { host: '127.0.0.1', port: Number(process.env.E2E_BACKEND_PORT || 7906) }
const VITE_PORT = Number(process.env.E2E_VITE_PORT || 5199)
const PROXY_PORT = VITE_PORT + 1

// Мутации: [имя, что ломаем, что ожидаем от теста].
const MUTATIONS = [
  {
    name: 'save-queue',
    spec: 'save-race.spec.js',
    description: 'очередь сохранений saving/saveQueued убрана (регресс #189)',
    from: `    async save() {
      this.addEndMarker()
      if (this.saving) {
        // Уже сохраняем — не запускаем второй POST параллельно, а запоминаем,
        // что нужно повторить. Повтор уйдёт в той же функции после текущего.
        this.saveQueued = true
        return
      }
      this.saving = true
      try {`,
    to: `    async save() {
      this.addEndMarker()
      try {`,
    fromTail: `      } finally {
        this.saving = false
      }
      if (this.saveQueued) {
        this.saveQueued = false
        await this.save()
      }
    },`,
    toTail: `      } finally {
        // МУТАЦИЯ: очередь сохранений убрана.
      }
    },`,
  },
  {
    name: 'tabs-clamp',
    spec: 'tabs-variant.spec.js',
    description: 'кламп индекса варианта строя убран (регресс #194)',
    from: `    lstIndexesTabsVariant: {
      handler(variants) {
        if (this.indexTabsVariant > variants.length - 1) this.indexTabsVariant = variants.length - 1
      },
      immediate: true,
    },`,
    to: `    lstIndexesTabsVariant: {
      handler(variants) {
        // МУТАЦИЯ: клампа нет.
      },
      immediate: true,
    },`,
  },
]

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

/** Ждёт, пока адрес отвечает. */
async function waitForHttp(url, timeoutMs = 60000) {
  const deadline = Date.now() + timeoutMs
  while (Date.now() < deadline) {
    try {
      const res = await fetch(url, { method: 'GET' })
      if (res.status < 500) return true
    } catch (e) {
      /* ещё не поднялся */
    }
    await sleep(500)
  }
  return false
}

/** Прокси: статика — на vite dev server, /api — на готовый бэкенд. */
function startProxy() {
  const server = http.createServer((req, res) => {
    const target = req.url.startsWith('/api/') ? BACKEND : { host: '127.0.0.1', port: VITE_PORT }
    const proxyReq = http.request(
      {
        host: target.host,
        port: target.port,
        path: req.url,
        method: req.method,
        headers: { ...req.headers, host: `${target.host}:${target.port}` },
      },
      (proxyRes) => {
        res.writeHead(proxyRes.statusCode, proxyRes.headers)
        proxyRes.pipe(res)
      },
    )
    proxyReq.on('error', (e) => {
      res.writeHead(502)
      res.end('proxy error: ' + e.message)
    })
    req.pipe(proxyReq)
  })
  server.listen(PROXY_PORT, '127.0.0.1')
  return server
}

/** Прогоняет playwright и возвращает код выхода. */
function runPlaywright(spec) {
  return new Promise((resolve) => {
    const child = spawn(
      'npx',
      ['playwright', 'test', spec, '--reporter=list', '--workers=1'],
      {
        env: { ...process.env, E2E_BASE_URL: `http://127.0.0.1:${PROXY_PORT}` },
        stdio: 'inherit',
      },
    )
    child.on('exit', (code) => resolve(code === null ? 1 : code))
  })
}

const selected = process.argv.slice(2)
const mutations = selected.length
  ? MUTATIONS.filter((m) => selected.includes(m.name))
  : MUTATIONS
if (!mutations.length) {
  console.error('неизвестная мутация: ' + selected.join(', '))
  process.exit(2)
}

const original = readFileSync(SUBS_EDIT, 'utf-8')
const vite = spawn(
  'npx',
  ['vite', '--host', '127.0.0.1', '--port', String(VITE_PORT), '--strictPort'],
  { stdio: 'ignore' },
)
const proxy = startProxy()

let failed = false
try {
  if (!(await waitForHttp(`http://127.0.0.1:${PROXY_PORT}/`))) {
    throw new Error('не удалось поднять админку из исходников (vite + прокси)')
  }
  console.log('админка из исходников: http://127.0.0.1:' + PROXY_PORT)

  for (const mutation of mutations) {
    console.log('\n=== МУТАЦИЯ: ' + mutation.name + ' — ' + mutation.description + ' ===')
    let text = original
    if (mutation.fromTail) {
      text = text.replace(mutation.from, mutation.to).replace(mutation.fromTail, mutation.toTail)
    } else {
      text = text.replace(mutation.from, mutation.to)
    }
    if (text === original) throw new Error('мутация не применилась: ' + mutation.name)
    writeFileSync(SUBS_EDIT, text)
    // Даём vite пересобрать модуль.
    await sleep(2000)

    const brokenCode = await runPlaywright(mutation.spec)
    if (brokenCode === 0) {
      console.error('ПРОВАЛ: тест ' + mutation.spec + ' ПРОШЁЛ на сломанном коде')
      failed = true
    } else {
      console.log('OK: тест ' + mutation.spec + ' УПАЛ на сломанном коде (регрессия поймана)')
    }

    writeFileSync(SUBS_EDIT, original)
    await sleep(2000)
    const fixedCode = await runPlaywright(mutation.spec)
    if (fixedCode !== 0) {
      console.error('ПРОВАЛ: тест ' + mutation.spec + ' не проходит на исправленном коде')
      failed = true
    } else {
      console.log('OK: тест ' + mutation.spec + ' проходит на исправленном коде')
    }
  }
} finally {
  writeFileSync(SUBS_EDIT, original)
  vite.kill()
  proxy.close()
}

if (failed) {
  console.error('\nИТОГ: проверка не пройдена')
  process.exit(1)
}
console.log('\nИТОГ: все проверки «до/после» пройдены')
