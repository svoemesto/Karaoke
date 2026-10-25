// Регрессионные тесты пакета PKG-S3-31 (аудит #336, OpenProject #403) — карточка песни.
//
// Записи пакета в этом файле:
//   BR-089 — блок мелодии/табов во вкладке Max скрыт условием `showMelodyVk`.
//   BR-090 — нормализация `idSponsr` писала «manage» вместо id поста.
//   BR-091 — ответ форматтеров применялся к другой песне.
//   BR-092 — числовые поля карточки — свободный текст без валидации.
//   BR-126 — message_id ручной публикации в Telegram не попадал в карточку.
//   BR-127 — асинхронные операции карточки не обрабатывали отказ.
//
// Проверки статические (по тексту исходника): без стенда и без Vue Test Utils это
// единственный способ зафиксировать инварианты карточки. Каждая проверка на старом
// коде даёт AssertionError.
//
// Запуск: cd webvue3 && node --test src/components/Songs/edit/__tests__/pkg-s3-31-songedit.test.js

import { test, describe } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync, existsSync } from 'node:fs'

const stripComments = (source) =>
  source.replace(/\/\*[\s\S]*?\*\//g, '').replace(/(^|[^:])\/\/[^\n]*/g, '$1')

const SONG_EDIT = stripComments(readFileSync(new URL('../SongEdit.vue', import.meta.url), 'utf-8'))

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

describe('PKG-S3-31 / SongEdit', () => {
  describe('BR-089 — вкладка Max показывает свою мелодию', () => {
    test('блок мелодии/табов во вкладке Max использует showMelodyMax', () => {
      const marker = 'openLinkMaxTabs'
      const index = SONG_EDIT.indexOf(marker)
      assert.ok(index > 0, 'BR-089: не найден блок вкладки Max (openLinkMaxTabs)')
      const blockStart = SONG_EDIT.lastIndexOf('<div v-if="showMelody', index)
      assert.ok(blockStart > 0, 'BR-089: не найден v-if блока мелодии во вкладке Max')
      const block = SONG_EDIT.slice(blockStart, index)
      assert.match(
        block,
        /showMelodyMax/,
        'BR-089: блок вкладки Max обязан использовать showMelodyMax, а не showMelodyVk',
      )
      assert.doesNotMatch(
        block,
        /showMelodyVk/,
        'BR-089: при пустом idVkMelody весь блок Max был скрыт',
      )
    })

    test('вычисляемое showMelodyMax используется в шаблоне', () => {
      assert.match(
        SONG_EDIT,
        /v-if="showMelodyMax"/,
        'BR-089: showMelodyMax обязан использоваться хотя бы в одном месте шаблона',
      )
    })
  })

  describe('BR-090 — id поста Sponsr извлекается из обоих видов URL', () => {
    test('есть метод extractSponsrId с регулярным выражением', () => {
      const body = methodBody(SONG_EDIT, 'extractSponsrId')
      assert.ok(body, 'BR-090: не найден метод extractSponsrId')
      assert.match(body, /smkaraoke/, 'BR-090: регулярное выражение обязано искать /smkaraoke/')
      assert.match(body, /manage\\\/post/, 'BR-090: edit-URL (manage/post/<id>) тоже допустим')
      assert.match(body, /\\d\+/, 'BR-090: id — только цифры')
    })

    test('watcher idSponsr не берёт первый сегмент пути', () => {
      const watcher = /'song\.idSponsr\.value'\s*:\s*\{[\s\S]*?\n    \},/.exec(SONG_EDIT)
      assert.ok(watcher, 'BR-090: не найден watcher song.idSponsr.value')
      assert.doesNotMatch(
        watcher[0],
        /\.split\('\/'\)\[0\]/,
        'BR-090: первый сегмент пути — это «manage», а не id поста',
      )
      assert.match(watcher[0], /extractSponsrId/, 'BR-090: watcher обязан звать extractSponsrId')
    })
  })

  describe('BR-091 — ответ форматтеров применяется только к своей песне', () => {
    test('watcher song фиксирует id песни на момент запроса', () => {
      const watcher = /\n    song:\s*\{[\s\S]*?\n    \},\n/.exec(SONG_EDIT)
      assert.ok(watcher, 'BR-091: не найден watcher song')
      assert.match(
        watcher[0],
        /const requestedId = this\.song && this\.song\.id/,
        'BR-091: id песни фиксируется на момент запроса',
      )
      assert.match(
        watcher[0],
        /this\.song\.id === requestedId/,
        'BR-091: ответ другой песни не должен попадать в превью',
      )
    })
  })

  describe('BR-092 — числовые поля валидируются до сохранения', () => {
    test('есть модуль проверки числовых полей песни', async () => {
      const moduleUrl = new URL('../../../../lib/song-numeric-fields.js', import.meta.url)
      assert.ok(
        existsSync(moduleUrl),
        'BR-092: должен быть модуль lib/song-numeric-fields.js с проверкой числовых полей',
      )
      const module = await import(moduleUrl.href)
      assert.equal(module.isValidNumericSongField('year', '2024'), true)
      assert.equal(module.isValidNumericSongField('year', '20x4'), false)
      assert.equal(module.isValidNumericSongField('year', '2024—2025'), false)
      assert.equal(module.isValidNumericSongField('year', ''), true, 'пустое поле допустимо')
      assert.equal(module.isValidNumericSongField('bpm', '120'), true)
      assert.equal(module.isValidNumericSongField('author', 'что угодно'), true, 'не числовое поле')
      assert.equal(module.isValidNumericSongField('audioDeltaMs', '-15'), true)
      const invalid = module.findInvalidNumericSongFields([
        { name: 'year', new: '20x4' },
        { name: 'author', new: 'Песня' },
      ])
      assert.equal(invalid.length, 1, 'невалидным считается только числовое поле')
      assert.equal(invalid[0].name, 'year')
    })

    test('performSave не отправляет невалидное числовое поле', () => {
      const body = methodBody(SONG_EDIT, 'performSave')
      assert.ok(body, 'BR-092: не найден метод performSave')
      assert.match(
        body,
        /findInvalidNumericSongFields/,
        'BR-092: перед отправкой числовые поля обязаны проверяться',
      )
      assert.match(
        body,
        /isValidNumericSongField/,
        'BR-092: невалидное поле не должно попадать в params',
      )
      assert.match(body, /showTelegramToast/, 'BR-092: пользователь должен увидеть ошибку')
    })
  })

  describe('BR-126 — message_id ручной публикации попадает в карточку', () => {
    test('publishToTelegramNow записывает idTelegramDemo при непустом messageId', () => {
      const body = methodBody(SONG_EDIT, 'publishToTelegramNow')
      assert.ok(body, 'BR-126: не найден метод publishToTelegramNow')
      assert.match(
        body,
        /name: 'idTelegramDemo'/,
        'BR-126: id опубликованного сообщения должен попасть в карточку',
      )
      assert.match(body, /data\.messageId/)
    })

    test('«уже опубликовано» не показывается как ошибка', () => {
      const body = methodBody(SONG_EDIT, 'publishToTelegramNow')
      assert.ok(body, 'BR-126: не найден метод publishToTelegramNow')
      assert.match(
        body,
        /state === 'published'/,
        'BR-126: success=false + state=published — это «уже опубликовано», а не ошибка',
      )
    })
  })

  describe('BR-127 — асинхронные операции карточки обрабатывают отказ', () => {
    test('findAudioParent: разбор ответа в try/catch и сообщение об ошибке', () => {
      const body = methodBody(SONG_EDIT, 'findAudioParent')
      assert.ok(body, 'BR-127: не найден метод findAudioParent')
      assert.match(body, /catch/, 'BR-127: отказ обязан обрабатываться')
      assert.match(body, /showTelegramToast/, 'BR-127: пользователь должен видеть ошибку')
      const parseCount = (body.match(/JSON\.parse/g) || []).length
      const catchCount = (body.match(/catch/g) || []).length
      assert.ok(
        parseCount <= catchCount,
        'BR-127: JSON.parse должен быть защищён (пустое тело ответа «песня не найдена»)',
      )
    })

    test('showSubsEdit: голоса проверяются до использования', () => {
      const body = methodBody(SONG_EDIT, 'showSubsEdit')
      assert.ok(body, 'BR-127: не найден метод showSubsEdit')
      assert.match(body, /catch/, 'BR-127: отказ обязан обрабатываться')
      assert.match(body, /\.voices/, 'BR-127: наличие voices проверяется')
      assert.match(body, /if \(!voices\)/, 'BR-127: без voices модалка не открывается')
    })

    test('doDeleteSong дожидается ответа и закрывает карточку только при успехе', () => {
      const body = methodBody(SONG_EDIT, 'doDeleteSong')
      assert.ok(body, 'BR-127: не найден метод doDeleteSong')
      assert.match(body, /\.then\(/, 'BR-127: удаление должно дожидаться ответа')
      assert.match(body, /catch/, 'BR-127: отказ удаления обязан обрабатываться')
      const thenBody = body.slice(body.indexOf('.then('), body.indexOf('.catch('))
      assert.match(
        thenBody,
        /\$emit\('close'\)/,
        'BR-127: карточка закрывается только после успешного удаления',
      )
    })

    test('closeSubsEdit: у цепочек форматтеров есть обработчик отказа', () => {
      const body = methodBody(SONG_EDIT, 'closeSubsEdit')
      assert.ok(body, 'BR-127: не найден метод closeSubsEdit')
      assert.match(body, /\.catch\(/, 'BR-127: отказ перезапроса форматтеров обязан обрабатываться')
      assert.match(body, /isUnmounted/, 'BR-127: запись в размонтированный компонент запрещена')
    })
  })
})
