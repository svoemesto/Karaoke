import { promisedXMLHttpRequest } from '../../lib/utils.js'
import { createRequestGeneration } from '../../lib/target-request-guard.js'
import { loadListWith } from '../../lib/list-loader.js'

// BR-391 (аудит #336, пакет PKG-S3-31, OpenProject #403): загрузка дайджеста картинок —
// с поколением запроса, `finally` и текстом ошибки в state.
const picturesDigestGeneration = createRequestGeneration()

// BR-395 (аудит #336, пакет PKG-S3-32, OpenProject #404): поколение автосейва картинки.
//
// ДЕФЕКТ. Снимок (`pictureSnapshot`) фиксировался ПОСЛЕ ответа сервера, но брался из
// ЖИВОГО `pictureCurrent`, а не из отправленного набора. Правки, сделанные пока запрос
// в полёте (при загрузке base64 `full` запрос идёт секунды), попадали в снимок как
// «сохранённые»: diff обнулялся, повторный автосейв не срабатывал — изменения молча
// терялись при закрытии модалки.
//
// РЕШЕНИЕ. Снимок собирается из ОТПРАВЛЕННОГО payload (`Object.assign({}, snapshot, sent)`),
// а поколение гарантирует, что пришедший позже ответ уже не «съест» снимок.
const pictureSaveGeneration = createRequestGeneration()

/**
 * Компонент «Store».
 *
 * @see AGENTS.md
 */

