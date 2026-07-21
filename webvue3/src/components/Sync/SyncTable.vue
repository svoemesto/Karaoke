<template>
  <div class="sync-table-wrapper">
    <custom-confirm
      v-if="isCustomConfirmVisible"
      :params="customConfirmParams"
      @close="closeCustomConfirm"
    />
    <button class="btn btn-success sync-oneclick-button" @click="confirmOneClick">
      🔄 Синхронизация в 1 клик
    </button>
    <table class="sync-table">
      <thead>
        <tr>
          <th rowspan="2">Таблица</th>
          <th :colspan="ops.length" class="sync-group sync-group-push">→ Server (push)</th>
          <th rowspan="2" />
          <th :colspan="ops.length" class="sync-group sync-group-pull">← Local (pull)</th>
          <th rowspan="2" />
        </tr>
        <tr>
          <th v-for="op in ops" :key="'ph-' + op.op" class="sync-op-col" :title="op.title">
            {{ op.label }}
          </th>
          <th v-for="op in ops" :key="'lh-' + op.op" class="sync-op-col" :title="op.title">
            {{ op.label }}
          </th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="entity in entities" :key="entity.key">
          <td class="sync-name-col">{{ entity.displayName }}</td>
          <td v-for="op in ops" :key="'p-' + op.op" class="sync-op-col">
            <input
              type="checkbox"
              :checked="entity[fieldName('PUSH', op.op)]"
              @change="onFlagChange(entity, 'PUSH', op.op, $event)"
            />
          </td>
          <td>
            <button
              class="btn btn-sm btn-primary sync-run-button"
              :disabled="!entity.allowPush"
              @click="confirmRun(entity, 'PUSH')"
            >
              → Server
            </button>
          </td>
          <td v-for="op in ops" :key="'l-' + op.op" class="sync-op-col">
            <input
              type="checkbox"
              :checked="entity[fieldName('PULL', op.op)]"
              @change="onFlagChange(entity, 'PULL', op.op, $event)"
            />
          </td>
          <td>
            <button
              class="btn btn-sm btn-primary sync-run-button"
              :disabled="!entity.allowPull"
              @click="confirmRun(entity, 'PULL')"
            >
              ← Local
            </button>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<script>
import CustomConfirm from '../Common/CustomConfirm.vue'

/**
 * Таблица управления двух-БД sync LOCAL ↔ SERVER.
 *
 * Отображает все 14 `SyncTarget`-сущностей с 8 флагами
 * `sync_<key>_<push|pull>_<insert|update|delete|move>_allowed` для
 * каждой. UI разбит на две группы колонок: PUSH (4 чекбокса:
 * insert/update/delete/move) и PULL (аналогично).
 *
 * **Действия**:
 * - «Синхронизировать» — запуск `POST /api/sync/run` с `key` и `direction`.
 * - «1 клик» — `POST /api/sync/oneclick` для всех сущностей в направлении
 *   `oneClickDirection` каждого `SyncTarget`.
 * - «Изменить флаг» — `POST /api/sync/setflag`.
 *
 * **REST-эндпоинты** (см. `SyncController.kt`):
 * - `GET /api/sync/entities` — список сущностей с их флагами.
 * - `POST /api/sync/run` — запуск одной сущности.
 * - `POST /api/sync/oneclick` — запуск всех.
 * - `POST /api/sync/setflag` — toggle одного флага.
 *
 * **Безопасность**:
 * - `pull_move` для append-only сущностей (`chatmessages`) держится
 *   выключенным — MOVE удаляет строку из источника (PROD), что для чата
 *   стирало бы переписку с сервера.
 *
 * @prop {SyncEntityDTO[]} entities - список сущностей (получен из GET /api/sync/entities)
 * @emits run-sync - запустить sync одной сущности
 * @emits one-click - запустить «1 клик» для всех
 * @emits set-flag - изменить флаг для сущности
 * @see docs/features/dual-db-sync.md
 * @see SyncController REST-эндпоинты
 */
/**
 * UI для двух-БД синхронизации LOCAL↔SERVER.
 *
 * Функционал:
 * - **«1 клик» sync**: кнопка `Push` (LOCAL→SERVER) / `Pull` (SERVER→LOCAL)
 *   для всех таблиц согласно `oneClickDirection` в `SyncTarget<T>`.
 * - **Sync одной таблицы**: выбор из списка зарегистрированных
 *   `SyncRegistry.all` (Settings, Authors, Pictures, и т.д.).
 * - **Diff preview**: показывает `RecordDiff[]` (только изменённые поля)
 *   до отправки — пользователь может отменить.
 * - **Форс-стоп**: прервать sync посередине (`SyncService.abort`).
 *
 * Подписывается на SSE `SYNC` для live-обновления прогресса
 * (текущая таблица, текущий ID, процент).
 *
 * @see docs/features/dual-db-sync.md
 */
