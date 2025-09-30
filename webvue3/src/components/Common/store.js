import {promisedXMLHttpRequest} from '../../lib/utils'

export default {
    state: {
        logMessage: '',
        logText: ''
    },
    getters: {
        getLogMessage(state) {
            return state.logMessage
        },
        getLogText(state) {
            return state.logText
        },
        getWebvueProp: () => async (key, defaultValue) => {
            return await promisedXMLHttpRequest({
                method: 'POST',
                url: "/apis/getwebvueprop",
                params: {key: key, default: defaultValue}
            });
        }
    },
    mutations: {
        setLogMessage(state, text) {
            state.logMessage = text;
            let tmpText = state.logText + '\n' + text;
            let linesSplit = tmpText.split('\n').filter((line) => line.length > 0);
            let linesSplitReverse = linesSplit.reverse();
            let linesSplitReverseSlice = linesSplitReverse.slice(0,3);
            let linesSplitReverseSliceReverse = linesSplitReverseSlice.reverse();
            let lines = linesSplitReverseSliceReverse;
            let countLines = lines.length;
            let counter = 0;
            let i = 0;

            // console.log('countLines', countLines);
            let textResult = '';
            while (i < countLines) {
                const line = lines[i];
                i++;
                if (line !== '') {
                    if (counter !== 0 && line !== undefined) textResult += '\n';
                    if (line !== undefined) textResult += line;
                    counter++;
                }
            }
            state.logText = textResult;
        },
        setLogText(state, text) {
            state.logText = text;
        }
    },
    actions: {
        setLogMessage(ctx, text) {
            ctx.commit('setLogMessage', text);
        }
    }
}