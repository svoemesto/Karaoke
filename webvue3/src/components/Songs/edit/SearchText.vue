<template>
  <transition name="modal-fade">
    <div class="st-modal-backdrop">
      <div class="st-area">
        <custom-confirm
          v-if="isCustomConfirmVisible"
          :params="customConfirmParams"
          @close="closeCustomConfirm"
        />

        <!-- Заголовок модального окна -->
        <div class="st-modal-header">Поиск текста песни в интернете (Yandex Search API)</div>

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
              <search-text-results-table
                v-if="searchAsyncId"
                :search-results-list="searchResultsList"
                @selected-result="selectedResult"
              />
            </div>

            <!-- Второй столбец тела -->
            <div class="st-body-column-2">
              <!-- Текст результата поиска -->
              <textarea class="result-text" v-text="resultText" />
              <button class="group-button" title="Открыть на сайте" @click="openResultLink">
                Открыть на сайте
              </button>
            </div>
          </div>

          <div v-else>Асинхронный поиск еще не готов, попробуйте позже.</div>

          <!-- Подвал -->
          <div class="st-footer">
            <!-- Вернуть текст и выйти -->
            <button class="btn-round-double" title="Вернуть текст и выйти" @click="returnAndClose">
              <img
                alt="return text"
                class="icon-40"
                src="../../../assets/svg/icon_markers_in_region_paste.svg"
              />
            </button>

            <!-- Скопировать текст и выйти-->
            <button class="btn-round-double" title="Сопировать текст и выйти" @click="copyAndClose">
              <img alt="copy text" class="icon-40" src="../../../assets/svg/icon_copy.svg" />
            </button>

            <!-- Искать заново (другим движком) -->
            <button class="group-button" title="Искать заново" @click="openResearchConfirm">
              Искать заново
            </button>

            <!-- Удалить результаты поиска (без нового поиска) -->
            <button
              class="group-button"
              title="Удалить результаты поиска"
              @click="openDeleteConfirm"
            >
              Удалить результаты поиска
            </button>

            <!-- Выйти-->
            <button class="btn-round-double" title="Выйти" @click="close">
              <img alt="close" class="icon-40" src="../../../assets/svg/icon_close.svg" />
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
import CustomConfirm from '../../Common/CustomConfirm.vue'
import SearchTextResultsTable from './SearchTextResultsTable.vue'

/**
 * UI для LLM-поиска текста песни и аккордов.
 *
 * Workflow:
 * 1. Пользователь нажимает «Найти текст» или «Найти аккорды».
 * 2. POST `/api/llm/findLyrics` или `/findChords` с параметрами песни.
 * 3. Backend `LyricsFinderService` ищет 5-10 кандидатов через
 *    `SearchTool` (Google/Bing) и парсит через `ScraperAgent` (LLM).
 * 4. UI показывает превью результатов, пользователь выбирает лучший.
 * 5. Применение → `Song.subsText` / `Song.chordsText` сохраняется.
 *
 * Использует SSE для live-обновления прогресса поиска (статус задачи).
 *
 * @emits apply-text - текст применён (`{ text: string }`)
 * @emits apply-chords - аккорды применены (`{ chords: string }`)
 * @see docs/features/llm-lyrics-search.md
 */
