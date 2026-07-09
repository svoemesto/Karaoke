import {promisedXMLHttpRequest} from "../../lib/utils";

// Таймер debounce для тихой перезагрузки дайджеста публикаций (см. schedulePublishReload ниже).
let publishReloadTimer = null;

// 'dd.mm.yy' -> 'yymmdd' - в таком виде даты сравнимы лексикографически (тот же приём,
// что и на бэкенде для сортировки строк сетки по SettingField.DATE, см. CrossSettings.kt).
function dateKey(str) {
    return str ? str.split('.').reverse().join('') : '';
}

// Попадает ли dateStr в загруженный диапазон [from, to]. Пустые from/to считаем "диапазон неизвестен" -
// в этом случае решаем не рисковать и не считаем дату входящей (лишний silent-reload безопаснее пропуска).
function isDateInLoadedRange(dateStr, from, to) {
    if (!dateStr || !from || !to) return false;
    const key = dateKey(dateStr);
    return dateKey(from) <= key && key <= dateKey(to);
}

// Ищет DTO песни в уже загруженной сетке публикаций по id, не мутируя state.
function findCellDTO(publishDigest, songId) {
    for (const publishRow of publishDigest) {
        for (const csrCell of publishRow.csrCells) {
            if (csrCell.settingsDTO && csrCell.settingsDTO.id === songId) {
                return csrCell.settingsDTO;
            }
        }
    }
    return null;
}

// Общий fetch дайджеста публикаций - используется и loadPublishDigest (со спиннером),
// и reloadPublishDigestSilently (тихий фоновый reload по SSE).
async function fetchPublishDigest(params) {
    let request = { method: 'POST', url: "/api/publicationsdigest", params: params };
    let data = await promisedXMLHttpRequest(request);
    let result = JSON.parse(data);
    return result.publicationsDigest;
}

