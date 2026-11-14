# Отчёт по KARAOKE-400 «karaoke-public: нет юнит-тестов для services/songShareLink.js (claim/heartbeat/release)»

> **Задача**: KARAOKE-400 (Plane, workspace `svoemesto`, проект `KARAOKE`).
> **Ветка**: `769-song-share-link-tests` (feature-ветка от `master`).
> **Дата**: 2026-11-14.
> **Статус**: добавлен `karaoke-public/src/services/__tests__/songShareLink.test.js` — 20 кейсов.

## 1. Что требовалось

В `karaoke-public/src/services/songShareLink.js` — хелперы «Временного полного доступа
к песне» (`browserId`, `sha256Hex`, `browserHash`, `claimShare`, `heartbeat`, `release`).
Модуля не было ни в одном tests-каталоге публичного SPA (каталога `services/__tests__`
вообще не существовало), хотя это код зоны безопасности: анонимный claim по секрету из
URL, продление lease heartbeat'ом и освобождение сессии.

Что сделать:
- создать `karaoke-public/src/services/__tests__/songShareLink.test.js` по образцу
  существующих тестов публичного SPA;
- покрыть контракты: `claimShare` — разбор JSON-ответа и сетевой отказ (reject
  `network_error`); `heartbeat` — то же; `release` — использует `navigator.sendBeacon`,
  при его отсутствии — XHR; `getBrowserId` — стабильный между вызовами и сохраняется
  в `localStorage`; `getBrowserHash` — sha256 от `'browser:' + id` (детерминированно,
  через подменённый `crypto.subtle`);
- тесты не должны ходить в сеть: XHR подменяется;
- продовый код менять только если тест вскроет реальный дефект.

Критерии готовности: `npm run test:unit` — зелёный, новый тест проходит;
`npm run lint:check` — зелёный; изменения закоммичены.

## 2. Что сделано

Добавлен один файл: `karaoke-public/src/services/__tests__/songShareLink.test.js`
(20 кейсов, 5 describe-блоков). **Продовый код не менялся** — тесты покрывают текущее
поведение модуля как есть.

### 2.1. Инфраструктура теста

- **XHR подменяется, а не `fetch`.** Сетевой слой `songShareLink.js` сделан на
  `XMLHttpRequest` во всех трёх сценариях — подмена `fetch` не перехватила бы ничего
  (это ровно тот случай, что описан в
  `knowledge/system/agent-mechanics-cases.md`, прецедент KARAOKE-400: модель переписала
  прод с XHR на `fetch` вместо подмены XHR в тесте).
- **`globalThis.navigator`, `globalThis.crypto`, `globalThis.localStorage`** подменяются
  через `Object.defineProperty(..., { value, configurable: true })` — в Node 21+ это
  геттеры без сеттера, присваивание падало бы в строгом режиме ES-модуля. Исходные
  дескрипторы восстанавливаются в `afterEach`.
- **Состояние мока сбрасывается в `beforeEach`**, у describe-блоков — `{ concurrency: 1 }`,
  у асинхронных тестов — свой таймаут (`{ timeout: 5000 }`). Раннер Node выполняет
  соседние describe-блоки параллельно, а мок с состоянием общий.
- **Микроtask'и разряжаются перед проверкой XHR**: `claimShare` сначала ждёт
  `getBrowserHash()` → `crypto.subtle.digest`, и только потом создаёт XHR, поэтому
  используется `await new Promise((r) => setTimeout(r, 0))`.
- **Сеть не задействована**: единственный сетевой объект — подменённый `FakeXHR`.

### 2.2. Покрытые контракты

