<template>
  <transition name="modal-fade">
    <div class="pbum-modal-backdrop">
      <div class="pbum-area">
        <div class="pbum-area-modal-header">
          Массовое изменение поля — {{ ids.length }} процессов
        </div>

        <div class="pbum-area-modal-body">
          <custom-confirm
            v-if="isCustomConfirmVisible"
            :params="customConfirmParams"
            @close="closeCustomConfirm"
          />
          <template v-if="!isCustomConfirmVisible">
            <div class="pbum-field-row">
              <label class="pbum-label">Поле для изменения:</label>
              <select v-model="form.field" class="form-select" data-testid="pbum-field-select">
                <option value="priority">priority (приоритет)</option>
                <option value="status">status (статус)</option>
                <option value="threadId">threadId (lane)</option>
              </select>
            </div>

            <div class="pbum-field-row">
              <label class="pbum-label">Новое значение:</label>
              <input
                v-if="form.field === 'priority' || form.field === 'threadId'"
                v-model.number="form.value"
                type="number"
                class="form-control"
                data-testid="pbum-value-input"
              />
              <select v-else-if="form.field === 'status'" v-model="form.value" class="form-select">
                <option v-for="opt in statusOptions" :key="opt" :value="opt">
                  {{ opt }}
                </option>
              </select>
            </div>

            <div class="pbum-preview" data-testid="pbum-preview">
              Будет изменено: <strong>{{ ids.length }}</strong> процессов, поле
              <strong>{{ form.field }}</strong
              >, новое значение <strong>{{ displayValue }}</strong
              >.
            </div>

            <div v-if="error" class="pbum-error">{{ error }}</div>
          </template>
        </div>

        <div class="pbum-area-modal-footer">
          <button type="button" class="pbum-btn-apply" :disabled="!canApply" @click="apply">
            Применить
          </button>
          <button type="button" class="pbum-btn-cancel" @click="cancel">Отмена</button>
        </div>
      </div>
    </div>
  </transition>
</template>

<script>
import CustomConfirm from '../Common/CustomConfirm.vue'

/**
 * Модалка «Массовое изменение поля» (specs/319-process-bulk-actions-v2, US1).
 *
 * Inline-форма по конвенции `SmartCopyModal.vue` (single-file modal с custom-confirm
 * для финального подтверждения). Lesson: разделение на Modal+Form опционально и для
 * простых форм избыточно — следуем существующему паттерну в проекте.
 *
 * @see specs/319-process-bulk-actions-v2/spec.md (US1)
 * @see specs/319-process-bulk-actions-v2/contracts/webvue3-bulk-store-actions.md
 */
export default {
  name: 'ProcessesBulkUpdateModal',
  components: { CustomConfirm },
  props: {
    ids: { type: Array, required: true },
  },
  emits: ['close'],
  data() {
    return {
      form: {
        field: 'priority',
        value: 0,
      },
      statusOptions: ['CREATING', 'WAITING', 'WORKING', 'DONE', 'ERROR'],
      isCustomConfirmVisible: false,
      customConfirmParams: null,
      error: null,
    }
  },
  computed: {
    /**
     * Кнопка «Применить» активна, если:
     * - есть хотя бы 1 id;
     * - значение валидно для выбранного поля.
     */
    canApply() {
      if (!this.ids.length) return false
      const { field, value } = this.form
      // Priority допускает отрицательные значения (WP #68 явно просит "приоритет с 0 на -1",
      // пример в задаче). Integer — единственное ограничение.
      if (field === 'priority') return Number.isInteger(value)
      if (field === 'threadId') return Number.isInteger(value)
      if (field === 'status') return this.statusOptions.includes(value)
      return false
    },
    displayValue() {
      return this.form.value === '' || this.form.value === null ? '∅' : this.form.value
    },
  },
  watch: {
    'form.field'(newField) {
      // Сброс значения к разумному default при смене поля.
      if (newField === 'priority' || newField === 'threadId') this.form.value = 0
      else if (newField === 'status') this.form.value = 'WAITING'
    },
  },
  methods: {
    apply() {
      this.error = null
      if (!this.ids.length) {
        this.error = 'Нет процессов для изменения (выборка пуста)'
        return
      }
      if (!this.canApply) {
        this.error = 'Заполните значение корректно'
        return
      }
      this.customConfirmParams = {
        isAlert: false,
        header: 'Подтвердите изменение',
        body: `Будет изменено <strong>${this.ids.length}</strong> процессов: <strong>${this.form.field}</strong> → <strong>${this.form.value}</strong>.`,
        callback: () => this.execute(),
      }
      this.isCustomConfirmVisible = true
    },
    execute() {
      this.isCustomConfirmVisible = false
      const ids = this.ids
      const { field, value } = this.form
      const dispatchAction = ids.length > 1000 ? 'bulkUpdateProcessesAsync' : 'bulkUpdateProcesses'
      this.$store
        .dispatch(dispatchAction, { ids, field, value })
        .then(() => {
          this.$emit('close')
        })
        .catch((err) => {
          this.error = err && err.message ? err.message : 'Bulk update failed'
          this.isCustomConfirmVisible = false
        })
    },
    closeCustomConfirm() {
      this.isCustomConfirmVisible = false
    },
    cancel() {
      this.$emit('close')
    },
  },
}
</script>

<style scoped>
.pbum-modal-fade-enter,
.pbum-modal-fade-leave-active {
  opacity: 0;
}
.pbum-modal-fade-enter-active,
.pbum-modal-fade-leave-active {
  transition: opacity 0.5s ease;
}

.pbum-modal-backdrop {
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
.pbum-area {
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
  min-width: 480px;
}
.pbum-area-modal-header {
  background-color: darkslategray;
  padding: 10px;
  color: white;
  font-size: larger;
  font-weight: 300;
}
.pbum-area-modal-body {
  background-color: white;
  padding: 10px;
  color: black;
  font-size: larger;
  font-weight: 300;
}
.pbum-area-modal-footer {
  background-color: darkslategray;
  padding: 10px;
  color: white;
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
.pbum-btn-apply,
.pbum-btn-cancel {
  border: 1px solid white;
  background: darkslategray;
  color: white;
  padding: 4px 14px;
  cursor: pointer;
  font-size: 1rem;
}
.pbum-btn-apply:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.pbum-field-row {
  display: flex;
  flex-direction: column;
  margin-bottom: 12px;
}
.pbum-label {
  font-size: 0.85rem;
  margin-bottom: 4px;
  color: #444;
}
.pbum-preview {
  padding: 8px 12px;
  background: #f4f6f8;
  border-radius: 4px;
  margin: 12px 0;
  font-size: 0.95rem;
  color: #333;
}
.pbum-error {
  margin-top: 8px;
  color: #b00020;
  background: #fff0f0;
  padding: 6px 10px;
  border-radius: 4px;
  font-size: 0.95rem;
}
</style>
