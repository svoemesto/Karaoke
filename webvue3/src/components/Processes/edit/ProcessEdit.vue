<template>
  <div class="pe-root">
    <div v-if="process">
      <custom-confirm
        v-if="isCustomConfirmVisible"
        :params="customConfirmParams"
        @close="closeCustomConfirm"
      />
      <div v-if="error" class="pe-error" v-text="error" />
      <div class="pe-body">
        <div class="label-and-input">
          <div class="label">Имя:</div>
          <input v-model="form.name" class="input-field" />
          <button class="btn-round" :disabled="notChanged('name')" @click="undoField('name')">
            <img alt="undo" class="icon-undo" src="../../../assets/svg/icon_undo.svg" />
          </button>
        </div>

        <div class="label-and-input">
          <div class="label">Статус:</div>
          <select v-model="form.status" class="input-field">
            <option v-for="opt in STATUS_OPTIONS" :key="opt.value" :value="opt.value">
              {{ opt.label }}
            </option>
          </select>
          <button class="btn-round" :disabled="notChanged('status')" @click="undoField('status')">
            <img alt="undo" class="icon-undo" src="../../../assets/svg/icon_undo.svg" />
          </button>
        </div>

        <div class="label-and-input">
          <div class="label">Тип:</div>
          <select v-model="form.type" class="input-field">
            <option v-for="opt in TYPE_OPTIONS" :key="opt.value" :value="opt.value">
              {{ opt.label }}
            </option>
          </select>
          <button class="btn-round" :disabled="notChanged('type')" @click="undoField('type')">
            <img alt="undo" class="icon-undo" src="../../../assets/svg/icon_undo.svg" />
          </button>
        </div>

        <div class="label-and-input">
          <div class="label">Порядок:</div>
          <input v-model.number="form.order" type="number" class="input-field" />
          <button class="btn-round" :disabled="notChanged('order')" @click="undoField('order')">
            <img alt="undo" class="icon-undo" src="../../../assets/svg/icon_undo.svg" />
          </button>
        </div>

        <div class="label-and-input">
          <div class="label">Приоритет:</div>
          <input v-model.number="form.priority" type="number" class="input-field" />
          <button
            class="btn-round"
            :disabled="notChanged('priority')"
            @click="undoField('priority')"
          >
            <img alt="undo" class="icon-undo" src="../../../assets/svg/icon_undo.svg" />
          </button>
        </div>

        <div class="label-and-input">
          <div class="label">Prioritet:</div>
          <input v-model.number="form.prioritet" type="number" class="input-field" />
          <button
            class="btn-round"
            :disabled="notChanged('prioritet')"
            @click="undoField('prioritet')"
          >
            <img alt="undo" class="icon-undo" src="../../../assets/svg/icon_undo.svg" />
          </button>
        </div>

        <div class="label-and-input">
          <div class="label">threadId:</div>
          <input v-model.number="form.threadId" type="number" class="input-field" />
          <button
            class="btn-round"
            :disabled="notChanged('threadId')"
            @click="undoField('threadId')"
          >
            <img alt="undo" class="icon-undo" src="../../../assets/svg/icon_undo.svg" />
          </button>
        </div>

        <div class="label-and-input">
          <div class="label">songId:</div>
          <input v-model.number="form.songId" type="number" class="input-field" />
          <button class="btn-round" :disabled="notChanged('songId')" @click="undoField('songId')">
            <img alt="undo" class="icon-undo" src="../../../assets/svg/icon_undo.svg" />
          </button>
        </div>

        <div class="label-and-input">
          <div class="label">Команда:</div>
          <input v-model="form.command" class="input-field" />
          <button class="btn-round" :disabled="notChanged('command')" @click="undoField('command')">
            <img alt="undo" class="icon-undo" src="../../../assets/svg/icon_undo.svg" />
          </button>
        </div>

        <div class="label-and-input">
          <div class="label">Аргументы:</div>
          <textarea v-model="form.args" class="input-field" rows="2" />
          <button class="btn-round" :disabled="notChanged('args')" @click="undoField('args')">
            <img alt="undo" class="icon-undo" src="../../../assets/svg/icon_undo.svg" />
          </button>
        </div>

        <div class="label-and-input">
          <div class="label">Env:</div>
          <textarea v-model="form.envs" class="input-field" rows="2" />
          <button class="btn-round" :disabled="notChanged('envs')" @click="undoField('envs')">
            <img alt="undo" class="icon-undo" src="../../../assets/svg/icon_undo.svg" />
          </button>
        </div>

        <div class="label-and-input">
          <div class="label">Описание:</div>
          <textarea v-model="form.description" class="input-field" rows="2" />
          <button
            class="btn-round"
            :disabled="notChanged('description')"
            @click="undoField('description')"
          >
            <img alt="undo" class="icon-undo" src="../../../assets/svg/icon_undo.svg" />
          </button>
        </div>

        <div class="label-and-input">
          <div class="label">Начало:</div>
          <input v-model="form.startedAtLocal" type="datetime-local" class="input-field" />
          <button
            class="btn-round"
            :disabled="notChanged('startedAt')"
            @click="undoField('startedAt')"
          >
            <img alt="undo" class="icon-undo" src="../../../assets/svg/icon_undo.svg" />
          </button>
        </div>

        <div class="label-and-input">
          <div class="label">Конец:</div>
          <input v-model="form.endedAtLocal" type="datetime-local" class="input-field" />
          <button class="btn-round" :disabled="notChanged('endedAt')" @click="undoField('endedAt')">
            <img alt="undo" class="icon-undo" src="../../../assets/svg/icon_undo.svg" />
          </button>
        </div>

        <div class="label-and-input">
          <div class="label">Без контроля:</div>
          <input v-model="form.withoutControl" type="checkbox" class="input-checkbox" />
          <button
            class="btn-round"
            :disabled="notChanged('withoutControl')"
            @click="undoField('withoutControl')"
          >
            <img alt="undo" class="icon-undo" src="../../../assets/svg/icon_undo.svg" />
          </button>
        </div>
      </div>
      <div class="pe-footer">
        <button class="pe-btn-save" :disabled="!hasChanges" @click="save">Сохранить</button>
      </div>
    </div>
  </div>
