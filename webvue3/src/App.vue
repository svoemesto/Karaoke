<!-- webvue3/src/App.vue -->
<template>
  <div id="app">
    <div class="app-header">
      <ul class="nav nav-pills">
        <li class="nav-item"><router-link to="/">Главная</router-link></li>
        <li class="nav-item"><router-link to="/songs">Песни</router-link></li>
        <li class="nav-item"><router-link to="/publish">Публикации</router-link></li>
        <li class="nav-item"><router-link to="/authors">Авторы</router-link></li>
        <li class="nav-item"><router-link to="/pictures">Картинки</router-link></li>
        <li class="nav-item"><router-link to="/processes">Процессы</router-link></li>
        <li class="nav-item"><router-link to="/properties">Настройки</router-link></li>
      </ul>
      <!-- Эти компоненты нужно будет создать или адаптировать для Vue 3 -->
      <BackendConsole/>
      <ProcessWorker/>
    </div>
    <router-view/>
  </div>
</template>

<script setup>
// Импорт компонентов, которые используются в шаблоне
// Пути могут отличаться в зависимости от их расположения в новом проекте
import ProcessWorker from "./components/Common/ProcessWorker.vue";
import BackendConsole from "./components/Common/BackendConsole.vue";

// В старом App.vue не было локальной логики, кроме стилей.
// Всё взаимодействие с EventSource, Vuex и т.д. происходило в main.js.
// В Vue 3 с Composition API, если бы была локальная логика, её можно было бы разместить здесь.
// Но для этой структуры App.vue, основная логика остаётся в main.js и других компонентах.
</script>

<script>
import {EventSourcePolyfill} from "event-source-polyfill";
import store from "./store/index.js";

export default {
  methods: {
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
            case 'tbl_processes': { console.log('RECORD_ADD: ', userEvent.data.record.description); this.addProcessByUserEvent(userEvent.data); break; }
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

    // loadSongs(params) {
    //   return this.$store.dispatch('loadSongsAndDictionaries', params)
    // },
    // loadProcesses(params) {
    //   return this.$store.dispatch('loadProcessesAndDictionaries', params)
    // },
    // loadPublications(params) {
    //   return this.$store.dispatch('loadPublications', params)
    // },
    // loadUnpublications() {
    //   return this.$store.dispatch('loadUnpublications')
    // },
    // async checkUpdateSongs() {
    //   // Получаем с бэка список айдишников песен, измененных с момента последней проверки
    //   let ids = JSON.parse(await this.$store.getters.getSongsIdsForUpdate);
    //   if (ids.length > 0) {
    //     // console.log('ids changed: ', ids);
    //     // Если список получен, то нужно проверить, есть ли эти песни в songPages и для тех что есть
    //     // сформировать структуру с индексами страниц и песен
    //
    //     // Получаем структуры вида [{songIndex, songId, pageIndex}]
    //     let songsIds = this.$store.getters.getSongsIds;
    //     // let songsIds = this.$store.getters.getSongsIds.filter(item => ids.includes(item.songId))
    //     // Получаем песни для обновления
    //     let songsForUpdate = JSON.parse(await this.$store.getters.getSongsForUpdateByIds(ids));
    //     let songsAndIndexesForUpdate = [];
    //     for (let i = 0; i < songsForUpdate.length; i++) {
    //       let song = songsForUpdate[i];
    //       let indexes = songsIds.find(item => item.songId === song.id);
    //       if (indexes) {
    //         songsAndIndexesForUpdate.push({ song: song, songIndex: indexes.songIndex, songId: song.id, pageIndex: indexes.pageIndex });
    //       } else {
    //         songsAndIndexesForUpdate.push({ song: song, songId: song.id });
    //       }
    //     }
    //     await this.$store.dispatch('updateSongsByIds', {songsAndIndexesForUpdate: songsAndIndexesForUpdate});
    //     await this.$store.dispatch('updateSongsDigestByIds', {songsAndIndexesForUpdate: songsAndIndexesForUpdate});
    //     await this.$store.dispatch('updatePublishDigestByIds', {songsAndIndexesForUpdate: songsAndIndexesForUpdate});
    //
    //   }
    //   await this.$store.dispatch('setLastUpdateSong', {lastUpdateSong: Date.now()});
    // },

    // async checkUpdateProcesses() {
    //   // Получаем с бэка список айдишников процессов, измененных с момента последней проверки
    //   let ids = JSON.parse(await this.$store.getters.getProcessesIdsForUpdate);
    //   if (ids.length > 0) {
    //     // Получаем структуры вида [{processIndex, processId, pageIndex}]
    //     let processesIds = this.$store.getters.getProcessesIds;
    //     // Получаем процессы для обновления
    //     let processesForUpdate = JSON.parse(await this.$store.getters.getProcessesForUpdateByIds(ids));
    //     let processesAndIndexesForUpdate = [];
    //     for (let i = 0; i < processesForUpdate.length; i++) {
    //       let process = processesForUpdate[i];
    //       let isProcessInPages = processesIds.filter(item => ids.includes(item.processId)).length > 0;
    //       if (isProcessInPages) {
    //         let indexes = processesIds.find(item => item.processId === process.id);
    //         processesAndIndexesForUpdate.push({ process: process, processIndex: indexes.processIndex, processId: process.id, pageIndex: indexes.pageIndex });
    //       } else {
    //         processesAndIndexesForUpdate.push({ process: process, processId: process.id });
    //       }
    //     }
    //     await this.$store.dispatch('updateProcessesByIds', {processesAndIndexesForUpdate: processesAndIndexesForUpdate});
    //
    //   }
    //   await this.$store.dispatch('setLastUpdateProcess', {lastUpdateProcess: Date.now()});
    // },

  },

  mounted() {
    console.log('APP mounted')
    const msgServer = new EventSourcePolyfill('/apis/subscribe')
    msgServer.addEventListener('user', (event) => {
      this.userEvent(JSON.parse(event.data).payload)
    }, false);
  }
}
</script>

