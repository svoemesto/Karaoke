// BR-397 (аудит #336, пакет PKG-RACE-TARGET, OpenProject #367).
//
// Гонка: сохранение шаблона новости — две последовательные POST (title, затем body), причём
// target и bodyValue читались в момент КАЖДОГО запроса. Переключение «Целевой БД» в окне между
// ними уводило title в одну БД, body — в другую, а reload() по watcher'у успевал подменить
// bodyValue значением из новой БД.
//
// В тесте рядом с исправленной цепочкой стоит её прежняя форма (legacyChain, копия строк
// компонента до правки) — она доказывает, что шов действительно воспроизводит гонку: на ней
// второй запрос уходит в другой target с чужим body. Исправленная цепочка обязана этого не делать.
//
// Запуск: cd webvue3 && npm run test:unit

import { describe, test } from 'node:test'
import assert from 'node:assert/strict'

import { saveTemplateChain, saveTemplateFromEditor } from '../templateSaveChain.js'

/**
 * Прежняя форма сохранения (до BR-397): target и bodyValue читаются в момент каждого запроса.
 * Оставлена в тесте как эталон «как было» — не вызывается из кода продукта.
 */
function legacyChain({ editor, saveOne }) {
  return Promise.resolve(saveOne(editor.titleKey, editor.titleValue, editor.target)).then(() =>
    saveOne(editor.bodyKey, editor.bodyValue, editor.target),
  )
}

/** Редактор с изменяемыми «живыми» полями — переключение target и reload между запросами. */
function createEditor() {
  return {
    target: 'local',
    titleKey: 'newsTemplateAirTitle',
    bodyKey: 'newsTemplateAirBody',
    titleValue: 'Заголовок',
    bodyValue: 'Текст из local',
  }
}

describe('BR-397: сохранение шаблона новости не расщепляется между БД', () => {
  test('шов воспроизводит гонку: legacy-цепочка уводит body в другую БД', async () => {
    const editor = createEditor()
    const calls = []
    const saveOne = (key, value, target) => {
      calls.push({ key, value, target })
      // После первого запроса админ переключил target, watcher вызвал reload и подменил body.
      if (calls.length === 1) {
        editor.target = 'remote'
        editor.bodyValue = 'Текст из remote'
      }
      return Promise.resolve()
    }

    await legacyChain({ editor, saveOne })

    assert.deepEqual(
      calls.map((call) => call.target),
      ['local', 'remote'],
      'на старом коде title и body уходят в разные БД — это и есть воспроизведённая гонка',
    )
    assert.equal(calls[1].value, 'Текст из remote', 'во вторую БД уходит чужой body')
  })

  test('исправленная цепочка фиксирует target и оба значения на всю операцию', async () => {
    const editor = createEditor()
    const calls = []
    const saveOne = (key, value, target) => {
      calls.push({ key, value, target })
      if (calls.length === 1) {
        editor.target = 'remote'
        editor.bodyValue = 'Текст из remote'
      }
      return Promise.resolve()
    }

    // Прод-путь: компонент передаёт себя, а снимок target/полей делает сама функция.
    await saveTemplateFromEditor({ editor, saveOne })

    assert.deepEqual(
      calls.map((call) => call.target),
      ['local', 'local'],
      'оба поля должны уйти в БД, выбранную в момент нажатия «Сохранить»',
    )
    assert.equal(calls[1].value, 'Текст из local', 'body — снимок на момент сохранения')
  })

  test('запросы идут последовательно: body не отправляется до ответа на title', async () => {
    const editor = createEditor()
    const events = []
    let releaseTitle
    const titleGate = new Promise((resolve) => {
      releaseTitle = resolve
    })
    const saveOne = (key) => {
      events.push(`start:${key}`)
      if (key === editor.titleKey) {
        return titleGate.then(() => events.push(`done:${key}`))
      }
      events.push(`done:${key}`)
      return Promise.resolve()
    }

    const chain = saveTemplateChain({
      target: 'local',
      titleKey: editor.titleKey,
      titleValue: editor.titleValue,
      bodyKey: editor.bodyKey,
      bodyValue: editor.bodyValue,
      saveOne,
    })

    await Promise.resolve()
    assert.deepEqual(events, ['start:newsTemplateAirTitle'], 'body ждёт ответа title')
    releaseTitle()
    await chain
    assert.deepEqual(events, [
      'start:newsTemplateAirTitle',
      'done:newsTemplateAirTitle',
      'start:newsTemplateAirBody',
      'done:newsTemplateAirBody',
    ])
  })
})
