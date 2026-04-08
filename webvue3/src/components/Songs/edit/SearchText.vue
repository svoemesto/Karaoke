<template>
  <transition name="modal-fade">
    <div class="st-modal-backdrop">
      <div class="st-area">

        <custom-confirm v-if="isCustomConfirmVisible" :params="customConfirmParams" @close="closeCustomConfirm" />
        
        <!-- Заголовок модального окна -->
        <div class="st-modal-header">
          Поиск текста песни в интернете (Yandex Search API)
        </div>

        <!-- Тело модального окна -->
        <div class="st-modal-body">

          <!-- Заголовок -->
          <div class="st-header">
            {{ searchAsyncQuery }}
          </div>

          <!-- Тело -->
          <div v-if="searchIsDone" class="st-body">

            <!-- Первый столбец тела -->
            <div class="st-body-column-1">
              <!-- Таблица результатов поиска -->
              <search-text-results-table v-if="searchAsyncId" :searchResultsList="searchResultsList" @selectedResult="selectedResult"/>
            </div>
            
            <!-- Второй столбец тела -->
            <div class="st-body-column-2">
              <!-- Текст результата поиска -->
              <textarea class="result-text" v-text="resultText"></textarea>
            </div>

          </div>

          <div v-else>
            Асинхронный поиск еще не готов, попробуйте позже.
          </div>

          <!-- Подвал -->
          <div class="st-footer">

            <!-- Вернуть текст и выйти -->
            <button class="btn-round-double" @click="returnAndClose" title="Вернуть текст и выйти">
              <img alt="return text" class="icon-40" src="../../../assets/svg/icon_markers_in_region_paste.svg">
            </button>

            <!-- Скопировать текст и выйти-->
            <button class="btn-round-double" @click="copyAndClose" title="Сопировать текст и выйти">
              <img alt="copy text" class="icon-40" src="../../../assets/svg/icon_copy.svg">
            </button>

            <!-- Выйти-->
            <button class="btn-round-double" @click="close" title="Выйти">
              <img alt="close" class="icon-40" src="../../../assets/svg/icon_close.svg">
            </button>

          </div>
        </div>

        <!-- Подвал модального окна -->
        <!-- <div class="st-modal-footer"> -->
          <!-- <button type="button" class="st-btn-close" @click="close">Выход</button> -->
        <!-- </div>  -->

      </div>
    </div>
  </transition>
</template>

<script>

import CustomConfirm from "../../Common/CustomConfirm.vue";
import SearchTextResultsTable from "./SearchTextResultsTable.vue";

export default {
  name: "SearchText",
  components: {
    CustomConfirm,
    SearchTextResultsTable
  },
  data() {
    return {
      isCustomConfirmVisible: false,
      customConfirmParams: undefined,
      searchAsyncId: undefined,
      searchResultsList: [],
      currentSearchAsync: undefined,
      currentResult: undefined,
      searchIsDone: false
    }
  },
  props: {
    songId: {
      type: String,
      required: true
    }
  },

  computed: {
    resultText() {
      return this.currentResult ? this.currentResult.text : "";
    },
    searchAsyncId() {
      return this.currentSearchAsync ? this.currentSearchAsync.id : "";
    },
    searchAsyncQuery() {
      return this.currentSearchAsync ? this.currentSearchAsync.query : "";
    }
  },

  methods: {
    closeCustomConfirm() {
      this.isCustomConfirmVisible = false;
    },
    close() {
      this.$emit('close');
    },
    copyAndClose() {
      this.copyToClipboard(this.resultText);
      this.$emit('close');
    },
    returnAndClose() {
      this.$emit('return', this.resultText);
    },
    async copyToClipboard(value) {
      await navigator.clipboard.writeText(value);
    },
    async getAsyncList(songId) { 
      const result = await this.$store.getters.getSearchAsyncList(songId);
      console.log('getAsyncList', result);
      return result; 
    },
    async getResultsList(searchAsyncId) { 
      const result = await this.$store.getters.getSearchResultsList(searchAsyncId);
      console.log('searchResultsList', result);
      return result; 
    },
    selectedResult(selectedResult) {
      this.currentResult = selectedResult;

    },
    doSearchTextForSong() {
      this.$store.dispatch('searchTextForSong');
      this.close;
    }
  },

  async mounted() {
    const listSearchAsync = await this.getAsyncList(this.songId);
    if (listSearchAsync.length > 0) {
      console.log('listSearchAsync.length', listSearchAsync.length);
      const currentSearchAsync = listSearchAsync[0];
      if (!currentSearchAsync.done) {
        this.searchIsDone = false;
      } else {
        this.searchIsDone = true;
        const searchAsyncId = currentSearchAsync.id;
        const listSearchResults = await this.getResultsList(searchAsyncId);
        this.searchResultsList = listSearchResults;
        this.currentSearchAsync = currentSearchAsync;
      }
    } else {
      this.customConfirmParams = {
        header: 'Подтвердите поиск текста',
        body: `Найти в Интернете тексты для этой песни?`,
        timeout: 10,
        callback: this.doSearchTextForSong
      }
      this.isCustomConfirmVisible = true;
    }
  }
}

