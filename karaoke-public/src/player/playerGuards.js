// BR-304 / BR-305 / BR-306 / BR-307 / BR-308 / BR-309 / BR-310 / BR-311
// (аудит #336, пакет PKG-S3-27, OpenProject #399): инварианты плеера.
//
// Модуль без зависимостей — тест запускается node --test без node_modules.

// ─── BR-304: ошибка init() ────────────────────────────────────────────────────────────────

export const INIT_ERROR = Object.freeze({
  IGNORE: 'ignore',
  SHOW: 'show',
})

/**
 * Отменён ли запрос (AbortError в любом браузере)?
 *
 * @param {Error|null} error
 * @returns {boolean}
 */
export function isAbortError(error) {
  if (!error) return false
  if (error.name === 'AbortError') return true
  // Chromium/Node: DOMException с кодом 20 (ABORT_ERR).
  if (error.code === 20) return true
  return /abort/i.test(String(error.message || ''))
}

/**
 * Что делать с ошибкой init(): показать её пользователю или молча уйти?
 *
 * Показывать НЕЛЬЗЯ, если запрос был отменён (пользователь переключил трек) или если загрузка
 * устарела (началась другая). Иначе ложная ошибка «The user aborted a request» висит поверх
 * видео следующего трека.
 *
 * @param {Error|null} error
 * @param {{aborted?: boolean, stale?: boolean}} [flags]
 * @returns {string} INIT_ERROR
 */
export function classifyInitError(error, flags = {}) {
  if (flags.aborted) return INIT_ERROR.IGNORE
  if (flags.stale) return INIT_ERROR.IGNORE
  if (isAbortError(error)) return INIT_ERROR.IGNORE
  return INIT_ERROR.SHOW
}

// ─── BR-305: пауза в pre-roll ───────────────────────────────────────────────────────────

/**
 * Время отображения в pre-roll (0 = старт splash, _preroll = старт аудио).
 *
 * На паузе в pre-roll отсчёт стоп-часов должен быть заморожен: `_getDisplayTime()` обязан
 * возвращать сохранённую позицию, иначе после паузы время прыгает на `_preroll` и возобновление
 * идёт по ветке «аудио уже началось» (минуя splash и silent offset).
 *
 * @param {{prerolling: boolean, paused: boolean, dtPaused: number, elapsedMs: number}} params
 * @returns {number|null} null — не в pre-roll
 */
export function prerollDisplayTime({ prerolling, paused, dtPaused, elapsedMs }) {
  if (!prerolling) return null
  const elapsed = paused ? 0 : Number(elapsedMs || 0) / 1000
  return Number(dtPaused || 0) + elapsed
}

// ─── BR-306: отложенное событие seek ────────────────────────────────────────────────────

/**
 * Трекер отложенной отправки seek-события.
 *
 * songId фиксируется В МОМЕНТ перемотки (а не в момент срабатывания таймера): иначе при
 * переходе на следующий трек событие уходит с id уже другой песни. Таймер снимается при
 * смене трека и при уничтожении плеера.
 *
 * @param {(songId: number|string, positionMs: number) => void} track
 * @returns {{schedule: Function, cancel: Function, pending: Function}}
 */
export function createSeekTracker(track) {
  let timer = null
  return {
    schedule(songId, dt, delayMs) {
      if (timer) clearTimeout(timer)
      timer = setTimeout(() => {
        timer = null
        track(songId, Math.round(dt))
      }, delayMs)
    },
    cancel() {
      if (timer) {
        clearTimeout(timer)
        timer = null
      }
    },
    pending() {
      return timer !== null
    },
  }
}

// ─── BR-307: поколение загрузки ─────────────────────────────────────────────────────────

/**
 * Устарела ли загрузка (плеер/трек сменились, пока шёл динамический import)?
 *
 * @param {number} currentGeneration — текущее поколение
 * @param {number} myGeneration — поколение, с которым начата загрузка
 * @returns {boolean}
 */
export function isStaleGeneration(currentGeneration, myGeneration) {
  return currentGeneration !== myGeneration
}

// ─── BR-308: транспонирование в главном потоке ─────────────────────────────────────────

export const TRANSPOSE_CHUNK = 4096
export const TRANSPOSE_CHUNKS_PER_YIELD = 8

/**
 * Нужно ли уступить главный поток между чанками транспонирования.
 *
 * Без уступки цикл `while (written < outLength)` крутит SoundTouch по всем сэмплам 5-минутной
 * песни без единого `await` — UI замирает на секунды.
 *
 * @param {number} writtenFrames — сколько фреймов уже записано
 * @param {number} [chunkSize]
 * @param {number} [chunksPerYield]
 * @returns {boolean}
 */
