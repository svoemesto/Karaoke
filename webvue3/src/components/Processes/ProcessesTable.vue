<template>
  <div class="processes-bv-table">
    <ProcessesFilterModal v-if="isProcessesFilterVisible" @close="closeProcessesFilter" />
    <ProcessEditModal
      v-if="isProcessEditVisible"
      :process-id="currentProcessForEditId"
      @close="closeProcessEdit"
    />
    <ProcessDeleteModal
      v-if="isProcessDeleteVisible"
      :process-id="currentProcessForDeleteId"
      @close="closeProcessDelete"
    />
    <ProcessAuditModal
      v-if="isProcessAuditVisible"
      :process-id="currentProcessForAuditId"
      @close="closeProcessAudit"
    />
    <ProcessesBulkUpdateModal
      v-if="isBulkUpdateModalVisible"
      :ids="effectiveBulkIds"
      @close="closeBulkUpdate"
    />
    <ProcessBulkReportModal v-if="hasBulkReport" @close="closeBulkReport" />
    <div class="processes-bv-table-header">
      <b-form-input
        :id="`rows-per-page-processes`"
        type="number"
        min="1"
        max="1000"
        size="sm"
        style="width: 65px"
        :model-value="perPage"
        :disabled="isSavingRowsPerPage"
        @change="onPerPageChange($event)"
      />
      <b-spinner v-if="isSavingRowsPerPage" small />
      <b-pagination
        v-model="currentPage"
        :total-rows="total"
        :per-page="perPage"
        :limit="30"
        size="sm"
        pills
      />
    </div>
    <div class="processes-bv-table-body">
      <b-table
        :items="displayItems"
        :busy="processesLoading"
        :fields="processFields"
        :per-page="100000"
        small
        bordered
        hover
      >
        <template #table-busy>
          <div class="text-center text-danger my-2">
            <b-spinner class="align-middle" />
            <strong>Loading...</strong>
          </div>
        </template>
        <template #table-colgroup="scope">
          <col v-for="field in scope.fields" :key="field.key" :style="field.style" />
        </template>

        <template #cell(id)="data">
          <div class="fld-id" v-text="data.value" />
        </template>

        <template #cell(threadId)="data">
          <div class="fld-thread-id" v-text="data.value" />
        </template>

        <template #cell(name)="data">
          <div
            class="fld-process-name"
            :style="{ paddingLeft: (data.item._depth || 0) * 20 + 'px' }"
          >
            <button
              v-if="data.item._isHead"
              class="btn-expand"
              :title="isExpanded(data.item.id) ? 'Свернуть' : 'Развернуть'"
              @click="toggleExpand(data.item.id)"
              v-text="isExpanded(data.item.id) ? '▼' : '▶'"
            />
            <span
              v-if="data.item.processDeletedAt"
              class="fld-deleted-badge"
              title="Процесс удалён (soft-delete)"
              v-text="'🗑️'"
            />
            <span :class="{ 'fld-deleted-text': data.item.processDeletedAt }" v-text="data.value" />
          </div>
        </template>

        <template #cell(status)="data">
          <div class="fld-status" v-text="data.value" />
        </template>

        <template #cell(priority)="data">
          <div class="fld-priority" v-text="data.value" />
        </template>

        <template #cell(description)="data">
          <div class="fld-description" v-text="data.value" />
        </template>

        <template #cell(type)="data">
          <div class="fld-type" v-text="data.value" />
        </template>

        <template #cell(startedAt)="data">
          <div class="fld-start" v-text="formatTimestamp(data.value)" />
        </template>

        <template #cell(endedAt)="data">
          <div class="fld-end" v-text="formatTimestamp(data.value)" />
        </template>

        <template #cell(updatedAt)="data">
          <div class="fld-updated" v-text="formatTimestamp(data.value)" />
        </template>

        <template #cell(actions)="data">
          <div class="fld-actions">
            <button
              class="btn-action"
              title="Редактировать"
              @click="openEdit(data.item.id)"
              v-text="'✏️'"
            />
            <button
              class="btn-action"
              title="Удалить"
              @click="openDelete(data.item.id)"
              v-text="'🗑️'"
            />
            <button
              v-if="canRetry(data.item)"
              class="btn-action"
              title="Retry (ERROR → WAITING)"
              @click="openRetry(data.item.id)"
              v-text="'🔄'"
            />
            <button
              class="btn-action"
              title="Audit"
              @click="openAudit(data.item.id)"
              v-text="'📋'"
            />
          </div>
        </template>
      </b-table>
    </div>
    <div class="processes-bv-table-footer">
      <button class="btn-round-double" title="Фильтр" @click="isProcessesFilterVisible = true">
        <img alt="filter" class="icon-40" src="../../assets/svg/icon_filter.svg" />
      </button>
      <button
        id="bulk-update-btn"
        class="btn-round-double"
        title="Массовое изменение поля для всех процессов из текущей выборки"
        :disabled="!canBulk || bulkOperationInProgress"
        @click="openBulkUpdate"
      >
        <img alt="bulk update field" class="icon-40" src="../../assets/svg/icon_edit.svg" />
      </button>
      <button
        id="bulk-delete-btn"
        class="btn-round-double"
        title="Массовое удаление всех процессов из текущей выборки"
        :disabled="!canBulk || bulkOperationInProgress"
        @click="confirmBulkDelete"
      >
        <img alt="bulk delete" class="icon-40" src="../../assets/svg/icon_delete.svg" />
      </button>
      <b-spinner v-if="bulkOperationInProgress" small class="ml-2" />
    </div>
  </div>