</template>

<script>
import CustomConfirm from '../../Common/CustomConfirm.vue'

/**
 * Форма редактирования процесса (specs/315-admin-ui-karaoke-process-v5, US2).
 *
 * Все редактируемые поля (FR-008): name, status, order, priority, command, args, envs,
 * description, songId, type, process_start/process_end (datetime Local, RC-3 iter #3),
 * prioritet, withoutControl, threadId. Паттерн label-and-input/custom-confirm/notChanged/save
 * как в SongEdit.vue (FR-009, Lesson #1).
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

// Поля, которые отправляются в editProcess (ключ — имя поля DTO/JSON).
const EDITABLE_FIELDS = [
  'name',
  'status',
  'order',
  'priority',
  'command',
  'args',
  'envs',
  'description',
  'songId',
  'type',
  'startedAt',
  'endedAt',
  'prioritet',
  'withoutControl',
  'threadId',
]

export default {
  name: 'ProcessEdit',
  components: { CustomConfirm },
  props: {
    processId: {
      type: Number,
      required: true,
    },
  },
  data() {
    return {
      STATUS_OPTIONS,
      TYPE_OPTIONS,
      process: null,
      form: {},
      snapshot: {},
      isCustomConfirmVisible: false,
      customConfirmParams: undefined,
      error: null,
    }
  },
  computed: {
    hasChanges() {
      return EDITABLE_FIELDS.some((field) => this.notChanged(field) === false)
    },
  },
  async beforeMount() {
    try {
      const process = await this.$store.dispatch('loadProcessForEdit', this.processId)
      this.process = process
      this.form = this.toForm(process)
      this.snapshot = Object.assign({}, this.form)
    } catch (e) {
      this.showError(e)
    }
  },
  methods: {
    toForm(process) {
      return {
        name: process.name || '',
        status: process.status || '',
        type: process.type || '',
        order: process.order || 0,
        priority: process.priority || 0,
        prioritet: process.prioritet || 0,
        threadId: process.threadId || 0,
        songId: process.songId || null,
        command: process.command || '',
        args: process.args || '',
        envs: process.envs || '',
        description: process.description || '',
        startedAtLocal: this.toLocalInput(process.startedAt),
        endedAtLocal: this.toLocalInput(process.endedAt),
        withoutControl: !!process.withoutControl,
      }
    },
    toLocalInput(ts) {
      if (!ts) return ''
      const d = new Date(ts)
      if (isNaN(d.getTime())) return ''
      const pad = (n) => String(n).padStart(2, '0')
      return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`
    },
    toBackendString(localValue) {
      if (!localValue) return null
      return localValue.replace('T', ' ') + ':00'
    },
    notChanged(field) {
      if (field === 'startedAt') return this.form.startedAtLocal === this.snapshot.startedAtLocal
      if (field === 'endedAt') return this.form.endedAtLocal === this.snapshot.endedAtLocal
      return this.form[field] === this.snapshot[field]
    },
    undoField(field) {
      if (field === 'startedAt') this.form.startedAtLocal = this.snapshot.startedAtLocal
      else if (field === 'endedAt') this.form.endedAtLocal = this.snapshot.endedAtLocal
      else this.form[field] = this.snapshot[field]
    },
    buildChanges() {
      const changes = {}
      if (this.notChanged('name') === false) changes.name = this.form.name
      if (this.notChanged('status') === false) changes.status = this.form.status
      if (this.notChanged('type') === false) changes.type = this.form.type
      if (this.notChanged('order') === false) changes.order = this.form.order
      if (this.notChanged('priority') === false) changes.priority = this.form.priority
      if (this.notChanged('prioritet') === false) changes.prioritet = this.form.prioritet
      if (this.notChanged('threadId') === false) changes.threadId = this.form.threadId
      if (this.notChanged('songId') === false) changes.songId = this.form.songId
      if (this.notChanged('command') === false) changes.command = this.form.command
      if (this.notChanged('args') === false) changes.args = this.form.args
      if (this.notChanged('envs') === false) changes.envs = this.form.envs
      if (this.notChanged('description') === false) changes.description = this.form.description
      if (this.notChanged('startedAt') === false)
        changes.startedAt = this.toBackendString(this.form.startedAtLocal)
      if (this.notChanged('endedAt') === false)
        changes.endedAt = this.toBackendString(this.form.endedAtLocal)
      if (this.notChanged('withoutControl') === false)
        changes.withoutControl = this.form.withoutControl
      return changes
    },
    save() {
      const changes = this.buildChanges()
      if (Object.keys(changes).length === 0) {
        this.$emit('close')
        return
      }
      this.customConfirmParams = {
        header: 'Подтвердите сохранение',
        body: 'Сохранить изменения процесса?',
        callback: (ret) => {
          if (ret.confirmed) this.doSave(changes)
        },
      }
      this.isCustomConfirmVisible = true
    },
    doSave(changes) {
      this.error = null
      this.$store
        .dispatch('editProcess', { id: this.processId, changes })
        .then(() => {
          this.$emit('close')
        })
        .catch((e) => {
          this.showError(e)
        })
    },
    closeCustomConfirm() {
      this.isCustomConfirmVisible = false
    },
    showError(e) {
      let message = e.message || 'Ошибка'
      if (e.responseBody) {
        try {
          const parsed = JSON.parse(e.responseBody)
          if (parsed.message) message = parsed.message
        } catch (_) {
          /* ignore */
        }
      }
      this.error = message
    },
  },
}
</script>

