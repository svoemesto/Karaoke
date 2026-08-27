<template>
  <div class="km-page">
    <!-- Хедер единый (spec 250) -->
    <AppHeader
      :back="{ to: '/account', label: '← Личный кабинет' }"
      :show-auth-widget="false"
      :show-theme-toggle="false"
    />

    <div class="km-content">
      <h1 class="km-title">🎤 Редактор караоке</h1>
      <p class="ke-intro">
        Здесь появляются песни, которые вам назначили на разметку. Разметьте слоги под музыку и
        отправьте на проверку — после одобрения песня станет доступна в онлайн-плеере.
      </p>

      <div v-if="loading" class="ke-empty">Загрузка…</div>
      <div v-else-if="tasks.length === 0" class="ke-empty">
        Вам пока не назначено ни одной песни.
      </div>

      <div v-else>
        <div class="ke-bulk-bar">
          <button
            class="ke-btn ke-btn-bulk"
            :disabled="approvedCount === 0 || isBulkBusy"
            :title="approvedCount === 0 ? 'Нет одобренных заданий' : ''"
            @click="onDeleteAllApproved"
          >
            Удалить все одобренные ({{ approvedCount }})
          </button>
        </div>

        <div class="ke-list">
          <RouterLink
            v-for="t in sortedTasks"
            :key="t.id"
            :to="`/account/editor/${t.id}`"
            class="ke-card"
          >
            <div class="ke-card-main">
              <div class="ke-card-song">{{ t.songName || 'Без названия' }}</div>
              <div class="ke-card-author">
                {{ t.author }}<span v-if="t.album"> · {{ t.album }}</span
                ><span v-if="t.year"> · {{ t.year }}</span>
              </div>
              <div v-if="t.status === 'rejected' && t.reviewComment" class="ke-card-comment">
                💬 {{ t.reviewComment }}
              </div>
            </div>
            <div class="ke-card-side">
              <span class="ke-badge" :class="`ke-badge-${t.status}`">{{
                statusLabel(t.status)
              }}</span>
              <button
                v-if="t.status !== 'approved'"
                class="ke-btn ke-btn-refuse"
                :disabled="isBusyId === t.id"
                @click.stop.prevent="onRefuse(t)"
              >
                Отказаться
              </button>
              <button
                v-else
                class="ke-btn ke-btn-delete"
                :disabled="isBusyId === t.id"
                @click.stop.prevent="onDelete(t)"
              >
                Удалить
              </button>
              <span class="ke-card-arrow">→</span>
            </div>
          </RouterLink>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { fetchTasks, refuseTask, deleteTask, deleteApprovedTasks } from '../services/songEditorApi'
import { useAuth } from '../composables/useAuth'
import { STATUS_LABELS } from '../composables/editorStatus'
import AppHeader from '../components/AppHeader.vue'

// Одобренные карточки идут строго после активных (паттерн STATUS_ORDER в
// SongEditorTable.vue webvue3, но с approved последним — по FR-001/US-1).
const STATUS_ORDER = {
  assigned: 0,
  in_progress: 1,
  submitted: 2,
  rejected: 3,
  approved: 4,
}

/**
 * View-страница «Editor Tasks» — основной layout и data-fetching.
 *
 * @see AGENTS.md
 * @see archive/docs/features/editor-tasks.md
 */

export default {
  name: 'EditorTasksView',
  components: { AppHeader },
  setup() {
    const { token, user, fetchMe } = useAuth()
    return { token, user, fetchMe }
  },
  data() {
    return {
      tasks: [],
      loading: true,
      // ID задания, для которого сейчас идёт запрос — блокирует повторный клик и подсвечивает.
      isBusyId: 0,
      // Бул-флаг для кнопки «Удалить все одобренные».
      isBulkBusy: false,
    }
  },
  computed: {
    sortedTasks() {
      return [...this.tasks].sort(
        (a, b) => (STATUS_ORDER[a.status] ?? 99) - (STATUS_ORDER[b.status] ?? 99),
      )
    },
    approvedCount() {
      return this.tasks.filter((t) => t.status === 'approved').length
    },
  },
  async mounted() {
    await this.fetchMe()
    if (!this.token) {
      this.$router.push({ path: '/login', query: { redirect: '/account/editor' } })
      return
    }
    if (!this.user || !this.user.editor) {
      this.$router.push('/account')
      return
    }
    await this.load()
  },
  methods: {
    statusLabel(s) {
      return STATUS_LABELS[s] || s
    },
    async load() {
      this.loading = true
      try {
        const { status, body } = await fetchTasks()
        this.tasks = status === 200 && Array.isArray(body) ? body : []
      } finally {
        this.loading = false
      }
    },
    async onRefuse(t) {
      if (
        !confirm(
          `Отказаться от задания «${t.songName || 'Без названия'}» (${this.statusLabel(
            t.status,
          )})? Задание и черновик будут удалены. Это действие нельзя отменить.`,
        )
      ) {
        return
      }
      this.isBusyId = t.id
      try {
        const { status, body } = await refuseTask(t.id)
        if (status === 200 && body && body.ok) {
          await this.load()
        } else if (status === 200 && body && body.error === 'assignment_not_found') {
          // Идемпотентно — карточка просто исчезает.
          await this.load()
        } else {
          alert('Не удалось отказаться от задания. Попробуйте ещё раз.')
        }
      } finally {
        this.isBusyId = 0
      }
    },
    async onDelete(t) {
      if (
        !confirm(
          `Удалить одобренное задание «${
            t.songName || 'Без названия'
          }» из моего списка? Песня и её разметка останутся как были — удаляется только запись о назначении.`,
        )
      ) {
        return
      }
      this.isBusyId = t.id
      try {
        const { status, body } = await deleteTask(t.id)
        if (status === 200 && body && body.ok) {
          await this.load()
        } else if (status === 200 && body && body.error === 'assignment_not_found') {
          await this.load()
        } else if (status === 200 && body && body.error === 'not_approved') {
          alert('Это задание нельзя удалить из личного кабинета.')
        } else {
          alert('Не удалось удалить задание. Попробуйте ещё раз.')
        }
      } finally {
        this.isBusyId = 0
      }
    },
    async onDeleteAllApproved() {
      const n = this.approvedCount
      if (n === 0) return
      if (
        !confirm(
          `Удалить все ${n} одобренных заданий? Это действие нельзя отменить. Сами песни не пострадают — удаляются только записи о назначениях.`,
        )
      ) {
        return
      }
      this.isBulkBusy = true
      try {
        const { status, body } = await deleteApprovedTasks()
        if (status === 200 && body && body.ok) {
          await this.load()
        } else {
          alert('Не удалось удалить одобренные задания. Попробуйте ещё раз.')
        }
      } finally {
        this.isBulkBusy = false
      }
    },
  },
}
</script>

