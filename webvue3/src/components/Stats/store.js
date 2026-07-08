import {promisedXMLHttpRequest} from "../../lib/utils";

// Хелпер GET → JSON. promisedXMLHttpRequest не сериализует params в query-string для GET
// (устоявшийся квирк проекта), поэтому все параметры собираем в URL вручную.
function getJson(url) {
    return promisedXMLHttpRequest({ method: 'GET', url, params: {} }).then(data => JSON.parse(data));
}

export default {
    state: {
        statsTarget: 'local',
        statsDays: 30,
        // Сводка
        summary: null,
        summaryIsLoading: false,
        // Временной ряд. mode: 'all' | 'type' | 'detail'
        timeSeries: [],
        timeSeriesMode: 'all',
        timeSeriesIsLoading: false,
        // Разбивки
        byType: [],
        channels: [],
        detailed: [],
        breakdownIsLoading: false,
        // География + внешние источники
        countries: [],
        referrers: [],
        geoIsLoading: false,
        // Топ пользователей
        topUsers: [],
        topUsersTotalCount: 0,
        topUsersIsLoading: false,
        // Drill-down событий пользователя
        userEvents: [],
        userEventsTotalCount: 0,
        userEventsIsLoading: false,
        // Топ песен
        statsBySong: [],
        statsBySongIsLoading: false,
        statsBySongTotalCount: 0,
        // Drill-down событий песни
        songEvents: [],
        songEventsTotalCount: 0,
        songEventsIsLoading: false,
        // Лог событий
        webEvents: [],
        webEventsIsLoading: false,
        webEventsTotalCount: 0,
        // Монетизация (подписки)
        monetizationSummary: null,
        monetizationSummaryIsLoading: false,
        monetizationTopSongs: [],
        monetizationTopSongsIsLoading: false,
    },
    getters: {
        getStatsTarget(state) { return state.statsTarget },
        getStatsDays(state) { return state.statsDays },
        getStatsSummary(state) { return state.summary },
        getStatsSummaryIsLoading(state) { return state.summaryIsLoading },
        getStatsTimeSeries(state) { return state.timeSeries },
        getStatsTimeSeriesMode(state) { return state.timeSeriesMode },
        getStatsTimeSeriesIsLoading(state) { return state.timeSeriesIsLoading },
        getStatsByType(state) { return state.byType },
        getStatsChannels(state) { return state.channels },
        getStatsDetailed(state) { return state.detailed },
        getStatsBreakdownIsLoading(state) { return state.breakdownIsLoading },
        getStatsCountries(state) { return state.countries },
        getStatsReferrers(state) { return state.referrers },
        getStatsGeoIsLoading(state) { return state.geoIsLoading },
        getStatsTopUsers(state) { return state.topUsers },
        getStatsTopUsersTotalCount(state) { return state.topUsersTotalCount },
        getStatsTopUsersIsLoading(state) { return state.topUsersIsLoading },
        getStatsUserEvents(state) { return state.userEvents },
        getStatsUserEventsTotalCount(state) { return state.userEventsTotalCount },
        getStatsUserEventsIsLoading(state) { return state.userEventsIsLoading },
        getStatsBySong(state) { return state.statsBySong },
        getStatsBySongIsLoading(state) { return state.statsBySongIsLoading },
        getStatsBySongTotalCount(state) { return state.statsBySongTotalCount },
        getStatsSongEvents(state) { return state.songEvents },
        getStatsSongEventsTotalCount(state) { return state.songEventsTotalCount },
        getStatsSongEventsIsLoading(state) { return state.songEventsIsLoading },
        getWebEvents(state) { return state.webEvents },
        getWebEventsIsLoading(state) { return state.webEventsIsLoading },
        getWebEventsTotalCount(state) { return state.webEventsTotalCount },
        getMonetizationSummary(state) { return state.monetizationSummary },
        getMonetizationSummaryIsLoading(state) { return state.monetizationSummaryIsLoading },
        getMonetizationTopSongs(state) { return state.monetizationTopSongs },
        getMonetizationTopSongsIsLoading(state) { return state.monetizationTopSongsIsLoading },
    },
    mutations: {
        setStatsTarget(state, target) { state.statsTarget = target },
        setStatsDays(state, days) { state.statsDays = days },
        setStatsSummary(state, v) { state.summary = v },
        setStatsSummaryIsLoading(state, v) { state.summaryIsLoading = v },
        setStatsTimeSeries(state, v) { state.timeSeries = v },
        setStatsTimeSeriesMode(state, v) { state.timeSeriesMode = v },
        setStatsTimeSeriesIsLoading(state, v) { state.timeSeriesIsLoading = v },
        setStatsByType(state, v) { state.byType = v },
        setStatsChannels(state, v) { state.channels = v },
        setStatsDetailed(state, v) { state.detailed = v },
        setStatsBreakdownIsLoading(state, v) { state.breakdownIsLoading = v },
        setStatsCountries(state, v) { state.countries = v },
        setStatsReferrers(state, v) { state.referrers = v },
        setStatsGeoIsLoading(state, v) { state.geoIsLoading = v },
        setStatsTopUsers(state, v) { state.topUsers = v },
        setStatsTopUsersTotalCount(state, v) { state.topUsersTotalCount = v },
        setStatsTopUsersIsLoading(state, v) { state.topUsersIsLoading = v },
        setStatsUserEvents(state, v) { state.userEvents = v },
        setStatsUserEventsTotalCount(state, v) { state.userEventsTotalCount = v },
        setStatsUserEventsIsLoading(state, v) { state.userEventsIsLoading = v },
        setStatsBySong(state, data) { state.statsBySong = data },
        setStatsBySongIsLoading(state, v) { state.statsBySongIsLoading = v },
        setStatsBySongTotalCount(state, v) { state.statsBySongTotalCount = v },
        setStatsSongEvents(state, v) { state.songEvents = v },
        setStatsSongEventsTotalCount(state, v) { state.songEventsTotalCount = v },
        setStatsSongEventsIsLoading(state, v) { state.songEventsIsLoading = v },
        setWebEvents(state, data) { state.webEvents = data },
        setWebEventsIsLoading(state, v) { state.webEventsIsLoading = v },
        setWebEventsTotalCount(state, v) { state.webEventsTotalCount = v },
        setMonetizationSummary(state, v) { state.monetizationSummary = v },
        setMonetizationSummaryIsLoading(state, v) { state.monetizationSummaryIsLoading = v },
        setMonetizationTopSongs(state, v) { state.monetizationTopSongs = v },
        setMonetizationTopSongsIsLoading(state, v) { state.monetizationTopSongsIsLoading = v },
    },
    actions: {
        setStatsTarget(ctx, target) { ctx.commit('setStatsTarget', target) },
        setStatsDays(ctx, days) { ctx.commit('setStatsDays', days) },

        loadStatsSummary(ctx) {
            ctx.commit('setStatsSummaryIsLoading', true);
            getJson(`/api/stats/summary?target=${ctx.state.statsTarget}`).then(r => {
                ctx.commit('setStatsSummary', r.summary);
                ctx.commit('setStatsSummaryIsLoading', false);
            }).catch(e => { console.log(e); ctx.commit('setStatsSummaryIsLoading', false); });
        },
        loadStatsTimeSeries(ctx, { mode } = {}) {
            const m = mode !== undefined ? mode : ctx.state.timeSeriesMode;
            ctx.commit('setStatsTimeSeriesMode', m);
            ctx.commit('setStatsTimeSeriesIsLoading', true);
            getJson(`/api/stats/timeseries?target=${ctx.state.statsTarget}&days=${ctx.state.statsDays}&mode=${m}`).then(r => {
                ctx.commit('setStatsTimeSeries', r.items);
                ctx.commit('setStatsTimeSeriesIsLoading', false);
            }).catch(e => { console.log(e); ctx.commit('setStatsTimeSeriesIsLoading', false); });
        },
        loadStatsBreakdown(ctx) {
            ctx.commit('setStatsBreakdownIsLoading', true);
            Promise.all([
                getJson(`/api/stats/by-type?target=${ctx.state.statsTarget}&days=${ctx.state.statsDays}`),
                getJson(`/api/stats/channels?target=${ctx.state.statsTarget}`),
                getJson(`/api/stats/by-detail?target=${ctx.state.statsTarget}&days=${ctx.state.statsDays}`),
            ]).then(([byType, channels, detailed]) => {
                ctx.commit('setStatsByType', byType.items);
                ctx.commit('setStatsChannels', channels.items);
                ctx.commit('setStatsDetailed', detailed.items);
                ctx.commit('setStatsBreakdownIsLoading', false);
            }).catch(e => { console.log(e); ctx.commit('setStatsBreakdownIsLoading', false); });
        },
        loadStatsGeo(ctx) {
            ctx.commit('setStatsGeoIsLoading', true);
            Promise.all([
                getJson(`/api/stats/countries?target=${ctx.state.statsTarget}`),
                getJson(`/api/stats/referrers?target=${ctx.state.statsTarget}`),
            ]).then(([countries, referrers]) => {
                ctx.commit('setStatsCountries', countries.items);
                ctx.commit('setStatsReferrers', referrers.items);
                ctx.commit('setStatsGeoIsLoading', false);
            }).catch(e => { console.log(e); ctx.commit('setStatsGeoIsLoading', false); });
        },
        loadStatsTopUsers(ctx, { page = 1, pageSize = 50 } = {}) {
            ctx.commit('setStatsTopUsersIsLoading', true);
            getJson(`/api/stats/top-users?target=${ctx.state.statsTarget}&page=${page}&pageSize=${pageSize}`).then(r => {
                ctx.commit('setStatsTopUsers', r.items);
                ctx.commit('setStatsTopUsersTotalCount', r.totalCount);
                ctx.commit('setStatsTopUsersIsLoading', false);
            }).catch(e => { console.log(e); ctx.commit('setStatsTopUsersIsLoading', false); });
        },
        // Drill-down по пользователю: залогиненный (siteUserId>0) ЛИБО аноним (anonId).
        loadStatsUserEvents(ctx, { siteUserId = 0, anonId = '', page = 1, pageSize = 50 }) {
            ctx.commit('setStatsUserEventsIsLoading', true);
            let url = `/api/stats/user-events?target=${ctx.state.statsTarget}&page=${page}&pageSize=${pageSize}`;
            if (siteUserId) url += `&siteUserId=${siteUserId}`;
            if (anonId) url += `&anonId=${encodeURIComponent(anonId)}`;
            getJson(url).then(r => {
                ctx.commit('setStatsUserEvents', r.items);
                ctx.commit('setStatsUserEventsTotalCount', r.totalCount);
                ctx.commit('setStatsUserEventsIsLoading', false);
            }).catch(e => { console.log(e); ctx.commit('setStatsUserEventsIsLoading', false); });
        },
        loadStatsBySong(ctx, { page = 1, pageSize = 50 } = {}) {
            ctx.commit('setStatsBySongIsLoading', true);
            getJson(`/api/stats/by-song?target=${ctx.state.statsTarget}&page=${page}&pageSize=${pageSize}`).then(r => {
                ctx.commit('setStatsBySong', r.items);
                ctx.commit('setStatsBySongTotalCount', r.totalCount);
                ctx.commit('setStatsBySongIsLoading', false);
            }).catch(e => { console.log(e); ctx.commit('setStatsBySongIsLoading', false); });
        },
        // Drill-down по песне: все события конкретной песни (клик по строке «Топ песен»).
        loadStatsSongEvents(ctx, { songId, page = 1, pageSize = 2000 } = {}) {
            ctx.commit('setStatsSongEventsIsLoading', true);
            const url = `/api/stats/song-events?target=${ctx.state.statsTarget}&songId=${songId}&page=${page}&pageSize=${pageSize}`;
            getJson(url).then(r => {
                ctx.commit('setStatsSongEvents', r.items);
                ctx.commit('setStatsSongEventsTotalCount', r.totalCount);
                ctx.commit('setStatsSongEventsIsLoading', false);
            }).catch(e => { console.log(e); ctx.commit('setStatsSongEventsIsLoading', false); });
        },
        loadWebEvents(ctx, { page = 1, pageSize = 50, eventType = '', days = 0 } = {}) {
            ctx.commit('setWebEventsIsLoading', true);
            let url = `/api/webevents?target=${ctx.state.statsTarget}&page=${page}&pageSize=${pageSize}`;
            if (eventType) url += `&eventType=${eventType}`;
            if (days) url += `&days=${days}`;
            getJson(url).then(r => {
                ctx.commit('setWebEvents', r.items);
                ctx.commit('setWebEventsTotalCount', r.totalCount);
                ctx.commit('setWebEventsIsLoading', false);
            }).catch(e => { console.log(e); ctx.commit('setWebEventsIsLoading', false); });
        },
        loadMonetizationSummary(ctx) {
            ctx.commit('setMonetizationSummaryIsLoading', true);
            getJson(`/api/stats/monetization?target=${ctx.state.statsTarget}`).then(r => {
                ctx.commit('setMonetizationSummary', r.summary);
                ctx.commit('setMonetizationSummaryIsLoading', false);
            }).catch(e => { console.log(e); ctx.commit('setMonetizationSummaryIsLoading', false); });
        },
        loadMonetizationTopSongs(ctx, { limit = 20 } = {}) {
            ctx.commit('setMonetizationTopSongsIsLoading', true);
            getJson(`/api/stats/monetization/top-songs?target=${ctx.state.statsTarget}&limit=${limit}`).then(r => {
                ctx.commit('setMonetizationTopSongs', r.items);
                ctx.commit('setMonetizationTopSongsIsLoading', false);
            }).catch(e => { console.log(e); ctx.commit('setMonetizationTopSongsIsLoading', false); });
        },
    }
}
