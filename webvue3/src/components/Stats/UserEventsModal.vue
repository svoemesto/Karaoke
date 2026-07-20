<template>
  <div class="uem-backdrop" @click.self="$emit('close')">
    <div class="uem-dialog">
      <div class="uem-header">
        <div>
          <h5 class="mb-0">События пользователя</h5>
          <div class="text-muted small">{{ userLabel }} — всего {{ totalCount }}</div>
        </div>
        <div class="d-flex align-items-center gap-2">
          <div class="btn-group btn-group-sm" role="group">
            <button class="btn" :class="viewMode === 'tree' ? 'btn-primary' : 'btn-outline-primary'" @click="viewMode = 'tree'">Дерево</button>
            <button class="btn" :class="viewMode === 'flat' ? 'btn-primary' : 'btn-outline-primary'" @click="viewMode = 'flat'">Таблица</button>
          </div>
          <button class="btn-close" @click="$emit('close')"/>
        </div>
      </div>
      <div class="uem-body">
        <div v-if="isLoading" class="text-center py-4"><BSpinner small /></div>
        <template v-else>
          <div v-if="totalCount > events.length" class="alert alert-warning py-1 px-2 small mb-2">
            Показаны последние {{ events.length }} из {{ totalCount }} событий.
          </div>

          <!-- Дерево: страница → действия -->
          <div v-if="viewMode === 'tree'">
            <div v-for="branch in tree" :key="branch.key" class="tree-branch">
              <div class="branch-head" @click="toggle(branch.key)">
                <span class="branch-caret">{{ expanded[branch.key] === false ? '▸' : '▾' }}</span>
                <span class="branch-icon">{{ branch.icon }}</span>
                <span class="branch-label" :title="branch.label">{{ branch.label }}</span>
                <span class="branch-meta">{{ branch.events.length }} соб. · {{ formatRange(branch.from, branch.to) }}</span>
              </div>
              <div v-if="expanded[branch.key] !== false" class="branch-body">
                <div v-for="(evt, i) in branch.events" :key="i" class="leaf">
                  <span class="leaf-time">{{ formatDate(evt.eventDate) }}</span>
                  <span class="leaf-type">{{ evt.eventType }}</span>
                  <span class="leaf-desc" :title="evt.eventDescription">{{ leafDetail(evt) }}</span>
                  <span class="leaf-ip">{{ evt.clientIp || '' }}</span>
                </div>
              </div>
            </div>
            <div v-if="!tree.length" class="text-center text-muted py-3">Нет событий</div>
          </div>

          <!-- Плоская таблица -->
          <table v-else class="table table-sm table-hover table-bordered mb-2">
            <thead class="table-dark">
              <tr><th>Дата</th><th>Тип</th><th>Страница</th><th>Описание</th><th>IP</th></tr>
            </thead>
            <tbody>
              <tr v-for="(evt, idx) in events" :key="idx">
                <td class="text-nowrap">{{ formatDate(evt.eventDate) }}</td>
                <td class="text-nowrap">{{ evt.eventType }}</td>
                <td class="text-start">{{ pageLabel(evt) }}</td>
                <td class="text-start">{{ evt.eventDescription }}</td>
                <td class="text-nowrap">{{ evt.clientIp || '-' }}</td>
              </tr>
              <tr v-if="!events.length"><td colspan="5" class="text-center text-muted">Нет событий</td></tr>
            </tbody>
          </table>
        </template>
      </div>
    </div>
  </div>
</template>

<script>
import { BSpinner } from 'bootstrap-vue-next'

