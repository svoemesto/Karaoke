// BR-354 (аудит #336, пакет PKG-S3-28, OpenProject #400): авторские поля страницы песен
// автора не теряются при переходе на NDJSON-стрим.
//
// ЧТО БЫЛО. Шаблон страницы песен автора читает `zak.authorWarning`,
// `zak.authorDescription`, `zak.authorShortDescription`, `zak.authorSkip`, но `zak`
// приходил из стрима, а store собирал объект только из `author`, `authorPictureUrl`,
// `albums`, `albumTypeCounts`. Авторские поля приходили только в REST-версии
// (`ZakromaPublicDto`) — в стриме их не было, поэтому предупреждение автора
// (в т.ч. правовое «удалено по требованию правообладателя») на публичной странице
// не показывалось НИКОГДА.
//
// ЧТО СТАЛО. NDJSON-сообщение `meta` несёт авторские поля; composable их читает,
// store кладёт в объект автора.
//
// Запуск: cd karaoke-public && npm run test:unit

import { describe, test } from 'node:test'
import assert from 'node:assert/strict'

import { readCode } from './sourceContract.js'

const WEB_ROOT = '../karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb'
const APP_ROOT = '../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp'

describe('BR-354: авторские поля доезжают до страницы автора', () => {
  test('NDJSON-сообщение meta содержит авторские поля', () => {
    const src = readCode(`${WEB_ROOT}/dto/ZakromaStreamMessageDto.kt`)
    assert.ok(src.includes('authorDescription'), 'нужно поле authorDescription')
    assert.ok(src.includes('authorShortDescription'), 'нужно поле authorShortDescription')
    assert.ok(src.includes('authorWarning'), 'нужно поле authorWarning')
    assert.ok(src.includes('authorSkip'), 'нужно поле authorSkip')
  })

  test('контроллер стрима отдаёт авторские метаданные', () => {
    const src = readCode(`${WEB_ROOT}/controllers/PublicApiController.kt`)
    assert.ok(src.includes('loadPublicMetaByName'), 'нужна загрузка метаданных автора')
    assert.ok(src.includes('authorWarning = authorMeta.warning'), 'нужна передача warning в meta')
  })

  test('модель читает метаданные автора одним SELECT-ом', () => {
    const src = readCode(`${APP_ROOT}/model/Author.kt`)
    assert.ok(src.includes('fun loadPublicMetaByName'), 'нужен лёгкий raw-SELECT метаданных')
    assert.ok(src.includes('short_description'), 'нужен столбец short_description')
  })

  test('composable читает авторские поля из meta', () => {
    const src = readCode('src/composables/useZakromaStreamProgress.js')
    assert.ok(src.includes('msg.authorDescription'), 'нужен разбор authorDescription')
    assert.ok(src.includes('msg.authorWarning'), 'нужен разбор authorWarning')
    assert.ok(src.includes('msg.authorSkip'), 'нужен разбор authorSkip')
  })

  test('store кладёт авторские поля в объект автора', () => {
    const src = readCode('src/store/modules/zakroma.js')
    assert.ok(
      src.includes('authorWarning: result.authorWarning'),
      'нужен authorWarning в setZakroma',
    )
    assert.ok(
      src.includes('authorDescription: result.authorDescription'),
      'нужен authorDescription',
    )
    assert.ok(src.includes('authorSkip: result.authorSkip'), 'нужен authorSkip')
  })
})
