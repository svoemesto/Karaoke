export function promisedXMLHttpRequest (obj) {
    return new Promise((resolve, reject) => {
        let xhr = new XMLHttpRequest();
        xhr.open(obj.method || "GET", obj.url, true);
        if (obj.headers === undefined) obj.headers = {'Content-type': 'application/x-www-form-urlencoded'};
        if (obj.headers) {
            Object.keys(obj.headers).forEach(key => {
                xhr.setRequestHeader(key, obj.headers[key]);
            });
        }
        xhr.onload = () => {
            if (xhr.status >= 200 && xhr.status < 300) {
                resolve(xhr.response);
            } else {
                reject(xhr.statusText);
            }
        };
        xhr.onerror = () => reject(xhr.statusText);
        xhr.send(getParamStringToSend(obj.params));
    });
}

export function setWebvueProp (field, key, value) {
    if (value === undefined || value === null) value = '';
    if (field !== undefined && field !== null) {
        promisedXMLHttpRequest({
            method: 'POST',
            url: "/api/setwebvueprop",
            params: {key: key, value: value}
        });
    }
}

function getParamStringToSend (params) {
    let urlEncodedDataPairs = [], name;
    for( name in params ) {
        urlEncodedDataPairs.push(encodeURIComponent(name)+'='+encodeURIComponent(params[name]));
    }
    return urlEncodedDataPairs.join('&');
}

function addZero(num) {
    if (num >= 0 && num <= 9) {
        return '0' + num;
    } else {
        return num;
    }
}

export function isStringContainThisSymbols(sourceString, symbolString) {
    for (let i = 0; i < sourceString.length; i++) {
        const symbolInString = sourceString[i];
        if (symbolString.includes(symbolInString)) return true
    }
    return false
}

export function stringDDMMYYtoDate(stringDDMMYY) {
    let parts = stringDDMMYY.split('.');
    return new Date(+`20${parts[2]}`, parts[1] - 1, parts[0]);
}

export function dateToStringDDMMYY(date) {
    return `${addZero(date.getDate())}.${addZero(date.getMonth() + 1)}.${date.getFullYear().toString().substring(2)}`
}

export function stringDDMMYYaddDays(stringDDMMYY, days) {
    let date = stringDDMMYYtoDate(stringDDMMYY);
    date.setDate(date.getDate() + Number(days));
    return `${addZero(date.getDate())}.${addZero(date.getMonth() + 1)}.${date.getFullYear().toString().substring(2)}`
}