<template>
  <transition name="modal-fade">
    <div class="pbrm-modal-backdrop">
      <div class="pbrm-area">
        <div class="pbrm-area-modal-header">Отчёт о bulk-операции</div>
        <div class="pbrm-area-modal-body">
          <div v-if="report" class="pbrm-summary">
            <div>
              <strong>batch_id:</strong> <code>{{ report.batchId }}</code>
            </div>
            <div>
              <strong>action:</strong> <code>{{ report.action }}</code>
            </div>
            <div><strong>запрошено:</strong> {{ report.requested }}</div>
            <div><strong>успешно:</strong> {{ report.succeeded }}</div>
            <div v-if="report.failed > 0"><strong>ошибок:</strong> {{ report.failed }}</div>
            <div><strong>длительность:</strong> {{ report.durationMs }} мс</div>
          </div>

          <div v-if="report && report.errors && report.errors.length > 0" class="pbrm-errors">
            <h6>Ошибки:</h6>
            <ul>
              <li v-for="err in report.errors" :key="err.processId">
                process <code>{{ err.processId }}</code
                >: {{ err.reason }}
                <span v-if="err.errorCode" class="pbrm-error-code">[{{ err.errorCode }}]</span>
              </li>
            </ul>
          </div>

          <div v-if="report" class="pbrm-success-info" data-testid="pbrm-success">
            Операция <code>{{ report.action }}</code> завершена. Успешно: {{ report.succeeded }} из
            {{ report.requested }}.
          </div>

          <div class="pbrm-actions">
            <button type="button" class="pbrm-btn-download" @click="downloadCsv">
              Скачать CSV
            </button>
            <button type="button" class="pbrm-btn-close" @click="close">Закрыть</button>
          </div>
        </div>
      </div>
    </div>
  </transition>
</template>

<script>
/**
 * Модалка отчёта о bulk-операции (specs/319-process-bulk-actions-v2, US3).
 *
 * Показывает `BulkOperationReport` (batchId, summary, errors), даёт скачать CSV.
 *
 * @see specs/319-process-bulk-actions-v2/spec.md (US3)
 * @see specs/319-process-bulk-actions-v2/contracts/admin-process-bulk-rest-api.md
 */
export default {
  name: 'ProcessBulkReportModal',
  emits: ['close'],
  computed: {
    report() {
      return this.$store.getters.getBulkOperationReport
    },
  },
  methods: {
    close() {
      this.$store.dispatch('clearBulkOperationReport')
      this.$emit('close')
    },
    downloadCsv() {
      if (!this.report) return
      const rows = []
      rows.push(['batch_id', 'action', 'requested', 'succeeded', 'failed', 'duration_ms'])
      rows.push([
        this.report.batchId,
        this.report.action,
        this.report.requested,
        this.report.succeeded,
        this.report.failed,
        this.report.durationMs,
      ])
      if (this.report.errors && this.report.errors.length > 0) {
        rows.push([])
        rows.push(['error_process_id', 'error_reason', 'error_code'])
        for (const err of this.report.errors) {
          rows.push([err.processId, err.reason, err.errorCode || ''])
        }
      }
      const csv = rows.map((r) => r.map(escapeCsvCell).join(',')).join('\n')
      const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' })
      const url = URL.createObjectURL(blob)
      const filename = `process-bulk-${this.report.action}-${this.report.batchId}.csv`
      const a = document.createElement('a')
      a.href = url
      a.download = filename
      document.body.appendChild(a)
      a.click()
      document.body.removeChild(a)
      URL.revokeObjectURL(url)
    },
  },
}

function escapeCsvCell(value) {
  if (value === null || value === undefined) return ''
  const s = String(value)
  if (/[",\n]/.test(s)) {
    return '"' + s.replace(/"/g, '""') + '"'
  }
  return s
}
</script>

<style scoped>
.pbrm-modal-backdrop {
  position: fixed;
  top: 0;
  left: 0;
  width: 100vw;
  height: 100vh;
  background: rgba(0, 0, 0, 0.5);
  z-index: 1060;
  display: flex;
  align-items: center;
  justify-content: center;
}
.pbrm-area {
  background: var(--bs-body-bg, #fff);
  border-radius: 6px;
  min-width: 520px;
  max-width: 720px;
  max-height: 80vh;
  display: flex;
  flex-direction: column;
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.25);
}
.pbrm-area-modal-header {
  padding: 12px 16px;
  font-weight: 600;
  border-bottom: 1px solid var(--bs-border-color, #dee2e6);
}
.pbrm-area-modal-body {
  padding: 16px;
  overflow-y: auto;
}
.pbrm-summary div {
  margin-bottom: 4px;
  font-size: 0.95rem;
}
.pbrm-errors {
  margin: 12px 0;
  padding: 8px 12px;
  background: var(--bs-danger-bg-subtle, #f8d7da);
  border-radius: 4px;
  font-size: 0.9rem;
}
.pbrm-errors ul {
  margin: 4px 0 0 0;
  padding-left: 20px;
}
.pbrm-error-code {
  color: var(--bs-secondary, #6c757d);
  margin-left: 4px;
}
.pbrm-success-info {
  padding: 8px 12px;
  background: var(--bs-success-bg-subtle, #d1e7dd);
  border-radius: 4px;
  font-size: 0.95rem;
  margin: 12px 0;
}
.pbrm-actions {
  display: flex;
  gap: 8px;
  margin-top: 16px;
  justify-content: flex-end;
}
.pbrm-btn-close,
.pbrm-btn-download {
  padding: 6px 14px;
  border: 1px solid var(--bs-border-color, #dee2e6);
  border-radius: 4px;
  cursor: pointer;
  background: var(--bs-body-bg, #fff);
}
.pbrm-btn-download {
  background: var(--bs-primary, #0d6efd);
  color: #fff;
  border-color: var(--bs-primary, #0d6efd);
}
.modal-fade-enter-active,
.modal-fade-leave-active {
  transition: opacity 0.2s ease;
}
.modal-fade-enter,
.modal-fade-leave-to {
  opacity: 0;
}
</style>