export default {
  state: {
    picturesDigest: [],
    picturesDigestIsLoading: false,
    // BR-391: текст ошибки загрузки дайджеста картинок ('' — ошибки нет).
    picturesDigestsError: '',
    pictureCurrent: undefined,
    pictureSnapshot: undefined,
    pictureCurrentId: 0,
    // Текущая страница пагинации в PicturesTable. Сохраняем в сторе, чтобы при уходе с компонента
    // и возврате — открывалась страница, на которой остановился пользователь.
    picturesTableCurrentPage: 1,
  },
  getters: {
    getPicturesDigest(state) {
      return state.picturesDigest
    },
    getPicturesDigestIsLoading(state) {
      return state.picturesDigestIsLoading
    },
    // BR-391: текст ошибки загрузки дайджеста картинок.
    getPicturesDigestsError(state) {
      return state.picturesDigestsError
    },
    getPictureCurrent(state) {
      return state.pictureCurrent
    },
    getPictureSnapshot(state) {
      return state.pictureSnapshot
    },
    getPicturesTableCurrentPage(state) {
      return state.picturesTableCurrentPage
    },
    getPictureDiff(state) {
      let result = []
      if (state.pictureCurrent && state.pictureSnapshot) {
        for (let key of Object.keys(state.pictureCurrent)) {
          let oldValue = state.pictureSnapshot[key]
          let newValue = state.pictureCurrent[key]
          if (oldValue !== newValue) {
            result.push({ name: key, new: newValue, old: oldValue })
          }
        }
      }
      return result
    },
    // getPropValue: () => async (key) => {
    //     let prop = JSON.parse(await promisedXMLHttpRequest({
    //         method: 'POST',
    //         url: "/api/properties/getproperty",
    //         params: { key: key }
    //     }));
    //     console.log(`key ${key} prop.property`, prop.property);
    //     return prop.property.value;
    // },
    loadPictureFromDiskBase64: () => async (pathToFile) => {
      let base64 = await promisedXMLHttpRequest({
        method: 'POST',
        url: '/api/picture/loadfromdisk',
        params: { pathToFile: pathToFile },
      })
      return base64
    },
    getPictureValuePromise(state) {
      let request = { method: 'POST', url: '/api/picture', params: { id: state.pictureCurrentId } }
      return promisedXMLHttpRequest(request)
    },
  },
  mutations: {
    updatePicturesDigests(state, result) {
      const picturesToUpdate = Array.isArray(result) ? result : [result]
      picturesToUpdate.forEach((updatedPicture) => {
        const index = state.picturesDigest.findIndex((picture) => picture.id === updatedPicture.id)
        if (index !== -1) {
          state.picturesDigest.splice(index, 1, updatedPicture)
        }
      })
    },
    setPicturesDigests(state, result) {
      state.picturesDigest = result.picturesDigests
    },
    setPicturesDigestIsLoading(state, isLoading) {
      state.picturesDigestIsLoading = isLoading
    },
    setPicturesDigestsError(state, message) {
      state.picturesDigestsError = message || ''
    },
    setPictureCurrentId(state, id) {
      state.pictureCurrentId = id
    },
    setPictureCurrent(state, picture) {
      state.pictureCurrent = Object.assign({}, picture)
    },
    setPictureSnapshot(state, picture) {
      state.pictureSnapshot = Object.assign({}, picture)
    },
    setPictureCurrentField(state, payload) {
      state.pictureCurrent[payload.name] = payload.value
    },
    // BR-395: снимок собирается из ОТПРАВЛЕННЫХ полей, а не из живого `pictureCurrent`.
    // Иначе правки, сделанные во время запроса, помечались бы как сохранённые.
    savePicture(state, sentDiffs) {
      state.pictureSnapshot = Object.assign({}, state.pictureSnapshot, sentDiffs || {})
    },
    // BR-396: после удаления картинки её строка убирается из дайджеста сразу —
    // иначе таблица показывала удалённую строку до применения фильтра/F5.
    removePictureFromDigest(state, id) {
      state.picturesDigest = state.picturesDigest.filter((picture) => picture.id !== id)
    },
    // BR-396: очистка текущей картинки — чтобы повторное открытие не показывало
    // уже удалённую запись.
    clearPictureCurrent(state) {
      state.pictureCurrent = undefined
      state.pictureSnapshot = undefined
    },
    setPicturesTableCurrentPage(state, page) {
      state.picturesTableCurrentPage = page
    },
  },
  actions: {
    loadOneRecord(ctx, id) {
      // Имя параметра — `filterId` (контракт бэкенда `POST /api/pictures/picturesdigests`),
      // как в Albums/store.js. Прежнее `filter_id` Spring игнорировал.
      const params = { filterId: id }
      let request = { method: 'POST', url: '/api/pictures/picturesdigests', params: params }
      promisedXMLHttpRequest(request)
        .then((data) => {
          let result = JSON.parse(data)
          ctx.commit('updatePicturesDigests', result.picturesDigests)
        })
        .catch((error) => {
          console.log(error)
        })
    },
    // BR-391: промис возвращается, флаг снимается в `finally` (в том числе при
    // ошибке — раньше он залипал навсегда, и таблица показывала «Loading...»),
    // ошибка видна в UI, а ответ применяется только владельцем текущего поколения.
    loadPicturesDigests(ctx, params) {
      return loadListWith({
        request: promisedXMLHttpRequest,
        commit: (type, payload) => ctx.commit(type, payload),
        generation: picturesDigestGeneration,
        requestOptions: { method: 'POST', url: '/api/pictures/picturesdigests', params },
        loadingCommit: 'setPicturesDigestIsLoading',
        errorCommit: 'setPicturesDigestsError',
        applyCommit: 'setPicturesDigests',
        errorMessage: 'Не удалось загрузить список картинок',
      })
    },
    setPictureValuePromise(ctx, payload) {
      let request = { method: 'POST', url: '/api/pictures/updatepicture', params: payload }
      return promisedXMLHttpRequest(request)
    },
    setPictureCurrent(ctx, picture) {
      ctx.commit('setPictureCurrent', picture)
    },
    setPictureSnapshot(ctx, picture) {
      ctx.commit('setPictureSnapshot', picture)
    },
    setPictureCurrentField(ctx, payload) {
      ctx.commit('setPictureCurrentField', payload)
    },
    // BR-395 / BR-396: payload — `{diffs, signal}` (сигнал отменяет запрос при уходе
    // со страницы). Для обратной совместимости принимается и «плоский» объект diff'ов.
    savePicture(ctx, payload) {
      const diffs = (payload && payload.diffs) || payload || {}
      const signal = payload ? payload.signal : undefined
      const ticket = pictureSaveGeneration.next()
      let params = { id: ctx.state.pictureCurrentId }
      // В снимок попадают только те поля, которые УШЛИ на сервер: иначе снимок
      // врал бы, что сохранено то, что не отправлялось.
      const sent = {}
      if (diffs.name) {
        params.name = diffs.name
        sent.name = diffs.name
      }
      if (diffs.full) {
        params.full = diffs.full
        sent.full = diffs.full
      }
      let request = { method: 'POST', url: '/api/pictures/updatepicture', params: params }
      if (signal) request.signal = signal
      return promisedXMLHttpRequest(request)
        .then(() => {
          // Ответ устаревшего запроса снимок не трогает.
          if (!pictureSaveGeneration.isCurrent(ticket)) return { applied: false, stale: true }
          ctx.commit('savePicture', sent)
          return { applied: true, stale: false }
        })
        .catch((error) => {
          // Ошибка видна вызывающему: он показывает её пользователю.
          console.log(error)
          throw error
        })
    },
    // BR-396: HTTP-запрос переехал из мутации в экшен (в мутации сетевых вызовов быть
    // не должно — см. knowledge/system/frontend/webvue3-audit-invariants.md, § 3).
    // Промис возвращается и отклоняется при ошибке: вызывающий ждёт результат и
    // закрывает модалку только после подтверждённого успеха.
    deletePictureCurrent(ctx) {
      const id = ctx.state.pictureCurrentId
      let request = {
        method: 'POST',
        url: '/api/picture/delete',
        params: { id: id },
      }
      return promisedXMLHttpRequest(request).then(() => {
        ctx.commit('removePictureFromDigest', id)
        ctx.commit('clearPictureCurrent')
        return true
      })
    },
    // BR-396: то же для сохранения картинки на диске — промис возвращается, отказ
    // доходит до вызывающего (иначе кнопка «молча» ничего не делала).
    savePictureCurrentToDisk(ctx) {
      let request = {
        method: 'POST',
        url: '/api/picture/savetodisk',
        params: { id: ctx.state.pictureCurrentId },
      }
      return promisedXMLHttpRequest(request).then(() => true)
    },
  },
}
