import { promisedXMLHttpRequest } from '../../lib/utils.js'
import { createRequestGeneration } from '../../lib/target-request-guard.js'
import { loadListWith } from '../../lib/list-loader.js'

// BR-433 (аудит #336, пакет PKG-S3-32, OpenProject #404): поколение запросов дайджеста
// заданий. Ответ применяет только владелец последнего запроса — при смене фильтра
// («Статус»/«Исполнитель»/«БД») побеждал последний ПРИШЕДШИЙ ответ, а не последний
// запрошенный: таблица показывала строки прежнего фильтра, а тулбар — новый.
const assignmentsDigestGeneration = createRequestGeneration()

// Задания онлайн-редактора караоке-разметки (tbl_song_assignments + tbl_song_assignment_drafts).
// target ('local'|'remote') — в запросах просмотра (digest/byId), в assign (где создать — обычно
// сервер, если весь цикл назначение→работа→апрув идёт на PROD) и в approve (читает черновик оттуда,
// где реально идёт работа, но Song пишет всегда в LOCAL — см. SongEditorController). reject/delete
// ТОЖЕ обязаны получать target — это запись статуса, а не просмотр: если писать не в ту БД, где реально
// работает пользователь (karaoke-web читает только свою единственную БД), правка останется невидимой
// и пользователь не сможет продолжить редактирование после отказа.
/**
 * Компонент «Store».
 *
 * @see AGENTS.md
 */
