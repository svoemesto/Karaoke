# Contract: webvue3 Bulk Store Actions (Vuex)

**Created**: 2026-09-08 | **Spec**: `specs/319-process-bulk-actions-v2/spec.md`

Vuex-стор для bulk-операций, расширяющий существующий
`webvue3/src/components/Processes/store.js`.

---

## State additions

```javascript
state: {
  // ... existing
  bulkSelectionIds: [],          // Array<Number> — snapshot id по текущему фильтру
  bulkSelectionTotal: 0,         // Number — total (может > ids.length из-за cap)
  bulkSelectionTimestamp: null,  // ISO String — когда сделан snapshot
  bulkOperationInProgress: false,// Boolean — флаг «операция идёт»
  bulkOperationReport: null,     // Object | null — последний BulkOperationReport
  bulkTaskStatus: null,          // Object | null — для async: { taskId, status, processedCount, ... }
}
```

## Getters

```javascript
getBulkSelectionIds: (state) => state.bulkSelectionIds,
getBulkSelectionTotal: (state) => state.bulkSelectionTotal,
getBulkSelectionTimestamp: (state) => state.bulkSelectionTimestamp,
getBulkSelectionCount: (state) => state.bulkSelectionIds.length,
getCanBulk: (state) => state.bulkSelectionIds.length >= 1,
getBulkOperationInProgress: (state) => state.bulkOperationInProgress,
getBulkOperationReport: (state) => state.bulkOperationReport,
getBulkTaskStatus: (state) => state.bulkTaskStatus,
```

## Mutations

```javascript
SET_BULK_SELECTION(state, { ids, total, timestamp }) { ... },
CLEAR_BULK_SELECTION(state) { ... },
SET_BULK_OPERATION_IN_PROGRESS(state, val) { ... },
SET_BULK_OPERATION_REPORT(state, report) { ... },
SET_BULK_TASK_STATUS(state, taskStatus) { ... },
```

## Actions

### A-1: `fetchBulkSelectionIds(ctx, filters)`

Загружает snapshot id по текущему фильтру через `GET /api/admin/processes/bulk/snapshot`.

**Параметр** `filters`: объект тех же полей, что в `loadProcesses`.

**Returns**: `{ ids, total, timestamp }`.

**Side effect**: `commit('SET_BULK_SELECTION', { ids, total, timestamp })`.

**Когда вызывается**:
- При apply фильтра в `ProcessesFilterModal`.
- При mount `ProcessesTable` (если фильтр уже применён).
- При reset фильтра.

### A-2: `bulkUpdateProcesses(ctx, { ids, field, value })`

Sync bulk-edit, ≤ 1000 процессов.

**Параметры**:
- `ids: Number[]` — массив process.id.
- `field: String` — `'priority' | 'status' | 'threadId'` (v1).
- `value: Any` — новое значение.

**Returns**: Promise → `BulkOperationReport`.

**Behavior**:
1. `commit('SET_BULK_OPERATION_IN_PROGRESS', true)`.
2. `POST /api/admin/processes/bulk-update` с body `{ ids, field, value, batchId: ctx.state.bulkOperationReport?.batchId || null }`.
3. При успехе: `commit('SET_BULK_OPERATION_REPORT', response)`, обновить `state.items` (map).
4. При ошибке: throw.
5. `commit('SET_BULK_OPERATION_IN_PROGRESS', false)`.

### A-3: `bulkDeleteProcesses(ctx, { ids })`

Sync bulk-delete, ≤ 1000 процессов.

**Параметр** `ids: Number[]`.

**Returns**: Promise → `BulkOperationReport`.

**Behavior**:
1. `commit('SET_BULK_OPERATION_IN_PROGRESS', true)`.
2. `POST /api/admin/processes/bulk-delete` с body `{ ids }`.
3. При успехе: `commit('SET_BULK_OPERATION_REPORT', response)`, удалить из `state.items`.
4. `commit('SET_BULK_OPERATION_IN_PROGRESS', false)`.

### A-4: `bulkUpdateProcessesAsync(ctx, { ids, field, value })`

Async bulk-edit для > 1000 процессов.