</template>

<script>
import { BPagination, BSpinner, BTable, BFormInput } from 'bootstrap-vue-next'
import ProcessesFilterModal from '../../components/Processes/filter/ProcessesFilterModal.vue'
import ProcessEditModal from '../../components/Processes/edit/ProcessEditModal.vue'
import ProcessDeleteModal from '../../components/Processes/delete/ProcessDeleteModal.vue'
import ProcessAuditModal from '../../components/Processes/audit/ProcessAuditModal.vue'
import ProcessesBulkUpdateModal from '../../components/Processes/ProcessesBulkUpdateModal.vue'
import ProcessBulkReportModal from '../../components/Processes/ProcessBulkReportModal.vue'

/**
 * Таблица процессов (specs/315-admin-ui-karaoke-process-v5, US1).
 *
 * Показывает top-level head-процессы (process_chain_id IS NULL), фильтрует по
 * статусу/типу/threadId/chainId/name/includeDeleted, разворачивает head → lazy load
 * tail-детей (FR-004). Soft-deleted процессы — зачёркнутый текст + 🗑️ (FR-002).
 *
 * @see specs/315-admin-ui-karaoke-process-v5/spec.md
 */
export default {
  name: 'ProcessesTable',
  components: {
    ProcessesFilterModal,
    ProcessEditModal,
    ProcessDeleteModal,
    ProcessAuditModal,
    ProcessesBulkUpdateModal,
    ProcessBulkReportModal,
    BPagination,
    BSpinner,
    BTable,
    BFormInput,
  },
  data() {
    return {
      perPage: 50,
      currentPage: 1,
      isProcessesFilterVisible: false,
      isProcessEditVisible: false,
      currentProcessForEditId: null,
      isProcessDeleteVisible: false,
      currentProcessForDeleteId: null,
      isProcessAuditVisible: false,
      currentProcessForAuditId: null,
      // specs/319-process-bulk-actions-v2 (US1, US2): bulk-actions UI state.
      isBulkUpdateModalVisible: false,
    }
  },
  computed: {
    // specs/358-rows-per-page: состояние saving для индикатора loading.
    isSavingRowsPerPage() {
      return this.$store.getters.isSavingRowsPerPage('processes')
    },
    processesLoading() {
      return this.$store.getters.getProcessesLoading
    },
    items() {
      return this.$store.getters.getProcessesItems
    },
    total() {
      return this.$store.getters.getProcessesTotal
    },
    // Плоский список: top-level head-процессы + развёрнутые дети (lazy load).
    displayItems() {
      const result = []
      for (const item of this.items) {
        result.push(Object.assign({}, item, { _depth: 0, _isHead: true }))
        if (this.isExpanded(item.id)) {
          const children = this.getChildren(item.id)
          for (const child of children) {
            result.push(Object.assign({}, child, { _depth: 1, _isHead: false }))
          }
        }
      }
      return result
    },
    processFields() {
      return [
        {
          key: 'id',
          sortable: true,
          label: 'ID',
          style: {
            minWidth: '50px',
            maxWidth: '50px',
            textAlign: 'center',
            fontSize: 'small',
          },
        },
        {
          key: 'threadId',
          sortable: true,
          label: 'thId',
          style: {
            minWidth: '50px',
            maxWidth: '50px',
            textAlign: 'center',
            fontSize: 'small',
          },
        },
        {
          key: 'name',
          sortable: true,
          label: 'Имя',
          style: {
            minWidth: '400px',
            maxWidth: '400px',
            textAlign: 'left',
            fontSize: 'small',
          },
        },
        {
          key: 'status',
          sortable: true,
          label: 'Статус',
          style: {
            minWidth: '90px',
            maxWidth: '90px',
            textAlign: 'left',
            fontSize: 'small',
          },
        },
        {
          key: 'priority',
          sortable: true,
          label: 'Prior',
          style: {
            minWidth: '50px',
            maxWidth: '50px',
            textAlign: 'left',
            fontSize: 'small',
          },
        },
        {
          key: 'description',
          sortable: true,
          label: 'Описание',
          style: {
            minWidth: '200px',
            maxWidth: '200px',
            textAlign: 'left',
            fontSize: 'small',
          },
        },
        {
          key: 'type',
          sortable: true,
          label: 'Тип',
          style: {
            minWidth: '120px',
            maxWidth: '120px',
            textAlign: 'left',
            fontSize: 'small',
          },
        },
        {
          key: 'startedAt',
          sortable: true,
          label: 'Начало',
          style: {
            minWidth: '120px',
            maxWidth: '120px',
            textAlign: 'left',
            fontSize: 'small',
          },
        },
        {
          key: 'endedAt',
          sortable: true,
          label: 'Конец',
          style: {
            minWidth: '120px',
            maxWidth: '120px',
            textAlign: 'left',
            fontSize: 'small',
          },
        },
        {
          key: 'updatedAt',
          sortable: true,
          label: 'Обновлено',
          style: {
            minWidth: '120px',
            maxWidth: '120px',
            textAlign: 'left',
            fontSize: 'small',
          },
        },
        {
          key: 'actions',
          label: 'Действия',
          style: {
            minWidth: '120px',
            maxWidth: '120px',
            textAlign: 'center',
            fontSize: 'small',
          },
        },
      ]
    },
    // specs/319-process-bulk-actions-v2: bulk-action UI bindings (FR-001, FR-002).
    bulkSelectionIds() {
      return this.$store.getters.getBulkSelectionIds
    },
    bulkSelectionCount() {
      return this.$store.getters.getBulkSelectionCount
    },
    bulkOperationInProgress() {
      return this.$store.getters.getBulkOperationInProgress
    },
    /**
     * Ids для отправки в bulk-endpoint. Snapshot из server, fallback — items на текущей странице.
     */
    effectiveBulkIds() {
      return this.bulkSelectionIds.length > 0 ? this.bulkSelectionIds : this.items.map((i) => i.id)
    },
    hasBulkReport() {
      return !!this.$store.getters.getBulkOperationReport
    },
    /**
     * Кнопка bulk-action активна, когда есть процессы в фильтре.
     * Источник: snapshot ids (если загружен), иначе — fallback на текущий `total` фильтра.
     * Fallback гарантирует активность кнопок даже если endpoint /bulk/snapshot
     * не отработал (например, на самой первой загрузке до ответа бэка).
     */
    canBulk() {
      if (this.bulkSelectionCount > 0) return true
      return this.total > 0
    },
  },
  watch: {
    currentPage: {
      handler() {
        this.loadPage()
      },
    },
  },
  async mounted() {
    // specs/358-rows-per-page: загрузить настройки таблиц (один раз при старте SPA,
    // защищён флагом `loaded` в store `tableSettings`). Важно: ждём завершения
    // ДО первой загрузки данных, чтобы limit/offset корректно передавались в backend.
    await this.$store.dispatch('loadTableSettings')
    this.perPage = this.$store.getters.getRowsPerPage('processes')
    this.loadPage()
  },
  methods: {
    /**
     * specs/358-rows-per-page: обработчик изменения поля «Строк на странице».
     * Парсит значение, валидирует диапазон, отправляет в backend, обновляет UI
     * только после успешного ответа (без оптимистичного обновления — см.
     * Clarifications Q3 spec.md).
     *
     * @param {string|number} newValue
     */
    async onPerPageChange(e) {
      // Bootstrap-vue-next `<b-form-input>` в нативном режиме передаёт в @change Event,
      // а не значение. Извлекаем value из target.
      const rawValue = e && e.target ? e.target.value : e
      const parsed = parseInt(rawValue, 10)
      if (isNaN(parsed) || parsed < 1 || parsed > 1000) {
        // eslint-disable-next-line no-console
        console.warn('[Processes.onPerPageChange] invalid value', rawValue)
        return
      }
      if (parsed === this.perPage) return
      this.currentPage = 1 // ADR-0004: page reset
      const ok = await this.$store.dispatch('setRowsPerPage', {
        tableKey: 'processes',
        value: parsed,
      })
      if (ok) {
        this.perPage = this.$store.getters.getRowsPerPage('processes')
        // specs/358-rows-per-page: backend `/api/admin/processes` принимает
        // `limit` (= perPage) и `offset` (= (page - 1) * perPage). Преобразуем
        // page+perPage в limit+offset.
        await this.$store.dispatch('loadProcesses', {
          ...this.buildFilters(),
          limit: this.perPage,
          offset: (this.currentPage - 1) * this.perPage,
        })
      }
    },
    buildFilters() {
      return {
        status: this.$store.getters.getProcessesFilterStatus,
        type: this.$store.getters.getProcessesFilterType,
        threadId: this.$store.getters.getProcessesFilterThreadId || null,
        chainId: this.$store.getters.getProcessesFilterChainId || null,
        includeDeleted: this.$store.getters.getProcessesFilterIncludeDeleted,
        name: this.$store.getters.getProcessesFilterName || null,
        limit: this.perPage,
        offset: (this.currentPage - 1) * this.perPage,
      }
    },
    loadPage() {
      const filters = this.buildFilters()
      this.$store.dispatch('loadProcesses', filters)
      // specs/319-process-bulk-actions-v2 (FR-002, FR-004): обновить snapshot для bulk-операций.
      this.$store.dispatch('fetchBulkSelectionIds', filters)
    },
    isExpanded(id) {
      return this.$store.getters.isProcessExpanded(id)
    },
    getChildren(parentId) {
      return this.$store.getters.getProcessChildren(parentId)
    },
    toggleExpand(id) {
      const wasExpanded = this.isExpanded(id)
      this.$store.dispatch('toggleProcessExpanded', id)
      if (!wasExpanded) {
        this.$store.dispatch('loadChildren', id)
      }
    },
    formatTimestamp(value) {
      if (!value) return ''
      const d = new Date(value)
      if (isNaN(d.getTime())) return String(value)
      const pad = (n) => String(n).padStart(2, '0')
      return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
    },
    closeProcessesFilter() {
      this.isProcessesFilterVisible = false
    },
    // specs/319-process-bulk-actions-v2: bulk handlers (T014, T016, T021).
    openBulkUpdate() {
      if (!this.canBulk || this.bulkOperationInProgress) return
      this.isBulkUpdateModalVisible = true
    },
    closeBulkUpdate() {
      this.isBulkUpdateModalVisible = false
      // После завершения операции отчёт уже в сторе; пользователь закроет
      // его отдельной кнопкой в ProcessBulkReportModal (T025).
    },
    confirmBulkDelete() {
      if (!this.canBulk || this.bulkOperationInProgress) return
      const ids = this.effectiveBulkIds
      const total = ids.length
      // eslint-disable-next-line no-alert
      const ok = window.confirm(`Будет удалено ${total} процессов. Действие необратимо.`)
      if (!ok) return
      this.$store
        .dispatch(total > 1000 ? 'bulkDeleteProcessesAsync' : 'bulkDeleteProcesses', { ids })
        .catch((err) => {
          // eslint-disable-next-line no-console
          console.error('Bulk delete failed:', err)
        })
    },
    closeBulkReport() {
      // Report modal сам вызывает clearBulkOperationReport при закрытии.
    },
    openEdit(id) {
      this.currentProcessForEditId = id
      this.isProcessEditVisible = true
    },
    closeProcessEdit() {
      this.isProcessEditVisible = false
      this.currentProcessForEditId = null
    },
    openDelete(id) {
      this.currentProcessForDeleteId = id
      this.isProcessDeleteVisible = true
    },
    closeProcessDelete() {
      this.isProcessDeleteVisible = false
      this.currentProcessForDeleteId = null
    },
    openAudit(id) {
      this.currentProcessForAuditId = id
      this.isProcessAuditVisible = true
    },
    closeProcessAudit() {
      this.isProcessAuditVisible = false
      this.currentProcessForAuditId = null
    },
    // Retry виден только для ERROR + не удалённых (FR-014, T048 iter #3).
    canRetry(item) {
      return item.status === 'ERROR' && !item.processDeletedAt
    },
    openRetry(id) {
      this.$store
        .dispatch('retryProcess', id)
        .then(() => {
          this.loadPage()
        })
        .catch((e) => {
          this.showError(e)
        })
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
      // eslint-disable-next-line no-alert
      alert(message)
    },
  },
}
</script>

