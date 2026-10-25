// Регрессионные тесты пакета PKG-S3-32 (аудит #336, OpenProject #404) — правки песен.
//
// Записи пакета в этом файле:
//   BR-429 — загрузка/поиск «похожих песен» шли через `try/finally` без `catch`: сбой
//            превращался в «Ничего не найдено», прежний список выдавался за новый.
//   BR-432 — отказ получения текущей обложки подменялся сообщением «Картинки альбома
//            пока нет», а отказ чтения файла с диска не показывался вовсе.
//   BR-434 — word-level diff строил полную LCS-матрицу (квадратичная память/время).
//   BR-443 — геттер фильтра песен расходился с модалкой: `filterFlagFree` вместо
//            `flagFree` и отсутствие `target` для фильтра «Задание редактора».
//
// Запуск: cd webvue3 && node --test src/components/Songs/edit/__tests__/pkg-s3-32-songs-edit.test.js

import { test, describe } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

/**
 * Подключает модуль стора ДИНАМИЧЕСКИ: на старом коде стор фильтра импортировал
 * `../../../lib/utils` БЕЗ расширения — такой импорт не резолвится в Node ESM, и
 * обычный статический import уронил бы весь файл с «module not found». Динамический
 * импорт обёрнут в catch, поэтому на старом коде тест падает с AssertionError.
 *
 * @param {string} specifier — путь к модулю относительно теста.
 * @returns {Promise<Object|null>} default-экспорт модуля либо null.
 */
const importStore = async (specifier) => {
  try {
    const mod = await import(specifier)
    return mod.default || mod
  } catch (_e) {
    return null
  }
}

const songsFilter = await importStore('../../filter/store.js')

const stripComments = (source) =>
  source
    .replace(/<!--[\s\S]*?-->/g, '')
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .replace(/(^|[^:])\/\/[^\n]*/g, '$1')

const FAMILY_SONGS_MODAL = stripComments(
  readFileSync(new URL('../FamilySongsModal.vue', import.meta.url), 'utf-8'),
)
const ALBUM_COVER_MODAL = stripComments(
  readFileSync(new URL('../AlbumCoverModal.vue', import.meta.url), 'utf-8'),
)
const AI_TEXT_EDITOR_MODAL = stripComments(
  readFileSync(new URL('../AiTextEditorModal.vue', import.meta.url), 'utf-8'),
)
const SONGS_FILTER_MODAL = stripComments(
  readFileSync(new URL('../../filter/SongsFilterModal.vue', import.meta.url), 'utf-8'),
)

/**
 * Состояние фильтра песен с заданными значениями.
 *
 * @param {Object} overrides — переопределяемые поля.
 * @returns {Object} state фильтра.
 */
const filterState = (overrides = {}) => ({
  songsFilterFlagFree: '',
  songsFilterId: '',
  songsFilterSongName: '',
  songsFilterSongAuthor: '',
  songsFilterSongAlbum: '',
  songsFilterPublishDate: '',
  songsFilterPublishTime: '',
  songsFilterIdStatus: '',
  songsFilterCountVoices: '',
  songsFilterTags: '',
  songsFilterResultVersion: '',
  songsFilterVersionBoosty: '',
  songsFilterVersionBoostyFiles: '',
  songsFilterVersionSponsr: '',
  songsFilterVersionDzenKaraoke: '',
  songsFilterVersionVkKaraoke: '',
  songsFilterVersionTelegramKaraoke: '',
  songsFilterVersionPlKaraoke: '',
  songsFilterVersionMaxKaraoke: '',
  songsFilterRate: '',
  songsFilterIsSync: '',
  songsFilterStatusProcessLyrics: '',
  songsFilterStatusProcessKaraoke: '',
  songsFilterStatusProcessDemo: '',
  songsFilterRootId: '',
  songsFilterAudioParentId: '',
  songsFilterAssignmentStatus: '',
  songsFilterSongType: '',
  ...overrides,
})

