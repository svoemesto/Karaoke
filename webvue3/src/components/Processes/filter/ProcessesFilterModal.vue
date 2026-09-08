<template>
  <transition name="modal-fade">
    <div class="prfm-modal-backdrop">
      <div class="prfm-area">
        <div class="prfm-area-modal-header">Фильтр процессов</div>

        <div class="prfm-area-modal-body">
          <div class="prfm-root-wrapper">
            <div class="prfm-filter-row">
              <div class="prfm-row-label">
                <div v-text="'Статус:'" />
              </div>
              <div class="prfm-row-checkboxes">
                <label v-for="opt in STATUS_OPTIONS" :key="opt.value" class="prfm-checkbox">
                  <input v-model="processesFilterStatus" type="checkbox" :value="opt.value" />
                  <span v-text="opt.label" />
                </label>
              </div>
            </div>

            <div class="prfm-filter-row">
              <div class="prfm-row-label">
                <div v-text="'Тип:'" />
              </div>
              <div class="prfm-row-checkboxes">
                <label v-for="opt in TYPE_OPTIONS" :key="opt.value" class="prfm-checkbox">
                  <input v-model="processesFilterType" type="checkbox" :value="opt.value" />
                  <span v-text="opt.label" />
                </label>
              </div>
            </div>

            <div class="prfm-filter-row">
              <div class="prfm-row-label">
                <div v-text="'thID:'" />
              </div>
              <div class="prfm-row-input">
                <input v-model="processesFilterThreadId" class="prfm-input-field" />
              </div>
              <button
                :disabled="!processesFilterThreadId"
                class="prfm-button-clear-field"
                @click.left="processesFilterThreadId = ''"
                v-text="'X'"
              />
            </div>

            <div class="prfm-filter-row">
              <div class="prfm-row-label">
                <div v-text="'chainId:'" />
              </div>
              <div class="prfm-row-input">
                <input v-model="processesFilterChainId" class="prfm-input-field" />
              </div>
              <button
                :disabled="!processesFilterChainId"
                class="prfm-button-clear-field"
                @click.left="processesFilterChainId = ''"
                v-text="'X'"
              />
            </div>

            <div class="prfm-filter-row">
              <div class="prfm-row-label">
                <div v-text="'Имя:'" />
              </div>
              <div class="prfm-row-input">
                <input v-model="processesFilterName" class="prfm-input-field" />
              </div>
              <button
                :disabled="!processesFilterName"
                class="prfm-button-clear-field"
                @click.left="processesFilterName = ''"
                v-text="'X'"
              />
            </div>

            <div class="prfm-filter-row">
              <div class="prfm-row-label">
                <div v-text="'Включая удалённые:'" />
              </div>
              <div class="prfm-row-input">
                <input
                  v-model="processesFilterIncludeDeleted"
                  type="checkbox"
                  class="prfm-checkbox-single"
                />
              </div>
            </div>
          </div>
        </div>

        <div class="prfm-area-modal-footer">
          <button type="button" class="prfm-btn-close" @click="ok">Применить фильтр</button>
          <button type="button" class="prfm-btn-close" @click="cancel">Отмена</button>
        </div>
      </div>
    </div>
  </transition>
</template>

<script>
/**
 * Модальное окно для filter процессов (specs/315-admin-ui-karaoke-process-v5, US1).
 *
 * @see specs/315-admin-ui-karaoke-process-v5/spec.md
 */
const STATUS_OPTIONS = [
  { value: 'CREATING', label: 'CREATING' },
  { value: 'WAITING', label: 'WAITING' },
  { value: 'WORKING', label: 'WORKING' },
  { value: 'DONE', label: 'DONE' },
  { value: 'ERROR', label: 'ERROR' },
]