<style>
.processes-bv-table {
  padding: 0;
  margin: 0;
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  font-family: Avenir, Helvetica, Arial, sans-serif;
}

.processes-bv-table-header {
  width: fit-content;
  display: flex;
  align-items: center;
  gap: 10px;
}

.processes-bv-table-body {
  width: fit-content;
}
.processes-bv-table-body th {
  position: relative;
}
.processes-bv-table-body th svg.bi {
  position: absolute;
  right: 2px;
  top: 50%;
  transform: translateY(-50%);
  opacity: 0 !important;
  transition: opacity 0.15s ease;
  pointer-events: none;
}
.processes-bv-table-body th:hover svg.bi {
  opacity: 0.6 !important;
}

.processes-bv-table-footer {
  margin-top: auto;
  display: flex;
  flex-direction: row;
  align-items: center;
}

.fld-id {
  min-width: 50px;
  max-width: 50px;
  text-align: center;
  font-size: small;
  white-space: nowrap;
  overflow: hidden;
}
.fld-thread-id {
  min-width: 50px;
  max-width: 50px;
  text-align: center;
  font-size: small;
  white-space: nowrap;
  overflow: hidden;
}
.fld-process-name {
  min-width: 400px;
  max-width: 400px;
  text-align: left;
  font-size: small;
  white-space: nowrap;
  overflow: hidden;
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 4px;
}
.fld-deleted-text {
  text-decoration: line-through;
  color: #999;
}
.fld-deleted-badge {
  font-size: small;
}
.btn-expand {
  border: thin solid black;
  border-radius: 4px;
  background: transparent;
  cursor: pointer;
  font-size: x-small;
  width: 20px;
  height: 20px;
  padding: 0;
  line-height: 1;
}
.btn-expand:hover {
  background-color: lightyellow;
}
.fld-status {
  min-width: 90px;
  max-width: 90px;
  text-align: center;
  font-size: small;
  white-space: nowrap;
  overflow: hidden;
}