</script>

<style scoped>

.st-modal-backdrop {
  position: fixed;
  top: 0;
  bottom: 0;
  left: 0;
  right: 0;
  background-color: rgba(0, 0, 0, 0.3);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 1055;
}

.st-area {
  background: #FFFFFF;
  box-shadow: 2px 2px 20px 1px;
  overflow-x: auto;
  display: flex;
  flex-direction: column;
  width: auto;
  height: auto;
  position: relative;
  max-width: calc(100vw - 50px);
  max-height: calc(100vh - 50px);
}

.st-modal-body {
  background-color: white;
  padding: 10px;
  color: black;
  font-size: larger;
  font-weight: 300;
}

.st-modal-header {
  background-color: darkslategray;
  padding: 10px;
  color: white;
  font-size: larger;
  font-weight: 300;
}

.st-modal-footer {
  background-color: darkslategray;
  padding: 10px;
  color: white;
  font-size: larger;
  font-weight: 300;
  display: flex;
  justify-content: flex-end;
}

.st-header {
  display: flex;
  flex-direction: row;
  justify-content: flex-start;
  border: thin dashed darkgray;
  border-radius: 10px;
}

.st-header-column-1 {
  display: flex;
  flex-direction: column;
  padding: 5px 0;
  background-color: transparent;
}

.st-header-column-2 {
  display: flex;
  flex-direction: column;
  padding: 5px 0;
  background-color: transparent;
  width: 100%;
}

.st-body {
  margin: 0;
  display: flex;
  flex-direction: row;
  height: max-content;
  background-color: transparent;
  z-index: 100;
}

.st-body-column-1 {
  width: max-content;
  height: calc(100vh - 300px);
  margin: 5px 5px 5px 0;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  overflow-y: auto;
}

.st-body-column-2 {
  width: max-content;
  height: calc(100vh - 300px);
  margin: 5px 0;
}

.st-body-column-3 {
  width: max-content;
  height: max-content;
  margin: 5px 0;
}

.st-body-column-4 {
  width: max-content;
  height: max-content;
  margin: 5px 0;
}

.st-body-column-5 {
  width: max-content;
  height: max-content;
  margin: 5px 5px 5px 5px;
}

.st-footer {
  display: flex;
  flex-direction: row;
  justify-content: center;
  border: thin dashed darkgray;
  border-radius: 10px;
  padding: 5px 0;
  background-color: transparent;
}

.st-btn-close {
  border: 1px solid white;
  border-radius: 10px;
  font-size: 20px;
  cursor: pointer;
  font-weight: bold;
  color: white;
  background: transparent;
  width: 100px;
  height: auto;
}

.result-text {
  overflow: auto;
  display: block;
  text-align: left;
  width: 500px;
  height: calc(100vh - 300px);
  font-family: 'Courier New', Courier, monospace;
  font-size: small;
}

.btn-round-double {
  border: solid 1px black;
  border-radius: 6px;
  width: 50px;
  height: 50px;
  margin-left: 2px;
  background-color: antiquewhite;
}
.btn-round-double:hover {
  background-color: lightpink;
}
.btn-round-double:focus {
  background-color: darksalmon;
}
.btn-round-double[disabled] {
  background-color: lightgray;
}

.icon-40 {
  width: 40px;
  height: 40px;
}

</style>