import {promisedXMLHttpRequest} from "@/lib/utils";

export default {
    state: {
        processPages: [[]],
        processStatuses: [],
        processTypes: [],
        currentProcessPageIndex: 0,
        currentProcessIndex: 0,
        currentProcessId: 0,
        processPageSize: 50,

        currentProcess: undefined,
        snapshotProcess: undefined,
        lastUpdateProcess: Date.now(),

        fieldProcessParams: [
            {
                name: 'id',
                params: {
                    width: '50',
                    label: 'ID',
                    textAlign: 'center',
                    fontSize: 'small',
                    labelFontSize: 'small',
                    labelPadding: '0 4px 0 4px',
                    filterWidth: '46',
                    filterFontSize: 'small'
                }
            },
            {
                name: 'name',
                params: {
                    width: '400',
                    label: 'Процесс',
                    textAlign: 'left',
                    fontSize: 'small',
                    labelFontSize: 'small',
                    labelPadding: '0 4px 0 4px',
                    filterWidth: '396',
                    filterFontSize: 'small'
                }
            },
            {
                name: 'status',
                params: {
                    width: '75',
                    label: 'Статус',
                    textAlign: 'center',
                    fontSize: 'small',
                    labelFontSize: 'small',
                    labelPadding: '0 4px 0 4px',
                    filterWidth: '71',
                    filterFontSize: 'small'
                }
            },
            {
                name: 'priority',
                params: {
                    width: '50',
                    label: 'Prior',
                    textAlign: 'center',
                    fontSize: 'small',
                    labelFontSize: 'small',
                    labelPadding: '0 4px 0 4px',
                    filterWidth: '46',
                    filterFontSize: 'small'
                }
            },
            {
                name: 'description',
                params: {
                    width: '175',
                    label: 'Описание',
                    textAlign: 'left',
                    fontSize: 'small',
                    labelFontSize: 'small',
                    labelPadding: '0 4px 0 4px',
                    filterWidth: '171',
                    filterFontSize: 'small'
                }
            },
            {
                name: 'type',
                params: {
                    width: '115',
                    label: 'Тип',
                    textAlign: 'left',
                    fontSize: 'small',
                    labelFontSize: 'small',
                    labelPadding: '0 4px 0 4px',
                    filterWidth: '111',
                    filterFontSize: 'small'
                }
            },
            {
                name: 'startStr',
                params: {
                    width: '120',
                    label: 'Начало',
                    textAlign: 'left',
                    fontSize: 'small',
                    labelFontSize: 'small',
                    labelPadding: '0 4px 0 4px',
                    filterWidth: '116',
                    filterFontSize: 'small'
                }
            },
            {
                name: 'endStr',
                params: {
                    width: '120',
                    label: 'Конец',
                    textAlign: 'left',
                    fontSize: 'small',
                    labelFontSize: 'small',
                    labelPadding: '0 4px 0 4px',
                    filterWidth: '116',
                    filterFontSize: 'small'
                }
            },
            {
                name: 'percentageStr',
                params: {
                    width: '70',
                    label: '%',
                    textAlign: 'center',
                    fontSize: 'small',
                    labelFontSize: 'small',
                    labelPadding: '0 4px 0 4px',
                    filterWidth: '66',
                    filterFontSize: 'small'
                }
            },
            {
                name: 'timePassedStr',
                params: {
                    width: '50',
                    label: 'Pass',
                    textAlign: 'center',
                    fontSize: 'small',
                    labelFontSize: 'small',
                    labelPadding: '0 4px 0 4px',
                    filterWidth: '46',
                    filterFontSize: 'small'
                }
            },
            {
                name: 'timeLeftStr',
                params: {
                    width: '50',
                    label: 'Left',
                    textAlign: 'center',
                    fontSize: 'small',
                    labelFontSize: 'small',
                    labelPadding: '0 4px 0 4px',
                    filterWidth: '46',
                    filterFontSize: 'small'
                }
            },
        ]
    },
    watch: {

    },
    getters: {


        getProcesses(state) {
            return state.processPages.length ? state.processPages[state.currentProcessPageIndex] : [];
        },
        getProcessStatuses(state) {
            return state.processStatuses.length ? state.processStatuses : [];
        },
        getProcessTypes(state) {
            return state.processTypes.length ? state.processTypes : [];
        },
        getCountProcesses(state) {
            return state.processPages.map(it => it.length).reduce((accumulator, currentValue) => {
                return accumulator + currentValue
            },0);
        },
        getCountProcessPages(state) {
            return state.processPages.length;
        },
        getCurrentProcessPageIndex(state) {
            return state.currentProcessPageIndex;
        },
        getCurrentProcessIndex(state) {
            return state.currentProcessIndex;
        },
        getCurrentProcess(state) {
            return state.currentProcess;
        },
        getCurrentProcessId(state) {
            return state.currentProcessId;
        },
        getProcessPageSize(state) {
            return state.processPageSize;
        },
        getProcessFieldParams: (state) => (name) => {
            return state.fieldProcessParams.filter(fieldParam => fieldParam.name === name)[0].params;
        },
        getProcessFieldsParamsWidth(state) {
            return state.fieldProcessParams
                .map(fp => fp.params)
                .map(p => p.width)
                .reduce((accumulator, currentValue) => {
                    return accumulator + +currentValue;
                },0);
        },
        getSnapshotProcess(state) {
            return state.snapshotProcess;
        },
        getLastUpdateProcess(state) {
            return state.lastUpdateProcess;
        },
        getProcessDiff(state) {
            let result = [];
            if (state.currentProcess && state.snapshotProcess) {
                for (let key of Object.keys(state.currentProcess)) {
                    let oldValue = state.snapshotProcess[key];
                    let newValue = state.currentProcess[key];
                    if (oldValue !== newValue) {
                        result.push({name: key, new: newValue, old: oldValue});
                    }
                }
            }
            return result;
        },
        isChangedProcess(state) {
            if (state.currentProcess && state.snapshotProcess) {
                for (let key of Object.keys(state.currentProcess)) {
                    let oldValue = state.snapshotProcess[key];
                    let newValue = state.currentProcess[key];
                    if (oldValue !== newValue) {
                        return true;
                    }
                }
            }
            return false
        },
        async getProcessesIdsForUpdate(state) {
            return await promisedXMLHttpRequest({
                method: 'POST',
                url: "/apis/processes/changed",
                params: { time: state.lastUpdateProcess }
            });
        },
        getProcessesIds(state) {
            return state.processPages.map(function (page, pageIndex) {
                return page.map(function (process, processIndex) {
                    return { processIndex: processIndex, processId: process.id, pageIndex: pageIndex }
                });
            }).flatMap(item => item);
        },
        getProcessesForUpdateByIds: () => async (ids) => {
            // console.log('getSongsForUpdateByIds ids: ', ids);
            let params = { ids: ids };
            let request = { method: 'POST', url: "/apis/processes/ids", params: params };
            return await promisedXMLHttpRequest(request);
        },
        async processesStatuses() {
            let request = { method: 'POST', url: "/apis/processes/statuses"};
            return JSON.parse(await promisedXMLHttpRequest(request)).dicts;
        },
        async processesTypes() {
            let request = { method: 'POST', url: "/apis/processes/types"};
            return JSON.parse(await promisedXMLHttpRequest(request)).dicts;
        }
    },
    mutations: {

        saveProcess(state) {
            state.snapshotProcess = !state.currentProcess ? undefined : Object.assign({}, state.currentProcess)
        },
        updateProcessesAndDictionaries(state, result) {
            state.currentProcessPageIndex = 0;
            state.processPages = result.pages;
            state.processStatuses = result.statuses;
            state.processTypes = result.types;
            state.currentProcess = state.processPages && state.processPages.length && state.processPages[0].length ? state.processPages[0][0] : undefined;
            state.currentProcessIndex = state.processPages && state.processPages.length && state.processPages[0].length ? 0 : undefined;
            state.currentProcessId = !state.currentProcess ? 0 : state.currentProcess.id;
            state.snapshotProcess = !state.currentProcess ? undefined : Object.assign({}, state.currentProcess)
        },
        updateProcess(state, processFromRest) {
            if (processFromRest) {
                const id = processFromRest.id;

                let processWithIndexesFiltered = state.processPages.map(function (page, pageIndex) {
                    return page.map(function (process, processIndex) {
                        return { process: process, processIndex: processIndex, processId: process.id, pageIndex: pageIndex }
                    });
                }).flatMap(item => item).filter(item => item.songId === id);
                let processWithIndexes = processWithIndexesFiltered.length ? processWithIndexesFiltered[0] : undefined;
                if (processWithIndexes) {
                    // console.log('Обновляем песню ID =', id);
                    if (id === state.currentProcessId) {
                        state.snapshotProcess = Object.assign({}, processFromRest)
                    } else {
                        state.processPages[processWithIndexes.pageIndex].splice(processWithIndexes.processIndex, 1, processFromRest);
                    }
                } else {
                    state.currentProcess = Object.assign({}, processFromRest);
                    state.currentProcessId = processFromRest.id;
                    state.snapshotProcess = Object.assign({}, state.currentProcess)
                }
            }
        },
        updateProcessesByIds(state, processesAndIndexesForUpdate) {
            // console.log('processesAndIndexesForUpdate', processesAndIndexesForUpdate)
            for (let processesAndIndexesFromRest of processesAndIndexesForUpdate) {
                if (processesAndIndexesFromRest.pageIndex !== undefined && processesAndIndexesFromRest.processIndex !== undefined ) {
                    state.processPages[processesAndIndexesFromRest.pageIndex].splice(processesAndIndexesFromRest.processIndex, 1, processesAndIndexesFromRest.process);
                    if (processesAndIndexesFromRest.processId === state.currentProcessId) {
                        state.currentProcess = Object.assign({}, processesAndIndexesFromRest.process)
                        state.snapshotProcess = Object.assign({}, processesAndIndexesFromRest.process)
                    }
                }
                if (processesAndIndexesFromRest.process.status === 'WORKING' ) {
                    state.workingProcess = Object.assign({}, processesAndIndexesFromRest.process)
                } else {
                    state.workingProcess = undefined
                }
            }
        },
        updateProcessByUserEvent(state, userEventData) {
            let processId = userEventData.recordId;

            // Получаем структуры вида [{processIndex, processId, pageIndex}]
            let processesIds = state.processPages.map(function (page, pageIndex) {
                return page.map(function (process, processIndex) {
                    return { processIndex: processIndex, processId: process.id, pageIndex: pageIndex }
                });
            }).flatMap(item => item);

            let process = userEventData.record;
            let isProcessInPages = processesIds.filter(item => processId === item.processId).length > 0;
            if (isProcessInPages) {
                let indexes = processesIds.find(item => item.processId === processId);
                let processIndex = indexes.processIndex;
                let pageIndex = indexes.pageIndex;
                state.processPages[pageIndex].splice(processIndex, 1, process);
                if (processId === state.currentProcessId) {
                    state.currentProcess = Object.assign({}, process)
                    state.snapshotProcess = Object.assign({}, process)
                }
            }

            if (process.status !== 'DONE' ) {
                state.workingProcess = Object.assign({}, process)
            } else {
                state.workingProcess = undefined
            }
        },
        addProcessByUserEvent(state, userEventData) {
            console.log('mutations addProcessByUserEvent from store.js Processes')
            console.log('Событие добавления процесса: ', userEventData)
        },
        deleteProcessByUserEvent(state, userEventData) {
            console.log('Событие удаления процесса: ', userEventData)
        },

        setLastUpdateProcess(state, lastUpdateProcess) {
            state.lastUpdateProcess = lastUpdateProcess;
        },
        setCurrentProcessId(state, currId) {
            let processWithIndexesFiltered = state.processPages.map(function (page, pageIndex) {
                return page.map(function (process, processIndex) {
                    return { process: process, processIndex: processIndex, processId: process.id, pageIndex: pageIndex }
                });
            }).flatMap(item => item).filter(item => item.processId === currId);
            let processWithIndexes = processWithIndexesFiltered.length ? processWithIndexesFiltered[0] : undefined;
            if (processWithIndexes) {
                state.currentProcess = processWithIndexes.process;
                state.currentProcessIndex = processWithIndexes.processIndex;
                state.currentProcessPageIndex = processWithIndexes.pageIndex;
                state.currentProcessId = processWithIndexes.process.id;
                state.snapshotProcess = Object.assign({}, state.currentProcess)
                console.log('currentProcessId: ', state.currentProcessId);
            }
        },
        async deleteCurrentProcess(state) {
            let params = { id: state.currentProcessId };
            let request = { method: 'POST', url: "/apis/process/delete", params: params };
            state.processPages[state.currentProcessPageIndex].splice(state.currentProcessIndex, 1);
            await promisedXMLHttpRequest(request);
        },
        setCurrentProcessPageIndex(state, pageNum) {
            if (state.processPages.length > pageNum) {
                let currentPage =  state.processPages[pageNum]
                if (currentPage.length > 0) {
                    let currentProcess = currentPage[0];
                    state.currentProcess = currentProcess;
                    state.currentProcessId = currentProcess.id;
                    state.currentProcessIndex = 0;
                    state.currentProcessPageIndex = pageNum;
                    state.snapshotProcess = Object.assign({}, state.currentProcess)
                }
            }
        },
        setProcessPageSize(state, newPageSize) {
            const oldPageSize = state.processPageSize;
            if (oldPageSize !== newPageSize) {
                const diffPageSize = newPageSize - oldPageSize;
                pages: for (let i = 0; i < state.processPages.length-1; i++ ) {
                    let currentPage = state.processPages[i];
                    let nextPage = state.processPages[i+1]
                    for ( let j = 0; j < Math.abs(diffPageSize) * (i + 1); j++ ) {
                        if (nextPage.length > 0) {
                            if (diffPageSize > 0) {
                                currentPage.push(nextPage.shift());
                                if (nextPage.length === 0) {
                                    state.processPages.splice(i+1,1);
                                    i--;
                                    continue pages;
                                }
                            } else {
                                nextPage.unshift(currentPage.pop());
                            }
                        }
                    }
                }
                while (state.processPages[state.processPages.length-1].length === 0) {
                    state.processPages.pop();
                }
                while (state.processPages[state.processPages.length-1].length > newPageSize) {
                    state.processPages.push([]);
                    while (state.processPages[state.processPages.length-2].length > newPageSize) {
                        state.processPages[state.processPages.length-1].unshift(state.processPages[state.processPages.length-2].pop())
                    }
                }

                state.processPageSize = newPageSize;
            }
        },
        setCurrentProcessField(state, payload) {
            state.currentProcess[payload.name] = payload.value;
        }
    },
    actions: {

        updateProcessByUserEvent(ctx, userEventData) {
            ctx.commit('updateProcessByUserEvent', userEventData);
        },
        addProcessByUserEvent(ctx, userEventData) {
            console.log('actions addProcessByUserEvent from store.js Processes')
            ctx.commit('addProcessByUserEvent', userEventData);
        },
        deleteProcessByUserEvent(ctx, userEventData) {
            ctx.commit('deleteProcessByUserEvent', userEventData);
        },

        updateProcessesByIds(ctx, payload) {
            ctx.commit('updateProcessesByIds', payload.processesAndIndexesForUpdate);
        },
        setLastUpdateProcess(ctx, payload) {
            ctx.commit('setLastUpdateProcess', payload.lastUpdateProcess);
        },
        setCurrentProcessField(ctx, payload) {
            ctx.commit('setCurrentProcessField', payload)
        },
        saveProcess(ctx, params) {
            params.id = ctx.state.currentProcessId;
            // console.log('params: ', params);
            let request = { method: 'POST', url: "/apis/process/update", params: params };
            promisedXMLHttpRequest(request).then(() => {
                ctx.commit('saveProcess')
            }).catch(error => {
                console.log(error);
            });
        },
        async loadProcessesAndDictionaries(ctx, params) {
            // console.log('params: ', params);
            params.pageSize = ctx.state.processPageSize;
            let request = { method: 'POST', url: "/apis/processes", params: params };
            promisedXMLHttpRequest(request).then(data => {
                let result = JSON.parse(data);
                // console.log('SongsAndDictionaries: ', result);
                ctx.commit('updateProcessesAndDictionaries', result)
            }).catch(error => {
                console.log(error);
            });
        },
        loadProcess(ctx, params) {
            // console.log('params: ', params);
            let request = { method: 'POST', url: "/apis/process", params: params };
            promisedXMLHttpRequest(request).then(data => {
                let result = JSON.parse(data);
                // console.log('Song: ', result);
                ctx.commit('updateProcess', result)
            }).catch(error => {
                console.log(error);
            });
        },

    }
}