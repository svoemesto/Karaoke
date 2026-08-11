<template>
  <transition name="modal-fade">
    <div class="slfm-modal-backdrop">
      <div class="slfm-area">
        <div class="slfm-area-modal-header">Фильтр временных ссылок</div>

        <div class="slfm-area-modal-body">
          <div class="slfm-root-wrapper">
            <div class="slfm-filter-row">
              <div class="slfm-row-label"><div v-text="'Только активные:'" /></div>
              <div class="slfm-row-input">
                <input v-model="activeOnlyFilter" type="checkbox" class="slfm-input-checkbox" />
              </div>
            </div>

            <div class="slfm-filter-row">
              <div class="slfm-row-label"><div v-text="'Owner ID:'" /></div>
              <div class="slfm-row-input">
                <input v-model="ownerIdFilter" class="slfm-input-field" type="number" />
              </div>
              <button
                :disabled="!ownerIdFilter"
                class="slfm-button-clear-field"
                @click.left="ownerIdFilter = ''"
                v-text="'X'"
              />
            </div>

            <div class="slfm-filter-row">
              <div class="slfm-row-label"><div v-text="'Song ID:'" /></div>
              <div class="slfm-row-input">
                <input v-model="songIdFilter" class="slfm-input-field" type="number" />
              </div>
              <button
                :disabled="!songIdFilter"
                class="slfm-button-clear-field"
                @click.left="songIdFilter = ''"
                v-text="'X'"
              />
            </div>

            <div class="slfm-filter-row">
              <div class="slfm-row-label"><div v-text="'Дата создания с:'" /></div>
              <div class="slfm-row-input">
                <input v-model="createdFromFilter" class="slfm-input-field" type="datetime-local" />
              </div>
              <button
                :disabled="!createdFromFilter"
                class="slfm-button-clear-field"
                @click.left="createdFromFilter = ''"
                v-text="'X'"
              />
            </div>

            <div class="slfm-filter-row">
              <div class="slfm-row-label"><div v-text="'Дата создания по:'" /></div>
              <div class="slfm-row-input">
                <input v-model="createdToFilter" class="slfm-input-field" type="datetime-local" />
              </div>
              <button
                :disabled="!createdToFilter"
                class="slfm-button-clear-field"
                @click.left="createdToFilter = ''"
                v-text="'X'"
              />
            </div>
          </div>
        </div>

        <div class="slfm-area-modal-footer">
          <button type="button" class="slfm-btn-close" @click="ok">Применить фильтр</button>
          <button type="button" class="slfm-btn-close" @click="cancel">Отмена</button>
        </div>
      </div>
    </div>
  </transition>
</template>

<script>
/**
 * Модальное окно фильтров для таблицы «Временные ссылки».
 *
 * Поля: activeOnly (checkbox), ownerId, songId, createdFrom, createdTo. По кнопке
 * «Применить» фильтры передаются в `loadShareLinksDigest` и применяются на бэкенде
 * через эндпоинт `POST /api/sharelinks/digest` (см. `ShareLinksAdminController.kt`).
 *
 * @see AGENTS.md
 * @see specs/171-admin-subscriptions-history/spec.md (FR-018)
 */
export default {
  name: 'ShareLinksFilterModal',
  data() {
    return {
      activeOnlyFilter: false,
      ownerIdFilter: '',
      songIdFilter: '',
      createdFromFilter: '',
      createdToFilter: '',
    }
  },
  methods: {
    ok() {
      const params = {}
      if (this.activeOnlyFilter) params.filterActiveOnly = true
      if (this.ownerIdFilter) params.filterOwnerId = Number(this.ownerIdFilter)
      if (this.songIdFilter) params.filterSongId = Number(this.songIdFilter)
      if (this.createdFromFilter)
        params.filterCreatedFrom = new Date(this.createdFromFilter).toISOString()
      if (this.createdToFilter)
        params.filterCreatedTo = new Date(this.createdToFilter).toISOString()
      this.$store.dispatch('loadShareLinksDigest', params)
      this.$emit('close')
    },
    cancel() {
      this.$emit('close')
    },
  },
}
</script>

<style scoped>
.slfm-modal-fade-enter,
.slfm-modal-fade-leave-active {
  opacity: 0;
}
.slfm-modal-fade-enter-active,
.slfm-modal-fade-leave-active {
  transition: opacity 0.5s ease;
}
.slfm-area-modal-header {
  background-color: darkslategray;
  padding: 10px;
  color: white;
  font-size: larger;
  font-weight: 300;
}
.slfm-area-modal-body {
  background-color: white;
  padding: 10px;
  color: black;
  font-size: larger;
  font-weight: 300;
}
.slfm-area-modal-footer {
  background-color: darkslategray;
  padding: 10px;
  color: white;
  font-size: larger;
  font-weight: 300;
  display: flex;
  justify-content: center;
}
.slfm-modal-backdrop {
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
.slfm-area {
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
.slfm-btn-close {
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
.slfm-root-wrapper {
  display: flex;
  flex-direction: column;
}
.slfm-button-clear-field {
  border: thin solid black;
  border-radius: 50%;
  font-size: x-small;
  height: 20px;
  width: 20px;
  margin-top: -4px;
  margin-left: -10px;
}
.slfm-filter-row {
  display: flex;
  flex-direction: row;
  align-items: center;
}
.slfm-row-label {
  min-width: 170px;
  max-width: 170px;
  text-align: right;
  padding: 0 3px;
  font-size: small;
}
.slfm-row-input {
  display: block;
  padding-bottom: 3px;
  width: 200px;
  text-align: left;
  font-size: small;
  border-radius: 5px;
  border-color: black;
  border-width: thin;
}
.slfm-input-field {
  box-sizing: border-box;
  border: 1px solid #767676;
  border-radius: 5px;
  padding: 1px 4px;
  font: inherit;
  background-color: white;
  width: calc(100% - 18px);
}
.slfm-input-field:hover {
  background-color: lightyellow;
}
.slfm-input-field:focus {
  background-color: cyan;
}
.slfm-input-checkbox {
  margin-left: 0;
}
</style>
