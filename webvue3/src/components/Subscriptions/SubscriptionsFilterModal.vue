<template>
  <transition name="modal-fade">
    <div class="subsfm-modal-backdrop">
      <div class="subsfm-area">
        <div class="subsfm-area-modal-header">Фильтр подписок</div>

        <div class="subsfm-area-modal-body">
          <div class="subsfm-root-wrapper">
            <div class="subsfm-filter-row">
              <div class="subsfm-row-label"><div v-text="'Scope:'" /></div>
              <div class="subsfm-row-input">
                <select v-model="scopeFilter" class="subsfm-input-field">
                  <option value="">Все</option>
                  <option value="SONG">SONG</option>
                  <option value="SITE">SITE</option>
                </select>
              </div>
              <button
                :disabled="!scopeFilter"
                class="subsfm-button-clear-field"
                @click.left="scopeFilter = ''"
                v-text="'X'"
              />
            </div>

            <div class="subsfm-filter-row">
              <div class="subsfm-row-label"><div v-text="'Статус:'" /></div>
              <div class="subsfm-row-input">
                <select v-model="statusFilter" class="subsfm-input-field">
                  <option value="">Все</option>
                  <option value="PAID">PAID</option>
                  <option value="PENDING">PENDING</option>
                  <option value="CREATED">CREATED</option>
                  <option value="FAILED">FAILED</option>
                  <option value="REFUNDED">REFUNDED</option>
                  <option value="CANCELED">CANCELED</option>
                </select>
              </div>
              <button
                :disabled="!statusFilter"
                class="subsfm-button-clear-field"
                @click.left="statusFilter = ''"
                v-text="'X'"
              />
            </div>

            <div class="subsfm-filter-row">
              <div class="subsfm-row-label"><div v-text="'User ID:'" /></div>
              <div class="subsfm-row-input">
                <input v-model="userIdFilter" class="subsfm-input-field" type="number" />
              </div>
              <button
                :disabled="!userIdFilter"
                class="subsfm-button-clear-field"
                @click.left="userIdFilter = ''"
                v-text="'X'"
              />
            </div>

            <div class="subsfm-filter-row">
              <div class="subsfm-row-label"><div v-text="'Song ID:'" /></div>
              <div class="subsfm-row-input">
                <input v-model="songIdFilter" class="subsfm-input-field" type="number" />
              </div>
              <button
                :disabled="!songIdFilter"
                class="subsfm-button-clear-field"
                @click.left="songIdFilter = ''"
                v-text="'X'"
              />
            </div>

            <div class="subsfm-filter-row">
              <div class="subsfm-row-label"><div v-text="'Дата создания с:'" /></div>
              <div class="subsfm-row-input">
                <input
                  v-model="createdFromFilter"
                  class="subsfm-input-field"
                  type="datetime-local"
                />
              </div>
              <button
                :disabled="!createdFromFilter"
                class="subsfm-button-clear-field"
                @click.left="createdFromFilter = ''"
                v-text="'X'"
              />
            </div>

            <div class="subsfm-filter-row">
              <div class="subsfm-row-label"><div v-text="'Дата создания по:'" /></div>
              <div class="subsfm-row-input">
                <input v-model="createdToFilter" class="subsfm-input-field" type="datetime-local" />
              </div>
              <button
                :disabled="!createdToFilter"
                class="subsfm-button-clear-field"
                @click.left="createdToFilter = ''"
                v-text="'X'"
              />
            </div>
          </div>
        </div>

        <div class="subsfm-area-modal-footer">
          <button type="button" class="subsfm-btn-close" @click="ok">Применить фильтр</button>
          <button type="button" class="subsfm-btn-close" @click="cancel">Отмена</button>
        </div>
      </div>
    </div>
  </transition>
</template>

<script>
/**
 * Модальное окно фильтров для таблицы «Подписки».
 *
 * Поля: scope (SONG/SITE), status (PAID/PENDING/CREATED/FAILED/REFUNDED/CANCELED),
 * userId, songId, createdFrom, createdTo. По кнопке «Применить» фильмы передаются в
 * `loadSubscriptionsDigest` и применяются на бэкенде через эндпоинт
 * `POST /api/subscriptions/digest` (см. `SubscriptionsController.kt`).
 *
 * @see AGENTS.md
 * @see specs/171-admin-subscriptions-history/spec.md (FR-004)
 */
export default {
  name: 'SubscriptionsFilterModal',
  data() {
    return {
      scopeFilter: '',
      statusFilter: '',
      userIdFilter: '',
      songIdFilter: '',
      createdFromFilter: '',
      createdToFilter: '',
    }
  },
  methods: {
    ok() {
      const params = {}
      if (this.scopeFilter) params.filterScope = this.scopeFilter
      if (this.statusFilter) params.filterStatus = this.statusFilter
      if (this.userIdFilter) params.filterUserId = Number(this.userIdFilter)
      if (this.songIdFilter) params.filterSongId = Number(this.songIdFilter)
      if (this.createdFromFilter)
        params.filterCreatedFrom = new Date(this.createdFromFilter).toISOString()
      if (this.createdToFilter)
        params.filterCreatedTo = new Date(this.createdToFilter).toISOString()
      this.$store.dispatch('loadSubscriptionsDigest', params)
      this.$emit('close')
    },
    cancel() {
      this.$emit('close')
    },
  },
}
</script>

<style scoped>
.subsfm-modal-fade-enter,
.subsfm-modal-fade-leave-active {
  opacity: 0;
}
.subsfm-modal-fade-enter-active,
.subsfm-modal-fade-leave-active {
  transition: opacity 0.5s ease;
}
.subsfm-area-modal-header {
  background-color: darkslategray;
  padding: 10px;
  color: white;
  font-size: larger;
  font-weight: 300;
}
.subsfm-area-modal-body {
  background-color: white;
  padding: 10px;
  color: black;
  font-size: larger;
  font-weight: 300;
}
.subsfm-area-modal-footer {
  background-color: darkslategray;
  padding: 10px;
  color: white;
  font-size: larger;
  font-weight: 300;
  display: flex;
  justify-content: center;
}
.subsfm-modal-backdrop {
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
.subsfm-area {
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
.subsfm-btn-close {
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
.subsfm-root-wrapper {
  display: flex;
  flex-direction: column;
}
.subsfm-button-clear-field {
  border: thin solid black;
  border-radius: 50%;
  font-size: x-small;
  height: 20px;
  width: 20px;
  margin-top: -4px;
  margin-left: -10px;
}
.subsfm-filter-row {
  display: flex;
  flex-direction: row;
  align-items: center;
}
.subsfm-row-label {
  min-width: 170px;
  max-width: 170px;
  text-align: right;
  padding: 0 3px;
  font-size: small;
}
.subsfm-row-input {
  display: block;
  padding-bottom: 3px;
  width: 200px;
  text-align: left;
  font-size: small;
  border-radius: 5px;
  border-color: black;
  border-width: thin;
}
.subsfm-input-field {
  box-sizing: border-box;
  border: 1px solid #767676;
  border-radius: 5px;
  padding: 1px 4px;
  font: inherit;
  background-color: white;
  width: calc(100% - 18px);
}
.subsfm-input-field:hover {
  background-color: lightyellow;
}
.subsfm-input-field:focus {
  background-color: cyan;
}
select.subsfm-input-field {
  appearance: none;
  -webkit-appearance: none;
  -moz-appearance: none;
  cursor: pointer;
}
</style>
