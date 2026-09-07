<template>
  <transition name="modal-fade">
    <div class="pam-modal-backdrop">
      <div class="pam-area">
        <div class="pam-area-modal-header">Audit процесса</div>
        <div class="pam-area-modal-body">
          <div class="pam-toolbar">
            <span class="pam-days-label">Период (дней):</span>
            <input v-model.number="days" type="number" min="1" max="30" class="pam-days-input" />
            <button type="button" class="pam-btn-reload" @click="reload">Обновить</button>
          </div>
          <div v-if="error" class="pam-error" v-text="error" />
          <table class="pam-table">
            <thead>
              <tr>
                <th>ID</th>
                <th>Действие</th>
                <th>Актор</th>
                <th>Время</th>
                <th>Было</th>
                <th>Стало</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in auditItems" :key="row.id">
                <td v-text="row.id" />
                <td v-text="row.action" />
                <td v-text="row.actor" />
                <td v-text="formatTimestamp(row.createdAt)" />
                <td class="pam-json" v-text="formatJson(row.oldValue)" />
                <td class="pam-json" v-text="formatJson(row.newValue)" />
              </tr>
              <tr v-if="auditItems.length === 0">
                <td colspan="6" class="pam-empty">Нет записей за выбранный период</td>
              </tr>
            </tbody>
          </table>
        </div>
        <div class="pam-area-modal-footer">
          <button type="button" class="pam-btn-close" @click="close">Выход</button>
        </div>
      </div>
    </div>
  </transition>
</template>

<script>
/**
 * Модалка audit-лога процесса (specs/315-admin-ui-karaoke-process-v5, US5).
 *
 * Read-only таблица записей (id, action, actor, createdAt, oldValue, newValue JSONB).
 * Период по умолчанию 30 дней (FR-018). Bootstrap-vue-next стили.
 *
 * @see specs/315-admin-ui-karaoke-process-v5/spec.md
 */
export default {
  name: 'ProcessAuditModal',
  props: {
    processId: {
      type: Number,
      required: true,
    },
  },
  data() {
    return {
      days: 30,
      error: null,
    }
  },
  computed: {
    auditItems() {
      return this.$store.getters.getCurrentProcessAudit
    },
  },
  async beforeMount() {
    await this.reload()
  },
  methods: {
    async reload() {
      this.error = null
      try {
        await this.$store.dispatch('loadAudit', { id: this.processId, days: this.days })
      } catch (e) {
        this.showError(e)
      }
    },
    close() {
      this.$emit('close')
    },
    formatTimestamp(value) {
      if (!value) return ''
      const d = new Date(value)
      if (isNaN(d.getTime())) return String(value)
      const pad = (n) => String(n).padStart(2, '0')
      return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
    },
    formatJson(value) {
      if (!value) return ''
      try {
        return JSON.stringify(value)
      } catch (_) {
        return String(value)
      }
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
.pam-modal-fade-enter,
.pam-modal-fade-leave-active {
  opacity: 0;
}
.pam-modal-fade-enter-active,
.pam-modal-fade-leave-active {
  transition: opacity 0.5s ease;
}

.pam-area-modal-header {
  background-color: darkslategray;
  padding: 10px;
  color: white;
  font-size: larger;
  font-weight: 300;
}
.pam-area-modal-body {
  background-color: white;
  padding: 10px;
  color: black;
  font-size: larger;
  font-weight: 300;
}
.pam-area-modal-footer {
  background-color: darkslategray;
  padding: 10px;
  color: white;
  display: flex;
  justify-content: flex-end;
}
.pam-modal-backdrop {
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
.pam-area {
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
.pam-btn-close {
  border: 1px solid white;
  border-radius: 10px;
  font-size: 20px;
  cursor: pointer;
  font-weight: bold;
  color: white;
  background: transparent;
  width: 100px;
}
.pam-toolbar {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  font-size: small;
}
.pam-days-input {
  width: 60px;
  border: 1px solid #767676;
  border-radius: 5px;
  padding: 1px 4px;
}
.pam-btn-reload {
  border: 1px solid darkslategray;
  border-radius: 6px;
  background: darkslategray;
  color: white;
  padding: 2px 10px;
  cursor: pointer;
  font-size: small;
}
.pam-table {
  border-collapse: collapse;
  font-size: small;
  width: 100%;
}
.pam-table th,
.pam-table td {
  border: 1px solid #ccc;
  padding: 3px 6px;
  text-align: left;
  vertical-align: top;
}
.pam-table th {
  background-color: #f0f0f0;
}
.pam-json {
  font-family: monospace;
  font-size: x-small;
  white-space: pre-wrap;
  word-break: break-all;
}
.pam-empty {
  text-align: center;
  color: #999;
}
.pam-error {
  color: red;
  font-size: small;
  margin-bottom: 6px;
}
</style>
