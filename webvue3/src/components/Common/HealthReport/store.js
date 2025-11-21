import {promisedXMLHttpRequest} from "../../../lib/utils.js";

export default {
    state: {
        healthReportList: [],
        healthReportListIsLoading: false
    },
    getters: {
        getHealthReportList(state) {
            return state.healthReportList
        },
    },
    mutations: {
        updateHealthReportList(state, result) {
            state.healthReportList = result;
        },
        setHealthReportListIsLoading(state, isLoading) { state.healthReportListIsLoading = isLoading },
    },
    actions: {
        loadHealthReportList(ctx, id) {
            const params = {id: id};
            let request = { method: 'POST', url: "/api/song/healthReportList", params: params };
            ctx.commit('setHealthReportListIsLoading', true);
            promisedXMLHttpRequest(request).then(data => {
                let result = JSON.parse(data);
                console.log('loadHealthReportList result', result);
                ctx.commit('updateHealthReportList', result)
                ctx.commit('setHealthReportListIsLoading', false);
            }).catch(error => {
                console.log(error);
            });
        },
        repairOneRecord(ctx, item) {
            const params = {
                id: item.settingsId,
                healthReportStatusName: item.healthReportStatusName,
                customCode: item.customCode,
                songVersionName: item.songVersionName,
                karaokePlatformName: item.karaokePlatformName,
                karaokeFileTypeName: item.karaokeFileTypeName,
                karaokeFileActionTypeName: item.karaokeFileActionTypeName,
                karaokeFileTypeLocationsName: item.karaokeFileTypeLocationsName
            };
            let request = { method: 'POST', url: "/api/song/executeHealthReportActions", params: params };
            promisedXMLHttpRequest(request).then(data => {
                this.dispatch('loadHealthReportList', item.settingsId);
            }).catch(error => {
                console.log(error);
            });
        }
    }
}