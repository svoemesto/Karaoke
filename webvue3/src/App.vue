<!-- webvue3/src/App.vue -->
<template>
  <BApp>
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
  </BApp>
</template>

<script setup>

import ProcessWorker from "./components/Common/ProcessWorker.vue";
import BackendConsole from "./components/Common/BackendConsole.vue";
import { BApp } from 'bootstrap-vue-next';

</script>

<script>

import {EventSourcePolyfill} from "event-source-polyfill";
import store from "./store/index.js";
import {useToast} from "bootstrap-vue-next";
import {h} from "vue";

export default {
  methods: {
    userEvent(userEvent, create) {
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
        case 'MESSAGE': { this.showMessageByUserEvent(userEvent.data, create); break; }
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
    showMessageByUserEvent(userEvent, create) {
      const vNodesMsg = h('div', [
        h('div', { style: { fontFamily: 'sans-serif', fontSize: 'small', textAlign: 'left' } }, userEvent.body)
      ]);
      create({
        slots: { default: () => [vNodesMsg] },
        body: userEvent.body,
        title: userEvent.head,
        autoHideDelay: 3000,
        bodyClass: 'toast-body-servermessage',
        headerClass: 'toast-header-servermessage',
        appendToast: false,
        position: 'top-start',
        // modelValue: true
      })
    },
  },

  mounted() {
    console.log('APP mounted')
    const {create} = useToast();
    const msgServer = new EventSourcePolyfill('/apis/subscribe')
    msgServer.addEventListener('user', (event) => {
      this.userEvent(JSON.parse(event.data).payload, create)
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

.d-flex {
  display: flex !important;
  flex-direction: column !important;
}
.toast-header-copytoclipboard {
  color: #fff !important;
  background-color: rgb(50 50 255 / 85%) !important
}
.toast-body-copytoclipboard {
  padding: .75rem !important;
  color: #000 !important;
  background-color: rgb(200 200 255 / 85%) !important;
}
.toast-header-servermessage {
  color: #fff !important;
  background-color: rgb(10 100 10 / 85%) !important
}
.toast-body-servermessage {
  padding: .75rem !important;
  color: #000 !important;
  background-color: rgb(200 255 200 / 85%) !important;
}
/*.b-toaster.b-toaster-top-left {*/
/*  top: 50px;*/
/*  left: 10px;*/
/*}*/

</style>