export default {
  name: 'SearchText',
  components: {
    CustomConfirm,
    SearchTextResultsTable,
  },
  props: {
    songId: {
      type: String,
      required: true,
    },
  },
  data() {
    return {
      isCustomConfirmVisible: false,
      customConfirmParams: undefined,
      searchResultsList: [],
      currentSearchAsync: undefined,
      currentResult: undefined,
      searchIsDone: false,
    }
  },

  computed: {
    resultText() {
      return this.currentResult ? this.currentResult.text : ''
    },
    resultLink() {
      return this.currentResult ? this.currentResult.url : ''
    },
    searchAsyncId() {
      return this.currentSearchAsync ? this.currentSearchAsync.id : ''
    },
    searchAsyncQuery() {
      return this.currentSearchAsync ? this.currentSearchAsync.query : ''
    },
  },

  async mounted() {
    const listSearchAsync = await this.getAsyncList(this.songId)
    if (listSearchAsync.length > 0) {
      console.log('listSearchAsync.length', listSearchAsync.length)
      const currentSearchAsync = listSearchAsync[0]
      if (!currentSearchAsync.done) {
        this.searchIsDone = false
      } else {
        this.searchIsDone = true
        const searchAsyncId = currentSearchAsync.id
        const listSearchResults = await this.getResultsList(searchAsyncId)
        this.searchResultsList = listSearchResults
        this.currentSearchAsync = currentSearchAsync
      }
    } else {
      this.customConfirmParams = {
        header: 'Подтвердите поиск текста',
        body: `Найти в Интернете тексты для этой песни?`,
        timeout: 10,
        callback: this.doSearchTextForSong,
      }
      this.isCustomConfirmVisible = true
    }
  },

  methods: {
    openResultLink() {
      window.open(this.resultLink, '_blank')
    },
    closeCustomConfirm() {
      this.isCustomConfirmVisible = false
    },
    close() {
      this.$emit('close')
    },
    copyAndClose() {
      this.copyToClipboard(this.resultText)
      this.$emit('close')
    },
    returnAndClose() {
      this.$emit('return', this.resultText)
    },
    async copyToClipboard(value) {
      await navigator.clipboard.writeText(value)
    },
    async getAsyncList(songId) {
      const result = await this.$store.getters.getSearchAsyncList(songId)
      console.log('getAsyncList', result)
      return result
    },
    async getResultsList(searchAsyncId) {
      const result = await this.$store.getters.getSearchResultsList(searchAsyncId)
      console.log('searchResultsList', result)
      return result
    },
    selectedResult(selectedResult) {
      this.currentResult = selectedResult
    },
    doSearchTextForSong() {
      this.$store.dispatch('searchTextForSong')
      this.close
    },
    /**
     * Открывает диалог подтверждения повторного поиска с выбором движка
     * (specs/015-search-engine-selection). Значение по умолчанию для селектора —
     * текущая настройка `lyricsSearchEngine`.
     */
    async openResearchConfirm() {
      const defaultEngine = await this.$store.getters.getPropValue('lyricsSearchEngine')
      this.customConfirmParams = {
        header: 'Выполнить поиск заново?',
        body: 'Ранее сохранённые результаты поиска для этой песни будут удалены. Продолжить?',
        fields: [
          {
            fldName: 'engine',
            fldLabel: 'Движок поиска',
            fldIsSelect: true,
            fldOptions: ['YANDEX_SYNC', 'YANDEX_ASYNC', 'SEARXNG', 'FOURGET'],
            fldValue: defaultEngine || 'FOURGET',
          },
        ],
        callback: (ret) => this.doResearch(ret.engine),
      }
      this.isCustomConfirmVisible = true
    },
    /** Удаляет старые результаты (на бэкенде, через forceResearch) и ищет заново выбранным движком. */
    async doResearch(engine) {
      await this.$store.dispatch('searchTextForSong', { forceResearch: true, engine: engine })
      const listSearchAsync = await this.getAsyncList(this.songId)
      if (listSearchAsync.length > 0) {
        const currentSearchAsync = listSearchAsync[0]
        this.searchIsDone = currentSearchAsync.done
        this.currentSearchAsync = currentSearchAsync
        this.searchResultsList = currentSearchAsync.done
          ? await this.getResultsList(currentSearchAsync.id)
          : []
      } else {
        this.searchIsDone = false
        this.currentSearchAsync = undefined
        this.searchResultsList = []
      }
      this.currentResult = undefined
    },
    /** Открывает диалог подтверждения удаления результатов поиска без нового поиска. */
    openDeleteConfirm() {
      this.customConfirmParams = {
        header: 'Удалить результаты поиска?',
        body: 'Все сохранённые результаты поиска текста для этой песни будут удалены. Продолжить?',
        callback: () => this.doDeleteResults(),
      }
      this.isCustomConfirmVisible = true
    },
    /** Удаляет результаты поиска (на бэкенде) и очищает локальное состояние, без нового поиска. */
    async doDeleteResults() {
      await this.$store.dispatch('deleteSearchResults', this.songId)
      this.searchResultsList = []
      this.currentSearchAsync = undefined
      this.currentResult = undefined
      this.searchIsDone = false
    },
  },
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
  background: #ffffff;
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
  height: calc(100vh - 327px);
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

.group-button {
  border: solid black thin;
  border-radius: 5px;
  background-color: white;
  width: 500px;
}
</style>