export default {
  state: {
    assignmentsDigest: [],
    assignmentsIsLoading: false,
    // BR-433: текст ошибки загрузки дайджеста заданий. Раньше отказ ловился только
    // `console.log`: список оставался прежним (или пустым) и выглядел как актуальный —
    // а по нему принимаются решения approve/reject/delete.
    assignmentsDigestError: '',
    assignmentsTarget: 'local',
    // Пользователи сайта для выпадающего списка при назначении (только редакторы — filterIsEditor).
    editorSiteUsers: [],
    // Текущее задание (с черновиком) для модалки ревью.
    assignmentCurrent: undefined,
    // БД по умолчанию для заданий редактора (KaraokeProperty editorAssignmentDefaultTarget,
    // 'local'|'remote') — сеет и assignmentsTarget («Задание редактора»), и target для кнопки
    // «Назначить» в таблице/карточке песни.
    defaultTarget: 'remote',
    // Статус назначения по songId (батч, для кнопки «Назначить»/«Назначено» в таблице/карточке песни):
    // songId -> {assignmentId, status, assigneeName}.
    assignmentStatusBySongId: {},
    // Количество заданий "на проверке" — бейдж пункта меню «Задания редактора» в App.vue (по
    // образцу chatUnreadTotal).
    submittedAssignmentsCount: 0,
  },
  getters: {
    getAssignmentsDigest(state) {
      return state.assignmentsDigest
    },
    getAssignmentsIsLoading(state) {
      return state.assignmentsIsLoading
    },
    // BR-433: текст ошибки загрузки дайджеста заданий.
    getAssignmentsDigestError(state) {
      return state.assignmentsDigestError
    },
    getAssignmentsTarget(state) {
      return state.assignmentsTarget
    },
    getEditorSiteUsers(state) {
      return state.editorSiteUsers
    },
    getAssignmentCurrent(state) {
      return state.assignmentCurrent
    },
    getEditorDefaultTarget(state) {
      return state.defaultTarget
    },
    getAssignmentStatusBySongId(state) {
      return state.assignmentStatusBySongId
    },
    getSubmittedAssignmentsCount(state) {
      return state.submittedAssignmentsCount
    },
  },
  mutations: {
    setAssignmentsDigest(state, result) {
      state.assignmentsDigest = result
    },
    // BR-433: применение ответа дайджеста (`{songAssignmentsDigest: [...]}`) — общий
    // помощник `loadListWith` коммитит РАЗБОРЕННЫЙ ответ, а не поле.
    setAssignmentsDigestFromResponse(state, result) {
      state.assignmentsDigest = (result && result.songAssignmentsDigest) || []
    },
    setAssignmentsIsLoading(state, v) {
      state.assignmentsIsLoading = v
    },
    // BR-433: текст ошибки загрузки дайджеста заданий.
    setAssignmentsDigestError(state, message) {
      state.assignmentsDigestError = message || ''
    },
    setAssignmentsTarget(state, t) {
      state.assignmentsTarget = t
    },
    setEditorSiteUsers(state, list) {
      state.editorSiteUsers = list
    },
    setAssignmentCurrent(state, a) {
      state.assignmentCurrent = a
    },
    setEditorDefaultTarget(state, t) {
      state.defaultTarget = t
    },
    setAssignmentStatusBySongId(state, map) {
      state.assignmentStatusBySongId = map
    },
    setSubmittedAssignmentsCount(state, v) {
      state.submittedAssignmentsCount = v
    },
  },
  actions: {
    /**
     * BR-433: загрузка дайджеста заданий.
     *
     * ДЕФЕКТ. У загрузки не было ни поколения запроса, ни отмены: два быстрых `reload()`
     * (смена «Статус» → «Исполнитель» или «БД») давали два POST, и ответ, пришедший
     * последним, перезаписывал список независимо от того, какой фильтр актуален.
     * Ошибка загрузки проглатывалась `console.log`, флаг загрузки снимался, и таблица
     * молча оставалась со старыми строками — неудачное обновление было неотличимо от
     * успешного.
     *
     * РЕШЕНИЕ. Общий помощник `loadListWith` (аудит #336, пакет PKG-S3-31, OpenProject
     * #403): поколение запроса, `finally` снимает флаг при любом исходе, текст ошибки
     * кладётся в state и показывается в таблице.
     */
    loadAssignmentsDigest(ctx, params) {
      const fullParams = Object.assign({}, params, { target: ctx.state.assignmentsTarget })
      return loadListWith({
        request: promisedXMLHttpRequest,
        commit: (type, payload) => ctx.commit(type, payload),
        generation: assignmentsDigestGeneration,
        requestOptions: { method: 'POST', url: '/api/songeditor/digest', params: fullParams },
        loadingCommit: 'setAssignmentsIsLoading',
        errorCommit: 'setAssignmentsDigestError',
        applyCommit: 'setAssignmentsDigestFromResponse',
        errorMessage: 'Не удалось загрузить список заданий редактора',
      })
    },
    // Принимает как голый id (существующий вызов из SongEditorTable), так и {id, target}
    // (кнопка «Назначить» в таблице/карточке песни — там задание может лежать в defaultTarget,
    // отличном от assignmentsTarget «Задания редактора»).
    //
    // BR-419: результат различает «найдено» и «не найдено»: для задания, отсутствующего в
    // выбранной БД, бэкенд отдаёт JSON `null`. Раньше вызывающий открывал модалку ревью
    // в любом случае, а `assignmentCurrent = null` рендерил только «Загрузка…» — модалка
    // становилась ловушкой (кнопки, включая «Закрыть», были внутри `v-if="a"`).
    loadAssignmentById(ctx, payload) {
      const isObj = payload !== null && typeof payload === 'object'
      const id = isObj ? payload.id : payload
      const target = (isObj && payload.target) || ctx.state.assignmentsTarget
      return promisedXMLHttpRequest({
        method: 'POST',
        url: '/api/songeditor/byId',
        params: { id, target },
      }).then((data) => {
        const result = data ? JSON.parse(data) : null
        ctx.commit('setAssignmentCurrent', result)
        return { found: !!result, assignment: result }
      })
    },
    // target — необязательное переопределение (используется кнопкой «Назначить» в таблице/карточке
    // песни, где действует defaultTarget, а не assignmentsTarget «Задания редактора»).
    loadEditorSiteUsers(ctx, target) {
      return promisedXMLHttpRequest({
        method: 'POST',
        url: '/api/siteusers/digest',
        params: { target: target || ctx.state.assignmentsTarget, filterIsEditor: 'true' },
      })
        .then((data) => {
          const result = JSON.parse(data)
          ctx.commit('setEditorSiteUsers', result.siteUsersDigest || [])
        })
        .catch((error) => console.log(error))
    },
    setAssignmentsTarget(ctx, target) {
      ctx.commit('setAssignmentsTarget', target)
    },
    /**
     * BR-427: чтение KaraokeProperty editorAssignmentDefaultTarget.
     *
     * ДЕФЕКТ. `getPropertyValuePromise` возвращает DTO (`JSON.parse(data).property`),
     * а сравнение шло с САМИМ объектом: `prop === 'local'` всегда было ложно. Поэтому
     * `defaultTarget` и `assignmentsTarget` всегда становились 'remote', независимо от
     * настройки `editorAssignmentDefaultTarget` — задания открывались не в той БД.
     *
     * РЕШЕНИЕ. Читается `prop.value` (строка) — как это делают другие потребители
     * (`ResourceLimitToggle.vue`, `SubsEdit.vue`). Пустое/отсутствующее значение → 'remote'.
     */
    async loadEditorDefaultTarget(ctx) {
      const prop = await ctx.dispatch('getPropertyValuePromise', 'editorAssignmentDefaultTarget')
      const raw = prop && typeof prop === 'object' ? prop.value : prop
      const text = String(raw === undefined || raw === null ? '' : raw).trim()
      const v = text === 'local' ? 'local' : 'remote'
      ctx.commit('setEditorDefaultTarget', v)
      ctx.commit('setAssignmentsTarget', v)
    },
    // Батч-статус назначений для видимых songId (таблица/карточка песни) — без N+1.
    //
    // BR-437 (аудит #336, пакет PKG-S3-32, OpenProject #404): ответ ЗАМЕНЯЛ всю карту
    // статусов без проверки актуальности. При быстром переключении страниц ответ
    // предыдущей страницы, пришедший позже, затирал статусы текущей — бейджи
    // «Назначено/В работе» пропадали, и админ мог назначить второго редактора.
    // `isCurrent` — проверка вызывающего: ответ применяется только если он ещё актуален.
    loadAssignmentStatusBySongIds(ctx, { songIds, target, isCurrent } = {}) {
      if (!songIds || !songIds.length) {
        ctx.commit('setAssignmentStatusBySongId', {})
        return Promise.resolve({})
      }
      const params = { songIds: songIds.join(','), target: target || ctx.state.defaultTarget }
      return promisedXMLHttpRequest({
        method: 'POST',
        url: '/api/songeditor/statusbysongids',
        params,
      })
        .then((data) => {
          const result = JSON.parse(data)
          const statuses = result.statuses || {}
          // BR-437: устаревший ответ не применяется.
          if (typeof isCurrent === 'function' && !isCurrent()) return statuses
          ctx.commit('setAssignmentStatusBySongId', statuses)
          return statuses
        })
        .catch((error) => {
          console.log(error)
          return {}
        })
    },
    /**
     * Поиск песен-кандидатов для назначения на разметку (AssignModal).
     *
     * @param {object} ctx - Vuex context
     * @param {object} payload
     * @param {string} [payload.query] - фильтр по song_name (LIKE)
     * @param {string} [payload.author] - фильтр по song_author (LIKE)
     * @param {string} [payload.album] - фильтр по song_album (LIKE)
     * @param {boolean} [payload.onlyStatus1=true] - только кандидаты на разметку (id_status=1)
     * @param {string} [payload.rootId] - точное совпадение по song.root_id
     * @param {string} [payload.audioRootId] - точное совпадение по song.audio_parent_id
     * @returns {Promise<Array<SongDTOdigest>>} массив песен-кандидатов
     *
     * @see specs/280-assign-modal-root-audio-id/spec.md FR-005, FR-007 — проброс filterRootId/filterAudioParentId
     * @see specs/280-assign-modal-root-audio-id/research.md D-1 — audioRootId в payload маппится в filterAudioParentId
     */
    searchCandidateSongs(ctx, { query, author, album, onlyStatus1, rootId, audioRootId }) {
      const params = {}
      if (query) params.filterSongName = query
      if (author) params.filterAuthor = author
      if (album) params.filterAlbum = album
      if (onlyStatus1) params.filterStatus = '1'
      if (rootId) params.filterRootId = rootId
      if (audioRootId) params.filterAudioParentId = audioRootId
      return promisedXMLHttpRequest({ method: 'POST', url: '/api/songsdigests', params }).then(
        (data) => {
          const result = JSON.parse(data)
          return result.songsDigests || []
        },
      )
    },
    // Задание покрывает всю песню (все голоса) — voice больше не передаётся. target — где создать
    // (по умолчанию assignmentsTarget, переопределяется явным параметром — см. кнопку «Назначить» в
    // таблице/карточке песни, использующую defaultTarget). clearMarkers — см. комментарий бэкенда
    // (SongEditorController.assign): null → возможен ответ error:"markers_exist", вызывающий код
    // обязан переспросить пользователя и повторить запрос с explicit true/false.
    assignSong(ctx, { songId, assigneeId, clearMarkers, target }) {
      const params = { songId, assigneeId, target: target || ctx.state.assignmentsTarget }
      if (clearMarkers !== undefined && clearMarkers !== null) params.clearMarkers = clearMarkers
      return promisedXMLHttpRequest({ method: 'POST', url: '/api/songeditor/assign', params }).then(
        (data) => JSON.parse(data),
      )
    },
    // target — откуда читать задание/черновик (по умолчанию local); Song и статус задания
    // бэкенд в любом случае пишет только в LOCAL (см. SongEditorController.approve).
    //
    // Feature 184: опциональный idStatus (5 или 6) — финальный статус песни при апруве.
    // 5 — «Маркеры проверены» (без рендера DEMO и sync related-таблиц), 6 — «Готова» (дефолт).
    // Имена — канон из specs/022-song-status-lifecycle и SongEdit.vue (title кнопки).
    // Параметр пробрасывается в POST /api/songeditor/approve ТОЛЬКО если явно передан —
    // старый клиент без idStatus сохраняет полную backward-compat (бэкенд применяет 6).
    //
    // Принимает ОБА формата для совместимости с любыми существующими вызовами:
    //   - число: approveAssignment(ctx, 42)         → { id: 42 } (без idStatus — дефолт 6)
    //   - объект: approveAssignment(ctx, {id: 42, idStatus: 5}) → { id: 42, idStatus: 5 }
    // Паттерн `isObj` — тот же, что в loadAssignmentById (см. выше).
    approveAssignment(ctx, payload) {
      const isObj = payload !== null && typeof payload === 'object'
      const id = isObj ? payload.id : payload
      const idStatus = isObj ? payload.idStatus : undefined
      const params = { id, target: ctx.state.assignmentsTarget }
      if (idStatus !== undefined) params.idStatus = idStatus
      return promisedXMLHttpRequest({
        method: 'POST',
        url: '/api/songeditor/approve',
        params,
      }).then((data) => JSON.parse(data))
    },
    rejectAssignment(ctx, { id, comment }) {
      return promisedXMLHttpRequest({
        method: 'POST',
        url: '/api/songeditor/reject',
        params: { id, comment: comment || '', target: ctx.state.assignmentsTarget },
      }).then((data) => JSON.parse(data))
    },
    deleteAssignment(ctx, id) {
      return promisedXMLHttpRequest({
        method: 'POST',
        url: '/api/songeditor/delete',
        params: { id, target: ctx.state.assignmentsTarget },
      }).then((data) => JSON.parse(data))
    },
    // Отозвать назначение у редактора — забрать задание и сразу дать возможность назначить его
    // другому (или тому же) редактору через «Назначить…» в таблице песен. Семантически тот же
    // эффект, что у delete(), но бэкенд дополнительно чистит черновик (tbl_song_assignment_drafts),
    // чтобы не оставлять orphan-запись на несуществующее назначение.
    revokeAssignment(ctx, id) {
      return promisedXMLHttpRequest({
        method: 'POST',
        url: '/api/songeditor/revoke',
        params: { id, target: ctx.state.assignmentsTarget },
      }).then((data) => JSON.parse(data))
    },
    /**
     * Массовое удаление всех одобренных заданий с учётом активных фильтров (`filterStatus` /
     * `filterAssigneeId` / `filterAuthor`) и текущего `assignmentsTarget` (local/remote).
     * Пустые фильтры → `undefined` (не отправляем пустые строки — иначе Spring свяжет как `""`).
     *
     * @see archive/docs/features/editor-tasks.md
     */
    deleteApprovedAssignments(ctx, params) {
      return promisedXMLHttpRequest({
        method: 'POST',
        url: '/api/songeditor/delete-approved',
        params: {
          target: ctx.state.assignmentsTarget,
          filterAssigneeId: params.filterAssigneeId || undefined,
          filterStatus: params.filterStatus || undefined,
          filterAuthor: params.filterAuthor || undefined,
        },
      }).then((data) => JSON.parse(data))
    },
    // Бейдж пункта меню «Задания редактора» (App.vue, по образцу loadChatUnreadCount) — target
    // берётся из defaultTarget ('remote' по умолчанию), т.к. реальный рабочий цикл чаще всего
    // идёт целиком на PROD.
    loadSubmittedAssignmentsCount(ctx) {
      const params = { target: ctx.state.defaultTarget }
      return promisedXMLHttpRequest({
        method: 'POST',
        url: '/api/songeditor/submittedcount',
        params,
      })
        .then((data) => {
          ctx.commit('setSubmittedAssignmentsCount', parseInt(data, 10) || 0)
        })
        .catch((error) => console.log(error))
    },
  },
}