**Behavior**:
1. `POST /api/admin/processes/bulk-update-async`.
2. Получить `taskId`.
3. Запустить polling `dispatch('pollBulkTask', { taskId })` каждые 2 секунды.
4. По завершении — `commit('SET_BULK_OPERATION_REPORT', finalReport)`.

### A-5: `bulkDeleteProcessesAsync(ctx, { ids })`

Async bulk-delete для > 1000 процессов. Аналогично A-4.

### A-6: `pollBulkTask(ctx, { taskId })`

Polling helper для async операций.

**Behavior**:
- `GET /api/admin/tasks/{taskId}`.
- `commit('SET_BULK_TASK_STATUS', response)`.
- Если `status ∈ {RUNNING, VALIDATING}` — `setTimeout(poll, 2000)`.
- Если `status ∈ {COMPLETED, PARTIAL, FAILED}` — стоп, `commit('SET_BULK_OPERATION_REPORT', response)`.

### A-7: `clearBulkOperationReport(ctx)`

Сбрасывает отчёт после показа админу.

---

## Component integration

### `ProcessesTable.vue`

```javascript
computed: {
  bulkSelectionCount() { return this.$store.getters.getBulkSelectionCount },
  canBulk() { return this.$store.getters.getCanBulk },
},

methods: {
  onFilterApplied(filters) {
    this.$store.dispatch('fetchBulkSelectionIds', filters)
  },
  onBulkUpdate() { this.isBulkUpdateModalVisible = true },
  onBulkDelete() { this.isBulkDeleteModalVisible = true },
},
```

Template (над таблицей):
```vue
<div class="bulk-actions-bar">
  <span>Отобрано: {{ bulkSelectionCount }}</span>
  <b-button :disabled="!canBulk" @click="onBulkUpdate">
    Изменить поле
  </b-button>
  <b-button :disabled="!canBulk" @click="onBulkDelete" variant="danger">
    Удалить
  </b-button>
  <b-tooltip v-if="!canBulk" target="bulk-actions">
    Нет процессов в выборке
  </b-tooltip>
</div>

<ProcessesBulkUpdateModal
  v-if="isBulkUpdateModalVisible"
  :ids="bulkSelectionIds"
  @close="isBulkUpdateModalVisible = false"
/>
```

### `ProcessesBulkUpdateModal.vue`

```javascript
data() {
  return {
    field: 'priority',
    value: null,
    confirmParams: null,
  }
},
computed: {
  isValid() {
    if (this.field === 'priority') return Number.isInteger(this.value) && this.value >= 0
    if (this.field === 'status') return ['CREATING','WAITING','WORKING','DONE','ERROR'].includes(this.value)
    if (this.field === 'threadId') return Number.isInteger(this.value)
    return false
  },
},
methods: {
  apply() {
    if (!this.isValid) { /* show error */ return }
    this.confirmParams = {
      message: `Будет изменено: ${this.ids.length} процессов, поле ${this.field}, новое значение ${this.value}.`,
      onConfirm: () => this.$store.dispatch('bulkUpdateProcesses', { ids: this.ids, field: this.field, value: this.value }),
    }
    this.isConfirmVisible = true
  },
},
```

### `ProcessesBulkDeleteModal.vue` (или inline custom-confirm)

```javascript
methods: {
  apply() {
    this.confirmParams = {
      message: `Будет удалено ${this.ids.length} процессов. Действие необратимо.`,
      onConfirm: () => this.$store.dispatch('bulkDeleteProcesses', { ids: this.ids }),
    }
    this.isConfirmVisible = true
  },
},
```

---

## Reference

- Existing store: `webvue3/src/components/Processes/store.js`
- SongsTable bulk pattern: `webvue3/src/components/Songs/SongsTable.vue` + `store.js`
- custom-confirm: `webvue3/src/components/Common/CustomConfirm.vue`
- Spec: `specs/319-process-bulk-actions-v2/spec.md`
- REST API: `specs/319-process-bulk-actions-v2/contracts/admin-process-bulk-rest-api.md`
- Data model: `specs/319-process-bulk-actions-v2/data-model.md`
