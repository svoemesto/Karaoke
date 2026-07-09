import {promisedXMLHttpRequest} from "../../../lib/utils.js";

// Держим в синхроне с backend MonitorSeverity.rank (karaoke-app/.../monitor/MonitorSeverity.kt).
const SEVERITY_RANK = {INFO: 0, WARNING: 1, ERROR: 2, CRITICAL: 3};

export default {
    state: {
        monitorAlerts: []
    },
    getters: {
        // Прочитанные (read=true) не показываем в модалке и не учитываем в цвете светофора - пока
        // ситуация не изменится (см. MonitoringService.isRead / contentHash на бэкенде).
        monitorVisibleAlerts(state) {
            return state.monitorAlerts.filter(a => !a.read);
        },
        monitorTopSeverity(state, getters) {
            return getters.monitorVisibleAlerts.reduce((max, a) => Math.max(max, SEVERITY_RANK[a.severityName] ?? 0), -1);
        }
    },
    mutations: {
        setMonitorAlerts(state, list) {
            state.monitorAlerts = list;
        }
    },
    actions: {
        // Первичная загрузка при открытии/перезагрузке страницы - SSE (MONITOR_ALERTS) доносит
        // только новые тики фонового планировщика, а не текущее состояние на момент подключения.
        loadMonitorAlerts(ctx) {
            promisedXMLHttpRequest({method: 'GET', url: "/api/monitor/alerts"}).then(data => {
                ctx.commit('setMonitorAlerts', JSON.parse(data));
            }).catch(error => {
                console.log(error);
            });
        },
        monitorAlertsByUserEvent(ctx, data) {
            ctx.commit('setMonitorAlerts', data);
        },
        resolveMonitorAlert(ctx, key) {
            return promisedXMLHttpRequest({method: 'POST', url: "/api/monitor/resolve", params: {key: key}});
        },
        markMonitorRead(ctx, key) {
            return promisedXMLHttpRequest({method: 'POST', url: "/api/monitor/markRead", params: {key: key}});
        }
    }
}
