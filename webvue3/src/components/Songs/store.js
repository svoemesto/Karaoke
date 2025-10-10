import { promisedXMLHttpRequest } from '../../lib/utils'
// import {useIntervalFn} from "@vueuse/core";
export default {
    state: {
        toSync: false,
        lastSettingType: '',
        lastSettingValue: '',
        lastPriorLyrics: '',
        lastPriorKaraoke: '',
        lastPriorChords: '',
        lastPriorMelody: '',
        lastPriorCodeLyrics: '',
        lastPriorCodeKaraoke: '',
        lastPriorDemucs: '',
        lastPriorSymlinks: '',
        lastPriorSmartCopy: '',
        songPages: [[]],
        songAuthors: [],
        songAlbums: [],
        currentSongPageIndex: 0,
        currentSongIndex: 0,
        currentSongId: 0,
        previousSongId: undefined,
        nextSongId: undefined,
        leftSongId: undefined,
        rightSongId: undefined,
        currentSong: undefined,
        songPageSize: 50,
        snapshotSong: undefined,
        lastUpdateSong: Date.now(),
        fieldSongParams: [
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
                name: 'resultVersion',
                params: {
                    width: '20',
                    label: 'V',
                    textAlign: 'center',
                    fontSize: 'small',
                    labelFontSize: '4pt',
                    labelPadding: '6px 4px 0 4px',
                    filterWidth: '16',
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
                name: 'flagSponsr',
                params: {
                    width: '20',
                    label: 'SP',
                    textAlign: 'center',
                    fontSize: 'small',
                    labelFontSize: '6pt',
                    labelPadding: '3px 4px 0 4px',
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
                name: 'flagDzenLyrics',
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
                name: 'flagDzenKaraoke',
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
        ],

        publications: [],
        unpublications: [],
        skipedpublications: [],

        songsDigest: [],
        songsDigestIsLoading: false,
        totalDuration: '',
    },
    watch: {
        currentSongId: {
            async handler () {
                await this.$store.dispatch('setPreviousAndNextSongId');
            }
        }
    },
    getters: {
        getToSync(state) {
            return state.toSync;
        },
        getLastSettingType(state) {
            return state.lastSettingType;
        },
        getLastSettingValue(state) {
            return state.lastSettingValue;
        },
        getLastPriorLyrics(state) {
            // console.log('getter getLastPriorLyrics', state.lastPriorLyrics);
            return state.lastPriorLyrics;
        },
        getLastPriorKaraoke(state) {
            return state.lastPriorKaraoke;
        },
        getLastPriorChords(state) {
            return state.lastPriorChords;
        },
        getLastPriorMelody(state) {
            return state.lastPriorMelody;
        },
        getLastPriorCodeLyrics(state) {
            return state.lastPriorCodeLyrics;
        },
        getLastPriorCodeKaraoke(state) {
            return state.lastPriorCodeKaraoke;
        },
        getLastPriorDemucs(state) {
            return state.lastPriorDemucs;
        },
        getLastPriorSymlinks(state) {
            return state.lastPriorSymlinks;
        },
        getLastPriorSmartCopy(state) {
            return state.lastPriorSmartCopy;
        },
        getSongs(state) {
            return state.songPages.length ? state.songPages[state.currentSongPageIndex] : [];
        },
        getAllSongsIds(state) {
            return state.songPages.length ? state.songPages.flatMap(page => page.flatMap(song => song.id)) : [];
        },
        getAuthors(state) {
            return state.songAuthors.length ? state.songAuthors : [];
        },
        getAlbums(state) {
            return state.songAlbums.length ? state.songAlbums : [];
        },
        getCountSongs(state) {
            return state.songPages.map(it => it.length).reduce((accumulator, currentValue) => {
                return accumulator + currentValue
            },0);
        },
        getCountSongPages(state) {
            return state.songPages.length;
        },
        getCurrentSongPageIndex(state) {
            return state.currentSongPageIndex;
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
        getPreviousSongId(state) {
            console.log('getPreviousSongId', state.previousSongId);
            return state.previousSongId;
        },
        getNextSongId(state) {
            console.log('getNextSongId', state.nextSongId);
            return state.nextSongId;
        },
        getLeftSongId(state) {
            console.log('getLeftSongId', state.leftSongId);
            return state.leftSongId;
        },
        getRightSongId(state) {
            console.log('getRightSongId', state.rightSongId);
            return state.rightSongId;
        },
        getSongPageSize(state) {
            return state.songPageSize;
        },
        getSongFieldParams: (state) => (name) => {
            return state.fieldSongParams.filter(fieldParam => fieldParam.name === name)[0].params;
        },
        getSongFieldsParamsWidth(state) {
            return state.fieldSongParams
              .map(fp => fp.params)
              .map(p => p.width)
              .reduce((accumulator, currentValue) => {
                  return accumulator + +currentValue;
              },0);
        },
        // getProp: (state) => (key) => {
        //     return state.fieldSongParams.filter(fieldParam => fieldParam.name === name)[0].params;
        // },
        getFirstSong(state) {
            return state.currentSongPageIndex >=0 && state.songPages.length && state.songPages[state.currentSongPageIndex][0] ? state.songPages[state.currentSongPageIndex][0] : [];
        },
        getLastSong(state) { return state.currentSongPageIndex >=0 && state.songPages.length ? state.songPages[state.currentSongPageIndex][-1] : [] },
        getSnapshotSong(state) {
            return state.snapshotSong;
        },
        getLastUpdateSong(state) {
            return state.lastUpdateSong;
        },
        getSongDiff(state) {
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
        isChangedSong(state) {
            if (state.currentSong && state.snapshotSong) {
                for (let key of Object.keys(state.currentSong)) {
                    let oldValue = state.snapshotSong[key];
                    let newValue = state.currentSong[key];
                    if (oldValue !== newValue) {
                        return true;
                    }
                }
            }
            return false
        },
        async getSongsIdsForUpdate(state) {
            return await promisedXMLHttpRequest({
                method: 'POST',
                url: "/apis/songs/changed",
                params: { time: state.lastUpdateSong }
            });
        },
        getSongsIds(state) {
            return state.songPages.map(function (page, pageIndex) {
                return page.map(function (song, songIndex) {
                    return { songIndex: songIndex, songId: song.id, pageIndex: pageIndex }
                });
            }).flatMap(item => item);
        },
        getSongsForUpdateByIds: () => async (ids) => {
            // console.log('getSongsForUpdateByIds ids: ', ids);
            let params = { ids: ids };
            let request = { method: 'POST', url: "/apis/songs/ids", params: params };
            return await promisedXMLHttpRequest(request);
        },
        async songAuthors() {
            let request = { method: 'POST', url: "/apis/songs/authors"};
            return JSON.parse(await promisedXMLHttpRequest(request)).authors;
        },

        async dicst() {
            let request = { method: 'POST', url: "/apis/songs/dicts"};
            return JSON.parse(await promisedXMLHttpRequest(request)).dicts;
        },


        getReplacedSymbolsInText: () => async (txt) => {
            return await promisedXMLHttpRequest({
                method: 'POST',
                url: "/apis/replacesymbolsinsong",
                params: { txt: txt }
            });
        },
        async getSearchSongText(state) {
            return await promisedXMLHttpRequest({
                method: 'POST',
                url: "/apis/searchsongtext",
                params: { id: state.currentSongId }
            });
        },
        doTfd: () => async (params) =>  {
            return await promisedXMLHttpRequest({
                method: 'POST',
                url: "/apis/utils/tfd",
                params: params
            });
        },
        async getSheetsageinfoBpm(state) {
            return await promisedXMLHttpRequest({
                method: 'POST',
                url: "/apis/song/sheetsageinfobpm",
                params: { id: state.currentSongId }
            });
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
        async getSponsrHeader(state) {
            return await promisedXMLHttpRequest({
                method: 'POST',
                url: "/apis/song/textsponsrhead",
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
        async getSponsrBody(state) {
            return await promisedXMLHttpRequest({
                method: 'POST',
                url: "/apis/song/textsponsrbody",
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
                url: "/apis/song/textdzenkaraokeheader",
                params: { id: state.currentSongId }
            });
        },
        async getDzenKaraokeBody(state) {
            return await promisedXMLHttpRequest({
                method: 'POST',
                url: "/apis/song/textdzenkaraokewoheader",
                params: { id: state.currentSongId }
            });
        },
        async getDzenLyricsHeader(state) {
            return await promisedXMLHttpRequest({
                method: 'POST',
                url: "/apis/song/textdzenlyricsheader",
                params: { id: state.currentSongId }
            });
        },
        async getDzenLyricsBody(state) {
            return await promisedXMLHttpRequest({
                method: 'POST',
                url: "/apis/song/textdzenlyricswoheader",
                params: { id: state.currentSongId }
            });
        },
        async getDzenChordsHeader(state) {
            return await promisedXMLHttpRequest({
                method: 'POST',
                url: "/apis/song/textdzenchordsheader",
                params: { id: state.currentSongId }
            });
        },
        async getDzenTabsHeader(state) {
            return await promisedXMLHttpRequest({
                method: 'POST',
                url: "/apis/song/textdzentabsheader",
                params: { id: state.currentSongId }
            });
        },
        async getDzenChordsBody(state) {
            return await promisedXMLHttpRequest({
                method: 'POST',
                url: "/apis/song/textdzenchordswoheader",
                params: { id: state.currentSongId }
            });
        },
        async getDzenTabsBody(state) {
            return await promisedXMLHttpRequest({
                method: 'POST',
                url: "/apis/song/textdzentabswoheader",
                params: { id: state.currentSongId }
            });
        },
        async getPlKaraokeHeader(state) {
            return await promisedXMLHttpRequest({
                method: 'POST',
                url: "/apis/song/textplkaraokeheader",
                params: { id: state.currentSongId }
            });
        },
        async getPlKaraokeBody(state) {
            return await promisedXMLHttpRequest({
                method: 'POST',
                url: "/apis/song/textplkaraokewoheader",
                params: { id: state.currentSongId }
            });
        },
        async getPlLyricsHeader(state) {
            return await promisedXMLHttpRequest({
                method: 'POST',
                url: "/apis/song/textpllyricsheader",
                params: { id: state.currentSongId }
            });
        },
        async getPlLyricsBody(state) {
            return await promisedXMLHttpRequest({
                method: 'POST',
                url: "/apis/song/textpllyricswoheader",
                params: { id: state.currentSongId }
            });
        },
        async getPlChordsHeader(state) {
            return await promisedXMLHttpRequest({
                method: 'POST',
                url: "/apis/song/textplchordsheader",
                params: { id: state.currentSongId }
            });
        },
        async getPlTabsHeader(state) {
            return await promisedXMLHttpRequest({
                method: 'POST',
                url: "/apis/song/textpltabsheader",
                params: { id: state.currentSongId }
            });
        },
        async getPlChordsBody(state) {
            return await promisedXMLHttpRequest({
                method: 'POST',
                url: "/apis/song/textplchordswoheader",
                params: { id: state.currentSongId }
            });
        },
        async getPlTabsBody(state) {
            return await promisedXMLHttpRequest({
                method: 'POST',
                url: "/apis/song/textpltabswoheader",
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
        async getVkChordsHeader(state) {
            return await promisedXMLHttpRequest({
                method: 'POST',
                url: "/apis/song/textvkchordsheader",
                params: { id: state.currentSongId }
            });
        },
        async getVkTabsHeader(state) {
            return await promisedXMLHttpRequest({
                method: 'POST',
                url: "/apis/song/textvktabsheader",
                params: { id: state.currentSongId }
            });
        },
        async getVkChordsBody(state) {
            return await promisedXMLHttpRequest({
                method: 'POST',
                url: "/apis/song/textvkchords",
                params: { id: state.currentSongId }
            });
        },
        async getVkTabsBody(state) {
            return await promisedXMLHttpRequest({
                method: 'POST',
                url: "/apis/song/textvktabs",
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
        async getTelegramChordsHeader(state) {
            return await promisedXMLHttpRequest({
                method: 'POST',
                url: "/apis/song/texttelegramchordsheader",
                params: { id: state.currentSongId }
            });
        },
        async getTelegramTabsHeader(state) {
            return await promisedXMLHttpRequest({
                method: 'POST',
                url: "/apis/song/texttelegramtabsheader",
                params: { id: state.currentSongId }
            });
        },
        async getIndexTabsVariant(state) {
            return await promisedXMLHttpRequest({
                method: 'POST',
                url: "/apis/song/indextabsvariant",
                params: { id: state.currentSongId }
            });
        },
        getSourceText: (state) => async (voiceId) => {
            return await promisedXMLHttpRequest({
                method: 'POST',
                url: "/apis/song/voicesourcetext",
                params: { id: state.currentSongId, voiceId: voiceId }
            });
        },
        getSourceSyllables: (state) => async (voiceId) => {
            return JSON.parse(await promisedXMLHttpRequest({
                method: 'POST',
                url: "/apis/song/voicesourcesyllables",
                params: { id: state.currentSongId, voiceId: voiceId }
            }));
        },
        getSourceMarkers: (state) => async (voiceId) => {
            let markers = JSON.parse(await promisedXMLHttpRequest({
                method: 'POST',
                url: "/apis/song/voicesourcemarkers",
                params: { id: state.currentSongId, voiceId: voiceId }
            }));
            markers.sort(function (a,b) {
                if (a.time > b.time) return 1;
                if (a.time < b.time) return -1;
                return 0;
            });
            return markers;
        },
        getPropValue: () => async (key) => {
            let prop = JSON.parse(await promisedXMLHttpRequest({
                method: 'POST',
                url: "/apis/properties/getproperty",
                params: { key: key }
            }));
            // console.log(`key ${key} prop.property`, prop.property);
            return prop.property.value;
        },
        async getTextFormatted(state) {
            return await promisedXMLHttpRequest({
                method: 'POST',
                url: "/apis/song/textformatted",
                params: { id: state.currentSongId }
            });
        },
        // TODO перенести в actions
        playKaraoke: (state) => async (id = state.currentSongId) => {
            return await promisedXMLHttpRequest({
                method: 'POST',
                url: "/apis/song/playkaraoke",
                params: { id: id }
            });
        },
        // TODO перенести в actions
        playLyrics: (state) => async (id = state.currentSongId) => {
            return await promisedXMLHttpRequest({
                method: 'POST',
                url: "/apis/song/playlyrics",
                params: { id: id }
            });
        },
        // TODO перенести в actions
        playChords: (state) => async (id = state.currentSongId) => {
            return await promisedXMLHttpRequest({
                method: 'POST',
                url: "/apis/song/playchords",
                params: { id: id }
            });
        },
        playTabs: (state) => async (id = state.currentSongId) => {
            return await promisedXMLHttpRequest({
                method: 'POST',
                url: "/apis/song/playtabs",
                params: { id: id }
            });
        },

        getPublications(state) {
            return state.publications;
        },

        getUnpublications(state) {
            return state.unpublications;
        },

        getSkipedPublications(state) {
            return state.skipedpublications;
        },

        getSongsDigest(state) {
            return state.songsDigest
        },
        getTotalDuration(state) {
            return state.totalDuration
        },
        getSongsDigestIds(state) {
            return state.songsDigest ? state.songsDigest.flatMap(song => song.id) : []
        },
        getSongsDigestIsLoading(state) { return state.songsDigestIsLoading },
        songAuthorsPromise() {
            let request = { method: 'POST', url: "/apis/songs/authors"};
            return promisedXMLHttpRequest(request);
        },
    },
    mutations: {
        setLastSettingType(state, value) {
            if (state.lastSettingType !== undefined && state.lastSettingType !== null && value !== undefined && value !== null) {
                const key = 'lastSettingType';
                promisedXMLHttpRequest({
                    method: 'POST',
                    url: "/apis/setwebvueprop",
                    params: {key: key, value: value}
                });
            }
            state.lastSettingType = value;
        },
        setLastSettingValue(state, value) {
            if (state.lastSettingValue !== undefined && state.lastSettingValue !== null && value !== undefined && value !== null) {
                const key = 'lastSettingValue';
                promisedXMLHttpRequest({
                    method: 'POST',
                    url: "/apis/setwebvueprop",
                    params: {key: key, value: value}
                });
            }
            state.lastSettingValue = value;
        },
        setLastPriorLyrics(state, value) {
            if (state.lastPriorLyrics !== undefined && state.lastPriorLyrics !== null && value !== undefined && value !== null) {
                const key = 'lastPriorLyrics';
                promisedXMLHttpRequest({
                    method: 'POST',
                    url: "/apis/setwebvueprop",
                    params: {key: key, value: value}
                });
            }
            state.lastPriorLyrics = value;
        },
        setLastPriorKaraoke(state, value) {
            if (state.lastPriorKaraoke !== undefined && state.lastPriorKaraoke !== null && value !== undefined && value !== null) {
                const key = 'lastPriorKaraoke';
                promisedXMLHttpRequest({
                    method: 'POST',
                    url: "/apis/setwebvueprop",
                    params: {key: key, value: value}
                });
            }
            state.lastPriorKaraoke = value;
        },
        setLastPriorChords(state, value) {
            if (state.lastPriorChords !== undefined && state.lastPriorChords !== null && value !== undefined && value !== null) {
                const key = 'lastPriorChords';
                promisedXMLHttpRequest({
                    method: 'POST',
                    url: "/apis/setwebvueprop",
                    params: {key: key, value: value}
                });
            }
            state.lastPriorChords = value;
        },
        setLastPriorMelody(state, value) {
            if (state.lastPriorMelody !== undefined && state.lastPriorMelody !== null && value !== undefined && value !== null) {
                const key = 'lastPriorMelody';
                promisedXMLHttpRequest({
                    method: 'POST',
                    url: "/apis/setwebvueprop",
                    params: {key: key, value: value}
                });
            }
            state.lastPriorMelody = value;
        },
        setLastPriorCodeLyrics(state, value) {
            if (state.lastPriorCodeLyrics !== undefined && state.lastPriorCodeLyrics !== null && value !== undefined && value !== null) {
                const key = 'lastPriorCodeLyrics';
                promisedXMLHttpRequest({
                    method: 'POST',
                    url: "/apis/setwebvueprop",
                    params: {key: key, value: value}
                });
            }
            state.lastPriorCodeLyrics = value;
        },
        setLastPriorCodeKaraoke(state, value) {
            if (state.lastPriorCodeKaraoke !== undefined && state.lastPriorCodeKaraoke !== null && value !== undefined && value !== null) {
                const key = 'lastPriorCodeKaraoke';
                promisedXMLHttpRequest({
                    method: 'POST',
                    url: "/apis/setwebvueprop",
                    params: {key: key, value: value}
                });
            }
            state.lastPriorCodeKaraoke = value;
        },
        setLastPriorDemucs(state, value) {
            if (state.lastPriorDemucs !== undefined && state.lastPriorDemucs !== null && value !== undefined && value !== null) {
                const key = 'lastPriorDemucs';
                promisedXMLHttpRequest({
                    method: 'POST',
                    url: "/apis/setwebvueprop",
                    params: {key: key, value: value}
                });
            }
            state.lastPriorDemucs = value;
        },
        setLastPriorSymlinks(state, value) {
            if (state.lastPriorSymlinks !== undefined && state.lastPriorSymlinks !== null && value !== undefined && value !== null) {
                const key = 'lastPriorSymlinks';
                promisedXMLHttpRequest({
                    method: 'POST',
                    url: "/apis/setwebvueprop",
                    params: {key: key, value: value}
                });
            }
            state.lastPriorSymlinks = value;
        },
        setLastPriorSmartCopy(state, value) {
            if (state.lastPriorSmartCopy !== undefined && state.lastPriorSmartCopy !== null && value !== undefined && value !== null) {
                const key = 'lastPriorSmartCopy';
                promisedXMLHttpRequest({
                    method: 'POST',
                    url: "/apis/setwebvueprop",
                    params: {key: key, value: value}
                });
            }
            state.lastPriorSmartCopy = value;
        },
        saveSong(state) {
            state.snapshotSong = !state.currentSong ? undefined : Object.assign({}, state.currentSong)
        },
        async updateSongsAndDictionaries(state, result) {
            state.currentSongPageIndex = 0;
            state.songPages = result.pages;
            state.songAuthors = result.authors;
            state.songAlbums = result.albums;
            state.currentSong = state.songPages && state.songPages.length && state.songPages[0].length ? state.songPages[0][0] : undefined;
            state.currentSongIndex = state.songPages && state.songPages.length && state.songPages[0].length ? 0 : undefined;
            state.currentSongId = !state.currentSong ? 0 : state.currentSong.id;
            state.snapshotSong = !state.currentSong ? undefined : Object.assign({}, state.currentSong)
            state.toSync = await this.dispatch('getToSyncFromRest');
        },
        async updateSong(state, songFromRest) {
            if (songFromRest) {
                const id = songFromRest.id;

                let songWithIndexesFiltered = state.songPages.map(function (page, pageIndex) {
                    return page.map(function (song, songIndex) {
                        return { song: song, songIndex: songIndex, songId: song.id, pageIndex: pageIndex }
                    });
                }).flatMap(item => item).filter(item => item.songId === id);
                let songWithIndexes = songWithIndexesFiltered.length ? songWithIndexesFiltered[0] : undefined;
                if (songWithIndexes) {
                    // console.log('Обновляем песню ID =', id);
                    if (id === state.currentSongId) {
                        state.snapshotSong = Object.assign({}, songFromRest)
                    } else {
                        state.songPages[songWithIndexes.pageIndex].splice(songWithIndexes.songIndex, 1, songFromRest);
                    }
                } else {
                    state.currentSong = Object.assign({}, songFromRest);
                    state.currentSongId = songFromRest.id;
                    state.snapshotSong = Object.assign({}, state.currentSong)
                    state.toSync = await this.dispatch('getToSyncFromRest');
                }
            }
        },
        updateSongsByIds(state, songsAndIndexesForUpdate) {
            // console.log('songsAndIndexesForUpdate', songsAndIndexesForUpdate)

            for (let songsAndIndexesFromRest of songsAndIndexesForUpdate) {
                if (songsAndIndexesFromRest.pageIndex !== undefined && songsAndIndexesFromRest.songIndex !== undefined ) {
                    state.songPages[songsAndIndexesFromRest.pageIndex].splice(songsAndIndexesFromRest.songIndex, 1, songsAndIndexesFromRest.song);
                    if (songsAndIndexesFromRest.songId === state.currentSongId) {
                        state.currentSong = Object.assign({}, songsAndIndexesFromRest.song)
                        state.snapshotSong = Object.assign({}, songsAndIndexesFromRest.song)
                    }
                }

                // for (let i = 0; i < this.$store.getters.getSongsDigest.length; i++) {
                //     let songInDigest = state.songsDigest[i];
                //     if (songInDigest.id === songsAndIndexesFromRest.song.id) {
                //         state.songsDigest.splice(i,1,songsAndIndexesFromRest.song);
                //     }
                // }

                // Надо найти текущую songsAndIndexesFromRest в publications и unpublications и обновить там её, если есть
                let song = songsAndIndexesFromRest.song;
                let publish = undefined;
                let publishFldInd = undefined;
                let unpublishInd = undefined;
                let publishInd = undefined;

                for (let i = 0; i < state.publications.length; i++) {
                    let publication = state.publications[i];
                    if (publication.publish10 && publication.publish10.id === songsAndIndexesFromRest.songId) {
                        publish = publication;
                        publishInd = i;
                        publishFldInd = 10;
                        break;
                    }
                    if (publication.publish11 && publication.publish11.id === songsAndIndexesFromRest.songId) {
                        publish = publication;
                        publishInd = i;
                        publishFldInd = 11;
                        break;
                    }
                    if (publication.publish12 && publication.publish12.id === songsAndIndexesFromRest.songId) {
                        publish = publication;
                        publishInd = i;
                        publishFldInd = 12;
                        break;
                    }
                    if (publication.publish13 && publication.publish13.id === songsAndIndexesFromRest.songId) {
                        publish = publication;
                        publishInd = i;
                        publishFldInd = 13;
                        break;
                    }
                    if (publication.publish14 && publication.publish14.id === songsAndIndexesFromRest.songId) {
                        publish = publication;
                        publishInd = i;
                        publishFldInd = 14;
                        break;
                    }
                    if (publication.publish15 && publication.publish15.id === songsAndIndexesFromRest.songId) {
                        publish = publication;
                        publishInd = i;
                        publishFldInd = 15;
                        break;
                    }
                    if (publication.publish16 && publication.publish16.id === songsAndIndexesFromRest.songId) {
                        publish = publication;
                        publishInd = i;
                        publishFldInd = 16;
                        break;
                    }
                    if (publication.publish17 && publication.publish17.id === songsAndIndexesFromRest.songId) {
                        publish = publication;
                        publishInd = i;
                        publishFldInd = 17;
                        break;
                    }
                    if (publication.publish18 && publication.publish18.id === songsAndIndexesFromRest.songId) {
                        publish = publication;
                        publishInd = i;
                        publishFldInd = 18;
                        break;
                    }
                    if (publication.publish19 && publication.publish19.id === songsAndIndexesFromRest.songId) {
                        publish = publication;
                        publishInd = i;
                        publishFldInd = 19;
                        break;
                    }
                    if (publication.publish20 && publication.publish20.id === songsAndIndexesFromRest.songId) {
                        publish = publication;
                        publishInd = i;
                        publishFldInd = 20;
                        break;
                    }
                    if (publication.publish21 && publication.publish21.id === songsAndIndexesFromRest.songId) {
                        publish = publication;
                        publishInd = i;
                        publishFldInd = 21;
                        break;
                    }
                    if (publication.publish22 && publication.publish22.id === songsAndIndexesFromRest.songId) {
                        publish = publication;
                        publishInd = i;
                        publishFldInd = 22;
                        break;
                    }
                    if (publication.publish23 && publication.publish23.id === songsAndIndexesFromRest.songId) {
                        publish = publication;
                        publishInd = i;
                        publishFldInd = 23;
                        break;
                    }

                }

                if (!publish) {
                    unp: for (let i = 0; i < state.unpublications.length; i++) {
                        let unpublication = state.unpublications[i];
                        for (let j = 0; j < unpublication.length; j++) {
                            let publication = unpublication[j];
                            if (publication.publish10 && publication.publish10.id === songsAndIndexesFromRest.songId) {
                                publish = publication;
                                publishInd = j;
                                unpublishInd = i;
                                break unp;
                            }
                        }
                    }
                }

                // console.log('unpublishInd', unpublishInd);
                // console.log('publishInd', publishInd);
                // console.log('publishFldInd', publishFldInd);

                if (publishInd !== undefined && unpublishInd === undefined) {
                    let p = Object.assign({},state.publications[publishInd]);
                    switch (publishFldInd) {
                        case 10: {p.publish10 = song; break }
                        case 11: {p.publish11 = song; break }
                        case 12: {p.publish12 = song; break }
                        case 13: {p.publish13 = song; break }
                        case 14: {p.publish14 = song; break }
                        case 15: {p.publish15 = song; break }
                        case 16: {p.publish16 = song; break }
                        case 17: {p.publish17 = song; break }
                        case 18: {p.publish18 = song; break }
                        case 19: {p.publish19 = song; break }
                        case 20: {p.publish20 = song; break }
                        case 21: {p.publish21 = song; break }
                        case 22: {p.publish22 = song; break }
                        case 23: {p.publish23 = song; break }
                    }
                    state.publications.splice(publishInd, 1, p)
                }
                if (unpublishInd !== undefined) {
                    let u = state.unpublications[unpublishInd];
                    let p = Object.assign({},u[publishInd]);
                    // console.log('u: ', u);
                    // console.log('p: ', p);
                    p.publish10 = song;
                    u.splice(publishInd, 1, p);
                    state.unpublications.splice(unpublishInd, 1, u)
                }

            }
        },
        setLastUpdateSong(state, lastUpdateSong) {
          state.lastUpdateSong = lastUpdateSong;
        },
        async setCurrentSongId(state, currId) {
            // console.log('state.songsDigest', state.songsDigest);
            let songWithIndexesFiltered = state.songPages.map(function (page, pageIndex) {
                return page.map(function (song, songIndex) {
                    console.log('song', song);
                    return { song: song, songIndex: songIndex, songId: song.id, pageIndex: pageIndex }
                });
            }).flatMap(item => item).filter(item => item.songId === currId);

            let songWithIndexes = songWithIndexesFiltered.length ? songWithIndexesFiltered[0] : undefined;
            if (songWithIndexes) {
                state.currentSong = songWithIndexes.song;
                state.currentSongIndex = songWithIndexes.songIndex;
                state.currentSongPageIndex = songWithIndexes.pageIndex;
                state.currentSongId = songWithIndexes.song.id;
                state.previousSongId = songWithIndexes.song.idPrevious;
                state.nextSongId = songWithIndexes.song.idNext;
                state.rightSongId = songWithIndexes.song.idRight;
                state.leftSongId = songWithIndexes.song.idLeft;
                state.snapshotSong = Object.assign({}, state.currentSong)
                state.toSync = await this.dispatch('getToSyncFromRest');
            } else {
                let request = { method: 'POST', url: "/apis/song", params: {id: currId} };
                 promisedXMLHttpRequest(request).then(async data => {
                     let songFromRest = JSON.parse(data);
                     // console.log('Song: ', songFromRest);
                     if (songFromRest) {
                         const id = songFromRest.id;

                         let songWithIndexesFiltered = state.songPages.map(function (page, pageIndex) {
                             return page.map(function (song, songIndex) {
                                 return {song: song, songIndex: songIndex, songId: song.id, pageIndex: pageIndex}
                             });
                         }).flatMap(item => item).filter(item => item.songId === id);
                         let songWithIndexes = songWithIndexesFiltered.length ? songWithIndexesFiltered[0] : undefined;
                         if (songWithIndexes) {
                             // console.log('Обновляем песню ID =', id);
                             if (id === state.currentSongId) {
                                 state.snapshotSong = Object.assign({}, songFromRest)
                             } else {
                                 state.songPages[songWithIndexes.pageIndex].splice(songWithIndexes.songIndex, 1, songFromRest);
                             }
                         } else {
                             state.currentSong = Object.assign({}, songFromRest);
                             state.currentSongId = songFromRest.id;
                             state.previousSongId = songFromRest.idPrevious;
                             state.nextSongId = songFromRest.idNext;
                             state.rightSongId = songFromRest.idRight;
                             state.leftSongId = songFromRest.idLeft;
                             state.snapshotSong = Object.assign({}, state.currentSong)
                             state.toSync = await this.dispatch('getToSyncFromRest');
                         }
                     }
                 });
            }
        },
        async deleteCurrentSong(state) {
            let previousSongId = state.previousSongId;
            let nextSongId = state.nextSongId;
            let params = { id: state.currentSongId };
            let request = { method: 'POST', url: "/apis/song/delete", params: params };
            state.songPages[state.currentSongPageIndex].splice(state.currentSongIndex, 1);
            await promisedXMLHttpRequest(request);

            if (previousSongId) {
                await state.commit('setCurrentSongId', previousSongId)
            } else {
                await state.commit('setCurrentSongId', nextSongId)
            }
            if (state.currentSongId) {
                let params = { id: state.currentSongId };
                let request = { method: 'POST', url: "/apis/song/setpublishdatetimetoauthor", params: params };
                promisedXMLHttpRequest(request).then(r => console.log('setDateTimeAuthorPromise result: ', r));
            }

        },
        setPreviousSongId(state, previousSongId) {
            state.previousSongId = previousSongId
        },
        setNextSongId(state, nextSongId) {
            state.nextSongId = nextSongId
        },
        async setCurrentSongPageIndex(state, pageNum) {
            if (state.songPages.length > pageNum) {
                let currentPage = state.songPages[pageNum]
                if (currentPage.length > 0) {
                    let currentSong = currentPage[0];
                    state.currentSong = currentSong;
                    state.currentSongId = currentSong.id;
                    state.currentSongIndex = 0;
                    state.currentSongPageIndex = pageNum;
                    state.snapshotSong = Object.assign({}, state.currentSong)
                    state.toSync = await this.dispatch('getToSyncFromRest');
                }
            }
        },
        setSongPageSize(state, newPageSize) {
            const oldPageSize = state.songPageSize;
            if (oldPageSize !== newPageSize) {
                const diffPageSize = newPageSize - oldPageSize;
                pages: for (let i = 0; i < state.songPages.length-1; i++ ) {
                    let currentPage = state.songPages[i];
                    let nextPage = state.songPages[i+1]
                    for ( let j = 0; j < Math.abs(diffPageSize) * (i + 1); j++ ) {
                        if (nextPage.length > 0) {
                            if (diffPageSize > 0) {
                                currentPage.push(nextPage.shift());
                                if (nextPage.length === 0) {
                                    state.songPages.splice(i+1,1);
                                    i--;
                                    continue pages;
                                }
                            } else {
                                nextPage.unshift(currentPage.pop());
                            }
                        }
                    }
                }
                while (state.songPages[state.songPages.length-1].length === 0) {
                    state.songPages.pop();
                }
                while (state.songPages[state.songPages.length-1].length > newPageSize) {
                    state.songPages.push([]);
                    while (state.songPages[state.songPages.length-2].length > newPageSize) {
                        state.songPages[state.songPages.length-1].unshift(state.songPages[state.songPages.length-2].pop())
                    }
                }

                state.songPageSize = newPageSize;
            }
        },
        setCurrentSongField(state, payload) {
            state.currentSong[payload.name] = payload.value;
        },

        updatePublications(state, result) {
            state.publications = result.publications;
        },

        updateUnpublications(state, result) {
            state.unpublications = result.publications;
        },

        updateSkipedPublications(state, result) {
            state.skipedpublications = result.publications;
        },

        updateSongsDigests(state, result) {
            state.songsDigest = result.songsDigests;
            state.totalDuration = result.totalDuration;
        },
        setSongsDigestIsLoading(state, isLoading) { state.songsDigestIsLoading = isLoading },
        updateSongsDigestByIds(state, songsAndIndexesForUpdate) {
            // console.log('songsAndIndexesForUpdate', songsAndIndexesForUpdate)

            for (let songsAndIndexesFromRest of songsAndIndexesForUpdate) {

                for (let i = 0; i < state.songsDigest.length; i++) {
                    let songInDigest = state.songsDigest[i];
                    if (songInDigest.id === songsAndIndexesFromRest.song.id) {
                        state.songsDigest.splice(i,1,songsAndIndexesFromRest.song);
                    }
                }
            }
        },
        updateSongByUserEvent(state, userEventData) {
            let songId = userEventData.recordId;
            for (let i = 0; i < state.songsDigest.length; i++) {
                let songInDigest = state.songsDigest[i];
                if (songInDigest.id === songId) {
                    state.songsDigest.splice(i,1,userEventData.record);
                }
            }
        },
        addSongByUserEvent(state, userEventData) {
            console.log('Событие добавления песни: ', userEventData)
        },
        deleteSongByUserEvent(state, userEventData) {
            console.log('Событие удаления песни: ', userEventData)
        },
        changeToSync(state) {
            let params = {dictName: 'Sync Ids', dictValue: state.currentSongId}
            if (state.toSync) {
                params.dictAction = "remove"
            } else {
                params.dictAction = "add"
            }
            promisedXMLHttpRequest({
                method: 'POST',
                url: "/apis/utils/tfd",
                params: params
            }).then(_ => {
                state.toSync = !state.toSync;
            });
        }
    },
    actions: {
        changeToSync(ctx) {
            ctx.commit('changeToSync');
        },
        setLastSettingType(ctx, payload) {
            ctx.commit('setLastSettingType', payload.value);
        },
        setLastSettingValue(ctx, payload) {
            ctx.commit('setLastSettingValue', payload.value);
        },
        setLastPriorLyrics(ctx, payload) {
            ctx.commit('setLastPriorLyrics', payload.value);
        },
        setLastPriorKaraoke(ctx, payload) {
            ctx.commit('setLastPriorKaraoke', payload.value);
        },
        setLastPriorChords(ctx, payload) {
            ctx.commit('setLastPriorChords', payload.value);
        },
        setLastPriorMelody(ctx, payload) {
            ctx.commit('setLastPriorMelody', payload.value);
        },
        setLastPriorCodeLyrics(ctx, payload) {
            ctx.commit('setLastPriorCodeLyrics', payload.value);
        },
        setLastPriorCodeKaraoke(ctx, payload) {
            ctx.commit('setLastPriorCodeKaraoke', payload.value);
        },
        setLastPriorDemucs(ctx, payload) {
            ctx.commit('setLastPriorDemucs', payload.value);
        },
        setLastPriorSymlinks(ctx, payload) {
            ctx.commit('setLastPriorSymlinks', payload.value);
        },
        setLastPriorSmartCopy(ctx, payload) {
            ctx.commit('setLastPriorSmartCopy', payload.value);
        },
        setPreviousAndNextSongId(ctx) {
            let previousSongId = undefined;
            let nextSongId = undefined;
            let currentSongIndex = ctx.state.currentSongIndex;
            let currentPageIndex = ctx.state.currentPageIndex;
            let currentPageLength = ctx.state.pages[currentPageIndex].length;
            if (currentSongIndex > 0) {
                previousSongId = ctx.state.pages[currentPageIndex][currentSongIndex-1].id;
            } else {
                if (currentPageIndex > 0) {
                    previousSongId = ctx.state.pages[currentPageIndex-1][ctx.state.pages[currentPageIndex-1].length-1].id;
                }
            }
            if (currentSongIndex < currentPageLength-1) {
                nextSongId = ctx.state.pages[currentPageIndex][currentSongIndex+1].id;
            } else {
                if (currentPageIndex < ctx.state.pages.length-1) {
                    nextSongId = ctx.state.pages[currentPageIndex+1][ctx.state.pages[currentPageIndex+1][0]].id;
                }
            }
            console.log('previousSongId', previousSongId);
            console.log('nextSongId', nextSongId);
            this.$store.commit('setPreviousSongId', previousSongId);
            this.$store.commit('setNextSongId', nextSongId);
        },

        // async deleteSong(ctx) {
        //     let params = { id: ctx.state.currentSongId };
        //     let request = { method: 'POST', url: "/apis/song/delete", params: params };
        //     ctx.songPages[ctx.currentSongPageIndex].splice(ctx.currentSongIndex, 1);
        //     return await promisedXMLHttpRequest(request);
        // },

        updateSongsByIds(ctx, payload) {
            ctx.commit('updateSongsByIds', payload.songsAndIndexesForUpdate);
        },
        setLastUpdateSong(ctx, payload) {
            ctx.commit('setLastUpdateSong', payload.lastUpdateSong);
        },
        setCurrentSongField(ctx, payload) {
            ctx.commit('setCurrentSongField', payload)
        },
        saveSong(ctx, params) {
            params.id = ctx.state.currentSongId;
            // console.log('params: ', params);
            let request = { method: 'POST', url: "/apis/song/update", params: params };
            promisedXMLHttpRequest(request).then(() => {
                ctx.commit('saveSong')
            }).catch(error => {
                console.log(error);
            });
        },
        async loadSongsAndDictionaries(ctx, params) {
            // console.log('params: ', params);
            params.pageSize = ctx.state.songPageSize;
            let request = { method: 'POST', url: "/apis/songs", params: params };
            promisedXMLHttpRequest(request).then(data => {
                let result = JSON.parse(data);
                // console.log('SongsAndDictionaries: ', result);
                ctx.commit('updateSongsAndDictionaries', result)
            }).catch(error => {
                console.log(error);
            });
        },
        loadSong(ctx, params) {
            let request = { method: 'POST', url: "/apis/song", params: params };
            promisedXMLHttpRequest(request).then(data => {
                let result = JSON.parse(data);
                ctx.commit('updateSong', result)
            }).catch(error => {
                console.log(error);
            });
        },
        async getSheetsageinfoChords(ctx) {
            return await promisedXMLHttpRequest({
                method: 'POST',
                url: "/apis/song/sheetsageinfochords",
                params: { id: ctx.state.currentSongId }
            });
        },
        async getSheetsageinfoBeattimes(ctx) {
            return await promisedXMLHttpRequest({
                method: 'POST',
                url: "/apis/song/sheetsageinfobeattimes",
                params: { id: ctx.state.currentSongId }
            });
        },
        async getDiffBeatsInc(ctx) {
            return await promisedXMLHttpRequest({
                method: 'POST',
                url: "/apis/song/diffbeatsinc",
                params: { id: ctx.state.currentSongId }
            });
        },
        async getDiffBeatsDec(ctx) {
            return await promisedXMLHttpRequest({
                method: 'POST',
                url: "/apis/song/diffbeatsdec",
                params: { id: ctx.state.currentSongId }
            });
        },
        getTextFormattedPromise: (ctx) => {
            return promisedXMLHttpRequest({
                method: 'POST',
                url: "/apis/song/textformatted",
                params: { id: ctx.state.currentSongId }
            });
        },
        getNotesFormattedPromise: (ctx) => {
            return promisedXMLHttpRequest({
                method: 'POST',
                url: "/apis/song/notesformatted",
                params: { id: ctx.state.currentSongId }
            });
        },
        getChordsFormattedPromise: (ctx) => {
            return promisedXMLHttpRequest({
                method: 'POST',
                url: "/apis/song/chordsformatted",
                params: { id: ctx.state.currentSongId }
            });
        },
        getAlbumPictureBase64Promise(ctx) {
            return promisedXMLHttpRequest({
                method: 'POST',
                url: "/apis/song/picturealbum",
                params: { id: ctx.state.currentSongId }
            });
        },
        getAuthorPictureBase64Promise(ctx) {
            return promisedXMLHttpRequest({
                method: 'POST',
                url: "/apis/song/pictureauthor",
                params: { id: ctx.state.currentSongId }
            });
        },
        updateOneRemoteSettingsPromise(ctx, id) {
            return promisedXMLHttpRequest({
                method: 'POST',
                url: "/apis/utils/updateremotesettingsfromlocaldatabase",
                params: { id: id }
            });
        },
        toSyncOneRemoteSettingsPromise(ctx, id) {
            return promisedXMLHttpRequest({
                method: 'POST',
                url: "/apis/utils/tosync",
                params: { id: id }
            });
        },
        updateRemoteSettingsPromise() {
            return promisedXMLHttpRequest({
                method: 'POST',
                url: "/apis/utils/updateremotedatabasefromlocaldatabase",
                params: { updateSettings: true, updatePictures: false }
            });
        },
        updateRemotePicturesPromise() {
            return promisedXMLHttpRequest({
                method: 'POST',
                url: "/apis/utils/updateremotedatabasefromlocaldatabase",
                params: { updateSettings: false, updatePictures: true }
            });
        },
        updateLocalSettingsPromise() {
            return promisedXMLHttpRequest({
                method: 'POST',
                url: "/apis/utils/updatelocaldatabasefromremotedatabase",
                params: { updateSettings: true, updatePictures: false }
            });
        },
        updateLocalPicturesPromise() {
            return promisedXMLHttpRequest({
                method: 'POST',
                url: "/apis/utils/updatelocaldatabasefromremotedatabase",
                params: { updateSettings: false, updatePictures: true }
            });
        },

        setDateTimeAuthorPromise(ctx) {
            let params = { id: ctx.state.currentSongId };
            let request = { method: 'POST', url: "/apis/song/setpublishdatetimetoauthor", params: params };
            return promisedXMLHttpRequest(request);
        },
        createSymlinksPromise(ctx, payload) {
            let params = { id: ctx.state.currentSongId, prior: payload.prior};
            let request = { method: 'POST', url: "/apis/song/symlink", params: params };
            return promisedXMLHttpRequest(request);
        },
        createDemucs2Promise(ctx, payload) {
            let params = { id: ctx.state.currentSongId, prior: payload.prior };
            let request = { method: 'POST', url: "/apis/song/demucs2", params: params };
            return promisedXMLHttpRequest(request);
        },
        createDemucs5Promise(ctx, payload) {
            let params = { id: ctx.state.currentSongId, prior: payload.prior };
            let request = { method: 'POST', url: "/apis/song/demucs5", params: params };
            return promisedXMLHttpRequest(request);
        },
        createSheetsagePromise(ctx, payload) {
            let params = { id: ctx.state.currentSongId, prior: payload.prior };
            let request = { method: 'POST', url: "/apis/song/sheetsage", params: params };
            return promisedXMLHttpRequest(request);
        },
        createPictureBoostyTeaserPromise(ctx) {
            let params = { id: ctx.state.currentSongId };
            let request = { method: 'POST', url: "/apis/song/createpictureboostyteaser", params: params };
            return promisedXMLHttpRequest(request);
        },
        createPictureSponsrTeaserPromise(ctx) {
            let params = { id: ctx.state.currentSongId };
            let request = { method: 'POST', url: "/apis/song/createpicturesponsrteaser", params: params };
            return promisedXMLHttpRequest(request);
        },
        createPictureBoostyFilesPromise(ctx) {
            let params = { id: ctx.state.currentSongId };
            let request = { method: 'POST', url: "/apis/song/createpictureboostyfiles", params: params };
            return promisedXMLHttpRequest(request);
        },
        createPictureVKPromise(ctx) {
            let params = { id: ctx.state.currentSongId };
            let request = { method: 'POST', url: "/apis/song/createpicturevk", params: params };
            return promisedXMLHttpRequest(request);
        },
        createPictureVKlinkPromise(ctx) {
            let params = { id: ctx.state.currentSongId };
            let request = { method: 'POST', url: "/apis/song/createpicturevklink", params: params };
            return promisedXMLHttpRequest(request);
        },
        createPictureKaraokePromise(ctx) {
            let params = { id: ctx.state.currentSongId };
            let request = { method: 'POST', url: "/apis/song/createpicturekaraoke", params: params };
            return promisedXMLHttpRequest(request);
        },
        createPictureLyricsPromise(ctx) {
            let params = { id: ctx.state.currentSongId };
            let request = { method: 'POST', url: "/apis/song/createpicturelyrics", params: params };
            return promisedXMLHttpRequest(request);
        },
        createPictureChordsPromise(ctx) {
            let params = { id: ctx.state.currentSongId };
            let request = { method: 'POST', url: "/apis/song/createpicturechords", params: params };
            return promisedXMLHttpRequest(request);
        },
        createPictureTabsPromise(ctx) {
            let params = { id: ctx.state.currentSongId };
            let request = { method: 'POST', url: "/apis/song/createpicturetabs", params: params };
            return promisedXMLHttpRequest(request);
        },
        createDescriptionFileKaraokePromise(ctx) {
            let params = { id: ctx.state.currentSongId };
            let request = { method: 'POST', url: "/apis/song/createdescriptionfilekaraoke", params: params };
            return promisedXMLHttpRequest(request);
        },
        createDescriptionFileLyricsPromise(ctx) {
            let params = { id: ctx.state.currentSongId };
            let request = { method: 'POST', url: "/apis/song/createdescriptionfilelyrics", params: params };
            return promisedXMLHttpRequest(request);
        },
        createDescriptionFileChordsPromise(ctx) {
            let params = { id: ctx.state.currentSongId };
            let request = { method: 'POST', url: "/apis/song/createdescriptionfilechords", params: params };
            return promisedXMLHttpRequest(request);
        },
        createDescriptionFileTabsPromise(ctx) {
            let params = { id: ctx.state.currentSongId };
            let request = { method: 'POST', url: "/apis/song/createdescriptionfiletabs", params: params };
            return promisedXMLHttpRequest(request);
        },
        createMP3KaraokePromise(ctx, payload) {
            let params = { id: ctx.state.currentSongId, prior: payload.prior };
            let request = { method: 'POST', url: "/apis/song/mp3karaoke", params: params };
            return promisedXMLHttpRequest(request);
        },
        createMP3LyricsPromise(ctx, payload) {
            let params = { id: ctx.state.currentSongId, prior: payload.prior };
            let request = { method: 'POST', url: "/apis/song/mp3lyrics", params: params };
            return promisedXMLHttpRequest(request);
        },
        createKaraokePromise(ctx, payload) {
            let params = { id: ctx.state.currentSongId, priorLyrics: payload.priorLyrics, priorKaraoke: payload.priorKaraoke, priorChords: payload.priorChords, priorMelody: payload.priorMelody };
            let request = { method: 'POST', url: "/apis/song/createkaraoke", params: params };
            return promisedXMLHttpRequest(request);
        },
        copyFieldsFromAnotherPromise(ctx, payload) {
            let params = { id: ctx.state.currentSongId, idAnother: payload.idAnother, fields: payload.fields };
            let request = { method: 'POST', url: "/apis/song/copyfieldsfromanother", params: params };
            return promisedXMLHttpRequest(request);
        },
        createFromFolderPromise(ctx, payload) {
            let params = { folder: payload.folder };
            let request = { method: 'POST', url: "/apis/utils/createfromfolder", params: params };
            return promisedXMLHttpRequest(request);
        },
        createDzenPicturesForFolderPromise(ctx, payload) {
            let params = { folder: payload.folder };
            let request = { method: 'POST', url: "/apis/utils/createdzenpicturesforfolder", params: params };
            return promisedXMLHttpRequest(request);
        },
        collectStorePromise(ctx, payload) {
            let params = { songsIds: payload.songsIds ? payload.songsIds : '', priorLyrics: payload.priorLyrics, priorKaraoke: payload.priorKaraoke };
            let request = { method: 'POST', url: "/apis/utils/collectstore", params: params };
            return promisedXMLHttpRequest(request);
        },
        actualizeVKLinkPictureWebPromise() {
            let request = { method: 'POST', url: "/apis/utils/actualizevklinkpictureweb" };
            return promisedXMLHttpRequest(request);
        },
        checkLastAlbumYmPromise() {
            let request = { method: 'POST', url: "/apis/utils/checklastalbumym" };
            return promisedXMLHttpRequest(request);
        },
        updateBpmAndKeyPromise() {
            let request = { method: 'POST', url: "/apis/utils/updatebpmandkey" };
            return promisedXMLHttpRequest(request);
        },
        updateBpmAndKeyLVPromise() {
            let request = { method: 'POST', url: "/apis/utils/updatebpmandkeylv" };
            return promisedXMLHttpRequest(request);
        },
        markDublicatesPromise(ctx, payload) {
            let request = { method: 'POST', url: "/apis/utils/markdublicates", params: { author: payload.author } };
            return promisedXMLHttpRequest(request);
        },
        deleteDublicatesPromise() {
            let request = { method: 'POST', url: "/apis/utils/deldublicates" };
            return promisedXMLHttpRequest(request);
        },
        clearPreDublicatesPromise() {
            let request = { method: 'POST', url: "/apis/utils/clearpredublicates" };
            return promisedXMLHttpRequest(request);
        },
        customFunctionPromise() {
            let request = { method: 'POST', url: "/apis/utils/customfunction" };
            return promisedXMLHttpRequest(request);
        },
        async saveSourceTextAndMarkers(ctx, payload) {
            let params = {
                id: ctx.state.currentSongId,
                voice: payload.voice,
                sourceText: payload.sourceText,
                sourceMarkers: payload.sourceMarkers,
                indexTabsVariant: payload.indexTabsVariant
            };
            let request = { method: 'POST', url: "/apis/song/savesourcetextmarkers", params: params };
            return await promisedXMLHttpRequest(request);
        },

        async loadPublications(ctx, params) {
            let request = { method: 'POST', url: "/apis/publications", params: params };
            promisedXMLHttpRequest(request).then(data => {
                let result = JSON.parse(data);
                ctx.commit('updatePublications', result)
            }).catch(error => {
                console.log(error);
            });
        },

        async loadUnpublications(ctx) {
            let request = { method: 'POST', url: "/apis/unpublications" };
            promisedXMLHttpRequest(request).then(data => {
                let result = JSON.parse(data);
                ctx.commit('updateUnpublications', result)
            }).catch(error => {
                console.log(error);
            });
        },

        async loadSkipedPublications(ctx) {
            let request = { method: 'POST', url: "/apis/skipedpublications" };
            promisedXMLHttpRequest(request).then(data => {
                let result = JSON.parse(data);
                ctx.commit('updateSkipedPublications', result)
            }).catch(error => {
                console.log(error);
            });
        },

        updateSongsDigestByIds(ctx, payload) {
            ctx.commit('updateSongsDigestByIds', payload.songsAndIndexesForUpdate);
        },
        updateSongByUserEvent(ctx, userEventData) {
            ctx.commit('updateSongByUserEvent', userEventData);
        },
        addSongByUserEvent(ctx, userEventData) {
            ctx.commit('addSongByUserEvent', userEventData);
        },
        deleteSongByUserEvent(ctx, userEventData) {
            ctx.commit('addSongByUserEvent', userEventData);
        },
        loadSongsDigests(ctx, params) {
            let request = { method: 'POST', url: "/apis/songsdigests", params: params };
            ctx.commit('setSongsDigestIsLoading', true);
            promisedXMLHttpRequest(request).then(data => {
                let result = JSON.parse(data);
                ctx.commit('updateSongsDigests', result)
                ctx.commit('setSongsDigestIsLoading', false);
            }).catch(error => {
                console.log(error);
            });
        },
        loadSongsDigestsPromise(ctx, params) {
            let request = { method: 'POST', url: "/apis/songsdigests", params: params };
            return promisedXMLHttpRequest(request)
        },
        searchTextForAll(ctx) {
            let params = { songsIds: ctx.getters.getSongsDigestIds.join(';') };
            let request = { method: 'POST', url: "/apis/songs/searchsongtextall", params: params };
            return promisedXMLHttpRequest(request);
        },
        addSyncForAll(ctx) {
            let params = { songsIds: ctx.getters.getSongsDigestIds.join(';') };
            let request = { method: 'POST', url: "/apis/songs/addsyncforall", params: params };
            return promisedXMLHttpRequest(request);
        },
        createKaraokeForAllPromise(ctx, payload) {
            let params = { songsIds: ctx.getters.getSongsDigestIds.join(';'), priorLyrics: payload.priorLyrics, priorKaraoke: payload.priorKaraoke, priorChords: payload.priorChords, priorMelody: payload.priorMelody };
            let request = { method: 'POST', url: "/apis/songs/createkaraokeall", params: params };
            return promisedXMLHttpRequest(request);
        },
        createDemucs2ForAllPromise(ctx, payload) {
            let params = { songsIds: ctx.getters.getSongsDigestIds.join(';'), prior: payload.prior };
            let request = { method: 'POST', url: "/apis/songs/createdemucs2all", params: params };
            return promisedXMLHttpRequest(request);
        },
        createDemucs5ForAllPromise(ctx, payload) {
            let params = { songsIds: ctx.getters.getSongsDigestIds.join(';'), prior: payload.prior };
            let request = { method: 'POST', url: "/apis/songs/createdemucs5all", params: params };
            return promisedXMLHttpRequest(request);
        },
        createSheetsageForAllPromise(ctx, payload) {
            let params = { songsIds: ctx.getters.getSongsDigestIds.join(';'), prior: payload.prior };
            let request = { method: 'POST', url: "/apis/songs/sheetsageall", params: params };
            return promisedXMLHttpRequest(request);
        },
        createMP3KaraokeForAllPromise(ctx, payload) {
            let params = { songsIds: ctx.getters.getSongsDigestIds.join(';'), prior: payload.prior };
            let request = { method: 'POST', url: "/apis/songs/createmp3karaokeall", params: params };
            return promisedXMLHttpRequest(request);
        },
        createMP3LyricsForAllPromise(ctx, payload) {
            let params = { songsIds: ctx.getters.getSongsDigestIds.join(';'), prior: payload.prior };
            let request = { method: 'POST', url: "/apis/songs/createmp3lyricsall", params: params };
            return promisedXMLHttpRequest(request);
        },
        createSymlinksForAllPromise(ctx, payload) {
            let params = { songsIds: ctx.getters.getSongsDigestIds.join(';'), prior: payload.prior };
            let request = { method: 'POST', url: "/apis/songs/createsymlinksall", params: params };
            return promisedXMLHttpRequest(request);
        },
        createSmartCopyForAllPromise(ctx, payload) {
            let request = { method: 'POST', url: "/apis/songs/smartcopyall", params: payload };
            return promisedXMLHttpRequest(request);
        },
        async getToSyncFromRest(ctx) {
            const ids = await promisedXMLHttpRequest({
                method: 'POST',
                url: "/apis/getdict",
                params: { dict: 'Sync Ids' }
            });
            const result = ids.indexOf(String(ctx.state.currentSongId)) !== -1
            console.log('getToSyncFromRest ids', ids);
            console.log('getToSyncFromRest ctx.state.currentSongId', ctx.state.currentSongId);
            console.log('getToSyncFromRest result', result);
            return result;
        },
    }
}