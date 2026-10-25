import { promisedXMLHttpRequest } from '../../lib/utils.js'

// Таймер debounce для тихой перезагрузки дайджеста публикаций (см. schedulePublishReload ниже).
let publishReloadTimer = null

// 'dd.mm.yy' -> 'yymmdd' - в таком виде даты сравнимы лексикографически (тот же приём,
// что и на бэкенде для сортировки строк сетки по SongField.DATE, см. CrossSong.kt).
function dateKey(str) {
  return str ? str.split('.').reverse().join('') : ''
}

// Попадает ли dateStr в загруженный диапазон [from, to]. Пустые from/to считаем "диапазон неизвестен" -
// в этом случае решаем не рисковать и не считаем дату входящей (лишний silent-reload безопаснее пропуска).
function isDateInLoadedRange(dateStr, from, to) {
  if (!dateStr || !from || !to) return false
  const key = dateKey(dateStr)
  return dateKey(from) <= key && key <= dateKey(to)
}

// Ищет DTO песни в уже загруженной сетке публикаций по id, не мутируя state.
function findCellDTO(publishDigest, songId) {
  for (const publishRow of publishDigest) {
    for (const csrCell of publishRow.csrCells) {
      if (csrCell.songDTO && csrCell.songDTO.id === songId) {
        return csrCell.songDTO
      }
    }
  }
  return null
}

// Общий fetch дайджеста публикаций - используется и loadPublishDigest (со спиннером),
// и reloadPublishDigestSilently (тихий фоновый reload по SSE).
async function fetchPublishDigest(params) {
  let request = { method: 'POST', url: '/api/publicationsdigest', params: params }
  let data = await promisedXMLHttpRequest(request)
  let result = JSON.parse(data)
  return result.publicationsDigest
}

/**
 * Компонент «Store».
 *
 * @see AGENTS.md
 */

