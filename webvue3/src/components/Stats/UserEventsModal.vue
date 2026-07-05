<template>
  <div class="uem-backdrop" @click.self="$emit('close')">
    <div class="uem-dialog">
      <div class="uem-header">
        <div>
          <h5 class="mb-0">События пользователя</h5>
          <div class="text-muted small">{{ user ? (user.displayName || user.email) : '' }} (#{{ user ? user.siteUserId : '' }}) — всего {{ totalCount }}</div>
        </div>
        <button class="btn-close" @click="$emit('close')"></button>
      </div>
      <div class="uem-body">
        <div v-if="isLoading" class="text-center py-4"><BSpinner small /></div>
        <template v-else>
          <table class="table table-sm table-hover table-bordered mb-2">
            <thead class="table-dark">
              <tr><th>Дата</th><th>Тип</th><th>Описание</th><th>IP</th></tr>
            </thead>
            <tbody>
              <tr v-for="(evt, idx) in events" :key="idx">
                <td class="text-nowrap">{{ formatDate(evt.eventDate) }}</td>
                <td class="text-nowrap">{{ evt.eventType }}</td>
                <td class="text-start">{{ evt.eventDescription }}</td>
                <td class="text-nowrap">{{ evt.clientIp || '-' }}</td>
              </tr>
              <tr v-if="!events.length"><td colspan="4" class="text-center text-muted">Нет событий</td></tr>
            </tbody>
          </table>
          <div class="d-flex align-items-center gap-2">
            <b-pagination v-model="pageModel" :total-rows="totalCount" :per-page="pageSize" :limit="10" size="sm" pills />
            <span class="text-muted small">Всего: {{ totalCount }}</span>
          </div>
        </template>
      </div>
    </div>
  </div>
</template>

<script>
import { BSpinner, BPagination } from 'bootstrap-vue-next'

export default {
  name: 'UserEventsModal',
  components: { BSpinner, BPagination },
  props: {
    user: { type: Object, default: null },
    events: { type: Array, default: () => [] },
    totalCount: { type: Number, default: 0 },
    isLoading: { type: Boolean, default: false },
    pageSize: { type: Number, default: 50 },
  },
  emits: ['close', 'page'],
  data() { return { page: 1 } },
  computed: {
    pageModel: {
      get() { return this.page },
      set(v) { this.page = v; this.$emit('page', v) }
    }
  },
  watch: {
    user() { this.page = 1 }
  },
  methods: {
    formatDate(ts) {
      if (!ts) return ''
      return new Date(ts).toLocaleString('ru-RU', { timeZone: 'Europe/Moscow' })
    }
  }
}
</script>

<style scoped>
.uem-backdrop {
  position: fixed; inset: 0; background: rgba(0,0,0,0.5);
  display: flex; align-items: center; justify-content: center; z-index: 1080;
}
.uem-dialog {
  background: #fff; border-radius: 10px; width: min(920px, 94vw);
  max-height: 88vh; display: flex; flex-direction: column;
  box-shadow: 0 10px 40px rgba(0,0,0,0.3);
}
.uem-header {
  display: flex; align-items: flex-start; justify-content: space-between;
  padding: 14px 18px; border-bottom: 1px solid #eee;
}
.uem-body { padding: 14px 18px; overflow: auto; font-size: 0.82rem; }
</style>
