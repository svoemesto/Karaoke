<template>
  <div class="uslm-overlay" @click.self="close">
    <div class="uslm-modal">
      <div class="uslm-head">
        <div class="uslm-title">Временный доступ к песне: {{ userLabel }}</div>
        <button class="uslm-close" @click="close">×</button>
      </div>

      <div v-if="isLinksLoading" class="uslm-loading">Загрузка...</div>
      <template v-else>
        <div class="uslm-tabs">
          <button :class="{ active: tab === 'active' }" @click="tab = 'active'">
            Активные ({{ activeLinks.length }})
          </button>
          <button :class="{ active: tab === 'history' }" @click="tab = 'history'">
            Завершённые ({{ finishedLinks.length }})
          </button>
        </div>

        <table v-if="visibleLinks.length" class="uslm-table">
          <thead>
            <tr>
              <th>Песня</th>
              <th>Создана</th>
              <th>Истекает</th>
              <th>Открытий</th>
              <th>Отказов</th>
              <th>Причина</th>
              <th>Действия</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="link in visibleLinks" :key="link.id">
              <td>#{{ link.songId }}</td>
              <td class="uslm-nowrap">{{ link.createdAtLabel || formatDate(link.createdAt) }}</td>
              <td class="uslm-nowrap">{{ link.expiresAtLabel || formatDate(link.expiresAt) }}</td>
              <td>{{ link.sessionsTotal || 0 }}</td>
              <td>{{ link.rejectedConcurrent || 0 }}</td>
              <td>{{ link.revokeReason || '—' }}</td>
              <td class="uslm-actions">
                <button v-if="link.active" class="uslm-btn-danger" @click="onRevoke(link)">
                  Отозвать
                </button>
                <button class="uslm-btn" @click="onShowSessions(link)">Сессии</button>
              </td>
            </tr>
          </tbody>
        </table>
        <div v-else class="uslm-empty">Ссылок нет</div>

        <div v-if="sessionsVisible" class="uslm-sessions">
          <div class="uslm-sessions-head">
            <span>Сессии по ссылке #{{ selectedLinkId }}</span>
            <button class="uslm-close" @click="sessionsVisible = false">×</button>
          </div>
          <table v-if="sessions.length" class="uslm-table">
            <thead>
              <tr>
                <th>Открыто</th>
                <th>Завершено</th>
                <th>Результат</th>
                <th>Browser hash</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="s in sessions" :key="s.id">
                <td class="uslm-nowrap">{{ s.openedAtLabel || formatDate(s.openedAt) }}</td>
                <td class="uslm-nowrap">
                  {{ s.finishedAtLabel || (s.finishedAt ? formatDate(s.finishedAt) : '—') }}
                </td>
                <td>{{ s.result || '—' }}</td>
                <td class="uslm-mono">{{ (s.browserHash || '').slice(0, 12) }}…</td>
              </tr>
            </tbody>
          </table>
          <div v-else class="uslm-empty">Сессий нет</div>
        </div>
      </template>
    </div>
  </div>
</template>

<script>
/**
 * Админская модалка просмотра и отзыва share-ссылок пользователя (add-song-share-link).
 * Аналогично UserSubscriptionsModal.vue: таблица ссылок, секции «Активные» / «Завершённые»,
 * кнопка «Отозвать» для активных, разворачиваемая панель с сессиями.
 *
 * @emits close
 *
 * @see AGENTS.md
 */
