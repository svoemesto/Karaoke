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
import {BApp} from 'bootstrap-vue-next';</script>

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
        case 'PROCESS_COUNT_WAITING': { this.setCountWaiting(userEvent.data); break; }
        case 'MESSAGE': { this.showMessageByUserEvent(userEvent.data, create); break; }
        case 'DUMMY': { console.log("DUMMY MESSAGE"); break; }
        case 'LOG': { this.logMessageByUserEvent(userEvent.data); break; }
        case 'CRUD': { this.crudMessageByUserEvent(userEvent.data, create); break; }
        case 'SYNC': { this.syncMessageByUserEvent(userEvent.data, create); break; }
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
    setCountWaiting(userEventData) {
      console.log('setCountWaiting userEventData', userEventData);
      this.$store.dispatch('setCountWaiting', userEventData);
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
      // console.log('methods addProcessByUserEvent from main.js');
      this.$store.dispatch('addProcessByUserEvent', userEventData);
    },
    logMessageByUserEvent(text) {
      this.$store.dispatch('setLogMessage', text);
    },
    showMessageByUserEvent(userEvent, create) {
      if (document.hidden) return
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
        position: 'top-end',
        // position: 'top-start',
        // noHoverPause: true,
        // noProgress: true
        // modelValue: true
      })
    },
    crudMessageByUserEvent(userEvent, create) {
      if (document.hidden) return
      const createTextWithLineBreaks = (lines) => {
        if (!Array.isArray(lines)) {
          return h('div', {
            style: {
              fontFamily: 'monospace',
              fontSize: 'x-small',
              textAlign: 'left',
              fontWeight: 'bold',
              paddingRight: '5px',
              color: 'darkred'
            }
          }, [String(lines)]); // Если это не массив, возвращаем как одну строку
        }
        const vnodes = [];
        lines.forEach((line, index) => {
          const lineToAdd = h('div', { style: { fontFamily: 'monospace', fontSize: 'x-small', textAlign: 'left', fontWeight: 'bold', paddingRight: '5px', color: 'darkred' } }, line);
          vnodes.push(lineToAdd);
          // Добавляем <br> после каждого элемента, кроме последнего
          // if (index < lines.length - 1) {
          //   vnodes.push(h('br'));
          // }
        });
        return vnodes;
      };

      // Проверяем, является ли userEvent.body массивом
      let listOfLists = userEvent;
      if (typeof userEvent === 'string') {
        try {
          listOfLists = JSON.parse(userEvent);
        } catch (e) {
          console.error("Error parsing userEvent as JSON:", e);
          // В случае ошибки, обрабатываем как одну строку
          listOfLists = [userEvent];
        }
      }

      let vNodesMsg = [];
      if (!Array.isArray(listOfLists) || listOfLists.length !== 3) {
        console.warn("userEvent is not an array of 3 lists. Treating as plain text.");
        // Обработка как простого текста, если структура неожиданная
        vNodesMsg = h('div', [
          h('div', { style: { fontFamily: 'sans-serif', fontSize: 'small', textAlign: 'left' } }, String(userEvent))
        ]);
        // ... остальная логика для vNodesMsg ...
      } else {
        // Обработка как массива из 3 списков
        vNodesMsg = h('div', [
          h('div', { style: { fontFamily: 'sans-serif', fontSize: 'small', textAlign: 'left' } }, [
            // Обработка первого списка
            ...(listOfLists[0] && listOfLists[0].length > 0 ? [
              `Создано записей: ${listOfLists[0].length}`,
              // h('br'),
              ...createTextWithLineBreaks(listOfLists[0]),
              // h('br') // Разделитель после первого списка
            ] : []),

            // Обработка второго списка
            ...(listOfLists[1] && listOfLists[1].length > 0 ? [
              `Обновлено записей: ${listOfLists[1].length}`,
              // h('br'),
              ...createTextWithLineBreaks(listOfLists[1]),
              // h('br') // Разделитель после второго списка
            ] : []),

            // Обработка третьего списка
            ...(listOfLists[2] && listOfLists[2].length > 0 ? [
              `Удалено записей: ${listOfLists[2].length}`,
              // h('br'),
              ...createTextWithLineBreaks(listOfLists[2])
              // Не добавляем <br> после последнего списка
            ] : [])
          ])
        ]);
      }

      create({
        slots: { default: () => [vNodesMsg] },
        title: "CRUD",
        autoHideDelay: 3000,
        bodyClass: 'toast-body-crudmessage',
        headerClass: 'toast-header-crudmessage',
        appendToast: false,
        position: 'top-end',
        // position: 'top-start',
        // noHoverPause: true,
        // noProgress: true
        // modelValue: true
      })
    },
    syncMessageByUserEvent(userEvent, create) {
      if (document.hidden) return
      const createTextWithLineBreaks = (lines) => {
        if (!Array.isArray(lines)) {
          return h('div', {
            style: {
              fontFamily: 'monospace',
              fontSize: 'x-small',
              textAlign: 'left',
              fontWeight: 'bold',
              paddingRight: '5px',
              color: 'darkred'
            }
          }, [String(lines)]); // Если это не массив, возвращаем как одну строку
        }
        const vnodes = [];
        lines.forEach((line, index) => {
          const lineToAdd = h('div', { style: { fontFamily: 'monospace', fontSize: 'x-small', textAlign: 'left', fontWeight: 'bold', paddingRight: '5px', color: 'darkred' } }, line);
          vnodes.push(lineToAdd);
          // Добавляем <br> после каждого элемента, кроме последнего
          // if (index < lines.length - 1) {
          //   vnodes.push(h('br'));
          // }
        });
        return vnodes;
      };

      // Проверяем, является ли userEvent.body массивом
      let listOfLists = userEvent;
      if (typeof userEvent === 'string') {
        try {
          listOfLists = JSON.parse(userEvent);
        } catch (e) {
          console.error("Error parsing userEvent as JSON:", e);
          // В случае ошибки, обрабатываем как одну строку
          listOfLists = [userEvent];
        }
      }

      let vNodesMsg = [];
      if (!Array.isArray(listOfLists) || listOfLists.length !== 1) {
        console.warn("userEvent is not an array of 1 lists. Treating as plain text.");
        // Обработка как простого текста, если структура неожиданная
        vNodesMsg = h('div', [
          h('div', { style: { fontFamily: 'sans-serif', fontSize: 'small', textAlign: 'left' } }, String(userEvent))
        ]);
        // ... остальная логика для vNodesMsg ...
      } else {
        // Обработка как массива
        vNodesMsg = h('div', [
          h('div', { style: { fontFamily: 'sans-serif', fontSize: 'small', textAlign: 'left' } }, [
            // Обработка первого списка
            ...(listOfLists[0] && listOfLists[0].length > 0 ? [
              `Добавлено записей в SYNC-таблицу: ${listOfLists[0].length}`,
              // h('br'),
              ...createTextWithLineBreaks(listOfLists[0]),
              // h('br') // Разделитель после первого списка
            ] : []),
          ])
        ]);
      }

      create({
        slots: { default: () => [vNodesMsg] },
        title: "SYNC",
        autoHideDelay: 3000,
        bodyClass: 'toast-body-syncmessage',
        headerClass: 'toast-header-syncmessage',
        appendToast: false,
        position: 'top-end',
        // position: 'top-start',
        // noHoverPause: true,
        // noProgress: true
        // modelValue: true
      })
    },
  },
  async mounted() {
    console.log('APP mounted')
    const {create} = useToast();
    const msgServer = new EventSourcePolyfill('/apis/subscribe')
    msgServer.addEventListener('user', (event) => {
      this.userEvent(JSON.parse(event.data).payload, create)
    }, false);

    this.$store.dispatch('setLastSettingType',{ value: await this.$store.getters.getWebvueProp('lastSettingType', 'COMMENT') });
    this.$store.dispatch('setLastSettingValue', { value: await this.$store.getters.getWebvueProp('lastSettingValue', 'Комментарий') });
    this.$store.dispatch('setLastPriorLyrics', { value: await this.$store.getters.getWebvueProp('lastPriorLyrics', '1') });
    this.$store.dispatch('setLastPriorKaraoke', { value: await this.$store.getters.getWebvueProp('lastPriorKaraoke', '0') });
    this.$store.dispatch('setLastPriorChords', { value: await this.$store.getters.getWebvueProp('lastPriorChords', '') });
    this.$store.dispatch('setLastPriorMelody', { value: await this.$store.getters.getWebvueProp('lastPriorMelody', '') });
    this.$store.dispatch('setLastPriorCodeLyrics', { value: await this.$store.getters.getWebvueProp('lastPriorCodeLyrics', '10') });
    this.$store.dispatch('setLastPriorCodeKaraoke', { value: await this.$store.getters.getWebvueProp('lastPriorCodeKaraoke', '10') });
    this.$store.dispatch('setLastPriorDemucs', { value: await this.$store.getters.getWebvueProp('lastPriorDemucs', '-1') });
    this.$store.dispatch('setLastPriorSymlinks', { value: await this.$store.getters.getWebvueProp('lastPriorSymlinks', '-1') });
    this.$store.dispatch('setLastPriorSmartCopy', { value: await this.$store.getters.getWebvueProp('lastPriorSmartCopy', '-1') });

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
  justify-content: flex-start;
  align-items: center;
}