export default {
    state: {
        publishDigest: [],
        publishDigestIsLoading: false
    },
    getters: {
        getPublishDigest(state) {
            // console.log('publishDigest: ', state.publishDigest);
            return state.publishDigest
        },
        getPublishDigestIsLoading(state) { return state.publishDigestIsLoading }
    },
    mutations: {
        updatePublishDigest(state, result) {
            state.publishDigest = result;
        },
        setPublishDigestIsLoading(state, isLoading) { state.publishDigestIsLoading = isLoading },
        updatePublishDigestByIds(state, songsAndIndexesForUpdate) {
            // console.log('songsAndIndexesForUpdate', songsAndIndexesForUpdate)

            for (let songsAndIndexesFromRest of songsAndIndexesForUpdate) {
                for (let i = 0; i < state.publishDigest.length; i++) {
                    let publishRow = state.publishDigest[i];
                    for (let j = 0; j < publishRow.csrCells.length; j++) {
                        let csrCell = publishRow.csrCells[j];
                        if (csrCell.settingsDTO && csrCell.settingsDTO.id === songsAndIndexesFromRest.song.id) {
                            csrCell.settingsDTO = songsAndIndexesFromRest.song;
                            state.publishDigest.splice(i,1, publishRow);
                        }
                    }
                }
            }
        },
        updatePublishDigestByUserEvent(state, userEventData) {
            let songId = userEventData.recordId;
            for (let i = 0; i < state.publishDigest.length; i++) {
                let publishRow = state.publishDigest[i];
                for (let j = 0; j < publishRow.csrCells.length; j++) {
                    let csrCell = publishRow.csrCells[j];
                    if (csrCell.settingsDTO && csrCell.settingsDTO.id === songId) {
                        csrCell.settingsDTO = userEventData.record;
                        state.publishDigest.splice(i,1, publishRow);
                    }
                }
            }
        },
        addPublishDigestByUserEvent(state, userEventData) {
            console.log('Событие добавления песни из публикаций: ', userEventData)
        },
        deletePublishDigestByUserEvent(state, userEventData) {
            let songId = userEventData.recordId;
            for (let i = 0; i < state.publishDigest.length; i++) {
                let publishRow = state.publishDigest[i];
                for (let j = 0; j < publishRow.csrCells.length; j++) {
                    let csrCell = publishRow.csrCells[j];
                    if (csrCell.settingsDTO && csrCell.settingsDTO.id === songId) {
                        csrCell.settingsDTO = null;
                        state.publishDigest.splice(i,1, publishRow);
                    }
                }
            }
        }
    },
    actions: {
        // Позиционно-значимое изменение (сменился слот дата/время или песня вошла/вышла из сетки) ->
        // тихая перезагрузка всего дайджеста (сервер сам разложит по правильным ячейкам). Иначе -
        // дешёвое обновление ячейки на месте, чтобы не устраивать reload на каждый апдейт статуса/цвета.
        updatePublishDigestByUserEvent(ctx, userEventData) {
            const rec = userEventData.record;
            if (rec) {
                const old = findCellDTO(ctx.state.publishDigest, userEventData.recordId);
                const positionChanged = old
                    ? (old.date !== rec.date || old.time !== rec.time)
                    : isDateInLoadedRange(rec.date, ctx.getters.getPublishFilterDateFrom, ctx.getters.getPublishFilterDateTo);
                if (positionChanged) {
                    ctx.dispatch('schedulePublishReload');
                    return;
                }
            }
            ctx.commit('updatePublishDigestByUserEvent', userEventData);
        },
        // Новая песня (RECORD_ADD tbl_settings) - если её дата уже попадает в загруженный диапазон,
        // она должна появиться в сетке -> тихая перезагрузка.
        addPublishDigestByUserEvent(ctx, userEventData) {
            const rec = userEventData.record;
            if (rec && isDateInLoadedRange(rec.date, ctx.getters.getPublishFilterDateFrom, ctx.getters.getPublishFilterDateTo)) {
                ctx.dispatch('schedulePublishReload');
            }
        },
        // Удаление песни, присутствовавшей в сетке, - зануляем ячейку сразу для мгновенного отклика
        // и заодно планируем тихий reload, чтобы досчитать финальную раскладку.
        deletePublishDigestByUserEvent(ctx, userEventData) {
            const wasInDigest = !!findCellDTO(ctx.state.publishDigest, userEventData.recordId);
            ctx.commit('deletePublishDigestByUserEvent', userEventData);
            if (wasInDigest) {
                ctx.dispatch('schedulePublishReload');
            }
        },
        updatePublishDigestByIds(ctx, payload) {
            ctx.commit('updatePublishDigestByIds', payload.songsAndIndexesForUpdate);
        },
        async getPublicationsDateFrom(ctx, param) {
            let request = { method: 'POST', url: "/api/publications/date", params: param };
            return await promisedXMLHttpRequest(request);
        },
        loadPublishDigest(ctx, params) {
            ctx.commit('setPublishDigestIsLoading', true);
            fetchPublishDigest(params).then(digest => {
                ctx.commit('updatePublishDigest', digest);
                ctx.commit('setPublishDigestIsLoading', false);
            }).catch(error => {
                console.log(error);
                ctx.commit('setPublishDigestIsLoading', false);
            });
        },
        // Debounce: коалесцирует всплески (массовая смена дат/статусов) в один запрос дайджеста.
        schedulePublishReload(ctx) {
            clearTimeout(publishReloadTimer);
            publishReloadTimer = setTimeout(() => {
                ctx.dispatch('reloadPublishDigestSilently');
            }, 300);
        },
        // Тот же запрос, что loadPublishDigest, но без setPublishDigestIsLoading - без мигания
        // спиннера песня просто "сама" встаёт на нужное место.
        reloadPublishDigestSilently(ctx) {
            const filterDateFrom = ctx.getters.getPublishFilterDateFrom;
            const filterDateTo = ctx.getters.getPublishFilterDateTo;
            if (!filterDateFrom || !filterDateTo) return;
            fetchPublishDigest({ filterDateFrom, filterDateTo }).then(digest => {
                ctx.commit('updatePublishDigest', digest);
            }).catch(error => {
                console.log(error);
            });
        }
    }
}