.fld-priority {
  min-width: 50px;
  max-width: 50px;
  text-align: center;
  font-size: small;
  white-space: nowrap;
  overflow: hidden;
}

.fld-description {
  min-width: 200px;
  max-width: 200px;
  text-align: left;
  font-size: small;
  white-space: nowrap;
  overflow: hidden;
}

.fld-type {
  min-width: 120px;
  max-width: 120px;
  text-align: center;
  font-size: small;
  white-space: nowrap;
  overflow: hidden;
}

.fld-start {
  min-width: 120px;
  max-width: 120px;
  text-align: center;
  font-size: small;
  white-space: nowrap;
  overflow: hidden;
}

.fld-end {
  min-width: 120px;
  max-width: 120px;
  text-align: center;
  font-size: small;
  white-space: nowrap;
  overflow: hidden;
}

.fld-updated {
  min-width: 120px;
  max-width: 120px;
  text-align: center;
  font-size: small;
  white-space: nowrap;
  overflow: hidden;
}

.fld-actions {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: center;
  gap: 4px;
}
.btn-action {
  border: thin solid black;
  border-radius: 4px;
  background: transparent;
  cursor: pointer;
  font-size: small;
  width: 28px;
  height: 24px;
  padding: 0;
  line-height: 1;
}
.btn-action:hover {
  background-color: lightyellow;
}
.btn-action:disabled {
  opacity: 0.3;
  cursor: default;
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

/* specs/358-rows-per-page: поле ввода «Строк на странице» — выровнено по центру
   с кнопками пагинации. Bootstrap .form-control имеет min-height через padding
   + font-size, что смещает baseline относительно .btn-sm кнопок. */
#rows-per-page-processes {
  padding: 0.25rem 0.5rem !important;
  line-height: 1.5 !important;
  height: 31px !important;
  font-size: 0.875rem !important;
  text-align: center;
  align-self: center;
}

/* specs/358-rows-per-page: убрать дефолтный margin-bottom у <b-pagination>
   внутри header-div, чтобы pagination был выровнен по центральной оси
   с input (без смещения baseline вниз). */
.processes-bv-table-header .pagination,
.processes-bv-table-header ul.pagination {
  margin-bottom: 0 !important;
}
</style>