export default {
  name: 'UserEventsModal',
  components: { BSpinner },
  props: {
    user: { type: Object, default: null },
    events: { type: Array, default: () => [] },
    totalCount: { type: Number, default: 0 },
    isLoading: { type: Boolean, default: false },
    cap: { type: Number, default: 2000 },
  },
  emits: ['close'],
  data() { return { viewMode: 'tree', expanded: {} } },
  computed: {
    userLabel() {
      const u = this.user
      if (!u) return ''
      if (u.siteUserId > 0) return `${u.displayName || u.email || ''} (#${u.siteUserId})`
      return `Аноним ${(u.anonId || '').slice(0, 12)}…`
    },
    // Дерево: группируем события по «странице» (песня по song_id либо раздел по rest_name).
    // Все действия клиента на одной странице сайта — листья одной ветки.
    tree() {
      const groups = new Map()
      for (const evt of this.events) {
        const key = evt.songId > 0 ? `song:${evt.songId}` : `rest:${evt.restName || 'other'}`
        let g = groups.get(key)
        if (!g) {
          g = {
            key,
            kind: evt.songId > 0 ? 'song' : 'rest',
            icon: evt.songId > 0 ? '🎵' : '📄',
            label: this.pageLabel(evt),
            events: [],
            from: null,
            to: null,
          }
          groups.set(key, g)
        }
        g.events.push(evt)
        const t = evt.eventDate ? new Date(evt.eventDate).getTime() : null
        if (t !== null) {
          if (g.from === null || t < g.from) g.from = t
          if (g.to === null || t > g.to) g.to = t
        }
      }
      const branches = Array.from(groups.values())
      // Листья внутри ветки — по времени по возрастанию.
      branches.forEach(b => b.events.sort((a, c) => new Date(a.eventDate) - new Date(c.eventDate)))
      // Ветки — по последней активности убыв.
      branches.sort((a, b) => (b.to || 0) - (a.to || 0))
      return branches
    }
  },
  watch: {
    user() { this.expanded = {}; this.viewMode = 'tree' }
  },
  methods: {
    toggle(key) { this.expanded = { ...this.expanded, [key]: this.expanded[key] === false } },
    pageLabel(evt) {
      if (evt.songId > 0) return evt.songName || `Песня #${evt.songId}`
      switch (evt.restName) {
        case 'main': case 'home': return 'Главная'
        case 'zakroma': return 'Закрома'
        case 'filter': case 'search': return 'Поиск'
        case 'song': return 'Страница песни'
        case '': case undefined: case null: return 'Другое'
        default: return evt.restName
      }
    },
    // Короткая деталь листа: описание, а если пусто — комбинация link_type/link_name.
    leafDetail(evt) {
      if (evt.eventDescription) return evt.eventDescription
      const parts = [evt.linkType, evt.linkName].filter(Boolean)
      return parts.join(' · ') || '—'
    },
    formatDate(ts) {
      if (!ts) return ''
      return new Date(ts).toLocaleString('ru-RU', { timeZone: 'Europe/Moscow' })
    },
    formatRange(from, to) {
      if (!from) return ''
      const opts = { timeZone: 'Europe/Moscow', day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' }
      const f = new Date(from).toLocaleString('ru-RU', opts)
      if (!to || to === from) return f
      return `${f} — ${new Date(to).toLocaleString('ru-RU', opts)}`
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

.tree-branch { border: 1px solid #e6e6e6; border-radius: 6px; margin-bottom: 8px; overflow: hidden; }
.branch-head {
  display: flex; align-items: center; gap: 8px; padding: 7px 10px; cursor: pointer;
  background: #f4f7fb; user-select: none;
}
.branch-head:hover { background: #eaf1fb; }
.branch-caret { width: 12px; color: #666; }
.branch-icon { flex: 0 0 auto; }
.branch-label { font-weight: 600; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.branch-meta { margin-left: auto; color: #888; font-size: 0.75rem; white-space: nowrap; }
.branch-body { padding: 4px 10px 8px 30px; }
.leaf {
  display: grid; grid-template-columns: 150px 130px 1fr 130px; gap: 8px;
  padding: 3px 0; border-bottom: 1px dashed #eee; align-items: baseline;
}
.leaf:last-child { border-bottom: none; }
.leaf-time { color: #666; white-space: nowrap; }
.leaf-type { color: #2b5fb3; white-space: nowrap; }
.leaf-desc { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.leaf-ip { color: #999; white-space: nowrap; text-align: right; font-size: 0.75rem; }
</style>
