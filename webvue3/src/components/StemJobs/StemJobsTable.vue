<template>
  <div class="sjt-table">
    <div class="sjt-toolbar">
      <label class="sjt-toolbar-item">
        БД:
        <select v-model="target" @change="reload">
          <option value="remote">Сервер</option>
          <option value="local">Локальная</option>
        </select>
      </label>
      <button class="sjt-toolbar-item sjt-btn" @click="reload">Обновить</button>
      <span class="sjt-toolbar-item sjt-count">Всего: {{ stemJobs.length }}</span>
    </div>

    <div class="sjt-table-body">
      <b-table
        v-model:sort-by="sortBy"
        :items="stemJobs"
        :busy="isBusy"
        :fields="fields"
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

        <template #cell(user)="data">
          <div class="fld-ellipsis" :title="data.item.siteUserEmail">
            {{ data.item.siteUserDisplayName || data.item.siteUserEmail }}
          </div>
        </template>

        <template #cell(originalFileName)="data">
          <div class="fld-ellipsis" :title="data.value">{{ data.value }}</div>
        </template>

        <template #cell(mode)="data">
          <div style="text-align: center">
            {{ data.value === 'DEMUCS5' ? '5 дорожек' : '2 дорожки' }}
          </div>
        </template>

        <template #cell(status)="data">
          <div :class="['sjt-status', statusClass(data.value)]">{{ statusText(data.value) }}</div>
        </template>

        <template #cell(fileSizeBytes)="data">
          <div style="text-align: center">{{ formatSize(data.value) }}</div>
        </template>

        <template #cell(errorMessage)="data">
          <div class="fld-ellipsis" :title="data.value">{{ data.value }}</div>
        </template>

        <template #cell(createdAt)="data"
          ><div style="text-align: center">{{ formatDate(data.value) }}</div></template
        >
        <template #cell(expiresAt)="data"
          ><div style="text-align: center">{{ formatDate(data.value) }}</div></template
        >

        <template #cell(actions)="data">
          <div class="sjt-actions">
            <button
              v-if="data.item.status === 'WAITING' || data.item.status === 'WORKING'"
              class="sjt-btn sjt-btn-warn"
              @click="onStop(data.item)"
            >
              Остановить
            </button>
            <button class="sjt-btn sjt-btn-danger" @click="onDelete(data.item)">Удалить</button>
          </div>
        </template>
      </b-table>
    </div>
  </div>
</template>

<script>
import { BSpinner, BTable } from 'bootstrap-vue-next'

/**
 * Таблица со списком jobs с пагинацией, фильтрами и сортировкой.
 *
 * @see AGENTS.md
 */