export default {
  name: 'SyncTable',
  components: { CustomConfirm },
  data() {
    return {
      isCustomConfirmVisible: false,
      customConfirmParams: undefined,
      ops: [
        { op: 'INSERT', label: 'Доб', title: 'Добавление' },
        { op: 'UPDATE', label: 'Изм', title: 'Изменение' },
        { op: 'DELETE', label: 'Уд', title: 'Удаление (в цели)' },
        { op: 'MOVE', label: 'Пер', title: 'Перемещение (удаление из источника после переноса)' },
      ],
    }
  },
  computed: {
    entities() {
      return this.$store.getters.getSyncEntities
    },
  },
  mounted() {
    this.$store.dispatch('loadSyncEntitiesPromise')
  },
  methods: {
    directionLabel(direction) {
      return direction === 'PUSH' ? 'Local → Server' : 'Server → Local'
    },
    // Имя поля флага в объекте сущности: PUSH+INSERT → pushInsert, PULL+MOVE → pullMove.
    fieldName(direction, op) {
      const prefix = direction === 'PUSH' ? 'push' : 'pull'
      return prefix + op.charAt(0) + op.slice(1).toLowerCase()
    },
    onFlagChange(entity, direction, operation, event) {
      const value = event.target.checked
      this.$store
        .dispatch('setSyncFlagPromise', { key: entity.key, direction, operation, value })
        .catch(() => {
          // откат визуального состояния при ошибке
          event.target.checked = !value
          this.customConfirmParams = {
            isAlert: true,
            alertType: 'error',
            header: 'Синхронизация',
            body: 'Не удалось сохранить флаг — см. лог сервера.',
            timeout: 15,
          }
          this.isCustomConfirmVisible = true
        })
    },
    confirmRun(entity, direction) {
      this.customConfirmParams = {
        header: 'Синхронизация',
        body: `Синхронизировать «${entity.displayName}» (${this.directionLabel(direction)})?`,
        timeout: 10,
        callback: () => this.doRun(entity, direction),
      }
      this.isCustomConfirmVisible = true
    },
    doRun(entity, direction) {
      this.$store
        .dispatch('runEntitySyncPromise', { key: entity.key, direction })
        .then((result) => {
          this.showResultAlert(`«${entity.displayName}» (${this.directionLabel(direction)})`, [
            result,
          ])
        })
        .catch(() => {
          this.customConfirmParams = {
            isAlert: true,
            alertType: 'error',
            header: 'Синхронизация',
            body: 'Не удалось выполнить синхронизацию — см. лог сервера.',
            timeout: 15,
          }
          this.isCustomConfirmVisible = true
        })
    },
    confirmOneClick() {
      this.customConfirmParams = {
        header: 'Синхронизация в 1 клик',
        body: 'Синхронизировать все разрешённые таблицы, каждую в её сторону (Settings/Pictures/Authors — на Server, пользователи сайта/статистика — на Local)?',
        timeout: 10,
        callback: this.doOneClick,
      }
      this.isCustomConfirmVisible = true
    },
    doOneClick() {
      this.$store
        .dispatch('runSyncOneClickPromise')
        .then((results) => {
          this.showResultAlert('Синхронизация в 1 клик', results)
        })
        .catch(() => {
          this.customConfirmParams = {
            isAlert: true,
            alertType: 'error',
            header: 'Синхронизация в 1 клик',
            body: 'Не удалось выполнить синхронизацию — см. лог сервера.',
            timeout: 15,
          }
          this.isCustomConfirmVisible = true
        })
    },
    showResultAlert(title, results) {
      const lines = results.map((r) => {
        if (r.skipped) return `${r.displayName || ''}: пропущено (запрещено настройками)`.trim()
        const created = r.created ? r.created.length : 0
        const updated = r.updated ? r.updated.length : 0
        const deleted = r.deleted ? r.deleted.length : 0
        const moved = r.moved ? r.moved.length : 0
        const label = r.displayName ? `${r.displayName}: ` : ''
        let line = `${label}добавлено ${created}, изменено ${updated}, удалено ${deleted}`
        if (moved) line += `, перемещено (удалено из источника) ${moved}`
        return line
      })
      this.customConfirmParams = {
        isAlert: true,
        alertType: 'info',
        header: title,
        body: lines.join('<br>'),
        timeout: 20,
      }
      this.isCustomConfirmVisible = true
    },
    closeCustomConfirm() {
      this.isCustomConfirmVisible = false
    },
  },
}
</script>

<style scoped>
.sync-table-wrapper {
  display: flex;
  flex-direction: column;
  gap: 10px;
  width: 100%;
  overflow-x: auto;
}
.sync-oneclick-button {
  align-self: flex-start;
  font-weight: 600;
}
/* Кнопки направления в ячейках таблицы не должны переноситься. */
.sync-run-button {
  white-space: nowrap;
}
.sync-table {
  border-collapse: collapse;
  width: 100%;
}
.sync-table th,
.sync-table td {
  border: 1px solid #ccc;
  padding: 4px 8px;
  text-align: center;
}
.sync-name-col {
  text-align: left;
  white-space: nowrap;
}
.sync-op-col {
  width: 34px;
  padding: 4px 2px;
  font-size: 12px;
}
.sync-group {
  font-size: 13px;
}
.sync-group-push {
  background: #eef6ff;
}
.sync-group-pull {
  background: #eefaf0;
}
</style>