const TYPE_OPTIONS = [
  { value: 'NONE', label: 'NONE' },
  { value: 'MELT_LYRICS', label: 'MELT_LYRICS' },
  { value: 'MELT_KARAOKE', label: 'MELT_KARAOKE' },
  { value: 'MELT_CHORDS', label: 'MELT_CHORDS' },
  { value: 'MELT_TABS', label: 'MELT_TABS' },
  { value: 'DEMUCS2', label: 'DEMUCS2' },
  { value: 'DEMUCS5', label: 'DEMUCS5' },
  { value: 'SHEETSAGE', label: 'SHEETSAGE' },
  { value: 'SHEETSAGE2', label: 'SHEETSAGE2' },
  { value: 'FF_720_KAR', label: 'FF_720_KAR' },
  { value: 'FF_720_LYR', label: 'FF_720_LYR' },
  { value: 'SYMLINK', label: 'SYMLINK' },
  { value: 'SMARTCOPY', label: 'SMARTCOPY' },
  { value: 'COPY_TO_STORE_LYRICS', label: 'COPY_TO_STORE_LYRICS' },
  { value: 'COPY_TO_STORE_KARAOKE', label: 'COPY_TO_STORE_KARAOKE' },
  { value: 'FF_MP3_ACCOMPANIMENT', label: 'FF_MP3_ACCOMPANIMENT' },
  { value: 'FF_MP3_VOCAL', label: 'FF_MP3_VOCAL' },
  { value: 'FF_MP3_DRUMS', label: 'FF_MP3_DRUMS' },
  { value: 'FF_MP3_BASS', label: 'FF_MP3_BASS' },
  { value: 'FF_MP3_OTHER', label: 'FF_MP3_OTHER' },
  { value: 'KEY_BPM_FROM_FILE', label: 'KEY_BPM_FROM_FILE' },
  { value: 'UPLOAD_TO_LOCAL_STORE', label: 'UPLOAD_TO_LOCAL_STORE' },
  { value: 'UPLOAD_TO_REMOTE_STORE', label: 'UPLOAD_TO_REMOTE_STORE' },
  { value: 'RENDER_MP4_LYRICS', label: 'RENDER_MP4_LYRICS' },
  { value: 'RENDER_MP4_KARAOKE', label: 'RENDER_MP4_KARAOKE' },
  { value: 'RENDER_MP4_CHORDS', label: 'RENDER_MP4_CHORDS' },
  { value: 'RENDER_MP4_TABS', label: 'RENDER_MP4_TABS' },
  { value: 'RENDER_MP4_DEMO', label: 'RENDER_MP4_DEMO' },
  { value: 'FORCED_ALIGN_MARKERS', label: 'FORCED_ALIGN_MARKERS' },
  { value: 'STEM_JOB_DEMUCS2', label: 'STEM_JOB_DEMUCS2' },
  { value: 'STEM_JOB_DEMUCS5', label: 'STEM_JOB_DEMUCS5' },
]

export default {
  name: 'ProcessesFilterModal',
  data() {
    return {
      STATUS_OPTIONS,
      TYPE_OPTIONS,
    }
  },
  computed: {
    processesFilterStatus: {
      get() {
        return this.$store.getters.getProcessesFilterStatus
      },
      set(value) {
        this.$store.dispatch('setProcessesFilterStatus', { value: value })
      },
    },
    processesFilterType: {
      get() {
        return this.$store.getters.getProcessesFilterType
      },
      set(value) {
        this.$store.dispatch('setProcessesFilterType', { value: value })
      },
    },
    processesFilterThreadId: {
      get() {
        return this.$store.getters.getProcessesFilterThreadId
      },
      set(value) {
        this.$store.dispatch('setProcessesFilterThreadId', { value: value })
      },
    },
    processesFilterChainId: {
      get() {
        return this.$store.getters.getProcessesFilterChainId
      },
      set(value) {
        this.$store.dispatch('setProcessesFilterChainId', { value: value })
      },
    },
    processesFilterIncludeDeleted: {
      get() {
        return this.$store.getters.getProcessesFilterIncludeDeleted
      },
      set(value) {
        this.$store.dispatch('setProcessesFilterIncludeDeleted', { value: value })
      },
    },
    processesFilterName: {
      get() {
        return this.$store.getters.getProcessesFilterName
      },
      set(value) {
        this.$store.dispatch('setProcessesFilterName', { value: value })
      },
    },
  },
  async beforeMount() {
    this.$store.dispatch('setProcessesFilterStatus', {
      value: JSON.parse(
        (await this.$store.getters.getWebvueProp('processesFilterStatus', '[]')) || '[]',
      ),
    })
    this.$store.dispatch('setProcessesFilterType', {
      value: JSON.parse(
        (await this.$store.getters.getWebvueProp('processesFilterType', '[]')) || '[]',
      ),
    })
    this.$store.dispatch('setProcessesFilterThreadId', {
      value: await this.$store.getters.getWebvueProp('processesFilterThreadId', ''),
    })
    this.$store.dispatch('setProcessesFilterChainId', {
      value: await this.$store.getters.getWebvueProp('processesFilterChainId', ''),
    })
    this.$store.dispatch('setProcessesFilterIncludeDeleted', {
      value: await this.$store.getters.getWebvueProp('processesFilterIncludeDeleted', false),
    })
    this.$store.dispatch('setProcessesFilterName', {
      value: await this.$store.getters.getWebvueProp('processesFilterName', ''),
    })
  },
  methods: {
    buildFilters() {
      return {
        status: this.processesFilterStatus,
        type: this.processesFilterType,
        threadId: this.processesFilterThreadId || null,
        chainId: this.processesFilterChainId || null,
        includeDeleted: this.processesFilterIncludeDeleted,
        name: this.processesFilterName || null,
      }
    },
    ok() {
      this.$store.dispatch('setProcessesFilterStatus', { value: this.processesFilterStatus })
      this.$store.dispatch('setProcessesFilterType', { value: this.processesFilterType })
      this.$store.dispatch('setProcessesFilterThreadId', { value: this.processesFilterThreadId })
      this.$store.dispatch('setProcessesFilterChainId', { value: this.processesFilterChainId })
      this.$store.dispatch('setProcessesFilterIncludeDeleted', {
        value: this.processesFilterIncludeDeleted,
      })
      this.$store.dispatch('setProcessesFilterName', { value: this.processesFilterName })

      const filters = this.buildFilters()
      this.$store.dispatch('loadProcesses', filters)
      // specs/319-process-bulk-actions-v2 (FR-002, FR-004): обновить snapshot id для bulk-операций.
      this.$store.dispatch('fetchBulkSelectionIds', filters)
      this.$emit('close')
    },
    cancel() {
      this.$emit('close')
    },
  },
}
</script>

