import Vue from 'vue'
import App from './App.vue'
import store from './store'
import router from './router'
import 'bootstrap'
// import 'bootstrap/dist/css/bootstrap.min.css'
import 'bootstrap/dist/css/bootstrap.css'
import 'bootstrap-vue/dist/bootstrap-vue.css'
import { EventSourcePolyfill } from 'event-source-polyfill';
import { ToastPlugin } from 'bootstrap-vue'

Vue.use(ToastPlugin)
Vue.config.productionTip = false

new Vue({
  store,
  router,
  render: h => h(App),
  data: {
    stompClientSongs: null
  },
  methods: {

    loadSongs(params) {
      return this.$store.dispatch('loadSongsAndDictionaries', params)
    },
    loadProcesses(params) {
      return this.$store.dispatch('loadProcessesAndDictionaries', params)
    },
    loadPublications(params) {
      return this.$store.dispatch('loadPublications', params)
    },
    loadUnpublications() {
      return this.$store.dispatch('loadUnpublications')
    },
    async checkUpdateSongs() {
      // Получаем с бэка список айдишников песен, измененных с момента последней проверки
      let ids = JSON.parse(await this.$store.getters.getSongsIdsForUpdate);
      if (ids.length > 0) {
        // console.log('ids changed: ', ids);
        // Если список получен, то нужно проверить, есть ли эти песни в songPages и для тех что есть
        // сформировать структуру с индексами страниц и песен

        // Получаем структуры вида [{songIndex, songId, pageIndex}]
        let songsIds = this.$store.getters.getSongsIds;
        // let songsIds = this.$store.getters.getSongsIds.filter(item => ids.includes(item.songId))
        // Получаем песни для обновления
        let songsForUpdate = JSON.parse(await this.$store.getters.getSongsForUpdateByIds(ids));
        let songsAndIndexesForUpdate = [];
        for (let i = 0; i < songsForUpdate.length; i++) {
          let song = songsForUpdate[i];
          let indexes = songsIds.find(item => item.songId === song.id);
          if (indexes) {
            songsAndIndexesForUpdate.push({ song: song, songIndex: indexes.songIndex, songId: song.id, pageIndex: indexes.pageIndex });
          } else {
            songsAndIndexesForUpdate.push({ song: song, songId: song.id });
          }
        }
        await this.$store.dispatch('updateSongsByIds', {songsAndIndexesForUpdate: songsAndIndexesForUpdate});
        await this.$store.dispatch('updateSongsDigestByIds', {songsAndIndexesForUpdate: songsAndIndexesForUpdate});
        await this.$store.dispatch('updatePublishDigestByIds', {songsAndIndexesForUpdate: songsAndIndexesForUpdate});

      }
      await this.$store.dispatch('setLastUpdateSong', {lastUpdateSong: Date.now()});
    },

    async checkUpdateProcesses() {
      // Получаем с бэка список айдишников процессов, измененных с момента последней проверки
      let ids = JSON.parse(await this.$store.getters.getProcessesIdsForUpdate);
      if (ids.length > 0) {
        // Получаем структуры вида [{processIndex, processId, pageIndex}]
        let processesIds = this.$store.getters.getProcessesIds;
        // Получаем процессы для обновления
        let processesForUpdate = JSON.parse(await this.$store.getters.getProcessesForUpdateByIds(ids));
        let processesAndIndexesForUpdate = [];
        for (let i = 0; i < processesForUpdate.length; i++) {
          let process = processesForUpdate[i];
          let isProcessInPages = processesIds.filter(item => ids.includes(item.processId)).length > 0;
          if (isProcessInPages) {
            let indexes = processesIds.find(item => item.processId === process.id);
            processesAndIndexesForUpdate.push({ process: process, processIndex: indexes.processIndex, processId: process.id, pageIndex: indexes.pageIndex });
          } else {
            processesAndIndexesForUpdate.push({ process: process, processId: process.id });
          }
        }
        await this.$store.dispatch('updateProcessesByIds', {processesAndIndexesForUpdate: processesAndIndexesForUpdate});

      }
      await this.$store.dispatch('setLastUpdateProcess', {lastUpdateProcess: Date.now()});
    },
    updateSongByUserEvent(userEventData) {
      this.$store.dispatch('updateSongByUserEvent', userEventData);
      this.$store.dispatch('updatePublishDigestByUserEvent', userEventData);
    },
    updateProcessByUserEvent(userEventData) {
      this.$store.dispatch('updateProcessByUserEvent', userEventData);
    },
    updateProcessWorkerStateByUserEvent(userEventData) {
      this.$store.dispatch('updateProcessWorkerStateByUserEvent', userEventData);
    },
    deleteSongByUserEvent(userEventData) {
      this.$store.dispatch('deleteSongByUserEvent', userEventData);
      this.$store.dispatch('deletePublishDigestByUserEvent', userEventData);
    },
    deleteProcessByUserEvent(userEventData) {
      this.$store.dispatch('deleteProcessByUserEvent', userEventData);
    },
    addSongByUserEvent(userEventData) {
      this.$store.dispatch('addSongByUserEvent', userEventData);
      this.$store.dispatch('addPublishDigestByUserEvent', userEventData);
    },
    addProcessByUserEvent(userEventData) {
      console.log('methods addProcessByUserEvent from main.js');
      this.$store.dispatch('addProcessByUserEvent', userEventData);
    },
    logMessageByUserEvent(text) {
      this.$store.dispatch('setLogMessage', text);
    },
    showMessageByUserEvent(userEvent) {
      console.log("Message from server: ", userEvent);
      this.$bvToast.toast(userEvent.body, {
        title: userEvent.head,
        autoHideDelay: 10000,
        // noAutoHide: true,
        // variant: userEvent.type,
        toaster: 'b-toaster-top-left',
        bodyClass: 'toast-body-info',
        headerClass: 'toast-header-info',
        appendToast: false
      })
    },
    userEvent(userEvent) {
      switch (userEvent.type) {
        case 'RECORD_CHANGE': {
          switch (userEvent.data.tableName) {
            case 'tbl_settings': { this.updateSongByUserEvent(userEvent.data); break; }
            case 'tbl_processes': { this.updateProcessByUserEvent(userEvent.data); break; }
            default: { console.log('Обновление неизвестной таблицы: ', userEvent.data.tableName) }
          }
          break;
        }
        case 'RECORD_ADD': {
          switch (userEvent.data.tableName) {
            case 'tbl_settings': { this.addSongByUserEvent(userEvent.data); break; }
            case 'tbl_processes': { this.addProcessByUserEvent(userEvent.data); break; }
            default: { console.log('Добавление записи неизвестной таблицы: ', userEvent.data.tableName) }
          }
          break;
        }
        case 'RECORD_DELETE': {
          switch (userEvent.data.tableName) {
            case 'tbl_settings': { this.deleteSongByUserEvent(userEvent.data); break; }
            case 'tbl_processes': { this.deleteProcessByUserEvent(userEvent.data); break; }
            default: { console.log('Удаление записи из неизвестной таблицы: ', userEvent.data.tableName) }
          }
          break;
        }
        case 'PROCESS_WORKER_STATE': { this.updateProcessWorkerStateByUserEvent(userEvent.data); break; }
        case 'MESSAGE': { this.showMessageByUserEvent(userEvent.data); break; }
        case 'DUMMY': { console.log("DUMMY MESSAGE"); break; }
        case 'LOG': { this.logMessageByUserEvent(userEvent.data); break; }
        default: { console.log("Неизвестный тип события: ", userEvent.type); }
      }
    }
  },
  async mounted () {
    // this.connect();
    // await this.loadSongs({filter_author: 'Ундервуд'});
    // await this.loadPublications({filterCond: 'fromnotpublish'});
    // await this.loadUnpublications();
    await this.loadProcesses({});
    // setInterval(this.checkUpdateSongs, 10_000);
    // setInterval(this.checkUpdateProcesses, 1_000);

    const msgServer = new EventSourcePolyfill('/apis/subscribe')
    msgServer.addEventListener('user', (event) => {
      this.userEvent(JSON.parse(event.data).payload)
    }, false);
  },

}).$mount('#app')