/*.nav-item:hover {*/
/*  text-decoration: none;*/
/*  background-color: #4AAE9B;*/
/*}*/

.nav {
  display: flex;
  justify-content: center;
}

/*nav a {*/
/*  font-weight: bold;*/
/*  color: #2c3e50;*/
/*}*/

li a {
  background: grey;
  color: white;
  text-decoration: none;
  border-radius: 15px;
  padding: 10px;
  margin: 0 2px 0 2px;
  font-weight: bold;
}

li a:hover {
  background: blue;
  color: white;
  text-decoration: none;
  border-radius: 15px;
  padding: 10px;
  margin: 0 2px 0 2px;
  font-weight: bold;
}

.router-link-active {
  padding: 0 5px 0 5px;
}

.router-link-exact-active {
  background: #535bf2;
  color: white;
  text-decoration: none;
  border-radius: 15px;
  padding: 10px;
  margin: 0 2px 0 2px;
  font-weight: bold;
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
.toast-header-crudmessage {
  color: #fff !important;
  background-color: rgb(100 10 10 / 85%) !important
}
.toast-body-crudmessage {
  padding: .75rem !important;
  color: #000 !important;
  background-color: rgb(255 200 200 / 85%) !important;
}
.toast-header-syncmessage {
  color: #fff !important;
  background-color: rgb(100 10 10 / 85%) !important
}
.toast-body-syncmessage {
  padding: .75rem !important;
  color: #000 !important;
  background-color: rgb(255 200 200 / 85%) !important;
}

</style>
