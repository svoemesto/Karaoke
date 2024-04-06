export default {
    state: {
        pages: [[]],
        authors: [],
        albums: [],
        currentPageIndex: 0,
        currentSongIndex: 0,
        currentSongId: 0,
        currentSong: undefined,
        pageSize: 45,
        snapshotSong: undefined,
        lastUpdate: Date.now(),
        fieldParams: [
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
                name: 'songName',
                params: {
                    width: '250',
                    label: 'Композиция',
                    textAlign: 'left',
                    fontSize: 'small',
                    labelFontSize: 'small',
                    labelPadding: '0 4px 0 4px',
                    filterWidth: '246',
                    filterFontSize: 'small'
                }
            },
            {
                name: 'author',
                params: {
                    width: '150',
                    label: 'Исполнитель',
                    textAlign: 'left',
                    fontSize: 'small',
                    labelFontSize: 'small',
                    labelPadding: '0 4px 0 4px',
                    filterWidth: '146',
                    filterFontSize: 'small'
                }
            },
            {
                name: 'year',
                params: {
                    width: '50',
                    label: 'Год',
                    textAlign: 'center',
                    fontSize: 'small',
                    labelFontSize: 'small',
                    labelPadding: '0 4px 0 4px',
                    filterWidth: '46',
                    filterFontSize: 'small'
                }
            },
            {
                name: 'album',
                params: {
                    width: '175',
                    label: 'Альбом',
                    textAlign: 'left',
                    fontSize: 'small',
                    labelFontSize: 'small',
                    labelPadding: '0 4px 0 4px',
                    filterWidth: '171',
                    filterFontSize: 'small'
                }
            },
            {
                name: 'track',
                params: {
                    width: '35',
                    label: '№',
                    textAlign: 'center',
                    fontSize: 'small',
                    labelFontSize: 'small',
                    labelPadding: '0 4px 0 4px',
                    filterWidth: '31',
                    filterFontSize: 'small'
                }
            },
            {
                name: 'date',
                params: {
                    width: '60',
                    label: 'Дата',
                    textAlign: 'center',
                    fontSize: 'small',
                    labelFontSize: 'small',
                    labelPadding: '0 4px 0 4px',
                    filterWidth: '56',
                    filterFontSize: 'small'
                }
            },
            {
                name: 'time',
                params: {
                    width: '50',
                    label: 'Время',
                    textAlign: 'center',
                    fontSize: 'small',
                    labelFontSize: 'small',
                    labelPadding: '0 4px 0 4px',
                    filterWidth: '46',
                    filterFontSize: 'small'
                }
            },
            {
                name: 'tags',
                params: {
                    width: '35',
                    label: 'Tags',
                    textAlign: 'center',
                    fontSize: 'small',
                    labelFontSize: 'x-small',
                    labelPadding: '2px 4px 0 4px',
                    filterWidth: '31',
                    filterFontSize: 'small'
                }
            },
            {
                name: 'status',
                params: {
                    width: '100',
                    label: 'Status',
                    textAlign: 'center',
                    fontSize: 'small',
                    labelFontSize: 'small',
                    labelPadding: '0 4px 0 4px',
                    filterWidth: '96',
                    filterFontSize: 'small'
                }
            },
            {
                name: 'flagBoosty',
                params: {
                    width: '20',
                    label: 'BOO',
                    textAlign: 'center',
                    fontSize: 'small',
                    labelFontSize: '4pt',
                    labelPadding: '6px 4px 0 4px',
                    filterWidth: '16',
                    filterFontSize: 'small'
                }
            },
            {
                name: 'flagVk',
                params: {
                    width: '20',
                    label: 'VG',
                    textAlign: 'center',
                    fontSize: 'small',
                    labelFontSize: '6pt',
                    labelPadding: '3px 4px 0 4px',
                    filterWidth: '16',
                    filterFontSize: 'small'
                }
            },
            {
                name: 'flagYoutubeLyrics',
                params: {
                    width: '20',
                    label: 'ZL',
                    textAlign: 'center',
                    fontSize: 'small',
                    labelFontSize: '6pt',
                    labelPadding: '3px 4px 0 4px',
                    filterWidth: '16',
                    filterFontSize: 'small'
                }
            },
            {
                name: 'flagYoutubeKaraoke',
                params: {
                    width: '20',
                    label: 'ZK',
                    textAlign: 'center',
                    fontSize: 'small',
                    labelFontSize: '6pt',
                    labelPadding: '3px 4px 0 4px',
                    filterWidth: '16',
                    filterFontSize: 'small'
                }
            },
            {
                name: 'flagVkLyrics',
                params: {
                    width: '20',
                    label: 'VL',
                    textAlign: 'center',
                    fontSize: 'small',
                    labelFontSize: '6pt',
                    labelPadding: '3px 4px 0 4px',
                    filterWidth: '16',
                    filterFontSize: 'small'
                }
            },
            {
                name: 'flagVkKaraoke',
                params: {
                    width: '20',
                    label: 'VK',
                    textAlign: 'center',
                    fontSize: 'small',
                    labelFontSize: '6pt',
                    labelPadding: '3px 4px 0 4px',
                    filterWidth: '16',
                    filterFontSize: 'small'
                }
            },
            {
                name: 'flagTelegramLyrics',
                params: {
                    width: '20',
                    label: 'TL',
                    textAlign: 'center',
                    fontSize: 'small',
                    labelFontSize: '6pt',
                    labelPadding: '3px 4px 0 4px',
                    filterWidth: '16',
                    filterFontSize: 'small'
                }
            },
            {
                name: 'flagTelegramKaraoke',
                params: {
                    width: '20',
                    label: 'TK',
                    textAlign: 'center',
                    fontSize: 'small',
                    labelFontSize: '6pt',
                    labelPadding: '3px 4px 0 4px',
                    filterWidth: '16',
                    filterFontSize: 'small'
                }
            }
        ]
    },
    getters: {
        getSongs(state) {
            return state.pages.length ? state.pages[state.currentPageIndex] : [];
        },
        getAuthors(state) {
            return state.authors.length ? state.authors : [];
        },
        getAlbums(state) {
            return state.albums.length ? state.albums : [];
        },
        getCountSongs(state) {
            return state.pages.map(it => it.length).reduce((accumulator, currentValue) => {
                return accumulator + currentValue
            },0);
        },
        getCountPages(state) {
            return state.pages.length;
        },
        getCurrentPageIndex(state) {
            return state.currentPageIndex;
        },
        getCurrentSongIndex(state) {
            return state.currentSongIndex;
        },
        getCurrentSong(state) {
            return state.currentSong;
        },
        getCurrentSongId(state) {
            return state.currentSongId;
        },
        getPageSize(state) {
            return state.pageSize;
        },
        getFieldParams: (state) => (name) => {
            return state.fieldParams.filter(fieldParam => fieldParam.name === name)[0].params;
        },
        getFieldsParamsWidth(state) {
            return state.fieldParams
              .map(fp => fp.params)
              .map(p => p.width)
              .reduce((accumulator, currentValue) => {
                  return accumulator + +currentValue;
              },0);
        },
        getFirstSong(state) {
            return state.pages.length && state.pages[state.currentPageIndex][0] ? state.pages[state.currentPageIndex][0] : [];
        },
        getLastSong(state) { return state.pages.length ? state.pages[state.currentPageIndex][-1] : [] },
        getSnapshotSong(state) {
            return state.snapshotSong;
        },
        getLastUpdate(state) {
            return state.lastUpdate;
        },
        getDiff(state) {
            let result = [];
            if (state.currentSong && state.snapshotSong) {
                for (let key of Object.keys(state.currentSong)) {
                    let oldValue = state.snapshotSong[key];
                    let newValue = state.currentSong[key];
                    if (oldValue !== newValue) {
                        result.push({name: key, new: newValue, old: oldValue});
                    }
                }
            }
            return result;
        },
        isChanged(state) {
            let result = [];
            if (state.currentSong && state.snapshotSong) {
                for (let key of Object.keys(state.currentSong)) {
                    let oldValue = state.snapshotSong[key];
                    let newValue = state.currentSong[key];
                    if (oldValue !== newValue) {
                        result.push({name: key, new: newValue, old: oldValue});
                    }
                }
            }
            return result.length > 0
        },
        async getVoices(state) {
            return await promisedXMLHttpRequest({
                method: 'POST',
                url: "/apis/song/voices",
                params: { id: state.currentSongId }
            });
        },
        async getBoostyHeader(state) {
            return await promisedXMLHttpRequest({
                method: 'POST',
                url: "/apis/song/textboostyhead",
                params: { id: state.currentSongId }
            });
        },
        async getBoostyBody(state) {
            return await promisedXMLHttpRequest({
                method: 'POST',
                url: "/apis/song/textboostybody",
                params: { id: state.currentSongId }
            });
        },
        async getBoostyFilesHeader(state) {
            return await promisedXMLHttpRequest({
                method: 'POST',
                url: "/apis/song/textboostyfileshead",
                params: { id: state.currentSongId }
            });
        },
        async getVkGroupBody(state) {
            return await promisedXMLHttpRequest({
                method: 'POST',
                url: "/apis/song/textvkbody",
                params: { id: state.currentSongId }
            });
        },
        async getDzenKaraokeHeader(state) {
            return await promisedXMLHttpRequest({
                method: 'POST',
                url: "/apis/song/textyoutubekaraokeheader",
                params: { id: state.currentSongId }
            });
        },
        async getDzenKaraokeBody(state) {
            return await promisedXMLHttpRequest({
                method: 'POST',
                url: "/apis/song/textyoutubekaraokewoheader",
                params: { id: state.currentSongId }
            });
        },
        async getDzenLyricsHeader(state) {
            return await promisedXMLHttpRequest({
                method: 'POST',
                url: "/apis/song/textyoutubelyricsheader",
                params: { id: state.currentSongId }
            });
        },
        async getDzenLyricsBody(state) {
            return await promisedXMLHttpRequest({
                method: 'POST',
                url: "/apis/song/textyoutubelyricswoheader",
                params: { id: state.currentSongId }
            });
        },
        async getVkKaraokeHeader(state) {
            return await promisedXMLHttpRequest({
                method: 'POST',
                url: "/apis/song/textvkkaraokeheader",
                params: { id: state.currentSongId }
            });
        },
        async getVkKaraokeBody(state) {
            return await promisedXMLHttpRequest({
                method: 'POST',
                url: "/apis/song/textvkkaraoke",
                params: { id: state.currentSongId }
            });
        },
        async getVkLyricsHeader(state) {
            return await promisedXMLHttpRequest({
                method: 'POST',
                url: "/apis/song/textvklyricsheader",
                params: { id: state.currentSongId }
            });
        },
        async getVkLyricsBody(state) {
            return await promisedXMLHttpRequest({
                method: 'POST',
                url: "/apis/song/textvklyrics",
                params: { id: state.currentSongId }
            });
        },
        async getTelegramKaraokeHeader(state) {
            return await promisedXMLHttpRequest({
                method: 'POST',
                url: "/apis/song/texttelegramkaraokeheader",
                params: { id: state.currentSongId }
            });
        },
        async getTelegramLyricsHeader(state) {
            return await promisedXMLHttpRequest({
                method: 'POST',
                url: "/apis/song/texttelegramlyricsheader",
                params: { id: state.currentSongId }
            });
        },
        // TODO перенести в actions
        async playKaraoke(state) {
            return await promisedXMLHttpRequest({
                method: 'POST',
                url: "/apis/song/playkaraoke",
                params: { id: state.currentSongId }
            });
        },
        // TODO перенести в actions
        async playLyrics(state) {
            return await promisedXMLHttpRequest({
                method: 'POST',
                url: "/apis/song/playlyrics",
                params: { id: state.currentSongId }
            });
        },
        async getSongsIdsForUpdate(state) {
            return await promisedXMLHttpRequest({
                method: 'POST',
                url: "/apis/songs/changed",
                params: { time: state.lastUpdate }
            });
        },
        getSongsIds(state) {
            return state.pages.map(function (page, pageIndex) {
                return page.map(function (song, songIndex) {
                    return { songIndex: songIndex, songId: song.id, pageIndex: pageIndex }
                });
            }).flatMap(item => item);
        },
        getSongsForUpdateByIds: () => async (ids) => {
            console.log('getSongsForUpdateByIds ids: ', ids);
            let params = { ids: ids };
            let request = { method: 'POST', url: "/apis/songs/ids", params: params };
            return await promisedXMLHttpRequest(request);
        }
    },
    mutations: {
        save(state) {
            state.snapshotSong = !state.currentSong ? undefined : Object.assign({}, state.currentSong)
        },
        updateSongsAndDictionaries(state, result) {
            state.currentPageIndex = 0;
            state.pages = result.pages;
            state.authors = result.authors;
            state.albums = result.albums;
            state.currentSong = state.pages && state.pages.length && state.pages[0].length ? state.pages[0][0] : undefined;
            state.currentSongIndex = state.pages && state.pages.length && state.pages[0].length ? 0 : undefined;
            state.currentSongId = !state.currentSong ? 0 : state.currentSong.id;
            state.snapshotSong = !state.currentSong ? undefined : Object.assign({}, state.currentSong)
        },
        updateSong(state, songFromRest) {
            if (songFromRest) {
                const id = songFromRest.id;

                let songWithIndexesFiltered = state.pages.map(function (page, pageIndex) {
                    return page.map(function (song, songIndex) {
                        return { song: song, songIndex: songIndex, songId: song.id, pageIndex: pageIndex }
                    });
                }).flatMap(item => item).filter(item => item.songId === id);
                let songWithIndexes = songWithIndexesFiltered.length ? songWithIndexesFiltered[0] : undefined;
                if (songWithIndexes) {
                    console.log('Обновляем песню ID =', id);
                    if (id === state.currentSongId) {
                        state.snapshotSong = Object.assign({}, songFromRest)
                    } else {
                        state.pages[songWithIndexes.pageIndex].splice(songWithIndexes.songIndex, 1, songFromRest);
                    }
                }
            }
        },
        updateSongsByIds(state, songsAndIndexesForUpdate) {
            console.log('songsAndIndexesForUpdate', songsAndIndexesForUpdate)
            for (let songsAndIndexesFromRest of songsAndIndexesForUpdate) {
                state.pages[songsAndIndexesFromRest.pageIndex].splice(songsAndIndexesFromRest.songIndex, 1, songsAndIndexesFromRest.song);
                if (songsAndIndexesFromRest.songId === state.currentSongId) {
                    state.snapshotSong = Object.assign({}, songsAndIndexesFromRest.song)
                }
            }
        },
        setLastUpdate(state, lastUpdate) {
          state.lastUpdate = lastUpdate;
        },
        setCurrentSongId(state, currId) {

            let songWithIndexesFiltered = state.pages.map(function (page, pageIndex) {
                return page.map(function (song, songIndex) {
                    return { song: song, songIndex: songIndex, songId: song.id, pageIndex: pageIndex }
                });
            }).flatMap(item => item).filter(item => item.songId === currId);

            let songWithIndexes = songWithIndexesFiltered.length ? songWithIndexesFiltered[0] : undefined;
            if (songWithIndexes) {
                state.currentSong = songWithIndexes.song;
                state.currentSongId = songWithIndexes.song.id;
                state.currentSongIndex = songWithIndexes.songIndex;
                state.currentPageIndex = songWithIndexes.pageIndex;
                state.snapshotSong = Object.assign({}, state.currentSong)
            }
        },
        setCurrentPageIndex(state, pageNum) {
            if (state.pages.length > pageNum) {
                let currentPage =  state.pages[pageNum]
                if (currentPage.length > 0) {
                    let currentSong = currentPage[0];
                    state.currentSong = currentSong;
                    state.currentSongId = currentSong.id;
                    state.currentSongIndex = 0;
                    state.currentPageIndex = pageNum;
                    state.snapshotSong = Object.assign({}, state.currentSong)
                }
            }
        },
        setPageSize(state, newPageSize) {
            const oldPageSize = state.pageSize;
            if (oldPageSize !== newPageSize) {
                const diffPageSize = newPageSize - oldPageSize;
                pages: for ( let i = 0; i < state.pages.length-1; i++ ) {
                    let currentPage = state.pages[i];
                    let nextPage = state.pages[i+1]
                    for ( let j = 0; j < Math.abs(diffPageSize) * (i + 1); j++ ) {
                        if (nextPage.length > 0) {
                            if (diffPageSize > 0) {
                                currentPage.push(nextPage.shift());
                                if (nextPage.length === 0) {
                                    state.pages.splice(i+1,1);
                                    i--;
                                    continue pages;
                                }
                            } else {
                                nextPage.unshift(currentPage.pop());
                            }
                        }
                    }
                }
                while (state.pages[state.pages.length-1].length === 0) {
                    state.pages.pop();
                }
                while (state.pages[state.pages.length-1].length > newPageSize) {
                    state.pages.push([]);
                    while (state.pages[state.pages.length-2].length > newPageSize) {
                        state.pages[state.pages.length-1].unshift(state.pages[state.pages.length-2].pop())
                    }
                }

                state.pageSize = newPageSize;
            }
        },
        setCurrentSongField(state, payload) {
            state.currentSong[payload.name] = payload.value;
        }
    },
    actions: {
        async saveSourceMarkers(ctx, payload) {
            let params = {
                id: payload.id,
                voice: payload.voice,
                sourceMarkers: payload.sourceMarkers
            };
            let request = { method: 'POST', url: "/apis/song/savesourcemarkers", params: params };
            return await promisedXMLHttpRequest(request);
        },
        async saveSourceText(ctx, payload) {
            let params = {
                id: payload.id,
                voice: payload.voice,
                sourceText: payload.sourceText
            };
            let request = { method: 'POST', url: "/apis/song/savesourcetext", params: params };
            return await promisedXMLHttpRequest(request);
        },
        updateSongsByIds(ctx, payload) {
            ctx.commit('updateSongsByIds', payload.songsAndIndexesForUpdate);
        },
        setLastUpdate(ctx, payload) {
            ctx.commit('setLastUpdate', payload.lastUpdate);
        },
        setCurrentSongField(ctx, payload) {
            ctx.commit('setCurrentSongField', payload)
        },
        save(ctx, params) {
            params.id = ctx.state.currentSongId;
            // console.log('params: ', params);
            let request = { method: 'POST', url: "/apis/update", params: params };
            promisedXMLHttpRequest(request).then(() => {
                ctx.commit('save')
            }).catch(error => {
                console.log(error);
            });
        },
        async loadSongsAndDictionaries(ctx, params) {
            console.log('params: ', params);
            params.pageSize = ctx.state.pageSize;
            let request = { method: 'POST', url: "/apis/songs", params: params };
            promisedXMLHttpRequest(request).then(data => {
                let result = JSON.parse(data);
                console.log('result: ', result);
                ctx.commit('updateSongsAndDictionaries', result)
            }).catch(error => {
                console.log(error);
            });
        },
        async loadSong(ctx, params) {
            console.log('params: ', params);
            params.pageSize = ctx.state.pageSize;
            let request = { method: 'POST', url: "/apis/song", params: params };
            promisedXMLHttpRequest(request).then(data => {
                let result = JSON.parse(data);
                console.log('result: ', result);
                ctx.commit('updateSong', result)
            }).catch(error => {
                console.log(error);
            });
        }
    }
}

function getParamStringToSend (params) {
    let urlEncodedDataPairs = [], name;
    for( name in params ) {
        urlEncodedDataPairs.push(encodeURIComponent(name)+'='+encodeURIComponent(params[name]));
    }
    return urlEncodedDataPairs.join('&');
}

function promisedXMLHttpRequest (obj) {
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