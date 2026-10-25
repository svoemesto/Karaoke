<template>
  <transition name="modal-fade">
    <div class="lhfm-modal-backdrop">
      <div class="lhfm-area">
        <div class="lhfm-area-modal-header">Фильтр истории прослушиваний</div>

        <div class="lhfm-area-modal-body">
          <div class="lhfm-root-wrapper">
            <div class="lhfm-filter-row">
              <div class="lhfm-row-label"><div v-text="'User ID:'" /></div>
              <div class="lhfm-row-input">
                <input v-model="userIdFilter" class="lhfm-input-field" type="number" />
              </div>
              <button
                :disabled="!userIdFilter"
                class="lhfm-button-clear-field"
                @click.left="userIdFilter = ''"
                v-text="'X'"
              />
            </div>

            <div class="lhfm-filter-row">
              <div class="lhfm-row-label"><div v-text="'Song ID:'" /></div>
              <div class="lhfm-row-input">
                <input v-model="songIdFilter" class="lhfm-input-field" type="number" />
              </div>
              <button
                :disabled="!songIdFilter"
                class="lhfm-button-clear-field"
                @click.left="songIdFilter = ''"
                v-text="'X'"
              />
            </div>

            <div class="lhfm-filter-row">
              <div class="lhfm-row-label"><div v-text="'Слушал с:'" /></div>
              <div class="lhfm-row-input">
                <input
                  v-model="lastPlayedFromFilter"
                  class="lhfm-input-field"
                  type="datetime-local"
                />
              </div>
              <button
                :disabled="!lastPlayedFromFilter"
                class="lhfm-button-clear-field"
                @click.left="lastPlayedFromFilter = ''"
                v-text="'X'"
              />
            </div>

            <div class="lhfm-filter-row">
              <div class="lhfm-row-label"><div v-text="'Слушал по:'" /></div>
              <div class="lhfm-row-input">
                <input
                  v-model="lastPlayedToFilter"
                  class="lhfm-input-field"
                  type="datetime-local"
                />
              </div>
              <button
                :disabled="!lastPlayedToFilter"
                class="lhfm-button-clear-field"
                @click.left="lastPlayedToFilter = ''"
                v-text="'X'"
              />
            </div>
          </div>
        </div>

        <div class="lhfm-area-modal-footer">
          <button type="button" class="lhfm-btn-close" @click="ok">Применить фильтр</button>
          <button type="button" class="lhfm-btn-close" @click="cancel">Отмена</button>
        </div>
      </div>
    </div>
  </transition>
</template>

<script>
/**
 * Модальное окно фильтров для таблицы «История прослушиваний».
 *
 * Поля: userId, songId, lastPlayedFrom, lastPlayedTo. По кнопке «Применить» фильтры
 * передаются в `loadListeningHistoryDigest` и применяются на бэкенде через эндпоинт
 * `POST /api/listeninghistory/digest`. SKIP-фильтр всегда активен на бэкенде (см. спек
 * `ListeningHistoryController.kt`).
 *
 * @see AGENTS.md
 * @see specs/171-admin-subscriptions-history/spec.md (FR-011)
 */