<style scoped>
.prfm-modal-fade-enter,
.prfm-modal-fade-leave-active {
  opacity: 0;
}

.prfm-modal-fade-enter-active,
.prfm-modal-fade-leave-active {
  transition: opacity 0.5s ease;
}

.prfm-area-modal-header {
  background-color: darkslategray;
  padding: 10px;
  color: white;
  font-size: larger;
  font-weight: 300;
}

.prfm-area-modal-body {
  background-color: white;
  padding: 10px;
  color: black;
  font-size: larger;
  font-weight: 300;
}

.prfm-area-modal-footer {
  background-color: darkslategray;
  padding: 10px;
  color: white;
  font-size: larger;
  font-weight: 300;
  display: flex;
  justify-content: center;
}

.prfm-modal-backdrop {
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

.prfm-area {
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

.prfm-btn-close {
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

.prfm-root-wrapper {
  display: flex;
  flex-direction: column;
}
.prfm-button-clear-field {
  border: thin solid black;
  border-radius: 50%;
  font-size: x-small;
  height: 20px;
  width: 20px;
  margin-top: -4px;
  margin-left: -10px;
}
.prfm-filter-row {
  display: flex;
  flex-direction: row;
  align-items: center;
}
.prfm-row-label {
  min-width: 140px;
  max-width: 140px;
  text-align: right;
  padding: 0 3px;
  font-size: small;
}
.prfm-row-input {
  display: block;
  padding-bottom: 3px;
  width: 200px;
  text-align: left;
  font-size: small;
  border-radius: 5px;
  border-color: black;
  border-width: thin;
}
.prfm-row-checkboxes {
  display: flex;
  flex-direction: row;
  flex-wrap: wrap;
  gap: 4px 12px;
  padding-bottom: 3px;
  font-size: small;
}
.prfm-checkbox {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 3px;
  white-space: nowrap;
}
.prfm-checkbox-single {
  margin-top: 4px;
}

/* Рамка/паддинг/фон/ШИРИНА заданы ЯВНО — поле сужено на 18px (10px под сдвиг кнопки
   margin-left:-10px + 8px видимого зазора), иначе круглая кнопка очистки наезжает на поле
   (было при width: fit-content). Паттерн — как в SongsFilterModal.vue. */
.prfm-input-field {
  box-sizing: border-box;
  border: 1px solid #767676;
  border-radius: 5px;
  padding: 1px 4px;
  font: inherit;
  background-color: white;
  width: calc(100% - 18px);
}

.prfm-input-field:hover {
  background-color: lightyellow;
}
.prfm-input-field:focus {
  background-color: cyan;
}

select.prfm-input-field {
  appearance: none;
  -webkit-appearance: none;
  -moz-appearance: none;
  cursor: pointer;
}
</style>