export function transposeYieldDue(
  writtenFrames,
  chunkSize = TRANSPOSE_CHUNK,
  chunksPerYield = TRANSPOSE_CHUNKS_PER_YIELD,
) {
  if (!writtenFrames) return false
  const stride = chunkSize * chunksPerYield
  return writtenFrames % stride === 0
}

// ─── BR-309: перегенерация фоновой текстуры ─────────────────────────────────────────────

/**
 * Нужно ли генерировать фоновую текстуру заново.
 *
 * Текстура 4096×4096 (≈64 МиБ) генерировалась на КАЖДЫЙ `init()` — то есть на каждый трек
 * плейлиста. Генерируем один раз на инстанс: пик памяти и джанк — не на каждом переходе.
 *
 * @param {*} currentCanvas — уже созданная текстура (null/undefined — её нет)
 * @returns {boolean}
 */
export function shouldGenerateStarfield(currentCanvas) {
  return !currentCanvas
}

// ─── BR-310: localStorage без защиты ────────────────────────────────────────────────────

/**
 * Безопасное чтение числа из хранилища.
 *
 * В песочнице iframe без `allow-same-origin` (и в некоторых приватных режимах) обращение к
 * `localStorage` бросает `SecurityError`. Необязательное чтение (сохранённый сдвиг
 * тональности) не должно ронять плеер.
 *
 * @param {Storage|null} storage
 * @param {string} key
 * @returns {number|null} null — значения нет или хранилище недоступно
 */
export function readStoredNumber(storage, key) {
  if (!storage) return null
  try {
    const raw = storage.getItem(key)
    if (raw === null || raw === undefined) return null
    const n = Number(raw)
    return Number.isFinite(n) ? n : null
  } catch (_) {
    return null
  }
}

// ─── BR-311: feature-detection Canvas API ──────────────────────────────────────────────

/**
 * Создать поверхность для фона: OffscreenCanvas, а при его отсутствии — обычный <canvas>.
 *
 * @param {number} width
 * @param {number} height
 * @param {{OffscreenCanvas?: Function, document?: Document}} [deps]
 * @returns {object|null} canvas
 */
export function createBackgroundCanvas(width, height, deps = {}) {
  const OffscreenCanvasCtor =
    deps.OffscreenCanvas !== undefined
      ? deps.OffscreenCanvas
      : typeof globalThis !== 'undefined'
        ? globalThis.OffscreenCanvas
        : undefined
  if (typeof OffscreenCanvasCtor === 'function') {
    return new OffscreenCanvasCtor(width, height)
  }
  const doc = deps.document !== undefined ? deps.document : globalThis.document
  if (!doc || typeof doc.createElement !== 'function') return null
  const canvas = doc.createElement('canvas')
  canvas.width = width
  canvas.height = height
  return canvas
}

/**
 * Есть ли `CanvasRenderingContext2D.roundRect` (Safari 16.0–16.3, Firefox < 112 — нет).
 *
 * @param {object} ctx
 * @returns {boolean}
 */
export function hasRoundRect(ctx) {
  return !!(ctx && typeof ctx.roundRect === 'function')
}

/**
 * Прямоугольник со скруглением — с fallback для браузеров без `roundRect`.
 *
 * @param {object} ctx
 * @param {number} x
 * @param {number} y
 * @param {number} w
 * @param {number} h
 * @param {number} r — радиус
 */
export function roundRectPath(ctx, x, y, w, h, r) {
  if (hasRoundRect(ctx)) {
    ctx.roundRect(x, y, w, h, r)
    return
  }
  // Fallback: прямоугольник + дуги (arcTo) — визуально почти то же, что roundRect.
  const radius = Math.max(0, Math.min(r, Math.min(w, h) / 2))
  ctx.beginPath()
  ctx.moveTo(x + radius, y)
  ctx.lineTo(x + w - radius, y)
  ctx.arcTo(x + w, y, x + w, y + radius, radius)
  ctx.lineTo(x + w, y + h - radius)
  ctx.arcTo(x + w, y + h, x + w - radius, y + h, radius)
  ctx.lineTo(x + radius, y + h)
  ctx.arcTo(x, y + h, x, y + h - radius, radius)
  ctx.lineTo(x, y + radius)
  ctx.arcTo(x, y, x + radius, y, radius)
  ctx.closePath()
}