export default {
  state: {
    publishDigest: [],
    publishDigestIsLoading: false,
    // BR-412: активный cond-вид ('unpublish' | 'undone' | 'skiped' | ...).
    // Тихая SSE-перезагрузка обязана запрашивать ТОТ ЖЕ набор, что и обычная
    // загрузка, — иначе фоновое событие по tbl_songs подменяло cond-выборку
    // обычной сеткой публикаций.
    publishFilterCond: null,
  },
  getters: {
    getPublishDigest(state) {
      // console.log('publishDigest: ', state.publishDigest);
      return state.publishDigest
    },
    getPublishDigestIsLoading(state) {
      return state.publishDigestIsLoading
    },
    // BR-412: активный cond-вид (null — обычная сетка публикаций).
    getPublishFilterCond(state) {
      return state.publishFilterCond
    },
  },
  mutations: {
    updatePublishDigest(state, result) {
      state.publishDigest = result
    },
    setPublishDigestIsLoading(state, isLoading) {
      state.publishDigestIsLoading = isLoading
    },
    // BR-412: активный cond-вид хранится вместе с дайджестом.
    setPublishFilterCond(state, cond) {
      state.publishFilterCond = cond || null
    },
    updatePublishDigestByIds(state, songsAndIndexesForUpdate) {
      // console.log('songsAndIndexesForUpdate', songsAndIndexesForUpdate)

      for (let songsAndIndexesFromRest of songsAndIndexesForUpdate) {
        for (let i = 0; i < state.publishDigest.length; i++) {
          let publishRow = state.publishDigest[i]
          for (let j = 0; j < publishRow.csrCells.length; j++) {
            let csrCell = publishRow.csrCells[j]
            if (csrCell.songDTO && csrCell.songDTO.id === songsAndIndexesFromRest.song.id) {
              csrCell.songDTO = songsAndIndexesFromRest.song
              state.publishDigest.splice(i, 1, publishRow)
            }
          }
        }
      }
    },
    updatePublishDigestByUserEvent(state, userEventData) {
      let songId = userEventData.recordId
      for (let i = 0; i < state.publishDigest.length; i++) {
        let publishRow = state.publishDigest[i]
        for (let j = 0; j < publishRow.csrCells.length; j++) {
          let csrCell = publishRow.csrCells[j]
          if (csrCell.songDTO && csrCell.songDTO.id === songId) {
            csrCell.songDTO = userEventData.record
            state.publishDigest.splice(i, 1, publishRow)
          }
        }
      }
    },
    addPublishDigestByUserEvent(state, userEventData) {
      console.log('Событие добавления песни из публикаций: ', userEventData)
    },
    deletePublishDigestByUserEvent(state, userEventData) {
      let songId = userEventData.recordId
      for (let i = 0; i < state.publishDigest.length; i++) {
        let publishRow = state.publishDigest[i]
        for (let j = 0; j < publishRow.csrCells.length; j++) {
          let csrCell = publishRow.csrCells[j]
          if (csrCell.songDTO && csrCell.songDTO.id === songId) {
            csrCell.songDTO = null
            state.publishDigest.splice(i, 1, publishRow)
          }
        }
      }
    },
  },
  actions: {
    // Позиционно-значимое изменение (сменился слот дата/время или песня вошла/вышла из сетки) ->
    // тихая перезагрузка всего дайджеста (сервер сам разложит по правильным ячейкам). Иначе -
    // дешёвое обновление ячейки на месте, чтобы не устраивать reload на каждый апдейт статуса/цвета.
    updatePublishDigestByUserEvent(ctx, userEventData) {
      const rec = userEventData.record
      if (rec) {
        const old = findCellDTO(ctx.state.publishDigest, userEventData.recordId)
        const positionChanged = old
          ? old.date !== rec.date || old.time !== rec.time
          : isDateInLoadedRange(
              rec.date,
              ctx.getters.getPublishFilterDateFrom,
              ctx.getters.getPublishFilterDateTo,
            )
        if (positionChanged) {
          ctx.dispatch('schedulePublishReload')
          return
        }
      }
      ctx.commit('updatePublishDigestByUserEvent', userEventData)
    },
    // Новая песня (RECORD_ADD tbl_songs) - если её дата уже попадает в загруженный диапазон,
    // она должна появиться в сетке -> тихая перезагрузка.
    addPublishDigestByUserEvent(ctx, userEventData) {
      const rec = userEventData.record
      if (
        rec &&
        isDateInLoadedRange(
          rec.date,
          ctx.getters.getPublishFilterDateFrom,
          ctx.getters.getPublishFilterDateTo,
        )
      ) {
        ctx.dispatch('schedulePublishReload')
      }
    },
    // Удаление песни, присутствовавшей в сетке, - зануляем ячейку сразу для мгновенного отклика
    // и заодно планируем тихий reload, чтобы досчитать финальную раскладку.
    deletePublishDigestByUserEvent(ctx, userEventData) {
      const wasInDigest = !!findCellDTO(ctx.state.publishDigest, userEventData.recordId)
      ctx.commit('deletePublishDigestByUserEvent', userEventData)
      if (wasInDigest) {
        ctx.dispatch('schedulePublishReload')
      }
    },
    updatePublishDigestByIds(ctx, payload) {
      ctx.commit('updatePublishDigestByIds', payload.songsAndIndexesForUpdate)
    },
    async getPublicationsDateFrom(ctx, param) {
      let request = { method: 'POST', url: '/api/publications/date', params: param }
      return await promisedXMLHttpRequest(request)
    },
    loadPublishDigest(ctx, params) {
      // BR-412: активный cond-вид запоминается вместе с дайджестом — тихая
      // перезагрузка должна запрашивать тот же набор.
      ctx.commit('setPublishFilterCond', (params && params.filterCond) || null)
      ctx.commit('setPublishDigestIsLoading', true)
      fetchPublishDigest(params)
        .then((digest) => {
          ctx.commit('updatePublishDigest', digest)
          ctx.commit('setPublishDigestIsLoading', false)
        })
        .catch((error) => {
          console.log(error)
          ctx.commit('setPublishDigestIsLoading', false)
        })
    },
    // Debounce: коалесцирует всплески (массовая смена дат/статусов) в один запрос дайджеста.
    schedulePublishReload(ctx) {
      clearTimeout(publishReloadTimer)
      publishReloadTimer = setTimeout(() => {
        ctx.dispatch('reloadPublishDigestSilently')
      }, 300)
    },
    // Тот же запрос, что loadPublishDigest, но без setPublishDigestIsLoading - без мигания
    // спиннера песня просто "сама" встаёт на нужное место.
    //
    // BR-412: запрос повторяет условия ТЕКУЩЕГО вида — в том числе cond-вид
    // ('unpublish'/'undone'/'skiped'). Раньше тихий reload запрашивал только диапазон
    // дат, и фоновое событие по tbl_songs подменяло cond-выборку обычной сеткой
    // публикаций: админ видел не те строки, на которые опирался.
    reloadPublishDigestSilently(ctx) {
      const filterDateFrom = ctx.getters.getPublishFilterDateFrom
      const filterDateTo = ctx.getters.getPublishFilterDateTo
      const filterCond = ctx.getters.getPublishFilterCond
      if (!filterDateFrom || !filterDateTo) return
      const params = { filterDateFrom, filterDateTo }
      if (filterCond) params.filterCond = filterCond
      fetchPublishDigest(params)
        .then((digest) => {
          ctx.commit('updatePublishDigest', digest)
        })
        .catch((error) => {
          console.log(error)
        })
    },
  },
}