<style scoped>
.km-page {
  min-height: 100vh;
  background: var(--km-bg);
  color: var(--km-text);
}
.km-content {
  max-width: 900px;
  margin: 0 auto;
  padding: 2rem 1rem;
}
.km-title {
  font-size: 1.4rem;
  margin: 0 0 0.5rem;
}
.ke-intro {
  color: var(--km-text2);
  font-size: 0.9rem;
  margin: 0 0 1.5rem;
  line-height: 1.5;
}
.ke-empty {
  color: var(--km-text2);
  text-align: center;
  padding: 3rem 1rem;
}
.ke-list {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}
.ke-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  background: var(--km-card);
  border: 1px solid var(--km-border);
  border-radius: 14px;
  padding: 1rem 1.25rem;
  text-decoration: none;
  color: var(--km-text);
  transition:
    background 0.15s,
    transform 0.15s;
}
.ke-card:hover {
  background: var(--km-hover);
  transform: translateY(-1px);
}
.ke-card-main {
  min-width: 0;
}
.ke-card-song {
  font-weight: 600;
  font-size: 1.02rem;
}
.ke-card-author {
  color: var(--km-text2);
  font-size: 0.85rem;
  margin-top: 0.15rem;
}
.ke-card-comment {
  color: #d98a2b;
  font-size: 0.82rem;
  margin-top: 0.4rem;
}
.ke-card-side {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  flex-shrink: 0;
}
.ke-card-arrow {
  color: var(--km-accent);
  font-size: 1.1rem;
}
.ke-badge {
  font-size: 0.72rem;
  font-weight: 700;
  border-radius: 20px;
  padding: 0.22rem 0.7rem;
  white-space: nowrap;
}
.ke-badge-assigned {
  background: #e2e6ea;
  color: #5a6570;
}
.ke-badge-in_progress {
  background: #dbeafe;
  color: #1e5fbf;
}
.ke-badge-submitted {
  background: #fef3c7;
  color: #92700a;
}
.ke-badge-approved {
  background: #d1f5d8;
  color: #24803a;
}
.ke-badge-rejected {
  background: #ffe0cc;
  color: #b8500f;
}
.ke-btn {
  font-size: 0.78rem;
  font-weight: 600;
  padding: 0.32rem 0.7rem;
  border-radius: 14px;
  border: 1px solid var(--km-border);
  background: var(--km-card);
  color: var(--km-text);
  cursor: pointer;
  transition: background 0.15s;
  white-space: nowrap;
}
.ke-btn:hover:not(:disabled) {
  background: var(--km-hover);
}
.ke-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.ke-btn-refuse {
  background: #fff3d6;
  color: #8a5a0a;
  border-color: #f2dd9a;
}
.ke-btn-refuse:hover:not(:disabled) {
  background: #fbe6b0;
}
.ke-btn-delete {
  background: #ffd7d2;
  color: #a82a18;
  border-color: #f5b8b0;
}
.ke-btn-delete:hover:not(:disabled) {
  background: #ffc4bd;
}
.ke-btn-bulk {
  background: #ffe0cc;
  color: #b8500f;
  border-color: #f5b894;
  font-size: 0.85rem;
  padding: 0.45rem 0.85rem;
}
.ke-btn-bulk:hover:not(:disabled) {
  background: #ffd0b8;
}
.ke-bulk-bar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 1rem;
}
</style>
