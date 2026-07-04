<template>
  <div class="sync-table-wrapper">
    <custom-confirm v-if="isCustomConfirmVisible" :params="customConfirmParams" @close="closeCustomConfirm" />
    <button class="button-action sync-oneclick-button" @click="confirmOneClick">🔄 Синхронизация в 1 клик</button>
    <table class="sync-table">
      <thead>
        <tr>
          <th>Таблица</th>
          <th>→ Server</th>
          <th>← Local</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="entity in entities" :key="entity.key">
          <td>{{ entity.displayName }}</td>
          <td>
            <button class="button-action button-action-inline" :disabled="!entity.allowPush" @click="confirmRun(entity, 'PUSH')">→ Server</button>
          </td>
          <td>
            <button class="button-action button-action-inline" :disabled="!entity.allowPull" @click="confirmRun(entity, 'PULL')">← Local</button>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<script>
import CustomConfirm from '../Common/CustomConfirm.vue';

export default {
  name: 'SyncTable',
  components: { CustomConfirm },
  data() {
    return {
      isCustomConfirmVisible: false,
      customConfirmParams: undefined,
    }
  },
  computed: {
    entities() { return this.$store.getters.getSyncEntities },
  },
  mounted() {
    this.$store.dispatch('loadSyncEntitiesPromise');
  },
  methods: {
    directionLabel(direction) { return direction === 'PUSH' ? 'Local → Server' : 'Server → Local'; },
    confirmRun(entity, direction) {
      this.customConfirmParams = {
        header: 'Синхронизация',
        body: `Синхронизировать «${entity.displayName}» (${this.directionLabel(direction)})?`,
        timeout: 10,
        callback: () => this.doRun(entity, direction)
      }
      this.isCustomConfirmVisible = true;
    },
    doRun(entity, direction) {
      this.$store.dispatch('runEntitySyncPromise', {key: entity.key, direction}).then(result => {
        this.showResultAlert(`«${entity.displayName}» (${this.directionLabel(direction)})`, [result]);
      }).catch(() => {
        this.customConfirmParams = {
          isAlert: true, alertType: 'error', header: 'Синхронизация',
          body: 'Не удалось выполнить синхронизацию — см. лог сервера.', timeout: 15
        }
        this.isCustomConfirmVisible = true;
      });
    },
    confirmOneClick() {
      this.customConfirmParams = {
        header: 'Синхронизация в 1 клик',
        body: 'Синхронизировать все разрешённые таблицы, каждую в её сторону (Settings/Pictures/Authors — на Server, пользователи сайта/статистика — на Local)?',
        timeout: 10,
        callback: this.doOneClick
      }
      this.isCustomConfirmVisible = true;
    },
    doOneClick() {
      this.$store.dispatch('runSyncOneClickPromise').then(results => {
        this.showResultAlert('Синхронизация в 1 клик', results);
      }).catch(() => {
        this.customConfirmParams = {
          isAlert: true, alertType: 'error', header: 'Синхронизация в 1 клик',
          body: 'Не удалось выполнить синхронизацию — см. лог сервера.', timeout: 15
        }
        this.isCustomConfirmVisible = true;
      });
    },
    showResultAlert(title, results) {
      const lines = results.map(r => {
        if (r.skipped) return `${r.displayName || ''}: пропущено (запрещено настройками)`.trim();
        const created = r.created ? r.created.length : 0;
        const updated = r.updated ? r.updated.length : 0;
        const deleted = r.deleted ? r.deleted.length : 0;
        const label = r.displayName ? `${r.displayName}: ` : '';
        return `${label}добавлено ${created}, изменено ${updated}, удалено ${deleted}`;
      });
      this.customConfirmParams = {
        isAlert: true, alertType: 'info', header: title,
        body: lines.join('<br>'), timeout: 20
      }
      this.isCustomConfirmVisible = true;
    },
    closeCustomConfirm() { this.isCustomConfirmVisible = false; },
  }
}
</script>

<style scoped>
.sync-table-wrapper {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.sync-oneclick-button {
  align-self: flex-start;
}
.sync-table {
  border-collapse: collapse;
  width: 100%;
}
.sync-table th, .sync-table td {
  border: 1px solid #ccc;
  padding: 4px 8px;
  text-align: center;
}
</style>
