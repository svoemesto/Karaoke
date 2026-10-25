import { promisedXMLHttpRequest } from '../../lib/utils'
import { createRequestGeneration } from '../../lib/target-request-guard.js'
import { loadListWith } from '../../lib/list-loader.js'

// BR-391 (аудит #336, пакет PKG-S3-31, OpenProject #403): загрузка дайджеста картинок —
// с поколением запроса, `finally` и текстом ошибки в state.
const picturesDigestGeneration = createRequestGeneration()

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
    savePicture(state) {
      state.pictureSnapshot = !state.pictureCurrent
        ? undefined
        : Object.assign({}, state.pictureCurrent)
    },
    setPicturesTableCurrentPage(state, page) {
      state.picturesTableCurrentPage = page
    },
    async deletePictureCurrent(state) {
      let request = {
        method: 'POST',
        url: '/api/picture/delete',
        params: { id: state.pictureCurrentId },
      }
      await promisedXMLHttpRequest(request)
    },
    async savePictureCurrentToDisk(state) {
      let request = {
        method: 'POST',
        url: '/api/picture/savetodisk',
        params: { id: state.pictureCurrentId },
      }
      await promisedXMLHttpRequest(request)
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
    savePicture(ctx, diffs) {
      let params = { id: ctx.state.pictureCurrentId }
      if (diffs.name) params.name = diffs.name
      if (diffs.full) params.full = diffs.full
      let request = { method: 'POST', url: '/api/pictures/updatepicture', params: params }
      promisedXMLHttpRequest(request)
        .then(() => {
          ctx.commit('savePicture')
        })
        .catch((error) => {
          console.log(error)
        })
    },
  },
}