<style>
/* Эти стили в основном касаются общего оформления, тостов и таблиц */
#app {
  font-family: Avenir, Helvetica, Arial, sans-serif !important;
  font-weight: 300 !important;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  text-align: center;
  color: #2c3e50;
}

.app-header {
  display: flex;
  flex-direction: row;
  justify-content: space-between;
  align-items: center;
}

.nav-item:hover {
  text-decoration: none;
  background-color: #4AAE9B;
}

.nav {
  display: flex;
  justify-content: center;
}

nav a {
  font-weight: bold;
  color: #2c3e50;
}

li a {
  padding: 0 5px 0 5px;
}

.router-link-active {
  padding: 0 5px 0 5px;
}

.router-link-exact-active {
  color: red;
  font-weight: bold;
  text-decoration: none;
}

.table-sm th {
  font-size: small !important;
}
.table-sm td, .table-sm th {
  padding: 0 !important;
}
.table-sm tr:hover {
  font-weight: bold !important;
}

/* Стили для тостов (теперь нужно убедиться, что они совместимы с новой системой тостов, например, через CSS-классы или библиотеку для Vue 3) */
.b-toaster {
  position: absolute;
  top: 50px;
  left: 10px;
}
/* Эти стили для тостов, возможно, нужно будет адаптировать под новую библиотеку или способы переопределения стилей */
/* Они могут не работать напрямую, если используется другая система тостов, например, через плагин Vue 3 */
.toast {
  opacity: 1;
  animation-duration: 0.3s !important;
}

.toast.fade-enter-active,
.toast.fade-leave-active {
  transition: opacity 0.3s, transform 0.3s !important;
}

.toast.fade-enter {
  opacity: 0;
  transform: translateY(-20px);
}

.toast.fade-leave-to {
  opacity: 0;
  transform: translateX(100px);
}

/* Предотвращение моргания */
/* .toast:not(.show) { display: none !important; } - УБРАНО, ТАК КАК ЭТО БЫЛО ПРИЧИНОЙ ПРОБЛЕМЫ С ТОСТЕРАМИ В СТАРОМ ПРОЕКТЕЕ */
/* Стили для заголовка и тела тоста */
.toast-header {
  color: #fff;
  background-color: rgb(50 50 255 / 85%)
}
.toast-body {
  padding: .75rem;
  color: #000;
  background-color: rgb(200 200 255 / 85%);
}

/* Стилизация заголовка и кнопки закрытия (если используется стандартная кнопка) */
.custom-toast-header {
  background-color: rgb(50 50 255 / 85%) !important;
  color: white !important;
}

/* Стилизация кнопки закрытия */
.custom-toast-header .close {
  color: white !important;
  opacity: 1 !important;
  text-shadow: none !important;
}

.custom-toast-header .close:hover {
  color: #f8f9fa !important;
  opacity: 0.8 !important;
}

.custom-header-with-icon .close {
  background: none !important;
  border: none !important;
  font-size: 1.3rem !important;
  font-weight: normal !important;
  color: #17a2b8 !important;
  opacity: 1 !important;
  padding: 0 !important;
  width: 24px !important;
  height: 24px !important;
  display: flex !important;
  align-items: center !important;
  justify-content: center !important;
}

.custom-header-with-icon .close:hover {
  background-color: rgba(23, 162, 184, 0.1) !important;
  border-radius: 50% !important;
  color: #0f6674 !important;
}

</style>
