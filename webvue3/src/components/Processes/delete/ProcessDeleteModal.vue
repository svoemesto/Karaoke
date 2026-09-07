<template>
  <transition name="modal-fade">
    <div class="pdm-modal-backdrop">
      <div class="pdm-area">
        <div class="pdm-area-modal-header">Удаление процесса</div>
        <div class="pdm-area-modal-body">
          <div v-if="process">
            <div class="pdm-info">
              Удалить процесс <b v-text="process.name" /> (id=<span v-text="process.id" />, статус:
              <span v-text="process.status" />)?
            </div>
            <div v-if="process.status === 'WORKING'" class="pdm-warning">
              ⚠️ Процесс выполняется (WORKING). Удаление прервёт поток (interrupt), подождёт 5
              секунд и принудительно завершит OS-процесс (destroyForcibly).
            </div>
            <div
              v-if="process.status === 'WAITING' || process.status === 'CREATING'"
              class="pdm-warning"
            >
              Процесс в очереди (WAITING/CREATING) — будет отменён и soft-deleted.
            </div>
            <div v-if="error" class="pdm-error" v-text="error" />
          </div>
          <div v-else class="pdm-loading">Загрузка...</div>
        </div>
        <div class="pdm-area-modal-footer">
          <button type="button" class="pdm-btn-danger" :disabled="!process" @click="confirmDelete">
            Удалить
          </button>
          <button type="button" class="pdm-btn-close" @click="close">Отмена</button>
        </div>
      </div>
    </div>
  </transition>
</template>

<script>
/**
 * Модалка подтверждения удаления процесса (specs/315-admin-ui-karaoke-process-v5, US3).
 *
 * Показывает информацию о процессе + предупреждение для WORKING (interrupt + 5 сек grace +
 * destroyForcibly). Действие → store.deleteProcess(id) → emit close.
 *
 * @see specs/315-admin-ui-karaoke-process-v5/spec.md
 */
export default {
  name: 'ProcessDeleteModal',
  props: {
    processId: {
      type: Number,
      required: true,
    },
  },
  data() {
    return {
      process: null,
      error: null,
    }
  },
  async beforeMount() {
    try {
      this.process = await this.$store.dispatch('loadProcessForEdit', this.processId)
    } catch (e) {
      this.showError(e)
    }
  },
  methods: {
    confirmDelete() {
      this.error = null
      this.$store
        .dispatch('deleteProcess', this.processId)
        .then(() => {
          this.$emit('close')
        })
        .catch((e) => {
          this.showError(e)
        })
    },
    close() {
      this.$emit('close')
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
.pdm-modal-fade-enter,
.pdm-modal-fade-leave-active {
  opacity: 0;
}
.pdm-modal-fade-enter-active,
.pdm-modal-fade-leave-active {
  transition: opacity 0.5s ease;
}

.pdm-area-modal-header {
  background-color: darkslategray;
  padding: 10px;
  color: white;
  font-size: larger;
  font-weight: 300;
}
.pdm-area-modal-body {
  background-color: white;
  padding: 10px;
  color: black;
  font-size: larger;
  font-weight: 300;
}
.pdm-area-modal-footer {
  background-color: darkslategray;
  padding: 10px;
  color: white;
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
.pdm-modal-backdrop {
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
.pdm-area {
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
.pdm-btn-close {
  border: 1px solid white;
  border-radius: 10px;
  font-size: 16px;
  cursor: pointer;
  font-weight: bold;
  color: white;
  background: transparent;
  width: 100px;
}
.pdm-btn-danger {
  border: 1px solid white;
  border-radius: 10px;
  font-size: 16px;
  cursor: pointer;
  font-weight: bold;
  color: white;
  background: indianred;
  width: 100px;
}
.pdm-btn-danger:disabled {
  background: lightgray;
  cursor: default;
}
.pdm-info {
  font-size: small;
  margin-bottom: 6px;
}
.pdm-warning {
  font-size: small;
  color: #b8860b;
  margin-bottom: 6px;
}
.pdm-error {
  color: red;
  font-size: small;
  margin-top: 6px;
}
.pdm-loading {
  font-size: small;
}
</style>