| Блок | Кейсы |
|---|---|
| Идентификатор устройства | стабильность между вызовами; сохранение в `localStorage` под ключом `km_share_browser_id`; сохранённый id используется повторно (новый UUID не генерируется); ветка без `randomUUID` (через `getRandomValues`) |
| Хеш браузера | `browserHash` = sha256 от `'browser:' + browserId` (проверяются алгоритм `SHA-256`, байты на входе и 64 hex-символа на выходе — детерминированно, через подменённый `crypto.subtle`); `sha256Hex('')` кодирует ноль байт; `sha256Hex(null)` → пустая строка |
| Анонимный claim | `POST /api/public/share/claim`, заголовок `Content-type: application/json`, тело `{ secret, browserHash }` (ровно эти два поля); разбор JSON-ответа; не-JSON ответ → `body = null`; пустой ответ → `body = null`; сетевой отказ → reject `network_error` |
| Heartbeat | `POST /api/public/share/heartbeat`, тело `{ sessionTokenHash }` (ровно одно поле); разбор JSON-ответа; истёкший lease — `status = 410` в теле ответа; сетевой отказ → reject `network_error` |
| Release | при наличии `navigator.sendBeacon` запрос уходит через beacon и **XHR не создаётся**; результат по умолчанию `closed`, переданный результат сохраняется; без `sendBeacon` — уходит в XHR (`POST /api/public/share/release`, тело `{ sessionTokenHash, result }`); при падении `sendBeacon` — fallback в XHR |

Сверка payload'ов с бэкендом (`PublicShareController`): `claim` читает `secret` +
`browserHash`, `heartbeat` — `sessionTokenHash`, `release` — `sessionTokenHash` + `result`
(и JSON, и form-urlencoded). Фронтенд отправляет ровно эти поля.

## 3. Проверки

| Проверка | Результат |
|---|---|
| `node --test src/services/__tests__/songShareLink.test.js` | 20 кейсов, 0 падений |
| `cd karaoke-public && npm run test:unit` | **368 тестов, 0 падений** (было 348, +20) |
| `cd karaoke-public && npm run lint:check` | зелёный (0 ошибок, 0 предупреждений) |
| `cd karaoke-public && npm run format:check` | зелёный |

## 4. Что НЕ менялось

- **Продовый код публичного SPA** — ни строки. Изменены только новый тестовый файл и
  знание.
- Остальные зависимости, сборка, e2e — без изменений.

## 5. Найденные расхождения (в код не вносились)

Продуктовый код не трогался (задача — «написать тест»; по
`knowledge/system/agent-mechanics-cases.md` в этом случае прод-файлы не меняются), но
при написании тестов всплыли расхождения между KDoc и фактическим поведением:

1. `claimShare` — KDoc обещает `Promise<{ linkId, sessionTokenHash, redirectTo } | null
   при ошибке>`, фактически промис **всегда** резолвится в `{ status, body }`, а при
   сетевом отказе — reject с `network_error` (значения `null` не бывает).
2. `heartbeat` — KDoc обещает `{ ok: true }` либо `{ status: 410 }`, фактически
   возвращается `{ status, body }`, где `body` — разобранный JSON ответа.

Тесты фиксируют **фактическое** поведение (`{ status, body }`). Расхождение KDoc ↔ код —
вопрос для владельца: править комментарий или приводить код к обещанному контракту.
Возможная причина расхождения — `claimShare`/`heartbeat` переводились с `fetch` на XHR
(прецедент KARAOKE-400 в `agent-mechanics-cases.md`), а KDoc остался от прежней версии.

## 6. Знание (MUST #0, выполнено)

Произведён поиск по `knowledge/` и прочитаны релевантные документы:
`knowledge/system/frontend/karaoke-public-tests.md` (правила тестов публичного SPA:
подмена `navigator` через `defineProperty`, ветка `sendBeacon`, параллельность
describe-блоков), `knowledge/system/agent-mechanics-cases.md` (прецедент с подменой
`fetch` вместо XHR), `knowledge/domains/karaoke-web/components/public-controllers-6.md`
(контракт `PublicShareController`).

Обновлено: `knowledge/system/frontend/karaoke-public-tests.md` — строка нового тестового
файла в таблице, счётчики (16 → 17 файлов, 279 → 299 кейсов), и два уточнения
наблюдений: `crypto`/`localStorage` в Node тоже геттеры без сеттера, и перед проверкой
XHR нужно разрядить микроtask'и (в `claimShare` XHR создаётся после `await` дайджеста).