describe('PKG-S3-32 / Правки песен', () => {
  describe('BR-429 — «похожие песни» различают сбой и пустой результат', () => {
    test('есть состояние ошибки загрузки', () => {
      assert.match(FAMILY_SONGS_MODAL, /loadError/, 'BR-429: нужен текст ошибки загрузки')
      assert.match(
        FAMILY_SONGS_MODAL,
        /mounted\s*\(\)[\s\S]*catch\s*\(/,
        'BR-429: загрузка обязана ловить отказ',
      )
      assert.match(
        FAMILY_SONGS_MODAL,
        /doSearch\(\)[\s\S]*catch\s*\(/,
        'BR-429: поиск обязан ловить отказ',
      )
    })

    test('ошибка показывается вместо «Ничего не найдено»', () => {
      assert.match(
        FAMILY_SONGS_MODAL,
        /v-else-if="loadError"/,
        'BR-429: ошибка должна показываться вместо «ничего не найдено»',
      )
      assert.match(
        FAMILY_SONGS_MODAL,
        /результат не обновлён/,
        'BR-429: прежний список должен помечаться как устаревший',
      )
    })
  })

  describe('BR-432 — обложка альбома различает отказ и отсутствие', () => {
    test('отказ получения текущей обложки даёт отдельный текст', () => {
      assert.match(ALBUM_COVER_MODAL, /currentError/, 'BR-432: нужен текст ошибки')
      assert.match(
        ALBUM_COVER_MODAL,
        /loadCurrentPicture\(\)[\s\S]*catch\s*\(/,
        'BR-432: загрузка обложки обязана ловить отказ',
      )
      assert.match(
        ALBUM_COVER_MODAL,
        /Не удалось получить текущую обложку/,
        'BR-432: отказ не должен подменяться сообщением «Картинки альбома пока нет»',
      )
    })

    test('отказ чтения файла с диска показывается', () => {
      assert.match(
        ALBUM_COVER_MODAL,
        /onDiskFileSelected\(path\)[\s\S]*catch\s*\([\s\S]*Не удалось прочитать файл/,
        'BR-432: отказ чтения файла должен показываться',
      )
    })
  })

  describe('BR-434 — diff текста ограничен по длине', () => {
    test('используется общий модуль text-diff', () => {
      assert.match(
        AI_TEXT_EDITOR_MODAL,
        /import \{ buildDiffHtml \} from '\.\.\/\.\.\/\.\.\/lib\/text-diff\.js'/,
        'BR-434: diff должен считаться в общем модуле (предел длины + вырезание префикса)',
      )
      assert.doesNotMatch(
        AI_TEXT_EDITOR_MODAL,
        /const dp = new Array\(n \+ 1\)/,
        'BR-434: полная LCS-матрица в компоненте осталась',
      )
    })

    test('есть сообщение о пропущенной подсветке', () => {
      assert.match(AI_TEXT_EDITOR_MODAL, /diffTruncated/, 'BR-434: нужен признак усечения')
      assert.match(
        AI_TEXT_EDITOR_MODAL,
        /v-if="diffTruncated"/,
        'BR-434: пользователь должен знать, что подсветка не вычислена',
      )
    })
  })

  describe('BR-443 — параметры фильтра песен собираются одним builder’ом', () => {
    test('ключ «Free» соответствует контракту бэкенда', () => {
      assert.ok(songsFilter, 'модуль стора фильтра песен должен быть подключаем')
      const state = filterState({ songsFilterFlagFree: 'true' })
      const params = songsFilter.getters.getSongsFilter(state, {}, state, {
        getEditorDefaultTarget: 'remote',
      })
      assert.equal(
        params.flagFree,
        'true',
        'BR-443: бэкенд принимает `flagFree`, а не `filterFlagFree`',
      )
      assert.equal(
        params.filterFlagFree,
        undefined,
        'BR-443: ключ filterFlagFree бэкенд не принимает',
      )
    })

    test('к «Заданию редактора» добавляется target', () => {
      assert.ok(songsFilter, 'модуль стора фильтра песен должен быть подключаем')
      const state = filterState({ songsFilterAssignmentStatus: 'unassigned' })
      const params = songsFilter.getters.getSongsFilter(state, {}, state, {
        getEditorDefaultTarget: 'remote',
      })
      assert.equal(params.filterAssignmentStatus, 'unassigned')
      assert.equal(
        params.target,
        'remote',
        'BR-443: без target статусы назначений читались не из той БД',
      )
    })

    test('модалка использует общий builder', () => {
      assert.match(
        SONGS_FILTER_MODAL,
        /const params = this\.\$store\.getters\.getSongsFilter/,
        'BR-443: модалка должна использовать общий сборщик параметров',
      )
      assert.doesNotMatch(
        SONGS_FILTER_MODAL,
        /params\.filterFlagFree/,
        'BR-443: в модалке не должно быть ключа filterFlagFree',
      )
    })
  })
})