export default {
  name: 'StemJobsTable',
  components: { BSpinner, BTable },
  data() {
    return {
      sortBy: [],
      isBusy: false,
    }
  },
  computed: {
    stemJobsIsLoading() {
      return this.$store.getters.getStemJobsIsLoading
    },
    stemJobs() {
      return this.$store.getters.getStemJobs
    },
    target: {
      get() {
        return this.$store.getters.getStemJobsTarget
      },
      set(value) {
        this.$store.dispatch('setStemJobsTarget', value)
      },
    },
    fields() {
      return [
        {
          key: 'id',
          label: 'ID',
          sortable: true,
          style: { minWidth: '50px', maxWidth: '50px', textAlign: 'center', fontSize: 'small' },
        },
        {
          key: 'user',
          label: 'Пользователь',
          sortable: true,
          style: { minWidth: '180px', maxWidth: '220px', textAlign: 'left', fontSize: 'small' },
        },
        {
          key: 'originalFileName',
          label: 'Файл',
          sortable: true,
          style: { minWidth: '160px', maxWidth: '220px', textAlign: 'left', fontSize: 'small' },
        },
        {
          key: 'mode',
          label: 'Режим',
          sortable: true,
          style: { minWidth: '90px', maxWidth: '90px', textAlign: 'center', fontSize: 'small' },
        },
        {
          key: 'status',
          label: 'Статус',
          sortable: true,
          style: { minWidth: '90px', maxWidth: '90px', textAlign: 'center', fontSize: 'small' },
        },
        {
          key: 'fileSizeBytes',
          label: 'Размер',
          sortable: true,
          style: { minWidth: '80px', maxWidth: '80px', textAlign: 'center', fontSize: 'small' },
        },
        {
          key: 'errorMessage',
          label: 'Ошибка',
          sortable: false,
          style: { minWidth: '160px', maxWidth: '220px', textAlign: 'left', fontSize: 'small' },
        },
        {
          key: 'createdAt',
          label: 'Создано',
          sortable: true,
          style: { minWidth: '130px', maxWidth: '130px', textAlign: 'center', fontSize: 'small' },
        },
        {
          key: 'expiresAt',
          label: 'Истекает',
          sortable: true,
          style: { minWidth: '130px', maxWidth: '130px', textAlign: 'center', fontSize: 'small' },
        },
        {
          key: 'actions',
          label: 'Действия',
          sortable: false,
          style: { minWidth: '160px', maxWidth: '160px', textAlign: 'center', fontSize: 'small' },
        },
      ]
    },
  },
  watch: {
    stemJobsIsLoading: {
      handler() {
        this.isBusy = this.stemJobsIsLoading
      },
    },
  },
  mounted() {
    this.reload()
  },
  methods: {
    reload() {
      this.$store.dispatch('loadStemJobs')
    },
    statusText(status) {
      return (
        { WAITING: 'В очереди', WORKING: 'В работе', DONE: 'Готово', ERROR: 'Ошибка' }[status] ||
        status
      )
    },
    statusClass(status) {
      return (
        {
          WAITING: 'sjt-status-waiting',
          WORKING: 'sjt-status-working',
          DONE: 'sjt-status-done',
          ERROR: 'sjt-status-error',
        }[status] || ''
      )
    },
    formatSize(bytes) {
      if (!bytes) return '—'
      return `${(bytes / (1024 * 1024)).toFixed(1)} МБ`
    },
    formatDate(ts) {
      if (!ts) return '—'
      return new Date(ts.replace(' ', 'T')).toLocaleString('ru-RU', {
        timeZone: 'Europe/Moscow',
        day: '2-digit',
        month: '2-digit',
        year: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
      })
    },
    onStop(job) {
      if (!confirm(`Остановить задание #${job.id}? Оно будет помечено как ошибка.`)) return
      this.$store.dispatch('stopStemJob', job.id)
    },
    onDelete(job) {
      if (!confirm(`Удалить задание #${job.id}? Файлы будут удалены из хранилища немедленно.`))
        return
      this.$store.dispatch('deleteStemJob', job.id)
    },
  },
}
</script>

<style scoped>
.sjt-table {
  padding: 0;
  margin: 0;
  width: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  font-family: Avenir, Helvetica, Arial, sans-serif;
}
.sjt-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
  font-size: small;
}
.sjt-toolbar-item {
  font-size: small;
}
.sjt-count {
  margin-left: auto;
  color: gray;
}
.sjt-btn {
  border: solid 1px black;
  border-radius: 6px;
  padding: 4px 10px;
  background-color: antiquewhite;
  cursor: pointer;
  font-size: small;
}
.sjt-btn:hover {
  background-color: lightpink;
}
.sjt-btn-warn {
  background-color: #ffe27a;
}
.sjt-btn-warn:hover {
  background-color: #ffd23f;
}
.sjt-btn-danger {
  background-color: #f5a3a3;
}
.sjt-btn-danger:hover {
  background-color: #e05555;
  color: white;
}
.sjt-table-body {
  width: fit-content;
  max-width: 100%;
  overflow-x: auto;
}
.sjt-actions {
  display: flex;
  gap: 6px;
  justify-content: center;
  flex-wrap: wrap;
}
.sjt-status {
  font-weight: 600;
}
.sjt-status-waiting,
.sjt-status-working {
  color: #a67c00;
}
.sjt-status-done {
  color: #3fae5b;
}
.sjt-status-error {
  color: #e05555;
}
.fld-ellipsis {
  font-size: small;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>