export default {
  name: 'ListeningHistoryFilterModal',
  computed: {
    // BR-382: фильтры живут в Vuex (ListeningHistory/filter/store.js), а не только
    // в `data` модалки. Раньше они применялись одноразовым dispatch и тут же терялись:
    // пагинация, «Обновить», смена БД и смена «строк на страницу» уходили без
    // фильтров, и таблица показывала весь список.
    userIdFilter: {
      get() {
        return this.$store.getters.getListeningHistoryFilterUserId
      },
      set(value) {
        this.$store.dispatch('setListeningHistoryFilterUserId', { value: value })
      },
    },
    songIdFilter: {
      get() {
        return this.$store.getters.getListeningHistoryFilterSongId
      },
      set(value) {
        this.$store.dispatch('setListeningHistoryFilterSongId', { value: value })
      },
    },
    lastPlayedFromFilter: {
      get() {
        return this.$store.getters.getListeningHistoryFilterLastPlayedFrom
      },
      set(value) {
        this.$store.dispatch('setListeningHistoryFilterLastPlayedFrom', { value: value })
      },
    },
    lastPlayedToFilter: {
      get() {
        return this.$store.getters.getListeningHistoryFilterLastPlayedTo
      },
      set(value) {
        this.$store.dispatch('setListeningHistoryFilterLastPlayedTo', { value: value })
      },
    },
  },
  methods: {
    ok() {
      // BR-382: параметры фильтра собираются в одном месте — в сторе
      // (`getListeningHistoryFilter`), и применяются ко всем последующим загрузкам.
      // Даты приводятся к ISO — формат, который ждёт бэкенд.
      if (this.lastPlayedFromFilter) {
        this.$store.dispatch('setListeningHistoryFilterLastPlayedFrom', {
          value: new Date(this.lastPlayedFromFilter).toISOString(),
        })
      }
      if (this.lastPlayedToFilter) {
        this.$store.dispatch('setListeningHistoryFilterLastPlayedTo', {
          value: new Date(this.lastPlayedToFilter).toISOString(),
        })
      }
      // BR-382: страница сбрасывается на 1 — иначе первая страница показывалась
      // под номером прежней (например, «3»), а счётчик «Показано N из M» врал.
      this.$store.commit('setListeningHistoryTableCurrentPage', 1)
      this.$store.dispatch('loadListeningHistoryDigest', { page: 1 })
      this.$emit('close')
    },
    cancel() {
      this.$emit('close')
    },
  },
}
</script>

<style scoped>
.lhfm-modal-fade-enter,
.lhfm-modal-fade-leave-active {
  opacity: 0;
}
.lhfm-modal-fade-enter-active,
.lhfm-modal-fade-leave-active {
  transition: opacity 0.5s ease;
}
.lhfm-area-modal-header {
  background-color: darkslategray;
  padding: 10px;
  color: white;
  font-size: larger;
  font-weight: 300;
}
.lhfm-area-modal-body {
  background-color: white;
  padding: 10px;
  color: black;
  font-size: larger;
  font-weight: 300;
}
.lhfm-area-modal-footer {
  background-color: darkslategray;
  padding: 10px;
  color: white;
  font-size: larger;
  font-weight: 300;
  display: flex;
  justify-content: center;
}
.lhfm-modal-backdrop {
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
.lhfm-area {
  background: #ffffff;
  box-shadow: 2px 2px 20px 1px;
  overflow-x: auto;
  display: flex;
  flex-direction: column;
  width: auto;
  height: auto;
  position: relative;
  max-width: calc(100vw - 20px);
  max-height: calc(100vh - 20px);
}
.lhfm-btn-close {
  border: 1px solid white;
  border-radius: 10px;
  cursor: pointer;
  font-weight: bold;
  color: white;
  background: transparent;
  width: 150px;
  height: auto;
  font-size: small;
}
.lhfm-root-wrapper {
  display: flex;
  flex-direction: column;
}
.lhfm-button-clear-field {
  border: thin solid black;
  border-radius: 50%;
  font-size: x-small;
  height: 20px;
  width: 20px;
  margin-top: -4px;
  margin-left: -10px;
}
.lhfm-filter-row {
  display: flex;
  flex-direction: row;
  align-items: center;
}
.lhfm-row-label {
  min-width: 170px;
  max-width: 170px;
  text-align: right;
  padding: 0 3px;
  font-size: small;
}
.lhfm-row-input {
  display: block;
  padding-bottom: 3px;
  width: 200px;
  text-align: left;
  font-size: small;
  border-radius: 5px;
  border-color: black;
  border-width: thin;
}
.lhfm-input-field {
  box-sizing: border-box;
  border: 1px solid #767676;
  border-radius: 5px;
  padding: 1px 4px;
  font: inherit;
  background-color: white;
  width: calc(100% - 18px);
}
.lhfm-input-field:hover {
  background-color: lightyellow;
}
.lhfm-input-field:focus {
  background-color: cyan;
}
select.lhfm-input-field {
  appearance: none;
  -webkit-appearance: none;
  -moz-appearance: none;
  cursor: pointer;
}
</style>
