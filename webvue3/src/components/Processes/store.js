import {promisedXMLHttpRequest} from "../../lib/utils";

export default {
    state: {
        processesDigest: [],
        processesDigestIsLoading: false,
        processIsWorking: false,
        processWillStopAfterThreadIsDone: false,
        workingProcess: undefined,
        countWaiting: '...',
    },
    getters: {
        getWorkingProcess(state) {
            return state.workingProcess;
        },
        getProcessesDigest(state) {
            return state.processesDigest
        },
        getProcessesDigestIds(state) {
            return state.processesDigest ? state.processesDigest.flatMap(process => process.id) : []
        },
        getProcessesDigestIsLoading(state) { return state.processesDigestIsLoading },
        getProcessIsWorking(state) {
            return state.processIsWorking;
        },
        getProcessWillStopAfterThreadIsDone(state) {
            return state.processWillStopAfterThreadIsDone;
        },
        getCountWaiting(state) {
            return state.countWaiting;
        },
    },
    mutations: {
        updateProcessesDigests(state, result) {
            state.processesDigest = result.processesDigests;
        },
        setProcessesDigestIsLoading(state, isLoading) { state.processesDigestIsLoading = isLoading },
        updateProcessesDigestByIds(state, processesAndIndexesForUpdate) {
            // console.log('songsAndIndexesForUpdate', songsAndIndexesForUpdate)

            for (let processesAndIndexesFromRest of processesAndIndexesForUpdate) {

                for (let i = 0; i < state.processesDigest.length; i++) {
                    let processInDigest = state.processesDigest[i];
                    if (processInDigest.id === processesAndIndexesFromRest.song.id) {
                        state.processesDigest.splice(i,1,processesAndIndexesFromRest.song);
                    }
                }
            }
        },
        updateProcessByUserEvent(state, userEventData) {
            let processId = userEventData.recordId;
            for (let i = 0; i < state.processesDigest.length; i++) {
                let processInDigest = state.processesDigest[i];
                if (processInDigest.id === processId) {
                    state.processesDigest.splice(i,1,userEventData.record);
                }
            }
            if (userEventData.record.status !== 'DONE' ) {
                state.workingProcess = Object.assign({}, userEventData.record)
            } else {
                state.workingProcess = undefined
            }
        },
        addProcessByUserEvent(state, userEventData) {
            // console.log('mutations addProcessByUserEvent from store.js ProcessesBv')
            // console.log('Событие добавления процесса: ', userEventData)
            // console.log('Процесс добавлен не будет, установлена заглушка.')
            // let processId = userEventData.recordId;
            // let processPriority = userEventData.record.priority;
            // for (let i = 0; i < state.processesDigest.length; i++) {
            //     let processInDigest = state.processesDigest[i];
            //     if (processPriority < processInDigest.priority || (processPriority === processInDigest.priority && processId < processInDigest.id)) {
            //         state.processesDigest.splice(i,0,userEventData.record);
            //         return;
            //     }
            // }
            // state.processesDigest.splice(state.processesDigest.length-1,0,userEventData.record);
        },
        deleteProcessByUserEvent(state, userEventData) {
            // console.log('Событие удаления процесса: ', userEventData)
            let processId = userEventData.recordId;
            for (let i = 0; i < state.processesDigest.length; i++) {
                let processInDigest = state.processesDigest[i];
                if (processInDigest.id === processId) {
                    state.processesDigest.splice(i,1);
                }
            }
        },
        setProcessIsWorking(state, processIsWorking) {
            state.processIsWorking = processIsWorking;
        },
        setCountWaiting(state, userEventData) {
            state.countWaiting = userEventData.countWaiting;
        },
        setProcessWillStopAfterThreadIsDone(state, processWillStopAfterThreadIsDone) {
            state.processWillStopAfterThreadIsDone = processWillStopAfterThreadIsDone;
        },
        updateProcessWorkerStateByUserEvent(state, userEventData) {
            // console.log('Событие изменения статуса воркера процесса: ', userEventData)
            state.processIsWorking = userEventData.work;
            state.processWillStopAfterThreadIsDone = userEventData.stopAfterThreadIsDone;
        },
    },
    actions: {
        updateProcessesDigestByIds(ctx, payload) {
            ctx.commit('updateProcessesDigestByIds', payload.processesAndIndexesForUpdate);
        },
        updateProcessByUserEvent(ctx, userEventData) {
            ctx.commit('updateProcessByUserEvent', userEventData);
        },
        addProcessByUserEvent(ctx, userEventData) {
            // console.log('actions addProcessByUserEvent from store.js ProcessesBv')
            ctx.commit('addProcessByUserEvent', userEventData);
        },
        deleteProcessByUserEvent(ctx, userEventData) {
            ctx.commit('deleteProcessByUserEvent', userEventData);
        },
        loadProcessesDigests(ctx, params) {
            let request = { method: 'POST', url: "/api/processesdigests", params: params };
            ctx.commit('setProcessesDigestIsLoading', true);
            promisedXMLHttpRequest(request).then(data => {
                let result = JSON.parse(data);
                ctx.commit('updateProcessesDigests', result)
                ctx.commit('setProcessesDigestIsLoading', false);
            }).catch(error => {
                console.log(error);
            });
        },
        deleteDoneProcessesPromise: () => {
            let request = { method: 'POST', url: "/api/processes/deletedone" };
            return promisedXMLHttpRequest(request);
        },
        startStopProcessWorker: () => {
            let request = { method: 'POST', url: "/api/processes/workerstartstop" };
            promisedXMLHttpRequest(request);
        },
        getProcessesWorkerStatusPromise: () => {
            let request = { method: 'POST', url: "/api/processes/workerstatus" };
            return promisedXMLHttpRequest(request);
        },
        getProcessesCountWaitingPromise: () => {
            let request = { method: 'POST', url: "/api/processes/countwaiting" };
            return promisedXMLHttpRequest(request);
        },
        setProcessIsWorking(ctx, processIsWorking) {
            ctx.commit('setProcessIsWorking', processIsWorking);
        },
        setCountWaiting(ctx, userEventData) {
            ctx.commit('setCountWaiting', userEventData);
        },
        setProcessWillStopAfterThreadIsDone(ctx, processWillStopAfterThreadIsDone) {
            ctx.commit('setProcessWillStopAfterThreadIsDone', processWillStopAfterThreadIsDone);
        },
        updateProcessWorkerStateByUserEvent(ctx, userEventData) {
            ctx.commit('updateProcessWorkerStateByUserEvent', userEventData);
        },

    }
}