export default {
  name: 'UserShareLinksModal',
  props: {
    siteUserId: { type: Number, required: true },
    userLabel: { type: String, default: '' },
    target: { type: String, default: 'local' },
  },
  emits: ['close'],
  data() {
    return { tab: 'active', sessionsVisible: false, selectedLinkId: null }
  },
  computed: {
    links() {
      return this.$store.getters.getSiteUserShareLinks
    },
    isLinksLoading() {
      return this.$store.getters.getSiteUserShareLinksIsLoading
    },
    sessions() {
      return this.$store.getters.getSiteUserShareLinkSessions
    },
    activeLinks() {
      return this.links.filter((l) => l.active && !l.revokedAt)
    },
    finishedLinks() {
      return this.links.filter((l) => !l.active || l.revokedAt)
    },
    visibleLinks() {
      return this.tab === 'active' ? this.activeLinks : this.finishedLinks
    },
  },
  async mounted() {
    await this.$store.dispatch('setSiteUsersTarget', this.target)
    await this.$store.dispatch('loadSiteUserShareLinks', {
      siteUserId: this.siteUserId,
      activeOnly: false,
      limit: 50,
      target: this.target,
    })
  },
  methods: {
    formatDate(ts) {
      if (!ts) return '—'
      try {
        return new Date(ts).toLocaleString('ru-RU')
      } catch (e) {
        return new Date(ts).toString()
      }
    },
    async onRevoke(link) {
      if (!window.confirm(`Отозвать ссылку #${link.id}? Все активные сессии будут завершены.`))
        return
      await this.$store.dispatch('revokeSiteUserShareLink', {
        shareLinkId: link.id,
        reason: 'admin',
        target: this.target,
      })
      await this.$store.dispatch('loadSiteUserShareLinks', {
        siteUserId: this.siteUserId,
        activeOnly: false,
        limit: 50,
        target: this.target,
      })
    },
    async onShowSessions(link) {
      this.selectedLinkId = link.id
      this.sessionsVisible = true
      await this.$store.dispatch('loadSiteUserShareLinkSessions', {
        shareLinkId: link.id,
        target: this.target,
      })
    },
    close() {
      this.$emit('close')
    },
  },
}
</script>

<style scoped>
.uslm-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  justify-content: center;
  align-items: flex-start;
  z-index: 1000;
  padding-top: 5vh;
}
.uslm-modal {
  background: #fff;
  border-radius: 8px;
  width: min(900px, 95vw);
  max-height: 85vh;
  overflow-y: auto;
  padding: 16px 24px;
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.3);
}
.uslm-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.uslm-title {
  font-weight: 600;
  font-size: 16px;
}
.uslm-close {
  background: none;
  border: none;
  font-size: 22px;
  cursor: pointer;
  color: #777;
}
.uslm-tabs {
  display: flex;
  gap: 4px;
  margin-bottom: 12px;
}
.uslm-tabs button {
  padding: 6px 12px;
  border: 1px solid #ddd;
  background: #f7f7f7;
  border-radius: 6px;
  cursor: pointer;
}
.uslm-tabs button.active {
  background: #f80;
  color: #1c1c1c;
  border-color: #f80;
}
.uslm-loading {
  padding: 24px;
  text-align: center;
}
.uslm-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 14px;
}
.uslm-table th,
.uslm-table td {
  border-bottom: 1px solid #eee;
  padding: 6px 8px;
  text-align: left;
}
.uslm-table th {
  background: #fafafa;
  font-weight: 600;
}
.uslm-nowrap {
  white-space: nowrap;
}
.uslm-mono {
  font-family: monospace;
}
.uslm-empty {
  padding: 24px;
  text-align: center;
  color: #777;
}
.uslm-actions {
  display: flex;
  gap: 4px;
}
.uslm-btn,
.uslm-btn-danger {
  padding: 4px 10px;
  border-radius: 4px;
  border: 1px solid #ddd;
  background: #f7f7f7;
  cursor: pointer;
  font-size: 12px;
}
.uslm-btn-danger {
  background: #fee;
  border-color: #e88;
  color: #b00;
}
.uslm-sessions {
  margin-top: 16px;
  padding: 12px;
  background: #f9f9f9;
  border-radius: 6px;
}
.uslm-sessions-head {
  display: flex;
  justify-content: space-between;
  margin-bottom: 8px;
  font-weight: 600;
}
</style>