<style scoped>
.pe-root {
  display: flex;
  flex-direction: column;
}
.pe-body {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.pe-footer {
  display: flex;
  justify-content: flex-end;
  margin-top: 8px;
}
.pe-btn-save {
  border: 1px solid darkslategray;
  border-radius: 6px;
  background: darkslategray;
  color: white;
  padding: 6px 16px;
  cursor: pointer;
  font-weight: bold;
}
.pe-btn-save:disabled {
  background: lightgray;
  border-color: lightgray;
  cursor: default;
}
.pe-error {
  color: red;
  font-size: small;
  margin-bottom: 6px;
}
.label-and-input {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 4px;
}
.label {
  min-width: 120px;
  max-width: 120px;
  text-align: right;
  font-size: small;
}
.input-field {
  box-sizing: border-box;
  border: 1px solid #767676;
  border-radius: 5px;
  padding: 1px 4px;
  font: inherit;
  background-color: white;
  width: 300px;
}
.input-checkbox {
  width: 20px;
  height: 20px;
}
.btn-round {
  border: thin solid black;
  border-radius: 50%;
  background: transparent;
  cursor: pointer;
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.btn-round:disabled {
  opacity: 0.3;
  cursor: default;
}
.icon-undo {
  width: 16px;
  height: 16px;
}
</style>
