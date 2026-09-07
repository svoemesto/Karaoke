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
    <div class="processes-bv-table-header">
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
    </div>
  </div>
</template>

<script>
import { BPagination, BSpinner, BTable } from 'bootstrap-vue-next'
import ProcessesFilterModal from '../../components/Processes/filter/ProcessesFilterModal.vue'
import ProcessEditModal from '../../components/Processes/edit/ProcessEditModal.vue'
import ProcessDeleteModal from '../../components/Processes/delete/ProcessDeleteModal.vue'
import ProcessAuditModal from '../../components/Processes/audit/ProcessAuditModal.vue'

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
    BPagination,
    BSpinner,
    BTable,
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
    }
  },
  computed: {
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
  },
  watch: {
    currentPage: {
      handler() {
        this.loadPage()
      },
    },
  },
  mounted() {
    this.loadPage()
  },
  methods: {
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
      this.$store.dispatch('loadProcesses', this.buildFilters())
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
</style>
